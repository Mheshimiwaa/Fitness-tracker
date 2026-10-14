'use strict';

/**
 * Shared, app-wide constants.
 *
 * Keeping these in one place means the validation schemas, the services and the
 * README all describe the SAME rules — there is no way for them to drift apart.
 */

/**
 * The user's main fitness goal, stored as a short machine-friendly code.
 * The Android app maps each code to a friendly label.
 */
const FITNESS_GOALS = Object.freeze([
  'lose_weight',
  'maintain',
  'gain_muscle',
  'improve_strength',
]);

/**
 * Weight can be displayed in kilograms or pounds. The server always stores the
 * unit alongside the number so nothing is ever ambiguous.
 */
const WEIGHT_UNITS = Object.freeze(['kg', 'lb']);

/**
 * Usernames: 3–20 characters, lowercase letters, digits and underscore, and
 * they must START with a letter. Enforcing a strict pattern keeps usernames
 * easy to search for and impossible to confuse with an email address.
 */
const USERNAME_PATTERN = /^[a-z][a-z0-9_]{2,19}$/;

const PASSWORD_MIN_LENGTH = 8;

/**
 * bcrypt only uses the first 72 bytes of a password. We reject anything longer
 * instead of silently ignoring the tail, which would surprise the user.
 */
const PASSWORD_MAX_LENGTH = 72;

/** Plausible adult human height range, in centimetres. */
const HEIGHT_CM_MIN = 50;
const HEIGHT_CM_MAX = 272;

/**
 * Plausible adult body-weight range, expressed per unit.
 *
 * Bounds are checked AFTER the unit is known — either supplied by the client or
 * defaulted from the user's preference — so "400" is rejected in kilograms but
 * accepted in pounds.
 */
const WEIGHT_KG_MIN = 20;
const WEIGHT_KG_MAX = 500;
const WEIGHT_LB_MIN = 44;
const WEIGHT_LB_MAX = 1102;

/** Exact conversion factor (international avoirdupois pound). */
const KG_PER_LB = 0.45359237;

/** Free-text note attached to a weight entry. */
const WEIGHT_NOTE_MAX_LENGTH = 200;

/**
 * A measurement is allowed slightly in the future to absorb clock skew between
 * the phone and the server, but no further.
 */
const MEASUREMENT_FUTURE_TOLERANCE_MS = 5 * 60 * 1000;
const MEASUREMENT_EARLIEST_YEAR = 1900;

/**
 * Adult BMI category thresholds, as defined by the World Health Organization.
 * Each constant is the EXCLUSIVE upper bound of that band, so 25 falls into
 * "overweight", exactly as the WHO table says.
 */
const BMI_UNDERWEIGHT_MAX = 18.5;
const BMI_NORMAL_MAX = 25;
const BMI_OVERWEIGHT_MAX = 30;

/**
 * Workout logging limits.
 *
 * These are deliberately generous — the point is to reject nonsense (a 10 000
 * character workout name, a set of 5 million reps) without getting in the way of
 * any realistic training session.
 */
const WORKOUT_NAME_MAX_LENGTH = 80;
const WORKOUT_NOTES_MAX_LENGTH = 500;
const WORKOUT_MIN_MINUTES = 1;
const WORKOUT_MAX_MINUTES = 600;
const WORKOUT_MAX_EXERCISES = 30;

const EXERCISE_NAME_MAX_LENGTH = 60;
const EXERCISE_NOTES_MAX_LENGTH = 200;
const EXERCISE_REST_MAX_SECONDS = 3600;
const EXERCISE_MAX_SETS = 30;

/**
 * A set is `reps x weight`. The weight is the load the user actually put on the
 * bar, so it is checked against the plausible range for the EXERCISE's unit.
 *
 * Zero is allowed on purpose: bodyweight movements (push-ups, pull-ups) and an
 * empty bar are both legitimate "0 added weight" sets.
 */
const SET_REPS_MIN = 1;
const SET_REPS_MAX = 1000;
const SET_WEIGHT_KG_MAX = 1000;
const SET_WEIGHT_LB_MAX = 2205;

/** How far back the dashboard's "recent training" numbers look. */
const WORKOUT_RECENT_DAYS = 7;

/**
 * Progressive-overload assistant.
 *
 * The comparison is deliberately RULE-BASED and explainable — there is no model
 * here, just the judgements a lifter would make themselves:
 *
 *   - did the heaviest set get heavier?
 *   - was more total work (weight x reps) done?
 *   - were more reps done without losing volume?
 *
 * `PROGRESSION_VOLUME_TOLERANCE` absorbs ordinary day-to-day noise (a slightly
 * sloppy rep, a different bar) so the app does not shout "regression" over a 1%
 * wobble. The two increments are the smallest sensible jump: 2.5 kg is one pair
 * of small plates, 5 lb the imperial equivalent.
 */
const PROGRESSION_VOLUME_TOLERANCE = 0.02;
const PROGRESSION_INCREMENT_KG = 2.5;
const PROGRESSION_INCREMENT_LB = 5;
const PROGRESSION_DEFAULT_LIMIT = 10;
const PROGRESSION_MAX_LIMIT = 50;

module.exports = {
  FITNESS_GOALS,
  WEIGHT_UNITS,
  USERNAME_PATTERN,
  PASSWORD_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  HEIGHT_CM_MIN,
  HEIGHT_CM_MAX,
  WEIGHT_KG_MIN,
  WEIGHT_KG_MAX,
  WEIGHT_LB_MIN,
  WEIGHT_LB_MAX,
  KG_PER_LB,
  WEIGHT_NOTE_MAX_LENGTH,
  MEASUREMENT_FUTURE_TOLERANCE_MS,
  MEASUREMENT_EARLIEST_YEAR,
  BMI_UNDERWEIGHT_MAX,
  BMI_NORMAL_MAX,
  BMI_OVERWEIGHT_MAX,
  WORKOUT_NAME_MAX_LENGTH,
  WORKOUT_NOTES_MAX_LENGTH,
  WORKOUT_MIN_MINUTES,
  WORKOUT_MAX_MINUTES,
  WORKOUT_MAX_EXERCISES,
  EXERCISE_NAME_MAX_LENGTH,
  EXERCISE_NOTES_MAX_LENGTH,
  EXERCISE_REST_MAX_SECONDS,
  EXERCISE_MAX_SETS,
  SET_REPS_MIN,
  SET_REPS_MAX,
  SET_WEIGHT_KG_MAX,
  SET_WEIGHT_LB_MAX,
  WORKOUT_RECENT_DAYS,
  PROGRESSION_VOLUME_TOLERANCE,
  PROGRESSION_INCREMENT_KG,
  PROGRESSION_INCREMENT_LB,
  PROGRESSION_DEFAULT_LIMIT,
  PROGRESSION_MAX_LIMIT,
};
