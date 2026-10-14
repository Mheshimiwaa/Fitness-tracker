package com.reprise.app.data.remote

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
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.ProgressionDto
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.SendFriendRequestRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.TrackedExerciseListDto
import com.reprise.app.data.remote.dto.UpdateWorkoutRequest
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import com.reprise.app.data.remote.dto.UserSearchResultsDto
import com.reprise.app.data.remote.dto.WeightEntryEnvelopeDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
import com.reprise.app.data.remote.dto.WorkoutEnvelopeDto
import com.reprise.app.data.remote.dto.WorkoutListDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The RepRise REST API, described as a Kotlin interface.
 *
 * Retrofit generates the implementation: each function becomes an HTTP request
 * built from the annotations. Notice the paths do NOT start with "/" — the base
 * URL (`http://10.0.2.2:4000/`) already ends with one.
 *
 * Every call returns `Response<T>` so the shared [apiCall] helper can read the
 * server's error body on failure.
 */
interface RepRiseApi {

    // --- Authentication + profile (Phase 2) ---------------------------------

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponseDto>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponseDto>

    /** Validates a stored token and returns the current user. */
    @GET("api/auth/me")
    suspend fun me(): Response<UserEnvelopeDto>

    /** Partial profile update: only the non-null fields are sent. */
    @PATCH("api/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): Response<UserEnvelopeDto>

    // --- Weight tracking + dashboard (Phase 3) ------------------------------

    /** The Home tab's aggregated summary (latest weight, change, BMI). */
    @GET("api/dashboard")
    suspend fun dashboard(): Response<DashboardDto>

    /**
     * Weight history, newest first, already converted into the user's preferred
     * unit.
     *
     * @param days  only include readings from the last N days
     * @param limit maximum number of entries to return
     */
    @GET("api/weights")
    suspend fun weightHistory(
        @Query("days") days: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<WeightHistoryDto>

    /** The single most recent reading, or `entry: null` when there is none. */
    @GET("api/weights/latest")
    suspend fun latestWeight(): Response<LatestWeightEnvelopeDto>

    @POST("api/weights")
    suspend fun createWeight(@Body body: CreateWeightRequest): Response<WeightEntryEnvelopeDto>

    @PATCH("api/weights/{id}")
    suspend fun updateWeight(
        @Path("id") id: String,
        @Body body: UpdateWeightRequest,
    ): Response<WeightEntryEnvelopeDto>

    /** Deletes an entry. The server answers 204 No Content, hence `Response<Unit>`. */
    @DELETE("api/weights/{id}")
    suspend fun deleteWeight(@Path("id") id: String): Response<Unit>

    // --- Workout logging (Phase 4) ------------------------------------------

    /**
     * Workout history, newest first, already converted into the user's preferred
     * unit.
     *
     * @param days  only include sessions from the last N days
     * @param limit maximum number of workouts to return
     */
    @GET("api/workouts")
    suspend fun workoutHistory(
        @Query("days") days: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<WorkoutListDto>

    /**
     * The distinct exercise names this user has logged before, alphabetically.
     *
     * Declared before `:id` on the server, so "exercises" is never mistaken for
     * an id.
     */
    @GET("api/workouts/exercises")
    suspend fun exerciseCatalogue(): Response<ExerciseCatalogueDto>

    @GET("api/workouts/{id}")
    suspend fun getWorkout(@Path("id") id: String): Response<WorkoutEnvelopeDto>

    @POST("api/workouts")
    suspend fun createWorkout(@Body body: CreateWorkoutRequest): Response<WorkoutEnvelopeDto>

    /** Replaces the whole exercise list when one is supplied. */
    @PATCH("api/workouts/{id}")
    suspend fun updateWorkout(
        @Path("id") id: String,
        @Body body: UpdateWorkoutRequest,
    ): Response<WorkoutEnvelopeDto>

    /** Deletes a workout. The server answers 204 No Content. */
    @DELETE("api/workouts/{id}")
    suspend fun deleteWorkout(@Path("id") id: String): Response<Unit>

    // --- Progressive overload assistant (Phase 5) ---------------------------

    /**
     * The trend for one exercise: its recent sessions plus a rule-based verdict
     * comparing the latest session with the previous one.
     *
     * An exercise that has never been logged is still a 200 — just an empty
     * session list and an `insufficient_data` verdict.
     *
     * @param limit how many recent sessions to include (server default 10)
     */
    @GET("api/progression")
    suspend fun progression(
        @Query("exercise") exercise: String,
        @Query("limit") limit: Int? = null,
    ): Response<ProgressionDto>

    /** Every exercise the user has logged, with how often and when last. */
    @GET("api/progression/exercises")
    suspend fun trackedExercises(): Response<TrackedExerciseListDto>

    // --- Friends (Phase 6a) -------------------------------------------------

    /**
     * Find other users by username prefix.
     *
     * Declared on the users resource, but grouped here because the Friends
     * screen is its only caller.
     */
    @GET("api/users/search")
    suspend fun searchUsers(@Query("username") username: String): Response<UserSearchResultsDto>

    @POST("api/friends/requests")
    suspend fun sendFriendRequest(
        @Body body: SendFriendRequestRequest,
    ): Response<FriendRequestEnvelopeDto>

    /** Pending requests, split into incoming and outgoing. */
    @GET("api/friends/requests")
    suspend fun friendRequests(): Response<FriendRequestsDto>

    /** Accept a request I received; the response carries the new friend. */
    @POST("api/friends/requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") id: String): Response<FriendEnvelopeDto>

    /**
     * Delete a pending request: a decline if I was asked, a withdrawal if I did
     * the asking. The server answers 204 No Content.
     */
    @DELETE("api/friends/requests/{id}")
    suspend fun deleteFriendRequest(@Path("id") id: String): Response<Unit>

    /** My accepted friends, alphabetically by username. */
    @GET("api/friends")
    suspend fun friends(): Response<FriendListDto>

    /** Remove a friendship. The server answers 204 No Content. */
    @DELETE("api/friends/{userId}")
    suspend fun removeFriend(@Path("userId") userId: String): Response<Unit>
}
