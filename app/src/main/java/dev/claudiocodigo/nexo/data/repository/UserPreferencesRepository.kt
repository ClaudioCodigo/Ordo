package dev.claudiocodigo.nexo.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val NEXTCLOUD_URL = stringPreferencesKey("nextcloud_url")
        val NEXTCLOUD_USER = stringPreferencesKey("nextcloud_user")
    }

    val nextcloudUrl: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NEXTCLOUD_URL]
    }

    suspend fun updateNextcloudUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NEXTCLOUD_URL] = url
        }
    }
}
