package com.iptvup.app.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.model.ProfileKind
import com.iptvup.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val profileId: String? = savedStateHandle["profileId"]
    val isEditing: Boolean = profileId != null

    var name by mutableStateOf("")
    var kind by mutableStateOf(ProfileKind.M3U)
    var m3uUrl by mutableStateOf("")
    var xtreamHost by mutableStateOf("")
    var xtreamUsername by mutableStateOf("")
    var xtreamPassword by mutableStateOf("")
    var epgUrl by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var initialLoading by mutableStateOf(isEditing)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        profileId?.let(::loadProfile)
    }

    fun save(onSaved: () -> Unit) {
        error = null
        val validation = validate()
        if (validation != null) {
            error = validation
            return
        }
        viewModelScope.launch {
            loading = true
            runCatching {
                if (profileId != null) {
                    profileRepository.updateProfile(
                        profileId = profileId,
                        name = name,
                        kind = kind,
                        m3uUrl = m3uUrl.takeIf { kind == ProfileKind.M3U },
                        xtreamHost = xtreamHost.takeIf { kind == ProfileKind.XTREAM },
                        xtreamUsername = xtreamUsername.takeIf { kind == ProfileKind.XTREAM },
                        xtreamPassword = xtreamPassword.takeIf { kind == ProfileKind.XTREAM },
                        epgUrl = epgUrl
                    )
                } else {
                    profileRepository.createProfile(
                        name = name,
                        kind = kind,
                        m3uUrl = m3uUrl.takeIf { kind == ProfileKind.M3U },
                        xtreamHost = xtreamHost.takeIf { kind == ProfileKind.XTREAM },
                        xtreamUsername = xtreamUsername.takeIf { kind == ProfileKind.XTREAM },
                        xtreamPassword = xtreamPassword.takeIf { kind == ProfileKind.XTREAM },
                        epgUrl = epgUrl
                    )
                }
            }.onSuccess {
                onSaved()
            }.onFailure {
                error = if (profileId != null) {
                    "No se pudo guardar el perfil: ${it.message ?: "error"}"
                } else {
                    "No se pudo crear el perfil: ${it.message ?: "error"}"
                }
            }
            loading = false
        }
    }

    fun clearError() {
        error = null
    }

    private fun loadProfile(profileId: String) {
        viewModelScope.launch {
            initialLoading = true
            val profile = profileRepository.getById(profileId)
            if (profile == null) {
                error = "No se encontró el perfil"
                initialLoading = false
                return@launch
            }
            name = profile.name
            kind = profile.kind
            val credentials = runCatching {
                profileRepository.getCredentials(profile)
            }.getOrNull()
            if (credentials == null) {
                error = "No se pudieron cargar las credenciales del perfil"
                initialLoading = false
                return@launch
            }
            m3uUrl = credentials.m3uUrl.orEmpty()
            xtreamHost = credentials.xtreamHost ?: profile.xtreamHost.orEmpty()
            xtreamUsername = credentials.xtreamUsername ?: profile.xtreamUsername.orEmpty()
            xtreamPassword = credentials.xtreamPassword.orEmpty()
            epgUrl = credentials.epgUrl.orEmpty()
            initialLoading = false
        }
    }

    private fun validate(): String? {
        if (name.isBlank()) return "Ponle un nombre al perfil"
        if (kind == ProfileKind.M3U) {
            if (m3uUrl.isBlank()) return "Introduce la URL de la lista M3U"
            if (!m3uUrl.trim().startsWith("http://") &&
                !m3uUrl.trim().startsWith("https://")
            ) {
                return "La URL M3U debe empezar por http:// o https://"
            }
        } else {
            if (xtreamHost.isBlank()) return "Introduce el host de Xtream Codes"
            if (xtreamUsername.isBlank()) return "Introduce el usuario de Xtream Codes"
            if (xtreamPassword.isBlank()) return "Introduce la contraseña de Xtream Codes"
        }
        if (epgUrl.isNotBlank() &&
            !epgUrl.trim().startsWith("http://") &&
            !epgUrl.trim().startsWith("https://")
        ) {
            return "La URL del EPG debe empezar por http:// o https://"
        }
        return null
    }
}
