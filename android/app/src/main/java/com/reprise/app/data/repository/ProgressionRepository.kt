package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.apiCall
import com.reprise.app.data.remote.dto.ProgressionComparisonDto
import com.reprise.app.data.remote.dto.ProgressionDto
import com.reprise.app.data.remote.dto.ProgressionSessionDto
import com.reprise.app.data.remote.dto.ProgressionSetDto
import com.reprise.app.data.remote.dto.TrackedExerciseDto
import com.reprise.app.domain.model.ExerciseProgression
import com.reprise.app.domain.model.ProgressionComparison
import com.reprise.app.domain.model.ProgressionSession
import com.reprise.app.domain.model.ProgressionSet
import com.reprise.app.domain.model.ProgressionVerdict
import com.reprise.app.domain.model.TrackedExercise
import com.reprise.app.domain.model.WeightUnit

/**
 * Reads the progressive-overload endpoints.
 *
 * There is nothing to write here: progression is DERIVED from the workouts the
 * user has already logged. This repository therefore only maps the server's
 * DTOs onto domain types — it never decides anything itself. Keeping it that way
 * means the rules stay in one place (the backend) instead of being duplicated,
 * and drifting, on the client.
 */
class ProgressionRepository(private val api: RepRiseApi) {

    /** The trend for one exercise, newest session first. */
    suspend fun progression(exercise: String, limit: Int? = null): ApiResult<ExerciseProgression> =
        apiCall { api.progression(exercise = exercise, limit = limit) }
            .map { it.toDomain() }

    /** The exercises the user has logged before, alphabetically. */
    suspend fun trackedExercises(): ApiResult<List<TrackedExercise>> =
        apiCall { api.trackedExercises() }
            .map { it.exercises.map(TrackedExerciseDto::toDomain) }

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

private fun ProgressionSetDto.toDomain(): ProgressionSet = ProgressionSet(
    reps = reps,
    weight = weight,
    weightKg = weightKg,
)

private fun ProgressionSessionDto.toDomain(): ProgressionSession = ProgressionSession(
    workoutId = workoutId,
    workoutName = workoutName,
    workoutDate = workoutDate,
    setCount = setCount,
    totalReps = totalReps,
    totalVolumeKg = totalVolumeKg,
    topWeight = topWeight,
    sets = sets.map(ProgressionSetDto::toDomain),
)

private fun ProgressionComparisonDto.toDomain(): ProgressionComparison = ProgressionComparison(
    hasPrevious = hasPrevious,
    latest = latest?.toDomain(),
    previous = previous?.toDomain(),
    topWeightDelta = topWeightDelta,
    totalRepsDelta = totalRepsDelta,
    totalVolumeDeltaKg = totalVolumeDeltaKg,
    verdict = ProgressionVerdict.fromApi(verdict),
    suggestion = suggestion,
)

private fun ProgressionDto.toDomain(): ExerciseProgression = ExerciseProgression(
    exercise = exercise,
    unit = WeightUnit.fromApi(unit),
    sessionCount = sessionCount,
    sessions = sessions.map(ProgressionSessionDto::toDomain),
    comparison = comparison.toDomain(),
)

private fun TrackedExerciseDto.toDomain(): TrackedExercise = TrackedExercise(
    name = name,
    timesLogged = timesLogged,
    lastPerformedAt = lastPerformedAt,
)
