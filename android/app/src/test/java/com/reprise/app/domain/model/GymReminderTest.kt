package com.reprise.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for the gym reminder's rules.
 *
 * The scheduling arithmetic is the reason this feature has tests at all: "the
 * next Monday at 18:00" is easy to get subtly wrong (off-by-one days, a time in
 * the past firing immediately, ISO versus Calendar weekday numbering), and a
 * wrong answer means a reminder that silently never arrives. Passing a fixed
 * clock and a fixed time zone makes every case exact.
 */
class GymReminderTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** Monday 19 October 2026, 10:00 UTC — a fixed point in the middle of a day. */
    private val mondayMorningUtc: Long = utcMillis(2026, Calendar.OCTOBER, 19, 10, 0)

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        timeZone: TimeZone = utc,
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    // --- the next occurrence -------------------------------------------------

    @Test
    fun `later today is the next occurrence`() {
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 18, minute = 0),
            days = ReminderDay.ALL,
        )

        val delay = millisUntilNextReminder(reminder, mondayMorningUtc, utc)

        // 10:00 -> 18:00 is eight hours.
        assertEquals(8 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `a time that has already passed today waits for tomorrow`() {
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 9, minute = 0),
            days = ReminderDay.ALL,
        )

        val delay = millisUntilNextReminder(reminder, mondayMorningUtc, utc)

        // 10:00 Monday -> 09:00 Tuesday is 23 hours.
        assertEquals(23 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `skips the days the user did not pick`() {
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 18, minute = 0),
            days = setOf(ReminderDay.WEDNESDAY),
        )

        val delay = millisUntilNextReminder(reminder, mondayMorningUtc, utc)

        // Monday 10:00 -> Wednesday 18:00 is 2 days and 8 hours.
        assertEquals((2 * 24 + 8) * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `firing exactly on the minute schedules the next one, not a second`() {
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 18, minute = 0),
            days = ReminderDay.ALL,
        )
        val exactlySix = utcMillis(2026, Calendar.OCTOBER, 19, 18, 0)

        val delay = millisUntilNextReminder(reminder, exactlySix, utc)

        // Not zero: a job that has just fired must not re-fire in a tight loop.
        assertEquals(24 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `an empty day set never fires`() {
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 18, minute = 0),
            days = emptySet(),
        )

        assertNull(millisUntilNextReminder(reminder, mondayMorningUtc, utc))
    }

    @Test
    fun `the time of day is read on the phone's own clock`() {
        // 10:00 UTC is 13:00 in a fixed +03:00 zone, so 18:00 local is five hours
        // away — the reminder follows the wall clock, not the server's.
        val plusThree: TimeZone = TimeZone.getTimeZone("GMT+03:00")
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 18, minute = 0),
            days = ReminderDay.ALL,
        )

        val delay = millisUntilNextReminder(reminder, mondayMorningUtc, plusThree)

        assertEquals(5 * 60 * 60 * 1000L, delay)
    }

    @Test
    fun `a week of offsets is enough to find any chosen day`() {
        // Today is Monday and only Sunday is enabled: that is six days away, so a
        // search that gave up after a few days would wrongly return null.
        val reminder = GymReminder(
            enabled = true,
            time = ReminderTime(hour = 8, minute = 0),
            days = setOf(ReminderDay.SUNDAY),
        )

        val delay = millisUntilNextReminder(reminder, mondayMorningUtc, utc)

        // Monday 10:00 -> Sunday 08:00 is 5 days and 22 hours.
        assertEquals((5 * 24 + 22) * 60 * 60 * 1000L, delay)
    }

    // --- which day it is -----------------------------------------------------

    @Test
    fun `calendar weekdays are translated to ISO numbers`() {
        // Calendar numbers Sunday as 1; ISO numbers Monday as 1. Getting this
        // backwards would shift every reminder by a day.
        assertEquals(1, ReminderDay.fromCalendarDay(Calendar.MONDAY))
        assertEquals(6, ReminderDay.fromCalendarDay(Calendar.SATURDAY))
        assertEquals(7, ReminderDay.fromCalendarDay(Calendar.SUNDAY))
    }

    @Test
    fun `the ISO day number matches the day of the week`() {
        assertEquals(1, isoDayNumber(mondayMorningUtc, utc))
        // Tuesday 20 October 2026.
        assertEquals(2, isoDayNumber(utcMillis(2026, Calendar.OCTOBER, 20, 10, 0), utc))
        // Sunday 25 October 2026.
        assertEquals(7, isoDayNumber(utcMillis(2026, Calendar.OCTOBER, 25, 10, 0), utc))
    }

    @Test
    fun `an unknown day number has no day`() {
        assertNull(ReminderDay.fromIsoNumber(0))
        assertNull(ReminderDay.fromIsoNumber(8))
    }

    // --- the setting itself --------------------------------------------------

    @Test
    fun `a disabled reminder fires on no day at all`() {
        val reminder = GymReminder(enabled = false, days = ReminderDay.ALL)

        assertFalse(reminder.isEnabledOn(isoDayNumber(mondayMorningUtc, utc)))
    }

    @Test
    fun `an enabled reminder fires on the days it was given`() {
        val reminder = GymReminder(
            enabled = true,
            days = setOf(ReminderDay.MONDAY, ReminderDay.FRIDAY),
        )

        assertTrue(reminder.isEnabledOn(1))
        assertFalse(reminder.isEnabledOn(2))
        assertTrue(reminder.isEnabledOn(5))
    }

    @Test
    fun `every day is the default`() {
        val reminder = GymReminder()

        assertEquals(7, reminder.days.size)
        assertFalse(reminder.enabled)
        assertEquals(ReminderTime(hour = 18, minute = 0), reminder.time)
    }

    // --- picking days --------------------------------------------------------

    @Test
    fun `tapping a day adds it`() {
        val days = toggleReminderDay(setOf(ReminderDay.MONDAY), ReminderDay.WEDNESDAY)

        assertEquals(setOf(ReminderDay.MONDAY, ReminderDay.WEDNESDAY), days)
    }

    @Test
    fun `tapping a chosen day removes it`() {
        val days = toggleReminderDay(
            setOf(ReminderDay.MONDAY, ReminderDay.WEDNESDAY),
            ReminderDay.WEDNESDAY,
        )

        assertEquals(setOf(ReminderDay.MONDAY), days)
    }

    @Test
    fun `the last remaining day cannot be removed`() {
        // Otherwise the user is left with an on switch that can never fire.
        val days = toggleReminderDay(setOf(ReminderDay.MONDAY), ReminderDay.MONDAY)

        assertEquals(setOf(ReminderDay.MONDAY), days)
    }

    // --- display -------------------------------------------------------------

    @Test
    fun `times are zero padded so they read as clock times`() {
        assertEquals("18:00", ReminderTime(hour = 18, minute = 0).toString())
        assertEquals("09:05", ReminderTime(hour = 9, minute = 5).toString())
        assertEquals("00:00", ReminderTime(hour = 0, minute = 0).toString())
        assertEquals("23:59", ReminderTime(hour = 23, minute = 59).toString())
    }
}
