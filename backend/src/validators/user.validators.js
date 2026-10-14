'use strict';

/**
 * Zod schemas for the user/profile endpoints.
 */

const { z } = require('zod');
const {
  usernameField,
  fitnessGoalField,
  weightUnitField,
  heightCmField,
  profileImageUrlField,
} = require('./common.fields');

/**
 * PATCH /api/users/me
 *
 * Every field is optional (the user may change just one thing), but at least
 * one field must be present — an empty body is almost always a client bug, so
 * we report it instead of silently doing nothing.
 */
const updateProfileSchema = z
  .object({
    displayName: z
      .string({ error: 'Display name is required.' })
      .trim()
      .min(1, { error: 'Display name is required.' })
      .max(40, { error: 'Display name must be 40 characters or fewer.' })
      .optional(),
    username: usernameField.optional(),
    fitnessGoal: fitnessGoalField.optional(),
    weightUnit: weightUnitField.optional(),
    // `null` is allowed to CLEAR a value (for example, clearing height).
    heightCm: heightCmField.nullable().optional(),
    profileImageUrl: profileImageUrlField.nullable().optional(),
  })
  .refine((data) => Object.keys(data).length > 0, {
    error: 'Provide at least one field to update.',
  });

/**
 * GET /api/users/search?username=<text>
 *
 * A short, non-empty search term. We do not apply the strict username pattern
 * because the user may be part-way through typing a name.
 */
const searchUsersQuerySchema = z.object({
  username: z
    .string({ error: 'Search text is required.' })
    .trim()
    .toLowerCase()
    .min(1, { error: 'Enter a username to search for.' })
    .max(30, { error: 'Search text must be 30 characters or fewer.' }),
});

module.exports = { updateProfileSchema, searchUsersQuerySchema };
