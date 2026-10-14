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
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.UpdateWorkoutRequest
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import com.reprise.app.data.remote.dto.WeightEntryEnvelopeDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
import com.reprise.app.data.remote.dto.WorkoutDto
import com.reprise.app.data.remote.dto.WorkoutEnvelopeDto
import com.reprise.app.data.remote.dto.WorkoutExerciseDto
import com.reprise.app.data.remote.dto.WorkoutExerciseRequest
import com.reprise.app.data.remote.dto.WorkoutListDto
import com.reprise.app.data.remote.dto.WorkoutSetDto
import com.reprise.app.data.remote.dto.WorkoutSetRequest
import com.reprise.app.data.remote.dto.WorkoutSummaryDto
import com.reprise.app.domain.model.WeightUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for [WorkoutRepository] — the DTO -> domain mapping layer, plus the
 * request bodies the editor produces.
 *
 * A hand-written [FakeWorkoutApi] stands in for Retrofit, so these run on the JVM with
 * no network and no emulator. This is the seam where a server-side rename (say
 * `weight` -> `load`) would otherwise slip through until runtime.
 */
class WorkoutRepositoryTest {

    private val json = "application/json".toMediaType()

    private fun setDto(reps: Int = 8, weight: Double = 60.0, weightKg: Double = 60.0) =
        WorkoutSetDto(reps = reps, weight = weight, weightKg = weightKg)

    private fun exerciseDto(
        name: String = "Bench Press",
        unit: String = "kg",
        sets: List<WorkoutSetDto> = listOf(setDto()),
    ) = WorkoutExerciseDto(
        name = name,
        unit = unit,
        restSeconds = 120,
        notes = null,
        sets = sets,
    )

    private fun workoutDto(
        id: String = "w1",
        name: String = "Push day",
        durationMinutes: Int? = 52,
        notes: String? = "felt strong",
        exercises: List<WorkoutExerciseDto> = listOf(exerciseDto()),
        summary: WorkoutSummaryDto = WorkoutSummaryDto(
            exerciseCount = 3,
            setCount = 12,
            totalReps = 96,
            totalVolumeKg = 1982.0,
        ),
    ) = WorkoutDto(
        id = id,
        name = name,
        workoutDate = "2026-10-14T08:33:57.367Z",
        durationMinutes = durationMinutes,
        notes = notes,
        exercises = exercises,
        summary = summary,
    )

    @Test
    fun `history maps the wire payload into the domain model`() = runBlocking {
        val api = FakeWorkoutApi(listResponse = Response.success(WorkoutListDto(listOf(workoutDto()))))

        val workouts = (WorkoutRepository(api).history() as ApiResult.Success).data

        assertEquals(1, workouts.size)
        val workout = workouts.first()
        assertEquals("w1", workout.id)
        assertEquals("Push day", workout.name)
        assertEquals(52, workout.durationMinutes)
        assertEquals("felt strong", workout.notes)
        assertEquals("2026-10-14T08:33:57.367Z", workout.workoutDate)

        val exercise = workout.exercises.single()
        assertEquals("Bench Press", exercise.name)
        assertEquals(WeightUnit.KG, exercise.unit)
        assertEquals(120, exercise.restSeconds)

        // The derived summary must arrive intact — it is what the list shows.
        assertEquals(3, workout.summary.exerciseCount)
        assertEquals(12, workout.summary.setCount)
        assertEquals(96, workout.summary.totalReps)
        assertEquals(1982.0, workout.summary.totalVolumeKg, 0.0001)
    }

    @Test
    fun `a pound account maps the display weight and keeps the kilograms`() = runBlocking {
        val api = FakeWorkoutApi(
            listResponse = Response.success(
                WorkoutListDto(
                    listOf(
                        workoutDto(
                            exercises = listOf(
                                exerciseDto(
                                    unit = "lb",
                                    sets = listOf(setDto(reps = 5, weight = 135.0, weightKg = 61.2)),
                                )
                            )
                        )
                    )
                )
            )
        )

        val workout = (WorkoutRepository(api).history() as ApiResult.Success).data.first()
        val exercise = workout.exercises.first()
        val set = exercise.sets.first()

        assertEquals(WeightUnit.LB, exercise.unit)
        assertEquals(5, set.reps)
        assertEquals(135.0, set.weight, 0.0001)
        // The canonical kilograms must survive untouched — volume maths uses it.
        assertEquals(61.2, set.weightKg, 0.0001)
    }

    @Test
    fun `history asks the server for the window it was given`() = runBlocking {
        val api = FakeWorkoutApi(listResponse = Response.success(WorkoutListDto()))

        WorkoutRepository(api).history(days = 30, limit = 10)

        assertEquals(30, api.lastHistoryDays)
        assertEquals(10, api.lastHistoryLimit)
    }

    @Test
    fun `the exercise catalogue returns the names`() = runBlocking {
        val api = FakeWorkoutApi(
            catalogueResponse = Response.success(
                ExerciseCatalogueDto(listOf("Bench Press", "Pull-up", "Squat"))
            )
        )

        val names = (WorkoutRepository(api).exerciseCatalogue() as ApiResult.Success).data

        assertEquals(listOf("Bench Press", "Pull-up", "Squat"), names)
    }

    @Test
    fun `workout loads a single session`() = runBlocking {
        val api = FakeWorkoutApi(getResponse = Response.success(WorkoutEnvelopeDto(workoutDto(id = "w9"))))

        val workout = (WorkoutRepository(api).workout("w9") as ApiResult.Success).data

        assertEquals("w9", workout.id)
        assertEquals("w9", api.lastGetId)
    }

    @Test
    fun `addWorkout sends the name, duration, note and sets without a unit`() = runBlocking {
        val api = FakeWorkoutApi(createResponse = Response.success(WorkoutEnvelopeDto(workoutDto())))

        val result = WorkoutRepository(api).addWorkout(
            name = "Push day",
            exercises = listOf(
                WorkoutExerciseRequest(
                    name = "Bench Press",
                    sets = listOf(WorkoutSetRequest(reps = 8, weight = 60.0)),
                )
            ),
            durationMinutes = 52,
            notes = "felt strong",
        )

        assertTrue(result is ApiResult.Success)
        val body = api.lastCreateRequest!!
        assertEquals("Push day", body.name)
        assertEquals(52, body.durationMinutes)
        assertEquals("felt strong", body.notes)
        val exercise = body.exercises.single()
        assertEquals("Bench Press", exercise.name)
        // The unit is deliberately left to the server (the user's preference).
        assertNull(exercise.unit)
        assertEquals(8, exercise.sets.single().reps)
        assertEquals(60.0, exercise.sets.single().weight, 0.0001)
        // The date is left to the server ("now") unless the caller back-dates it.
        assertNull(body.workoutDate)
    }

    @Test
    fun `updateWorkout sends an explicit null to clear the duration and note`() {
        // This is the crux of the "clear a field" behaviour: the request properties
        // have NO defaults, so kotlinx.serialization always encodes them — an
        // explicit null included. A default would silently drop it instead.
        val body = UpdateWorkoutRequest(
            name = "Push day",
            durationMinutes = null,
            notes = null,
            exercises = emptyList(),
        )

        val encoded = Json.encodeToString(UpdateWorkoutRequest.serializer(), body).let { raw ->
            Json.parseToJsonElement(raw).jsonObject
        }

        assertTrue("durationMinutes must be present", encoded.containsKey("durationMinutes"))
        assertTrue("notes must be present", encoded.containsKey("notes"))
        assertEquals("null", encoded["durationMinutes"]!!.jsonPrimitive.content)
        assertEquals("null", encoded["notes"]!!.jsonPrimitive.content)
        assertEquals("Push day", encoded["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `deleteWorkout reports success for a bodyless response`() = runBlocking {
        val api = FakeWorkoutApi(deleteResponse = Response.success(Unit))

        assertEquals(ApiResult.Success(Unit), WorkoutRepository(api).deleteWorkout("w1"))
        assertEquals("w1", api.lastDeleteId)
    }

    @Test
    fun `a rejection passes through with the server message intact`() = runBlocking {
        val body = """{"error":{"message":"Workout not found."}}"""
        val api = FakeWorkoutApi(listResponse = Response.error(404, body.toResponseBody(json)))

        val result = WorkoutRepository(api).history()

        val failure = result as ApiResult.Failure
        assertEquals("Workout not found.", failure.message)
        assertEquals(404, failure.statusCode)
    }
}

/**
 * A [RepRiseApi] that returns canned responses and records what it was asked.
 *
 * Only the workout calls are exercised here. The auth/weight calls throw, so a
 * future change that starts using them from this repository fails loudly rather
 * than silently returning an empty object.
 */
private class FakeWorkoutApi(
    private val listResponse: Response<WorkoutListDto> = Response.success(WorkoutListDto()),
    private val catalogueResponse: Response<ExerciseCatalogueDto> =
        Response.success(ExerciseCatalogueDto()),
    private val getResponse: Response<WorkoutEnvelopeDto> = Response.success(
        WorkoutEnvelopeDto(
            WorkoutDto(
                id = "w1",
                name = "Push day",
                workoutDate = "2026-10-14T08:33:57.367Z",
            )
        )
    ),
    private val createResponse: Response<WorkoutEnvelopeDto> = Response.success(
        WorkoutEnvelopeDto(
            WorkoutDto(
                id = "w1",
                name = "Push day",
                workoutDate = "2026-10-14T08:33:57.367Z",
            )
        )
    ),
    private val updateResponse: Response<WorkoutEnvelopeDto> = Response.success(
        WorkoutEnvelopeDto(
            WorkoutDto(
                id = "w1",
                name = "Push day",
                workoutDate = "2026-10-14T08:33:57.367Z",
            )
        )
    ),
    private val deleteResponse: Response<Unit> = Response.success(Unit),
) : RepRiseApi {

    var lastHistoryDays: Int? = null
        private set
    var lastHistoryLimit: Int? = null
        private set
    var lastGetId: String? = null
        private set
    var lastCreateRequest: CreateWorkoutRequest? = null
        private set
    var lastUpdateRequest: UpdateWorkoutRequest? = null
        private set
    var lastDeleteId: String? = null
        private set

    override suspend fun workoutHistory(days: Int?, limit: Int?): Response<WorkoutListDto> {
        lastHistoryDays = days
        lastHistoryLimit = limit
        return listResponse
    }

    override suspend fun exerciseCatalogue(): Response<ExerciseCatalogueDto> = catalogueResponse

    override suspend fun getWorkout(id: String): Response<WorkoutEnvelopeDto> {
        lastGetId = id
        return getResponse
    }

    override suspend fun createWorkout(body: CreateWorkoutRequest): Response<WorkoutEnvelopeDto> {
        lastCreateRequest = body
        return createResponse
    }

    override suspend fun updateWorkout(
        id: String,
        body: UpdateWorkoutRequest,
    ): Response<WorkoutEnvelopeDto> {
        lastUpdateRequest = body
        return updateResponse
    }

    override suspend fun deleteWorkout(id: String): Response<Unit> {
        lastDeleteId = id
        return deleteResponse
    }

    // --- Not used by WorkoutRepository ---------------------------------------

    override suspend fun register(body: RegisterRequest): Response<AuthResponseDto> =
        throw NotImplementedError("register is not used by WorkoutRepository")

    override suspend fun login(body: LoginRequest): Response<AuthResponseDto> =
        throw NotImplementedError("login is not used by WorkoutRepository")

    override suspend fun me(): Response<UserEnvelopeDto> =
        throw NotImplementedError("me is not used by WorkoutRepository")

    override suspend fun updateProfile(body: UpdateProfileRequest): Response<UserEnvelopeDto> =
        throw NotImplementedError("updateProfile is not used by WorkoutRepository")

    override suspend fun dashboard(): Response<DashboardDto> =
        throw NotImplementedError("dashboard is not used by WorkoutRepository")

    override suspend fun weightHistory(days: Int?, limit: Int?): Response<WeightHistoryDto> =
        throw NotImplementedError("weightHistory is not used by WorkoutRepository")

    override suspend fun latestWeight(): Response<LatestWeightEnvelopeDto> =
        throw NotImplementedError("latestWeight is not used by WorkoutRepository")

    override suspend fun createWeight(body: CreateWeightRequest): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("createWeight is not used by WorkoutRepository")

    override suspend fun updateWeight(
        id: String,
        body: UpdateWeightRequest,
    ): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("updateWeight is not used by WorkoutRepository")

    override suspend fun deleteWeight(id: String): Response<Unit> =
        throw NotImplementedError("deleteWeight is not used by WorkoutRepository")
}
