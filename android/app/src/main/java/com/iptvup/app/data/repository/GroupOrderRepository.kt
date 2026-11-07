package com.iptvup.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupOrderRepository @Inject constructor(
    private val remote: ProfileRemoteDataSource,
    private val auth: FirebaseAuth,
    private val settings: SettingsStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listening: Job? = null
    private var listeningProfile: String? = null

    fun observe(profileId: String): Flow<List<String>> =
        settings.groupOrder(profileId)

    /** Mantiene el orden de grupos local sincronizado con la nube mientras dure la sesión. */
    fun listen(profileId: String) {
        if (listeningProfile == profileId && listening?.isActive == true) return
        listening?.cancel()
        listeningProfile = profileId
        val uid = auth.currentUser?.uid ?: return
        listening = scope.launch {
            remote.observeGroupOrder(uid, profileId).collect { order ->
                settings.setGroupOrder(profileId, order)
            }
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
