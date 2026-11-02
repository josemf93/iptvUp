package com.iptvup.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.iptvup.app.data.repository.AuthRepository
import com.iptvup.app.data.repository.AuthRepository.UnauthorizedAccountException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun signIn() {
        if (loading) return
        error = null
        if (email.isBlank() || password.isBlank()) {
            error = "Introduce tu email y tu contraseña"
            return
        }
        viewModelScope.launch {
            loading = true
            authRepository.signIn(email, password)
                .onFailure { error = messageFor(it) }
            loading = false
        }
    }

    private fun messageFor(exception: Throwable): String = when (exception) {
        is UnauthorizedAccountException -> exception.message
            ?: "Tu cuenta no está autorizada"
        is FirebaseAuthInvalidCredentialsException -> "Email o contraseña incorrectos"
        is FirebaseAuthInvalidUserException -> "No existe ninguna cuenta con ese email"
        is FirebaseNetworkException -> "Sin conexión a internet"
        else -> exception.message ?: "No se pudo iniciar sesión"
    }
}
