package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.apiCall
import com.reprise.app.data.remote.apiCallUnit
import com.reprise.app.data.remote.dto.FriendRequestDto
import com.reprise.app.data.remote.dto.FriendRequestsDto
import com.reprise.app.data.remote.dto.SendFriendRequestRequest
import com.reprise.app.data.remote.dto.UserDto
import com.reprise.app.domain.model.FriendRequest
import com.reprise.app.domain.model.FriendRequestDirection
import com.reprise.app.domain.model.FriendRequests
import com.reprise.app.domain.model.UserSummary

/**
 * Everything the app needs to read and change friendships.
 *
 * Like the other repositories, this is the only place that knows about the wire
 * DTOs: callers get [UserSummary] / [FriendRequest] and never see JSON.
 *
 * Mutual exclusivity (you cannot befriend yourself, you cannot request someone
 * you are already friends with) is enforced by the SERVER. This repository only
 * maps and forwards — it deliberately does not re-implement those rules, so a
 * rule change never needs an app release.
 */
class FriendRepository(private val api: RepRiseApi) {

    /** Find other users whose username starts with [query]. */
    suspend fun searchUsers(query: String): ApiResult<List<UserSummary>> =
        apiCall { api.searchUsers(query) }
            .map { it.users.map(UserDto::toSummary) }

    /** Pending requests, split into incoming and outgoing. */
    suspend fun requests(): ApiResult<FriendRequests> =
        apiCall { api.friendRequests() }
            .map(FriendRequestsDto::toDomain)

    /** Ask [username] to be friends. */
    suspend fun sendRequest(username: String): ApiResult<Unit> =
        apiCall { api.sendFriendRequest(SendFriendRequestRequest(username)) }
            .map { }

    /** Accept a request I received, returning the new friend. */
    suspend fun acceptRequest(id: String): ApiResult<UserSummary> =
        apiCall { api.acceptFriendRequest(id) }
            .map { it.friend.toSummary() }

    /** Decline (recipient) or withdraw (sender) a pending request. */
    suspend fun deleteRequest(id: String): ApiResult<Unit> =
        apiCallUnit { api.deleteFriendRequest(id) }

    /** My friends, alphabetically by username. */
    suspend fun friends(): ApiResult<List<UserSummary>> =
        apiCall { api.friends() }
            .map { it.friends.map(UserDto::toSummary) }

    /** Remove an accepted friendship. */
    suspend fun removeFriend(userId: String): ApiResult<Unit> =
        apiCallUnit { api.removeFriend(userId) }

    /** Apply [transform] to a successful result, leaving failures untouched. */
    private inline fun <T : Any, R : Any> ApiResult<T>.map(
        transform: (T) -> R,
    ): ApiResult<R> = when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
    }
}

// ---------------------------------------------------------------------------
// DTO -> domain mappings
// ---------------------------------------------------------------------------

/**
 * Map a wire user onto the small public view.
 *
 * Works for both a full profile (`UserDto`) and a search result, because every
 * field beyond these four is optional on the DTO.
 */
private fun UserDto.toSummary(): UserSummary = UserSummary(
    id = id,
    username = username,
    displayName = displayName,
    profileImageUrl = profileImageUrl,
)

/**
 * Map one request, or null when the server sent a direction we do not know.
 *
 * Returning null (rather than throwing) means a newer server can add a third
 * direction without crashing this app; the request is simply skipped.
 */
private fun FriendRequestDto.toDomainOrNull(): FriendRequest? {
    val direction = FriendRequestDirection.fromApi(direction) ?: return null
    return FriendRequest(
        id = id,
        direction = direction,
        user = user.toSummary(),
        createdAt = createdAt,
    )
}

private fun FriendRequestsDto.toDomain(): FriendRequests = FriendRequests(
    incoming = incoming.mapNotNull(FriendRequestDto::toDomainOrNull),
    outgoing = outgoing.mapNotNull(FriendRequestDto::toDomainOrNull),
)
