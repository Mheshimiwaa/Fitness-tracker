package com.reprise.app.domain.model

/**
 * Domain models for friends and friend requests (Phase 6a).
 *
 * Pure Kotlin — no JSON, no HTTP. `FriendRepository` maps the DTOs onto these,
 * so the UI never depends on the wire shape.
 */

/**
 * A deliberately small view of ANOTHER user.
 *
 * This is not [User]: your own profile carries your email, goal and height, but
 * none of that is any of a stranger's business. Search results and friend lists
 * carry only what is needed to recognise someone.
 */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
)

/**
 * Which way a pending friend request points.
 *
 * `apiValue` must match the server exactly (see
 * `backend/src/services/friendship.service.js`).
 */
enum class FriendRequestDirection(val apiValue: String) {
    /** Somebody asked to be my friend; I can accept or decline. */
    INCOMING("incoming"),

    /** I asked somebody else; I can still withdraw it. */
    OUTGOING("outgoing");

    companion object {
        /** Map a server value, or null when it is not recognised. */
        fun fromApi(value: String?): FriendRequestDirection? =
            entries.firstOrNull { it.apiValue == value }
    }
}

/** One pending friend request, from the viewing user's point of view. */
data class FriendRequest(
    /** The request id — needed to accept, decline or withdraw it. */
    val id: String,
    val direction: FriendRequestDirection,
    val user: UserSummary,
    val createdAt: String?,
)

/** Both directions at once, exactly as `GET /api/friends/requests` returns them. */
data class FriendRequests(
    val incoming: List<FriendRequest>,
    val outgoing: List<FriendRequest>,
) {
    companion object {
        /** Used before the first load, and for a user with no requests. */
        val EMPTY = FriendRequests(incoming = emptyList(), outgoing = emptyList())
    }
}
