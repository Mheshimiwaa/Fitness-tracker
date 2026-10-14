package com.reprise.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the weight-tracking and dashboard endpoints.
 *
 * Note how `weight` always arrives already converted into the user's preferred
 * unit, with `weightKg` alongside it as the canonical value. That means no
 * screen ever has to do unit maths — it just prints `weight` + `unit`.
 */

/** One body-weight reading. */
@Serializable
data class WeightEntryDto(
    val id: String,
    val weight: Double,
    val unit: String,
    val weightKg: Double,
    val measuredAt: String,
    val note: String? = null,
)

/** Response of POST/PATCH /api/weights/:id — `{"entry": {...}}`. */
@Serializable
data class WeightEntryEnvelopeDto(
    val entry: WeightEntryDto,
)

/** Response of GET /api/weights/latest — `entry` is null when nothing exists yet. */
@Serializable
data class LatestWeightEnvelopeDto(
    val entry: WeightEntryDto? = null,
)

/** One end of a weight change (what the weight was, and when). */
@Serializable
data class WeightPointDto(
    val weight: Double,
    val measuredAt: String,
)

/** The delta between the oldest and newest reading in a window. */
@Serializable
data class WeightChangeDto(
    val display: Double,
    val unit: String,
    val kg: Double,
    val from: WeightPointDto,
    val to: WeightPointDto,
)

/** Response of GET /api/weights. */
@Serializable
data class WeightHistoryDto(
    val entries: List<WeightEntryDto> = emptyList(),
    val count: Int = 0,
    val change: WeightChangeDto? = null,
    val days: Int? = null,
)

/** Body for POST /api/weights. */
@Serializable
data class CreateWeightRequest(
    val weight: Double,
    val unit: String? = null,
    val measuredAt: String? = null,
    val note: String? = null,
)

/**
 * Body for PATCH /api/weights/:id.
 *
 * Every field defaults to null, and because kotlinx.serialization skips default
 * values, only what the user actually changed is sent.
 */
@Serializable
data class UpdateWeightRequest(
    val weight: Double? = null,
    val unit: String? = null,
    val measuredAt: String? = null,
    val note: String? = null,
)

/** The weight section of the dashboard. */
@Serializable
data class DashboardWeightDto(
    val latest: WeightEntryDto? = null,
    val previous: WeightEntryDto? = null,
    val entryCount: Int = 0,
    val changeWindowDays: Int = 30,
    val change: WeightChangeDto? = null,
)

/** The BMI section of the dashboard. Null when it cannot be calculated yet. */
@Serializable
data class BmiDto(
    val value: Double,
    val category: String,
)

/** Response of GET /api/dashboard. */
@Serializable
data class DashboardDto(
    val weight: DashboardWeightDto = DashboardWeightDto(),
    val workouts: DashboardWorkoutsDto = DashboardWorkoutsDto(),
    val bmi: BmiDto? = null,
    val bmiUnavailableReason: String? = null,
)
