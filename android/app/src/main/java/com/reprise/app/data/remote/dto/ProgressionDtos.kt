package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the progressive-overload endpoints (Phase 5).
 *
 * The server does all the comparison work and sends the verdict plus a
 * ready-to-read [ProgressionComparisonDto.suggestion]; the app only maps those
 * strings onto the domain and draws them. That keeps the rules in ONE place
 * (the backend) so a rule change never needs an app release.
 *
 * Note that [ProgressionComparisonDto.verdict] and `suggestion` deliberately
 * have no default: if the server ever renamed them the app should fail loudly
 * (a parse error we would catch in tests) rather than silently show nothing.
 */

/** One set inside a past session, with the load in both units. */
@Serializable
data class ProgressionSetDto(
    val reps: Int,
    val weight: Double,
    val weightKg: Double = 0.0,
)

/** One past performance of a single exercise. */
@Serializable
data class ProgressionSessionDto(
    val workoutId: String,
    val workoutName: String? = null,
    val workoutDate: String,
    val setCount: Int = 0,
    val totalReps: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val topWeight: Double = 0.0,
    val topWeightKg: Double = 0.0,
    val sets: List<ProgressionSetDto> = emptyList(),
)

/** The rule-based comparison of the latest session against the previous one. */
@Serializable
data class ProgressionComparisonDto(
    val hasPrevious: Boolean = false,
    val latest: ProgressionSessionDto? = null,
    val previous: ProgressionSessionDto? = null,
    val topWeightDelta: Double = 0.0,
    val totalRepsDelta: Int = 0,
    val totalVolumeDeltaKg: Double = 0.0,
    val verdict: String,
    val suggestion: String,
)

/** Response of GET /api/progression?exercise=... */
@Serializable
data class ProgressionDto(
    val exercise: String,
    val unit: String = "kg",
    val sessionCount: Int = 0,
    /** Newest first. */
    val sessions: List<ProgressionSessionDto> = emptyList(),
    val comparison: ProgressionComparisonDto,
)

/** One entry in the "exercises you have logged" list. */
@Serializable
data class TrackedExerciseDto(
    val name: String,
    val timesLogged: Int = 0,
    val lastPerformedAt: String? = null,
)

/** Response of GET /api/progression/exercises. */
@Serializable
data class TrackedExerciseListDto(
    val exercises: List<TrackedExerciseDto> = emptyList(),
)
