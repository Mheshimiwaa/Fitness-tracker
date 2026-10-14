package com.reprise.app.domain.model

import androidx.annotation.StringRes
import com.reprise.app.R

/**
 * Domain models for the progressive-overload assistant (Phase 5).
 *
 * Pure Kotlin — no JSON, no HTTP. `ProgressionRepository` maps the DTOs onto
 * these, so the UI never depends on the wire shape.
 *
 * The important idea: the server decides the verdict, and the app only decides
 * how to draw it. All of the maths (and every threshold) lives in
 * `backend/src/services/progression.service.js`.
 */

/** One set inside a past session, with the load in the user's current unit. */
data class ProgressionSet(
    val reps: Int,
    val weight: Double,
    val weightKg: Double,
)

/** One past performance of an exercise — a single occurrence within a workout. */
data class ProgressionSession(
    val workoutId: String,
    val workoutName: String?,
    val workoutDate: String,
    val setCount: Int,
    val totalReps: Int,
    /** Tonnage in kilograms, so sessions stay comparable across units. */
    val totalVolumeKg: Double,
    /** The heaviest set, in the user's current preferred unit. */
    val topWeight: Double,
    val sets: List<ProgressionSet>,
)

/**
 * What the assistant concluded from the last two sessions.
 *
 * `apiValue` must match the server's strings exactly (see
 * `backend/src/services/progression.service.js`).
 */
enum class ProgressionVerdict(
    val apiValue: String,
    @param:StringRes val labelRes: Int,
) {
    PROGRESSING("progressing", R.string.progression_verdict_progressing),
    HOLDING("holding", R.string.progression_verdict_holding),
    REGRESSING("regressing", R.string.progression_verdict_regressing),
    INSUFFICIENT_DATA("insufficient_data", R.string.progression_verdict_insufficient);

    companion object {
        /**
         * Map a server verdict.
         *
         * An unrecognised value falls back to [INSUFFICIENT_DATA] rather than
         * throwing: a newer server must not crash an older app, and "we cannot
         * tell yet" is the safe thing to show when the verdict is unknown.
         */
        fun fromApi(value: String?): ProgressionVerdict =
            entries.firstOrNull { it.apiValue == value } ?: INSUFFICIENT_DATA
    }
}

/** The latest-vs-previous comparison, plus the assistant's one-line suggestion. */
data class ProgressionComparison(
    val hasPrevious: Boolean,
    val latest: ProgressionSession?,
    val previous: ProgressionSession?,
    /** Heaviest-set change, in the user's current unit. */
    val topWeightDelta: Double,
    val totalRepsDelta: Int,
    /** Tonnage change in kilograms (the display figures stay in the user's unit). */
    val totalVolumeDeltaKg: Double,
    val verdict: ProgressionVerdict,
    val suggestion: String,
)

/** Everything the Progress detail screen needs for one exercise. */
data class ExerciseProgression(
    val exercise: String,
    val unit: WeightUnit,
    val sessionCount: Int,
    /** Newest first. */
    val sessions: List<ProgressionSession>,
    val comparison: ProgressionComparison,
)

/** One exercise the user has logged before, for the Progress list. */
data class TrackedExercise(
    val name: String,
    val timesLogged: Int,
    val lastPerformedAt: String?,
)
