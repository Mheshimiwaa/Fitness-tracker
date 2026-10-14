package com.reprise.app.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [initialOf], the tiny rule behind the avatar circle.
 *
 * It is easy to get wrong at the edges (a blank name, surrounding spaces), and
 * getting it wrong would show every such user the same empty circle.
 */
class UserAvatarTest {

    @Test
    fun `uses the first letter of the display name, uppercased`() {
        assertEquals("A", initialOf("alice", "alice_lifts"))
        assertEquals("A", initialOf("Alice Nguyen", "alice_lifts"))
    }

    @Test
    fun `ignores surrounding whitespace`() {
        assertEquals("B", initialOf("   bob carter", "bob_runs"))
    }

    @Test
    fun `falls back to the username when the display name is blank`() {
        assertEquals("B", initialOf("", "bob_runs"))
        assertEquals("R", initialOf("   ", "runs"))
    }

    @Test
    fun `never crashes on empty input`() {
        // Both blank: there is genuinely nothing to show, so an empty string is
        // the honest answer rather than an exception.
        assertEquals("", initialOf("", ""))
    }
}
