package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.apiCall
import com.reprise.app.data.remote.dto.LeaderboardDto
import com.reprise.app.data.remote.dto.LeaderboardEntryDto
import com.reprise.app.data.remote.dto.UserDto
import com.reprise.app.domain.model.Leaderboard
import com.reprise.app.domain.model.LeaderboardEntry
import com.reprise.app.domain.model.UserSummary

/**
 * Reads the weekly leaderboard (Phase 6b).
 *
 * There is nothing to write here: the board is DERIVED from the workouts the
 * user and their friends have already logged, so this repository only maps the
 * wire shape onto the domain models. Every scoring rule — how many points a day
 * is worth, when the bonus applies, where the week starts — lives on the server,
 * so changing it never requires an app release.
 */
class LeaderboardRepository(private val api: RepRiseApi) {

    /** This week's board for the signed-in user and their friends. */
    suspend fun weekly(): ApiResult<Leaderboard> =
        apiCall { api.weeklyLeaderboard() }
            .map(LeaderboardDto::toDomain)

    /** Apply [transform] to a successful result, leaving failures untouched. */
    private inline fun <T : Any, R : Any> ApiResult<T>.map(
        transform: (T) -> R,
    ): ApiResult<R> = when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
    }
}

// ---------------------------------------------------------------------------
// DTO -> domain mappings
// ---------------------------------------------------------------------------

private fun LeaderboardEntryDto.toDomain(): LeaderboardEntry = LeaderboardEntry(
    rank = rank,
    user = user.toSummary(),
    score = score,
    activeDays = activeDays,
    workoutCount = workoutCount,
    isMe = isMe,
)

private fun LeaderboardDto.toDomain(): Leaderboard = Leaderboard(
    weekStart = week.startDate,
    weekEnd = week.endDate,
    pointsPerDay = scoring.pointsPerDay,
    bonusDays = scoring.bonusDays,
    bonusPoints = scoring.bonusPoints,
    entries = entries.map(LeaderboardEntryDto::toDomain),
)

/**
 * Map a wire user onto the small public view.
 *
 * Every field beyond these four is optional on [UserDto], so this works for both
 * a board entry and a full profile.
 */
private fun UserDto.toSummary(): UserSummary = UserSummary(
    id = id,
    username = username,
    displayName = displayName,
    profileImageUrl = profileImageUrl,
)
