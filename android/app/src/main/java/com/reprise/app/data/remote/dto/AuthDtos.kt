package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects (DTOs) for the auth + profile endpoints.
 *
 * Why separate DTOs instead of using the domain models directly?
 *   - The wire format is owned by the SERVER and can change independently of our
 *     UI models. Keeping the two apart means a server change touches one mapping
 *     function, not every screen.
 *   - They are plain `@Serializable` classes, which is exactly what
 *     kotlinx.serialization needs to turn JSON into Kotlin objects.
 *
 * The API is configured with `ignoreUnknownKeys = true`, so we only declare the
 * fields we actually use (`createdAt`, `updatedAt`, ... are safely ignored).
 */

@Serializable
data class RegisterRequest(
    val displayName: String,
    val username: String,
    val email: String,
    val password: String,
)

/** The server accepts either a username or an email in this field. */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/**
 * A user as returned by the API.
 *
 * `email`, `fitnessGoal` and the rest are nullable because search results return
 * a *smaller* view of a user (no email), while `/api/users/me` returns the full
 * profile.
 */
@Serializable
data class UserDto(
    val id: String,
    val displayName: String,
    val username: String,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val fitnessGoal: String? = null,
    val weightUnit: String? = null,
    val heightCm: Int? = null,
)

/** Response of register/login: the user plus a signed JWT. */
@Serializable
data class AuthResponseDto(
    val user: UserDto,
    val token: String,
)

/** Response of GET /api/auth/me and GET/PATCH /api/users/me. */
@Serializable
data class UserEnvelopeDto(
    val user: UserDto,
)

/**
 * Body for PATCH /api/users/me.
 *
 * Only the fields the caller actually wants to change are sent: every property
 * has a default, and kotlinx.serialization does NOT encode properties that still
 * hold their default value. Passing `null` (or omitting a field) therefore means
 * "leave it alone".
 */
@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val username: String? = null,
    val fitnessGoal: String? = null,
    val weightUnit: String? = null,
    val heightCm: Int? = null,
)
