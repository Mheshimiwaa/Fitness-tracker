package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the weekly leaderboard (Phase 6b).
 *
 * Like the friends DTOs, these reuse [UserDto] for the people on the board: it
 * is already "a user with the extra profile fields optional", so one class
 * covers both a summary and a full profile.
 *
 * The server also sends a top-level `me` object (a copy of my own entry). It is
 * deliberately NOT modelled here: the domain derives `me` from the `isMe` flag
 * so there is only one source of truth, and `ignoreUnknownKeys` drops the
 * duplicate harmlessly.
 */

/** The window the board covers. `endDate` is exclusive (the next Monday). */
@Serializable
data class LeaderboardWeekDto(
    val startDate: String? = null,
    val endDate: String? = null,
)

/** The rules behind the scores, so the UI can explain them without hard-coding. */
@Serializable
data class LeaderboardScoringDto(
    val pointsPerDay: Int = 0,
    val bonusDays: Int = 0,
    val bonusPoints: Int = 0,
)

/** One row on the board. */
@Serializable
data class LeaderboardEntryDto(
    val rank: Int = 0,
    val user: UserDto,
    val score: Int = 0,
    val activeDays: Int = 0,
    val workoutCount: Int = 0,
    val isMe: Boolean = false,
)

/** Response of `GET /api/leaderboard/weekly`. */
@Serializable
data class LeaderboardDto(
    val week: LeaderboardWeekDto = LeaderboardWeekDto(),
    val scoring: LeaderboardScoringDto = LeaderboardScoringDto(),
    val entries: List<LeaderboardEntryDto> = emptyList(),
)
