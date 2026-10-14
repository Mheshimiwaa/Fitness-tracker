package com.reprise.app.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.reprise.app.R
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ScreenContainer
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * Profile and Settings entry point.
 *
 * Phase 1 placeholder. Phase 2 shows the signed-in user's profile; Phase 7
 * adds theme/unit/goal settings, the privacy explanation and logout.
 */
@Composable
fun ProfileScreen() {
    ScreenContainer(title = stringResource(R.string.title_profile)) {
        EmptyState(
            icon = Icons.Filled.AccountCircle,
            title = stringResource(R.string.empty_profile_title),
            message = stringResource(R.string.empty_profile_message),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    RepRiseTheme { ProfileScreen() }
}
