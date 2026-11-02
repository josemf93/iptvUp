package com.iptvup.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.core.crypto.SecretStore
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.local.ProfileDao
import com.iptvup.app.data.local.ProfileEntity
import com.iptvup.app.data.local.toEntity
import com.iptvup.app.data.local.toModel
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.model.ProfileCredentials
import com.iptvup.app.data.model.ProfileKind
import com.iptvup.app.data.remote.ApiClient
import com.iptvup.app.data.remote.ProfileRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val remote: ProfileRemoteDataSource,
    private val api: ApiClient,
    private val settings: SettingsStore,
    private val secrets: SecretStore,
    private val auth: FirebaseAuth
) {

    val profiles: Flow<List<IptvProfile>> = profileDao.observeAll().map { entities ->
        entities.map(ProfileEntity::toModel)
    }

    val activeProfileId: Flow<String?> = settings.activeProfileId

    val activeProfile: Flow<IptvProfile?> = combine(profiles, activeProfileId) { list, id ->
        list.firstOrNull { it.id == id }
    }

    suspend fun refreshProfiles() {
        val uid = auth.currentUser?.uid ?: return
        val remoteProfiles = remote.listProfiles(uid)
        val localProfiles = profileDao.getAll().associateBy { it.id }
        profileDao.upsertAll(
            remoteProfiles.map { remoteProfile ->
                val localLastSync = localProfiles[remoteProfile.id]?.lastSyncedAt
                remoteProfile.toEntity().copy(
                    lastSyncedAt = localLastSync ?: remoteProfile.lastSyncedAt
                )
            }
        )
        val remoteIds = remoteProfiles.map { it.id }.toSet()
        profileDao.getAll()
            .filter { it.id !in remoteIds }
            .forEach { profileDao.deleteById(it.id) }
        val activeId = settings.getActiveProfileId()
        if (activeId != null && remoteProfiles.none { it.id == activeId }) {
            settings.setActiveProfileId(null)
        }
    }

    suspend fun getById(id: String): IptvProfile? =
        profileDao.getById(id)?.toModel()

    suspend fun createProfile(
        name: String,
        kind: ProfileKind,
        m3uUrl: String?,
        xtreamHost: String?,
        xtreamUsername: String?,
        xtreamPassword: String?,
        epgUrl: String?
    ) {
        api.createProfile(
            ApiClient.CreateProfileRequest(
                name = name.trim(),
                kind = kind.wire,
                m3uUrl = m3uUrl?.trim()?.takeIf { it.isNotBlank() },
                xtreamHost = xtreamHost?.trim()?.takeIf { it.isNotBlank() },
                xtreamUsername = xtreamUsername?.trim()?.takeIf { it.isNotBlank() },
                xtreamPassword = xtreamPassword?.takeIf { it.isNotBlank() },
                epgUrl = epgUrl?.trim()?.takeIf { it.isNotBlank() }
            )
        )
        refreshProfiles()
    }

    suspend fun updateProfile(
        profileId: String,
        name: String,
        kind: ProfileKind,
        m3uUrl: String?,
        xtreamHost: String?,
        xtreamUsername: String?,
        xtreamPassword: String?,
        epgUrl: String?
    ) {
        api.updateProfile(
            profileId,
            ApiClient.CreateProfileRequest(
                name = name.trim(),
                kind = kind.wire,
                m3uUrl = m3uUrl?.trim()?.takeIf { it.isNotBlank() },
                xtreamHost = xtreamHost?.trim()?.takeIf { it.isNotBlank() },
                xtreamUsername = xtreamUsername?.trim()?.takeIf { it.isNotBlank() },
                xtreamPassword = xtreamPassword?.takeIf { it.isNotBlank() },
                epgUrl = epgUrl?.trim()?.takeIf { it.isNotBlank() }
            )
        )
        secrets.remove(credentialsKey(profileId))
        refreshProfiles()
    }

    suspend fun deleteProfile(profile: IptvProfile) {
        val uid = auth.currentUser?.uid ?: return
        remote.deleteProfile(uid, profile.id)
        profileDao.deleteById(profile.id)
        secrets.remove(credentialsKey(profile.id))
        if (settings.getActiveProfileId() == profile.id) {
            settings.setActiveProfileId(null)
        }
    }

    suspend fun selectProfile(profileId: String) {
        settings.setActiveProfileId(profileId)
    }

    suspend fun clearActiveProfile() {
        settings.setActiveProfileId(null)
    }

    suspend fun getCredentials(profile: IptvProfile): ProfileCredentials {
        val key = credentialsKey(profile.id)
        val cached = secrets.get(key)?.let(::decodeCredentials)
        return try {
            val fresh = api.getCredentials(profile.id)
            secrets.put(key, encodeCredentials(fresh))
            fresh
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            cached ?: throw exception
        }
    }

    suspend fun invalidateCredentials(profileId: String) {
        secrets.remove(credentialsKey(profileId))
    }

    private fun credentialsKey(profileId: String) = "creds_$profileId"

    private fun encodeCredentials(credentials: ProfileCredentials): String =
        JSONObject().apply {
            credentials.m3uUrl?.let { put("m3uUrl", it) }
            credentials.xtreamHost?.let { put("xtreamHost", it) }
            credentials.xtreamUsername?.let { put("xtreamUsername", it) }
            credentials.xtreamPassword?.let { put("xtreamPassword", it) }
            credentials.epgUrl?.let { put("epgUrl", it) }
        }.toString()

    private fun decodeCredentials(raw: String): ProfileCredentials? = runCatching {
        val json = JSONObject(raw)
        ProfileCredentials(
            m3uUrl = json.optStringOrNull("m3uUrl"),
            xtreamHost = json.optStringOrNull("xtreamHost"),
            xtreamUsername = json.optStringOrNull("xtreamUsername"),
            xtreamPassword = json.optStringOrNull("xtreamPassword"),
            epgUrl = json.optStringOrNull("epgUrl")
        )
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() }
    }
}
