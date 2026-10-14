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

module.exports = {
  FITNESS_GOALS,
  WEIGHT_UNITS,
  USERNAME_PATTERN,
  PASSWORD_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  HEIGHT_CM_MIN,
  HEIGHT_CM_MAX,
};
