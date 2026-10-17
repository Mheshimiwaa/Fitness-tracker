package com.reprise.app.domain.model

/**
 * Domain models for workout logging.
 *
 * These are pure Kotlin — no JSON annotations, no HTTP. The DTOs in
 * `data/remote/dto` are mapped onto them by `WorkoutRepository`, so the UI never
 * depends on the shape of the wire format.
 */

/** One set: how many reps, at what load (already in the user's preferred unit). */
data class WorkoutSet(
    val reps: Int,
    /** The load to DISPLAY, in the exercise's unit. */
    val weight: Double,
    /** The same load in kilograms — the canonical value used for volume maths. */
    val weightKg: Double,
)

/** One exercise within a workout, and the sets performed for it. */
data class WorkoutExercise(
    val name: String,
    val unit: WeightUnit,
    val restSeconds: Int?,
    val notes: String?,
    val sets: List<WorkoutSet>,
)

/**
 * The headline numbers the server derives from a workout.
 *
 * `totalVolumeKg` (tonnage) is the sum of `weight x reps` in kilograms, so two
 * sessions are comparable even if one was logged in pounds.
 */
data class WorkoutSummary(
    val exerciseCount: Int,
    val setCount: Int,
    val totalReps: Int,
    val totalVolumeKg: Double,
)

/** One logged training session. */
data class Workout(
    val id: String,
    val name: String,
    /** ISO-8601 instant, e.g. "2026-10-14T08:33:57.367Z". */
    val workoutDate: String,
    val durationMinutes: Int?,
    val notes: String?,
    val exercises: List<WorkoutExercise>,
    val summary: WorkoutSummary,
)

/**
 * The workouts section of the dashboard: how much the user has been training.
 *
 * "Recent" is a rolling window of [recentDays] days, not a calendar week, so a
 * Monday-morning dashboard does not report zero just because the week has only
 * just begun.
 */
data class WorkoutStats(
    val totalCount: Int,
    val recentCount: Int,
    val recentDays: Int,
    val recentVolumeKg: Double,
    /** ISO-8601 instant of the most recent session, or null when there is none. */
    val lastWorkoutAt: String?,
) {
    /** True when the user has never logged a workout. */
    val isEmpty: Boolean get() = totalCount == 0
}

/**
 * The streak section of the dashboard: consecutive days trained.
 *
 * The server counts days in UTC and decides whether the run is still standing;
 * the app only has to choose the right words for it — see [streakStatus].
 */
data class StreakStats(
    /** Consecutive days up to today (or yesterday, while today is still young). */
    val current: Int,
    /** The best run ever, which survives a broken streak. */
    val longest: Int,
    /** True when a workout has already been logged today. */
    val trainedToday: Boolean,
    /** Distinct days ever trained — zero means the user has never logged one. */
    val activeDays: Int,
)

/**
 * What the streak card should say, derived from the numbers alone.
 *
 * Keeping this a pure function means the awkward part — the difference between
 * "not trained yet today" and "your streak is over" — is unit-tested instead of
 * hiding inside a composable.
 */
enum class StreakStatus {
    /** No workout has ever been logged, so there is nothing to continue. */
    NOT_STARTED,

    /** A run is alive and today has not been trained yet. */
    AT_RISK,

    /** A run is alive and today is already done. */
    ACTIVE,

    /** A run existed and a whole day was missed. */
    ENDED,
}

/**
 * Classify a streak for the UI.
 *
 * AT_RISK is deliberately distinct from ENDED: on the morning after training,
 * the run is still intact and the user should be encouraged, not told they
 * failed. It becomes ENDED only once the server reports the run as gone.
 */
fun streakStatus(streak: StreakStats): StreakStatus = when {
    streak.activeDays == 0 -> StreakStatus.NOT_STARTED
    streak.current == 0 -> StreakStatus.ENDED
    streak.trainedToday -> StreakStatus.ACTIVE
    else -> StreakStatus.AT_RISK
}
