package com.reprise.app.data.repository

import com.reprise.app.data.local.TokenStore
import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.apiCall
import com.reprise.app.data.remote.dto.AuthResponseDto
import com.reprise.app.data.remote.dto.LoginRequest
import com.reprise.app.data.remote.dto.RegisterRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import com.reprise.app.data.remote.dto.UserDto
import com.reprise.app.domain.model.AuthState
import com.reprise.app.domain.model.FitnessGoal
import com.reprise.app.domain.model.User
import com.reprise.app.domain.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response

/**
 * The single source of truth for "who is signed in".
 *
 * It owns three related responsibilities, which is why they live together:
 *   1. calling the auth endpoints,
 *   2. persisting/clearing the JWT, and
 *   3. publishing [authState] so the UI can react.
 *
 * ViewModels never touch Retrofit or SharedPreferences — they only call these
 * methods and observe the state.
 */
class AuthRepository(
    private val api: RepRiseApi,
    private val tokenStore: TokenStore,
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    /** The current session. Starts as [AuthState.Unknown] until the token is checked. */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** The signed-in user, or null when signed out. */
    val currentUser: User?
        get() = (_authState.value as? AuthState.SignedIn)?.user

    /**
     * Called once at startup.
     *
     * If a token is stored we ask the server to confirm it (`GET /api/auth/me`).
     * A rejected token is discarded immediately, so a revoked or expired session
     * can never leave the UI in a half-signed-in state.
     */
    suspend fun restoreSession() {
        if (tokenStore.getToken().isNullOrBlank()) {
            _authState.value = AuthState.SignedOut
            return
        }

        when (val result = apiCall { api.me() }) {
            is ApiResult.Success -> _authState.value = AuthState.SignedIn(result.data.user.toDomain())
            is ApiResult.Failure -> {
                tokenStore.clear()
                _authState.value = AuthState.SignedOut
            }
        }
    }

    /** Create an account and sign in. */
    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): ApiResult<User> = authenticate {
        api.register(RegisterRequest(displayName, username, email, password))
    }

    /** Sign in with a username or email. */
    suspend fun login(username: String, password: String): ApiResult<User> = authenticate {
        api.login(LoginRequest(username, password))
    }

    /**
     * Update the caller's own profile. Only the non-null arguments are sent, so
     * unspecified fields keep their current value.
     */
    suspend fun updateProfile(
        displayName: String? = null,
        fitnessGoal: FitnessGoal? = null,
        weightUnit: WeightUnit? = null,
        heightCm: Int? = null,
    ): ApiResult<User> {
        val request = UpdateProfileRequest(
            displayName = displayName,
            fitnessGoal = fitnessGoal?.apiValue,
            weightUnit = weightUnit?.apiValue,
            heightCm = heightCm,
        )

        return when (val result = apiCall { api.updateProfile(request) }) {
            is ApiResult.Success -> {
                val user = result.data.user.toDomain()
                _authState.value = AuthState.SignedIn(user)
                ApiResult.Success(user)
            }

            is ApiResult.Failure -> result.toFailure()
        }
    }

    /** Forget the session locally. The token is short-lived, so no server call is needed. */
    fun logout() {
        tokenStore.clear()
        _authState.value = AuthState.SignedOut
    }

    /**
     * Shared logic for register/login: on success the token is saved and the
     * session state flips to [AuthState.SignedIn], which is what makes the UI
     * navigate away from the auth screens.
     */
    private suspend fun authenticate(
        call: suspend () -> Response<AuthResponseDto>,
    ): ApiResult<User> {
        return when (val result = apiCall(call)) {
            is ApiResult.Success -> {
                tokenStore.saveToken(result.data.token)
                val user = result.data.user.toDomain()
                _authState.value = AuthState.SignedIn(user)
                ApiResult.Success(user)
            }

            is ApiResult.Failure -> result.toFailure()
        }
    }

    /** Widen `ApiResult.Failure` (an `ApiResult<Nothing>`) to `ApiResult<User>`. */
    private fun ApiResult.Failure.toFailure(): ApiResult<User> =
        ApiResult.Failure(message, fieldErrors, statusCode)
}

/** Map the wire DTO onto the domain model. */
private fun UserDto.toDomain(): User = User(
    id = id,
    displayName = displayName,
    username = username,
    email = email,
    profileImageUrl = profileImageUrl,
    fitnessGoal = FitnessGoal.fromApi(fitnessGoal),
    weightUnit = WeightUnit.fromApi(weightUnit),
    heightCm = heightCm,
)
