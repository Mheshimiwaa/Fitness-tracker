package com.reprise.app.ui.profile

import com.reprise.app.domain.model.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the reminder time box.
 *
 * This is the only place that decides whether what the user typed is a time or a
 * mistake, so it is tested directly rather than through the UI. The rule that
 * matters: anything that is not a real clock time must be REJECTED, never
 * clamped, because silently turning "18:60" into 19:00 would set a reminder the
 * user did not ask for.
 */
class ReminderTimeDraftTest {

    @Test
    fun `reads a plain 24-hour time`() {
        assertEquals(
            ReminderTimeDraft.Value(ReminderTime(hour = 18, minute = 30)),
            parseReminderTimeDraft("18:30"),
        )
    }

    @Test
    fun `accepts the boundaries of the day`() {
        assertEquals(
            ReminderTimeDraft.Value(ReminderTime(hour = 0, minute = 0)),
            parseReminderTimeDraft("00:00"),
        )
        assertEquals(
            ReminderTimeDraft.Value(ReminderTime(hour = 23, minute = 59)),
            parseReminderTimeDraft("23:59"),
        )
    }

    @Test
    fun `accepts a single digit hour or minute`() {
        // "8:30" is a time a person would really type; it is normalised when saved.
        assertEquals(
            ReminderTimeDraft.Value(ReminderTime(hour = 8, minute = 30)),
            parseReminderTimeDraft("8:30"),
        )
        assertEquals(
            ReminderTimeDraft.Value(ReminderTime(hour = 18, minute = 5)),
            parseReminderTimeDraft("18:5"),
        )
    }

    @Test
    fun `ignores surrounding spaces`() {
        assertEquals(
            ReminderTimeDraft.Value(ReminderTime(hour = 7, minute = 0)),
            parseReminderTimeDraft("  7:00  "),
        )
    }

    @Test
    fun `an empty box is not an error`() {
        assertEquals(ReminderTimeDraft.Blank, parseReminderTimeDraft(""))
        assertEquals(ReminderTimeDraft.Blank, parseReminderTimeDraft("   "))
    }

    @Test
    fun `rejects an hour that is not on the clock`() {
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("24:00"))
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("25:30"))
    }

    @Test
    fun `rejects a minute that is not on the clock`() {
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("18:60"))
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("18:99"))
    }

    @Test
    fun `rejects text that is not a time`() {
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("evening"))
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("18"))
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("18:30:00"))
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("18h30"))
        assertEquals(ReminderTimeDraft.Invalid, parseReminderTimeDraft("-1:00"))
    }

    @Test
    fun `only a valid value may be saved`() {
        val parsed = parseReminderTimeDraft("18:30")

        assertTrue(parsed is ReminderTimeDraft.Value)
        assertEquals(ReminderTime(hour = 18, minute = 30), (parsed as ReminderTimeDraft.Value).time)
    }
}
