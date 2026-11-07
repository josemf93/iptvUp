package com.iptvup.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    recentRepository: RecentRepository,
    private val sportsRepository: SportsRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val profileId: String = checkNotNull(savedStateHandle["profileId"])

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

    private val _sportsEvents = MutableStateFlow<List<SportsEventRaw>>(emptyList())
    val sportsEvents: StateFlow<List<SportsEventRaw>> = _sportsEvents.asStateFlow()

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
                _sportsEvents.value = sportsRepository.eventsForToday()
                    .sortedBy { it.startMs }
                    .take(MAX_EVENTS)
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

    private companion object {
        const val MAX_EVENTS = 20
    }
}
