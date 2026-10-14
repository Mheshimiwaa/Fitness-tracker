package com.reprise.app.data.remote

import com.reprise.app.data.remote.dto.ApiErrorEnvelopeDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.Response

/**
 * Turns a Retrofit [Response] into an [ApiResult], so repository code never has
 * to deal with HTTP status codes or exception handling.
 *
 * Why go through Retrofit's `Response<T>` instead of letting Retrofit throw?
 * Because on a failure we want to read the SERVER's error message (our backend
 * always explains what went wrong) rather than showing a generic "HTTP 400".
 */

private const val NETWORK_ERROR_MESSAGE =
    "Can't reach the server. Check that the backend is running and that the API URL is correct."

private const val UNEXPECTED_ERROR_MESSAGE =
    "Something went wrong. Please try again."

/** Parses the JSON error bodies our API returns. */
private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Run [block] and map the result to an [ApiResult].
 *
 * @param block a Retrofit call such as `{ api.me() }`.
 */
suspend fun <T : Any> apiCall(block: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = block()
        val body = response.body()

        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            parseErrorResponse(response)
        }
    } catch (cancellation: CancellationException) {
        // A cancelled coroutine is NOT an error — rethrow so structured
        // concurrency keeps working.
        throw cancellation
    } catch (io: IOException) {
        // No network, DNS failure, connection refused, timeout...
        ApiResult.Failure(NETWORK_ERROR_MESSAGE)
    } catch (unexpected: Exception) {
        ApiResult.Failure(UNEXPECTED_ERROR_MESSAGE)
    }
}

/** Build an [ApiResult.Failure] from a non-2xx response. */
private fun parseErrorResponse(response: Response<*>): ApiResult.Failure {
    val statusCode = response.code()
    val rawBody = runCatching { response.errorBody()?.string() }.getOrNull()

    if (rawBody.isNullOrBlank()) {
        return ApiResult.Failure(fallbackMessage(statusCode), statusCode = statusCode)
    }

    val parsed = runCatching { errorJson.decodeFromString<ApiErrorEnvelopeDto>(rawBody) }.getOrNull()
    val message = parsed?.error?.message?.takeIf { it.isNotBlank() } ?: fallbackMessage(statusCode)

    return ApiResult.Failure(
        message = message,
        fieldErrors = parsed?.error?.details.toFieldErrors(message),
        statusCode = statusCode,
    )
}

/**
 * Extract per-field messages from the two shapes our API uses for `details`:
 *
 *   1. Zod validation — an ARRAY of `{ path, message }`:
 *      `[ { "path": "email", "message": "Enter a valid email address." } ]`
 *
 *   2. A targeted conflict — an OBJECT naming the offending field, with the
 *      reason in `message`:
 *      `{ "field": "username" }`
 *
 * Any other shape (including null) yields no field errors, and the caller just
 * shows the message as a form-level error.
 */
private fun JsonElement?.toFieldErrors(message: String): Map<String, String> = when (this) {
    is JsonArray -> mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val path = (obj["path"] as? JsonPrimitive)?.contentOrNull
        val detail = (obj["message"] as? JsonPrimitive)?.contentOrNull
        if (path.isNullOrBlank() || detail.isNullOrBlank()) null else path to detail
    }.toMap()

    is JsonObject -> {
        val field = (this["field"] as? JsonPrimitive)?.contentOrNull
        if (field.isNullOrBlank()) emptyMap() else mapOf(field to message)
    }

    else -> emptyMap()
}

/** Message for a failure that carried no readable body. */
private fun fallbackMessage(statusCode: Int): String = when (statusCode) {
    400 -> "The request was not valid. Please check the highlighted fields."
    401 -> "Your session has expired. Please sign in again."
    403 -> "You are not allowed to do that."
    404 -> "We could not find what you were looking for."
    409 -> "That value is already taken."
    429 -> "Too many attempts. Please wait a moment and try again."
    in 500..599 -> "The server had a problem. Please try again shortly."
    else -> UNEXPECTED_ERROR_MESSAGE
}
