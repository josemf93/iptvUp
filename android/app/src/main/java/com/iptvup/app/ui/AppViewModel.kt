package com.iptvup.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.repository.AuthRepository
import com.iptvup.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppStartState {
    data object Loading : AppStartState
    data class Ready(val profileId: String?) : AppStartState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val user = authRepository.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val _startState = MutableStateFlow<AppStartState>(AppStartState.Loading)
    val startState: StateFlow<AppStartState> = _startState.asStateFlow()

    private val _authReady = MutableStateFlow(false)
    val authReady: StateFlow<Boolean> = _authReady.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { currentUser ->
                _authReady.value = true
                if (currentUser == null) {
                    _startState.value = AppStartState.Ready(null)
                } else {
                    launch { authRepository.markSeen() }
                    _startState.value = AppStartState.Loading
                    val activeId = settings.getActiveProfileId()
                    val localProfile = activeId?.let { profileRepository.getById(it) }
                    _startState.value = AppStartState.Ready(
                        if (localProfile != null) activeId else null
                    )
                    runCatching { profileRepository.refreshProfiles() }
                }
            }
        }
    }

    fun logout() {
        authRepository.signOut()
    }
}
