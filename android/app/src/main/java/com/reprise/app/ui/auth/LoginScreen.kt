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
 * The sign-in screen.
 *
 * It is completely stateless with respect to the network: the fields are local
 * UI state, and everything about the request (`isSubmitting`, errors) arrives
 * through [formState]. The screen reports intent with `onLogin`.
 */
@Composable
fun LoginScreen(
    formState: AuthFormState,
    onLogin: (username: String, password: String) -> Unit,
    onRegisterClick: () -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val usernameError = formState.fieldErrors["username"]
    val passwordError = formState.fieldErrors["password"]
    val canSubmit = username.isNotBlank() && password.isNotBlank() && !formState.isSubmitting

    // A form-level failure is only worth a banner when no individual field is at
    // fault — otherwise the same sentence would appear twice.
    val bannerMessage = if (formState.fieldErrors.isEmpty()) formState.errorMessage else null

    AuthScreen(
        title = stringResource(R.string.auth_login_title),
        subtitle = stringResource(R.string.auth_login_subtitle),
        errorMessage = bannerMessage,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_username_or_email)) },
            singleLine = true,
            enabled = !formState.isSubmitting,
            isError = usernameError != null,
            supportingText = {
                Text(usernameError ?: stringResource(R.string.field_username_or_email_hint))
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
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
            supportingText = passwordError,
            isError = passwordError != null,
            enabled = !formState.isSubmitting,
        )

        AuthSubmitButton(
            text = stringResource(R.string.action_sign_in),
            isSubmitting = formState.isSubmitting,
            enabled = canSubmit,
            onClick = { onLogin(username, password) },
        )

        TextButton(
            onClick = onRegisterClick,
            enabled = !formState.isSubmitting,
        ) {
            Text(
                text = stringResource(R.string.action_go_to_register),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    RepRiseTheme {
        LoginScreen(
            formState = AuthFormState(),
            onLogin = { _, _ -> },
            onRegisterClick = {},
            onInputChanged = {},
        )
    }
}
