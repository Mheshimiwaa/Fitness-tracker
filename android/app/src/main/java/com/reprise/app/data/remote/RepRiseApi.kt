package com.reprise.app.data.remote

import com.reprise.app.data.remote.dto.AuthResponseDto
import com.reprise.app.data.remote.dto.CreateWeightRequest
import com.reprise.app.data.remote.dto.DashboardDto
import com.reprise.app.data.remote.dto.LatestWeightEnvelopeDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import com.reprise.app.data.remote.dto.WeightEntryEnvelopeDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
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
}
