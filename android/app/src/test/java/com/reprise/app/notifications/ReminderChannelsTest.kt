package com.reprise.app.notifications

import com.reprise.app.domain.model.ReminderSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the mapping from a chosen sound to the notification channel
 * that will carry it.
 *
 * The mapping has to be right in a way that is invisible until the day of the
 * reminder: Android fixes a channel's sound when the channel is created, so if
 * two different sounds were given the same channel one of them would silently
 * play the other's tone.
 */
class ReminderChannelsTest {

    @Test
    fun `the default sound keeps the channel that already existed`() {
        // Unchanged on purpose: the user has already seen this channel in
        // Android's settings and may have allowed or tuned it.
        assertEquals("gym_reminder", reminderChannelId(ReminderSound.SystemDefault))
    }

    @Test
    fun `silence has a channel of its own`() {
        assertEquals("gym_reminder_silent", reminderChannelId(ReminderSound.Silent))
    }

    @Test
    fun `each imported sound gets its own channel`() {
        val first = reminderChannelId(ReminderSound.Clip(uri = "content://media/audio/1", name = "a"))
        val second = reminderChannelId(ReminderSound.Clip(uri = "content://media/audio/2", name = "b"))

        assertNotEquals(first, second)

        // Two choices of the SAME file are still the same sound and may share a
        // channel; only the URI is part of the identity.
        assertEquals(
            first,
            reminderChannelId(ReminderSound.Clip(uri = "content://media/audio/1", name = "renamed")),
        )
    }

    @Test
    fun `a channel id is short, printable and stable`() {
        val id = reminderChannelId(ReminderSound.Clip(uri = "content://media/audio/1", name = "a"))

        assertTrue("expected a rediscoverable id, got '$id'", id.startsWith("gym_reminder_"))
        assertTrue("expected only hex after the prefix, got '$id'", id.removePrefix("gym_reminder_").all { it in "0123456789abcdef" })
        assertEquals("the same URI must always give the same channel", id, reminderChannelId(ReminderSound.Clip(uri = "content://media/audio/1", name = "a")))
    }

    @Test
    fun `imported channels are recognised as this app's reminder channels`() {
        assertTrue(isReminderChannelId("gym_reminder"))
        assertTrue(isReminderChannelId("gym_reminder_silent"))
        assertTrue(isReminderChannelId(reminderChannelId(ReminderSound.Clip("content://x", "y"))))

        // Somebody else's channel, or a future unrelated one, must be left alone:
        // the notification code deletes every channel it recognises.
        assertFalse(isReminderChannelId("gym_reminderx"))
        assertFalse(isReminderChannelId("workout_reminder"))
        assertFalse(isReminderChannelId(""))
    }

    @Test
    fun `the three choices are named differently in Android's settings`() {
        val names = listOf(
            ReminderSound.SystemDefault,
            ReminderSound.Silent,
            ReminderSound.Clip(uri = "content://x", name = "y"),
        ).map { reminderChannelNameRes(it) }

        assertEquals("each kind of sound needs its own name in the settings list", names.size, names.toSet().size)
    }
}
