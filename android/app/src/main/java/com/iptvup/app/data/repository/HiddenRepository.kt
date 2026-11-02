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
        val items = remote.getHidden(uid, profileId).filter { it.kind == KIND_GROUP }
        dao.deleteByProfile(profileId)
        items.forEach { item -> dao.insert(item) }
    }

    suspend fun getHiddenGroups(profileId: String): Set<String> =
        dao.keys(profileId, KIND_GROUP).toSet()

    suspend fun toggleGroup(profileId: String, groupName: String) {
        toggle(profileId, KIND_GROUP, groupName)
    }

    suspend fun setGroupsHidden(
        profileId: String,
        groups: List<String>,
        hidden: Boolean
    ) {
        val target = if (hidden) groups.toSet() else emptySet()
        val items = target.map { group ->
            HiddenItemEntity(
                id = "$profileId:$KIND_GROUP:$group",
                profileId = profileId,
                kind = KIND_GROUP,
                itemKey = group,
                createdAt = System.currentTimeMillis()
            )
        }
        // Una sola transacción: la UI cambia todos los switches de golpe.
        dao.replaceKind(profileId, KIND_GROUP, items)

        val uid = auth.currentUser?.uid ?: return
        runCatching {
            remote.setHiddenKind(uid, profileId, KIND_GROUP, target)
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
    }
}
