package com.reprise.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A coloured circle showing a person's initial.
 *
 * The project deliberately ships no image-loading library, so a profile picture
 * URL (when the server sends one) is not rendered; the initial is a
 * dependency-free stand-in that still makes each person recognisable at a
 * glance. The Friends and Leaderboard screens share this one definition so the
 * two lists look identical.
 *
 * @param displayName the person's name — preferred for the initial
 * @param username    used only when the display name has no usable letter
 */
@Composable
fun UserAvatar(
    displayName: String,
    username: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initialOf(displayName, username),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * The first letter of a display name, falling back to the username.
 *
 * Uppercased so "alice" and "Alice" look the same, and guarded so a blank
 * display name never yields an empty circle.
 */
internal fun initialOf(displayName: String, username: String): String {
    val fromName = displayName.trim().take(1)
    return (if (fromName.isNotBlank()) fromName else username.take(1)).uppercase()
}
