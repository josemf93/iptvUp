package com.iptvup.app.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.core.settings.SettingsStore
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
    private val authRepository: AuthRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val profiles = profileRepository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val profileIcons = settings.profileIcons.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    var error by mutableStateOf<String?>(null)
        private set
    var navigateTo by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            error = null
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
            // Entra inmediatamente: si toca actualizar, sigue en segundo plano.
            syncManager.sync(profile.id)
            navigateTo = profile.id
        }
    }

    fun setProfileIcon(profileId: String, iconKey: String) {
        viewModelScope.launch {
            settings.setProfileIcon(profileId, iconKey)
        }
    }

    fun dismissError() {
        error = null
    }

    fun consumeNavigation() {
        navigateTo = null
    }

    fun delete(profile: IptvProfile) {
        viewModelScope.launch {
            runCatching { profileRepository.deleteProfile(profile) }
                .onSuccess { settings.clearProfileIcon(profile.id) }
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
