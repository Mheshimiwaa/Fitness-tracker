package com.reprise.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The RepRise colour palette.
 *
 * These raw colour values come straight from the design specification. Keeping
 * them in one file makes it easy to change the brand later, and the Compose
 * theme (see Theme.kt) maps them onto Material 3 "roles" such as `primary`,
 * `background` and `surface`.
 */

// --- Brand accent (identical in light and dark) -----------------------------
val Yellow = Color(0xFFFFD600)       // primary accent
val YellowDeep = Color(0xFFF5C400)   // secondary accent / pressed states

// --- Dark theme -------------------------------------------------------------
val DarkBackground = Color(0xFF0B0B0B)
val DarkSurface = Color(0xFF171717)
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFA3A3A3)
val DarkBorder = Color(0xFF303030)

// --- Light theme ------------------------------------------------------------
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF5F5F5)
val LightTextPrimary = Color(0xFF171717)
val LightTextSecondary = Color(0xFF666666)
val LightBorder = Color(0xFFE5E5E5)

// --- Semantic colours -------------------------------------------------------
// Used for progress/positive feedback and errors. They are deliberately not
// part of the yellow brand so that "good" and "bad" never read as brand colour.
val SuccessGreenLight = Color(0xFF1B7F3B)
val SuccessGreenDark = Color(0xFF3DDC84)
val ErrorRedLight = Color(0xFFB3261E)
val ErrorRedDark = Color(0xFFFF6B6B)

// A near-black used for text/icons drawn ON TOP of the yellow accent.
val OnYellow = Color(0xFF171717)
