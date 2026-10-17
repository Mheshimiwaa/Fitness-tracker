package com.reprise.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reprise.app.R
import com.reprise.app.domain.model.FitnessGoal
import com.reprise.app.domain.model.GymReminder
import com.reprise.app.domain.model.ReminderDay
import com.reprise.app.domain.model.ReminderSound
import com.reprise.app.domain.model.ReminderTime
import com.reprise.app.domain.model.ThemeMode
import com.reprise.app.domain.model.User
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.auth.AuthFormState
import com.reprise.app.ui.friends.FriendsScreen
import com.reprise.app.ui.home.HomeScreen
import com.reprise.app.ui.leaderboard.LeaderboardScreen
import com.reprise.app.ui.profile.ProfileScreen
import com.reprise.app.ui.progression.ProgressionScreen
import com.reprise.app.ui.theme.ReminderSoundImport
import com.reprise.app.ui.weights.WeightsScreen
import com.reprise.app.ui.workouts.WorkoutEditorScreen
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
    reminder: GymReminder,
    onSaveDisplayName: (String) -> Unit,
    onChangeFitnessGoal: (FitnessGoal) -> Unit,
    onChangeWeightUnit: (WeightUnit) -> Unit,
    onSaveHeight: (Int?) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (ReminderTime) -> Unit,
    onToggleReminderDay: (ReminderDay) -> Unit,
    soundImport: ReminderSoundImport,
    onReminderSoundChange: (ReminderSound) -> Unit,
    onImportReminderSound: (Uri) -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()

    // Observing the current destination lets us highlight the selected tab and
    // decide whether the bar should be visible at all.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Before the NavHost has composed, `currentRoute` is null — treat that as a
    // tab so the bar does not flash away on the first frame.
    val isTopLevel = currentRoute == null ||
        TopLevelDestination.entries.any { it.route == currentRoute }

    // Switching tabs: keep a single copy of each on the back stack, and restore
    // the scroll/state the user left behind.
    val openTab: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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
                            onClick = { if (!selected) openTab(destination) },
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
            modifier = Modifier
                .padding(innerPadding)
                // ...and record it as consumed, so a screen's own `imePadding()`
                // only adds the part of the keyboard the bar has not already
                // covered. Without this a tab screen would leave a bar-high gap
                // between its content and the keyboard.
                .consumeWindowInsets(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    user = user,
                    onOpenWeights = { navController.navigate(WEIGHT_ROUTE) },
                    onOpenWorkouts = { openTab(TopLevelDestination.WORKOUTS) },
                    onLogWorkout = { navController.navigate(workoutEditorRoute()) },
                )
            }
            composable(TopLevelDestination.WORKOUTS.route) {
                WorkoutsScreen(
                    onLogWorkout = { navController.navigate(workoutEditorRoute()) },
                    onOpenWorkout = { id -> navController.navigate(workoutEditorRoute(id)) },
                    onOpenProgress = { navController.navigate(PROGRESSION_ROUTE) },
                )
            }
            composable(TopLevelDestination.FRIENDS.route) {
                FriendsScreen(
                    onOpenLeaderboard = { navController.navigate(LEADERBOARD_ROUTE) },
                )
            }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(
                    user = user,
                    formState = formState,
                    themeMode = themeMode,
                    reminder = reminder,
                    onSaveDisplayName = onSaveDisplayName,
                    onChangeFitnessGoal = onChangeFitnessGoal,
                    onChangeWeightUnit = onChangeWeightUnit,
                    onSaveHeight = onSaveHeight,
                    onPhotoPicked = onPhotoPicked,
                    onRemovePhoto = onRemovePhoto,
                    onThemeModeChange = onThemeModeChange,
                    onReminderEnabledChange = onReminderEnabledChange,
                    onReminderTimeChange = onReminderTimeChange,
                    onToggleReminderDay = onToggleReminderDay,
                    soundImport = soundImport,
                    onReminderSoundChange = onReminderSoundChange,
                    onImportReminderSound = onImportReminderSound,
                    onLogout = onLogout,
                )
            }

            // Pushed detail screens, reachable from a tab.
            composable(WEIGHT_ROUTE) {
                WeightsScreen(onBack = { navController.popBackStack() })
            }
            composable(PROGRESSION_ROUTE) {
                ProgressionScreen(onBack = { navController.popBackStack() })
            }
            composable(LEADERBOARD_ROUTE) {
                LeaderboardScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "$WORKOUT_EDITOR_ROUTE/{workoutId}",
                arguments = listOf(
                    navArgument("workoutId") {
                        type = NavType.StringType
                        defaultValue = NEW_WORKOUT_ID
                    },
                ),
            ) { entry ->
                // "new" is the sentinel for "log a new workout"; anything else
                // is the id of an existing session to edit.
                val workoutId = entry.arguments
                    ?.getString("workoutId")
                    ?.takeIf { it != NEW_WORKOUT_ID }

                WorkoutEditorScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
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
