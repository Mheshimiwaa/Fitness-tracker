'use strict';

/**
 * Authentication middleware.
 *
 * Protects routes that need a signed-in user. It expects:
 *
 *   Authorization: Bearer <token>
 *
 * On success it loads the user document and attaches it as `req.user`, so
 * downstream handlers know WHO is making the request. Loading the user from the
 * database (rather than trusting the token payload) means a deleted account is
 * rejected immediately — the token alone is not enough.
 *
 * On failure it forwards a 401 ApiError, so the client always gets the standard
 * error shape.
 */

const ApiError = require('../utils/ApiError');
const { verifyToken } = require('../utils/token');
const { findUserById } = require('../services/user.service');

/** Pull the token out of an "Authorization: Bearer xxx" header. */
function extractBearerToken(headerValue) {
  if (typeof headerValue !== 'string') return null;
  const [scheme, token] = headerValue.split(' ');
  if (!token || scheme.toLowerCase() !== 'bearer') return null;
  return token.trim();
}

async function authenticate(req, _res, next) {
  const token = extractBearerToken(req.headers.authorization);

  if (!token) {
    return next(ApiError.unauthorized('Missing or malformed Authorization header.'));
  }

  let payload;
  try {
    payload = verifyToken(token);
  } catch (error) {
    // Covers bad signature, tampering, and expiry. Deliberately vague: we do not
    // tell an attacker WHICH part of the token failed.
    const message =
      error.name === 'TokenExpiredError'
        ? 'Your session has expired. Please sign in again.'
        : 'Invalid authentication token.';
    return next(ApiError.unauthorized(message));
  }

  const user = await findUserById(payload.sub);
  if (!user) {
    return next(ApiError.unauthorized('This account no longer exists.'));
  }

  req.user = user;
  req.token = token;
  next();
}

module.exports = { authenticate };
