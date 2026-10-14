package com.reprise.app.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.ScreenContainer
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.domain.model.Workout
import com.reprise.app.domain.model.WorkoutExercise
import com.reprise.app.domain.model.WorkoutSet
import com.reprise.app.domain.model.WorkoutSummary
import com.reprise.app.ui.theme.RepRiseTheme
import com.reprise.app.util.formatShortDate
import com.reprise.app.util.formatVolume

/**
 * Workouts tab: the logged history, newest first.
 *
 * All the data work lives in [WorkoutsViewModel]; this file is purely how the
 * screen looks. Tapping a row opens the editor for that session.
 */
@Composable
fun WorkoutsScreen(
    onLogWorkout: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutsViewModel = viewModel(factory = WorkoutsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Runs on first composition AND whenever the user returns from the editor,
    // so a newly logged session appears without a manual refresh.
    LaunchedEffect(Unit) { viewModel.refresh() }

    var pendingDelete by remember { mutableStateOf<Workout?>(null) }

    ScreenContainer(title = stringResource(R.string.title_workouts), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onLogWorkout,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.workouts_log_button))
            }
            OutlinedButton(
                onClick = onOpenProgress,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.workouts_progress_button))
            }
        }

        state.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::refresh,
            )
        }

        when {
            state.isLoading && state.workouts.isEmpty() -> CenteredLoader()

            state.workouts.isEmpty() -> EmptyState(
                icon = Icons.Filled.DateRange,
                title = stringResource(R.string.empty_workouts_title),
                message = stringResource(R.string.empty_workouts_message),
            )

            else -> {
                Text(
                    text = pluralStringResource(
                        R.plurals.workouts_count,
                        state.workouts.size,
                        state.workouts.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.workouts.forEach { workout ->
                    WorkoutCard(
                        workout = workout,
                        onOpen = { onOpenWorkout(workout.id) },
                        onDelete = { pendingDelete = workout },
                    )
                }
            }
        }
    }

    pendingDelete?.let { workout ->
        DeleteWorkoutDialog(
            workout = workout,
            onConfirm = {
                viewModel.delete(workout.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

@Composable
private fun WorkoutCard(
    workout: Workout,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    RepRiseCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatShortDate(workout.workoutDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = summaryLine(workout),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (workout.summary.totalVolumeKg > 0.0) {
                    Text(
                        text = stringResource(
                            R.string.workouts_volume,
                            formatVolume(workout.summary.totalVolumeKg),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                workout.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        }
    }
}

/** "3 exercises · 12 sets · 45 min", omitting whatever is not recorded. */
@Composable
private fun summaryLine(workout: Workout): String {
    val parts = buildList {
        val exercises = workout.summary.exerciseCount
        val sets = workout.summary.setCount
        add(pluralStringResource(R.plurals.workout_exercises, exercises, exercises))
        add(pluralStringResource(R.plurals.workout_sets, sets, sets))
        workout.durationMinutes?.let { add(stringResource(R.string.workouts_minutes, it)) }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun CenteredLoader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DeleteWorkoutDialog(
    workout: Workout,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workouts_delete_title)) },
        text = {
            Text(
                stringResource(R.string.workouts_delete_message) + " (${workout.name})",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewWorkout(): Workout = Workout(
    id = "1",
    name = "Push day",
    workoutDate = "2026-10-14T08:33:57.367Z",
    durationMinutes = 45,
    notes = "felt strong",
    exercises = listOf(
        WorkoutExercise(
            name = "Bench Press",
            unit = WeightUnit.KG,
            restSeconds = 120,
            notes = null,
            sets = listOf(
                WorkoutSet(reps = 8, weight = 60.0, weightKg = 60.0),
                WorkoutSet(reps = 6, weight = 65.0, weightKg = 65.0),
            ),
        ),
    ),
    summary = WorkoutSummary(
        exerciseCount = 3,
        setCount = 12,
        totalReps = 96,
        totalVolumeKg = 1982.0,
    ),
)

@Preview(showBackground = true)
@Composable
private fun WorkoutCardPreview() {
    RepRiseTheme {
        ScreenContainer(title = "Workouts") {
            WorkoutCard(workout = previewWorkout(), onOpen = {}, onDelete = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutsEmptyPreview() {
    RepRiseTheme {
        ScreenContainer(title = "Workouts") {
            EmptyState(
                icon = Icons.Filled.DateRange,
                title = "No workouts yet",
                message = "Your logged strength sessions will appear here.",
            )
        }
    }
}
