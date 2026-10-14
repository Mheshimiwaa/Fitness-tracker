package com.reprise.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reprise.app.ui.auth.AuthFormState
import com.reprise.app.ui.auth.LoginScreen
import com.reprise.app.ui.auth.RegisterScreen

/**
 * The signed-out navigation graph: Login and Register.
 *
 * It is a separate [NavHost] from the main tabs on purpose — when the user signs
 * in, the whole graph is replaced, which guarantees the auth screens (and their
 * typed-in passwords) cannot be returned to with the back button.
 */
@Composable
fun AuthNavHost(
    formState: AuthFormState,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (displayName: String, username: String, email: String, password: String) -> Unit,
    onInputChanged: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AuthDestination.LOGIN.route,
    ) {
        composable(AuthDestination.LOGIN.route) {
            LoginScreen(
                formState = formState,
                onLogin = onLogin,
                onRegisterClick = {
                    // Clear any error from the previous screen before moving on.
                    onInputChanged()
                    navController.navigate(AuthDestination.REGISTER.route) {
                        launchSingleTop = true
                    }
                },
                onInputChanged = onInputChanged,
            )
        }

        composable(AuthDestination.REGISTER.route) {
            RegisterScreen(
                formState = formState,
                onRegister = onRegister,
                onSignInClick = {
                    onInputChanged()
                    // Return to the login screen rather than stacking another copy.
                    navController.popBackStack(
                        route = AuthDestination.LOGIN.route,
                        inclusive = false,
                    )
                },
                onInputChanged = onInputChanged,
            )
        }
    }
}
