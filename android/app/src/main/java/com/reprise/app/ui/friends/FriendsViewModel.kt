package com.reprise.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.FriendRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.FriendRequest
import com.reprise.app.domain.model.FriendRequests
import com.reprise.app.domain.model.UserSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * How long the search box waits after the last keystroke before calling the API.
 * Long enough that typing a whole username is one request, short enough that a
 * paused typist still sees results promptly.
 */
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * How the signed-in user currently relates to another user.
 *
 * Derived entirely from the lists we already hold, so the search box can show
 * "Add", "Requested" or "Already friends" without the server having to tell us
 * the relationship with every search result.
 */
enum class FriendRelation {
    /** Already in my friend list. */
    FRIEND,

    /** I have asked them and they have not answered yet. */
    REQUESTED,

    /** They have asked me — the request is in my Requests section. */
    RESPONDS,

    /** No relationship yet; I can send a request. */
    NONE,
}

/**
 * What the Friends screen needs to draw itself.
 *
 * One screen, one state object: the friend list, both directions of pending
 * requests, and the search box all change together (accepting a request moves a
 * person out of "requests" and into "friends"), so keeping them in separate
 * flows would only create ways for them to disagree.
 */
data class FriendsUiState(
    // --- the lists ---------------------------------------------------------
    val isLoading: Boolean = true,
    val friends: List<UserSummary> = emptyList(),
    val requests: FriendRequests = FriendRequests.EMPTY,
    /** A problem loading the lists, shown with a "Try again" action. */
    val loadError: String? = null,

    // --- the search box ----------------------------------------------------
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<UserSummary> = emptyList(),
    /** A problem running the search, shown under the box. */
    val searchError: String? = null,
    /** True once a search has actually run, so we can say "no matches". */
    val hasSearched: Boolean = false,

    // --- in-flight actions -------------------------------------------------
    /** Request ids / user ids with an action in flight (their button spins). */
    val busyIds: Set<String> = emptySet(),
    /** A failed add/accept/decline/remove, shown as a banner and then dismissed. */
    val actionError: String? = null,
) {
    /** How I currently relate to the user with this id. */
    fun relationTo(userId: String): FriendRelation = when {
        friends.any { it.id == userId } -> FriendRelation.FRIEND
        requests.outgoing.any { it.user.id == userId } -> FriendRelation.REQUESTED
        requests.incoming.any { it.user.id == userId } -> FriendRelation.RESPONDS
        else -> FriendRelation.NONE
    }

    /** The request from this user, if they have asked to add me. */
    fun incomingRequestFor(userId: String): FriendRequest? =
        requests.incoming.firstOrNull { it.user.id == userId }

    /**
     * Whether a change to [user] is in flight.
     *
     * Accepting is keyed by the REQUEST id (that is what the action acts on),
     * while adding or removing is keyed by the user id — so both are checked.
     */
    fun isBusy(user: UserSummary): Boolean {
        val request = incomingRequestFor(user.id)
        return (request != null && request.id in busyIds) || user.id in busyIds
    }
}

/**
 * Backs the Friends tab (Phase 6a).
 *
 * Loads on entry and after every successful change, so the lists always reflect
 * the server. It never decides whether a friendship is allowed — the server owns
 * those rules and returns a message we simply show.
 */
class FriendsViewModel(
    private val friendRepository: FriendRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    /** The in-flight debounced search, cancelled whenever the query changes. */
    private var searchJob: Job? = null

    /** Load the friend list and both directions of pending requests. */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)

        viewModelScope.launch {
            val friendsResult = friendRepository.friends()
            val requestsResult = friendRepository.requests()

            // Report the first problem, but still show whichever list DID load.
            val error = (friendsResult as? ApiResult.Failure)?.message
                ?: (requestsResult as? ApiResult.Failure)?.message

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                friends = (friendsResult as? ApiResult.Success)?.data ?: _uiState.value.friends,
                requests = (requestsResult as? ApiResult.Success)?.data ?: _uiState.value.requests,
                loadError = error,
            )
        }
    }

    // --- search -------------------------------------------------------------

    /**
     * The user typed. Restart the debounce, and clear results when the box is
     * emptied so the screen does not keep showing a stale search.
     */
    fun onQueryChange(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = query)

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                results = emptyList(),
                searchError = null,
                hasSearched = false,
            )
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(trimmed)
        }
    }

    /** Empty the search box (the trailing clear button). */
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            isSearching = false,
            results = emptyList(),
            searchError = null,
            hasSearched = false,
        )
    }

    private suspend fun runSearch(query: String) {
        when (val result = friendRepository.searchUsers(query)) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                isSearching = false,
                results = result.data,
                hasSearched = true,
                searchError = null,
            )

            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                isSearching = false,
                results = emptyList(),
                hasSearched = true,
                searchError = result.message,
            )
        }
    }

    // --- actions ------------------------------------------------------------

    /** Ask [user] to be friends. */
    fun sendRequest(user: UserSummary) = act(user.id) {
        friendRepository.sendRequest(user.username)
    }

    /** Accept the request [request]. */
    fun acceptRequest(request: FriendRequest) = act(request.id) {
        friendRepository.acceptRequest(request.id)
    }

    /** Decline a request I received, or withdraw one I sent. */
    fun deleteRequest(request: FriendRequest) = act(request.id) {
        friendRepository.deleteRequest(request.id)
    }

    /** Remove an accepted friendship. */
    fun removeFriend(user: UserSummary) = act(user.id) {
        friendRepository.removeFriend(user.id)
    }

    /** Dismiss the action-error banner. */
    fun dismissActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }

    /**
     * Run one change against the server, marking [id] busy while it is in flight.
     *
     * On success the lists are reloaded (a request becoming a friendship moves a
     * person between sections); on failure the server's own message is shown,
     * because it explains the rule that was broken better than we could.
     */
    private fun act(id: String, block: suspend () -> ApiResult<*>): Unit {
        if (id in _uiState.value.busyIds) return
        _uiState.value = _uiState.value.copy(
            busyIds = _uiState.value.busyIds + id,
            actionError = null,
        )

        viewModelScope.launch {
            val result = block()
            _uiState.value = _uiState.value.copy(
                busyIds = _uiState.value.busyIds - id,
                actionError = (result as? ApiResult.Failure)?.message,
            )
            if (result is ApiResult.Success) refresh()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { FriendsViewModel(AppModule.friendRepository) }
        }
    }
}
