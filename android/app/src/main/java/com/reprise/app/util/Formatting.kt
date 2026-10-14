package com.reprise.app.util

import java.text.SimpleDateFormat
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
): String {
    val formatted = formatWeight(kotlin.math.abs(value), decimals, locale)
    val sign = if (value > 0) "+" else if (value < 0) "-" else ""
    return sign + formatted
}
