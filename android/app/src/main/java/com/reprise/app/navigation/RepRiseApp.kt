package com.reprise.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.LoadingState
import com.reprise.app.domain.model.AuthState
import com.reprise.app.ui.auth.AuthViewModel
import com.reprise.app.ui.theme.SettingsViewModel

/**
 * The root of the UI: decides whether to show the sign-in flow or the app.
 *
 * "Gate" pattern: instead of navigating to a login screen and popping it later,
 * we simply render different trees for signed-out and signed-in users. The
 * session state is the single source of truth, so there is no way for the app
 * and the UI to disagree about whether somebody is signed in.
 */
@Composable
fun RepRiseApp() {
    // Both ViewModels are scoped to the Activity, so every screen shares one
    // instance and therefore one copy of the state.
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val formState by authViewModel.formState.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val reminder by settingsViewModel.gymReminder.collectAsStateWithLifecycle()
    val soundImport by settingsViewModel.soundImport.collectAsStateWithLifecycle()

    when (val state = authState) {
        // Still validating the stored token — show a spinner, NOT the login form.
        AuthState.Unknown -> LoadingState(message = stringResource(R.string.auth_checking_session))

        AuthState.SignedOut -> AuthNavHost(
            formState = formState,
            onLogin = authViewModel::login,
            onRegister = authViewModel::register,
            onInputChanged = authViewModel::clearMessages,
        )

        is AuthState.SignedIn -> MainScaffold(
            user = state.user,
            formState = formState,
            themeMode = themeMode,
            reminder = reminder,
            onSaveDisplayName = authViewModel::updateDisplayName,
            onChangeFitnessGoal = authViewModel::updateFitnessGoal,
            onChangeWeightUnit = authViewModel::updateWeightUnit,
            onSaveHeight = authViewModel::updateHeight,
            onPhotoPicked = authViewModel::uploadPhoto,
            onRemovePhoto = authViewModel::removePhoto,
            onThemeModeChange = settingsViewModel::setThemeMode,
            onReminderEnabledChange = settingsViewModel::setReminderEnabled,
            onReminderTimeChange = settingsViewModel::setReminderTime,
            onToggleReminderDay = settingsViewModel::toggleReminderDay,
            soundImport = soundImport,
            onReminderSoundChange = settingsViewModel::setReminderSound,
            onImportReminderSound = settingsViewModel::importReminderSound,
            onLogout = authViewModel::logout,
        )
    }
}
