package com.iptvup.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.data.local.FavoriteDao
import com.iptvup.app.data.local.FavoriteEntity
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
    private val remote: ProfileRemoteDataSource,
    private val auth: FirebaseAuth
) {

    fun observe(profileId: String): Flow<Set<String>> =
        dao.observeKeys(profileId).map { it.toSet() }

    suspend fun refresh(profileId: String) {
        val uid = auth.currentUser?.uid ?: return
        val keys = remote.getFavorites(uid, profileId)
        dao.deleteByProfile(profileId)
        val now = System.currentTimeMillis()
        keys.forEach { key ->
            dao.insert(
                FavoriteEntity(
                    id = "$profileId:$key",
                    profileId = profileId,
                    channelKey = key,
                    createdAt = now
                )
            )
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
        }
    }
}
