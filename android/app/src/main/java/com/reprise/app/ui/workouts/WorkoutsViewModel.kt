package com.reprise.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.WorkoutRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Workouts tab needs to draw itself: the logged sessions, or the reason
 * they could not be loaded.
 */
data class WorkoutsUiState(
    val isLoading: Boolean = true,
    /** Newest first, exactly as the API returns them. */
    val workouts: List<Workout> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Backs the Workouts tab: loads the history and deletes sessions.
 *
 * Like Home, it does NOT load in `init` — the screen calls [refresh] when it
 * enters composition, which happens again every time the user returns from the
 * editor, so a freshly logged workout appears without a manual pull.
 */
class WorkoutsViewModel(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutsUiState())
    val uiState: StateFlow<WorkoutsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = workoutRepository.history()) {
                is ApiResult.Success -> _uiState.value = WorkoutsUiState(
                    isLoading = false,
                    workouts = result.data,
                )

                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    /** Delete a session, then reload so the list and counts stay accurate. */
    fun delete(workoutId: String) {
        viewModelScope.launch {
            when (val result = workoutRepository.deleteWorkout(workoutId)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _uiState.value =
                    _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkoutsViewModel(AppModule.workoutRepository) }
        }
    }
}
