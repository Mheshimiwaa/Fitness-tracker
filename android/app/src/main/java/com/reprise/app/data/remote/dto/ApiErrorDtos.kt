package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The error envelope every RepRise endpoint uses:
 *
 *   { "error": { "message": "...", "details": ... } }
 *
 * `details` is deliberately a raw [JsonElement] because the server sends two
 * different shapes:
 *   - validation failures -> an ARRAY of { path, message } (one per bad field)
 *   - other errors        -> a small OBJECT (e.g. { "field": "username" })
 * Decoding it loosely here and interpreting it in `ApiCall.kt` avoids a
 * deserialisation crash on the object case.
 */
@Serializable
data class ApiErrorEnvelopeDto(
    val error: ApiErrorBodyDto,
)

@Serializable
data class ApiErrorBodyDto(
    val message: String,
    val details: JsonElement? = null,
)
