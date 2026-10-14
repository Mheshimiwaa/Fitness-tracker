package com.reprise.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the small mapping rules between the server's strings and our
 * domain types.
 *
 * The `apiValue` strings MUST match the backend exactly (see
 * `backend/src/config/constants.js`). If somebody renames one on either side,
 * these tests fail loudly instead of silently downgrading every user to the
 * default goal.
 */
class DomainMappingTest {

    @Test
    fun `api values match the server contract`() {
        assertEquals(
            setOf("lose_weight", "maintain", "gain_muscle", "improve_strength"),
            FitnessGoal.entries.map { it.apiValue }.toSet(),
        )
        assertEquals(
            setOf("kg", "lb"),
            WeightUnit.entries.map { it.apiValue }.toSet(),
        )
    }

    @Test
    fun `fitness goal maps every known api value`() {
        assertEquals(FitnessGoal.LOSE_WEIGHT, FitnessGoal.fromApi("lose_weight"))
        assertEquals(FitnessGoal.MAINTAIN, FitnessGoal.fromApi("maintain"))
        assertEquals(FitnessGoal.GAIN_MUSCLE, FitnessGoal.fromApi("gain_muscle"))
        assertEquals(FitnessGoal.IMPROVE_STRENGTH, FitnessGoal.fromApi("improve_strength"))
    }

    @Test
    fun `fitness goal falls back to maintain for unknown or missing values`() {
        assertEquals(FitnessGoal.MAINTAIN, FitnessGoal.fromApi("bulking_to_the_moon"))
        assertEquals(FitnessGoal.MAINTAIN, FitnessGoal.fromApi(null))
    }

    @Test
    fun `weight unit maps known values and defaults to kilograms`() {
        assertEquals(WeightUnit.KG, WeightUnit.fromApi("kg"))
        assertEquals(WeightUnit.LB, WeightUnit.fromApi("lb"))
        assertEquals(WeightUnit.KG, WeightUnit.fromApi("stone"))
        assertEquals(WeightUnit.KG, WeightUnit.fromApi(null))
    }

    @Test
    fun `theme mode resolves against the system setting`() {
        // SYSTEM follows the device...
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false))

        // ...while the explicit choices ignore it.
        assertTrue(ThemeMode.DARK.isDark(systemInDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemInDarkTheme = true))
    }
}
