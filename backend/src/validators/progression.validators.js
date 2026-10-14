'use strict';

/**
 * Zod schemas for the progression endpoints.
 *
 * There is no body to validate here: progression is a read that takes the
 * exercise name (and an optional window size) as query parameters. The exercise
 * name is trimmed and length-checked; whether it matches anything is a question
 * for the data, not the schema — an unknown exercise simply has no sessions.
 */

const { z } = require('zod');
const { EXERCISE_NAME_MAX_LENGTH, PROGRESSION_MAX_LIMIT } = require('../config/constants');

/** GET /api/progression?exercise=Bench%20Press&limit=10 */
const progressionQuerySchema = z.object({
  exercise: z
    .string({ error: 'Exercise is required.' })
    .trim()
    .min(1, { error: 'Exercise is required.' })
    .max(EXERCISE_NAME_MAX_LENGTH, {
      error: `Exercise name must be ${EXERCISE_NAME_MAX_LENGTH} characters or fewer.`,
    }),
  limit: z.coerce
    .number({ error: 'Limit must be a number.' })
    .int({ error: 'Limit must be a whole number.' })
    .min(1, { error: 'Limit must be at least 1.' })
    .max(PROGRESSION_MAX_LIMIT, {
      error: `Limit must be at most ${PROGRESSION_MAX_LIMIT}.`,
    })
    .optional(),
});

module.exports = { progressionQuerySchema };
