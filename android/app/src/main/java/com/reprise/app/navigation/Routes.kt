package com.reprise.app.navigation

/**
 * Routes for screens that are PUSHED on top of a bottom-navigation tab, rather
 * than being tabs of their own.
 *
 * The four tabs live in [TopLevelDestination]; keeping these separate makes it
 * obvious which destinations appear in the bar (and therefore which ones need a
 * back arrow instead).
 */
const val WEIGHT_ROUTE = "weight"
