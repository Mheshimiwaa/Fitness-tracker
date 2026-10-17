package com.reprise.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [parseHeightDraft] — the only place that decides whether what
 * the user typed into the height box is a height, a request to remove one, or a
 * mistake.
 *
 * A plain JVM test: the function is pure, so no coroutines or device are needed.
 *
 * The bounds mirror `HEIGHT_CM_MIN` / `HEIGHT_CM_MAX` in the backend's
 * `config/constants.js`. The server still has the final say; its message wins if
 * the two ever drift.
 */
class HeightDraftTest {

    @Test
    fun `an empty box means clear the height`() {
        assertEquals(HeightDraft.Clear, parseHeightDraft(""))
        assertEquals(HeightDraft.Clear, parseHeightDraft("   "))
    }

    @Test
    fun `a whole number in range is a value`() {
        assertEquals(HeightDraft.Value(180), parseHeightDraft("180"))
        // Surrounding whitespace from the keyboard must not upset it.
        assertEquals(HeightDraft.Value(180), parseHeightDraft("  180  "))
    }

    @Test
    fun `the range boundaries themselves are accepted`() {
        assertEquals(HeightDraft.Value(50), parseHeightDraft("50"))
        assertEquals(HeightDraft.Value(272), parseHeightDraft("272"))
    }

    @Test
    fun `a number outside the range is invalid`() {
        assertEquals(HeightDraft.Invalid, parseHeightDraft("49"))
        assertEquals(HeightDraft.Invalid, parseHeightDraft("273"))
        assertEquals(HeightDraft.Invalid, parseHeightDraft("0"))
        assertEquals(HeightDraft.Invalid, parseHeightDraft("-180"))
    }

    @Test
    fun `text and decimals are invalid rather than silently rounded`() {
        assertEquals(HeightDraft.Invalid, parseHeightDraft("tall"))
        assertEquals(HeightDraft.Invalid, parseHeightDraft("180.5"))
        assertEquals(HeightDraft.Invalid, parseHeightDraft("1,80"))
    }
}
