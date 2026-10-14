package com.reprise.app.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.LeaderboardRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.Leaderboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the leaderboard screen needs to draw itself.
 *
 * A single read-only payload, so the state is correspondingly small: loading,
 * the board itself, and a problem if the read failed.
 */
data class LeaderboardUiState(
    val isLoading: Boolean = true,
    /** Null until the first successful load. */
    val leaderboard: Leaderboard? = null,
    /** A problem loading the board, shown with a "Try again" action. */
    val error: String? = null,
)

/**
 * Backs the weekly leaderboard screen (Phase 6b).
 *
 * Read-only: the board is computed from workouts the user has already logged, so
 * there is nothing here to add, edit or delete. All of the scoring happens on
 * the server; this ViewModel only fetches and exposes the result.
 */
class LeaderboardViewModel(
    private val leaderboardRepository: LeaderboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    /** Fetch this week's board. */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            when (val result = leaderboardRepository.weekly()) {
                is ApiResult.Success -> _uiState.value = LeaderboardUiState(
                    isLoading = false,
                    leaderboard = result.data,
                    error = null,
                )

                // Keep whatever board we already had; the banner explains the
                // refresh failed rather than blanking the screen.
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.message,
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LeaderboardViewModel(AppModule.leaderboardRepository) }
        }
    }
}
