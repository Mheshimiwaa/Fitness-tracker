package com.reprise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reprise.app.navigation.RepRiseApp
import com.reprise.app.ui.theme.RepRiseTheme

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
            // RepRiseTheme wraps the whole UI so every screen shares the palette.
            RepRiseTheme {
                RepRiseApp()
            }
        }
    }
}
