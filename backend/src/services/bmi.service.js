'use strict';

/**
 * BMI service — a pure calculation with no database access.
 *
 * BMI = body mass (kg) / height (m)^2
 *
 * It is deliberately kept separate from the weight service so it can be tested
 * on its own, and so the formula lives in exactly one place.
 *
 * IMPORTANT: BMI is a rough screening measure. It does not distinguish muscle
 * from fat, so a muscular lifter can read as "overweight" while being perfectly
 * healthy. The API reports the number and the WHO category and nothing more —
 * the app shows the disclaimers. It never gives health advice.
 */

const {
  BMI_UNDERWEIGHT_MAX,
  BMI_NORMAL_MAX,
  BMI_OVERWEIGHT_MAX,
} = require('../config/constants');

const { round } = require('../utils/units');

/**
 * Map a BMI value to its adult WHO category.
 * @returns {'underweight'|'normal'|'overweight'|'obese'}
 */
function categoriseBmi(value) {
  if (value < BMI_UNDERWEIGHT_MAX) return 'underweight';
  if (value < BMI_NORMAL_MAX) return 'normal';
  if (value < BMI_OVERWEIGHT_MAX) return 'overweight';
  return 'obese';
}

/**
 * Calculate BMI from a weight in kilograms and a height in centimetres.
 *
 * @returns {{ value: number, category: string }|null} null when either input is
 *          missing or nonsensical, so callers can show an honest empty state
 *          instead of a fake number.
 */
function calculateBmi(weightKg, heightCm) {
  if (!Number.isFinite(weightKg) || weightKg <= 0) return null;
  if (!Number.isFinite(heightCm) || heightCm <= 0) return null;

  const heightMetres = heightCm / 100;
  const value = weightKg / (heightMetres * heightMetres);

  if (!Number.isFinite(value) || value <= 0) return null;

  return { value: round(value, 1), category: categoriseBmi(value) };
}

module.exports = { calculateBmi, categoriseBmi };
