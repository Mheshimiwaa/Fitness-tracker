package com.reprise.app.domain.model

/**
 * Domain models for the weekly leaderboard (Phase 6b).
 *
 * Pure Kotlin — no JSON, no HTTP. `LeaderboardRepository` maps the DTOs onto
 * these, so the UI never depends on the wire shape.
 *
 * The important idea: the SERVER decides the scores (and the week) and reports
 * the scoring rules alongside them, so the app can display "10 points per active
 * day, plus 5 for 3+ days" without hard-coding those numbers. Nothing in this
 * file does arithmetic on scores.
 */

/** One person's row on the board. */
data class LeaderboardEntry(
    /** Their position, 1-based. Equal scores share the same rank. */
    val rank: Int,
    /** A deliberately small public view of the person. */
    val user: UserSummary,
    /** Points this week, as calculated by the server. */
    val score: Int,
    /** How many distinct days they trained this week. */
    val activeDays: Int,
    /** How many workouts they logged this week (a day may hold several). */
    val workoutCount: Int,
    /** True for the signed-in user's own row. */
    val isMe: Boolean,
)

/**
 * A whole weekly board: the window it covers, the rules behind the numbers, and
 * the ranked rows.
 */
data class Leaderboard(
    /** Monday 00:00 UTC that opens the week. */
    val weekStart: String?,
    /** The following Monday 00:00 UTC — EXCLUSIVE, so the real last day is one back. */
    val weekEnd: String?,
    val pointsPerDay: Int,
    val bonusDays: Int,
    val bonusPoints: Int,
    val entries: List<LeaderboardEntry>,
) {
    /**
     * My own row, or null if the server somehow left me off the board.
     *
     * Derived from [entries] rather than stored separately so the two can never
     * disagree.
     */
    val me: LeaderboardEntry? get() = entries.firstOrNull { it.isMe }

    companion object {
        val EMPTY = Leaderboard(
            weekStart = null,
            weekEnd = null,
            pointsPerDay = 0,
            bonusDays = 0,
            bonusPoints = 0,
            entries = emptyList(),
        )
    }
}
