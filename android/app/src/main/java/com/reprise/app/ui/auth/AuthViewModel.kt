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
import com.reprise.app.domain.model.FitnessGoal
import com.reprise.app.domain.model.WeightUnit
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
    /**
     * The profile field the last submit concerned, or null for login/register.
     *
     * The profile screen has several cards that each save on their own, so the
     * state has to remember WHICH one acted. Without this, every card would show
     * the same "saved" confirmation at once.
     */
    val field: String? = null,
    /** Whether that submit succeeded. Only meaningful together with [field]. */
    val saved: Boolean = false,
)

/**
 * The names of the writable `PATCH /api/users/me` fields.
 *
 * These are exactly the strings the server reports validation errors under, so
 * one set of constants covers both the `fieldErrors` lookup and the "which card
 * just saved" check.
 */
object ProfileField {
    const val DISPLAY_NAME = "displayName"
    const val FITNESS_GOAL = "fitnessGoal"
    const val WEIGHT_UNIT = "weightUnit"
    const val HEIGHT_CM = "heightCm"
}

/**
 * What the card that owns [field] should show: the server's complaint about that
 * field, or a generic failure from the last attempt on it.
 *
 * A failure naming other fields is deliberately ignored, so an error never shows
 * up next to a field the user did not touch.
 */
fun AuthFormState.errorFor(field: String): String? {
    fieldErrors[field]?.let { return it }
    return if (fieldErrors.isEmpty() && this.field == field) errorMessage else null
}

/** True when the most recent successful save wrote [field]. */
fun AuthFormState.didSave(field: String): Boolean = saved && this.field == field

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

    /** Rename the signed-in user. */
    fun updateDisplayName(displayName: String) {
        submit(field = ProfileField.DISPLAY_NAME) {
            authRepository.updateProfile(displayName = displayName.trim())
        }
    }

    /** Change the training goal shown on the profile and used by the dashboard. */
    fun updateFitnessGoal(goal: FitnessGoal) {
        submit(field = ProfileField.FITNESS_GOAL) {
            authRepository.updateProfile(fitnessGoal = goal)
        }
    }

    /** Switch the unit every weight in the app is displayed in. */
    fun updateWeightUnit(unit: WeightUnit) {
        submit(field = ProfileField.WEIGHT_UNIT) {
            authRepository.updateProfile(weightUnit = unit)
        }
    }

    /**
     * Save the body height, or pass `null` to remove it.
     *
     * Height feeds the dashboard's BMI, so clearing a wrong entry has to be
     * possible — see [AuthRepository.updateHeight] for why that needs its own
     * request body.
     */
    fun updateHeight(heightCm: Int?) {
        submit(field = ProfileField.HEIGHT_CM) {
            authRepository.updateHeight(heightCm)
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
    private fun submit(field: String? = null, block: suspend () -> ApiResult<*>) {
        if (_formState.value.isSubmitting) return

        _formState.value = AuthFormState(isSubmitting = true, field = field)

        viewModelScope.launch {
            _formState.value = when (val result = block()) {
                is ApiResult.Success -> AuthFormState(field = field, saved = true)
                is ApiResult.Failure -> AuthFormState(
                    isSubmitting = false,
                    errorMessage = result.message,
                    fieldErrors = result.fieldErrors,
                    field = field,
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
