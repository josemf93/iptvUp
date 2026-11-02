package com.iptvup.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.repository.AuthRepository
import com.iptvup.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val user = authRepository.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { currentUser ->
                if (currentUser == null) {
                    profileRepository.clearActiveProfile()
                } else {
                    runCatching { profileRepository.refreshProfiles() }
                }
            }
        }
    }

    fun logout() {
        authRepository.signOut()
    }
}
