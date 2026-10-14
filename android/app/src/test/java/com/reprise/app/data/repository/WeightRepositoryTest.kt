package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.dto.AuthResponseDto
import com.reprise.app.data.remote.dto.BmiDto
import com.reprise.app.data.remote.dto.CreateWeightRequest
import com.reprise.app.data.remote.dto.CreateWorkoutRequest
import com.reprise.app.data.remote.dto.DashboardDto
import com.reprise.app.data.remote.dto.DashboardWeightDto
import com.reprise.app.data.remote.dto.DashboardWorkoutsDto
import com.reprise.app.data.remote.dto.ExerciseCatalogueDto
import com.reprise.app.data.remote.dto.FriendEnvelopeDto
import com.reprise.app.data.remote.dto.FriendListDto
import com.reprise.app.data.remote.dto.FriendRequestEnvelopeDto
import com.reprise.app.data.remote.dto.FriendRequestsDto
import com.reprise.app.data.remote.dto.LatestWeightEnvelopeDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.ProgressionDto
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.SendFriendRequestRequest
import com.reprise.app.data.remote.dto.TrackedExerciseListDto
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.UpdateWorkoutRequest
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import com.reprise.app.data.remote.dto.UserSearchResultsDto
import com.reprise.app.data.remote.dto.WeightChangeDto
import com.reprise.app.data.remote.dto.WeightEntryDto
import com.reprise.app.data.remote.dto.WeightEntryEnvelopeDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
import com.reprise.app.data.remote.dto.WeightPointDto
import com.reprise.app.data.remote.dto.WorkoutEnvelopeDto
import com.reprise.app.data.remote.dto.WorkoutListDto
import com.reprise.app.domain.model.BmiCategory
import com.reprise.app.domain.model.BmiUnavailableReason
import com.reprise.app.domain.model.WeightUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for [WeightRepository] — the DTO -> domain mapping layer.
 *
 * A hand-written [FakeApi] stands in for Retrofit, so these run on the JVM with
 * no network and no emulator. That matters because this is the seam where a
 * server-side rename (say `weight` -> `value`) would otherwise slip through
 * unnoticed until runtime.
 */
class WeightRepositoryTest {

    private val json = "application/json".toMediaType()

    private fun entryDto(
        id: String = "1",
        weight: Double = 82.4,
        unit: String = "kg",
        weightKg: Double = 82.4,
        measuredAt: String = "2026-10-14T08:33:57.367Z",
        note: String? = null,
    ) = WeightEntryDto(
        id = id,
        weight = weight,
        unit = unit,
        weightKg = weightKg,
        measuredAt = measuredAt,
        note = note,
    )

    @Test
    fun `dashboard maps the wire payload into the domain model`() = runBlocking {
        val api = FakeApi(
            dashboardResponse = Response.success(
                DashboardDto(
                    weight = DashboardWeightDto(
                        latest = entryDto(note = "morning"),
                        previous = entryDto(id = "2", weight = 84.0, weightKg = 84.0),
                        entryCount = 2,
                        changeWindowDays = 30,
                        change = WeightChangeDto(
                            display = -1.6,
                            unit = "kg",
                            kg = -1.6,
                            from = WeightPointDto(84.0, "2026-10-01T08:00:00.000Z"),
                            to = WeightPointDto(82.4, "2026-10-14T08:33:57.367Z"),
                        ),
                    ),
                    bmi = BmiDto(value = 25.4, category = "overweight"),
                )
            )
        )

        val result = WeightRepository(api).dashboard()

        assertTrue(result is ApiResult.Success)
        val dashboard = (result as ApiResult.Success).data
        assertEquals(82.4, dashboard.latestWeight!!.weight, 0.0001)
        assertEquals(WeightUnit.KG, dashboard.latestWeight!!.unit)
        assertEquals("morning", dashboard.latestWeight!!.note)
        assertEquals(2, dashboard.weightEntryCount)
        assertEquals(30, dashboard.changeWindowDays)
        assertEquals(-1.6, dashboard.weightChange!!.amount, 0.0001)
        assertEquals(WeightUnit.KG, dashboard.weightChange!!.unit)
        assertEquals(BmiCategory.OVERWEIGHT, dashboard.bmi!!.category)
        assertEquals(25.4, dashboard.bmi!!.value, 0.0001)
        assertNull(dashboard.bmiUnavailableReason)
    }

    @Test
    fun `a pound account maps pound values and unit`() = runBlocking {
        val api = FakeApi(
            dashboardResponse = Response.success(
                DashboardDto(
                    weight = DashboardWeightDto(
                        latest = entryDto(weight = 181.7, unit = "lb", weightKg = 82.4),
                        entryCount = 1,
                    )
                )
            )
        )

        val dashboard = (WeightRepository(api).dashboard() as ApiResult.Success).data

        assertEquals(181.7, dashboard.latestWeight!!.weight, 0.0001)
        assertEquals(WeightUnit.LB, dashboard.latestWeight!!.unit)
        // The canonical kilograms must survive untouched.
        assertEquals(82.4, dashboard.latestWeight!!.weightKg, 0.0001)
    }

    @Test
    fun `the dashboard carries the training section`() = runBlocking {
        val api = FakeApi(
            dashboardResponse = Response.success(
                DashboardDto(
                    workouts = DashboardWorkoutsDto(
                        totalCount = 12,
                        recentCount = 3,
                        recentDays = 7,
                        recentVolumeKg = 1982.0,
                        lastWorkoutAt = "2026-10-14T08:33:57.367Z",
                    )
                )
            )
        )

        val stats = (WeightRepository(api).dashboard() as ApiResult.Success).data.workouts

        assertEquals(12, stats.totalCount)
        assertEquals(3, stats.recentCount)
        assertEquals(7, stats.recentDays)
        assertEquals(1982.0, stats.recentVolumeKg, 0.0001)
        assertEquals("2026-10-14T08:33:57.367Z", stats.lastWorkoutAt)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun `a missing BMI carries the reason why`() = runBlocking {
        val api = FakeApi(
            dashboardResponse = Response.success(
                DashboardDto(bmi = null, bmiUnavailableReason = "no_height")
            )
        )

        val dashboard = (WeightRepository(api).dashboard() as ApiResult.Success).data

        assertNull(dashboard.bmi)
        assertEquals(BmiUnavailableReason.NO_HEIGHT, dashboard.bmiUnavailableReason)
    }

    @Test
    fun `an unrecognised BMI category is treated as unavailable`() = runBlocking {
        // Showing a bare number with no category would be worse than showing
        // nothing, so an unknown code degrades to "no BMI".
        val api = FakeApi(
            dashboardResponse = Response.success(DashboardDto(bmi = BmiDto(20.0, "vibes")))
        )

        val dashboard = (WeightRepository(api).dashboard() as ApiResult.Success).data

        assertNull(dashboard.bmi)
    }

    @Test
    fun `history maps every entry and the change`() = runBlocking {
        val api = FakeApi(
            historyResponse = Response.success(
                WeightHistoryDto(
                    entries = listOf(entryDto(id = "1"), entryDto(id = "2", weight = 84.0)),
                    count = 2,
                    change = WeightChangeDto(
                        display = -1.6,
                        unit = "kg",
                        kg = -1.6,
                        from = WeightPointDto(84.0, "2026-10-01T08:00:00.000Z"),
                        to = WeightPointDto(82.4, "2026-10-14T08:33:57.367Z"),
                    ),
                    days = 30,
                )
            )
        )

        val history = (WeightRepository(api).history(days = 30) as ApiResult.Success).data

        assertEquals(2, history.entries.size)
        assertEquals("1", history.entries.first().id)
        assertEquals(-1.6, history.change!!.amount, 0.0001)
    }

    @Test
    fun `a history with no comparable entries has a null change`() = runBlocking {
        val api = FakeApi(
            historyResponse = Response.success(WeightHistoryDto(entries = listOf(entryDto()), count = 1))
        )

        val history = (WeightRepository(api).history() as ApiResult.Success).data

        assertEquals(1, history.entries.size)
        assertNull(history.change)
    }

    @Test
    fun `addWeight sends the unit code and the note`() = runBlocking {
        val api = FakeApi(
            createResponse = Response.success(WeightEntryEnvelopeDto(entryDto(note = "morning")))
        )

        val result = WeightRepository(api).addWeight(
            weight = 82.4,
            unit = WeightUnit.LB,
            note = "morning",
        )

        assertTrue(result is ApiResult.Success)
        // The unit must travel as the SERVER's code, not the enum name.
        assertEquals("lb", api.lastCreateRequest!!.unit)
        assertEquals(82.4, api.lastCreateRequest!!.weight, 0.0001)
        assertEquals("morning", api.lastCreateRequest!!.note)
        // Date is left to the server ("now") unless the caller back-dates it.
        assertNull(api.lastCreateRequest!!.measuredAt)
    }

    @Test
    fun `updateWeight sends only the fields it was given`() = runBlocking {
        val api = FakeApi(updateResponse = Response.success(WeightEntryEnvelopeDto(entryDto())))

        WeightRepository(api).updateWeight(id = "abc", weight = 74.5)

        assertEquals(74.5, api.lastUpdateRequest!!.weight!!, 0.0001)
        assertNull(api.lastUpdateRequest!!.note)
        assertNull(api.lastUpdateRequest!!.measuredAt)
    }

    @Test
    fun `deleteWeight reports success for a bodyless response`() = runBlocking {
        val api = FakeApi(deleteResponse = Response.success(Unit))

        assertEquals(ApiResult.Success(Unit), WeightRepository(api).deleteWeight("abc"))
        assertEquals("abc", api.lastDeleteId)
    }

    @Test
    fun `a rejection passes through with the server message intact`() = runBlocking {
        val body = """{"error":{"message":"Authentication required."}}"""
        val api = FakeApi(
            dashboardResponse = Response.error(401, body.toResponseBody(json))
        )

        val result = WeightRepository(api).dashboard()

        val failure = result as ApiResult.Failure
        assertEquals("Authentication required.", failure.message)
        assertEquals(401, failure.statusCode)
    }
}

/**
 * A [RepRiseApi] that returns canned responses and records what it was asked.
 *
 * Only the weight/dashboard calls are exercised here. The auth calls throw, so a
 * future change that starts using them from this repository fails loudly rather
 * than silently returning an empty object.
 */
private class FakeApi(
    private val dashboardResponse: Response<DashboardDto> = Response.success(DashboardDto()),
    private val historyResponse: Response<WeightHistoryDto> = Response.success(WeightHistoryDto()),
    private val createResponse: Response<WeightEntryEnvelopeDto> = Response.success(
        WeightEntryEnvelopeDto(
            WeightEntryDto(
                id = "1",
                weight = 80.0,
                unit = "kg",
                weightKg = 80.0,
                measuredAt = "2026-10-14T08:00:00.000Z",
            )
        )
    ),
    private val updateResponse: Response<WeightEntryEnvelopeDto> = Response.success(
        WeightEntryEnvelopeDto(
            WeightEntryDto(
                id = "1",
                weight = 80.0,
                unit = "kg",
                weightKg = 80.0,
                measuredAt = "2026-10-14T08:00:00.000Z",
            )
        )
    ),
    private val deleteResponse: Response<Unit> = Response.success(Unit),
) : RepRiseApi {

    var lastCreateRequest: CreateWeightRequest? = null
        private set
    var lastUpdateRequest: UpdateWeightRequest? = null
        private set
    var lastDeleteId: String? = null
        private set

    override suspend fun dashboard(): Response<DashboardDto> = dashboardResponse

    override suspend fun weightHistory(days: Int?, limit: Int?): Response<WeightHistoryDto> =
        historyResponse

    override suspend fun createWeight(body: CreateWeightRequest): Response<WeightEntryEnvelopeDto> {
        lastCreateRequest = body
        return createResponse
    }

    override suspend fun updateWeight(
        id: String,
        body: UpdateWeightRequest,
    ): Response<WeightEntryEnvelopeDto> {
        lastUpdateRequest = body
        return updateResponse
    }

    override suspend fun deleteWeight(id: String): Response<Unit> {
        lastDeleteId = id
        return deleteResponse
    }

    // --- Not used by WeightRepository ---------------------------------------

    override suspend fun latestWeight(): Response<LatestWeightEnvelopeDto> =
        throw NotImplementedError("latestWeight is not used by WeightRepository")

    override suspend fun register(body: RegisterRequest): Response<AuthResponseDto> =
        throw NotImplementedError("register is not used by WeightRepository")

    override suspend fun login(body: LoginRequest): Response<AuthResponseDto> =
        throw NotImplementedError("login is not used by WeightRepository")

    override suspend fun me(): Response<UserEnvelopeDto> =
        throw NotImplementedError("me is not used by WeightRepository")

    override suspend fun updateProfile(body: UpdateProfileRequest): Response<UserEnvelopeDto> =
        throw NotImplementedError("updateProfile is not used by WeightRepository")

    // --- Workouts (handled by WorkoutRepositoryTest) -------------------------

    override suspend fun workoutHistory(days: Int?, limit: Int?): Response<WorkoutListDto> =
        throw NotImplementedError("workoutHistory is not used by WeightRepository")

    override suspend fun exerciseCatalogue(): Response<ExerciseCatalogueDto> =
        throw NotImplementedError("exerciseCatalogue is not used by WeightRepository")

    override suspend fun getWorkout(id: String): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("getWorkout is not used by WeightRepository")

    override suspend fun createWorkout(body: CreateWorkoutRequest): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("createWorkout is not used by WeightRepository")

    override suspend fun updateWorkout(
        id: String,
        body: UpdateWorkoutRequest,
    ): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("updateWorkout is not used by WeightRepository")

    override suspend fun deleteWorkout(id: String): Response<Unit> =
        throw NotImplementedError("deleteWorkout is not used by WeightRepository")

    // --- Progression (handled by ProgressionRepositoryTest) ------------------

    override suspend fun progression(exercise: String, limit: Int?): Response<ProgressionDto> =
        throw NotImplementedError("progression is not used by WeightRepository")

    override suspend fun trackedExercises(): Response<TrackedExerciseListDto> =
        throw NotImplementedError("trackedExercises is not used by WeightRepository")

    // --- Friends (Phase 6a; handled by FriendRepositoryTest) -----------------

    override suspend fun searchUsers(username: String): Response<UserSearchResultsDto> =
        throw NotImplementedError("searchUsers is not used by WeightRepository")

    override suspend fun sendFriendRequest(
        body: SendFriendRequestRequest,
    ): Response<FriendRequestEnvelopeDto> =
        throw NotImplementedError("sendFriendRequest is not used by WeightRepository")

    override suspend fun friendRequests(): Response<FriendRequestsDto> =
        throw NotImplementedError("friendRequests is not used by WeightRepository")

    override suspend fun acceptFriendRequest(id: String): Response<FriendEnvelopeDto> =
        throw NotImplementedError("acceptFriendRequest is not used by WeightRepository")

    override suspend fun deleteFriendRequest(id: String): Response<Unit> =
        throw NotImplementedError("deleteFriendRequest is not used by WeightRepository")

    override suspend fun friends(): Response<FriendListDto> =
        throw NotImplementedError("friends is not used by WeightRepository")

    override suspend fun removeFriend(userId: String): Response<Unit> =
        throw NotImplementedError("removeFriend is not used by WeightRepository")
}
