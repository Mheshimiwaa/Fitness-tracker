package com.reprise.app.ui.leaderboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.DetailScreen
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.UserAvatar
import com.reprise.app.domain.model.Leaderboard
import com.reprise.app.domain.model.LeaderboardEntry
import com.reprise.app.domain.model.UserSummary
import com.reprise.app.ui.theme.RepRiseTheme
import com.reprise.app.util.formatWeekRange

/**
 * The weekly leaderboard (Phase 6b).
 *
 * A read-only ranking of the signed-in user and their friends by how
 * consistently they trained this week. Every number — the score, the week window
 * and the rules — is decided by the server; this file only draws the payload and
 * explains the rules using the values that came with it.
 *
 * It is a pushed screen (opened from the Friends tab) rather than an inline
 * section, so the board gets room to breathe and the Friends list stays focused.
 */
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaderboardViewModel = viewModel(factory = LeaderboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Loads on entry, so a workout logged since the last visit is reflected.
    LaunchedEffect(Unit) { viewModel.refresh() }

    DetailScreen(
        title = stringResource(R.string.title_leaderboard),
        onBack = onBack,
        modifier = modifier,
    ) {
        LeaderboardContent(state = state, onRetry = viewModel::refresh)
    }
}

@Composable
private fun LeaderboardContent(
    state: LeaderboardUiState,
    onRetry: () -> Unit,
) {
    state.error?.let { message ->
        ErrorBanner(
            message = message,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
    }

    val leaderboard = state.leaderboard

    when {
        // First load: nothing to show yet.
        leaderboard == null && state.isLoading -> CenteredLoader()

        // Failed with nothing cached — the banner above already explains it.
        leaderboard == null -> Unit

        leaderboard.entries.isEmpty() -> EmptyState(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.leaderboard_empty_title),
            message = stringResource(R.string.leaderboard_empty_message),
        )

        else -> {
            WeekLine(leaderboard)
            leaderboard.me?.let { YourStandingCard(it) }
            // "You are on the board alone" is worth saying plainly.
            if (leaderboard.entries.size == 1) {
                Text(
                    text = stringResource(R.string.leaderboard_solo_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScoringCard(leaderboard)
            leaderboard.entries.forEach { entry ->
                LeaderboardRow(entry)
            }
        }
    }
}

@Composable
private fun WeekLine(leaderboard: Leaderboard) {
    val range = formatWeekRange(leaderboard.weekStart, leaderboard.weekEnd)
    if (range.isBlank()) return

    Text(
        text = stringResource(R.string.leaderboard_week, range),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The headline answer to "where am I?": my rank and score this week. */
@Composable
private fun YourStandingCard(me: LeaderboardEntry) {
    RepRiseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.leaderboard_you_rank, me.rank),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = activityLine(me),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = pointsLabel(me.score),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The rules, using the numbers the server sent rather than hard-coded ones. */
@Composable
private fun ScoringCard(leaderboard: Leaderboard) {
    RepRiseCard {
        Text(
            text = stringResource(R.string.leaderboard_how_scored),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.leaderboard_scoring_rules,
                leaderboard.pointsPerDay,
                leaderboard.bonusPoints,
                leaderboard.bonusDays,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    RepRiseCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RankBadge(entry.rank)
            UserAvatar(
                displayName = entry.user.displayName,
                username = entry.user.username,
                size = 36.dp,
                profileImageUrl = entry.user.profileImageUrl,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.user.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (entry.isMe) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.isMe) {
                        Spacer(Modifier.width(8.dp))
                        YouChip()
                    }
                }
                Text(
                    text = stringResource(R.string.friends_username_at, entry.user.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pointsLabel(entry.score),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.leaderboard_active_days,
                        entry.activeDays,
                        entry.activeDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A fixed-width rank so the avatars below it line up down the list. */
@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small filled tag marking the signed-in user's own row. */
@Composable
private fun YouChip() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = stringResource(R.string.leaderboard_you_label),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun CenteredLoader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** "35 pts" / "1 pt". */
@Composable
private fun pointsLabel(score: Int): String =
    pluralStringResource(R.plurals.leaderboard_points, score, score)

/** "5 active days · 6 workouts". */
@Composable
private fun activityLine(entry: LeaderboardEntry): String {
    val days = pluralStringResource(
        R.plurals.leaderboard_active_days,
        entry.activeDays,
        entry.activeDays,
    )
    val workouts = pluralStringResource(
        R.plurals.leaderboard_workouts,
        entry.workoutCount,
        entry.workoutCount,
    )
    return "$days · $workouts"
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun LeaderboardContentPreview() {
    val alice = UserSummary("1", "alice_lifts", "Alice Nguyen", null)
    val bob = UserSummary("2", "bob_runs", "Bob Carter", null)
    val me = UserSummary("3", "me_user", "Sam Spotter", null)

    RepRiseTheme {
        LeaderboardContent(
            state = LeaderboardUiState(
                isLoading = false,
                leaderboard = Leaderboard(
                    weekStart = "2026-10-12T00:00:00.000Z",
                    weekEnd = "2026-10-19T00:00:00.000Z",
                    pointsPerDay = 10,
                    bonusDays = 3,
                    bonusPoints = 5,
                    entries = listOf(
                        LeaderboardEntry(1, alice, 45, 4, 5, false),
                        LeaderboardEntry(2, me, 35, 3, 3, true),
                        LeaderboardEntry(2, bob, 35, 3, 4, false),
                    ),
                ),
            ),
            onRetry = {},
        )
    }
}
