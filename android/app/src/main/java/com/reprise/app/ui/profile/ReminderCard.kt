package com.reprise.app.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reprise.app.R
import com.reprise.app.components.RepRiseCard
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderTime
import com.reprise.app.notifications.GymReminderNotifications
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * The gym reminder editor: a switch, a time, and the days it fires on.
 *
 * The reminder is the app's only NOTIFICATION, and it is set up here because
 * this is where the rest of the user's preferences live. It is stored on the
 * device rather than on the server (see [GymReminder]) — it is about this
 * phone's notifications, like the theme, and it keeps working with no network.
 *
 * The card lives in its own file rather than inside ProfileScreen.kt because it
 * is the one card with real local state of its own (a permission request and a
 * text draft), and because that keeps the profile screen readable.
 */
@Composable
internal fun ReminderCard(
    reminder: GymReminder,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (ReminderTime) -> Unit,
    onToggleDay: (ReminderDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Whether Android will actually DELIVER the notification, which is separate
    // from whether the user turned the setting on. Re-read on every change to
    // the switch so that coming back from Android's settings is enough to
    // refresh it.
    var notificationsAllowed by remember { mutableStateOf(GymReminderNotifications.areEnabled(context)) }
    LaunchedEffect(reminder.enabled) {
        notificationsAllowed = GymReminderNotifications.areEnabled(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted || GymReminderNotifications.areEnabled(context)
    }

    // Re-keyed on the saved time so that saving normalises what is displayed
    // ("8:30" becomes "08:30") without rewriting the box on every keystroke,
    // which would move the cursor while the user types.
    var draft by rememberSaveable(reminder.time) {
        mutableStateOf(reminder.time.toString())
    }

    val parsed = parseReminderTimeDraft(draft)
    val isInvalid = parsed is ReminderTimeDraft.Invalid
    val canSave = parsed is ReminderTimeDraft.Value && parsed.time != reminder.time

    RepRiseCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.profile_reminder_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { enabled ->
                    onEnabledChange(enabled)
                    // Ask only at the moment it matters, and only on the Android
                    // versions where notifications are a runtime permission.
                    if (enabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationsAllowed
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (reminder.enabled) {
                    R.string.profile_reminder_on
                } else {
                    R.string.profile_reminder_off
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (reminder.enabled && !notificationsAllowed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.profile_reminder_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_reminder_time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.profile_reminder_time_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_reminder_time_field)) },
            singleLine = true,
            isError = isInvalid,
            supportingText = isInvalid?.let {
                { Text(stringResource(R.string.profile_reminder_time_invalid)) }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { (parsed as? ReminderTimeDraft.Value)?.let { onTimeChange(it.time) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(text = stringResource(R.string.profile_reminder_save_time))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_reminder_days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.profile_reminder_days_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Seven labels do not fit one phone-width row, so they wrap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderDay.entries.forEach { day ->
                FilterChip(
                    selected = day in reminder.days,
                    onClick = { onToggleDay(day) },
                    label = { Text(stringResource(day.shortLabelRes)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reminderSummary(reminder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Reminds you on Mon, Wed and Fri at 18:00." — built from the current setting. */
@Composable
private fun reminderSummary(reminder: GymReminder): String {
    // `map` is inline, so stringResource may be called inside it; joinToString is
    // not, which is why the labels are resolved first.
    val dayLabels = ReminderDay.entries
        .filter { it in reminder.days }
        .map { stringResource(it.shortLabelRes) }

    return when {
        dayLabels.size == ReminderDay.entries.size ->
            stringResource(R.string.profile_reminder_summary_daily, reminder.time.toString())

        else ->
            stringResource(
                R.string.profile_reminder_summary_days,
                dayLabels.joinToString(", "),
                reminder.time.toString(),
            )
    }
}

/**
 * What a time box can contain.
 *
 * A plain function rather than logic inside the composable, for the same reason
 * `parseHeightDraft` is: it is the only place that decides whether what the user
 * typed is a time or a mistake, so it can be unit-tested without a UI.
 */
internal sealed interface ReminderTimeDraft {
    /** Nothing typed. */
    data object Blank : ReminderTimeDraft

    /** A usable time of day. */
    data class Value(val time: ReminderTime) : ReminderTimeDraft

    /** Not "HH:mm", or out of range. */
    data object Invalid : ReminderTimeDraft
}

/**
 * Read the reminder time box.
 *
 * The hour and minute may each be one or two digits ("8:30" is accepted and
 * normalised to 08:30 when saved), but both parts must be present and inside a
 * real clock — "25:00", "18:60" and "18" are all rejected rather than being
 * silently clamped into something the user did not ask for.
 */
internal fun parseReminderTimeDraft(input: String): ReminderTimeDraft {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ReminderTimeDraft.Blank

    val parts = trimmed.split(":")
    if (parts.size != 2) return ReminderTimeDraft.Invalid

    val hour = parts[0].toIntOrNull() ?: return ReminderTimeDraft.Invalid
    val minute = parts[1].toIntOrNull() ?: return ReminderTimeDraft.Invalid

    if (hour !in ReminderTime.HOUR_MIN..ReminderTime.HOUR_MAX) return ReminderTimeDraft.Invalid
    if (minute !in ReminderTime.MINUTE_MIN..ReminderTime.MINUTE_MAX) return ReminderTimeDraft.Invalid

    return ReminderTimeDraft.Value(ReminderTime(hour = hour, minute = minute))
}

@Preview(showBackground = true)
@Composable
private fun ReminderCardPreview() {
    RepRiseTheme {
        ReminderCard(
            reminder = GymReminder(
                enabled = true,
                time = ReminderTime(hour = 18, minute = 30),
                days = setOf(ReminderDay.MONDAY, ReminderDay.WEDNESDAY, ReminderDay.FRIDAY),
            ),
            onEnabledChange = {},
            onTimeChange = {},
            onToggleDay = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReminderCardOffPreview() {
    RepRiseTheme {
        ReminderCard(
            reminder = GymReminder(),
            onEnabledChange = {},
            onTimeChange = {},
            onToggleDay = {},
        )
    }
}
