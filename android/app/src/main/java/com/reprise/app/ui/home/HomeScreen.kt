package com.reprise.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.reprise.app.R
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ScreenContainer
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * Home / Dashboard.
 *
 * Phase 1 placeholder. From Phase 3 onward this screen will show the greeting,
 * latest weight, BMI, weekly workout count, streak, leaderboard position and
 * quick actions — all loaded from GET /api/dashboard.
 *
 * It shows an honest empty state for now: no fabricated fitness numbers.
 */
@Composable
fun HomeScreen() {
    ScreenContainer(title = stringResource(R.string.title_home)) {
        EmptyState(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.empty_home_title),
            message = stringResource(R.string.empty_home_message),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RepRiseTheme { HomeScreen() }
}
