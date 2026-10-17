package com.reprise.app.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.reprise.app.R
import com.reprise.app.components.RepRiseCard
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderSound
import com.reprise.app.domain.model.ReminderTime
import com.reprise.app.notifications.GymReminderNotifications
import com.reprise.app.ui.theme.ReminderSoundImport
import com.reprise.app.ui.theme.RepRiseTheme
import java.util.Locale
import kotlin.math.abs

/**
 * The gym reminder editor: a switch, a time, and the days it fires on.
 *
 * The reminder is the app's only NOTIFICATION, and it is set up here because
 * this is where the rest of the user's preferences live. It is stored on the
 * device rather than on the server (see [GymReminder]) — it is about this
 * phone's notifications, like the theme, and it keeps working with no network.
 *
 * The TIME IS PICKED ON A WHEEL, the way an alarm clock's is: two columns that
 * scroll and snap to the middle row. Nothing can be mistyped, so there is no
 * "that is not a time" state to explain, and no keyboard ever covers the card.
 *
 * The card lives in its own file rather than inside ProfileScreen.kt because it
 * is the one card with real local state of its own (a permission request and
 * whether the wheel is open), and because that keeps the profile screen
 * readable.
 */
@Composable
internal fun ReminderCard(
    reminder: GymReminder,
    soundImport: ReminderSoundImport,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (ReminderTime) -> Unit,
    onToggleDay: (ReminderDay) -> Unit,
    onSoundChange: (ReminderSound) -> Unit,
    onImportSound: (Uri) -> Unit,
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

    // Reads the audio file the user picks. `OpenDocument` (rather than
    // GetContent) also covers sounds that live in cloud storage, and gives a
    // long-lived read grant, which is what a document picked now and copied a
    // moment later needs.
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        picked?.let(onImportSound)
    }

    // Only needed on Android 9 and older, where writing into the phone's shared
    // sound library still requires storage access. Asking at the moment the user
    // reaches for a sound keeps it out of the app's startup.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) soundPicker.launch(arrayOf(AUDIO_MIME_TYPE))
    }

    // Survives rotation, so turning the phone with the wheel open does not
    // silently close it.
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    var choosingSound by rememberSaveable { mutableStateOf(false) }

    val chooseSound: () -> Unit = {
        if (needsStoragePermission(context)) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            soundPicker.launch(arrayOf(AUDIO_MIME_TYPE))
        }
    }

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

        // The time reads as the setting itself (large, like an alarm's), with
        // one button that opens the wheel. Nothing here is typed.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = reminder.time.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = { choosingTime = true }) {
                Text(text = stringResource(R.string.profile_reminder_change_time))
            }
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

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_reminder_sound),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.profile_reminder_sound_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = soundLabel(reminder.sound),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = { choosingSound = true }) {
                Text(text = stringResource(R.string.profile_reminder_change_sound))
            }
        }

        when (soundImport) {
            ReminderSoundImport.Idle -> Unit

            ReminderSoundImport.Working -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profile_reminder_sound_importing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is ReminderSoundImport.Failed -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(soundImport.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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

    if (choosingTime) {
        ReminderTimeDialog(
            time = reminder.time,
            onDismiss = { choosingTime = false },
            onConfirm = { picked ->
                choosingTime = false
                // Setting the same time again is a no-op, so nothing is written
                // and no job is re-armed when the user just taps Set.
                if (picked != reminder.time) onTimeChange(picked)
            },
        )
    }

    if (choosingSound) {
        ReminderSoundDialog(
            onDismiss = { choosingSound = false },
            onChooseFile = {
                choosingSound = false
                chooseSound()
            },
            onUseDefault = {
                choosingSound = false
                onSoundChange(ReminderSound.SystemDefault)
            },
            onUseSilent = {
                choosingSound = false
                onSoundChange(ReminderSound.Silent)
            },
        )
    }
}

/** What the reminder currently sounds like, in the user's terms. */
@Composable
private fun soundLabel(sound: ReminderSound): String = when (sound) {
    ReminderSound.SystemDefault -> stringResource(R.string.profile_reminder_sound_default)
    ReminderSound.Silent -> stringResource(R.string.profile_reminder_sound_silent)

    // The imported file's own name is the most useful label, but storage may
    // predate the name being kept, hence the fallback.
    is ReminderSound.Clip -> sound.name.ifBlank {
        stringResource(R.string.profile_reminder_sound_custom)
    }
}

/**
 * The three ways to choose a sound, on top of the card.
 *
 * Choosing a FILE has to go through Android's document picker, because that is
 * how apps are allowed to read one. The other two choices are Android's own and
 * are offered here directly, since a file picker has no way to express "no
 * sound" or "whatever the phone uses".
 */
@Composable
private fun ReminderSoundDialog(
    onDismiss: () -> Unit,
    onChooseFile: () -> Unit,
    onUseDefault: () -> Unit,
    onUseSilent: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_reminder_sound_picker_title)) },
        text = {
            // A Column (not a Row) so the three choices read as a list, each with
            // room for its full sentence.
            Column {
                SoundOption(
                    label = stringResource(R.string.profile_reminder_sound_choose),
                    onClick = onChooseFile,
                )
                SoundOption(
                    label = stringResource(R.string.profile_reminder_sound_use_default),
                    onClick = onUseDefault,
                )
                SoundOption(
                    label = stringResource(R.string.profile_reminder_sound_use_silent),
                    onClick = onUseSilent,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.profile_reminder_cancel))
            }
        },
    )
}

/** One full-width, left-aligned choice inside [ReminderSoundDialog]. */
@Composable
private fun SoundOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, modifier = Modifier.fillMaxWidth())
    }
}

/** What a document picker is asked for. */
private const val AUDIO_MIME_TYPE = "audio/*"

/**
 * Whether importing a sound needs Android's storage permission first.
 *
 * Only on Android 9 and older: from Android 10 the sound library can be written
 * without it. On the versions that need it, the manifest asks for it but does not
 * have it, so the user is prompted on the way in to picking a sound.
 */
private fun needsStoragePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED

/** "Reminds you on Mon, Wed, Fri at 18:00." — built from the current setting. */
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
 * The two-wheel time picker, on top of the card.
 *
 * The wheels run on a DRAFT that is only handed back when the user confirms, so
 * scrolling past a value does not set it — the same reason an alarm clock asks
 * you to press Done. Dismissing the dialog therefore changes nothing.
 */
@Composable
private fun ReminderTimeDialog(
    time: ReminderTime,
    onDismiss: () -> Unit,
    onConfirm: (ReminderTime) -> Unit,
) {
    // What the wheels are sitting on, seeded from the saved time so the dialog
    // opens where the user left it.
    var hour by remember { mutableIntStateOf(time.hour) }
    var minute by remember { mutableIntStateOf(time.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_reminder_picker_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelColumn(
                    values = 0..23,
                    initialValue = time.hour,
                    label = stringResource(R.string.profile_reminder_hour),
                    onCenteredValueChange = { hour = it },
                )
                Text(
                    text = ":",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WheelColumn(
                    values = 0..59,
                    initialValue = time.minute,
                    label = stringResource(R.string.profile_reminder_minute),
                    onCenteredValueChange = { minute = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ReminderTime(hour = hour, minute = minute)) }) {
                Text(text = stringResource(R.string.profile_reminder_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.profile_reminder_cancel))
            }
        },
    )
}

/** How tall one row of a wheel is, and how many rows are visible at once. */
private val WheelRowHeight: Dp = 40.dp
private const val WheelVisibleRows = 5

/**
 * Where one visible row of a wheel sits on screen, in pixels.
 *
 * A local stand-in for the layout type so the centring arithmetic can be
 * exercised by a plain unit test, with no device and no Compose UI involved.
 */
internal data class WheelRowBounds(
    /** Position in the wheel's lazy list, INCLUDING the padding rows. */
    val index: Int,
    /** Distance of the row's top from the top of the visible area. */
    val offset: Int,
    /** Height of the row. */
    val size: Int,
)

/**
 * Which value a wheel is showing in its middle row.
 *
 * The value nearest the viewport's midpoint wins, and the leading padding rows
 * are subtracted to turn a list position back into a value ("row 5" is the
 * third value when two rows are padding). Returns null when the list has not
 * been laid out yet, or when the winner is padding rather than a value — both
 * mean "no value to report yet" rather than a particular number.
 */
internal fun centeredWheelValue(
    rows: List<WheelRowBounds>,
    viewportCentre: Int,
    paddingRows: Int,
    valueCount: Int,
): Int? = rows
    .minByOrNull { abs(it.offset + it.size / 2 - viewportCentre) }
    ?.index
    ?.minus(paddingRows)
    ?.takeIf { it in 0 until valueCount }

/**
 * One snapping column of a wheel: a lazy list whose middle row is the chosen
 * value.
 *
 * Two things make it behave like an alarm clock's wheel rather than a plain
 * list:
 *
 *  1. `SnapPosition.Center` settles a flick with a value in the MIDDLE of the
 *     column, and [WheelVisibleRows] / 2 empty rows are padded at each end so
 *     the first and last values (00 and 59) can reach the middle too. Without
 *     the padding a wheel would stop early at both ends and make them
 *     unselectable.
 *  2. The chosen value is read from the LAYOUT ([LazyListState.layoutInfo])
 *     rather than from the scroll offset. Working out "which row is centred"
 *     from offsets alone goes wrong at the padded ends; asking the layout which
 *     item's midpoint is nearest the viewport's midpoint is right everywhere.
 */
@Composable
private fun WheelColumn(
    values: IntRange,
    initialValue: Int,
    label: String,
    onCenteredValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(values) { values.toList() }
    val paddingRows = WheelVisibleRows / 2

    // Seeded so the wheel OPENS on the saved value, already centred: a value
    // sits in the middle row when the list is scrolled its index rows in.
    val state: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialValue)

    val centeredValue by remember(state) {
        derivedStateOf<Int?> {
            val info = state.layoutInfo
            centeredWheelValue(
                rows = info.visibleItemsInfo.map {
                    WheelRowBounds(index = it.index, offset = it.offset, size = it.size)
                },
                viewportCentre = (info.viewportStartOffset + info.viewportEndOffset) / 2,
                paddingRows = paddingRows,
                valueCount = items.size,
            )
        }
    }

    // Reported outwards so the dialog's Set button reads the value the wheel is
    // sitting on. A LaunchedEffect keeps this off the composition path, so a
    // fling past twenty values does not update state twenty times per frame.
    LaunchedEffect(centeredValue) {
        centeredValue?.let(onCenteredValueChange)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            state = state,
            modifier = Modifier
                .height(WheelRowHeight * WheelVisibleRows)
                .width(72.dp),
            flingBehavior = rememberSnapFlingBehavior(state, snapPosition = SnapPosition.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count = paddingRows) { Spacer(modifier = Modifier.height(WheelRowHeight)) }

            items(count = items.size) { index ->
                val value = items[index]
                val centered = value == centeredValue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelRowHeight)
                        .semantics { contentDescription = "$label $value" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format(Locale.ROOT, "%02d", value),
                        style = if (centered) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = if (centered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            items(count = paddingRows) { Spacer(modifier = Modifier.height(WheelRowHeight)) }
        }
    }
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
                sound = ReminderSound.Clip(uri = "content://media/1", name = "boxing_bell.mp3"),
            ),
            soundImport = ReminderSoundImport.Idle,
            onEnabledChange = {},
            onTimeChange = {},
            onToggleDay = {},
            onSoundChange = {},
            onImportSound = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReminderCardOffPreview() {
    RepRiseTheme {
        ReminderCard(
            reminder = GymReminder(),
            soundImport = ReminderSoundImport.Idle,
            onEnabledChange = {},
            onTimeChange = {},
            onToggleDay = {},
            onSoundChange = {},
            onImportSound = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReminderTimeDialogPreview() {
    RepRiseTheme {
        ReminderTimeDialog(
            time = ReminderTime(hour = 18, minute = 30),
            onDismiss = {},
            onConfirm = {},
        )
    }
}
