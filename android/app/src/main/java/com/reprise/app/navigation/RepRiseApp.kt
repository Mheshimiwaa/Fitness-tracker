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
import com.reprise.app.ui.friends.FriendsScreen
import com.reprise.app.ui.home.HomeScreen
import com.reprise.app.ui.profile.ProfileScreen
import com.reprise.app.ui.workouts.WorkoutsScreen

/**
 * The root composable: a single [Scaffold] holding the bottom navigation bar and
 * the [NavHost] that swaps the screen content.
 *
 * Navigation model (from the specification): one Activity, four top-level
 * destinations, no separate Activity per screen.
 */
@Composable
fun RepRiseApp() {
    // rememberNavController() survives recomposition; it owns the back stack.
    val navController = rememberNavController()

    // Observe the current destination so we can highlight the selected tab.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            // Apply the Scaffold's padding so content is not hidden behind the bar.
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) { HomeScreen() }
            composable(TopLevelDestination.WORKOUTS.route) { WorkoutsScreen() }
            composable(TopLevelDestination.FRIENDS.route) { FriendsScreen() }
            composable(TopLevelDestination.PROFILE.route) { ProfileScreen() }
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
