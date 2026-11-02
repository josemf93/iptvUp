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
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.repository.PlaylistRepository
import com.iptvup.app.data.repository.ProfileRepository
import com.iptvup.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HiddenUi(
    val groups: List<String>,
    val channels: List<Pair<String, String>>
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val playlistRepository: PlaylistRepository,
    private val hiddenRepository: HiddenRepository,
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

    val hiddenUi = hiddenRepository.observe(profileId)
        .map { items ->
            val groups = items
                .filter { it.kind == HiddenRepository.KIND_GROUP }
                .map { it.itemKey }
            val channels = items
                .filter { it.kind == HiddenRepository.KIND_CHANNEL }
                .map { item ->
                    val name = channelDao.getByKey(profileId, item.itemKey)?.name
                        ?: item.itemKey
                    item.itemKey to name
                }
            HiddenUi(groups = groups, channels = channels)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HiddenUi(emptyList(), emptyList())
        )

    init {
        viewModelScope.launch {
            profile = profileRepository.getById(profileId)
            runCatching { hiddenRepository.refresh(profileId) }
        }
    }

    fun sync() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            status = "Preparando..."
            val current = profile ?: profileRepository.getById(profileId)
            if (current == null) {
                error = "No se encontró el perfil"
                busy = false
                return@launch
            }
            try {
                val credentials = profileRepository.getCredentials(current)
                playlistRepository.sync(current, credentials) { message ->
                    status = message
                }.onSuccess {
                    profile = profileRepository.getById(profileId)
                }.onFailure {
                    error = it.message ?: "No se pudo actualizar el perfil"
                }
            } catch (exception: Exception) {
                error = exception.message ?: "No se pudo actualizar el perfil"
            }
            busy = false
        }
    }

    fun restoreGroup(group: String) {
        viewModelScope.launch {
            hiddenRepository.toggleGroup(profileId, group)
        }
    }

    fun restoreChannel(channelKey: String) {
        viewModelScope.launch {
            hiddenRepository.toggleChannel(profileId, channelKey)
        }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            updateManager.check(manual = true)
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
    }
}
