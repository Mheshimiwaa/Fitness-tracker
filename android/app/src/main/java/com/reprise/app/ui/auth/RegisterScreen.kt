package com.reprise.app.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.reprise.app.R
import com.reprise.app.ui.theme.RepRiseTheme

/**
 * The "create an account" screen.
 *
 * Client-side we only check that nothing is blank — the server owns the real
 * rules (username pattern, password strength, uniqueness) and returns per-field
 * messages that we display beneath the matching box.
 */
@Composable
fun RegisterScreen(
    formState: AuthFormState,
    onRegister: (displayName: String, username: String, email: String, password: String) -> Unit,
    onSignInClick: () -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val displayNameError = formState.fieldErrors["displayName"]
    val usernameError = formState.fieldErrors["username"]
    val emailError = formState.fieldErrors["email"]
    val passwordError = formState.fieldErrors["password"]

    val allFilled = displayName.isNotBlank() &&
        username.isNotBlank() &&
        email.isNotBlank() &&
        password.isNotBlank()
    val canSubmit = allFilled && !formState.isSubmitting

    // Show the form-level banner only when no field is individually at fault.
    val bannerMessage = if (formState.fieldErrors.isEmpty()) formState.errorMessage else null

    AuthScreen(
        title = stringResource(R.string.auth_register_title),
        subtitle = stringResource(R.string.auth_register_subtitle),
        errorMessage = bannerMessage,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {
                displayName = it
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_display_name)) },
            singleLine = true,
            enabled = !formState.isSubmitting,
            isError = displayNameError != null,
            supportingText = {
                Text(displayNameError ?: stringResource(R.string.field_display_name_hint))
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_username)) },
            singleLine = true,
            enabled = !formState.isSubmitting,
            isError = usernameError != null,
            supportingText = {
                Text(usernameError ?: stringResource(R.string.field_username_hint))
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_email)) },
            singleLine = true,
            enabled = !formState.isSubmitting,
            isError = emailError != null,
            supportingText = {
                Text(emailError ?: stringResource(R.string.field_email_hint))
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        PasswordField(
            value = password,
            onValueChange = {
                password = it
                onInputChanged()
            },
            label = stringResource(R.string.field_password),
            supportingText = passwordError ?: stringResource(R.string.field_password_hint),
            isError = passwordError != null,
            enabled = !formState.isSubmitting,
        )

        AuthSubmitButton(
            text = stringResource(R.string.action_create_account),
            isSubmitting = formState.isSubmitting,
            enabled = canSubmit,
            onClick = { onRegister(displayName, username, email, password) },
        )

        TextButton(
            onClick = onSignInClick,
            enabled = !formState.isSubmitting,
        ) {
            Text(
                text = stringResource(R.string.action_go_to_login),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    RepRiseTheme {
        RegisterScreen(
            formState = AuthFormState(),
            onRegister = { _, _, _, _ -> },
            onSignInClick = {},
            onInputChanged = {},
        )
    }
}
