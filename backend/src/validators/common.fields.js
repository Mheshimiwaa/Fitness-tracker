'use strict';

/**
 * Reusable Zod field schemas.
 *
 * These describe individual pieces of user input. Both the auth endpoints and
 * the profile-update endpoint use them, which guarantees a username rules is
 * EXACTLY the same whether it is set at registration or changed later.
 */

const { z } = require('zod');
const {
  FITNESS_GOALS,
  WEIGHT_UNITS,
  USERNAME_PATTERN,
  PASSWORD_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  HEIGHT_CM_MIN,
  HEIGHT_CM_MAX,
} = require('../config/constants');

/** Trimmed, lower-cased, pattern-checked username. */
const usernameField = z
  .string({ error: 'Username is required.' })
  .trim()
  .toLowerCase()
  .regex(USERNAME_PATTERN, {
    error:
      'Username must be 3-20 characters, start with a letter, and contain only letters, numbers and underscores.',
  });

/**
 * Email address.
 *
 * We only TRIM here (no lower-casing) so the user's email is displayed exactly
 * as they typed it. The service stores a separate lower-cased copy for the
 * case-insensitive unique index — see `normalizeEmail` in user.service.js.
 */
const emailField = z
  .string({ error: 'Email is required.' })
  .trim()
  .pipe(z.email({ error: 'Enter a valid email address.' }));

/** Password with a minimum length and a letter+number requirement. */
const passwordField = z
  .string({ error: 'Password is required.' })
  .min(PASSWORD_MIN_LENGTH, {
    error: `Password must be at least ${PASSWORD_MIN_LENGTH} characters.`,
  })
  .max(PASSWORD_MAX_LENGTH, {
    error: `Password must be at most ${PASSWORD_MAX_LENGTH} characters.`,
  })
  .regex(/[A-Za-z]/, { error: 'Password must contain at least one letter.' })
  .regex(/[0-9]/, { error: 'Password must contain at least one number.' });

/** Public display name shown to friends. */
const displayNameField = z
  .string({ error: 'Display name is required.' })
  .trim()
  .min(1, { error: 'Display name is required.' })
  .max(40, { error: 'Display name must be 40 characters or fewer.' });

/** The user's main fitness goal. */
const fitnessGoalField = z.enum(FITNESS_GOALS, {
  error: `Fitness goal must be one of: ${FITNESS_GOALS.join(', ')}.`,
});

/** Preferred weight unit. */
const weightUnitField = z.enum(WEIGHT_UNITS, {
  error: `Weight unit must be one of: ${WEIGHT_UNITS.join(', ')}.`,
});

/** Height in centimetres. Accepts "180" or 180 and stores a number. */
const heightCmField = z.coerce
  .number({ error: 'Height must be a number of centimetres.' })
  .min(HEIGHT_CM_MIN, { error: `Height must be at least ${HEIGHT_CM_MIN} cm.` })
  .max(HEIGHT_CM_MAX, { error: `Height must be at most ${HEIGHT_CM_MAX} cm.` });

/** Optional profile image, which must be a valid absolute http(s) URL. */
const profileImageUrlField = z
  .string()
  .trim()
  .url({ error: 'Profile image must be a valid URL.' })
  .max(2048, { error: 'Profile image URL is too long.' });

module.exports = {
  usernameField,
  emailField,
  passwordField,
  displayNameField,
  fitnessGoalField,
  weightUnitField,
  heightCmField,
  profileImageUrlField,
};
