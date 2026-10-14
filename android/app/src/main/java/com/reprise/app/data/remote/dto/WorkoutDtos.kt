package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the workout endpoints.
 *
 * As with weights, every set arrives with `weight` already converted into the
 * user's preferred unit and `weightKg` alongside it as the canonical value, so
 * no screen ever has to do unit maths.
 *
 * The server also sends `createdAt`/`updatedAt`; those are deliberately not
 * modelled (the JSON parser ignores unknown keys).
 */

/** One set: how many reps, at what load. */
@Serializable
data class WorkoutSetDto(
    val reps: Int,
    val weight: Double,
    val weightKg: Double,
)

/** One exercise and its sets, in the user's current preferred unit. */
@Serializable
data class WorkoutExerciseDto(
    val name: String,
    val unit: String,
    val restSeconds: Int? = null,
    val notes: String? = null,
    val sets: List<WorkoutSetDto> = emptyList(),
)

/** Derived totals for a workout (the server never stores these). */
@Serializable
data class WorkoutSummaryDto(
    val exerciseCount: Int = 0,
    val setCount: Int = 0,
    val totalReps: Int = 0,
    val totalVolumeKg: Double = 0.0,
)

/** One logged training session. */
@Serializable
data class WorkoutDto(
    val id: String,
    val name: String,
    val workoutDate: String,
    val durationMinutes: Int? = null,
    val notes: String? = null,
    val exercises: List<WorkoutExerciseDto> = emptyList(),
    val summary: WorkoutSummaryDto = WorkoutSummaryDto(),
)

/** Response of POST/PATCH/GET /api/workouts/:id — `{"workout": {...}}`. */
@Serializable
data class WorkoutEnvelopeDto(
    val workout: WorkoutDto,
)

/** Response of GET /api/workouts. */
@Serializable
data class WorkoutListDto(
    val workouts: List<WorkoutDto> = emptyList(),
    val count: Int = 0,
    val days: Int? = null,
)

/** Response of GET /api/workouts/exercises — distinct names for suggestions. */
@Serializable
data class ExerciseCatalogueDto(
    val exercises: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// Request bodies
// ---------------------------------------------------------------------------

/** One set in a create/update request. */
@Serializable
data class WorkoutSetRequest(
    val reps: Int,
    val weight: Double,
)

/**
 * One exercise in a create/update request.
 *
 * `unit` is left null so the server defaults it to the user's preference. The
 * sets' weights are then interpreted in that unit, which is exactly the unit
 * they were already displayed in when the editor was opened.
 */
@Serializable
data class WorkoutExerciseRequest(
    val name: String,
    val unit: String? = null,
    val restSeconds: Int? = null,
    val notes: String? = null,
    val sets: List<WorkoutSetRequest>,
)

/** Body for POST /api/workouts. */
@Serializable
data class CreateWorkoutRequest(
    val name: String,
    val workoutDate: String? = null,
    val durationMinutes: Int? = null,
    val notes: String? = null,
    val exercises: List<WorkoutExerciseRequest>,
)

/**
 * Body for PATCH /api/workouts/:id.
 *
 * NOTE: none of these properties has a default, so kotlinx.serialization always
 * encodes them — including an explicit `null`. That is deliberate: the editor
 * always saves the COMPLETE workout it is showing (a full replace), and the
 * server treats `durationMinutes: null` / `notes: null` as "clear this field".
 * Fields with defaults would be silently dropped when null, making it
 * impossible to remove a duration.
 *
 * `exercises` replaces the whole list, which matches the editor's semantics.
 * The date is NOT sent, so an existing session keeps the day it was logged on.
 */
@Serializable
data class UpdateWorkoutRequest(
    val name: String,
    val durationMinutes: Int?,
    val notes: String?,
    val exercises: List<WorkoutExerciseRequest>,
)

/** The workouts section of the dashboard. */
@Serializable
data class DashboardWorkoutsDto(
    val totalCount: Int = 0,
    val recentCount: Int = 0,
    val recentDays: Int = 7,
    val recentVolumeKg: Double = 0.0,
    val lastWorkoutAt: String? = null,
)
