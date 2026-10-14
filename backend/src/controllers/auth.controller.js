'use strict';

/**
 * Auth controller — the HTTP layer for registration and login.
 *
 * Controllers are deliberately thin: validate (done by middleware), call a
 * service, and shape the response. All the interesting rules live in the
 * service files.
 */

const authService = require('../services/auth.service');
const userService = require('../services/user.service');

/**
 * POST /api/auth/register  ->  201 Created
 * Body: { displayName, username, email, password }
 */
async function register(req, res) {
  const { user, token } = await authService.registerUser(req.body);
  res.status(201).json({ user, token });
}

/**
 * POST /api/auth/login  ->  200 OK
 * Body: { username, password }
 */
async function login(req, res) {
  const { user, token } = await authService.loginUser(req.body);
  res.json({ user, token });
}

/**
 * GET /api/auth/me  ->  200 OK
 *
 * Requires a valid token. Useful for the app to check on startup whether a
 * stored token is still valid and to refresh the cached profile.
 */
async function me(req, res) {
  res.json({ user: userService.toPublicUser(req.user) });
}

module.exports = { register, login, me };
