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
import com.reprise.app.data.remote.dto.FriendRequestDto
import com.reprise.app.data.remote.dto.FriendRequestEnvelopeDto
import com.reprise.app.data.remote.dto.FriendRequestsDto
import com.reprise.app.data.remote.dto.LatestWeightEnvelopeDto
import com.reprise.app.data.remote.dto.LeaderboardDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.ProgressionDto
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.SendFriendRequestRequest
import com.reprise.app.data.remote.dto.TrackedExerciseListDto
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
import com.reprise.app.domain.model.FriendRequestDirection
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for [FriendRepository] — the DTO -> domain mapping layer.
 *
 * The friendship RULES live on the server (backend/tests/friends.test.js); what
 * matters here is that each call reaches the right endpoint with the right
 * argument, and that the wire shape maps faithfully onto the domain models.
 */
class FriendRepositoryTest {

    private val json = "application/json".toMediaType()

    private fun userDto(
        id: String = "u1",
        username: String = "alice_lifts",
        displayName: String = "Alice Nguyen",
    ) = UserDto(id = id, username = username, displayName = displayName)

    private fun requestDto(
        id: String = "r1",
        direction: String = "incoming",
        user: UserDto = userDto(),
    ) = FriendRequestDto(id = id, direction = direction, user = user, createdAt = "2026-10-14T09:00:00.000Z")

    @Test
    fun `the friend list maps onto user summaries`() = runBlocking {
        val api = FakeFriendApi(
            friendsResponse = Response.success(
                FriendListDto(
                    friends = listOf(
                        userDto(id = "u1", username = "alice_lifts", displayName = "Alice Nguyen"),
                        userDto(id = "u2", username = "bob_runs", displayName = "Bob Carter"),
                    )
                )
            )
        )

        val friends = (FriendRepository(api).friends() as ApiResult.Success).data

        assertEquals(2, friends.size)
        assertEquals("Alice Nguyen", friends.first().displayName)
        assertEquals("bob_runs", friends[1].username)
        assertEquals("u2", friends[1].id)
    }

    @Test
    fun `search passes the query through and maps the results`() = runBlocking {
        val api = FakeFriendApi(
            searchResponse = Response.success(
                UserSearchResultsDto(users = listOf(userDto(id = "u9", username = "carol_k", displayName = "Carol Diaz")))
            )
        )

        val results = (FriendRepository(api).searchUsers("car") as ApiResult.Success).data

        assertEquals("car", api.lastSearchQuery)
        assertEquals(1, results.size)
        assertEquals("carol_k", results.first().username)
    }

    @Test
    fun `requests keep their direction and the other person`() = runBlocking {
        val api = FakeFriendApi(
            requestsResponse = Response.success(
                FriendRequestsDto(
                    incoming = listOf(
                        requestDto(id = "r1", direction = "incoming", user = userDto(id = "u2", username = "bob_runs")),
                    ),
                    outgoing = listOf(
                        requestDto(id = "r2", direction = "outgoing", user = userDto(id = "u3", username = "carol_k")),
                    ),
                )
            )
        )

        val requests = (FriendRepository(api).requests() as ApiResult.Success).data

        assertEquals(1, requests.incoming.size)
        assertEquals(1, requests.outgoing.size)
        assertEquals(FriendRequestDirection.INCOMING, requests.incoming.first().direction)
        assertEquals(FriendRequestDirection.OUTGOING, requests.outgoing.first().direction)
        // The id is needed to accept or decline, so it must survive the mapping.
        assertEquals("r1", requests.incoming.first().id)
        assertEquals("bob_runs", requests.incoming.first().user.username)
        assertEquals("2026-10-14T09:00:00.000Z", requests.incoming.first().createdAt)
    }

    @Test
    fun `a direction the app does not know is skipped rather than crashing`() = runBlocking {
        val api = FakeFriendApi(
            requestsResponse = Response.success(
                FriendRequestsDto(
                    incoming = listOf(
                        requestDto(id = "r1", direction = "incoming"),
                        requestDto(id = "r2", direction = "telepathic"),
                    ),
                )
            )
        )

        val requests = (FriendRepository(api).requests() as ApiResult.Success).data

        // The unknown one is dropped; the known one still arrives.
        assertEquals(1, requests.incoming.size)
        assertEquals("r1", requests.incoming.first().id)
    }

    @Test
    fun `sending a request posts the username`() = runBlocking {
        val api = FakeFriendApi()

        val result = FriendRepository(api).sendRequest("bob_runs")

        assertTrue(result is ApiResult.Success)
        assertEquals("bob_runs", api.lastSentUsername)
    }

    @Test
    fun `accepting returns the new friend`() = runBlocking {
        val api = FakeFriendApi(
            acceptResponse = Response.success(FriendEnvelopeDto(userDto(id = "u2", username = "bob_runs"))),
        )

        val friend = (FriendRepository(api).acceptRequest("r1") as ApiResult.Success).data

        assertEquals("r1", api.lastAcceptedId)
        assertEquals("bob_runs", friend.username)
    }

    @Test
    fun `deleting a request targets the right request id`() = runBlocking {
        val api = FakeFriendApi(deleteResponse = Response.success(Unit))

        val result = FriendRepository(api).deleteRequest("r1")

        assertTrue(result is ApiResult.Success)
        assertEquals("r1", api.lastDeletedRequestId)
    }

    @Test
    fun `removing a friend targets the right user id`() = runBlocking {
        val api = FakeFriendApi(removeResponse = Response.success(Unit))

        val result = FriendRepository(api).removeFriend("u2")

        assertTrue(result is ApiResult.Success)
        assertEquals("u2", api.lastRemovedUserId)
    }

    @Test
    fun `a rejected request keeps the server message`() = runBlocking {
        val body = """{"error":{"message":"There is already a pending friend request between you."}}"""
        val api = FakeFriendApi(sendResponse = Response.error(409, body.toResponseBody(json)))

        val failure = FriendRepository(api).sendRequest("bob_runs") as ApiResult.Failure

        assertEquals("There is already a pending friend request between you.", failure.message)
        assertEquals(409, failure.statusCode)
    }

    @Test
    fun `a failed delete reports the server message`() = runBlocking {
        val body = """{"error":{"message":"Friend request not found."}}"""
        val api = FakeFriendApi(deleteResponse = Response.error(404, body.toResponseBody(json)))

        val failure = FriendRepository(api).deleteRequest("gone") as ApiResult.Failure

        assertEquals("Friend request not found.", failure.message)
        assertEquals(404, failure.statusCode)
    }

    @Test
    fun `an empty friend list is a success, not an error`() = runBlocking {
        val api = FakeFriendApi(friendsResponse = Response.success(FriendListDto()))

        val friends = (FriendRepository(api).friends() as ApiResult.Success).data

        assertTrue(friends.isEmpty())
        assertNull(api.lastRemovedUserId)
    }
}

/**
 * A [RepRiseApi] that returns canned friend responses and records what it was
 * asked. Named `FakeFriendApi` (not `FakeApi`) because a private class name must
 * be unique across the whole package.
 *
 * Every other call throws, so a future change that starts using one from this
 * repository fails loudly instead of silently returning an empty object.
 */
private class FakeFriendApi(
    private val searchResponse: Response<UserSearchResultsDto> = Response.success(UserSearchResultsDto()),
    private val requestsResponse: Response<FriendRequestsDto> = Response.success(FriendRequestsDto()),
    private val sendResponse: Response<FriendRequestEnvelopeDto> = Response.success(
        FriendRequestEnvelopeDto(
            FriendRequestDto(
                id = "r1",
                direction = "outgoing",
                user = UserDto(id = "u2", displayName = "Bob Carter", username = "bob_runs"),
            )
        )
    ),
    private val acceptResponse: Response<FriendEnvelopeDto> = Response.success(
        FriendEnvelopeDto(UserDto(id = "u2", displayName = "Bob Carter", username = "bob_runs"))
    ),
    private val deleteResponse: Response<Unit> = Response.success(Unit),
    private val friendsResponse: Response<FriendListDto> = Response.success(FriendListDto()),
    private val removeResponse: Response<Unit> = Response.success(Unit),
) : RepRiseApi {

    var lastSearchQuery: String? = null
        private set
    var lastSentUsername: String? = null
        private set
    var lastAcceptedId: String? = null
        private set
    var lastDeletedRequestId: String? = null
        private set
    var lastRemovedUserId: String? = null
        private set

    override suspend fun searchUsers(username: String): Response<UserSearchResultsDto> {
        lastSearchQuery = username
        return searchResponse
    }

    override suspend fun sendFriendRequest(
        body: SendFriendRequestRequest,
    ): Response<FriendRequestEnvelopeDto> {
        lastSentUsername = body.username
        return sendResponse
    }

    override suspend fun friendRequests(): Response<FriendRequestsDto> = requestsResponse

    override suspend fun acceptFriendRequest(id: String): Response<FriendEnvelopeDto> {
        lastAcceptedId = id
        return acceptResponse
    }

    override suspend fun deleteFriendRequest(id: String): Response<Unit> {
        lastDeletedRequestId = id
        return deleteResponse
    }

    override suspend fun friends(): Response<FriendListDto> = friendsResponse

    override suspend fun removeFriend(userId: String): Response<Unit> {
        lastRemovedUserId = userId
        return removeResponse
    }

    // --- Not used by FriendRepository ----------------------------------------

    override suspend fun register(body: RegisterRequest): Response<AuthResponseDto> =
        throw NotImplementedError("register is not used by FriendRepository")

    override suspend fun login(body: LoginRequest): Response<AuthResponseDto> =
        throw NotImplementedError("login is not used by FriendRepository")

    override suspend fun me(): Response<UserEnvelopeDto> =
        throw NotImplementedError("me is not used by FriendRepository")

    override suspend fun updateProfile(body: UpdateProfileRequest): Response<UserEnvelopeDto> =
        throw NotImplementedError("updateProfile is not used by FriendRepository")

    override suspend fun dashboard(): Response<DashboardDto> =
        throw NotImplementedError("dashboard is not used by FriendRepository")

    override suspend fun weightHistory(days: Int?, limit: Int?): Response<WeightHistoryDto> =
        throw NotImplementedError("weightHistory is not used by FriendRepository")

    override suspend fun latestWeight(): Response<LatestWeightEnvelopeDto> =
        throw NotImplementedError("latestWeight is not used by FriendRepository")

    override suspend fun createWeight(body: CreateWeightRequest): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("createWeight is not used by FriendRepository")

    override suspend fun updateWeight(
        id: String,
        body: UpdateWeightRequest,
    ): Response<WeightEntryEnvelopeDto> =
        throw NotImplementedError("updateWeight is not used by FriendRepository")

    override suspend fun deleteWeight(id: String): Response<Unit> =
        throw NotImplementedError("deleteWeight is not used by FriendRepository")

    override suspend fun workoutHistory(days: Int?, limit: Int?): Response<WorkoutListDto> =
        throw NotImplementedError("workoutHistory is not used by FriendRepository")

    override suspend fun exerciseCatalogue(): Response<ExerciseCatalogueDto> =
        throw NotImplementedError("exerciseCatalogue is not used by FriendRepository")

    override suspend fun getWorkout(id: String): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("getWorkout is not used by FriendRepository")

    override suspend fun createWorkout(body: CreateWorkoutRequest): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("createWorkout is not used by FriendRepository")

    override suspend fun updateWorkout(
        id: String,
        body: UpdateWorkoutRequest,
    ): Response<WorkoutEnvelopeDto> =
        throw NotImplementedError("updateWorkout is not used by FriendRepository")

    override suspend fun deleteWorkout(id: String): Response<Unit> =
        throw NotImplementedError("deleteWorkout is not used by FriendRepository")

    override suspend fun progression(exercise: String, limit: Int?): Response<ProgressionDto> =
        throw NotImplementedError("progression is not used by FriendRepository")

    override suspend fun trackedExercises(): Response<TrackedExerciseListDto> =
        throw NotImplementedError("trackedExercises is not used by FriendRepository")

    // --- Leaderboard (Phase 6b; handled by LeaderboardRepositoryTest) ---------

    override suspend fun weeklyLeaderboard(): Response<LeaderboardDto> =
        throw NotImplementedError("weeklyLeaderboard is not used by FriendRepository")
}
