package com.reprise.app.data.remote

/**
 * The outcome of a network call, as a value rather than an exception.
 *
 * Making failure part of the type means the compiler forces callers to consider
 * it — we cannot accidentally forget to handle "the request failed".
 *
 * `out T` (covariant) lets `ApiResult<Nothing>` (a failure) be used wherever an
 * `ApiResult<Whatever>` is expected.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>

    /**
     * @param message   a human-readable explanation, taken from the server when
     *                  it sent one, otherwise a friendly fallback.
     * @param fieldErrors maps a request field name (e.g. "username") to the
     *                  problem with it, so a form can highlight the right box.
     * @param statusCode the HTTP status, or null when the request never reached
     *                  the server (no network).
     */
    data class Failure(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val statusCode: Int? = null,
    ) : ApiResult<Nothing>
}
