'use strict';

/**
 * JSON Web Token (JWT) helpers.
 *
 * After a successful login we hand the client a signed token. The client sends
 * it back on every protected request as:
 *
 *   Authorization: Bearer <token>
 *
 * A JWT is NOT encrypted — anyone can read its payload (base64). It is SIGNED,
 * so the server can detect tampering. Therefore:
 *   - put only non-sensitive identifiers in it (we store the user's _id), and
 *   - never put a password or personal data inside.
 */

const jwt = require('jsonwebtoken');
const env = require('../config/env');

/**
 * Create a signed token for a user.
 * @param {{ _id: unknown }} user
 * @returns {string} the signed JWT
 */
function signToken(user) {
  // `sub` (subject) is the standard JWT claim for "who this token is about".
  return jwt.sign({ sub: String(user._id) }, env.jwtSecret, {
    expiresIn: env.jwtExpiresIn,
  });
}

/**
 * Verify a token's signature and expiry.
 * @param {string} token
 * @returns {{ sub: string }} the decoded payload
 * @throws {Error} when the token is invalid, tampered with, or expired
 */
function verifyToken(token) {
  return jwt.verify(token, env.jwtSecret);
}

module.exports = { signToken, verifyToken };
