package com.reprise.app.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.ScreenContainer
import com.reprise.app.domain.model.Bmi
import com.reprise.app.domain.model.BmiCategory
import com.reprise.app.domain.model.BmiUnavailableReason
import com.reprise.app.domain.model.Dashboard
import com.reprise.app.domain.model.StreakStats
import com.reprise.app.domain.model.StreakStatus
import com.reprise.app.domain.model.User
import com.reprise.app.domain.model.WeightChange
import com.reprise.app.domain.model.WeightEntry
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.domain.model.WorkoutStats
import com.reprise.app.domain.model.streakStatus
import com.reprise.app.ui.theme.RepRiseTheme
import com.reprise.app.util.formatShortDate
import com.reprise.app.util.formatSignedWeight
import com.reprise.app.util.formatVolume
import com.reprise.app.util.formatWeight

/**
 * Home / Dashboard.
 *
 * Shows the signed-in user a real summary loaded from `GET /api/dashboard`:
 * their training streak, their latest body weight, the change over the last 30
 * days, their BMI, and how much they have been training.
 *
 * Every number here comes from the server — there is no fabricated data, and
 * empty sections explain what to do next rather than showing a fake value.
 */
@Composable
fun HomeScreen(
    user: User,
    onOpenWeights: () -> Unit,
    onOpenWorkouts: () -> Unit,
    onLogWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Runs on first composition AND whenever the user returns to this screen,
    // so a weight logged elsewhere shows up without a manual refresh.
    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenContainer(
        title = stringResource(R.string.home_greeting, user.displayName),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::refresh,
            )
        }

        when (val dashboard = state.dashboard) {
            null -> LoadingCards()
            else -> {
                WeightCard(dashboard = dashboard, onOpenWeights = onOpenWeights)
                TrainingCard(
                    dashboard = dashboard,
                    onOpenWorkouts = onOpenWorkouts,
                    onLogWorkout = onLogWorkout,
                )
                StreakCard(dashboard = dashboard)
                BmiCard(dashboard = dashboard)
            }
        }
    }
}

/** A centred spinner shown before the first successful load. */
@Composable
private fun LoadingCards() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Latest weight, direction of travel, and the two primary actions. */
@Composable
private fun WeightCard(
    dashboard: Dashboard,
    onOpenWeights: () -> Unit,
) {
    RepRiseCard {
        CardHeader(
            title = stringResource(R.string.home_weight_title),
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_scale),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        val latest = dashboard.latestWeight
        if (latest == null) {
            Text(
                text = stringResource(R.string.home_weight_none_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_weight_none_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            WeightValue(entry = latest)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatShortDate(latest.measuredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChangeLine(dashboard = dashboard)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_weight_entries, dashboard.weightEntryCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenWeights) {
                Text(text = stringResource(R.string.home_action_log_weight))
            }
            if (dashboard.weightEntryCount > 0) {
                OutlinedButton(onClick = onOpenWeights) {
                    Text(text = stringResource(R.string.home_action_view_history))
                }
            }
        }
    }
}

/** The big "82.4 kg" reading. */
@Composable
private fun WeightValue(entry: WeightEntry) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatWeight(entry.weight),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(entry.unit.shortLabelRes),
            modifier = Modifier.padding(bottom = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The change over the dashboard window.
 *
 * The arrow shows DIRECTION only — it is deliberately not coloured green or red,
 * because whether gaining or losing is "good" depends entirely on the person's
 * goal. The app tracks; it does not judge.
 */
@Composable
private fun ChangeLine(dashboard: Dashboard) {
    val change = dashboard.weightChange
    if (change == null) {
        Text(
            text = stringResource(R.string.home_weight_no_change),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (change.amount != 0.0) {
            Icon(
                imageVector = if (change.amount < 0) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowUp
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "${formatSignedWeight(change.amount)} ${stringResource(change.unit.shortLabelRes)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_weight_window, dashboard.changeWindowDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How much the user has been training.
 *
 * "Last N days" is a ROLLING window from the server, not a calendar week, so a
 * Monday morning does not look like a failure just because the week is new.
 *
 * Volume is shown in kilograms because it is a cross-session comparison figure,
 * not a body weight the user picked a unit for.
 */
@Composable
private fun TrainingCard(
    dashboard: Dashboard,
    onOpenWorkouts: () -> Unit,
    onLogWorkout: () -> Unit,
) {
    val stats = dashboard.workouts

    RepRiseCard {
        CardHeader(
            title = stringResource(R.string.home_workouts_title),
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_workouts),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (stats.isEmpty) {
            Text(
                text = stringResource(R.string.home_workouts_none_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_workouts_none_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatColumn(
                    label = stringResource(R.string.home_workouts_recent_label, stats.recentDays),
                    value = stats.recentCount.toString(),
                )
                StatColumn(
                    label = stringResource(R.string.home_workouts_total_label),
                    value = stats.totalCount.toString(),
                )
                StatColumn(
                    label = stringResource(R.string.home_workouts_volume_label),
                    value = formatVolume(stats.recentVolumeKg),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            stats.lastWorkoutAt?.let { last ->
                Text(
                    text = "${stringResource(R.string.home_workouts_last_label)}: " +
                        formatShortDate(last),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLogWorkout) {
                Text(text = stringResource(R.string.home_action_log_workout))
            }
            if (!stats.isEmpty) {
                OutlinedButton(onClick = onOpenWorkouts) {
                    Text(text = stringResource(R.string.home_action_view_workouts))
                }
            }
        }
    }
}

/** One big number with a small caption underneath. */
@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Consecutive days trained.
 *
 * The card is written to ENCOURAGE, not to scold. A day with no workout yet is
 * not a failure — the streak is still standing until the day is over — so the
 * copy for that state invites the user to train today instead of reporting a
 * loss. The personal record is shown alongside the current run whenever the user
 * has ever done better, which is exactly when it is worth knowing.
 */
@Composable
private fun StreakCard(dashboard: Dashboard) {
    val streak = dashboard.streak
    val status = streakStatus(streak)

    RepRiseCard {
        CardHeader(
            title = stringResource(R.string.home_streak_title),
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_flame),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (status) {
            StreakStatus.NOT_STARTED, StreakStatus.ENDED -> {
                Text(
                    text = stringResource(
                        if (status == StreakStatus.NOT_STARTED) {
                            R.string.home_streak_none_title
                        } else {
                            R.string.home_streak_ended_title
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(streakMessageRes(status)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StreakStatus.AT_RISK, StreakStatus.ACTIVE -> {
                Text(
                    text = streak.current.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.home_streak_days_in_a_row,
                        streak.current,
                        streak.current,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(streakMessageRes(status)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The record is only interesting while it is still ahead of the user.
        if (streak.longest > streak.current) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.home_streak_best,
                    pluralStringResource(
                        R.plurals.home_streak_days,
                        streak.longest,
                        streak.longest,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The line under the streak number explaining what to do now. */
private fun streakMessageRes(status: StreakStatus): Int = when (status) {
    StreakStatus.NOT_STARTED -> R.string.home_streak_none_message
    StreakStatus.AT_RISK -> R.string.home_streak_at_risk
    StreakStatus.ACTIVE -> R.string.home_streak_active
    StreakStatus.ENDED -> R.string.home_streak_ended_message
}

/** BMI, or an explanation of what is still missing. */
@Composable
private fun BmiCard(dashboard: Dashboard) {
    RepRiseCard {
        CardHeader(
            title = stringResource(R.string.home_bmi_title),
            leading = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (val bmi = dashboard.bmi) {
            null -> Text(
                text = stringResource(missingBmiRes(dashboard.bmiUnavailableReason)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatWeight(bmi.value),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(bmi.category.labelRes),
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(bmi.category.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_bmi_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Which explanation to show when there is no BMI yet. */
private fun missingBmiRes(reason: BmiUnavailableReason?): Int = when (reason) {
    BmiUnavailableReason.NO_HEIGHT -> R.string.home_bmi_needs_height
    else -> R.string.home_bmi_needs_weight
}

/** A card title with a leading icon. */
@Composable
private fun CardHeader(
    title: String,
    leading: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Build a dashboard for previews without repeating every field each time. */
private fun previewDashboard(
    latestWeight: WeightEntry? = null,
    weightChange: WeightChange? = null,
    weightEntryCount: Int = 0,
    bmi: Bmi? = null,
    bmiUnavailableReason: BmiUnavailableReason? = null,
    workouts: WorkoutStats = WorkoutStats(
        totalCount = 0,
        recentCount = 0,
        recentDays = 7,
        recentVolumeKg = 0.0,
        lastWorkoutAt = null,
    ),
    streak: StreakStats = StreakStats(
        current = 0,
        longest = 0,
        trainedToday = false,
        activeDays = 0,
    ),
): Dashboard = Dashboard(
    latestWeight = latestWeight,
    previousWeight = null,
    weightEntryCount = weightEntryCount,
    changeWindowDays = 30,
    weightChange = weightChange,
    workouts = workouts,
    streak = streak,
    bmi = bmi,
    bmiUnavailableReason = bmiUnavailableReason,
)

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RepRiseTheme {
        ScreenContainer(title = "Hi, Alex Lifter") {
            WeightCard(
                dashboard = previewDashboard(
                    latestWeight = WeightEntry(
                        id = "1",
                        weight = 82.4,
                        unit = WeightUnit.KG,
                        weightKg = 82.4,
                        measuredAt = "2026-10-14T08:33:57.367Z",
                        note = "morning",
                    ),
                    weightChange = WeightChange(amount = -2.4, unit = WeightUnit.KG),
                    weightEntryCount = 3,
                ),
                onOpenWeights = {},
            )
            TrainingCard(
                dashboard = previewDashboard(
                    workouts = WorkoutStats(
                        totalCount = 12,
                        recentCount = 3,
                        recentDays = 7,
                        recentVolumeKg = 1982.0,
                        lastWorkoutAt = "2026-10-14T08:33:57.367Z",
                    ),
                ),
                onOpenWorkouts = {},
                onLogWorkout = {},
            )
            StreakCard(
                dashboard = previewDashboard(
                    streak = StreakStats(
                        current = 5,
                        longest = 12,
                        trainedToday = false,
                        activeDays = 34,
                    ),
                ),
            )
            BmiCard(
                dashboard = previewDashboard(
                    bmi = Bmi(value = 25.4, category = BmiCategory.OVERWEIGHT),
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    RepRiseTheme {
        ScreenContainer(title = "Home") {
            WeightCard(dashboard = previewDashboard(), onOpenWeights = {})
            TrainingCard(
                dashboard = previewDashboard(),
                onOpenWorkouts = {},
                onLogWorkout = {},
            )
            StreakCard(dashboard = previewDashboard())
            BmiCard(
                dashboard = previewDashboard(
                    bmiUnavailableReason = BmiUnavailableReason.NO_HEIGHT,
                ),
            )
        }
    }
}
