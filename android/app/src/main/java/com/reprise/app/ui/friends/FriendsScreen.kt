package com.reprise.app.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.EmptyState
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.ScreenContainer
import com.reprise.app.components.UserAvatar
import com.reprise.app.domain.model.FriendRequest
import com.reprise.app.domain.model.FriendRequestDirection
import com.reprise.app.domain.model.FriendRequests
import com.reprise.app.domain.model.UserSummary
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * The Friends tab (Phase 6a), plus the entry point to the weekly leaderboard
 * (Phase 6b).
 *
 * Three things live on one screen, in the order a person actually needs them:
 *   1. a search box, to find someone by username and send a request,
 *   2. the requests waiting for an answer,
 *   3. the friend list itself (plus requests you sent, so they can be withdrawn).
 *
 * A card at the top opens the weekly leaderboard, which is a pushed screen of
 * its own (see [com.reprise.app.ui.leaderboard.LeaderboardScreen]).
 *
 * Every rule about who may befriend whom is enforced by the SERVER; this file
 * only draws the state and forwards taps, which is why it can stay thin.
 */
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    onOpenLeaderboard: () -> Unit = {},
    viewModel: FriendsViewModel = viewModel(factory = FriendsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Load on entry, so a request accepted elsewhere shows up when you come back.
    LaunchedEffect(Unit) { viewModel.refresh() }

    FriendsContent(
        state = state,
        actions = FriendsActions(
            onRetry = viewModel::refresh,
            onDismissError = viewModel::dismissActionError,
            onQueryChange = viewModel::onQueryChange,
            onClearSearch = viewModel::clearSearch,
            onAdd = viewModel::sendRequest,
            onAcceptRequest = viewModel::acceptRequest,
            onDeleteRequest = viewModel::deleteRequest,
            onRemoveFriend = viewModel::removeFriend,
        ),
        onOpenLeaderboard = onOpenLeaderboard,
        modifier = modifier,
    )
}

/**
 * Everything the stateless content needs to do something.
 *
 * Bundling the callbacks keeps [FriendsContent]'s signature to two parameters,
 * which is what lets it be previewed and reasoned about without a ViewModel.
 */
private data class FriendsActions(
    val onRetry: () -> Unit,
    val onDismissError: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onAdd: (UserSummary) -> Unit,
    val onAcceptRequest: (FriendRequest) -> Unit,
    val onDeleteRequest: (FriendRequest) -> Unit,
    val onRemoveFriend: (UserSummary) -> Unit,
)

@Composable
private fun FriendsContent(
    state: FriendsUiState,
    actions: FriendsActions,
    onOpenLeaderboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which friend the "Remove friend?" dialog is about, if any.
    var pendingRemoval by remember { mutableStateOf<UserSummary?>(null) }

    ScreenContainer(title = stringResource(R.string.title_friends), modifier = modifier) {
        state.loadError?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.action_retry),
                onAction = actions.onRetry,
            )
        }

        state.actionError?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.action_dismiss),
                onAction = actions.onDismissError,
            )
        }

        LeaderboardCard(onOpen = onOpenLeaderboard)

        SearchCard(state, actions)

        if (state.requests.incoming.isNotEmpty()) {
            Section(stringResource(R.string.friends_section_requests)) {
                RepRiseCard {
                    state.requests.incoming.forEachIndexed { index, request ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        RequestRow(
                            request = request,
                            isBusy = request.id in state.busyIds,
                            onAccept = { actions.onAcceptRequest(request) },
                            onDecline = { actions.onDeleteRequest(request) },
                        )
                    }
                }
            }
        }

        FriendsListSection(state, onRemoveClick = { pendingRemoval = it })

        if (state.requests.outgoing.isNotEmpty()) {
            Section(stringResource(R.string.friends_section_sent)) {
                RepRiseCard {
                    state.requests.outgoing.forEachIndexed { index, request ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        SentRequestRow(
                            request = request,
                            isBusy = request.id in state.busyIds,
                            onWithdraw = { actions.onDeleteRequest(request) },
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { friend ->
        RemoveFriendDialog(
            friend = friend,
            onConfirm = {
                actions.onRemoveFriend(friend)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Leaderboard entry point
// ---------------------------------------------------------------------------

/**
 * A tappable card that opens the weekly leaderboard.
 *
 * Shown above the search box because the week's standings are the thing a
 * returning user wants to see first; searching is a one-off task.
 */
@Composable
private fun ColumnScope.LeaderboardCard(onOpen: () -> Unit) {
    RepRiseCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_leaderboard),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.leaderboard_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.leaderboard_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.SearchCard(state: FriendsUiState, actions: FriendsActions) {
    val focusManager = LocalFocusManager.current
    // Held in a local so the `when` below can read it without `!!`.
    val searchError = state.searchError

    RepRiseCard {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.friends_search_label)) },
            placeholder = { Text(stringResource(R.string.friends_search_hint)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null, // decorative: the label says it all
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = actions.onClearSearch) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.friends_search_stop),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )

        when {
            state.isSearching -> {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BusySpinner()
                    Text(
                        text = stringResource(R.string.friends_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.searchError != null -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = searchError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.results.isNotEmpty() -> {
                state.results.forEach { result ->
                    Spacer(Modifier.height(4.dp))
                    SearchResultRow(
                        user = result,
                        relation = state.relationTo(result.id),
                        isBusy = state.isBusy(result),
                        onAdd = { actions.onAdd(result) },
                        onAccept = { state.incomingRequestFor(result.id)?.let(actions.onAcceptRequest) },
                    )
                }
            }

            // Only say "no matches" when a search actually ran, so the box does
            // not accuse the user of anything while they are still typing.
            state.hasSearched -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.friends_search_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    user: UserSummary,
    relation: FriendRelation,
    isBusy: Boolean,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
) {
    UserRow(
        user = user,
        subtitle = stringResource(R.string.friends_username_at, user.username),
    ) {
        when {
            isBusy -> BusySpinner()

            relation == FriendRelation.FRIEND ->
                QuietLabel(stringResource(R.string.friends_already_friends))

            relation == FriendRelation.REQUESTED ->
                QuietLabel(stringResource(R.string.friends_requested))

            relation == FriendRelation.RESPONDS ->
                TextButton(onClick = onAccept) {
                    Text(stringResource(R.string.friends_accept))
                }

            else -> TextButton(onClick = onAdd) {
                Text(stringResource(R.string.friends_add))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------

@Composable
private fun RequestRow(
    request: FriendRequest,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    UserRow(
        user = request.user,
        subtitle = stringResource(R.string.friends_wants_to_add_you_named, request.user.displayName),
    ) {
        if (isBusy) {
            BusySpinner()
        } else {
            TextButton(onClick = onDecline) {
                Text(
                    text = stringResource(R.string.friends_decline),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.friends_accept))
            }
        }
    }
}

@Composable
private fun SentRequestRow(
    request: FriendRequest,
    isBusy: Boolean,
    onWithdraw: () -> Unit,
) {
    UserRow(
        user = request.user,
        subtitle = stringResource(R.string.friends_you_requested_named, request.user.displayName),
    ) {
        if (isBusy) {
            BusySpinner()
        } else {
            TextButton(onClick = onWithdraw) {
                Text(stringResource(R.string.friends_withdraw))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Friends
// ---------------------------------------------------------------------------

@Composable
private fun FriendsListSection(
    state: FriendsUiState,
    onRemoveClick: (UserSummary) -> Unit,
) {
    if (state.isLoading && state.friends.isEmpty()) {
        CenteredLoader()
        return
    }

    if (state.friends.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.empty_friends_title),
            message = stringResource(R.string.empty_friends_message),
        )
        return
    }

    Section(pluralStringResource(R.plurals.friends_count, state.friends.size, state.friends.size)) {
        RepRiseCard {
            state.friends.forEachIndexed { index, friend ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                UserRow(
                    user = friend,
                    subtitle = stringResource(R.string.friends_username_at, friend.username),
                ) {
                    if (friend.id in state.busyIds) {
                        BusySpinner()
                    } else {
                        TextButton(onClick = { onRemoveClick(friend) }) {
                            Text(
                                text = stringResource(R.string.friends_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveFriendDialog(
    friend: UserSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.friends_remove_title)) },
        text = { Text(stringResource(R.string.friends_remove_message, friend.displayName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.friends_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------

/** A titled group (a section label with its card below, 8dp apart). */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** One person: an avatar, their name, a subtitle, and trailing actions. */
@Composable
private fun UserRow(
    user: UserSummary,
    subtitle: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(displayName = user.displayName, username = user.username)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        trailing()
    }
}

/** A muted, non-interactive label such as "Requested" or "Already friends". */
@Composable
private fun QuietLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small spinner shown in place of a row's buttons while an action runs. */
@Composable
private fun BusySpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp,
    )
}

@Composable
private fun CenteredLoader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun FriendsContentPreview() {
    val alice = UserSummary(id = "1", username = "alice_lifts", displayName = "Alice Nguyen", profileImageUrl = null)
    val bob = UserSummary(id = "2", username = "bob_runs", displayName = "Bob Carter", profileImageUrl = null)
    val carol = UserSummary(id = "3", username = "carol_k", displayName = "Carol Diaz", profileImageUrl = null)
    val dave = UserSummary(id = "4", username = "dave_d", displayName = "Dave Patel", profileImageUrl = null)

    RepRiseTheme {
        FriendsContent(
            state = FriendsUiState(
                isLoading = false,
                friends = listOf(alice),
                requests = FriendRequests(
                    incoming = listOf(
                        FriendRequest("r1", FriendRequestDirection.INCOMING, bob, null),
                    ),
                    outgoing = listOf(
                        FriendRequest("r2", FriendRequestDirection.OUTGOING, carol, null),
                    ),
                ),
                query = "dave",
                results = listOf(dave),
                hasSearched = true,
            ),
            actions = FriendsActions(
                onRetry = {},
                onDismissError = {},
                onQueryChange = {},
                onClearSearch = {},
                onAdd = {},
                onAcceptRequest = {},
                onDeleteRequest = {},
                onRemoveFriend = {},
            ),
            onOpenLeaderboard = {},
        )
    }
}
