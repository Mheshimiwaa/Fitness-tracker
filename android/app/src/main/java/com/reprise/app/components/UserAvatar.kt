package com.reprise.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A person's picture: their photo when they have one, and a coloured circle with
 * their initial when they do not.
 *
 * The initial is drawn FIRST and the photo on top of it, rather than being
 * swapped out once the photo has loaded. Coil draws nothing while it is fetching
 * and nothing if the fetch fails, so this one arrangement covers all three
 * cases — no photo, photo still loading, photo that could not be loaded — with
 * no state to observe and no empty circle to flash. The Friends and Leaderboard
 * screens share this definition, so the two lists look identical.
 *
 * @param displayName      the person's name — preferred for the initial
 * @param username         used only when the display name has no usable letter
 * @param profileImageUrl  the photo the API reports for this person, or null
 */
@Composable
fun UserAvatar(
    displayName: String,
    username: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    profileImageUrl: String? = null,
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

            if (profileImageUrl != null) {
                // Fills the circle (the Surface clips it) and crops rather than
                // stretching, so a non-square photo still looks like a portrait.
                AsyncImage(
                    model = profileImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
