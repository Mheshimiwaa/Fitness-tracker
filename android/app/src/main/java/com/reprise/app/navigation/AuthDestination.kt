package com.reprise.app.navigation

/**
 * The routes in the signed-out navigation graph.
 */
enum class AuthDestination(val route: String) {
    LOGIN("login"),
    REGISTER("register"),
}
