package com.iptvup.app.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.repository.AuthRepository
import com.iptvup.app.data.repository.PlaylistRepository
import com.iptvup.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val playlistRepository: PlaylistRepository,
    private val authRepository: AuthRepository,
    auth: FirebaseAuth
) : ViewModel() {

    val profiles = profileRepository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val email: String? = auth.currentUser?.email

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
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            pendingProfile = null
            status = "Seleccionando perfil..."
            try {
                profileRepository.selectProfile(profile.id)
                status = "Obteniendo credenciales..."
                val credentials = profileRepository.getCredentials(profile)
                playlistRepository.sync(profile, credentials) { message ->
                    status = message
                }.onSuccess {
                    navigateTo = profile.id
                }.onFailure {
                    pendingProfile = profile
                    error = "No se pudo actualizar el perfil: ${it.message ?: "error"}"
                }
            } catch (exception: Exception) {
                pendingProfile = profile
                error = "No se pudo actualizar el perfil: ${exception.message ?: "error"}"
            } finally {
                busy = false
            }
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
