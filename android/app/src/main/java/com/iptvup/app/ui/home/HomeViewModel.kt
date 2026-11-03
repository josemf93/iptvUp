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
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.repository.RecentRepository
import com.iptvup.app.data.sports.SportsEventRaw
import com.iptvup.app.data.sports.SportsRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val hiddenRepository: HiddenRepository,
    recentRepository: RecentRepository,
    private val sportsRepository: SportsRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val profileId: String = checkNotNull(savedStateHandle["profileId"])

    val channelCount = channelDao.observeAll(profileId)
        .map { it.size }
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
                val hiddenGroups = runCatching {
                    hiddenRepository.getHiddenGroups(profileId)
                }.getOrDefault(emptySet())
                val channels = channelDao.observeAll(profileId).first()
                    .filter {
                        it.groupName !in hiddenGroups &&
                            !ChannelQuality.isJunkChannel(it.name)
                    }
                val now = System.currentTimeMillis()
                val programs = epgDao.getWindow(
                    profileId = profileId,
                    from = now - 6 * 60 * 60 * 1000L,
                    to = now + 36 * 60 * 60 * 1000L
                )
                _sportsEvents.value = raw
                    .map { event -> matchEvent(event, channels, programs) }
                    .sortedBy { it.event.startMs }
                    .take(8)
            } catch (_: Exception) {
                _sportsEvents.value = emptyList()
            } finally {
                _sportsLoading.value = false
            }
        }
    }

    fun syncFiltered() {
        syncManager.sync(profileId, force = true, fullSync = false)
    }

    fun syncAll() {
        syncManager.sync(profileId, force = true, fullSync = true)
    }

    private fun matchEvent(
        event: SportsEventRaw,
        channels: List<ChannelEntity>,
        programs: List<EpgProgramEntity>
    ): SportsEventUi {
        val eventStart = event.startMs
        val epgMatches = programs.filter { program ->
            val text = "${program.title} ${program.description.orEmpty()}"
            program.endMs >= eventStart - 2 * 60 * 60 * 1000L &&
                program.startMs <= eventStart + 3 * 60 * 60 * 1000L &&
                containsTeam(text, event.homeTeam) &&
                containsTeam(text, event.awayTeam)
        }
        val epgChannel = epgMatches
            .flatMap { program ->
                channels.filter { it.epgChannelId == program.epgChannelId }
            }
            .maxByOrNull { ChannelQuality.sportsRank(it.name) }
        val channel = epgChannel ?: broadcasterChannel(event, channels)
        return SportsEventUi(event = event, channel = channel)
    }

    private fun containsTeam(text: String, team: String): Boolean {
        val normalizedText = normalize(text)
        val normalizedTeam = normalize(team)
        if (normalizedTeam.isBlank()) return false
        if (normalizedText.contains(normalizedTeam)) return true
        return normalizedTeam.split(' ')
            .filter { it.length >= 4 && it !in GENERIC_TEAM_TOKENS }
            .any { normalizedText.contains(it) }
    }

    private fun broadcasterChannel(
        event: SportsEventRaw,
        channels: List<ChannelEntity>
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
        val normalizedChannels = channels.map { channel -> channel to normalize(channel.name) }
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

    private companion object {
        val DIACRITICS = Regex("\\p{M}+")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        val GENERIC_TEAM_TOKENS = setOf(
            "real", "club", "fc", "cf", "cd", "sd", "ud", "bc",
            "baloncesto", "basket", "de", "del", "la", "el"
        )
    }
}
