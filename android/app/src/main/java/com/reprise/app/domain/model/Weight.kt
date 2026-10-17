package com.reprise.app.domain.model

import androidx.annotation.StringRes
import com.reprise.app.R

/**
 * Domain models for body-weight tracking and the dashboard.
 *
 * These are pure Kotlin — no JSON annotations, no HTTP. The DTOs in
 * `data/remote/dto` are mapped onto them by `WeightRepository`, so the UI never
 * depends on the shape of the wire format.
 */

/** One body-weight reading, already expressed in the user's preferred unit. */
data class WeightEntry(
    val id: String,
    /** The value to DISPLAY, in [unit]. */
    val weight: Double,
    val unit: WeightUnit,
    /** The same reading in kilograms — the canonical value used for BMI. */
    val weightKg: Double,
    /** ISO-8601 instant, e.g. "2026-10-14T08:33:57.367Z". */
    val measuredAt: String,
    val note: String?,
)

/** The change between the oldest and newest reading in a window. */
data class WeightChange(
    /** Signed difference in [unit] (negative = lost weight). */
    val amount: Double,
    val unit: WeightUnit,
)

/** GET /api/weights: the history plus the change across the returned window. */
data class WeightHistory(
    val entries: List<WeightEntry>,
    val change: WeightChange?,
)

/**
 * The Home tab's summary.
 *
 * Sections that cannot be filled yet are nullable rather than faked, so the UI
 * can show an honest empty state.
 */
data class Dashboard(
    val latestWeight: WeightEntry?,
    val previousWeight: WeightEntry?,
    val weightEntryCount: Int,
    val changeWindowDays: Int,
    val weightChange: WeightChange?,
    val workouts: WorkoutStats,
    val streak: StreakStats,
    val bmi: Bmi?,
    val bmiUnavailableReason: BmiUnavailableReason?,
)

/** A calculated BMI value and its WHO category. */
data class Bmi(
    val value: Double,
    val category: BmiCategory,
)

/** Why the dashboard could not calculate a BMI. Drives the empty-state copy. */
enum class BmiUnavailableReason {
    NO_HEIGHT,
    NO_WEIGHT,
    UNKNOWN;

    companion object {
        fun fromApi(value: String?): BmiUnavailableReason? = when (value) {
            null -> null
            "no_height" -> NO_HEIGHT
            "no_weight" -> NO_WEIGHT
            else -> UNKNOWN
        }
    }
}

/**
 * The adult BMI bands, as published by the World Health Organization.
 *
 * `apiValue` must match the server's codes exactly. BMI is a screening measure
 * only — it does not know muscle from fat — so each band carries a neutral,
 * non-medical description rather than advice.
 */
enum class BmiCategory(
    val apiValue: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    UNDERWEIGHT("underweight", R.string.bmi_underweight, R.string.bmi_underweight_desc),
    NORMAL("normal", R.string.bmi_normal, R.string.bmi_normal_desc),
    OVERWEIGHT("overweight", R.string.bmi_overweight, R.string.bmi_overweight_desc),
    OBESE("obese", R.string.bmi_obese, R.string.bmi_obese_desc);

    companion object {
        /** Map a server category code, or null when it is not recognised. */
        fun fromApi(value: String?): BmiCategory? =
            entries.firstOrNull { it.apiValue == value }
    }
}
