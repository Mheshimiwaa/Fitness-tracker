package com.reprise.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reprise.app.MainActivity
import com.reprise.app.R

/**
 * Everything about the gym reminder NOTIFICATION itself: the channel, the tap
 * action and the fact that it is posted at all.
 *
 * Keeping this in one object means the worker only has to ask for the reminder
 * to be shown; it does not need to know about channels, pending intents or the
 * API-level differences between them.
 */
object GymReminderNotifications {

    /** Stable id, so re-posting replaces the previous reminder instead of stacking. */
    private const val NOTIFICATION_ID = 1001

    private const val CHANNEL_ID = "gym_reminder"

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
     * Post the reminder.
     *
     * Returns false when the app is not allowed to post notifications. The caller
     * cannot do much about it, but returning a value keeps that fact visible
     * instead of silently doing nothing.
     */
    fun show(context: Context): Boolean {
        // The channel is created FIRST, before the permission check: it is
        // configuration, not delivery. Registering it even when notifications are
        // blocked means the user can find "Gym reminder" (with its description) in
        // Android's notification settings and allow it there, instead of seeing an
        // app that claims to send a reminder with no categories at all.
        createChannel(context)

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    /**
     * Create the notification channel (Android 8+).
     *
     * Creating a channel that already exists is a no-op, so this can be called
     * every time instead of tracking whether it has been done.
     */
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminder_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminder_description)
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }
}
