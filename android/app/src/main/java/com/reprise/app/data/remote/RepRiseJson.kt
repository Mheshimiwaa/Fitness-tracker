package com.reprise.app.data.remote

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration used for every request and response.
 *
 * It lives outside [ApiClient] so unit tests can assert how request bodies are
 * encoded without touching the Retrofit client (which needs a Context).
 *
 * IMPORTANT — the missing line is the interesting one. `encodeDefaults` is left
 * at kotlinx.serialization's default of `false`, so a property that still holds
 * its declared default is NOT written to the JSON. That is how the profile
 * request expresses "leave this field alone": its properties default to `null`
 * and are simply absent from the body. The consequence is that a `null` there
 * can never mean "clear this field" — a field that must be clearable has to be
 * declared with no default at all (see `UpdateHeightRequest`).
 */
internal val repRiseJson = Json {
    // The server may send fields we do not model (createdAt, updatedAt, ...).
    // Ignoring them means a server addition never breaks the app.
    ignoreUnknownKeys = true
    // If a value cannot be decoded, fall back to the property default instead
    // of throwing.
    coerceInputValues = true
}
