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
}
