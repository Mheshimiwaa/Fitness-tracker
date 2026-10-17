package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.dto.AuthResponseDto
import com.reprise.app.data.remote.dto.CreateWeightRequest
import com.reprise.app.data.remote.dto.CreateWorkoutRequest
import com.reprise.app.data.remote.dto.DashboardDto
import com.reprise.app.data.remote.dto.ExerciseCatalogueDto
import com.reprise.app.data.remote.dto.FriendEnvelopeDto
import com.reprise.app.data.remote.dto.FriendListDto
import com.reprise.app.data.remote.dto.FriendRequestEnvelopeDto
import com.reprise.app.data.remote.dto.FriendRequestsDto
import com.reprise.app.data.remote.dto.LatestWeightEnvelopeDto
import com.reprise.app.data.remote.dto.LeaderboardDto
import com.reprise.app.data.remote.dto.LeaderboardEntryDto
import com.reprise.app.data.remote.dto.LeaderboardScoringDto
import com.reprise.app.data.remote.dto.LeaderboardWeekDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.ProgressionDto
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.SendFriendRequestRequest
import com.reprise.app.data.remote.dto.TrackedExerciseListDto
import com.reprise.app.data.remote.dto.UpdateHeightRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.UpdateWorkoutRequest
import com.reprise.app.data.remote.dto.UserDto
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import com.reprise.app.data.remote.dto.UserSearchResultsDto
import com.reprise.app.data.remote.dto.WeightEntryEnvelopeDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
import com.reprise.app.data.remote.dto.WorkoutEnvelopeDto
import com.reprise.app.data.remote.dto.WorkoutListDto
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for [LeaderboardRepository] — the DTO -> domain mapping layer.
 *
 * The SCORING lives on the server (backend/tests/leaderboard.test.js); what
 * matters here is that the wire shape maps faithfully, that `isMe` survives, and
 * that the derived `me` helper picks the right row.
 */
class LeaderboardRepositoryTest {

    private val json = "application/json".toMediaType()

    private fun userDto(
        id: String = "u1",
        username: String = "alice_lifts",
        displayName: String = "Alice Nguyen",
    ) = UserDto(id = id, username = username, displayName = displayName)

    private fun entryDto(
        rank: Int = 1,
        user: UserDto = userDto(),
        score: Int = 35,
        activeDays: Int = 3,
        workoutCount: Int = 4,
        isMe: Boolean = false,
    ) = LeaderboardEntryDto(
        rank = rank,
        user = user,
        score = score,
        activeDays = activeDays,
        workoutCount = workoutCount,
        isMe = isMe,
    )

    @Test
    fun `the board maps entries, the week and the scoring rules`() = runBlocking {
        val api = FakeLeaderboardApi(
            boardResponse = Response.success(
                LeaderboardDto(
                    week = LeaderboardWeekDto(
                        startDate = "2026-10-12T00:00:00.000Z",
                        endDate = "2026-10-19T00:00:00.000Z",
                    ),
                    scoring = LeaderboardScoringDto(pointsPerDay = 10, bonusDays = 3, bonusPoints = 5),
                    entries = listOf(
                        entryDto(rank = 1, user = userDto(id = "u2", username = "bob_runs")),
                        entryDto(rank = 2, user = userDto(id = "u1"), score = 20, activeDays = 2, isMe = true),
                    ),
                )
            )
        )

        val board = (LeaderboardRepository(api).weekly() as ApiResult.Success).data

        assertEquals(2, board.entries.size)
        assertEquals("2026-10-12T00:00:00.000Z", board.weekStart)
        assertEquals("2026-10-19T00:00:00.000Z", board.weekEnd)
        assertEquals(10, board.pointsPerDay)
        assertEquals(3, board.bonusDays)
        assertEquals(5, board.bonusPoints)
        // The person, not just the id, must survive the mapping.
        assertEquals("bob_runs", board.entries.first().user.username)
    }

    @Test
    fun `the derived me row is the entry flagged isMe`() = runBlocking {
        val api = FakeLeaderboardApi(
            boardResponse = Response.success(
                LeaderboardDto(
                    entries = listOf(
                        entryDto(rank = 1, user = userDto(id = "u2", username = "bob_runs")),
                        entryDto(rank = 2, user = userDto(id = "u1"), score = 20, isMe = true),
                    ),
                )
            )
        )

        val board = (LeaderboardRepository(api).weekly() as ApiResult.Success).data

        assertEquals("u1", board.me?.user?.id)
        assertEquals(2, board.me?.rank)
        assertEquals(20, board.me?.score)
    }

    @Test
    fun `an empty board has no me row and no entries`() = runBlocking {
        val api = FakeLeaderboardApi(boardResponse = Response.success(LeaderboardDto()))

        val board = (LeaderboardRepository(api).weekly() as ApiResult.Success).data

        assertTrue(board.entries.isEmpty())
        assertNull(board.me)
    }

    @Test
    fun `a server failure keeps the server message`() = runBlocking {
        val body = """{"error":{"message":"Not authorised."}}"""
        val api = FakeLeaderboardApi(boardResponse = Response.error(401, body.toResponseBody(json)))

        val failure = LeaderboardRepository(api).weekly() as ApiResult.Failure

        assertEquals("Not authorised.", failure.message)
        assertEquals(401, failure.statusCode)
    }
}

/**
 * A [RepRiseApi] that returns a canned leaderboard and records nothing else.
 * Named `FakeLeaderboardApi` because a private class name must be unique across
 * the whole package. Every other call throws, so a future change that starts
 * using one from this repository fails loudly instead of silently.
 */
private class FakeLeaderboardApi(
    private val boardResponse: Response<LeaderboardDto> = Response.success(LeaderboardDto()),
) : RepRiseApi {

    override suspend fun weeklyLeaderboard(): Response<LeaderboardDto> = boardResponse

    // --- Not used by LeaderboardRepository -----------------------------------

    override suspend fun register(body: RegisterRequest): Response<AuthResponseDto> =
        throw NotImplementedError("register is not used by LeaderboardRepository")

    override suspend fun login(body: LoginRequest): Response<AuthResponseDto> =
        throw NotImplementedError("login is not used by LeaderboardRepository")

    override suspend fun me(): Response<UserEnvelopeDto> =
        throw NotImplementedError("me is not used by LeaderboardRepository")

    override suspend fun updateProfile(body: UpdateProfileRequest): Response<UserEnvelopeDto> =
        throw NotImplementedError("updateProfile is not used by LeaderboardRepository")

    override suspend fun updateHeight(body: UpdateHeightRequest): Response<UserEnvelopeDto> =
        throw NotImplementedError("updateHeight is not used by LeaderboardRepository")

    override suspend fun dashboard(): Response<DashboardDto> =
        throw NotImplementedError("dashboard is not used by LeaderboardRepository")

    override suspend fun weightHistory(days: Int?, limit: Int?): Response<WeightHistoryDto> =
        throw NotImplementedError("weightHistory is not used by LeaderboardRepository")

    override suspend fun latestWeight(): Response<LatestWeightEnvelopeDto> =
        throw NotImplementedError("latestWeight is not used by LeaderboardRepository")

    override suspend fun createWeight(body: CreateWeightRequest): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("createWeight is not used by LeaderboardRepository")

    override suspend fun updateWeight(
        id: String,
        body: UpdateWeightRequest,
    ): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("updateWeight is not used by LeaderboardRepository")

    override suspend fun deleteWeight(id: String): Response<Unit> =
        throw NotImplementedError("deleteWeight is not used by LeaderboardRepository")

    override suspend fun workoutHistory(days: Int?, limit: Int?): Response<WorkoutListDto> =
        throw NotImplementedError("workoutHistory is not used by LeaderboardRepository")

    override suspend fun exerciseCatalogue(): Response<ExerciseCatalogueDto> =
        throw NotImplementedError("exerciseCatalogue is not used by LeaderboardRepository")

    override suspend fun getWorkout(id: String): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("getWorkout is not used by LeaderboardRepository")

    override suspend fun createWorkout(body: CreateWorkoutRequest): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("createWorkout is not used by LeaderboardRepository")

    override suspend fun updateWorkout(
        id: String,
        body: UpdateWorkoutRequest,
    ): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("updateWorkout is not used by LeaderboardRepository")

    override suspend fun deleteWorkout(id: String): Response<Unit> =
        throw NotImplementedError("deleteWorkout is not used by LeaderboardRepository")

    override suspend fun progression(exercise: String, limit: Int?): Response<ProgressionDto> =
        throw NotImplementedError("progression is not used by LeaderboardRepository")

    override suspend fun trackedExercises(): Response<TrackedExerciseListDto> =
        throw NotImplementedError("trackedExercises is not used by LeaderboardRepository")

    override suspend fun searchUsers(username: String): Response<UserSearchResultsDto> =
        throw NotImplementedError("searchUsers is not used by LeaderboardRepository")

    override suspend fun sendFriendRequest(
        body: SendFriendRequestRequest,
    ): Response<FriendRequestEnvelopeDto> =
        throw NotImplementedError("sendFriendRequest is not used by LeaderboardRepository")

    override suspend fun friendRequests(): Response<FriendRequestsDto> =
        throw NotImplementedError("friendRequests is not used by LeaderboardRepository")

    override suspend fun acceptFriendRequest(id: String): Response<FriendEnvelopeDto> =
        throw NotImplementedError("acceptFriendRequest is not used by LeaderboardRepository")

    override suspend fun deleteFriendRequest(id: String): Response<Unit> =
        throw NotImplementedError("deleteFriendRequest is not used by LeaderboardRepository")

    override suspend fun friends(): Response<FriendListDto> =
        throw NotImplementedError("friends is not used by LeaderboardRepository")

    override suspend fun removeFriend(userId: String): Response<Unit> =
        throw NotImplementedError("removeFriend is not used by LeaderboardRepository")
}
