package com.reprise.app.ui.workouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.reprise.app.R
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ScreenContainer
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * Workout history.
 *
 * Phase 1 placeholder. Phase 4 adds workout creation/editing, exercise and set
 * entry, history and "reuse a previous workout"; Phase 5 adds the progressive
 * overload comparison.
 */
@Composable
fun WorkoutsScreen() {
    ScreenContainer(title = stringResource(R.string.title_workouts)) {
        EmptyState(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.empty_workouts_title),
            message = stringResource(R.string.empty_workouts_message),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutsScreenPreview() {
    RepRiseTheme { WorkoutsScreen() }
}
