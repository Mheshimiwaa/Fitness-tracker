'use strict';

/**
 * Zod schemas for the body-weight endpoints.
 *
 * Two rules are worth calling out:
 *
 *   1. `weight` is validated against the PLAUSIBLE RANGE FOR ITS UNIT. Zod can
 *      only do that when the unit is present in the body, so the service also
 *      re-checks after defaulting the unit from the user's preference. Zod
 *      still runs a wide, unit-agnostic sanity check so a client that forgets
 *      the unit cannot store something absurd.
 *
 *   2. `measuredAt` may be back-dated (people log yesterday's weigh-in) but not
 *      set in the future beyond a small clock-skew tolerance.
 */

const { z } = require('zod');
const {
  WEIGHT_UNITS,
  WEIGHT_KG_MIN,
  WEIGHT_KG_MAX,
  WEIGHT_LB_MIN,
  WEIGHT_LB_MAX,
  WEIGHT_NOTE_MAX_LENGTH,
  MEASUREMENT_FUTURE_TOLERANCE_MS,
  MEASUREMENT_EARLIEST_YEAR,
} = require('../config/constants');

/** A measurement timestamp that is neither far in the future nor ancient. */
const measuredAtField = z.coerce
  .date({ error: 'Measured date must be a valid date.' })
  .refine(
    (value) => value.getTime() <= Date.now() + MEASUREMENT_FUTURE_TOLERANCE_MS,
    { error: 'Measured date cannot be in the future.' }
  )
  .refine((value) => value.getFullYear() >= MEASUREMENT_EARLIEST_YEAR, {
    error: `Measured date must be after ${MEASUREMENT_EARLIEST_YEAR}.`,
  });

/** Optional free-text note. An empty string is normalised to null. */
const noteField = z
  .string()
  .trim()
  .max(WEIGHT_NOTE_MAX_LENGTH, {
    error: `Note must be ${WEIGHT_NOTE_MAX_LENGTH} characters or fewer.`,
  })
  .transform((value) => (value === '' ? null : value));

/** The weight number itself. Wide bounds; refined per-unit below. */
const weightField = z.coerce
  .number({ error: 'Weight must be a number.' })
  .positive({ error: 'Weight must be greater than zero.' })
  .max(WEIGHT_LB_MAX, { error: 'Weight is unrealistically high.' });

/** Reject a value that is plausible in one unit but not in the stated one. */
function checkUnitRange(data, ctx) {
  if (!data.unit || data.weight === undefined) return;

  const [min, max] =
    data.unit === 'lb' ? [WEIGHT_LB_MIN, WEIGHT_LB_MAX] : [WEIGHT_KG_MIN, WEIGHT_KG_MAX];

  if (data.weight < min || data.weight > max) {
    ctx.addIssue({
      code: 'custom',
      path: ['weight'],
      message: `Weight must be between ${min} and ${max} ${data.unit}.`,
    });
  }
}

/** POST /api/weights */
const createWeightSchema = z
  .object({
    weight: weightField,
    unit: z
      .enum(WEIGHT_UNITS, {
        error: `Weight unit must be one of: ${WEIGHT_UNITS.join(', ')}.`,
      })
      .optional(),
    measuredAt: measuredAtField.optional(),
    note: noteField.optional(),
  })
  .superRefine(checkUnitRange);

/**
 * PATCH /api/weights/:id
 *
 * At least one field must be present; an empty body is a client bug.
 */
const updateWeightSchema = z
  .object({
    weight: weightField.optional(),
    unit: z
      .enum(WEIGHT_UNITS, {
        error: `Weight unit must be one of: ${WEIGHT_UNITS.join(', ')}.`,
      })
      .optional(),
    measuredAt: measuredAtField.optional(),
    note: noteField.optional(),
  })
  .superRefine((data, ctx) => {
    const hasAnyField = Object.values(data).some((value) => value !== undefined);
    if (!hasAnyField) {
      ctx.addIssue({ code: 'custom', message: 'Provide at least one field to update.' });
    }
    checkUnitRange(data, ctx);
  });

/** GET /api/weights?days=30&limit=100&order=desc */
const listWeightsQuerySchema = z.object({
  days: z.coerce
    .number({ error: 'Days must be a number.' })
    .int({ error: 'Days must be a whole number.' })
    .min(1, { error: 'Days must be at least 1.' })
    .max(3650, { error: 'Days must be at most 3650.' })
    .optional(),
  limit: z.coerce
    .number({ error: 'Limit must be a number.' })
    .int({ error: 'Limit must be a whole number.' })
    .min(1, { error: 'Limit must be at least 1.' })
    .max(500, { error: 'Limit must be at most 500.' })
    .optional(),
  order: z
    .enum(['asc', 'desc'], { error: "Order must be either 'asc' or 'desc'." })
    .optional(),
});

/** The `:id` path parameter. A 24-hex MongoDB id. */
const weightIdParamSchema = z.object({
  id: z
    .string({ error: 'Weight entry id is required.' })
    .regex(/^[a-f\d]{24}$/i, { error: 'Weight entry id is not valid.' }),
});

module.exports = {
  createWeightSchema,
  updateWeightSchema,
  listWeightsQuerySchema,
  weightIdParamSchema,
};
