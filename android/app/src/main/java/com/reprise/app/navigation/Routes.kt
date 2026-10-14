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

/**
 * The workout editor.
 *
 * It carries the session id as a path argument so ONE route covers both "log a
 * new workout" and "edit an existing one" — [NEW_WORKOUT_ID] means create.
 */
const val WORKOUT_EDITOR_ROUTE = "workout_editor"

/** The route argument used when the user is logging a NEW workout. */
const val NEW_WORKOUT_ID = "new"

/**
 * Build the editor's route.
 *
 * @param workoutId an existing session's id, or null to log a new one
 */
fun workoutEditorRoute(workoutId: String? = null): String =
    "$WORKOUT_EDITOR_ROUTE/${workoutId ?: NEW_WORKOUT_ID}"
