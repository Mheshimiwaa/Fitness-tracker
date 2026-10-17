package com.reprise.app.domain.model

import androidx.annotation.StringRes
import com.reprise.app.R
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The gym reminder: a nudge to train, at a time the user picks, on the days the
 * user picks.
 *
 * This is a DEVICE preference, not an account setting — it is about this phone's
 * notifications, exactly like the theme — so it lives in DataStore rather than
 * on the server. That also means the reminder works with no network at all.
 *
 * Two decisions are baked into the types below:
 *
 *  1. DAYS ARE NUMBERED THE ISO WAY (Monday = 1 … Sunday = 7). `java.util.Calendar`
 *     numbers them from Sunday, which is a classic source of off-by-one bugs, so
 *     the conversion happens in exactly one place — [ReminderDay.fromCalendarDay].
 *  2. THE SCHEDULING ARITHMETIC IS A PURE FUNCTION ([millisUntilNextReminder]).
 *     Working out "when is the next 18:00 on a Monday?" is the part that is easy
 *     to get wrong, so it takes its clock and time zone as parameters and can be
 *     tested without a device, an alarm, or a real wait.
 *
 * `java.time` is deliberately not used: the app supports API 24, where those
 * classes are not available without core-library desugaring.
 */

/** A weekday the reminder can fire on, ISO-numbered (Monday = 1 … Sunday = 7). */
enum class ReminderDay(
    val isoNumber: Int,
    @param:StringRes val shortLabelRes: Int,
) {
    MONDAY(1, R.string.day_monday_short),
    TUESDAY(2, R.string.day_tuesday_short),
    WEDNESDAY(3, R.string.day_wednesday_short),
    THURSDAY(4, R.string.day_thursday_short),
    FRIDAY(5, R.string.day_friday_short),
    SATURDAY(6, R.string.day_saturday_short),
    SUNDAY(7, R.string.day_sunday_short),
    ;

    companion object {
        /**
         * The ISO number of a `java.util.Calendar.DAY_OF_WEEK` value.
         *
         * Calendar counts Sunday as 1 and Saturday as 7; ISO counts Monday as 1
         * and Sunday as 7. This is the only place the two are translated.
         */
        fun fromCalendarDay(calendarDay: Int): Int =
            if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1

        /** The day with this ISO number, or null when the number is out of range. */
        fun fromIsoNumber(isoNumber: Int): ReminderDay? =
            entries.firstOrNull { it.isoNumber == isoNumber }

        /** Every day of the week. The default, so the reminder works unconfigured. */
        val ALL: Set<ReminderDay> = entries.toSet()
    }
}

/** A time of day on the 24-hour clock, in the device's own time zone. */
data class ReminderTime(val hour: Int, val minute: Int) {

    /**
     * "18:00". Zero-padded and formatted with [Locale.ROOT] so it always reads
     * back as the ASCII "HH:mm" the parser expects, whatever the phone's locale.
     */
    override fun toString(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    companion object {
        /** 18:00 — after work for most people, and easy to change. */
        val DEFAULT = ReminderTime(hour = 18, minute = 0)

        /** The hour and minute range a valid time must fall inside. */
        const val HOUR_MIN = 0
        const val HOUR_MAX = 23
        const val MINUTE_MIN = 0
        const val MINUTE_MAX = 59
    }
}

/**
 * A complete reminder setting.
 *
 * [days] is a set because the user picks arbitrary days (Mon/Wed/Fri is the
 * common gym rhythm) and it is stored as a set, so there is no order to preserve
 * or "which index is which day" bug to have.
 */
data class GymReminder(
    val enabled: Boolean = false,
    val time: ReminderTime = ReminderTime.DEFAULT,
    val days: Set<ReminderDay> = ReminderDay.ALL,
    /**
     * What the reminder sounds like. The default is the phone's own notification
     * tone, which is what the reminder used before it became choosable — so a
     * reminder set up earlier keeps behaving exactly as it did.
     */
    val sound: ReminderSound = ReminderSound.SystemDefault,
) {
    /** True when this reminder should fire this many days into the week. */
    fun isEnabledOn(isoDayNumber: Int): Boolean =
        enabled && days.any { it.isoNumber == isoDayNumber }
}

/**
 * Add or remove a day, refusing to remove the last one.
 *
 * A reminder with no days selected could never fire, which would leave the user
 * with a switch that does nothing. Rather than allow a state the feature cannot
 * honour, the last remaining day stays put.
 */
fun toggleReminderDay(days: Set<ReminderDay>, day: ReminderDay): Set<ReminderDay> =
    when {
        day !in days -> days + day
        days.size == 1 -> days
        else -> days - day
    }

/**
 * How long until this reminder's next occurrence, in milliseconds — or null when
 * it is off, has no days, or (in principle) never matches.
 *
 * "Next" means strictly after [nowMillis]: at exactly 18:00:00.000 the answer is
 * tomorrow, not zero, so a job that has just fired cannot immediately re-fire
 * itself in a loop.
 *
 * The candidate instants are built with [Calendar] in the given [timeZone], so
 * "18:00" means 18:00 on the user's clock — including the days on either side of
 * a daylight-saving change, which [Calendar] normalises for us.
 */
fun millisUntilNextReminder(
    reminder: GymReminder,
    nowMillis: Long,
    timeZone: TimeZone,
): Long? {
    if (reminder.days.isEmpty()) return null

    val calendar = Calendar.getInstance(timeZone)

    // A week plus one day of offsets is enough to reach any enabled weekday from
    // any starting day; the extra day covers "today's time has already passed".
    for (offsetDays in 0..7) {
        calendar.timeInMillis = nowMillis
        calendar.add(Calendar.DAY_OF_MONTH, offsetDays)
        calendar.set(Calendar.HOUR_OF_DAY, reminder.time.hour)
        calendar.set(Calendar.MINUTE, reminder.time.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val candidate = calendar.timeInMillis
        if (candidate <= nowMillis) continue

        val isoDay = ReminderDay.fromCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
        if (reminder.days.any { it.isoNumber == isoDay }) return candidate - nowMillis
    }

    return null
}

/** The ISO day number (Monday = 1 … Sunday = 7) that [nowMillis] falls on. */
fun isoDayNumber(nowMillis: Long, timeZone: TimeZone): Int {
    val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
    return ReminderDay.fromCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
}
