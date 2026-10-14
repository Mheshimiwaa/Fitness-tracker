package com.reprise.app.data.remote

import com.reprise.app.data.remote.dto.AuthResponseDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

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
}
