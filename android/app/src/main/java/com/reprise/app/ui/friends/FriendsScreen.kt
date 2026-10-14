package com.reprise.app.ui.friends

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.reprise.app.R
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ScreenContainer
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * Friends.
 *
 * Phase 1 placeholder. Phase 6 adds username search, friend requests, the
 * friend list and the weekly leaderboard.
 */
@Composable
fun FriendsScreen() {
    ScreenContainer(title = stringResource(R.string.title_friends)) {
        EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.empty_friends_title),
            message = stringResource(R.string.empty_friends_message),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FriendsScreenPreview() {
    RepRiseTheme { FriendsScreen() }
}
