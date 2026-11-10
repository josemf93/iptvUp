package com.iptvup.app.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.repository.ProfileRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val EPG_CHECK_INTERVAL_MS = 60 * 60 * 1000L

@HiltViewModel
class MainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    private val updateManager: UpdateManager,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    init {
        viewModelScope.launch {
            if (settingsStore.syncOnOpen.first()) {
                syncManager.sync(profileId)
            } else {
                syncManager.checkOnOpen(profileId)
            }
        }
        // Mientras la pantalla está abierta, revisa la EPG cada hora (solo la EPG, nunca los canales).
        viewModelScope.launch {
            while (true) {
                delay(EPG_CHECK_INTERVAL_MS)
                syncManager.checkOnOpen(profileId)
            }
        }
    }

    /** Se llama al volver a la app: comprueba si la EPG está al día. */
    fun onResumed() {
        viewModelScope.launch { syncManager.checkOnOpen(profileId) }
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
