package com.reprise.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.repository.AuthRepository
import com.reprise.app.di.AppModule
import com.reprise.app.domain.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything a form needs to render: is a request in flight, what went wrong,
 * and which fields are at fault.
 *
 * Keeping this in ONE object means screens stay stateless and easy to reason
 * about — they just draw the current state.
 */
data class AuthFormState(
    val isSubmitting: Boolean = false,
    /** A failure that applies to the whole form (e.g. "invalid credentials"). */
    val errorMessage: String? = null,
    /** Per-field messages, keyed by the server's field name (e.g. "email"). */
    val fieldErrors: Map<String, String> = emptyMap(),
    /** True right after a successful profile save, so the UI can confirm it. */
    val profileSaved: Boolean = false,
)

/**
 * The session ViewModel: sign in, register, update the profile, sign out.
 *
 * [authState] is not owned here — it comes straight from the repository, so
 * there is exactly one source of truth about who is signed in.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /** Who is signed in. Drives the navigation between the auth and main graphs. */
    val authState: StateFlow<AuthState> = authRepository.authState

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    init {
        // On a cold start, check whether the stored token is still valid.
        viewModelScope.launch { authRepository.restoreSession() }
    }

    fun login(username: String, password: String) {
        submit { authRepository.login(username.trim(), password) }
    }

    fun register(displayName: String, username: String, email: String, password: String) {
        submit {
            authRepository.register(
                displayName = displayName.trim(),
                username = username.trim(),
                email = email.trim(),
                password = password,
            )
        }
    }

    /** Rename the signed-in user. Only the display name is editable in this phase. */
    fun updateDisplayName(displayName: String) {
        submit(profileSaved = true) {
            authRepository.updateProfile(displayName = displayName.trim())
        }
    }

    fun logout() {
        _formState.value = AuthFormState()
        authRepository.logout()
    }

    /**
     * Clear stale feedback as soon as the user edits a field, so an old error
     * does not linger next to freshly typed input.
     */
    fun clearMessages() {
        if (_formState.value != AuthFormState()) {
            _formState.value = AuthFormState()
        }
    }

    /**
     * Run a suspending call and fold the outcome into [formState].
     *
     * The `isSubmitting` guard makes a double-tap on "Sign in" harmless: the
     * second call returns immediately instead of firing a second request.
     */
    private fun submit(profileSaved: Boolean = false, block: suspend () -> ApiResult<*>) {
        if (_formState.value.isSubmitting) return

        _formState.value = AuthFormState(isSubmitting = true)

        viewModelScope.launch {
            _formState.value = when (val result = block()) {
                is ApiResult.Success -> AuthFormState(profileSaved = profileSaved)
                is ApiResult.Failure -> AuthFormState(
                    isSubmitting = false,
                    errorMessage = result.message,
                    fieldErrors = result.fieldErrors,
                )
            }
        }
    }

    companion object {
        /**
         * Tells Compose how to build this ViewModel. The repository comes from the
         * app-wide [AppModule] container.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(AppModule.authRepository) }
        }
    }
}
