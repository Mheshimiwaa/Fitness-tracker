package com.reprise.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Small formatting helpers shared by the weight and dashboard screens.
 *
 * The API sends timestamps as ISO-8601 strings (e.g. "2026-10-14T08:33:57.367Z")
 * and weights as plain numbers. Turning those into something a person wants to
 * read is a *presentation* concern, so it lives here rather than on the server —
 * which also means the app can localise later without an API change.
 *
 * Everything takes an explicit [Locale] (defaulting to the device's) so the
 * behaviour is deterministic and can be unit-tested.
 */

/** Patterns tried in order when parsing a timestamp. */
private val ISO_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "yyyy-MM-dd'T'HH:mm:ssZ",
    "yyyy-MM-dd",
)

/**
 * Rewrite an ISO timezone into the RFC-822 form [SimpleDateFormat] understands
 * everywhere: a trailing "Z" becomes "+0000", and "+00:00" loses its colon.
 */
private fun normalizeOffset(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.endsWith("Z") || trimmed.endsWith("z")) {
        return trimmed.dropLast(1) + "+0000"
    }
    return trimmed.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
}

/**
 * Parse an ISO-8601 instant.
 *
 * Returns null (rather than throwing) for anything unrecognised, so a surprising
 * value from the server degrades to "show the raw text" instead of crashing.
 */
fun parseIsoInstant(value: String?): Date? {
    if (value.isNullOrBlank()) return null

    val normalized = normalizeOffset(value)
    for (pattern in ISO_PATTERNS) {
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            // Strict parsing stops "2026-13-45" from being silently accepted.
            isLenient = false
            // Only used when the value carries no offset of its own.
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = runCatching { format.parse(normalized) }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

/**
 * A short, human date such as "14 Oct 2026".
 *
 * Falls back to the raw input when it cannot be parsed, so the UI still shows
 * *something* meaningful.
 */
fun formatShortDate(value: String?, locale: Locale = Locale.getDefault()): String {
    val date = parseIsoInstant(value) ?: return value.orEmpty()
    return SimpleDateFormat("d MMM yyyy", locale).format(date)
}

/** Format a weight with a fixed number of decimals, e.g. "82.4". */
fun formatWeight(
    value: Double,
    decimals: Int = 1,
    locale: Locale = Locale.getDefault(),
): String = String.format(locale, "%.${decimals}f", value)

/**
 * Format a weight change with an explicit sign, e.g. "+1.2" or "-2.4".
 *
 * The sign is the whole point: without it a 1.2 kg loss and a 1.2 kg gain would
 * look identical.
 */
fun formatSignedWeight(
    value: Double,
    decimals: Int = 1,
    locale: Locale = Locale.getDefault(),
): String = signedPrefix(value) + formatWeight(kotlin.math.abs(value), decimals, locale)

/**
 * Format a training-volume change with an explicit sign and thousands
 * separators, e.g. "+1,200" or "-800".
 *
 * Shares [formatVolume]'s grouping so a change sits visually beside the volume
 * figure it belongs to.
 */
fun formatSignedVolume(value: Double, locale: Locale = Locale.getDefault()): String =
    signedPrefix(value) + String.format(locale, "%,.0f", kotlin.math.abs(value))

/** Format an integer with an explicit sign, e.g. "+3", "-2", or plain "0". */
fun formatSignedInt(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "-${-value}"
    else -> "0"
}

/** "+", "-", or nothing at zero — the one place that decides a sign. */
private fun signedPrefix(value: Double): String = when {
    value > 0 -> "+"
    value < 0 -> "-"
    else -> ""
}

/**
 * Format a number for a text FIELD the user will edit: "60" rather than
 * "60.0", and "62.5" rather than "62.50000000000001".
 *
 * Always uses [Locale.US] so the decimal point is a "." — the value is parsed
 * back with the same assumption, and a locale that wrote "62,5" would otherwise
 * be ambiguous.
 */
fun formatEditableNumber(value: Double, decimals: Int = 1): String {
    if (!value.isFinite()) return ""
    var text = String.format(Locale.US, "%.${decimals}f", value)
    if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
    return text
}

/**
 * Format a training volume in whole kilograms with thousands separators, e.g.
 * "1,982".
 *
 * Volume (reps x weight, summed) is always expressed in kilograms because it is
 * a figure for COMPARING sessions, not a body weight the user chose a unit for.
 */
fun formatVolume(value: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%,.0f", value)

/** Milliseconds in a day — used to step back from an exclusive end date. */
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * A human week range such as "12–18 Oct 2026".
 *
 * The leaderboard's `endDate` is EXCLUSIVE — it is the following Monday — so a
 * day is subtracted to show the week's real last day. The days are formatted in
 * UTC, the same zone the server uses to pick the window, so what the user reads
 * cannot drift with the device's timezone.
 *
 * A range that crosses a month or a year names both ends ("28 Sep – 4 Oct 2026",
 * "28 Dec 2026 – 3 Jan 2027"); one within a single month shows only the day of
 * the first end ("12–18 Oct 2026").
 *
 * Falls back to an empty string when either end cannot be parsed.
 */
fun formatWeekRange(
    startIso: String?,
    endIsoExclusive: String?,
    locale: Locale = Locale.getDefault(),
): String {
    val start = parseIsoInstant(startIso) ?: return ""
    val endExclusive = parseIsoInstant(endIsoExclusive) ?: return ""
    val end = Date(endExclusive.time - DAY_MS)

    val startDay = calendarUtc(start)
    val endDay = calendarUtc(end)

    return when {
        startDay.get(Calendar.YEAR) != endDay.get(Calendar.YEAR) ->
            "${utcFormat("d MMM yyyy", locale).format(start)} – " +
                utcFormat("d MMM yyyy", locale).format(end)

        startDay.get(Calendar.MONTH) != endDay.get(Calendar.MONTH) ->
            "${utcFormat("d MMM", locale).format(start)} – " +
                utcFormat("d MMM yyyy", locale).format(end)

        else ->
            "${utcFormat("d", locale).format(start)}–" +
                utcFormat("d MMM yyyy", locale).format(end)
    }
}

/** A calendar pinned to UTC, so a date's day/month/year never shift by locale. */
private fun calendarUtc(date: Date): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }

/** A formatter pinned to UTC, matching how the server expresses the week. */
private fun utcFormat(pattern: String, locale: Locale): SimpleDateFormat =
    SimpleDateFormat(pattern, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
