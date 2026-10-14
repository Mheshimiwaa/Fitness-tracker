package com.reprise.app.navigation

import androidx.annotation.StringRes
import com.reprise.app.R

/**
 * The four top-level destinations shown in the bottom navigation bar.
 *
 * Using an `enum` means the set of destinations is fixed and the compiler
 * forces us to handle every case (for example when choosing an icon). Each
 * entry pairs a stable `route` string with the string resources for its label
 * and screen title.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
) {
    HOME("home", R.string.nav_home, R.string.title_home),
    WORKOUTS("workouts", R.string.nav_workouts, R.string.title_workouts),
    FRIENDS("friends", R.string.nav_friends, R.string.title_friends),
    PROFILE("profile", R.string.nav_profile, R.string.title_profile),
}
