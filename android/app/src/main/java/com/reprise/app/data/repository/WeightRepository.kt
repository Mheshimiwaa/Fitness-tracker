package com.reprise.app.data.repository

import com.reprise.app.data.remote.ApiResult
import com.reprise.app.data.remote.RepRiseApi
import com.reprise.app.data.remote.apiCall
import com.reprise.app.data.remote.apiCallUnit
import com.reprise.app.data.remote.dto.BmiDto
import com.reprise.app.data.remote.dto.CreateWeightRequest
import com.reprise.app.data.remote.dto.DashboardDto
import com.reprise.app.data.remote.dto.UpdateWeightRequest
import com.reprise.app.data.remote.dto.WeightChangeDto
import com.reprise.app.data.remote.dto.WeightEntryDto
import com.reprise.app.data.remote.dto.WeightHistoryDto
import com.reprise.app.domain.model.Bmi
import com.reprise.app.domain.model.BmiCategory
import com.reprise.app.domain.model.BmiUnavailableReason
import com.reprise.app.domain.model.Dashboard
import com.reprise.app.domain.model.WeightChange
import com.reprise.app.domain.model.WeightEntry
import com.reprise.app.domain.model.WeightHistory
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.domain.model.WorkoutStats

/**
 * Everything the app needs to read and write body-weight data.
 *
 * The repository's job is to translate between the wire DTOs and the domain
 * models, so no ViewModel or screen ever sees a `...Dto` type. It owns no state:
 * each call goes straight to the network and the result flows back.
 */
class WeightRepository(private val api: RepRiseApi) {

    /** The Home tab's summary. */
    suspend fun dashboard(): ApiResult<Dashboard> =
        apiCall { api.dashboard() }.map { it.toDomain() }

    /**
     * Weight history, newest first.
     *
     * @param days restrict to the last N days (null = the server's default)
     */
    suspend fun history(days: Int? = null, limit: Int? = null): ApiResult<WeightHistory> =
        apiCall { api.weightHistory(days = days, limit = limit) }.map { it.toDomain() }

    /**
     * Log a new reading.
     *
     * @param unit   null lets the server default to the user's preference
     * @param measuredAt ISO-8601 timestamp, or null for "now"
     */
    suspend fun addWeight(
        weight: Double,
        unit: WeightUnit? = null,
        measuredAt: String? = null,
        note: String? = null,
    ): ApiResult<WeightEntry> = apiCall {
        api.createWeight(
            CreateWeightRequest(
                weight = weight,
                unit = unit?.apiValue,
                measuredAt = measuredAt,
                // An empty note is sent as-is; the server normalises "" to null.
                note = note,
            )
        )
    }.map { it.entry.toDomain() }

    /** Edit an existing reading. Only the non-null arguments are sent. */
    suspend fun updateWeight(
        id: String,
        weight: Double? = null,
        note: String? = null,
    ): ApiResult<WeightEntry> = apiCall {
        api.updateWeight(id, UpdateWeightRequest(weight = weight, note = note))
    }.map { it.entry.toDomain() }

    /**
     * Delete an entry.
     *
     * Uses [apiCallUnit] because the server answers 204 No Content: a successful
     * response carries no body for [apiCall] to unwrap.
     */
    suspend fun deleteWeight(id: String): ApiResult<Unit> =
        apiCallUnit { api.deleteWeight(id) }

    /**
     * Apply [transform] to a successful result, leaving failures untouched.
     *
     * This keeps every method above a one-liner: the network call plus a single
     * mapping step.
     */
    private inline fun <T : Any, R : Any> ApiResult<T>.map(
        transform: (T) -> R,
    ): ApiResult<R> = when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
    }
}

// ---------------------------------------------------------------------------
// DTO -> domain mappings
// ---------------------------------------------------------------------------

private fun WeightEntryDto.toDomain(): WeightEntry = WeightEntry(
    id = id,
    weight = weight,
    unit = WeightUnit.fromApi(unit),
    weightKg = weightKg,
    measuredAt = measuredAt,
    note = note,
)

private fun WeightChangeDto.toDomain(): WeightChange = WeightChange(
    amount = display,
    unit = WeightUnit.fromApi(unit),
)

private fun WeightHistoryDto.toDomain(): WeightHistory = WeightHistory(
    entries = entries.map { it.toDomain() },
    change = change?.toDomain(),
)

private fun BmiDto.toDomain(): Bmi? {
    // An unknown category would leave nothing sensible to display, so treat the
    // whole value as unavailable rather than showing a bare number.
    val category = BmiCategory.fromApi(category) ?: return null
    return Bmi(value = value, category = category)
}

private fun DashboardDto.toDomain(): Dashboard = Dashboard(
    latestWeight = weight.latest?.toDomain(),
    previousWeight = weight.previous?.toDomain(),
    weightEntryCount = weight.entryCount,
    changeWindowDays = weight.changeWindowDays,
    weightChange = weight.change?.toDomain(),
    workouts = WorkoutStats(
        totalCount = workouts.totalCount,
        recentCount = workouts.recentCount,
        recentDays = workouts.recentDays,
        recentVolumeKg = workouts.recentVolumeKg,
        lastWorkoutAt = workouts.lastWorkoutAt,
    ),
    bmi = bmi?.toDomain(),
    bmiUnavailableReason = BmiUnavailableReason.fromApi(bmiUnavailableReason),
)
