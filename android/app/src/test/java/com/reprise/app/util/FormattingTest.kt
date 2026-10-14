package com.reprise.app.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the presentation helpers.
 *
 * Dates are compared against `java.time.Instant`, which is an INDEPENDENT oracle
 * for the parsing: it means a bug in our own formatter cannot make the test agree
 * with itself. (java.time is used only in these JVM tests — the app itself avoids
 * it because it is not available on every supported Android version.)
 */
class FormattingTest {

    @Test
    fun `parses an instant with milliseconds and a trailing Z`() {
        val parsed = parseIsoInstant("2026-10-14T08:33:57.367Z")

        assertNotNull(parsed)
        assertEquals(
            java.time.Instant.parse("2026-10-14T08:33:57.367Z").toEpochMilli(),
            parsed!!.time,
        )
    }

    @Test
    fun `parses an offset written with a colon`() {
        val parsed = parseIsoInstant("2026-10-14T08:33:57+00:00")

        assertNotNull(parsed)
        assertEquals(
            java.time.Instant.parse("2026-10-14T08:33:57Z").toEpochMilli(),
            parsed!!.time,
        )
    }

    @Test
    fun `parses a date with no time as midnight UTC`() {
        val parsed = parseIsoInstant("2026-10-14")

        assertNotNull(parsed)
        assertEquals(
            java.time.Instant.parse("2026-10-14T00:00:00Z").toEpochMilli(),
            parsed!!.time,
        )
    }

    @Test
    fun `unparseable timestamps return null instead of throwing`() {
        assertNull(parseIsoInstant("not a date"))
        assertNull(parseIsoInstant(""))
        assertNull(parseIsoInstant(null))
    }

    @Test
    fun `a short date falls back to the raw text when it cannot be parsed`() {
        // Better to show something odd than to crash or show nothing.
        assertEquals("garbage", formatShortDate("garbage"))
        assertEquals("", formatShortDate(null))
    }

    @Test
    fun `a short date renders a day, month and year`() {
        val formatted = formatShortDate("2026-10-14T08:33:57.367Z", Locale.US)

        assertTrue("unexpected format: $formatted", formatted.contains("2026"))
        assertEquals(3, formatted.split(" ").size)
    }

    @Test
    fun `weights are formatted to one decimal place`() {
        assertEquals("82.4", formatWeight(82.4, locale = Locale.US))
        assertEquals("82.0", formatWeight(82.0, locale = Locale.US))
        assertEquals("90.7", formatWeight(90.7184, locale = Locale.US))
    }

    @Test
    fun `a signed weight always shows its direction`() {
        assertEquals("+1.2", formatSignedWeight(1.2, locale = Locale.US))
        assertEquals("-2.4", formatSignedWeight(-2.4, locale = Locale.US))
        // Zero is neither a gain nor a loss, so it carries no sign.
        assertEquals("0.0", formatSignedWeight(0.0, locale = Locale.US))
    }

    @Test
    fun `an editable number drops a pointless decimal point`() {
        // Prefilling a form with "60.0" looks like noise; "60" does not.
        assertEquals("60", formatEditableNumber(60.0))
        assertEquals("62.5", formatEditableNumber(62.5))
        assertEquals("100", formatEditableNumber(100.0))
        // Floating point noise must not leak into the field.
        assertEquals("82.4", formatEditableNumber(82.40000000000001))
    }

    @Test
    fun `volume is a whole number of kilograms with grouping`() {
        assertEquals("1,982", formatVolume(1982.0, locale = Locale.US))
        assertEquals("0", formatVolume(0.0, locale = Locale.US))
        assertEquals("12,345", formatVolume(12345.4, locale = Locale.US))
    }

    @Test
    fun `a signed volume keeps grouping and shows its direction`() {
        assertEquals("+1,200", formatSignedVolume(1200.0, locale = Locale.US))
        assertEquals("-800", formatSignedVolume(-800.0, locale = Locale.US))
        assertEquals("0", formatSignedVolume(0.0, locale = Locale.US))
    }

    @Test
    fun `a signed whole number shows its direction but no decimal point`() {
        assertEquals("+3", formatSignedInt(3))
        assertEquals("-2", formatSignedInt(-2))
        assertEquals("0", formatSignedInt(0))
    }

    @Test
    fun `a week range shows the inclusive last day within one month`() {
        // The server's `endDate` is the NEXT Monday (exclusive), so the range
        // must read 12–18, not 12–19.
        assertEquals(
            "12–18 Oct 2026",
            formatWeekRange("2026-10-12T00:00:00.000Z", "2026-10-19T00:00:00.000Z", Locale.US),
        )
    }

    @Test
    fun `a week range that crosses a month names both months`() {
        assertEquals(
            "28 Sep – 4 Oct 2026",
            formatWeekRange("2026-09-28T00:00:00.000Z", "2026-10-05T00:00:00.000Z", Locale.US),
        )
    }

    @Test
    fun `a week range that crosses a year names both years`() {
        assertEquals(
            "28 Dec 2026 – 3 Jan 2027",
            formatWeekRange("2026-12-28T00:00:00.000Z", "2027-01-04T00:00:00.000Z", Locale.US),
        )
    }

    @Test
    fun `a week range is blank when either end cannot be parsed`() {
        assertEquals("", formatWeekRange("nonsense", "2026-10-19T00:00:00.000Z", Locale.US))
        assertEquals("", formatWeekRange("2026-10-12T00:00:00.000Z", null, Locale.US))
    }
}
