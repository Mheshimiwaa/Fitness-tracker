package com.reprise.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reprise.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app's DataStore instance.
 *
 * DataStore is the modern replacement for SharedPreferences: it is asynchronous
 * (so it never blocks the UI thread) and it exposes values as a [Flow], which
 * Compose can collect and automatically recompose on.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reprise_settings"
)

/**
 * Persists small user preferences that are NOT secret — currently just the
 * theme choice. (The JWT lives in [TokenStore], which is encrypted.)
 */
class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    /**
     * Emits the saved theme choice, defaulting to [ThemeMode.SYSTEM] for a fresh
     * install or if the stored value is unrecognised.
     */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        val stored = preferences[themeModeKey]
        ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }
}
