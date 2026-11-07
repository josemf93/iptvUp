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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    channelDao: ChannelDao,
    private val favoritesRepository: FavoritesRepository,
    private val hiddenRepository: HiddenRepository
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    val channels: StateFlow<List<ChannelEntity>> = channelDao.observeFavorites(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            favoritesRepository.listen(profileId)
            hiddenRepository.listen(profileId)
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            favoritesRepository.toggle(profileId, channel.channelKey)
        }
    }
}
