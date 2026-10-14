package com.reprise.app.ui.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.ProgressionRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.ExerciseProgression
import com.reprise.app.domain.model.TrackedExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Progress screen needs to draw itself.
 *
 * The screen has TWO levels — a list of exercises, and one exercise's trend —
 * and both live in this single state object rather than on the nav back stack.
 * That is deliberate: an exercise name is free text with spaces in it, so
 * passing it through a route argument would mean a lot of encode/decode
 * ceremony for no benefit. Selecting is just a state change, and the back arrow
 * clears the selection first.
 */
data class ProgressionUiState(
    val isLoadingExercises: Boolean = true,
    /** Alphabetically, as the server returns them. */
    val exercises: List<TrackedExercise> = emptyList(),
    val listError: String? = null,
    /** null = showing the list; non-null = showing this exercise's trend. */
    val selectedExercise: String? = null,
    val isLoadingDetail: Boolean = false,
    val progression: ExerciseProgression? = null,
    val detailError: String? = null,
)

/**
 * Backs the Progressive Overload screen.
 *
 * Like the other list screens it loads on entry rather than in `init`, so
 * returning from logging a workout shows the updated trend. It never computes a
 * verdict itself — it renders what the server decided.
 */
class ProgressionViewModel(
    private val progressionRepository: ProgressionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressionUiState())
    val uiState: StateFlow<ProgressionUiState> = _uiState.asStateFlow()

    /** Load the list of exercises the user has logged. */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoadingExercises = true, listError = null)

        viewModelScope.launch {
            when (val result = progressionRepository.trackedExercises()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingExercises = false,
                    exercises = result.data,
                )

                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoadingExercises = false,
                    listError = result.message,
                )
            }
        }
    }

    /** Open one exercise's trend. */
    fun select(exercise: String) {
        if (_uiState.value.selectedExercise == exercise) return
        _uiState.value = _uiState.value.copy(
            selectedExercise = exercise,
            isLoadingDetail = true,
            progression = null,
            detailError = null,
        )
        loadDetail(exercise)
    }

    /** Go back to the list. */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedExercise = null,
            isLoadingDetail = false,
            progression = null,
            detailError = null,
        )
    }

    /** Retry the detail load after a failure (and after, say, logging a session). */
    fun retryDetail() {
        val exercise = _uiState.value.selectedExercise ?: return
        _uiState.value = _uiState.value.copy(isLoadingDetail = true, detailError = null)
        loadDetail(exercise)
    }

    private fun loadDetail(exercise: String) {
        viewModelScope.launch {
            when (val result = progressionRepository.progression(exercise)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingDetail = false,
                    progression = result.data,
                )

                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoadingDetail = false,
                    detailError = result.message,
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProgressionViewModel(AppModule.progressionRepository) }
        }
    }
}
