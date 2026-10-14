package com.reprise.app.ui.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [parseWorkoutDraft] — the editor's validation rules.
 *
 * They are straightforward JVM tests because the function is PURE: no coroutines,
 * no ViewModel, no device. That is the whole reason the rules were pulled out of
 * [WorkoutEditorViewModel] into a free function.
 *
 * The bounds here mirror `backend/src/validators/workout.validators.js`; the
 * server still has the final say on plausible loads, and its message wins.
 */
class WorkoutDraftParseTest {

    private fun state(
        name: String = "Push day",
        durationText: String = "",
        notes: String = "",
        exercises: List<ExerciseDraft> = listOf(
            ExerciseDraft(id = 1, name = "Bench Press", sets = listOf(SetDraft(id = 2, "8", "60")))
        ),
    ) = WorkoutEditorUiState(
        name = name,
        durationText = durationText,
        notes = notes,
        exercises = exercises,
    )

    @Test
    fun `a complete form parses into requests with no errors`() {
        val parsed = parseWorkoutDraft(state(durationText = "52", notes = "felt strong"))

        assertFalse(parsed.hasErrors)
        assertEquals("Push day", parsed.name)
        assertEquals(52, parsed.durationMinutes)
        assertEquals("felt strong", parsed.notes)
        val exercise = parsed.exercises.single()
        assertEquals("Bench Press", exercise.name)
        assertEquals(8, exercise.sets.single().reps)
        assertEquals(60.0, exercise.sets.single().weight, 0.0001)
        // The unit is left to the server (the user's preferred unit).
        assertNull(exercise.unit)
    }

    @Test
    fun `a blank name is an error`() {
        val parsed = parseWorkoutDraft(state(name = "   "))

        assertTrue(parsed.nameError)
        assertTrue(parsed.hasErrors)
    }

    @Test
    fun `a blank exercise name flags that row and skips it`() {
        val parsed = parseWorkoutDraft(
            state(
                exercises = listOf(
                    ExerciseDraft(id = 7, name = "", sets = listOf(SetDraft(id = 8, "8", "60"))),
                    ExerciseDraft(id = 9, name = "Squat", sets = listOf(SetDraft(id = 10, "5", "100"))),
                )
            )
        )

        assertTrue(parsed.missingExerciseNames.contains(7L))
        // The well-formed exercise is still parsed, so the user sees the real rows.
        assertEquals(listOf("Squat"), parsed.exercises.map { it.name })
    }

    @Test
    fun `reps must be a positive whole number`() {
        val parsed = parseWorkoutDraft(
            state(
                exercises = listOf(
                    ExerciseDraft(
                        id = 1,
                        name = "Bench Press",
                        sets = listOf(
                            SetDraft(id = 2, "0", "60"),
                            SetDraft(id = 3, "", "60"),
                            SetDraft(id = 4, "eight", "60"),
                            SetDraft(id = 5, "8", "60"),
                        ),
                    )
                )
            )
        )

        assertEquals(setOf(2L, 3L, 4L), parsed.badReps)
        assertEquals(1, parsed.exercises.single().sets.size)
        assertEquals(8, parsed.exercises.single().sets.single().reps)
    }

    @Test
    fun `weight accepts zero but not a negative`() {
        val parsed = parseWorkoutDraft(
            state(
                exercises = listOf(
                    ExerciseDraft(
                        id = 1,
                        name = "Pull-up",
                        sets = listOf(
                            // 0 kg = a bodyweight set, which is legitimate.
                            SetDraft(id = 2, "8", "0"),
                            SetDraft(id = 3, "8", "-5"),
                            SetDraft(id = 4, "8", ""),
                        ),
                    )
                )
            )
        )

        assertEquals(setOf(3L, 4L), parsed.badWeights)
        assertEquals(1, parsed.exercises.single().sets.size)
        assertEquals(0.0, parsed.exercises.single().sets.single().weight, 0.0001)
    }

    @Test
    fun `a comma decimal separator is accepted`() {
        // Many locales write "62,5"; treating it as invalid would be hostile.
        val parsed = parseWorkoutDraft(
            state(
                exercises = listOf(
                    ExerciseDraft(id = 1, name = "Squat", sets = listOf(SetDraft(id = 2, "5", "62,5"))),
                )
            )
        )

        assertFalse(parsed.hasErrors)
        assertEquals(62.5, parsed.exercises.single().sets.single().weight, 0.0001)
    }

    @Test
    fun `a blank duration means not recorded rather than an error`() {
        val parsed = parseWorkoutDraft(state(durationText = "   "))

        assertNull(parsed.durationMinutes)
        assertFalse(parsed.durationError)
    }

    @Test
    fun `a non-numeric or zero duration is an error`() {
        assertTrue(parseWorkoutDraft(state(durationText = "soon")).durationError)
        assertTrue(parseWorkoutDraft(state(durationText = "0")).durationError)
        assertTrue(parseWorkoutDraft(state(durationText = "-5")).durationError)
    }

    @Test
    fun `a blank note becomes null so it is not stored as empty text`() {
        assertNull(parseWorkoutDraft(state(notes = "   ")).notes)
    }

    @Test
    fun `a form with no exercises is reported rather than sent`() {
        val parsed = parseWorkoutDraft(state(exercises = emptyList()))

        assertTrue(parsed.noExercises)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.exercises.isEmpty())
    }
}
