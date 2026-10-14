package com.reprise.app.ui.friends

import com.reprise.app.domain.model.FriendRequest
import com.reprise.app.domain.model.FriendRequestDirection
import com.reprise.app.domain.model.FriendRequests
import com.reprise.app.domain.model.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure helpers on [FriendsUiState].
 *
 * These decide what the search box shows next to a result — "Add", "Requested"
 * or "Already friends" — and whether a row is mid-action. They are plain
 * functions over the state, so they can be checked without a ViewModel, a
 * coroutine or a server.
 */
class FriendsUiStateTest {

    private fun user(id: String, username: String = "user_$id") =
        UserSummary(id = id, username = username, displayName = "User $id", profileImageUrl = null)

    private fun request(id: String, direction: FriendRequestDirection, user: UserSummary) =
        FriendRequest(id = id, direction = direction, user = user, createdAt = null)

    private fun state(
        friends: List<UserSummary> = emptyList(),
        incoming: List<FriendRequest> = emptyList(),
        outgoing: List<FriendRequest> = emptyList(),
        busyIds: Set<String> = emptySet(),
    ) = FriendsUiState(
        isLoading = false,
        friends = friends,
        requests = FriendRequests(incoming = incoming, outgoing = outgoing),
        busyIds = busyIds,
    )

    @Test
    fun `a stranger has no relationship yet`() {
        val state = state()

        assertEquals(FriendRelation.NONE, state.relationTo("u1"))
    }

    @Test
    fun `an accepted friend is recognised`() {
        val state = state(friends = listOf(user("u1")))

        assertEquals(FriendRelation.FRIEND, state.relationTo("u1"))
    }

    @Test
    fun `someone I have asked shows as requested`() {
        val state = state(
            outgoing = listOf(request("r1", FriendRequestDirection.OUTGOING, user("u2"))),
        )

        assertEquals(FriendRelation.REQUESTED, state.relationTo("u2"))
    }

    @Test
    fun `someone who asked me shows as awaiting my reply`() {
        val state = state(
            incoming = listOf(request("r1", FriendRequestDirection.INCOMING, user("u3"))),
        )

        assertEquals(FriendRelation.RESPONDS, state.relationTo("u3"))
    }

    @Test
    fun `a person with no action in flight is not busy`() {
        val state = state(friends = listOf(user("u1")))

        assertFalse(state.isBusy(user("u1")))
    }

    @Test
    fun `adding a stranger keys the spinner on the user id`() {
        val state = state(busyIds = setOf("u1"))

        assertTrue(state.isBusy(user("u1")))
    }

    @Test
    fun `accepting keys the spinner on the request id`() {
        val sender = user("u3")
        val incoming = request("r1", FriendRequestDirection.INCOMING, sender)

        // Busy on the request id → the row shows a spinner.
        assertTrue(state(incoming = listOf(incoming), busyIds = setOf("r1")).isBusy(sender))
        // Busy on an unrelated id → the row is idle.
        assertFalse(state(incoming = listOf(incoming), busyIds = setOf("other")).isBusy(sender))
    }

    @Test
    fun `the incoming request for a user is found by their id`() {
        val sender = user("u3")
        val state = state(incoming = listOf(request("r1", FriendRequestDirection.INCOMING, sender)))

        assertEquals("r1", state.incomingRequestFor("u3")?.id)
        assertNull(state.incomingRequestFor("nobody"))
    }
}
