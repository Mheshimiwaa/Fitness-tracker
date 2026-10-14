package com.reprise.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reprise.app.R
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.ScreenContainer
import com.reprise.app.domain.model.FitnessGoal
import com.reprise.app.domain.model.ThemeMode
import com.reprise.app.domain.model.User
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.auth.AuthErrorBanner
import com.reprise.app.ui.auth.AuthFormState
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * The signed-in user's profile.
 *
 * Phase 2 shows the real account details fetched from the API, lets the user
 * rename themselves (a genuine `PATCH /api/users/me`), pick a theme, and sign
 * out. Preferences such as units, height and goal are edited in Phase 7.
 */
@Composable
fun ProfileScreen(
    user: User,
    formState: AuthFormState,
    themeMode: ThemeMode,
    onSaveDisplayName: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
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

        AppearanceCard(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
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

/** Read-only view of the account fields. */
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
        InfoRow(
            label = stringResource(R.string.profile_goal),
            value = stringResource(user.fitnessGoal.labelRes),
        )
        InfoRow(
            label = stringResource(R.string.profile_unit),
            value = stringResource(user.weightUnit.labelRes),
        )
        InfoRow(
            label = stringResource(R.string.profile_height),
            value = user.heightCm?.let { stringResource(R.string.profile_height_value, it) }
                ?: stringResource(R.string.profile_not_set),
        )
    }
}

/** Edits the display name — the one profile field the UI writes back in Phase 2. */
@Composable
private fun RenameCard(
    currentName: String,
    formState: AuthFormState,
    onSave: (String) -> Unit,
) {
    // `key`-ing the text field on the current name means a successful save
    // (which changes user.displayName) refreshes the box.
    var draftName by rememberSaveable(currentName) { mutableStateOf(currentName) }

    val nameError = formState.fieldErrors["displayName"]
    val bannerMessage = if (formState.fieldErrors.isEmpty()) formState.errorMessage else null

    RepRiseCard {
        Text(
            text = stringResource(R.string.profile_edit_name_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (bannerMessage != null) {
            AuthErrorBanner(message = bannerMessage)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (formState.profileSaved) {
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
            onSaveDisplayName = {},
            onThemeModeChange = {},
            onLogout = {},
        )
    }
}
