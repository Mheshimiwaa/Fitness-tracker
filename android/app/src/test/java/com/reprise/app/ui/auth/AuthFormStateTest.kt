package com.reprise.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the two helpers the profile cards use to read [AuthFormState].
 *
 * The profile screen gives every personal field its own card, so the shared form
 * state has to say which card an error or a confirmation belongs to. These tests
 * pin that routing down — it is pure data logic, so a plain JVM test covers it.
 */
class AuthFormStateTest {

    @Test
    fun `a field error is shown only next to that field`() {
        val state = AuthFormState(fieldErrors = mapOf(ProfileField.DISPLAY_NAME to "Too long."))

        assertEquals("Too long.", state.errorFor(ProfileField.DISPLAY_NAME))
        assertNull(state.errorFor(ProfileField.HEIGHT_CM))
    }

    @Test
    fun `a generic failure lands on the field the user acted on`() {
        val state = AuthFormState(
            errorMessage = "Could not reach the server.",
            field = ProfileField.HEIGHT_CM,
        )

        assertEquals("Could not reach the server.", state.errorFor(ProfileField.HEIGHT_CM))
        assertNull(state.errorFor(ProfileField.DISPLAY_NAME))
    }

    @Test
    fun `a specific field error wins over the generic message`() {
        val state = AuthFormState(
            errorMessage = "Validation failed.",
            fieldErrors = mapOf(ProfileField.FITNESS_GOAL to "Unsupported goal."),
            field = ProfileField.FITNESS_GOAL,
        )

        assertEquals("Unsupported goal.", state.errorFor(ProfileField.FITNESS_GOAL))
        // The untouched card stays quiet rather than repeating form-level text.
        assertNull(state.errorFor(ProfileField.WEIGHT_UNIT))
    }

    @Test
    fun `only the field that was saved reports success`() {
        val state = AuthFormState(field = ProfileField.WEIGHT_UNIT, saved = true)

        assertTrue(state.didSave(ProfileField.WEIGHT_UNIT))
        assertFalse(state.didSave(ProfileField.HEIGHT_CM))
    }

    @Test
    fun `a failed save is never mistaken for a successful one`() {
        val state = AuthFormState(
            errorMessage = "Could not reach the server.",
            field = ProfileField.WEIGHT_UNIT,
        )

        assertFalse(state.didSave(ProfileField.WEIGHT_UNIT))
    }
}
