package com.reprise.app.domain.model

/**
 * Whether anybody is signed in.
 *
 * Having an explicit [Unknown] state matters: on a cold start we must first
 * check whether a stored token is still valid, and we should not flash the login
 * screen at a user who is already signed in.
 */
sealed interface AuthState {

    /** Still checking the stored token — show a splash/loading screen. */
    data object Unknown : AuthState

    /** No valid session: the auth screens should be shown. */
    data object SignedOut : AuthState

    /** A valid session: [user] is the signed-in account. */
    data class SignedIn(val user: User) : AuthState
}
