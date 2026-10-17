package com.reprise.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for how a [ReminderSound] choice is stored and read back.
 *
 * This is worth testing exactly because it is easy to get subtly wrong: one
 * string has to express three different choices, and mistaking "no value" for
 * "silence" would leave the user with a reminder that never makes a sound — a
 * bug that would only show up on the day of the reminder itself.
 */
class ReminderSoundTest {

    @Test
    fun `the phone's default sound is stored as nothing at all`() {
        assertEquals(null, ReminderSound.SystemDefault.storedUri())
        assertEquals(null, ReminderSound.SystemDefault.storedName())
    }

    @Test
    fun `silence is stored as a keyword, not as an empty value`() {
        // An empty value would be indistinguishable from a preference that was
        // never written, which is the default sound.
        assertEquals("silent", ReminderSound.Silent.storedUri())
        assertEquals(null, ReminderSound.Silent.storedName())
    }

    @Test
    fun `an imported sound keeps both its uri and its name`() {
        val clip = ReminderSound.Clip(uri = "content://media/audio/7", name = "bell.mp3")

        assertEquals("content://media/audio/7", clip.storedUri())
        assertEquals("bell.mp3", clip.storedName())
    }

    @Test
    fun `a nameless imported sound stores no name`() {
        // A blank name is the same as not having one: the card falls back to a
        // generic label either way.
        assertEquals(null, ReminderSound.Clip(uri = "content://media/audio/7", name = "").storedName())
        assertEquals(
            null,
            ReminderSound.Clip(uri = "content://media/audio/7", name = "   ").storedName(),
        )
    }

    @Test
    fun `everything written comes back unchanged`() {
        val sounds = listOf(
            ReminderSound.SystemDefault,
            ReminderSound.Silent,
            ReminderSound.Clip(uri = "content://media/audio/7", name = "bell.mp3"),
        )

        for (sound in sounds) {
            val readBack = parseStoredReminderSound(sound.storedUri(), sound.storedName())

            assertEquals("expected $sound to survive a round trip", sound, readBack)
        }
    }

    @Test
    fun `an absent preference means the phone's default sound`() {
        assertEquals(ReminderSound.SystemDefault, parseStoredReminderSound(null, null))
        assertEquals(ReminderSound.SystemDefault, parseStoredReminderSound("", null))
        assertEquals(ReminderSound.SystemDefault, parseStoredReminderSound("   ", null))
    }

    @Test
    fun `the silence keyword is recognised`() {
        assertEquals(ReminderSound.Silent, parseStoredReminderSound("silent", null))
        // A name left over from a previous choice must not turn silence into a clip.
        assertEquals(ReminderSound.Silent, parseStoredReminderSound("silent", "bell.mp3"))
    }

    @Test
    fun `any other stored value is an imported sound`() {
        assertEquals(
            ReminderSound.Clip(uri = "content://media/audio/7", name = "bell.mp3"),
            parseStoredReminderSound("content://media/audio/7", "bell.mp3"),
        )

        // Storage written by a version that did not keep the name still works.
        assertEquals(
            ReminderSound.Clip(uri = "content://media/audio/7", name = ""),
            parseStoredReminderSound("content://media/audio/7", null),
        )
    }
}
