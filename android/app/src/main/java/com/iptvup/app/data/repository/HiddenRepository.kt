package com.iptvup.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.data.local.HiddenDao
import com.iptvup.app.data.local.HiddenItemEntity
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenRepository @Inject constructor(
    private val dao: HiddenDao,
    private val remote: ProfileRemoteDataSource,
    private val auth: FirebaseAuth
) {

    fun observe(profileId: String): Flow<List<HiddenItemEntity>> =
        dao.observeAll(profileId)

    suspend fun refresh(profileId: String) {
        val uid = auth.currentUser?.uid ?: return
        val items = remote.getHidden(uid, profileId)
        dao.deleteByProfile(profileId)
        items.forEach { item -> dao.insert(item) }
    }

    suspend fun toggleGroup(profileId: String, groupName: String) {
        toggle(profileId, KIND_GROUP, groupName)
    }

    suspend fun toggleChannel(profileId: String, channelKey: String) {
        toggle(profileId, KIND_CHANNEL, channelKey)
    }

    suspend fun setGroupsHidden(
        profileId: String,
        groups: List<String>,
        hidden: Boolean
    ) {
        val uid = auth.currentUser?.uid
        groups.forEach { group ->
            val exists = dao.exists(profileId, KIND_GROUP, group)
            if (exists == hidden) return@forEach

            if (hidden) {
                dao.insert(
                    HiddenItemEntity(
                        id = "$profileId:$KIND_GROUP:$group",
                        profileId = profileId,
                        kind = KIND_GROUP,
                        itemKey = group,
                        createdAt = System.currentTimeMillis()
                    )
                )
                if (uid != null) {
                    runCatching { remote.addHidden(uid, profileId, KIND_GROUP, group) }
                }
            } else {
                dao.delete(profileId, KIND_GROUP, group)
                if (uid != null) {
                    runCatching { remote.removeHidden(uid, profileId, KIND_GROUP, group) }
                }
            }
        }
    }

    private suspend fun toggle(profileId: String, kind: String, itemKey: String) {
        val exists = dao.exists(profileId, kind, itemKey)
        if (exists) {
            dao.delete(profileId, kind, itemKey)
        } else {
            dao.insert(
                HiddenItemEntity(
                    id = "$profileId:$kind:$itemKey",
                    profileId = profileId,
                    kind = kind,
                    itemKey = itemKey,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            if (exists) {
                remote.removeHidden(uid, profileId, kind, itemKey)
            } else {
                remote.addHidden(uid, profileId, kind, itemKey)
            }
        }
    }

    companion object {
        const val KIND_GROUP = "group"
        const val KIND_CHANNEL = "channel"
    }
}
