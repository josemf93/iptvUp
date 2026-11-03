package com.iptvup.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.EpgProgramEntity
import com.iptvup.app.data.local.toParsedProgram
import com.iptvup.app.data.local.dedupePrograms
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.model.EpgEntry
import com.iptvup.app.data.repository.FavoritesRepository
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.repository.RecentRepository
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.OkHttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val favoritesRepository: FavoritesRepository,
    private val hiddenRepository: HiddenRepository,
    private val recentRepository: RecentRepository,
    private val okHttp: OkHttpClient
) : ViewModel() {

    val channelId: String = checkNotNull(savedStateHandle["channelId"])

    fun createPlayer(context: Context): ExoPlayer = buildStreamPlayer(context, okHttp)

    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    val channel: StateFlow<ChannelEntity?> = _channel.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _epg = MutableStateFlow(EpgEntry())
    val epg: StateFlow<EpgEntry> = _epg.asStateFlow()

    private val _epgPrograms = MutableStateFlow<List<EpgProgramEntity>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgramEntity>> = _epgPrograms.asStateFlow()

    private val _canFallback = MutableStateFlow(false)
    val canFallback: StateFlow<Boolean> = _canFallback.asStateFlow()

    private var fallbackCandidates: List<ChannelEntity> = emptyList()
    private val attemptedFallbacks = mutableSetOf<String>()

    val favorite: StateFlow<Boolean> = _channel
        .filterNotNull()
        .flatMapLatest { channel ->
            favoritesRepository.observe(channel.profileId).map { keys ->
                channel.channelKey in keys
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            val channel = channelDao.getById(channelId)
            _channel.value = channel
            _loaded.value = true
            if (channel != null) {
                runCatching { recentRepository.record(channel) }
                prepareFallbacks(channel)
            }
            while (isActive) {
                refreshEpg()
                delay(60_000L)
            }
        }
    }

    fun toggleFavorite() {
        val channel = _channel.value ?: return
        viewModelScope.launch {
            favoritesRepository.toggle(channel.profileId, channel.channelKey)
        }
    }

    fun changeChannel(direction: Int) {
        val current = _channel.value ?: return
        viewModelScope.launch {
            val hiddenItems = hiddenRepository.observe(current.profileId).first()
            val hiddenGroups = hiddenItems
                .filter { it.kind == HiddenRepository.KIND_GROUP }
                .map { it.itemKey }
                .toSet()
            val groupChannels = channelDao
                .getForGroup(current.profileId, current.groupName)
                .filter {
                    it.groupName !in hiddenGroups
                }
            if (groupChannels.size <= 1) return@launch
            val currentIndex = groupChannels.indexOfFirst { it.id == current.id }
            if (currentIndex < 0) return@launch
            val nextIndex = (currentIndex + direction + groupChannels.size) %
                groupChannels.size
            val next = groupChannels[nextIndex]
            _channel.value = next
            _loaded.value = true
            prepareFallbacks(next)
            runCatching { recentRepository.record(next) }
            refreshEpg()
        }
    }

    fun fallbackToNext(): Boolean {
        val next = fallbackCandidates.firstOrNull {
            it.id !in attemptedFallbacks
        } ?: run {
            _canFallback.value = false
            return false
        }
        attemptedFallbacks += next.id
        _channel.value = next
        _loaded.value = true
        _canFallback.value = fallbackCandidates.any { it.id !in attemptedFallbacks }
        viewModelScope.launch {
            runCatching { recentRepository.record(next) }
            refreshEpg()
        }
        return true
    }

    private fun prepareFallbacks(channel: ChannelEntity) {
        viewModelScope.launch {
            fallbackCandidates = emptyList()
            attemptedFallbacks.clear()
            _canFallback.value = false

            val baseName = ChannelQuality.baseName(channel.name)
                .trim()
                .lowercase()
            if (baseName.isBlank()) return@launch

            fallbackCandidates = channelDao.getVisible(channel.profileId)
                .asSequence()
                .filter { it.id != channel.id }
                .filter { it.streamUrl != channel.streamUrl }
                .filter { ChannelQuality.label(it.name) != "8K" }
                .filter {
                    ChannelQuality.baseName(it.name).trim().lowercase() == baseName
                }
                .sortedWith(
                    compareByDescending<ChannelEntity> {
                        it.groupName == channel.groupName
                    }.thenByDescending {
                        ChannelQuality.sportsRank(it.name)
                    }
                )
                .toList()
            attemptedFallbacks += channel.id
            _canFallback.value = fallbackCandidates.isNotEmpty()
        }
    }

    private suspend fun refreshEpg() {
        val channel = _channel.value ?: return
        val epgId = channel.epgChannelId
        if (epgId == null) {
            _epg.value = EpgEntry()
            _epgPrograms.value = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        val programs = epgDao.getForChannel(
            profileId = channel.profileId,
            epgId = epgId,
            from = now - 2 * 60 * 60 * 1000L,
            to = now + 24 * 60 * 60 * 1000L
        ).dedupePrograms()
        _epgPrograms.value = programs
        _epg.value = EpgEntry(
            now = programs.lastOrNull { it.startMs <= now && it.endMs > now }
                ?.toParsedProgram(),
            next = programs.firstOrNull { it.startMs > now }?.toParsedProgram()
        )
    }
}
