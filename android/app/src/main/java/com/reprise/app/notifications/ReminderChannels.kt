package com.reprise.app.notifications

import androidx.annotation.StringRes
import com.reprise.app.R
import com.reprise.app.domain.model.ReminderSound
import java.security.MessageDigest
import java.util.Locale

/**
 * The notification channel that carries the reminder **for a given sound**.
 *
 * A channel is normally something you create once and forget, but Android
 * FREEZES a channel's sound at creation: calling `setSound` again afterwards is
 * silently ignored. So a channel cannot be reused when the sound changes — the
 * volume, importance and tone the user has already tuned for one sound must not
 * be rewritten for another. The honest way to change the sound is therefore to
 * post the reminder on a DIFFERENT channel, which is what this mapping is for.
 *
 * Three channels exist, one per choice, and the IDs are derived here so that the
 * decision lives in one readable place instead of being spelled out in the
 * notification code and again in the worker.
 */

/**
 * The channel used for the phone's default notification tone.
 *
 * The ID is unchanged from the version of the app that existed before sounds
 * could be chosen, deliberately: the user has already seen this channel and may
 * have allowed or tuned it, and re-using it keeps that work.
 */
private const val DEFAULT_CHANNEL_ID = "gym_reminder"

/** The channel used when the user asked for no sound. */
private const val SILENT_CHANNEL_ID = "gym_reminder_silent"

/** Every channel for an imported sound begins with this. */
private const val CLIP_CHANNEL_PREFIX = "gym_reminder_"

/** Which channel carries the reminder when [sound] is set. */
internal fun reminderChannelId(sound: ReminderSound): String = when (sound) {
    ReminderSound.SystemDefault -> DEFAULT_CHANNEL_ID
    ReminderSound.Silent -> SILENT_CHANNEL_ID
    // One channel per imported sound, keyed by the sound itself: two different
    // files must not share a channel, or the second one would inherit the first
    // one's frozen tone.
    is ReminderSound.Clip -> CLIP_CHANNEL_PREFIX + sound.uri.shortDigest()
}

/**
 * Whether [channelId] is one of this app's reminder channels.
 *
 * Used to clear away the channels for sounds the user has moved on from, so
 * Android's notification settings do not slowly fill up with entries named
 * after files that are no longer in use.
 */
internal fun isReminderChannelId(channelId: String): Boolean =
    channelId == DEFAULT_CHANNEL_ID ||
        channelId == SILENT_CHANNEL_ID ||
        channelId.startsWith(CLIP_CHANNEL_PREFIX)

/**
 * What [sound]'s channel is called in Android's notification settings.
 *
 * The three names differ so the settings list explains itself: the user can see
 * which kind of sound the reminder is using without opening each entry.
 */
@StringRes
internal fun reminderChannelNameRes(sound: ReminderSound): Int = when (sound) {
    ReminderSound.SystemDefault -> R.string.notification_channel_reminder_name
    ReminderSound.Silent -> R.string.notification_channel_reminder_name_silent
    is ReminderSound.Clip -> R.string.notification_channel_reminder_name_custom
}

/**
 * A short, stable fingerprint of the URI.
 *
 * Channel IDs are limited in length and end up visible in Android's settings, so
 * a 12-character digest is used rather than the whole URI. Twelve hex digits
 * (48 bits) makes an accidental collision between two of one user's sounds
 * effectively impossible, and the value survives a restart because it depends
 * only on the URI.
 */
private fun String.shortDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.take(DIGEST_BYTES)
        .joinToString(separator = "") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xFF) }
}

/** Six bytes, rendered as twelve hexadecimal characters. */
private const val DIGEST_BYTES = 6
