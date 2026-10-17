package com.reprise.app.ui.theme

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.R
import com.reprise.app.data.audio.ReminderSoundImporter
import com.reprise.app.data.local.SettingsStore
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderSound
import com.reprise.app.domain.model.ReminderTime
import com.reprise.app.domain.model.ThemeMode
import com.reprise.app.domain.model.toggleReminderDay
import com.reprise.app.notifications.GymReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Whether "add a sound" is working, and why it failed if it did.
     *
     * Importing is the one reminder setting that can fail for a reason the user
     * needs to know about (the file was unreadable, or too big to be a sensible
     * notification sound), so the card has something to show besides the choice.
     */
    private val _soundImport = MutableStateFlow<ReminderSoundImport>(ReminderSoundImport.Idle)
    val soundImport: StateFlow<ReminderSoundImport> = _soundImport.asStateFlow()

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
     * Choose what the reminder sounds like.
     *
     * No job re-arm is needed — the sound is read when the reminder fires, not
     * when it is scheduled — but a change of sound does mean the next reminder
     * will be posted on a different notification channel, which the notification
     * code works out for itself.
     */
    fun setReminderSound(sound: ReminderSound) {
        _soundImport.value = ReminderSoundImport.Idle
        viewModelScope.launch { settingsStore.setReminderSound(sound) }
    }

    /**
     * Copy a sound the user picked into the phone's sound library, then select it.
     *
     * The copy is the reason this is not simply a stored preference: Android plays
     * notification sounds, and it cannot read a file that belongs to this app, so
     * the picked file has to be imported before it can be used at all. See
     * [ReminderSoundImporter].
     */
    fun importReminderSound(source: Uri) {
        if (_soundImport.value is ReminderSoundImport.Working) return

        _soundImport.value = ReminderSoundImport.Working
        viewModelScope.launch {
            _soundImport.value = when (val result = ReminderSoundImporter.import(appContext, source)) {
                is ReminderSoundImporter.Result.Imported -> {
                    settingsStore.setReminderSound(
                        ReminderSound.Clip(uri = result.uri, name = result.name)
                    )
                    ReminderSoundImport.Idle
                }

                ReminderSoundImporter.Result.TooLarge ->
                    ReminderSoundImport.Failed(R.string.profile_reminder_sound_too_large)

                ReminderSoundImporter.Result.Unreadable ->
                    ReminderSoundImport.Failed(R.string.profile_reminder_sound_unreadable)
            }
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

/**
 * What the "add a sound" flow is doing, so the card can show progress and
 * explain a failure.
 *
 * A failure carries a string RESOURCE rather than ready-made text, because the
 * explanation is written for the user in their language and belongs in
 * `strings.xml` with every other sentence the app says — not in a ViewModel.
 */
sealed interface ReminderSoundImport {
    data object Idle : ReminderSoundImport

    data object Working : ReminderSoundImport

    /** @param messageRes why the sound could not be added. */
    data class Failed(@param:StringRes val messageRes: Int) : ReminderSoundImport
}
