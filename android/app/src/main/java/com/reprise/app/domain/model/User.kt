package com.reprise.app.domain.model

import androidx.annotation.StringRes
import com.reprise.app.R

/**
 * The signed-in user, as the rest of the app sees them.
 *
 * This is a pure Kotlin model with no JSON or HTTP concerns — the DTOs in
 * `data/remote/dto` are mapped onto it by the repository.
 */
data class User(
    val id: String,
    val displayName: String,
    val username: String,
    val email: String?,
    val profileImageUrl: String?,
    val fitnessGoal: FitnessGoal,
    val weightUnit: WeightUnit,
    val heightCm: Int?,
)

/**
 * The user's main goal. The `apiValue` must match the server's allowed values
 * exactly; `labelRes` is the human-readable text shown in the UI.
 */
enum class FitnessGoal(
    val apiValue: String,
    @param:StringRes val labelRes: Int,
) {
    LOSE_WEIGHT("lose_weight", R.string.goal_lose_weight),
    MAINTAIN("maintain", R.string.goal_maintain),
    GAIN_MUSCLE("gain_muscle", R.string.goal_gain_muscle),
    IMPROVE_STRENGTH("improve_strength", R.string.goal_improve_strength);

    companion object {
        /** Map a server value to an enum, defaulting to [MAINTAIN]. */
        fun fromApi(value: String?): FitnessGoal =
            entries.firstOrNull { it.apiValue == value } ?: MAINTAIN
    }
}

/** The unit weights are displayed in. */
enum class WeightUnit(
    val apiValue: String,
    @param:StringRes val labelRes: Int,
    /** The compact form used inline with a value, e.g. "82.4 kg". */
    @param:StringRes val shortLabelRes: Int,
) {
    KG("kg", R.string.unit_kg, R.string.unit_kg_short),
    LB("lb", R.string.unit_lb, R.string.unit_lb_short);

    companion object {
        fun fromApi(value: String?): WeightUnit =
            entries.firstOrNull { it.apiValue == value } ?: KG
    }
}
