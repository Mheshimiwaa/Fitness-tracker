package com.reprise.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Maps the RepRise palette onto Material 3 colour roles.
 *
 * "Roles" are named slots (primary, background, surface, outline, ...) that
 * Material 3 components read from. By filling them in once, every button, card
 * and navigation bar automatically uses the RepRise look.
 */
private val RepRiseDarkColorScheme = darkColorScheme(
    primary = Yellow,
    onPrimary = OnYellow,          // dark text/icons on top of the yellow accent
    primaryContainer = YellowDeep,
    onPrimaryContainer = OnYellow,
    secondary = YellowDeep,
    onSecondary = OnYellow,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = ErrorRedDark,
    onError = DarkBackground,
)

private val RepRiseLightColorScheme = lightColorScheme(
    primary = Yellow,
    onPrimary = OnYellow,
    primaryContainer = YellowDeep,
    onPrimaryContainer = OnYellow,
    secondary = YellowDeep,
    onSecondary = OnYellow,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = ErrorRedLight,
    onError = LightBackground,
)

/**
 * The single theme wrapper used by the whole app.
 *
 * @param darkTheme whether to use the dark palette. Defaults to the system
 *   setting; the Settings screen (Phase 7) will pass an explicit value once the
 *   user picks "Light", "Dark" or "System".
 * @param content the composable UI to draw inside the theme.
 *
 * Note: dynamic colour (Android 12+ wallpaper colours) is deliberately OFF so
 * the app keeps its own yellow identity on every device.
 */
@Composable
fun RepRiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typography: Typography = RepRiseTypography,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) RepRiseDarkColorScheme else RepRiseLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
