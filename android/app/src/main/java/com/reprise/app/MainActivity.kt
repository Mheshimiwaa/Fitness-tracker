package com.reprise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.navigation.RepRiseApp
import com.reprise.app.ui.theme.RepRiseTheme
import com.reprise.app.ui.theme.SettingsViewModel

/**
 * The app's only Activity.
 *
 * Compose apps use a single Activity whose content is a composable tree. Each
 * screen is a composable, not an Activity, which keeps navigation fast and the
 * manifest tiny.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status/navigation bars for a modern edge-to-edge look.
        enableEdgeToEdge()

        setContent {
            // The theme preference is read OUT here so the whole tree — including
            // the loading and auth screens — is drawn in the chosen theme.
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

            RepRiseTheme(
                darkTheme = themeMode.isDark(isSystemInDarkTheme()),
            ) {
                RepRiseApp()
            }
        }
    }
}
