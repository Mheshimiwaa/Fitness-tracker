package com.reprise.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [streakStatus], the rule that decides what the streak card says.
 *
 * The distinction that matters — and the reason this is a tested function rather
 * than a `when` buried in the UI — is between a run that is still alive and one
 * that is over. Getting it wrong in either direction is either a lie ("your
 * streak is broken") or a dark pattern ("keep it alive" after it is gone).
 */
class StreakStatusTest {

    @Test
    fun `never having trained is not a broken streak`() {
        val status = streakStatus(
            StreakStats(current = 0, longest = 0, trainedToday = false, activeDays = 0)
        )

        assertEquals(StreakStatus.NOT_STARTED, status)
    }

    @Test
    fun `a run is at risk while today has not been trained yet`() {
        val status = streakStatus(
            StreakStats(current = 4, longest = 4, trainedToday = false, activeDays = 9)
        )

        assertEquals(StreakStatus.AT_RISK, status)
    }

    @Test
    fun `training today makes the run active`() {
        val status = streakStatus(
            StreakStats(current = 5, longest = 12, trainedToday = true, activeDays = 34)
        )

        assertEquals(StreakStatus.ACTIVE, status)
    }

    @Test
    fun `a current streak of zero after training before means the run ended`() {
        val status = streakStatus(
            StreakStats(current = 0, longest = 6, trainedToday = false, activeDays = 20)
        )

        assertEquals(StreakStatus.ENDED, status)
    }

    @Test
    fun `training today can never be at risk`() {
        // The server is the authority on whether today counts; whatever else is
        // true, a day already trained is not a day to be nagged about.
        val status = streakStatus(
            StreakStats(current = 1, longest = 0, trainedToday = true, activeDays = 1)
        )

        assertEquals(StreakStatus.ACTIVE, status)
    }

    @Test
    fun `a streak of one is still a streak`() {
        val active = streakStatus(
            StreakStats(current = 1, longest = 1, trainedToday = true, activeDays = 1)
        )
        val atRisk = streakStatus(
            StreakStats(current = 1, longest = 1, trainedToday = false, activeDays = 1)
        )

        assertEquals(StreakStatus.ACTIVE, active)
        assertEquals(StreakStatus.AT_RISK, atRisk)
    }
}
