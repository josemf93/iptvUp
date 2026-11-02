package com.iptvup.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    suspend fun getActiveProfileId(): String? =
        context.settingsDataStore.data.first()[ACTIVE_PROFILE_ID]

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

    private companion object {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val SKIPPED_VERSION_CODE = intPreferencesKey("skipped_version_code")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
    }
}
