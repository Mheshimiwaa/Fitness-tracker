package com.reprise.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reprise.app.R
import com.reprise.app.domain.model.ThemeMode
import com.reprise.app.domain.model.User
import com.reprise.app.ui.auth.AuthFormState
import com.reprise.app.ui.friends.FriendsScreen
import com.reprise.app.ui.home.HomeScreen
import com.reprise.app.ui.profile.ProfileScreen
import com.reprise.app.ui.weights.WeightsScreen
import com.reprise.app.ui.workouts.WorkoutsScreen

/**
 * The signed-in shell: a bottom navigation bar plus the four top-level screens,
 * and the pushed screens those tabs open.
 *
 * This is shown only when a valid session exists (see [RepRiseApp]). The
 * signed-in [user] and the session callbacks are passed down explicitly, which
 * keeps every screen a plain function of its inputs and easy to preview.
 */
@Composable
fun MainScaffold(
    user: User,
    formState: AuthFormState,
    themeMode: ThemeMode,
    onSaveDisplayName: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()

    // Observe the current destination so we can highlight the selected tab and
    // decide whether the bar should be visible at all.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Before the NavHost has composed, `currentRoute` is null — treat that as a
    // tab so the bar does not flash away on the first frame.
    val isTopLevel = currentRoute == null ||
        TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            // Detail screens (e.g. the weight log) are pushed ON TOP of a tab and
            // hide the bar, so the back arrow is unambiguously the way out.
            if (isTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        // Keep a single copy of each tab on the back
                                        // stack and remember where the user was.
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { DestinationIcon(destination) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            // Apply the Scaffold's padding so content is not hidden behind the bar.
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    user = user,
                    onOpenWeights = { navController.navigate(WEIGHT_ROUTE) },
                )
            }
            composable(TopLevelDestination.WORKOUTS.route) { WorkoutsScreen() }
            composable(TopLevelDestination.FRIENDS.route) { FriendsScreen() }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(
                    user = user,
                    formState = formState,
                    themeMode = themeMode,
                    onSaveDisplayName = onSaveDisplayName,
                    onThemeModeChange = onThemeModeChange,
                    onLogout = onLogout,
                )
            }

            // A pushed detail screen, reachable from the Home tab.
            composable(WEIGHT_ROUTE) {
                WeightsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Picks the right icon for a tab.
 *
 * Two icons are custom vector drawables (dumbbell, group of people) because
 * Material's compact "core" icon set does not include them.
 */
@Composable
private fun DestinationIcon(destination: TopLevelDestination) {
    when (destination) {
        TopLevelDestination.HOME -> Icon(Icons.Filled.Home, contentDescription = null)
        TopLevelDestination.WORKOUTS ->
            Icon(painterResource(R.drawable.ic_workouts), contentDescription = null)
        TopLevelDestination.FRIENDS ->
            Icon(painterResource(R.drawable.ic_friends), contentDescription = null)
        TopLevelDestination.PROFILE -> Icon(Icons.Filled.Person, contentDescription = null)
    }
}
