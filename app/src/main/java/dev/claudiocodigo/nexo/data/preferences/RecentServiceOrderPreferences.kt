package dev.claudiocodigo.nexo.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface RecentServiceOrderPreferences {
    val recentTechnician: Flow<String?>
    val recentClient: Flow<String?>
    val recentUnit: Flow<String?>
    val recentCategory: Flow<String?>
    suspend fun saveRecentSelections(
        technician: String?,
        client: String?,
        unit: String?,
        category: String? = null
    )
}

private val Context.editorDataStore by preferencesDataStore(name = "nexo_editor_prefs")

@Singleton
class DataStoreRecentServiceOrderPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : RecentServiceOrderPreferences {
    private val keyRecentTechnician = stringPreferencesKey("recent_technician")
    private val keyRecentClient = stringPreferencesKey("recent_client")
    private val keyRecentUnit = stringPreferencesKey("recent_unit")
    private val keyRecentCategory = stringPreferencesKey("recent_category")

    override val recentTechnician: Flow<String?> = context.editorDataStore.data.map { it[keyRecentTechnician] }
    override val recentClient: Flow<String?> = context.editorDataStore.data.map { it[keyRecentClient] }
    override val recentUnit: Flow<String?> = context.editorDataStore.data.map { it[keyRecentUnit] }
    override val recentCategory: Flow<String?> = context.editorDataStore.data.map { it[keyRecentCategory] }

    override suspend fun saveRecentSelections(
        technician: String?,
        client: String?,
        unit: String?,
        category: String?
    ) {
        context.editorDataStore.edit { prefs ->
            technician?.takeIf { it.isNotBlank() }?.let { prefs[keyRecentTechnician] = it.trim() }
            client?.takeIf { it.isNotBlank() }?.let { prefs[keyRecentClient] = it.trim() }
            unit?.takeIf { it.isNotBlank() }?.let { prefs[keyRecentUnit] = it.trim() }
            category?.takeIf { it.isNotBlank() }?.let { prefs[keyRecentCategory] = it.trim() }
        }
    }
}
