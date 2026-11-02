package com.iptvup.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.toParsedProgram
import com.iptvup.app.data.model.EpgEntry
import com.iptvup.app.data.repository.FavoritesRepository
import com.iptvup.app.data.repository.RecentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
    private val recentRepository: RecentRepository
) : ViewModel() {

    val channelId: String = checkNotNull(savedStateHandle["channelId"])

    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    val channel: StateFlow<ChannelEntity?> = _channel.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _epg = MutableStateFlow(EpgEntry())
    val epg: StateFlow<EpgEntry> = _epg.asStateFlow()

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

    private suspend fun refreshEpg() {
        val channel = _channel.value ?: return
        val epgId = channel.epgChannelId ?: return
        val now = System.currentTimeMillis()
        val nowProgram = epgDao.getNow(channel.profileId, epgId, now)
        val nextProgram = epgDao.getNext(channel.profileId, epgId, now)
        _epg.value = EpgEntry(
            now = nowProgram?.toParsedProgram(),
            next = nextProgram?.toParsedProgram()
        )
    }
}
