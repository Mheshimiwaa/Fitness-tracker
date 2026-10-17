package com.reprise.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderTime
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
 * Persists small user preferences that are NOT secret — the theme choice and the
 * gym reminder. (The JWT lives in [TokenStore], which is encrypted.)
 */
class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    private val reminderEnabledKey = booleanPreferencesKey("reminder_enabled")
    private val reminderTimeKey = stringPreferencesKey("reminder_time")
    private val reminderDaysKey = stringSetPreferencesKey("reminder_days")

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

    /**
     * Emits the saved gym reminder.
     *
     * Every field falls back to a sensible default rather than to "off": an
     * unreadable time should not silently disable a reminder the user turned on,
     * so a fresh install simply means "disabled, every day, at 18:00".
     */
    val gymReminder: Flow<GymReminder> = context.settingsDataStore.data.map { preferences ->
        GymReminder(
            enabled = preferences[reminderEnabledKey] ?: false,
            time = parseStoredTime(preferences[reminderTimeKey]),
            days = parseStoredDays(preferences[reminderDaysKey]),
        )
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[reminderEnabledKey] = enabled
        }
    }

    suspend fun setReminderTime(time: ReminderTime) {
        context.settingsDataStore.edit { preferences ->
            preferences[reminderTimeKey] = time.toString()
        }
    }

    suspend fun setReminderDays(days: Set<ReminderDay>) {
        context.settingsDataStore.edit { preferences ->
            // Stored as ISO day numbers ("1".."7") so the value stays readable
            // and survives a future rename of the enum constants.
            preferences[reminderDaysKey] = days.map { it.isoNumber.toString() }.toSet()
        }
    }
}

/**
 * Read a stored "HH:mm", falling back to the default when it is missing or
 * malformed. Storage is not user input, so a bad value is a bug rather than
 * something to report — but it must never crash the app or disable the feature.
 */
private fun parseStoredTime(stored: String?): ReminderTime {
    val parts = stored?.split(":") ?: return ReminderTime.DEFAULT
    if (parts.size != 2) return ReminderTime.DEFAULT

    val hour = parts[0].toIntOrNull() ?: return ReminderTime.DEFAULT
    val minute = parts[1].toIntOrNull() ?: return ReminderTime.DEFAULT
    if (hour !in ReminderTime.HOUR_MIN..ReminderTime.HOUR_MAX) return ReminderTime.DEFAULT
    if (minute !in ReminderTime.MINUTE_MIN..ReminderTime.MINUTE_MAX) return ReminderTime.DEFAULT

    return ReminderTime(hour = hour, minute = minute)
}

/**
 * Read the stored set of ISO day numbers.
 *
 * An EMPTY stored set means "the user picked every day", which is also the
 * default: an empty set can never fire, so it is never a state worth keeping.
 */
private fun parseStoredDays(stored: Set<String>?): Set<ReminderDay> {
    if (stored == null) return ReminderDay.ALL

    val days = stored.mapNotNull { entry ->
        ReminderDay.fromIsoNumber(entry.toIntOrNull() ?: return@mapNotNull null)
    }.toSet()

    return days.ifEmpty { ReminderDay.ALL }
}
