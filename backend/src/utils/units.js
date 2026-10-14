'use strict';

/**
 * Unit conversion and rounding helpers.
 *
 * Weight is stored exactly as the user entered it (value + unit), so nothing is
 * ever ambiguous. Anything that needs a single canonical number — BMI, or a
 * chart drawn in the user's current preference — converts through KILOGRAMS.
 *
 * Keeping these three functions in one file means the conversion factor is
 * written down exactly once.
 */

const { KG_PER_LB } = require('../config/constants');

/** Convert a value expressed in `unit` into kilograms. */
function toKilograms(value, unit) {
  return unit === 'lb' ? value * KG_PER_LB : value;
}

/** Convert a value in kilograms into `unit`. */
function fromKilograms(kilograms, unit) {
  return unit === 'kg' ? kilograms : kilograms / KG_PER_LB;
}

/**
 * Round to a fixed number of decimal places.
 *
 * The `Number.EPSILON` nudge avoids the classic floating-point surprise where
 * `Math.round(1.005 * 100)` gives 100 instead of 101.
 */
function round(value, decimals = 1) {
  if (!Number.isFinite(value)) return value;
  const factor = 10 ** decimals;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

module.exports = { toKilograms, fromKilograms, round };
