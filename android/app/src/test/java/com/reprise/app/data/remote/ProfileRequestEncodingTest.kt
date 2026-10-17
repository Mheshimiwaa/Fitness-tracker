package com.reprise.app.data.remote

import com.reprise.app.data.remote.dto.UpdateHeightRequest
import com.reprise.app.data.remote.dto.UpdateProfileRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins down exactly what the two profile request bodies look like on the wire.
 *
 * This is worth a test because the behaviour is invisible until it fails in the
 * field. [repRiseJson] leaves `encodeDefaults` at kotlinx's default of `false`,
 * so a property still holding its declared default is never written — which is
 * how "omit a field" is expressed, and also why a `null` in
 * [UpdateProfileRequest] can only ever mean "leave it alone". Clearing a height
 * therefore needs [UpdateHeightRequest], whose `heightCm` has no default.
 *
 * The assertions read the encoded JSON rather than comparing strings, so they do
 * not depend on property order.
 */
class ProfileRequestEncodingTest {

    private inline fun <reified T> bodyOf(value: T): JsonObject =
        repRiseJson.parseToJsonElement(repRiseJson.encodeToString(value)).jsonObject

    @Test
    fun `an unset field is absent from the partial profile request`() {
        val body = bodyOf(UpdateProfileRequest(displayName = "Alex"))

        assertEquals(setOf("displayName"), body.keys)
        assertEquals("Alex", body["displayName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `goal and unit travel under the names the server validates`() {
        val body = bodyOf(UpdateProfileRequest(fitnessGoal = "gain_muscle", weightUnit = "lb"))

        assertEquals(setOf("fitnessGoal", "weightUnit"), body.keys)
    }

    @Test
    fun `a null height in the partial request is dropped, not sent as null`() {
        // The trap the height editor has to route around: `null` here is
        // indistinguishable from "not mentioned", so it cannot clear anything.
        val body = bodyOf(UpdateProfileRequest(displayName = "Alex", heightCm = null))

        assertFalse(body.containsKey("heightCm"))
    }

    @Test
    fun `a height in either request is sent as a number`() {
        assertEquals("180", bodyOf(UpdateProfileRequest(heightCm = 180))["heightCm"]!!.jsonPrimitive.content)
        assertEquals("180", bodyOf(UpdateHeightRequest(heightCm = 180))["heightCm"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a null height in the dedicated request IS sent as an explicit null`() {
        val body = bodyOf(UpdateHeightRequest(heightCm = null))

        assertEquals(setOf("heightCm"), body.keys)
        assertEquals(JsonNull, body["heightCm"])
    }
}
