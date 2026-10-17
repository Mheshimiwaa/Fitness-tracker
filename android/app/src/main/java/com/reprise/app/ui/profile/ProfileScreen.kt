package com.reprise.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reprise.app.R
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.ScreenContainer
import com.reprise.app.domain.model.FitnessGoal
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderTime
import com.reprise.app.domain.model.ThemeMode
import com.reprise.app.domain.model.User
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.auth.AuthFormState
import com.reprise.app.ui.auth.ProfileField
import com.reprise.app.ui.auth.didSave
import com.reprise.app.ui.auth.errorFor
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * The signed-in user's profile.
 *
 * Every value shown here comes from the API, and the four personal fields —
 * display name, training goal, preferred unit and height — are all genuinely
 * writable via `PATCH /api/users/me`. Two preferences are local instead: the
 * theme, and the gym reminder, because both are about this device rather than
 * the account. See [ReminderCard] for why.
 *
 * Each card saves on its own and therefore shows its own confirmation and its
 * own errors; see [AuthFormState.field] for how one shared state serves them all.
 */
@Composable
fun ProfileScreen(
    user: User,
    formState: AuthFormState,
    themeMode: ThemeMode,
    reminder: GymReminder,
    onSaveDisplayName: (String) -> Unit,
    onChangeFitnessGoal: (FitnessGoal) -> Unit,
    onChangeWeightUnit: (WeightUnit) -> Unit,
    onSaveHeight: (Int?) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (ReminderTime) -> Unit,
    onToggleReminderDay: (ReminderDay) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenContainer(title = stringResource(R.string.title_profile), modifier = modifier) {
        IdentityCard(user = user)

        AccountDetailsCard(user = user)

        RenameCard(
            currentName = user.displayName,
            formState = formState,
            onSave = onSaveDisplayName,
        )

        TrainingCard(
            goal = user.fitnessGoal,
            unit = user.weightUnit,
            formState = formState,
            onChangeGoal = onChangeFitnessGoal,
            onChangeUnit = onChangeWeightUnit,
        )

        HeightCard(
            currentHeightCm = user.heightCm,
            formState = formState,
            onSave = onSaveHeight,
        )

        AppearanceCard(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )

        ReminderCard(
            reminder = reminder,
            onEnabledChange = onReminderEnabledChange,
            onTimeChange = onReminderTimeChange,
            onToggleDay = onToggleReminderDay,
        )

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(R.string.action_log_out))
        }
    }
}

/** Avatar placeholder plus the name and @handle. */
@Composable
private fun IdentityCard(user: User) {
    RepRiseCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Column {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The account facts that cannot be changed from the app.
 *
 * Goal, unit and height used to be listed here too; now that each has its own
 * editor, repeating them would just be somewhere for the two views to disagree.
 */
@Composable
private fun AccountDetailsCard(user: User) {
    RepRiseCard {
        Text(
            text = stringResource(R.string.profile_account_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow(
            label = stringResource(R.string.profile_email),
            value = user.email ?: stringResource(R.string.profile_not_set),
        )
    }
}

/** Edits the display name. */
@Composable
private fun RenameCard(
    currentName: String,
    formState: AuthFormState,
    onSave: (String) -> Unit,
) {
    // `key`-ing the text field on the current name means a successful save
    // (which changes user.displayName) refreshes the box.
    var draftName by rememberSaveable(currentName) { mutableStateOf(currentName) }

    val nameError = formState.errorFor(ProfileField.DISPLAY_NAME)

    RepRiseCard {
        Text(
            text = stringResource(R.string.profile_edit_name_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (formState.didSave(ProfileField.DISPLAY_NAME)) {
            SavedNotice()
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_display_name)) },
            singleLine = true,
            enabled = !formState.isSubmitting,
            isError = nameError != null,
            supportingText = nameError?.let { text -> { Text(text) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onSave(draftName) },
            modifier = Modifier.fillMaxWidth(),
            enabled = draftName.isNotBlank() &&
                draftName != currentName &&
                !formState.isSubmitting,
        ) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

/** Picks the training goal and the unit every weight is shown in. */
@Composable
private fun TrainingCard(
    goal: FitnessGoal,
    unit: WeightUnit,
    formState: AuthFormState,
    onChangeGoal: (FitnessGoal) -> Unit,
    onChangeUnit: (WeightUnit) -> Unit,
) {
    val goalError = formState.errorFor(ProfileField.FITNESS_GOAL)
    val unitError = formState.errorFor(ProfileField.WEIGHT_UNIT)

    RepRiseCard {
        Text(
            text = stringResource(R.string.profile_training_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel(
            text = stringResource(R.string.profile_goal),
            hint = stringResource(R.string.profile_goal_hint),
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Four chips do not fit on one phone-width row, so they wrap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitnessGoal.entries.forEach { option ->
                FilterChip(
                    selected = option == goal,
                    // Tapping the current value would be a pointless request.
                    onClick = { if (option != goal) onChangeGoal(option) },
                    enabled = !formState.isSubmitting,
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        FieldError(message = goalError)
        if (formState.didSave(ProfileField.FITNESS_GOAL)) {
            Spacer(modifier = Modifier.height(8.dp))
            SavedNotice()
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel(
            text = stringResource(R.string.profile_unit),
            hint = stringResource(R.string.profile_unit_hint),
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    onClick = { if (option != unit) onChangeUnit(option) },
                    enabled = !formState.isSubmitting,
                    label = { Text(stringResource(option.shortLabelRes)) },
                )
            }
        }
        FieldError(message = unitError)
        if (formState.didSave(ProfileField.WEIGHT_UNIT)) {
            Spacer(modifier = Modifier.height(8.dp))
            SavedNotice()
        }
    }
}

/** Edits the height used by the dashboard's BMI. An empty box removes it. */
@Composable
private fun HeightCard(
    currentHeightCm: Int?,
    formState: AuthFormState,
    onSave: (Int?) -> Unit,
) {
    // Re-keyed on the saved height so the box shows the new value after a save,
    // and starts empty when the height has just been cleared.
    var draft by rememberSaveable(currentHeightCm) {
        mutableStateOf(currentHeightCm?.toString().orEmpty())
    }

    val parsed = parseHeightDraft(draft)
    val serverError = formState.errorFor(ProfileField.HEIGHT_CM)

    val isInvalid = parsed is HeightDraft.Invalid
    val canSave = when (parsed) {
        is HeightDraft.Invalid -> false
        is HeightDraft.Clear -> currentHeightCm != null
        is HeightDraft.Value -> parsed.cm != currentHeightCm
    }

    val supporting = when {
        isInvalid -> stringResource(R.string.profile_height_invalid, HEIGHT_CM_MIN, HEIGHT_CM_MAX)
        serverError != null -> serverError
        else -> stringResource(R.string.profile_height_hint)
    }

    RepRiseCard {
        Text(
            text = stringResource(R.string.profile_height),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (formState.didSave(ProfileField.HEIGHT_CM)) {
            SavedNotice()
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_height_field)) },
            singleLine = true,
            enabled = !formState.isSubmitting,
            isError = isInvalid || serverError != null,
            supportingText = { Text(supporting) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                when (parsed) {
                    is HeightDraft.Value -> onSave(parsed.cm)
                    is HeightDraft.Clear -> onSave(null)
                    is HeightDraft.Invalid -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave && !formState.isSubmitting,
        ) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

/** Lets the user choose System / Light / Dark. */
@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    RepRiseCard {
        Text(
            text = stringResource(R.string.profile_appearance_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == themeMode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(stringResource(mode.labelRes)) },
                )
            }
        }
    }
}

/** A small caption above a group of chips. */
@Composable
private fun SectionLabel(text: String, hint: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The error line under a group of chips, when there is one. */
@Composable
private fun FieldError(message: String?) {
    if (message == null) return

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** The confirmation shown by the card whose field was just saved. */
@Composable
private fun SavedNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.profile_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** One label/value line inside a card. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * The shortest and tallest heights the server accepts.
 *
 * Kept in step with `HEIGHT_CM_MIN` / `HEIGHT_CM_MAX` in the backend's
 * `config/constants.js` so the app can explain the rule before making a request.
 */
private const val HEIGHT_CM_MIN = 50
private const val HEIGHT_CM_MAX = 272

/** The three things a height box can contain. */
internal sealed interface HeightDraft {
    /** Nothing typed: the user wants the height removed. */
    data object Clear : HeightDraft

    /** A usable number of centimetres. */
    data class Value(val cm: Int) : HeightDraft

    /** Not a whole number, or outside the range the server accepts. */
    data object Invalid : HeightDraft
}

/**
 * Read the height box.
 *
 * This is a plain function rather than logic inside the composable so it can be
 * unit-tested without a UI: it is the only place that decides whether what the
 * user typed is a height, a request to clear one, or a mistake.
 */
internal fun parseHeightDraft(input: String): HeightDraft {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return HeightDraft.Clear

    val cm = trimmed.toIntOrNull() ?: return HeightDraft.Invalid
    return if (cm in HEIGHT_CM_MIN..HEIGHT_CM_MAX) HeightDraft.Value(cm) else HeightDraft.Invalid
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    RepRiseTheme {
        ProfileScreen(
            user = User(
                id = "1",
                displayName = "Alex Lifter",
                username = "alex_lifter",
                email = "alex@example.com",
                profileImageUrl = null,
                fitnessGoal = FitnessGoal.GAIN_MUSCLE,
                weightUnit = WeightUnit.KG,
                heightCm = 180,
            ),
            formState = AuthFormState(),
            themeMode = ThemeMode.SYSTEM,
            reminder = GymReminder(
                enabled = true,
                time = ReminderTime(hour = 18, minute = 30),
                days = setOf(ReminderDay.MONDAY, ReminderDay.WEDNESDAY, ReminderDay.FRIDAY),
            ),
            onSaveDisplayName = {},
            onChangeFitnessGoal = {},
            onChangeWeightUnit = {},
            onSaveHeight = {},
            onThemeModeChange = {},
            onReminderEnabledChange = {},
            onReminderTimeChange = {},
            onToggleReminderDay = {},
            onLogout = {},
        )
    }
}
