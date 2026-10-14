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
};
