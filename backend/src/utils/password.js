'use strict';

/**
 * Password hashing helpers.
 *
 * NEVER store a password in plain text. We use bcrypt, which:
 *   1. adds a random "salt" so two users with the same password get different
 *      hashes (defeating pre-computed "rainbow table" attacks), and
 *   2. is deliberately SLOW, so brute-forcing a stolen hash is expensive.
 *
 * bcrypt hashes are one-way: we can check a password against a hash, but we can
 * never turn a hash back into the password.
 */

const bcrypt = require('bcryptjs');
const env = require('../config/env');

/**
 * Hash a plain-text password.
 * @param {string} plainPassword
 * @returns {Promise<string>} the bcrypt hash (safe to store in MongoDB)
 */
async function hashPassword(plainPassword) {
  const salt = await bcrypt.genSalt(env.bcryptRounds);
  return bcrypt.hash(plainPassword, salt);
}

/**
 * Check a plain-text password against a stored hash.
 * @param {string} plainPassword
 * @param {string} passwordHash
 * @returns {Promise<boolean>} true when the password matches
 */
async function verifyPassword(plainPassword, passwordHash) {
  if (!passwordHash) return false;
  return bcrypt.compare(plainPassword, passwordHash);
}

module.exports = { hashPassword, verifyPassword };
