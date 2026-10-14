package com.reprise.app.domain.model

import androidx.annotation.StringRes
import com.reprise.app.R

/**
 * The user's theme preference.
 *
 * `labelRes` is the human-readable name shown in the Appearance setting; the
 * enum value itself (SYSTEM/LIGHT/DARK) is what gets persisted.
 */
enum class ThemeMode(@param:StringRes val labelRes: Int) {
    /** Follow the device's light/dark setting. */
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark);

    /** Resolve this preference into "should we draw dark colours?" */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}
