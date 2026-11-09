package com.iptvup.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val remote: ProfileRemoteDataSource
) {

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = try {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = auth.currentUser ?: error("No se pudo iniciar sesión")
        val status = remote.getUserStatus(user.uid)
        if (status == null || status == "disabled") {
            auth.signOut()
            Result.failure(UnauthorizedAccountException())
        } else {
            runCatching { remote.touchLastSeen(user.uid) }
            Result.success(Unit)
        }
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    /** Marca la actividad de la cuenta; se llama al abrir la app con sesión iniciada. */
    suspend fun markSeen() {
        auth.currentUser?.uid?.let { uid -> runCatching { remote.touchLastSeen(uid) } }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Result.success(Unit)
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    fun signOut() {
        auth.signOut()
    }

    class UnauthorizedAccountException : Exception(
        "Tu cuenta no está autorizada. Pide acceso al administrador."
    )
}
