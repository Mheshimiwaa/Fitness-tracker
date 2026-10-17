package com.reprise.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reprise.app.data.local.SettingsStore
import com.reprise.app.domain.model.isoDayNumber
import kotlinx.coroutines.flow.first
import java.util.TimeZone

/**
 * Fires at the moment the user asked to be reminded: posts the notification, then
 * schedules the next occurrence.
 *
 * The settings are re-read HERE, at fire time, rather than being baked into the
 * job when it was scheduled. A job can sit queued for a day, so the setting that
 * matters is the one the user has now:
 *
 *   - if they turned the reminder off in the meantime, nothing is posted;
 *   - if the system ran the job late, so that it is no longer one of the chosen
 *     days, nothing is posted (a Tuesday reminder for Mon/Wed/Fri would be
 *     worse than a late one);
 *   - either way the job re-arms, so the reminder is never silently lost.
 */
class GymReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val reminder = store.gymReminder.first()

        val today = isoDayNumber(
            nowMillis = System.currentTimeMillis(),
            timeZone = TimeZone.getDefault(),
        )
        if (reminder.isEnabledOn(today)) {
            GymReminderNotifications.show(applicationContext)
        }

        GymReminderScheduler.sync(applicationContext, reminder)

        // "Failure" here would only mean a retry of a reminder the user has
        // already missed; the next occurrence is scheduled either way.
        return Result.success()
    }
}
