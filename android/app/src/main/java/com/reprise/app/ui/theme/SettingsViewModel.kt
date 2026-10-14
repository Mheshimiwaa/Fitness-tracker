package com.reprise.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.local.SettingsStore
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the persisted theme preference to the UI.
 *
 * `stateIn` turns the DataStore [kotlinx.coroutines.flow.Flow] into a
 * [StateFlow] so Compose always has a current value to draw (SYSTEM, until the
 * saved value has been read). `WhileSubscribed` stops collecting when no screen
 * is watching, which is the recommended default.
 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(AppModule.settingsStore) }
        }
    }
}
