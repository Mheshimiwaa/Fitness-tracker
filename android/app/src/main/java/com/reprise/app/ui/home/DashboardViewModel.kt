package com.reprise.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.WeightRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.Dashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Home tab needs to draw itself: the loaded summary, or the reason it
 * could not be loaded.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboard: Dashboard? = null,
    val errorMessage: String? = null,
)

/**
 * Loads GET /api/dashboard for the Home tab.
 *
 * The screen re-enters composition whenever the user comes back from another
 * screen, so [refresh] is called again at that point — which is how a weight
 * logged on the Weight screen shows up here immediately.
 */
class DashboardViewModel(
    private val weightRepository: WeightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun refresh() {
        // Keep showing the current numbers while reloading, so the screen does
        // not flash back to a spinner on every return.
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            _uiState.value = when (val result = weightRepository.dashboard()) {
                is ApiResult.Success -> DashboardUiState(isLoading = false, dashboard = result.data)
                is ApiResult.Failure -> DashboardUiState(
                    isLoading = false,
                    dashboard = _uiState.value.dashboard,
                    errorMessage = result.message,
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(AppModule.weightRepository) }
        }
    }
}
