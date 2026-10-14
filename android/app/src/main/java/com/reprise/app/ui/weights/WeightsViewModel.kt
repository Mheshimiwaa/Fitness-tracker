package com.reprise.app.ui.weights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.WeightRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.WeightChange
import com.reprise.app.domain.model.WeightEntry
import com.reprise.app.domain.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The add/edit form while it is open.
 *
 * `entryId == null` means "adding a new entry"; otherwise we are editing that
 * entry. Keeping the form's state here (rather than in `remember` inside the
 * screen) means a rotation does not lose what the user typed, and the save
 * logic can be unit-tested.
 */
data class WeightEditorState(
    val entryId: String? = null,
    val weightText: String = "",
    val noteText: String = "",
    val isSaving: Boolean = false,
    /** A form-level failure (network, or the server rejecting the request). */
    val errorMessage: String? = null,
    /** A problem with the weight box specifically, so it can be highlighted. */
    val weightError: WeightValidation? = null,
) {
    val isEditing: Boolean get() = entryId != null
}

/** Everything the Weight screen needs to draw itself. */
data class WeightsUiState(
    val isLoading: Boolean = true,
    /** Newest first, exactly as the API returns them. */
    val entries: List<WeightEntry> = emptyList(),
    val change: WeightChange? = null,
    /** The history window in days, or null for "all time". */
    val selectedDays: Int? = DEFAULT_PERIOD_DAYS,
    val preferredUnit: WeightUnit = WeightUnit.KG,
    val errorMessage: String? = null,
    /** Non-null while the add/edit dialog is showing. */
    val editor: WeightEditorState? = null,
) {
    /** Oldest first, which is the order the chart expects. */
    val chronologicalEntries: List<WeightEntry> get() = entries.asReversed()
}

/** The period choices offered by the screen. `null` means "all time". */
val PERIOD_OPTIONS: List<Int?> = listOf(7, 30, 90, null)

const val DEFAULT_PERIOD_DAYS: Int = 30

/**
 * Backs the Weight screen: loads the history, and adds, edits and deletes
 * entries through [WeightRepository].
 */
class WeightsViewModel(
    private val weightRepository: WeightRepository,
    defaultUnit: WeightUnit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeightsUiState(preferredUnit = defaultUnit))
    val uiState: StateFlow<WeightsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Reload the history for the currently selected period. */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val days = _uiState.value.selectedDays
            when (val result = weightRepository.history(days = days)) {
                is ApiResult.Success -> {
                    val entries = result.data.entries
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        entries = entries,
                        change = result.data.change,
                        // Trust the unit the server actually used; fall back to
                        // the current one when there is nothing to show yet.
                        preferredUnit = entries.firstOrNull()?.unit ?: _uiState.value.preferredUnit,
                        errorMessage = null,
                    )
                }

                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    /** Switch the history window (7 / 30 / 90 days, or all time). */
    fun selectDays(days: Int?) {
        if (days == _uiState.value.selectedDays) return
        _uiState.value = _uiState.value.copy(selectedDays = days)
        refresh()
    }

    // --- The add/edit dialog -------------------------------------------------

    fun startAdd() {
        _uiState.value = _uiState.value.copy(
            editor = WeightEditorState(
                // Prefill with the most recent reading — most people weigh in
                // near their last value, so this saves typing.
                weightText = _uiState.value.entries.firstOrNull()
                    ?.let { trimTrailingZero(it.weight) }
                    .orEmpty(),
            )
        )
    }

    fun startEdit(entry: WeightEntry) {
        _uiState.value = _uiState.value.copy(
            editor = WeightEditorState(
                entryId = entry.id,
                weightText = trimTrailingZero(entry.weight),
                noteText = entry.note.orEmpty(),
            )
        )
    }

    fun dismissEditor() {
        _uiState.value = _uiState.value.copy(editor = null)
    }

    fun onWeightChanged(text: String) {
        val editor = _uiState.value.editor ?: return
        // Clear a stale error as soon as the user starts fixing the field.
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(weightText = text, weightError = null, errorMessage = null)
        )
    }

    fun onNoteChanged(text: String) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(editor = editor.copy(noteText = text))
    }

    /**
     * Validate and submit the current form.
     *
     * Client-side checks give instant feedback for the obvious mistakes; the
     * server still performs the authoritative range check, and its per-field
     * message is folded back into [WeightEditorState.weightError].
     */
    fun save() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return

        val weight = editor.weightText.trim().replace(',', '.').toDoubleOrNull()
        val validationError = when {
            editor.weightText.isBlank() -> WeightValidation.REQUIRED
            weight == null -> WeightValidation.NOT_A_NUMBER
            weight <= 0.0 -> WeightValidation.NOT_POSITIVE
            else -> null
        }

        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                editor = editor.copy(weightError = validationError)
            )
            return
        }
        if (weight == null) return // unreachable; keeps the compiler happy.

        _uiState.value = _uiState.value.copy(
            editor = editor.copy(isSaving = true, errorMessage = null, weightError = null)
        )

        viewModelScope.launch {
            val note = editor.noteText.trim()
            val result = if (editor.entryId == null) {
                weightRepository.addWeight(
                    weight = weight,
                    unit = _uiState.value.preferredUnit,
                    note = note,
                )
            } else {
                weightRepository.updateWeight(
                    id = editor.entryId,
                    weight = weight,
                    note = note,
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    // Close the dialog and pull the fresh history (the change
                    // and the chart both depend on it).
                    _uiState.value = _uiState.value.copy(editor = null)
                    refresh()
                }

                is ApiResult.Failure -> {
                    val current = _uiState.value.editor ?: return@launch
                    // A problem with the "weight" field is shown ON that field;
                    // anything else becomes a banner above the form.
                    val isWeightFieldError = result.fieldErrors.containsKey("weight")
                    _uiState.value = _uiState.value.copy(
                        editor = current.copy(
                            isSaving = false,
                            weightError = if (isWeightFieldError) WeightValidation.SERVER else null,
                            errorMessage = result.message,
                        )
                    )
                }
            }
        }
    }

    /** Delete an entry, then reload so the summary stays accurate. */
    fun delete(entryId: String) {
        viewModelScope.launch {
            when (val result = weightRepository.deleteWeight(entryId)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _uiState.value =
                    _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    /** Dismiss the error banner (used by the banner's retry action). */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Seed the form's unit from the signed-in user's preference.
                val unit = AppModule.authRepository.currentUser?.weightUnit ?: WeightUnit.KG
                WeightsViewModel(AppModule.weightRepository, defaultUnit = unit)
            }
        }
    }
}

/** Why a weight value could not be accepted. Kept as codes so the UI can pick a string. */
enum class WeightValidation {
    REQUIRED,
    NOT_A_NUMBER,
    NOT_POSITIVE,
    SERVER,
}

/** "82.4" rather than "82.40000000000001" when prefilling the form. */
private fun trimTrailingZero(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
