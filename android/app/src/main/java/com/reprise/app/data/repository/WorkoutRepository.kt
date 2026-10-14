package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.apiCall
import com.reprise.app.data.remote.apiCallUnit
import com.reprise.app.data.remote.dto.CreateWorkoutRequest
import com.reprise.app.data.remote.dto.UpdateWorkoutRequest
import com.reprise.app.data.remote.dto.WorkoutDto
import com.reprise.app.data.remote.dto.WorkoutExerciseDto
import com.reprise.app.data.remote.dto.WorkoutExerciseRequest
import com.reprise.app.data.remote.dto.WorkoutSetDto
import com.reprise.app.data.remote.dto.WorkoutSummaryDto
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.domain.model.Workout
import com.reprise.app.domain.model.WorkoutExercise
import com.reprise.app.domain.model.WorkoutSet
import com.reprise.app.domain.model.WorkoutSummary

/**
 * Everything the app needs to read and write workout data.
 *
 * Like [WeightRepository], this is the only place that knows about the wire
 * DTOs: callers get [Workout] and never see a `...Dto`.
 */
class WorkoutRepository(private val api: RepRiseApi) {

    /** Workout history, newest first. */
    suspend fun history(days: Int? = null, limit: Int? = null): ApiResult<List<Workout>> =
        apiCall { api.workoutHistory(days = days, limit = limit) }
            .map { it.workouts.map(WorkoutDto::toDomain) }

    /** The distinct exercise names the user has logged, for suggestion chips. */
    suspend fun exerciseCatalogue(): ApiResult<List<String>> =
        apiCall { api.exerciseCatalogue() }.map { it.exercises }

    /** Load one workout (used when the editor opens for an existing session). */
    suspend fun workout(id: String): ApiResult<Workout> =
        apiCall { api.getWorkout(id) }.map { it.workout.toDomain() }

    /**
     * Log a new workout.
     *
     * @param exercises already validated and in the order the user arranged them
     * @param notes     blank/null when the user left the note empty
     */
    suspend fun addWorkout(
        name: String,
        exercises: List<WorkoutExerciseRequest>,
        durationMinutes: Int? = null,
        notes: String? = null,
    ): ApiResult<Workout> = apiCall {
        api.createWorkout(
            CreateWorkoutRequest(
                name = name,
                durationMinutes = durationMinutes,
                notes = notes,
                exercises = exercises,
            )
        )
    }.map { it.workout.toDomain() }

    /**
     * Edit an existing workout, sending the COMPLETE session (a full replace).
     *
     * Passing `null` for [durationMinutes] or [notes] clears that field — see
     * [UpdateWorkoutRequest] for why this is not a partial update.
     */
    suspend fun updateWorkout(
        id: String,
        name: String,
        exercises: List<WorkoutExerciseRequest>,
        durationMinutes: Int?,
        notes: String?,
    ): ApiResult<Workout> = apiCall {
        api.updateWorkout(
            id,
            UpdateWorkoutRequest(
                name = name,
                durationMinutes = durationMinutes,
                notes = notes,
                exercises = exercises,
            )
        )
    }.map { it.workout.toDomain() }

    /** Delete a workout. The server answers 204, so success is judged by status. */
    suspend fun deleteWorkout(id: String): ApiResult<Unit> =
        apiCallUnit { api.deleteWorkout(id) }

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

private fun WorkoutSetDto.toDomain(): WorkoutSet = WorkoutSet(
    reps = reps,
    weight = weight,
    weightKg = weightKg,
)

private fun WorkoutExerciseDto.toDomain(): WorkoutExercise = WorkoutExercise(
    name = name,
    unit = WeightUnit.fromApi(unit),
    restSeconds = restSeconds,
    notes = notes,
    sets = sets.map(WorkoutSetDto::toDomain),
)

private fun WorkoutSummaryDto.toDomain(): WorkoutSummary = WorkoutSummary(
    exerciseCount = exerciseCount,
    setCount = setCount,
    totalReps = totalReps,
    totalVolumeKg = totalVolumeKg,
)

private fun WorkoutDto.toDomain(): Workout = Workout(
    id = id,
    name = name,
    workoutDate = workoutDate,
    durationMinutes = durationMinutes,
    notes = notes,
    exercises = exercises.map(WorkoutExerciseDto::toDomain),
    summary = summary.toDomain(),
)
