package com.reprise.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `bmi categories match the server contract`() {
        // These strings are produced by backend/src/services/bmi.service.js and
        // must stay in step with it.
        assertEquals(
            setOf("underweight", "normal", "overweight", "obese"),
            BmiCategory.entries.map { it.apiValue }.toSet(),
        )
    }

    @Test
    fun `bmi category maps every known api value`() {
        assertEquals(BmiCategory.UNDERWEIGHT, BmiCategory.fromApi("underweight"))
        assertEquals(BmiCategory.NORMAL, BmiCategory.fromApi("normal"))
        assertEquals(BmiCategory.OVERWEIGHT, BmiCategory.fromApi("overweight"))
        assertEquals(BmiCategory.OBESE, BmiCategory.fromApi("obese"))
    }

    @Test
    fun `bmi category rejects unknown and missing values`() {
        // Unlike a goal, a wrong BMI category would be actively misleading, so
        // there is deliberately no fallback: null means "do not show a BMI".
        assertNull(BmiCategory.fromApi("greek_god"))
        assertNull(BmiCategory.fromApi(null))
    }

    @Test
    fun `bmi unavailable reason maps known values`() {
        assertEquals(BmiUnavailableReason.NO_HEIGHT, BmiUnavailableReason.fromApi("no_height"))
        assertEquals(BmiUnavailableReason.NO_WEIGHT, BmiUnavailableReason.fromApi("no_weight"))
        assertEquals(BmiUnavailableReason.UNKNOWN, BmiUnavailableReason.fromApi("who_knows"))
        assertNull(BmiUnavailableReason.fromApi(null))
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

    @Test
    fun `progression verdicts match the server contract`() {
        // Produced by backend/src/services/progression.service.js. Renaming a
        // verdict there must break this test, not silently downgrade the UI.
        assertEquals(
            setOf("progressing", "holding", "regressing", "insufficient_data"),
            ProgressionVerdict.entries.map { it.apiValue }.toSet(),
        )
    }

    @Test
    fun `progression verdict maps every known api value`() {
        assertEquals(ProgressionVerdict.PROGRESSING, ProgressionVerdict.fromApi("progressing"))
        assertEquals(ProgressionVerdict.HOLDING, ProgressionVerdict.fromApi("holding"))
        assertEquals(ProgressionVerdict.REGRESSING, ProgressionVerdict.fromApi("regressing"))
        assertEquals(ProgressionVerdict.INSUFFICIENT_DATA, ProgressionVerdict.fromApi("insufficient_data"))
    }

    @Test
    fun `progression verdict falls back to insufficient data when unrecognised`() {
        // A newer server must not crash an older app: "we cannot tell yet" is the
        // safe thing to show when the verdict is something we do not know.
        assertEquals(ProgressionVerdict.INSUFFICIENT_DATA, ProgressionVerdict.fromApi("superhuman"))
        assertEquals(ProgressionVerdict.INSUFFICIENT_DATA, ProgressionVerdict.fromApi(null))
    }
}
