package com.iptvup.app.data.repository

import com.iptvup.app.core.diag.DiagLog

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.data.local.FavoriteDao
import com.iptvup.app.data.local.FavoriteEntity
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
    private val remote: ProfileRemoteDataSource,
    private val auth: FirebaseAuth
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listening: Job? = null
    private var listeningProfile: String? = null

    fun observe(profileId: String): Flow<Set<String>> =
        dao.observeKeys(profileId).map { it.toSet() }

    /** Mantiene los favoritos locales sincronizados con la nube mientras dure la sesión. */
    fun listen(profileId: String) {
        if (listeningProfile == profileId && listening?.isActive == true) return
        listening?.cancel()
        listeningProfile = profileId
        val uid = auth.currentUser?.uid ?: return
        listening = scope.launch {
            remote.observeFavorites(uid, profileId).collect { keys ->
                val now = System.currentTimeMillis()
                dao.replaceForProfile(
                    profileId,
                    keys.map { key ->
                        FavoriteEntity(
                            id = "$profileId:$key",
                            profileId = profileId,
                            channelKey = key,
                            createdAt = now
                        )
                    }
                )
            }
        }
    }

    suspend fun toggle(profileId: String, channelKey: String) {
        val exists = dao.exists(profileId, channelKey)
        if (exists) {
            dao.delete(profileId, channelKey)
        } else {
            dao.insert(
                FavoriteEntity(
                    id = "$profileId:$channelKey",
                    profileId = profileId,
                    channelKey = channelKey,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            if (exists) {
                remote.removeFavorite(uid, profileId, channelKey)
            } else {
                remote.addFavorite(uid, profileId, channelKey)
            }
        }.onFailure { DiagLog.logError("No se pudo guardar un favorito en la nube", it) }
    }
}
