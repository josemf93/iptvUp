package com.iptvup.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "iptvup_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val activeProfileId: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[ACTIVE_PROFILE_ID]
    }

    val skippedVersionCode: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[SKIPPED_VERSION_CODE] ?: 0
    }

    val lastUpdateCheckMs: Flow<Long> = context.settingsDataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK] ?: 0L
    }

    val profileIcons: Flow<Map<String, String>> = context.settingsDataStore.data.map { preferences ->
        preferences.asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (name.startsWith(PROFILE_ICON_PREFIX) && value is String) {
                name.removePrefix(PROFILE_ICON_PREFIX) to value
            } else {
                null
            }
        }.toMap()
    }

    val syncOnOpen: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SYNC_ON_OPEN] ?: false
    }

    suspend fun lastEpgSyncMs(profileId: String): Long =
        context.settingsDataStore.data.first()[epgSyncedKey(profileId)] ?: 0L

    suspend fun setLastEpgSync(profileId: String, ms: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[epgSyncedKey(profileId)] = ms
        }
    }

    suspend fun setSyncOnOpen(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SYNC_ON_OPEN] = enabled
        }
    }

    fun groupOrder(profileId: String): Flow<List<String>> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[groupOrderKey(profileId)] ?: return@map emptyList()
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index)
                        if (value.isNotBlank()) add(value)
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun setGroupOrder(profileId: String, groups: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[groupOrderKey(profileId)] = JSONArray(groups).toString()
        }
    }

    suspend fun getActiveProfileId(): String? =
        context.settingsDataStore.data.first()[ACTIVE_PROFILE_ID]

    suspend fun setProfileIcon(profileId: String, iconKey: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[profileIconKey(profileId)] = iconKey
        }
    }

    suspend fun clearProfileIcon(profileId: String) {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(profileIconKey(profileId))
        }
    }

    suspend fun setActiveProfileId(profileId: String?) {
        context.settingsDataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(ACTIVE_PROFILE_ID)
            } else {
                preferences[ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    suspend fun setSkippedVersionCode(versionCode: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SKIPPED_VERSION_CODE] = versionCode
        }
    }

    suspend fun setLastUpdateCheck(ms: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK] = ms
        }
    }

    private fun profileIconKey(profileId: String) =
        stringPreferencesKey("$PROFILE_ICON_PREFIX$profileId")

    private fun epgSyncedKey(profileId: String) =
        longPreferencesKey("epg_synced_$profileId")

    private fun groupOrderKey(profileId: String) =
        stringPreferencesKey("group_order_$profileId")

    private companion object {
        const val PROFILE_ICON_PREFIX = "profile_icon_"
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val SKIPPED_VERSION_CODE = intPreferencesKey("skipped_version_code")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
        val SYNC_ON_OPEN = booleanPreferencesKey("sync_on_open")
    }
}
