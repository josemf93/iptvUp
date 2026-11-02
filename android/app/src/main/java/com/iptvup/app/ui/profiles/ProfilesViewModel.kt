package com.iptvup.app.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.repository.AuthRepository
import com.iptvup.app.data.repository.ProfileRepository
import com.iptvup.app.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    val profiles = profileRepository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pendingProfile by mutableStateOf<IptvProfile?>(null)
        private set
    var navigateTo by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { profileRepository.refreshProfiles() }
                .onFailure {
                    error = "No se pudieron cargar los perfiles: ${it.message ?: "error de red"}"
                }
        }
    }

    fun open(profile: IptvProfile) {
        viewModelScope.launch {
            error = null
            profileRepository.selectProfile(profile.id)
            // Entra inmediatamente: la descarga sigue en segundo plano.
            syncManager.sync(profile.id)
            navigateTo = profile.id
        }
    }

    fun enterCached() {
        val profile = pendingProfile ?: return
        pendingProfile = null
        error = null
        navigateTo = profile.id
    }

    fun retry() {
        val profile = pendingProfile
        if (profile != null) {
            open(profile)
        } else {
            error = null
            refresh()
        }
    }

    fun dismissError() {
        error = null
        pendingProfile = null
    }

    fun consumeNavigation() {
        navigateTo = null
    }

    fun delete(profile: IptvProfile) {
        viewModelScope.launch {
            runCatching { profileRepository.deleteProfile(profile) }
                .onFailure {
                    error = "No se pudo borrar el perfil: ${it.message ?: "error"}"
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            profileRepository.clearActiveProfile()
            authRepository.signOut()
        }
    }
}
