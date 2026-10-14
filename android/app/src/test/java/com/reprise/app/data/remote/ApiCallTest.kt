package com.reprise.app.data.remote

import com.reprise.app.data.remote.dto.UserDto
import com.reprise.app.data.remote.dto.UserEnvelopeDto
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for [apiCall] — the single place that turns an HTTP response into
 * an [ApiResult].
 *
 * These run on the JVM (no emulator, no network): the responses are built in
 * code, so the error-parsing rules are pinned down exactly. That matters because
 * this is the code that decides what the user actually reads when something goes
 * wrong.
 */
class ApiCallTest {

    private val json = "application/json".toMediaType()

    private fun errorResponse(code: Int, body: String): Response<Any> =
        Response.error(code, body.toResponseBody(json))

    @Test
    fun `a successful response becomes Success with the decoded body`() = runBlocking {
        val expected = UserEnvelopeDto(
            UserDto(id = "1", displayName = "Alex", username = "alex"),
        )

        val result = apiCall { Response.success(expected) }

        assertEquals(ApiResult.Success(expected), result)
    }

    @Test
    fun `a 400 with a validation array becomes per-field errors`() = runBlocking {
        val body = """
            {"error":{"message":"Validation failed.","details":[
              {"path":"password","message":"Password must be at least 8 characters."},
              {"path":"email","message":"Enter a valid email address."}
            ]}}
        """.trimIndent()

        val result = apiCall { errorResponse(400, body) }

        assertTrue(result is ApiResult.Failure)
        val failure = result as ApiResult.Failure
        assertEquals("Validation failed.", failure.message)
        assertEquals("Password must be at least 8 characters.", failure.fieldErrors["password"])
        assertEquals("Enter a valid email address.", failure.fieldErrors["email"])
        assertEquals(400, failure.statusCode)
    }

    @Test
    fun `a 409 conflict object highlights the named field`() = runBlocking {
        // The server sends { field: "username" } for a targeted conflict, with
        // the explanation in `message`.
        val body = """{"error":{"message":"That username is already taken.","details":{"field":"username"}}}"""

        val result = apiCall { errorResponse(409, body) }

        val failure = result as ApiResult.Failure
        assertEquals("That username is already taken.", failure.message)
        assertEquals("That username is already taken.", failure.fieldErrors["username"])
    }

    @Test
    fun `a 401 with no details is a form-level failure`() = runBlocking {
        val body = """{"error":{"message":"Invalid username or password."}}"""

        val result = apiCall { errorResponse(401, body) }

        val failure = result as ApiResult.Failure
        assertEquals("Invalid username or password.", failure.message)
        assertTrue(failure.fieldErrors.isEmpty())
    }

    @Test
    fun `an unreadable error body falls back to a status-based message`() = runBlocking {
        val result = apiCall { errorResponse(500, "") }

        val failure = result as ApiResult.Failure
        assertTrue(failure.message.contains("server had a problem", ignoreCase = true))
        assertEquals(500, failure.statusCode)
    }

    @Test
    fun `an unreachable server becomes a network failure with no status code`() = runBlocking {
        val result = apiCall<Any> { throw IOException("connection refused") }

        val failure = result as ApiResult.Failure
        assertTrue(failure.message.contains("Can't reach the server"))
        // statusCode == null is how the UI tells "never arrived" from "rejected".
        assertNull(failure.statusCode)
    }

    // --- apiCallUnit: endpoints that answer 204 No Content -------------------

    /**
     * A real 204, built by hand. Retrofit short-circuits 204/205 to a SUCCESS
     * with a null body, so this is exactly what a `DELETE /api/weights/:id`
     * produces — and the case [apiCall] alone would misread as a failure.
     */
    private fun noContentResponse(): Response<Unit> {
        val request = okhttp3.Request.Builder()
            .url("http://localhost/api/weights/000000000000000000000000")
            .build()
        val raw = okhttp3.Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .build()

        return retrofit2.Response.success(null, raw)
    }

    @Test
    fun `apiCallUnit treats a 204 with no body as success`() = runBlocking {
        val result = apiCallUnit { noContentResponse() }

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `apiCallUnit still reads the server message on failure`() = runBlocking {
        val body = """{"error":{"message":"Weight entry not found."}}"""

        val result = apiCallUnit { errorResponse(404, body) }

        val failure = result as ApiResult.Failure
        assertEquals("Weight entry not found.", failure.message)
        assertEquals(404, failure.statusCode)
    }

    @Test
    fun `apiCallUnit turns an IOException into a network failure`() = runBlocking {
        val result = apiCallUnit { throw IOException("connection refused") }

        val failure = result as ApiResult.Failure
        assertTrue(failure.message.contains("Can't reach the server"))
        assertNull(failure.statusCode)
    }
}
