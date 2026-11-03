package com.iptvup.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupOrderRepository @Inject constructor(
    private val remote: ProfileRemoteDataSource,
    private val auth: FirebaseAuth,
    private val settings: SettingsStore
) {

    fun observe(profileId: String): Flow<List<String>> =
        settings.groupOrder(profileId)

    suspend fun refresh(profileId: String) {
        val uid = auth.currentUser?.uid ?: return
        val remoteOrder = runCatching {
            remote.getGroupOrder(uid, profileId)
        }.getOrNull().orEmpty()
        if (remoteOrder.isNotEmpty()) {
            settings.setGroupOrder(profileId, remoteOrder)
        }
    }

    suspend fun save(profileId: String, groups: List<String>) {
        settings.setGroupOrder(profileId, groups)
        val uid = auth.currentUser?.uid ?: return
        runCatching { remote.setGroupOrder(uid, profileId, groups) }
    }

    fun sortGroups(groups: List<String>, order: List<String>): List<String> {
        if (order.isEmpty()) return groups
        val positions = order.withIndex().associate { it.value to it.index }
        return groups.sortedWith(
            compareBy({ positions[it] ?: Int.MAX_VALUE }, { it.lowercase() })
        )
    }
}
