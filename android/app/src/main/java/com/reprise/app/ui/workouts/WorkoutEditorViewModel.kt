package com.reprise.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.dto.WorkoutExerciseRequest
import com.reprise.app.data.remote.dto.WorkoutSetRequest
import com.reprise.app.data.repository.WorkoutRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.util.formatEditableNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One set row in the editor.
 *
 * [id] is a stable key: the list is rendered with `key = { it.id }`, so removing
 * the middle row does not make Compose reuse the wrong TextField and scramble
 * what the user typed.
 */
data class SetDraft(
    val id: Long,
    val repsText: String = "",
    val weightText: String = "",
)

/** One exercise card in the editor, with its set rows. */
data class ExerciseDraft(
    val id: Long,
    val name: String = "",
    val sets: List<SetDraft> = emptyList(),
)

/**
 * The whole create/edit form.
 *
 * Keeping the draft here (rather than in `remember` inside the screen) means a
 * rotation does not lose what the user typed, and the save/validation logic is
 * unit-testable without a device.
 */
data class WorkoutEditorUiState(
    /** null = creating a new session. */
    val workoutId: String? = null,
    /** True while an existing session is being fetched for editing. */
    val isLoading: Boolean = false,
    val name: String = "",
    val durationText: String = "",
    val notes: String = "",
    val exercises: List<ExerciseDraft> = emptyList(),
    /** The unit the set weights are entered in (the user's preference). */
    val unit: WeightUnit = WeightUnit.KG,
    val isSaving: Boolean = false,
    /** Set once the server has accepted the save, so the screen can go back. */
    val isSaved: Boolean = false,
    /** A form-level failure (network, or the server rejecting the request). */
    val errorMessage: String? = null,
    val nameError: Boolean = false,
    val durationError: Boolean = false,
    /** The form has no exercises at all (only reachable by edge cases). */
    val noExercises: Boolean = false,
    /** Exercise rows whose name is blank. */
    val exerciseNameErrors: Set<Long> = emptySet(),
    /** Set rows whose reps are missing or not a positive whole number. */
    val setRepsErrors: Set<Long> = emptySet(),
    /** Set rows whose weight is missing or not a valid, non-negative number. */
    val setWeightErrors: Set<Long> = emptySet(),
    /** Previously logged exercise names, offered as tappable suggestions. */
    val suggestedExercises: List<String> = emptyList(),
) {
    val isEditing: Boolean get() = workoutId != null

    /** The last exercise cannot be removed — a workout needs at least one. */
    val canRemoveExercise: Boolean get() = exercises.size > 1
}

/**
 * Backs the workout editor: builds the draft, validates it, and saves through
 * [WorkoutRepository].
 *
 * Validation is deliberately split. The client checks the obviously-wrong cases
 * for instant feedback; the server remains the authority (plausible loads,
 * totals, ranges) and its message is shown when it disagrees.
 */
class WorkoutEditorViewModel(
    private val workoutRepository: WorkoutRepository,
    defaultUnit: WeightUnit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutEditorUiState(unit = defaultUnit))
    val uiState: StateFlow<WorkoutEditorUiState> = _uiState.asStateFlow()

    /** Source of stable row ids. Only ever incremented. */
    private var nextId = 1L

    /** Guard so a recomposition does not wipe a draft the user is typing. */
    private var hasLoaded = false

    private fun newId(): Long = nextId++

    private fun newSet(): SetDraft = SetDraft(id = newId())

    private fun newExercise(): ExerciseDraft =
        ExerciseDraft(id = newId(), sets = listOf(newSet()))

    /**
     * Prepare the form. Called from the screen's `LaunchedEffect`.
     *
     * @param workoutId null to create a new session, otherwise the id to edit
     */
    fun load(workoutId: String?) {
        if (hasLoaded) return
        hasLoaded = true

        // Suggestions are a nicety, so a failure here is deliberately silent:
        // the user can still type any exercise name they like.
        loadSuggestions()

        if (workoutId == null) {
            _uiState.value = _uiState.value.copy(exercises = listOf(newExercise()))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, workoutId = workoutId)

        viewModelScope.launch {
            when (val result = workoutRepository.workout(workoutId)) {
                is ApiResult.Success -> {
                    val workout = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        workoutId = workout.id,
                        name = workout.name,
                        durationText = workout.durationMinutes?.toString().orEmpty(),
                        notes = workout.notes.orEmpty(),
                        exercises = workout.exercises
                            .map { exercise ->
                                ExerciseDraft(
                                    id = newId(),
                                    name = exercise.name,
                                    sets = exercise.sets.map { set ->
                                        SetDraft(
                                            id = newId(),
                                            repsText = set.reps.toString(),
                                            weightText = formatEditableNumber(set.weight),
                                        )
                                    }.ifEmpty { listOf(newSet()) },
                                )
                            }
                            .ifEmpty { listOf(newExercise()) },
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

    private fun loadSuggestions() {
        viewModelScope.launch {
            val result = workoutRepository.exerciseCatalogue()
            if (result is ApiResult.Success) {
                _uiState.value = _uiState.value.copy(suggestedExercises = result.data)
            }
        }
    }

    // --- Top-level fields ----------------------------------------------------

    fun onNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameError = false, errorMessage = null)
    }

    fun onDurationChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            durationText = value,
            durationError = false,
            errorMessage = null,
        )
    }

    fun onNotesChanged(value: String) {
        _uiState.value = _uiState.value.copy(notes = value)
    }

    // --- Exercises and sets --------------------------------------------------

    /** Apply [transform] to the exercise with [id], leaving the others alone. */
    private fun mutateExercise(id: Long, transform: (ExerciseDraft) -> ExerciseDraft) {
        val state = _uiState.value
        _uiState.value = state.copy(
            exercises = state.exercises.map { if (it.id == id) transform(it) else it },
            errorMessage = null,
        )
    }

    fun addExercise() {
        _uiState.value = _uiState.value.copy(exercises = _uiState.value.exercises + newExercise())
    }

    fun removeExercise(id: Long) {
        val state = _uiState.value
        // Never let the list reach zero: a workout has at least one exercise.
        if (!state.canRemoveExercise) return
        _uiState.value = state.copy(
            exercises = state.exercises.filterNot { it.id == id },
            exerciseNameErrors = state.exerciseNameErrors - id,
        )
    }

    fun onExerciseNameChanged(id: Long, value: String) {
        mutateExercise(id) { it.copy(name = value) }
        _uiState.value =
            _uiState.value.copy(exerciseNameErrors = _uiState.value.exerciseNameErrors - id)
    }

    /** Fill an exercise name from a suggestion chip. */
    fun applySuggestion(id: Long, name: String) {
        mutateExercise(id) { it.copy(name = name) }
        _uiState.value =
            _uiState.value.copy(exerciseNameErrors = _uiState.value.exerciseNameErrors - id)
    }

    fun addSet(exerciseId: Long) {
        mutateExercise(exerciseId) { it.copy(sets = it.sets + newSet()) }
    }

    fun removeSet(exerciseId: Long, setId: Long) {
        mutateExercise(exerciseId) { exercise ->
            // Every exercise keeps at least one set row.
            if (exercise.sets.size <= 1) exercise
            else exercise.copy(sets = exercise.sets.filterNot { it.id == setId })
        }
        _uiState.value = _uiState.value.copy(
            setRepsErrors = _uiState.value.setRepsErrors - setId,
            setWeightErrors = _uiState.value.setWeightErrors - setId,
        )
    }

    fun onRepsChanged(exerciseId: Long, setId: Long, value: String) {
        updateSet(exerciseId, setId) { it.copy(repsText = value) }
        _uiState.value = _uiState.value.copy(setRepsErrors = _uiState.value.setRepsErrors - setId)
    }

    fun onWeightChanged(exerciseId: Long, setId: Long, value: String) {
        updateSet(exerciseId, setId) { it.copy(weightText = value) }
        _uiState.value =
            _uiState.value.copy(setWeightErrors = _uiState.value.setWeightErrors - setId)
    }

    private fun updateSet(exerciseId: Long, setId: Long, transform: (SetDraft) -> SetDraft) {
        mutateExercise(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.id == setId) transform(it) else it })
        }
    }

    // --- Saving --------------------------------------------------------------

    /**
     * Validate and submit the form.
     *
     * If anything is wrong, the offending rows are flagged and nothing is sent.
     * Otherwise the whole workout is written and [WorkoutEditorUiState.isSaved]
     * flips, which the screen uses to navigate back.
     */
    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return

        val parsed = parseWorkoutDraft(state)
        if (parsed.hasErrors) {
            _uiState.value = state.copy(
                nameError = parsed.nameError,
                durationError = parsed.durationError,
                noExercises = parsed.noExercises,
                exerciseNameErrors = parsed.missingExerciseNames,
                setRepsErrors = parsed.badReps,
                setWeightErrors = parsed.badWeights,
                errorMessage = null,
            )
            return
        }

        _uiState.value = state.copy(
            isSaving = true,
            errorMessage = null,
            nameError = false,
            durationError = false,
            noExercises = false,
            exerciseNameErrors = emptySet(),
            setRepsErrors = emptySet(),
            setWeightErrors = emptySet(),
        )

        viewModelScope.launch {
            val result = if (state.workoutId == null) {
                workoutRepository.addWorkout(
                    name = parsed.name,
                    exercises = parsed.exercises,
                    durationMinutes = parsed.durationMinutes,
                    notes = parsed.notes,
                )
            } else {
                workoutRepository.updateWorkout(
                    id = state.workoutId,
                    name = parsed.name,
                    exercises = parsed.exercises,
                    durationMinutes = parsed.durationMinutes,
                    notes = parsed.notes,
                )
            }

            when (result) {
                is ApiResult.Success -> _uiState.value =
                    _uiState.value.copy(isSaving = false, isSaved = true)

                is ApiResult.Failure -> {
                    // The client already checked the obvious cases, so a server
                    // failure is usually a network problem or an unexpected rule
                    // (an implausible load). Either way its message explains.
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.message,
                        nameError = result.fieldErrors.containsKey("name"),
                    )
                }
            }
        }
    }

    companion object {
        /** Mirrors the server's WORKOUT_MIN_MINUTES. */
        const val MIN_DURATION_MINUTES = 1

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val unit = AppModule.authRepository.currentUser?.weightUnit ?: WeightUnit.KG
                WorkoutEditorViewModel(AppModule.workoutRepository, defaultUnit = unit)
            }
        }
    }
}

/**
 * What [parseWorkoutDraft] made of the form.
 *
 * The row sets hold draft ids, so the screen can flag exactly the rows at fault.
 */
internal data class WorkoutDraftParse(
    val nameError: Boolean,
    val durationError: Boolean,
    val noExercises: Boolean,
    val durationMinutes: Int?,
    val notes: String?,
    val name: String,
    val exercises: List<WorkoutExerciseRequest>,
    val missingExerciseNames: Set<Long>,
    val badReps: Set<Long>,
    val badWeights: Set<Long>,
) {
    val hasErrors: Boolean
        get() = nameError || durationError || noExercises ||
            missingExerciseNames.isNotEmpty() || badReps.isNotEmpty() || badWeights.isNotEmpty()
}

/**
 * Read the form into request objects, reporting every problem it finds.
 *
 * This is a pure function of the state (no coroutines, no ViewModel) precisely so
 * the rules can be unit-tested directly, and it reports ALL the bad rows at once
 * rather than making the user fix them one at a time.
 *
 * A blank duration is fine ("not recorded"); a non-numeric or out-of-range one is
 * not. A weight of 0 is accepted — bodyweight and assisted exercises exist — but
 * the SERVER still applies the per-unit plausible range and its message wins.
 */
internal fun parseWorkoutDraft(state: WorkoutEditorUiState): WorkoutDraftParse {
    val missingExerciseNames = mutableSetOf<Long>()
    val badReps = mutableSetOf<Long>()
    val badWeights = mutableSetOf<Long>()
    val requests = mutableListOf<WorkoutExerciseRequest>()

    for (exercise in state.exercises) {
        if (exercise.name.isBlank()) {
            missingExerciseNames += exercise.id
            continue
        }

        val sets = mutableListOf<WorkoutSetRequest>()
        for (set in exercise.sets) {
            val reps = set.repsText.trim().toIntOrNull()
            if (reps == null || reps < 1) {
                badReps += set.id
                continue
            }
            // Accept a comma as the decimal separator — many locales write it.
            val weight = set.weightText.trim().replace(',', '.').toDoubleOrNull()
            if (weight == null || weight < 0.0) {
                badWeights += set.id
                continue
            }
            sets += WorkoutSetRequest(reps = reps, weight = weight)
        }

        if (sets.isNotEmpty()) {
            requests += WorkoutExerciseRequest(name = exercise.name.trim(), sets = sets)
        }
    }

    val durationMinutes = state.durationText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    return WorkoutDraftParse(
        nameError = state.name.isBlank(),
        // A blank duration is "not recorded"; anything else must be a whole
        // number of minutes the server will accept (>= 1).
        durationError = state.durationText.isNotBlank() &&
            (durationMinutes == null || durationMinutes < WorkoutEditorViewModel.MIN_DURATION_MINUTES),
        noExercises = state.exercises.isEmpty(),
        durationMinutes = durationMinutes,
        notes = state.notes.trim().ifBlank { null },
        name = state.name.trim(),
        exercises = requests,
        missingExerciseNames = missingExerciseNames,
        badReps = badReps,
        badWeights = badWeights,
    )
}
