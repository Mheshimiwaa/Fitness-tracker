package com.reprise.app.ui.workouts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.DetailScreen
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * The workout editor: name, duration, notes, and a list of exercises each with
 * its own sets.
 *
 * Pushed on top of the Workouts tab (so the bottom bar is hidden and the back
 * arrow is the way out). All state lives in [WorkoutEditorViewModel], which
 * means rotating the device does not lose a half-typed session.
 *
 * @param workoutId the session to edit, or null to log a new one
 * @param onDone    called after a successful save, to leave the screen
 */
@Composable
fun WorkoutEditorScreen(
    workoutId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutEditorViewModel = viewModel(factory = WorkoutEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Load once per screen entry; the ViewModel ignores repeat calls.
    LaunchedEffect(workoutId) { viewModel.load(workoutId) }

    // Leave as soon as the server accepts the save.
    LaunchedEffect(state.isSaved) { if (state.isSaved) onDone() }

    DetailScreen(
        title = stringResource(
            if (state.isEditing) R.string.workout_editor_edit_title
            else R.string.workout_editor_new_title
        ),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (state.isLoading) {
            CenteredLoader()
            return@DetailScreen
        }

        state.errorMessage?.let { message ->
            ErrorBanner(message = message)
        }

        if (state.noExercises) {
            ErrorBanner(message = stringResource(R.string.workout_error_no_exercises))
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.workout_field_name)) },
            placeholder = { Text(stringResource(R.string.workout_field_name_hint)) },
            singleLine = true,
            enabled = !state.isSaving,
            isError = state.nameError,
            supportingText = if (state.nameError) {
                { Text(stringResource(R.string.workout_error_name)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        OutlinedTextField(
            value = state.durationText,
            onValueChange = viewModel::onDurationChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.workout_field_duration)) },
            singleLine = true,
            enabled = !state.isSaving,
            isError = state.durationError,
            supportingText = if (state.durationError) {
                { Text(stringResource(R.string.workout_error_duration)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.workout_field_notes)) },
            placeholder = { Text(stringResource(R.string.workout_field_notes_hint)) },
            enabled = !state.isSaving,
            minLines = 2,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        state.exercises.forEachIndexed { index, exercise ->
            ExerciseEditor(
                index = index,
                exercise = exercise,
                unit = state.unit,
                enabled = !state.isSaving,
                canRemove = state.canRemoveExercise,
                nameError = exercise.id in state.exerciseNameErrors,
                repsErrors = state.setRepsErrors,
                weightErrors = state.setWeightErrors,
                suggestions = state.suggestedExercises,
                onNameChange = { viewModel.onExerciseNameChanged(exercise.id, it) },
                onApplySuggestion = { viewModel.applySuggestion(exercise.id, it) },
                onRemove = { viewModel.removeExercise(exercise.id) },
                onAddSet = { viewModel.addSet(exercise.id) },
                onRemoveSet = { viewModel.removeSet(exercise.id, it) },
                onRepsChange = { setId, value -> viewModel.onRepsChanged(exercise.id, setId, value) },
                onWeightChange = { setId, value ->
                    viewModel.onWeightChanged(exercise.id, setId, value)
                },
            )
        }

        OutlinedButton(
            onClick = viewModel::addExercise,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.workout_add_exercise))
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(text = stringResource(R.string.action_save_short))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// One exercise card
// ---------------------------------------------------------------------------

@Composable
private fun ExerciseEditor(
    index: Int,
    exercise: ExerciseDraft,
    unit: WeightUnit,
    enabled: Boolean,
    canRemove: Boolean,
    nameError: Boolean,
    repsErrors: Set<Long>,
    weightErrors: Set<Long>,
    suggestions: List<String>,
    onNameChange: (String) -> Unit,
    onApplySuggestion: (String) -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Long) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onWeightChange: (Long, String) -> Unit,
) {
    RepRiseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.workout_exercise_label, index + 1),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRemove, enabled = enabled && canRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.workout_remove_exercise),
                )
            }
        }

        OutlinedTextField(
            value = exercise.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.workout_field_exercise_name)) },
            placeholder = { Text(stringResource(R.string.workout_field_exercise_hint)) },
            singleLine = true,
            enabled = enabled,
            isError = nameError,
            supportingText = if (nameError) {
                { Text(stringResource(R.string.workout_error_exercise_name)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        // Tappable names the user has used before, so common lifts are quick to
        // enter and spelled consistently (which the future progression feature
        // will compare on).
        val reusable = suggestions.filter { it != exercise.name }
        if (reusable.isNotEmpty() && enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.workout_suggestions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                reusable.forEach { name ->
                    AssistChip(
                        onClick = { onApplySuggestion(name) },
                        label = { Text(text = name) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.workout_sets_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        exercise.sets.forEach { set ->
            SetRow(
                set = set,
                unit = unit,
                enabled = enabled,
                canRemove = exercise.sets.size > 1,
                repsError = set.id in repsErrors,
                weightError = set.id in weightErrors,
                onRepsChange = { onRepsChange(set.id, it) },
                onWeightChange = { onWeightChange(set.id, it) },
                onRemove = { onRemoveSet(set.id) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onAddSet, enabled = enabled) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.workout_add_set))
        }
    }
}

@Composable
private fun SetRow(
    set: SetDraft,
    unit: WeightUnit,
    enabled: Boolean,
    canRemove: Boolean,
    repsError: Boolean,
    weightError: Boolean,
    onRepsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = set.repsText,
            onValueChange = onRepsChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.workout_set_reps)) },
            singleLine = true,
            enabled = enabled,
            isError = repsError,
            supportingText = if (repsError) {
                { Text(stringResource(R.string.workout_error_reps)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
        )
        OutlinedTextField(
            value = set.weightText,
            onValueChange = onWeightChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.workout_set_weight, stringResource(unit.shortLabelRes))) },
            singleLine = true,
            enabled = enabled,
            isError = weightError,
            supportingText = if (weightError) {
                { Text(stringResource(R.string.workout_error_weight)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
        )
        IconButton(
            onClick = onRemove,
            enabled = enabled && canRemove,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.workout_remove_set),
            )
        }
    }
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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun ExerciseEditorPreview() {
    RepRiseTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ExerciseEditor(
                index = 0,
                exercise = ExerciseDraft(
                    id = 1,
                    name = "Bench Press",
                    sets = listOf(
                        SetDraft(id = 2, repsText = "8", weightText = "60"),
                        SetDraft(id = 3, repsText = "6", weightText = "65"),
                    ),
                ),
                unit = WeightUnit.KG,
                enabled = true,
                canRemove = true,
                nameError = false,
                repsErrors = emptySet(),
                weightErrors = emptySet(),
                suggestions = listOf("Squat", "Deadlift"),
                onNameChange = {},
                onApplySuggestion = {},
                onRemove = {},
                onAddSet = {},
                onRemoveSet = {},
                onRepsChange = { _, _ -> },
                onWeightChange = { _, _ -> },
            )
        }
    }
}
