package com.iptvup.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.iptvup.app.data.local.HiddenItemEntity
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.model.ProfileKind
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getUserStatus(uid: String): String? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return null
        return snapshot.getString("status") ?: "active"
    }

    suspend fun touchLastSeen(uid: String) {
        firestore.collection("users")
            .document(uid)
            .update("lastSeenAt", System.currentTimeMillis())
            .await()
    }

    suspend fun listProfiles(uid: String): List<IptvProfile> {
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("iptvProfiles")
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            val name = document.getString("name") ?: return@mapNotNull null
            IptvProfile(
                id = document.id,
                name = name,
                kind = ProfileKind.fromWire(document.getString("kind")),
                epgUrl = document.getString("epgUrl"),
                xtreamHost = document.getString("xtreamHost"),
                xtreamUsername = document.getString("xtreamUsername"),
                updatedAt = document.getLong("updatedAt") ?: 0L,
                lastSyncedAt = document.getLong("lastSyncedAt")
            )
        }
    }

    suspend fun deleteProfile(uid: String, profileId: String) {
        firestore.collection("users")
            .document(uid)
            .collection("iptvProfiles")
            .document(profileId)
            .delete()
            .await()
    }

    suspend fun getFavorites(uid: String, profileId: String): List<String> {
        val snapshot = favoritesRef(uid, profileId).get().await()
        return snapshot.documents.map { it.id }
    }

    suspend fun addFavorite(uid: String, profileId: String, channelKey: String) {
        favoritesRef(uid, profileId)
            .document(channelKey)
            .set(mapOf("createdAt" to System.currentTimeMillis()))
            .await()
    }

    suspend fun removeFavorite(uid: String, profileId: String, channelKey: String) {
        favoritesRef(uid, profileId)
            .document(channelKey)
            .delete()
            .await()
    }

    suspend fun getHidden(uid: String, profileId: String): List<HiddenItemEntity> {
        val snapshot = hiddenRef(uid, profileId).get().await()
        return snapshot.documents.mapNotNull { document ->
            val itemKey = document.getString("itemKey") ?: return@mapNotNull null
            val kind = document.getString("kind") ?: return@mapNotNull null
            HiddenItemEntity(
                id = "$profileId:$kind:$itemKey",
                profileId = profileId,
                kind = kind,
                itemKey = itemKey,
                createdAt = document.getLong("createdAt") ?: 0L
            )
        }
    }

    suspend fun addHidden(uid: String, profileId: String, kind: String, itemKey: String) {
        hiddenRef(uid, profileId)
            .document(hiddenDocumentId(kind, itemKey))
            .set(
                mapOf(
                    "kind" to kind,
                    "itemKey" to itemKey,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun removeHidden(uid: String, profileId: String, kind: String, itemKey: String) {
        hiddenRef(uid, profileId)
            .document(hiddenDocumentId(kind, itemKey))
            .delete()
            .await()
    }

    suspend fun setHiddenKind(
        uid: String,
        profileId: String,
        kind: String,
        itemKeys: Set<String>
    ) {
        val ref = hiddenRef(uid, profileId)
        val existing = ref.get().await().documents.filter { document ->
            document.getString("kind") == kind
        }
        val existingKeys = existing.mapNotNull { it.getString("itemKey") }.toSet()
        val operations = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, String?>>()
        existing.forEach { document ->
            if (document.getString("itemKey") !in itemKeys) {
                operations += document.reference to null
            }
        }
        (itemKeys - existingKeys).forEach { itemKey ->
            operations += ref.document(hiddenDocumentId(kind, itemKey)) to itemKey
        }
        operations.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (document, itemKey) ->
                if (itemKey == null) {
                    batch.delete(document)
                } else {
                    batch.set(
                        document,
                        mapOf(
                            "kind" to kind,
                            "itemKey" to itemKey,
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                }
            }
            batch.commit().await()
        }
    }

    private fun favoritesRef(uid: String, profileId: String) =
        firestore.collection("users")
            .document(uid)
            .collection("iptvProfiles")
            .document(profileId)
            .collection("favorites")

    private fun hiddenRef(uid: String, profileId: String) =
        firestore.collection("users")
            .document(uid)
            .collection("iptvProfiles")
            .document(profileId)
            .collection("hidden")

    private fun hiddenDocumentId(kind: String, itemKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$kind:$itemKey".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
