package com.iptvup.app.data.sync

import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.repository.PlaylistRepository
import com.iptvup.app.data.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val profileId: String, val status: String) : SyncState
    data class Success(val profileId: String, val syncedAt: Long) : SyncState
    data class Error(val profileId: String, val message: String) : SyncState
}

@Singleton
class SyncManager @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val playlistRepository: PlaylistRepository,
    private val hiddenRepository: HiddenRepository,
    private val channelDao: ChannelDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var job: Job? = null
    private var activeProfileId: String? = null

    fun sync(profileId: String, force: Boolean = false, fullSync: Boolean = false) {
        if (job?.isActive == true && activeProfileId == profileId) return

        job?.cancel()
        activeProfileId = profileId
        job = scope.launch {
            val profile = profileRepository.getById(profileId)
            if (profile == null) {
                _state.value = SyncState.Error(profileId, "Perfil no encontrado")
                return@launch
            }

            runCatching { hiddenRepository.refresh(profileId) }
            val hiddenGroups = hiddenRepository.getHiddenGroups(profileId)
            if (!fullSync && hiddenGroups.isNotEmpty()) {
                // Los grupos ocultos no se guardan: así el arranque carga solo lo visible.
                hiddenGroups.chunked(400).forEach { chunk ->
                    channelDao.deleteByGroups(profileId, chunk)
                }
            }

            val lastSync = profile.lastSyncedAt
            if (!force && lastSync != null &&
                System.currentTimeMillis() - lastSync < DAY_MS
            ) {
                // Ya se actualizó hoy: se usa la caché y no se vuelve a descargar.
                _state.value = SyncState.Idle
                return@launch
            }

            _state.value = SyncState.Running(profileId, "Preparando...")
            try {
                val credentials = profileRepository.getCredentials(profile)
                playlistRepository.sync(
                    profile = profile,
                    credentials = credentials,
                    hiddenGroups = hiddenGroups,
                    fullSync = fullSync
                ) { status ->
                    _state.value = SyncState.Running(profileId, status)
                }.onSuccess {
                    _state.value = SyncState.Success(profileId, System.currentTimeMillis())
                }.onFailure {
                    _state.value = SyncState.Error(
                        profileId,
                        it.message ?: "No se pudo actualizar el perfil"
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = SyncState.Error(
                    profileId,
                    exception.message ?: "No se pudo actualizar el perfil"
                )
            }
        }
    }

    fun clearError(profileId: String) {
        val current = _state.value
        if (current is SyncState.Error && current.profileId == profileId) {
            _state.value = SyncState.Idle
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
