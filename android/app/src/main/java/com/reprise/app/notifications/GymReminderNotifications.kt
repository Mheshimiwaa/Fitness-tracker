package com.reprise.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reprise.app.MainActivity
import com.reprise.app.R
import com.reprise.app.domain.model.ReminderSound

/**
 * Everything about the gym reminder NOTIFICATION itself: the channel, the tap
 * action and the fact that it is posted at all.
 *
 * Keeping this in one object means the worker only has to ask for the reminder
 * to be shown; it does not need to know about channels, pending intents or the
 * API-level differences between them.
 *
 * The SOUND is the one part that is not straightforward, because Android plays
 * it, not this app, and a channel's sound is fixed once the channel is created.
 * [ReminderChannels] derives which channel a given sound needs; all this object
 * has to do is create that channel with the right tone and post on it.
 */
object GymReminderNotifications {

    /** Stable id, so re-posting replaces the previous reminder instead of stacking. */
    private const val NOTIFICATION_ID = 1001

    /**
     * Whether the app is currently allowed to post notifications.
     *
     * On Android 13+ this is false until the user grants POST_NOTIFICATIONS, so
     * the profile screen can tell the difference between "the reminder is on" and
     * "the reminder is on but Android will not show it".
     */
    fun areEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Post the reminder with [sound].
     *
     * Returns false when the app is not allowed to post notifications. The caller
     * cannot do much about it, but returning a value keeps that fact visible
     * instead of silently doing nothing.
     */
    fun show(context: Context, sound: ReminderSound): Boolean {
        val channelId = reminderChannelId(sound)

        // The channel is created FIRST, before the permission check: it is
        // configuration, not delivery. Registering it even when notifications are
        // blocked means the user can find "Gym reminder" (with its description) in
        // Android's notification settings and allow it there, instead of seeing an
        // app that claims to send a reminder with no categories at all.
        createChannel(context, sound, channelId)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            // Reuse the running task rather than starting a second copy of the app.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_flame)
            .setContentTitle(context.getString(R.string.notification_gym_reminder_title))
            .setContentText(context.getString(R.string.notification_gym_reminder_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_gym_reminder_text))
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // On Android 7 there are no channels, so the tone has to be given to the
        // notification itself. Doing this only below 8 matters: from 8 onward the
        // channel owns the sound, and a notification-level sound would be a second
        // place for the setting to live.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(preOreoSound(sound))
        }

        manager.notify(NOTIFICATION_ID, builder.build())
        return true
    }

    /**
     * Create the channel for [sound], and retire the ones it replaces.
     *
     * Creating a channel that already exists is a no-op, so this can be called
     * every time instead of tracking whether it has been done. Retiring the
     * others is what stops Android's notification settings from accumulating an
     * entry per sound the user has tried.
     */
    private fun createChannel(context: Context, sound: ReminderSound, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            channelId,
            context.getString(reminderChannelNameRes(sound)),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminder_description)
            applySound(sound)
        }

        manager.createNotificationChannel(channel)

        // A channel that has been replaced is dead weight: the reminder will
        // never be posted on it again. The CURRENT channel is obviously kept.
        manager.notificationChannels
            .filter { isReminderChannelId(it.id) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    /**
     * Give the channel its tone.
     *
     * The default is expressed by NOT calling `setSound`, rather than by passing
     * the system's default URI: the channel then tracks the phone's notification
     * sound, so changing the tone in Android's settings changes the reminder too.
     */
    private fun NotificationChannel.applySound(sound: ReminderSound) {
        when (sound) {
            ReminderSound.SystemDefault -> Unit

            ReminderSound.Silent -> setSound(null, null)

            is ReminderSound.Clip -> setSound(
                Uri.parse(sound.uri),
                AudioAttributes.Builder()
                    // Announcing a notification, not playing music, is what lets
                    // Android route it through the notification volume and mute it
                    // in silent mode.
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    /**
     * The same choice for Android 7, which has no channels.
     *
     * There, the sound belongs to the notification rather than to a category, so
     * it is set on the builder. The system's default tone has to be named
     * explicitly: a null URI on a notification means "play nothing", not "use the
     * default", so leaving it null would have made the reminder silent on those
     * versions.
     */
    private fun preOreoSound(sound: ReminderSound): Uri? =
        when (sound) {
            ReminderSound.SystemDefault -> Settings.System.DEFAULT_NOTIFICATION_URI

            // A null URI is how a notification says "no sound".
            ReminderSound.Silent -> null

            is ReminderSound.Clip -> Uri.parse(sound.uri)
        }
}
