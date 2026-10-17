package com.reprise.app.ui.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.local.SettingsStore
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderTime
import com.reprise.app.domain.model.ThemeMode
import com.reprise.app.domain.model.toggleReminderDay
import com.reprise.app.notifications.GymReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the persisted theme preference and gym reminder to the UI.
 *
 * `stateIn` turns the DataStore [kotlinx.coroutines.flow.Flow] into a
 * [StateFlow] so Compose always has a current value to draw (SYSTEM, until the
 * saved value has been read). `WhileSubscribed` stops collecting when no screen
 * is watching, which is the recommended default.
 *
 * This ViewModel also owns the LINK between the reminder setting and the
 * scheduled job: every change is written to DataStore and then handed to
 * [GymReminderScheduler]. Doing it here, rather than in the composable, means
 * there is one place that can leave the two disagreeing — and it keeps the
 * screen a plain function of its inputs.
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val appContext: Context,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ThemeMode.SYSTEM,
    )

    val gymReminder: StateFlow<GymReminder> = settingsStore.gymReminder.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = GymReminder(),
    )

    init {
        // Re-arm on launch. A job lives in WorkManager's own database and
        // normally survives both a reboot and an app update, but it is not
        // guaranteed to — for example, a force-stop clears pending work. Doing
        // this once per app start heals that, and is harmless otherwise because
        // [GymReminderScheduler.sync] replaces rather than duplicates.
        viewModelScope.launch {
            GymReminderScheduler.sync(appContext, settingsStore.gymReminder.first())
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setReminderEnabled(enabled)
            syncScheduledReminder()
        }
    }

    fun setReminderTime(time: ReminderTime) {
        viewModelScope.launch {
            settingsStore.setReminderTime(time)
            syncScheduledReminder()
        }
    }

    /** Add or remove one day, keeping at least one selected — see [toggleReminderDay]. */
    fun toggleReminderDay(day: ReminderDay) {
        viewModelScope.launch {
            val days = toggleReminderDay(settingsStore.gymReminder.first().days, day)
            settingsStore.setReminderDays(days)
            syncScheduledReminder()
        }
    }

    /**
     * Re-read the stored reminder and hand it to WorkManager.
     *
     * Reading it back (instead of passing the value the caller just wrote) means
     * the job is always scheduled from what is actually persisted, so a failed
     * or reordered write cannot leave the job and the setting disagreeing.
     */
    private suspend fun syncScheduledReminder() {
        GymReminderScheduler.sync(appContext, settingsStore.gymReminder.first())
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(AppModule.settingsStore, AppModule.appContext) }
        }
    }
}
