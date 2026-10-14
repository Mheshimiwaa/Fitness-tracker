package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.dto.AuthResponseDto
import com.reprise.app.data.remote.dto.CreateWeightRequest
import com.reprise.app.data.remote.dto.CreateWorkoutRequest
import com.reprise.app.data.remote.dto.DashboardDto
import com.reprise.app.data.remote.dto.ExerciseCatalogueDto
import com.reprise.app.data.remote.dto.LatestWeightEnvelopeDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.ProgressionComparisonDto
import com.reprise.app.data.remote.dto.ProgressionDto
import com.reprise.app.data.remote.dto.ProgressionSessionDto
import com.reprise.app.data.remote.dto.ProgressionSetDto
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.TrackedExerciseDto
import com.reprise.app.data.remote.dto.TrackedExerciseListDto
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.UpdateWorkoutRequest
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import com.reprise.app.data.remote.dto.WeightEntryEnvelopeDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
import com.reprise.app.data.remote.dto.WorkoutEnvelopeDto
import com.reprise.app.data.remote.dto.WorkoutListDto
import com.reprise.app.domain.model.ProgressionVerdict
import com.reprise.app.domain.model.WeightUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for [ProgressionRepository] — the DTO -> domain mapping layer.
 *
 * The rules themselves are tested on the SERVER (backend/tests/progression.test.js).
 * What matters here is that the wire shape maps faithfully: the numbers reach the
 * UI unchanged, and the verdict string becomes the right enum — including the
 * deliberate fallback that keeps an older app alive against a newer server.
 */
class ProgressionRepositoryTest {

    private val json = "application/json".toMediaType()

    private fun setDto(reps: Int = 8, weight: Double = 65.0, weightKg: Double = 65.0) =
        ProgressionSetDto(reps = reps, weight = weight, weightKg = weightKg)

    private fun sessionDto(
        workoutId: String = "w2",
        workoutDate: String = "2026-10-08T10:00:00.000Z",
        topWeight: Double = 65.0,
        topWeightKg: Double = 65.0,
        totalReps: Int = 24,
        totalVolumeKg: Double = 1560.0,
        sets: List<ProgressionSetDto> = listOf(setDto()),
    ) = ProgressionSessionDto(
        workoutId = workoutId,
        workoutName = "Push day",
        workoutDate = workoutDate,
        setCount = sets.size,
        totalReps = totalReps,
        totalVolumeKg = totalVolumeKg,
        topWeight = topWeight,
        topWeightKg = topWeightKg,
        sets = sets,
    )

    private fun comparisonDto(
        verdict: String = "progressing",
        suggestion: String = "Progress: aim for 67.5 kg next time.",
        hasPrevious: Boolean = true,
    ) = ProgressionComparisonDto(
        hasPrevious = hasPrevious,
        latest = sessionDto(),
        previous = sessionDto(workoutId = "w1", topWeight = 60.0, topWeightKg = 60.0),
        topWeightDelta = 5.0,
        totalRepsDelta = 0,
        totalVolumeDeltaKg = 120.0,
        verdict = verdict,
        suggestion = suggestion,
    )

    @Test
    fun `the progression payload maps onto the domain model`() = runBlocking {
        val api = FakeProgressionApi(
            progressionResponse = Response.success(
                ProgressionDto(
                    exercise = "Bench Press",
                    unit = "kg",
                    sessionCount = 2,
                    sessions = listOf(
                        sessionDto(workoutId = "w2", topWeight = 65.0, topWeightKg = 65.0),
                        sessionDto(workoutId = "w1", topWeight = 60.0, topWeightKg = 60.0),
                    ),
                    comparison = comparisonDto(),
                )
            )
        )

        val progression = (ProgressionRepository(api).progression("Bench Press") as ApiResult.Success).data

        assertEquals("Bench Press", progression.exercise)
        assertEquals(WeightUnit.KG, progression.unit)
        assertEquals(2, progression.sessionCount)
        assertEquals(2, progression.sessions.size)

        // Newest first, exactly as the server ordered them.
        assertEquals("w2", progression.sessions.first().workoutId)
        assertEquals(65.0, progression.sessions.first().topWeight, 0.0001)

        val comparison = progression.comparison
        assertTrue(comparison.hasPrevious)
        assertEquals(ProgressionVerdict.PROGRESSING, comparison.verdict)
        assertEquals(5.0, comparison.topWeightDelta, 0.0001)
        assertEquals(120.0, comparison.totalVolumeDeltaKg, 0.0001)
        // The sets are mapped too, not dropped along the way.
        assertEquals(8, comparison.latest?.sets?.first()?.reps)
        assertEquals("Progress: aim for 67.5 kg next time.", comparison.suggestion)
    }

    @Test
    fun `every verdict the server can send maps to its enum`() = runBlocking {
        val cases = mapOf(
            "progressing" to ProgressionVerdict.PROGRESSING,
            "holding" to ProgressionVerdict.HOLDING,
            "regressing" to ProgressionVerdict.REGRESSING,
            "insufficient_data" to ProgressionVerdict.INSUFFICIENT_DATA,
        )

        cases.forEach { (wire, expected) ->
            val api = FakeProgressionApi(
                progressionResponse = Response.success(
                    ProgressionDto(
                        exercise = "Bench Press",
                        comparison = comparisonDto(verdict = wire),
                    )
                )
            )
            val progression = (ProgressionRepository(api).progression("Bench Press") as ApiResult.Success).data
            assertEquals("verdict $wire", expected, progression.comparison.verdict)
        }
    }

    @Test
    fun `an unknown verdict falls back to insufficient data rather than crashing`() = runBlocking {
        val api = FakeProgressionApi(
            progressionResponse = Response.success(
                ProgressionDto(
                    exercise = "Bench Press",
                    comparison = comparisonDto(verdict = "superhuman"),
                )
            )
        )

        val progression = (ProgressionRepository(api).progression("Bench Press") as ApiResult.Success).data

        assertEquals(ProgressionVerdict.INSUFFICIENT_DATA, progression.comparison.verdict)
    }

    @Test
    fun `a pound account keeps its display unit and weights`() = runBlocking {
        val api = FakeProgressionApi(
            progressionResponse = Response.success(
                ProgressionDto(
                    exercise = "Bench Press",
                    unit = "lb",
                    sessions = listOf(
                        sessionDto(topWeight = 143.0, topWeightKg = 64.9, sets = listOf(setDto(weight = 143.0, weightKg = 64.9))),
                    ),
                    comparison = comparisonDto(),
                )
            )
        )

        val progression = (ProgressionRepository(api).progression("Bench Press") as ApiResult.Success).data

        assertEquals(WeightUnit.LB, progression.unit)
        val set = progression.sessions.first().sets.first()
        assertEquals(143.0, set.weight, 0.0001)
        // The canonical kilograms survive untouched.
        assertEquals(64.9, set.weightKg, 0.0001)
    }

    @Test
    fun `an exercise with no history is an empty trend, not an error`() = runBlocking {
        val api = FakeProgressionApi(
            progressionResponse = Response.success(
                ProgressionDto(
                    exercise = "Front Squat",
                    sessionCount = 0,
                    sessions = emptyList(),
                    comparison = ProgressionComparisonDto(
                        hasPrevious = false,
                        latest = null,
                        previous = null,
                        verdict = "insufficient_data",
                        suggestion = "Log this exercise to start tracking your progress.",
                    ),
                )
            )
        )

        val progression = (ProgressionRepository(api).progression("Front Squat") as ApiResult.Success).data

        assertEquals(0, progression.sessionCount)
        assertTrue(progression.sessions.isEmpty())
        assertNull(progression.comparison.latest)
        assertEquals(ProgressionVerdict.INSUFFICIENT_DATA, progression.comparison.verdict)
    }

    @Test
    fun `progression passes the exercise and limit through to the api`() = runBlocking {
        val api = FakeProgressionApi(
            progressionResponse = Response.success(
                ProgressionDto(exercise = "Squat", comparison = comparisonDto())
            )
        )

        ProgressionRepository(api).progression("Squat", limit = 5)

        assertEquals("Squat", api.lastExercise)
        assertEquals(5, api.lastLimit)
    }

    @Test
    fun `the tracked exercise list maps names, counts and last dates`() = runBlocking {
        val api = FakeProgressionApi(
            listResponse = Response.success(
                TrackedExerciseListDto(
                    listOf(
                        TrackedExerciseDto("Barbell Row", timesLogged = 3, lastPerformedAt = "2026-10-06T10:00:00.000Z"),
                        TrackedExerciseDto("Bench Press", timesLogged = 5, lastPerformedAt = "2026-10-08T10:00:00.000Z"),
                    )
                )
            )
        )

        val exercises = (ProgressionRepository(api).trackedExercises() as ApiResult.Success).data

        assertEquals(2, exercises.size)
        assertEquals("Barbell Row", exercises.first().name)
        assertEquals(3, exercises.first().timesLogged)
        assertEquals("2026-10-08T10:00:00.000Z", exercises[1].lastPerformedAt)
    }

    @Test
    fun `a rejection passes through with the server message intact`() = runBlocking {
        val body = """{"error":{"message":"Validation failed."}}"""
        val api = FakeProgressionApi(
            progressionResponse = Response.error(400, body.toResponseBody(json))
        )

        val result = ProgressionRepository(api).progression("Bench Press")

        val failure = result as ApiResult.Failure
        assertEquals("Validation failed.", failure.message)
        assertEquals(400, failure.statusCode)
    }
}

/**
 * A [RepRiseApi] that returns canned progression responses and records what it
 * was asked. Named `FakeProgressionApi` (not `FakeApi`) because a private class
 * name must be unique across the whole package.
 *
 * Every other call throws, so a future change that starts using one from this
 * repository fails loudly instead of silently returning an empty object.
 */
private class FakeProgressionApi(
    private val progressionResponse: Response<ProgressionDto> = Response.success(
        ProgressionDto(
            exercise = "Bench Press",
            comparison = ProgressionComparisonDto(verdict = "insufficient_data", suggestion = "Log again."),
        )
    ),
    private val listResponse: Response<TrackedExerciseListDto> =
        Response.success(TrackedExerciseListDto()),
) : RepRiseApi {

    var lastExercise: String? = null
        private set
    var lastLimit: Int? = null
        private set

    override suspend fun progression(exercise: String, limit: Int?): Response<ProgressionDto> {
        lastExercise = exercise
        lastLimit = limit
        return progressionResponse
    }

    override suspend fun trackedExercises(): Response<TrackedExerciseListDto> = listResponse

    // --- Not used by ProgressionRepository ------------------------------------

    override suspend fun register(body: RegisterRequest): Response<AuthResponseDto> =
        throw NotImplementedError("register is not used by ProgressionRepository")

    override suspend fun login(body: LoginRequest): Response<AuthResponseDto> =
        throw NotImplementedError("login is not used by ProgressionRepository")

    override suspend fun me(): Response<UserEnvelopeDto> =
        throw NotImplementedError("me is not used by ProgressionRepository")

    override suspend fun updateProfile(body: UpdateProfileRequest): Response<UserEnvelopeDto> =
        throw NotImplementedError("updateProfile is not used by ProgressionRepository")

    override suspend fun dashboard(): Response<DashboardDto> =
        throw NotImplementedError("dashboard is not used by ProgressionRepository")

    override suspend fun weightHistory(days: Int?, limit: Int?): Response<WeightHistoryDto> =
        throw NotImplementedError("weightHistory is not used by ProgressionRepository")

    override suspend fun latestWeight(): Response<LatestWeightEnvelopeDto> =
        throw NotImplementedError("latestWeight is not used by ProgressionRepository")

    override suspend fun createWeight(body: CreateWeightRequest): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("createWeight is not used by ProgressionRepository")

    override suspend fun updateWeight(
        id: String,
        body: UpdateWeightRequest,
    ): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("updateWeight is not used by ProgressionRepository")

    override suspend fun deleteWeight(id: String): Response<Unit> =
        throw NotImplementedError("deleteWeight is not used by ProgressionRepository")

    override suspend fun workoutHistory(days: Int?, limit: Int?): Response<WorkoutListDto> =
        throw NotImplementedError("workoutHistory is not used by ProgressionRepository")

    override suspend fun exerciseCatalogue(): Response<ExerciseCatalogueDto> =
        throw NotImplementedError("exerciseCatalogue is not used by ProgressionRepository")

    override suspend fun getWorkout(id: String): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("getWorkout is not used by ProgressionRepository")

    override suspend fun createWorkout(body: CreateWorkoutRequest): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("createWorkout is not used by ProgressionRepository")

    override suspend fun updateWorkout(
        id: String,
        body: UpdateWorkoutRequest,
    ): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("updateWorkout is not used by ProgressionRepository")

    override suspend fun deleteWorkout(id: String): Response<Unit> =
        throw NotImplementedError("deleteWorkout is not used by ProgressionRepository")
}
