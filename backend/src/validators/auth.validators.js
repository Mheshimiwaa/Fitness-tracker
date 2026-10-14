'use strict';

/**
 * Zod schemas for the authentication endpoints.
 *
 * Why validate here, BEFORE the controller runs?
 *   1. Controllers can then trust their input completely — no defensive checks
 *      scattered through the business logic.
 *   2. Every validation failure returns the SAME 400 shape, produced by the
 *      central error handler.
 *   3. The schemas document the API: reading this file tells you exactly what
 *      POST /api/auth/register accepts.
 */

const { z } = require('zod');
const {
  usernameField,
  emailField,
  passwordField,
  displayNameField,
} = require('./common.fields');

/** POST /api/auth/register */
const registerSchema = z.object({
  displayName: displayNameField,
  username: usernameField,
  email: emailField,
  password: passwordField,
});

/**
 * POST /api/auth/login
 *
 * The strict password rules are deliberately NOT applied here. Login must accept
 * whatever the user typed and simply report "invalid credentials" if it is
 * wrong — replying "your password is too short" would leak the password policy
 * and confuse anyone whose password predates a rule change.
 */
const loginSchema = z.object({
  username: z
    .string({ error: 'Username is required.' })
    .trim()
    .toLowerCase()
    .min(1, { error: 'Username is required.' }),
  password: z.string({ error: 'Password is required.' }).min(1, {
    error: 'Password is required.',
  }),
});

module.exports = { registerSchema, loginSchema };
