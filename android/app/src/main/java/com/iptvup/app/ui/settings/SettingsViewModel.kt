package com.iptvup.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.repository.AuthRepository
import com.iptvup.app.data.repository.ProfileRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.data.sync.SyncState
import com.iptvup.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val channelDao: ChannelDao,
    private val updateManager: UpdateManager,
    auth: FirebaseAuth
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])
    val email: String? = auth.currentUser?.email

    var profile by mutableStateOf<IptvProfile?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val channelCount = channelDao.observeAll(profileId)
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val updateState = updateManager.state

    init {
        viewModelScope.launch {
            profile = profileRepository.getById(profileId)
        }
        viewModelScope.launch {
            syncManager.state.collect { state ->
                when {
                    state is SyncState.Running && state.profileId == profileId -> {
                        busy = true
                        status = state.status
                        error = null
                    }

                    state is SyncState.Success && state.profileId == profileId -> {
                        busy = false
                        status = ""
                        profile = profileRepository.getById(profileId)
                    }

                    state is SyncState.Error && state.profileId == profileId -> {
                        busy = false
                        error = state.message
                    }
                }
            }
        }
    }

    fun sync() {
        syncManager.sync(profileId)
    }

    fun checkUpdates() {
        viewModelScope.launch {
            updateManager.check(manual = true)
        }
    }

    fun installUpdate() {
        viewModelScope.launch {
            updateManager.downloadAndInstall()
        }
    }

    fun switchProfile(onDone: () -> Unit) {
        viewModelScope.launch {
            profileRepository.clearActiveProfile()
            onDone()
        }
    }

    fun logout() {
        viewModelScope.launch {
            profileRepository.clearActiveProfile()
            authRepository.signOut()
        }
    }

    fun dismissError() {
        error = null
        syncManager.clearError(profileId)
    }
}
