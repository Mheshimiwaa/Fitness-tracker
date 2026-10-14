package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the friends endpoints (Phase 6a).
 *
 * Reuses [UserDto] for the user objects the server sends: search results,
 * request senders and friends are all "a smaller view of a user", and `UserDto`
 * already marks email/goal/height as optional, so one class covers both the full
 * profile and the summary.
 */

/** Response of `GET /api/users/search?username=`. */
@Serializable
data class UserSearchResultsDto(
    val users: List<UserDto> = emptyList(),
)

/** Body for `POST /api/friends/requests`. */
@Serializable
data class SendFriendRequestRequest(
    val username: String,
)

/**
 * One pending friend request.
 *
 * `direction` is `"incoming"` or `"outgoing"` — a string on the wire, mapped to
 * an enum by the repository.
 */
@Serializable
data class FriendRequestDto(
    val id: String,
    val direction: String,
    val user: UserDto,
    val createdAt: String? = null,
)

/** Response of `POST /api/friends/requests` — `{"request": {...}}`. */
@Serializable
data class FriendRequestEnvelopeDto(
    val request: FriendRequestDto,
)

/** Response of `GET /api/friends/requests` — the two directions. */
@Serializable
data class FriendRequestsDto(
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
)

/** Response of `GET /api/friends`. */
@Serializable
data class FriendListDto(
    val friends: List<UserDto> = emptyList(),
)

/** Response of `POST /api/friends/requests/:id/accept` — `{"friend": {...}}`. */
@Serializable
data class FriendEnvelopeDto(
    val friend: UserDto,
)
