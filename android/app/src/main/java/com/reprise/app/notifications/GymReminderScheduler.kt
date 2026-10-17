package com.reprise.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.millisUntilNextReminder
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Keeps WorkManager in step with the user's reminder setting.
 *
 * ONE-SHOT WORK THAT RE-ARMS ITSELF, rather than a repeating job, because the
 * time of day matters here. A repeating job drifts: if the system defers one run
 * by an hour to save battery, every later run is an hour late too. A one-shot
 * job re-computes the next exact occurrence every time it fires, so the reminder
 * returns to the chosen time instead of sliding later and later.
 *
 * WorkManager (not an exact alarm) because a reminder may legitimately be a few
 * minutes late, and because WorkManager survives a reboot on its own — no
 * BOOT_COMPLETED receiver, and no SCHEDULE_EXACT_ALARM permission to ask for.
 *
 * [sync] is IDEMPOTENT: it replaces the pending job with one that matches the
 * current setting, so it is safe to call on every change and on every app start.
 */
object GymReminderScheduler {

    /**
     * A single unique name means there is only ever ONE reminder job, however
     * many times the user edits the time — replacing it cannot leave a second
     * job behind to fire at the old time.
     */
    private const val UNIQUE_WORK_NAME = "gym_reminder"

    fun sync(context: Context, reminder: GymReminder) {
        val workManager = WorkManager.getInstance(context)

        if (!reminder.enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val delayMillis = millisUntilNextReminder(
            reminder = reminder,
            nowMillis = System.currentTimeMillis(),
            timeZone = TimeZone.getDefault(),
        ) ?: return

        val request = OneTimeWorkRequestBuilder<GymReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            // REPLACE, not KEEP: the point of a sync is that the newest setting
            // wins, including when the user has just changed the time.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
