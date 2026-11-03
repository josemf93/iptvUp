package com.iptvup.app.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.repository.ProfileRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    private val updateManager: UpdateManager
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    init {
        // Primera entrada sin caché: descarga todo una vez.
        syncManager.syncIfEmpty(profileId)
    }

    val profile = flow {
        emit(profileRepository.getById(profileId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val updateState = updateManager.state

    fun checkUpdates(manual: Boolean = false) {
        viewModelScope.launch { updateManager.check(manual) }
    }

    fun installUpdate() {
        viewModelScope.launch { updateManager.downloadAndInstall() }
    }

    fun skipUpdate() {
        viewModelScope.launch { updateManager.skip() }
    }

    fun dismissUpdate() {
        updateManager.dismiss()
    }
}
