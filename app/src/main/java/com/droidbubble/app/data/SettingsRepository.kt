package com.droidbubble.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.droidbubble.app.model.IslandSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "island_settings")

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<IslandSettings> = context.dataStore.data.map { preferences ->
        readIslandSettings(preferences)
    }

    suspend fun updateSettings(update: (IslandSettings) -> IslandSettings) {
        context.dataStore.edit { preferences ->
            writeIslandSettings(preferences, update(readIslandSettings(preferences)))
        }
    }
}
