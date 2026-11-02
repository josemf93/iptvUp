package com.iptvup.app.ui.favorites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.repository.FavoritesRepository
import com.iptvup.app.data.repository.HiddenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val favoritesRepository: FavoritesRepository,
    private val hiddenRepository: HiddenRepository
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val favorites = favoritesRepository.observe(profileId)
    private val hidden = hiddenRepository.observe(profileId)

    val channels: StateFlow<List<ChannelEntity>> = combine(
        channelDao.observeAll(profileId),
        favorites,
        hidden
    ) { all, favoriteKeys, hiddenItems ->
        val hiddenGroups = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
        val hiddenChannels = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_CHANNEL }
            .map { it.itemKey }
            .toSet()
        all.filter {
            it.channelKey in favoriteKeys &&
                it.groupName !in hiddenGroups &&
                it.channelKey !in hiddenChannels
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            runCatching { favoritesRepository.refresh(profileId) }
            runCatching { hiddenRepository.refresh(profileId) }
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            favoritesRepository.toggle(profileId, channel.channelKey)
        }
    }
}
