package com.iptvup.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.EpgProgramEntity
import com.iptvup.app.data.local.FavoriteDao
import com.iptvup.app.data.repository.RecentRepository
import com.iptvup.app.data.sports.SportsEventRaw
import com.iptvup.app.data.sports.SportsRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class SportsEventUi(
    val event: SportsEventRaw,
    val channel: ChannelEntity?
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    favoriteDao: FavoriteDao,
    private val epgDao: EpgDao,
    recentRepository: RecentRepository,
    private val sportsRepository: SportsRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val profileId: String = checkNotNull(savedStateHandle["profileId"])

    val channelCount = channelDao.observeCount(profileId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val favoriteCount = favoriteDao.observeKeys(profileId)
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val recent = recentRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val syncing = syncManager.state
        .map { it is SyncState.Running && it.profileId == profileId }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private val _sportsEvents = MutableStateFlow<List<SportsEventUi>>(emptyList())
    val sportsEvents: StateFlow<List<SportsEventUi>> = _sportsEvents.asStateFlow()

    private val _sportsLoading = MutableStateFlow(false)
    val sportsLoading: StateFlow<Boolean> = _sportsLoading.asStateFlow()

    init {
        viewModelScope.launch {
            syncManager.state.collect { state ->
                if (state is SyncState.Success && state.profileId == profileId) {
                    loadSports()
                }
            }
        }
    }

    fun loadSports() {
        if (_sportsLoading.value) return
        viewModelScope.launch {
            _sportsLoading.value = true
            try {
                val raw = sportsRepository.eventsForToday()
                if (raw.isEmpty()) {
                    _sportsEvents.value = emptyList()
                    return@launch
                }
                val channels = channelDao.getVisible(profileId)
                    .filter { ChannelQuality.label(it.name) != "8K" }
                val from = raw.minOf { it.startMs } - 3 * HOUR_MS
                val to = raw.maxOf { it.startMs } + 3 * HOUR_MS
                val epgIds = channels.mapNotNull { it.epgChannelId }.distinct()
                val programs = epgIds.chunked(EPG_ID_CHUNK).flatMap { ids ->
                    epgDao.getWindowForChannels(profileId, ids, from, to)
                }
                _sportsEvents.value = withContext(Dispatchers.Default) {
                    matchEvents(raw, channels, programs)
                }
            } catch (_: Exception) {
                _sportsEvents.value = emptyList()
            } finally {
                _sportsLoading.value = false
            }
        }
    }

    fun sync() {
        syncManager.sync(profileId)
    }

    private fun matchEvents(
        raw: List<SportsEventRaw>,
        channels: List<ChannelEntity>,
        programs: List<EpgProgramEntity>
    ): List<SportsEventUi> {
        val channelsByEpgId = channels
            .filter { it.epgChannelId != null }
            .groupBy { it.epgChannelId!! }
        val normalizedChannels = channels.map { it to normalize(it.name) }
        val normalizedPrograms = HashMap<String, String>()
        return raw
            .sortedBy { it.startMs }
            .take(8)
            .map { event ->
                val eventStart = event.startMs
                val homeTokens = teamTokens(event.homeTeam)
                val awayTokens = teamTokens(event.awayTeam)
                val epgMatches = programs.filter { program ->
                    program.endMs >= eventStart - 2 * HOUR_MS &&
                        program.startMs <= eventStart + 3 * HOUR_MS &&
                        matchesTeams(
                            normalizedPrograms.getOrPut(program.id) {
                                normalize("${program.title} ${program.description.orEmpty()}")
                            },
                            homeTokens,
                            awayTokens
                        )
                }
                val epgChannel = epgMatches
                    .flatMap { program -> channelsByEpgId[program.epgChannelId].orEmpty() }
                    .maxByOrNull { ChannelQuality.sportsRank(it.name) }
                val channel = epgChannel ?: broadcasterChannel(event, normalizedChannels)
                SportsEventUi(event = event, channel = channel)
            }
    }

    private fun teamTokens(team: String): TeamTokens {
        val normalized = normalize(team)
        val tokens = normalized.split(' ')
            .filter { it.length >= 4 && it !in GENERIC_TEAM_TOKENS }
        return TeamTokens(normalized, tokens)
    }

    private fun matchesTeams(text: String, home: TeamTokens, away: TeamTokens): Boolean =
        containsTeam(text, home) && containsTeam(text, away)

    private fun containsTeam(text: String, team: TeamTokens): Boolean {
        if (team.normalized.isBlank()) return false
        if (text.contains(team.normalized)) return true
        return team.tokens.any { text.contains(it) }
    }

    private fun broadcasterChannel(
        event: SportsEventRaw,
        normalizedChannels: List<Pair<ChannelEntity, String>>
    ): ChannelEntity? {
        val tokens = when {
            event.competition.contains("Champions", ignoreCase = true) -> listOf(
                "movistar liga de campeones",
                "movistar champions",
                "liga de campeones",
                "m liga de campeones",
                "movistar"
            )
            event.competition.contains("Europa", ignoreCase = true) -> listOf(
                "movistar",
                "dazn",
                "m deportes"
            )
            event.competition.contains("Copa", ignoreCase = true) ||
                event.competition.contains("Supercopa", ignoreCase = true) -> listOf(
                "movistar",
                "telecinco",
                "la 1",
                "dazn",
                "m deportes"
            )
            event.competition == "LaLiga" -> listOf(
                "movistar laliga",
                "movistar la liga",
                "dazn",
                "laliga",
                "m laliga"
            )
            else -> emptyList()
        }
        if (tokens.isEmpty()) return null
        return tokens.asSequence()
            .mapNotNull { token ->
                val normalizedToken = normalize(token)
                normalizedChannels
                    .filter { (_, name) -> name.contains(normalizedToken) }
                    .map { it.first }
                    .maxByOrNull { ChannelQuality.sportsRank(it.name) }
            }
            .firstOrNull()
    }

    private fun normalize(value: String): String {
        val text = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
        return text.replace(NON_ALPHANUMERIC, " ").trim()
    }

    private data class TeamTokens(val normalized: String, val tokens: List<String>)

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        const val EPG_ID_CHUNK = 400
        val DIACRITICS = Regex("\\p{M}+")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        val GENERIC_TEAM_TOKENS = setOf(
            "real", "club", "fc", "cf", "cd", "sd", "ud", "bc",
            "baloncesto", "basket", "de", "del", "la", "el"
        )
    }
}
