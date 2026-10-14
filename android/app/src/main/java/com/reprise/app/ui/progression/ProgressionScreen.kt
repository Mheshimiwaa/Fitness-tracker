package com.reprise.app.ui.progression

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.reprise.app.components.DetailScreen
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.domain.model.ExerciseProgression
import com.reprise.app.domain.model.ProgressionComparison
import com.reprise.app.domain.model.ProgressionSession
import com.reprise.app.domain.model.ProgressionSet
import com.reprise.app.domain.model.ProgressionVerdict
import com.reprise.app.domain.model.TrackedExercise
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.theme.RepRiseTheme
import com.reprise.app.util.formatShortDate
import com.reprise.app.util.formatSignedInt
import com.reprise.app.util.formatSignedVolume
import com.reprise.app.util.formatSignedWeight
import com.reprise.app.util.formatVolume
import com.reprise.app.util.formatWeight

/**
 * The Progressive Overload screen (Phase 5).
 *
 * Two levels in one screen: a list of the exercises the user has logged, and —
 * once one is tapped — that exercise's trend and the assistant's rule-based
 * suggestion. See [ProgressionUiState] for why the selection is state rather
 * than a nav argument.
 *
 * All of the judgement happens on the SERVER; this file only draws it.
 */
@Composable
fun ProgressionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressionViewModel = viewModel(factory = ProgressionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Loads on entry, so a session logged after this screen was last open shows
    // up in the trend.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val selected = state.selectedExercise

    // The system back gesture should step back to the list before leaving the
    // screen, matching the toolbar arrow.
    BackHandler(enabled = selected != null) { viewModel.clearSelection() }

    DetailScreen(
        title = selected ?: stringResource(R.string.title_progress),
        onBack = {
            if (selected != null) viewModel.clearSelection() else onBack()
        },
        modifier = modifier,
    ) {
        if (selected == null) {
            ProgressionList(
                state = state,
                onSelect = viewModel::select,
                onRetry = viewModel::refresh,
            )
        } else {
            ProgressionDetail(state = state, onRetry = viewModel::retryDetail)
        }
    }
}

// ---------------------------------------------------------------------------
// Level 1: the list of tracked exercises
// ---------------------------------------------------------------------------

@Composable
private fun ProgressionList(
    state: ProgressionUiState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
) {
    state.listError?.let { message ->
        ErrorBanner(
            message = message,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
    }

    when {
        state.isLoadingExercises && state.exercises.isEmpty() -> CenteredLoader()

        state.exercises.isEmpty() -> {
            // Do not also claim the list is empty when a load simply failed —
            // the banner above already explains what happened.
            if (state.listError == null) {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.progression_empty_title),
                    message = stringResource(R.string.progression_empty_message),
                )
            }
        }

        else -> {
            Text(
                text = stringResource(R.string.progression_list_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.exercises.forEach { exercise ->
                TrackedExerciseCard(
                    exercise = exercise,
                    onOpen = { onSelect(exercise.name) },
                )
            }
        }
    }
}

@Composable
private fun TrackedExerciseCard(
    exercise: TrackedExercise,
    onOpen: () -> Unit,
) {
    RepRiseCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = loggedLine(exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "logged 3 times · Last: 8 Oct 2026", omitting the date if it is unknown. */
@Composable
private fun loggedLine(exercise: TrackedExercise): String {
    val times = pluralStringResource(
        R.plurals.progression_times_logged,
        exercise.timesLogged,
        exercise.timesLogged,
    )
    val last = exercise.lastPerformedAt
        ?: return times
    return times + " · " + stringResource(R.string.progression_last_performed, formatShortDate(last))
}

// ---------------------------------------------------------------------------
// Level 2: one exercise's trend
// ---------------------------------------------------------------------------

@Composable
private fun ProgressionDetail(
    state: ProgressionUiState,
    onRetry: () -> Unit,
) {
    state.detailError?.let { message ->
        ErrorBanner(
            message = message,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
    }

    val progression = state.progression

    when {
        state.isLoadingDetail && progression == null -> CenteredLoader()

        progression == null -> Unit // a failed load: the banner above says why

        else -> ProgressionContent(progression = progression)
    }
}

@Composable
private fun ProgressionContent(progression: ExerciseProgression) {
    val unitShort = stringResource(progression.unit.shortLabelRes)
    val comparison = progression.comparison

    VerdictCard(comparison = comparison)

    if (comparison.hasPrevious) {
        ComparisonCard(
            comparison = comparison,
            unitShort = unitShort,
        )
    } else {
        Text(
            text = stringResource(R.string.progression_no_previous),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (progression.sessions.isNotEmpty()) {
        Text(
            text = stringResource(R.string.progression_history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        progression.sessions.forEach { session ->
            SessionCard(session = session, unitShort = unitShort)
        }
    }
}

/** The verdict, coloured by its meaning, plus the assistant's suggestion. */
@Composable
private fun VerdictCard(comparison: ProgressionComparison) {
    RepRiseCard {
        Text(
            text = stringResource(comparison.verdict.labelRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = verdictColor(comparison.verdict),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = comparison.suggestion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun verdictColor(verdict: ProgressionVerdict) = when (verdict) {
    ProgressionVerdict.PROGRESSING -> MaterialTheme.colorScheme.primary
    ProgressionVerdict.REGRESSING -> MaterialTheme.colorScheme.error
    // "Holding steady" and "not enough data" are neutral, not warnings.
    ProgressionVerdict.HOLDING,
    ProgressionVerdict.INSUFFICIENT_DATA,
    -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Latest against previous: top set, total reps and volume, each with the change
 * since last time. Deliberately a plain three-row block rather than a chart —
 * two numbers do not need a chart.
 */
@Composable
private fun ComparisonCard(
    comparison: ProgressionComparison,
    unitShort: String,
) {
    val latest = comparison.latest ?: return
    val previous = comparison.previous ?: return

    RepRiseCard {
        Text(
            text = stringResource(R.string.progression_comp_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        StatBlock(
            label = stringResource(R.string.progression_stat_top_weight),
            latestValue = withUnit(formatWeight(latest.topWeight), unitShort),
            delta = withUnit(formatSignedWeight(comparison.topWeightDelta), unitShort),
            previousValue = withUnit(formatWeight(previous.topWeight), unitShort),
        )

        Spacer(modifier = Modifier.height(12.dp))
        StatBlock(
            label = stringResource(R.string.progression_stat_reps),
            latestValue = latest.totalReps.toString(),
            delta = formatSignedInt(comparison.totalRepsDelta),
            previousValue = previous.totalReps.toString(),
        )

        Spacer(modifier = Modifier.height(12.dp))
        StatBlock(
            label = stringResource(R.string.progression_stat_volume),
            // Volume is always kilograms, whatever the user's display unit is.
            latestValue = withUnit(formatVolume(latest.totalVolumeKg), stringResource(R.string.unit_kg_short)),
            delta = withUnit(
                formatSignedVolume(comparison.totalVolumeDeltaKg),
                stringResource(R.string.unit_kg_short),
            ),
            previousValue = withUnit(
                formatVolume(previous.totalVolumeKg),
                stringResource(R.string.unit_kg_short),
            ),
        )
    }
}

@Composable
private fun StatBlock(
    label: String,
    latestValue: String,
    delta: String,
    previousValue: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = latestValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = delta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.progression_previous_label) + " " + previousValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One past session: when, how much, and the heaviest set. */
@Composable
private fun SessionCard(session: ProgressionSession, unitShort: String) {
    RepRiseCard {
        Text(
            text = formatShortDate(session.workoutDate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        session.workoutName?.takeIf { it.isNotBlank() }?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sessionSummary(session),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = topSetLine(session, unitShort),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "3 sets · 24 reps" */
@Composable
private fun sessionSummary(session: ProgressionSession): String {
    val sets = pluralStringResource(R.plurals.workout_sets, session.setCount, session.setCount)
    val reps = pluralStringResource(R.plurals.progression_reps, session.totalReps, session.totalReps)
    return "$sets · $reps"
}

/** "Top set 65 kg · 1,560 kg" */
@Composable
private fun topSetLine(session: ProgressionSession, unitShort: String): String {
    val top = withUnit(formatWeight(session.topWeight), unitShort)
    val volume = withUnit(formatVolume(session.totalVolumeKg), stringResource(R.string.unit_kg_short))
    return stringResource(R.string.progression_stat_top_weight) + " " + top + " · " + volume
}

/** "65 kg" — a number with its unit, in the right order for the locale. */
@Composable
private fun withUnit(value: String, unitShort: String): String =
    stringResource(R.string.value_with_unit, value, unitShort)

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

private fun previewTrackedExercises(): List<TrackedExercise> = listOf(
    TrackedExercise(name = "Bench Press", timesLogged = 5, lastPerformedAt = "2026-10-08T10:00:00.000Z"),
    TrackedExercise(name = "Barbell Row", timesLogged = 3, lastPerformedAt = "2026-10-06T10:00:00.000Z"),
    TrackedExercise(name = "Back Squat", timesLogged = 1, lastPerformedAt = "2026-09-30T10:00:00.000Z"),
)

private fun previewSession(
    workoutId: String,
    workoutDate: String,
    topWeight: Double,
    totalReps: Int,
    totalVolumeKg: Double,
): ProgressionSession = ProgressionSession(
    workoutId = workoutId,
    workoutName = "Push day",
    workoutDate = workoutDate,
    setCount = 3,
    totalReps = totalReps,
    totalVolumeKg = totalVolumeKg,
    topWeight = topWeight,
    sets = List(3) { ProgressionSet(reps = 8, weight = topWeight, weightKg = topWeight) },
)

private fun previewProgression(): ExerciseProgression = ExerciseProgression(
    exercise = "Bench Press",
    unit = WeightUnit.KG,
    sessionCount = 2,
    sessions = listOf(
        previewSession("2", "2026-10-08T10:00:00.000Z", topWeight = 65.0, totalReps = 24, totalVolumeKg = 1560.0),
        previewSession("1", "2026-10-01T10:00:00.000Z", topWeight = 60.0, totalReps = 24, totalVolumeKg = 1440.0),
    ),
    comparison = ProgressionComparison(
        hasPrevious = true,
        latest = previewSession("2", "2026-10-08T10:00:00.000Z", 65.0, 24, 1560.0),
        previous = previewSession("1", "2026-10-01T10:00:00.000Z", 60.0, 24, 1440.0),
        topWeightDelta = 5.0,
        totalRepsDelta = 0,
        totalVolumeDeltaKg = 120.0,
        verdict = ProgressionVerdict.PROGRESSING,
        suggestion = "Progress: your top set went from 60 to 65 kg. Next session, aim for 67.5 kg " +
            "for the same reps — or add one rep per set first.",
    ),
)

@Preview(showBackground = true)
@Composable
private fun ProgressionListPreview() {
    RepRiseTheme {
        DetailScreen(title = "Progress", onBack = {}) {
            Text(
                text = "Tap an exercise to see how your last session compares.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            previewTrackedExercises().forEach { exercise ->
                TrackedExerciseCard(exercise = exercise, onOpen = {})
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressionDetailPreview() {
    RepRiseTheme {
        DetailScreen(title = "Bench Press", onBack = {}) {
            ProgressionContent(progression = previewProgression())
        }
    }
}
