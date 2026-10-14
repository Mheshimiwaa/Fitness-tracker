'use strict';

/**
 * Loads and centralises configuration for the whole backend.
 *
 * Why this file exists:
 *   - We read every environment variable in ONE place instead of scattering
 *     `process.env.X` across the codebase. That makes the app easier to reason
 *     about and easier to test.
 *   - If a required variable is missing we fail immediately, at startup, with a
 *     clear message — instead of crashing later with a confusing error.
 *
 * CommonJS note: `require(...)` imports a module and `module.exports = ...`
 * exposes values from this file to other files.
 */

const path = require('path');
const dotenv = require('dotenv');

// Read backend/.env into process.env. Variables already present in the real
// environment are NOT overwritten by dotenv, which is the behaviour we want
// when deploying.
dotenv.config({ path: path.resolve(__dirname, '../../.env'), quiet: true });

/** Return a required variable or throw a helpful error. */
function required(name) {
  const value = process.env[name];
  if (value === undefined || value.trim() === '') {
    throw new Error(
      `Missing required environment variable "${name}". ` +
        'Copy backend/.env.example to backend/.env and fill it in.'
    );
  }
  return value.trim();
}

/** Return a variable, or a fallback when it is unset/blank. */
function optional(name, fallback) {
  const value = process.env[name];
  return value === undefined || value.trim() === '' ? fallback : value.trim();
}

const nodeEnv = optional('NODE_ENV', 'development');

const env = {
  nodeEnv,
  isProduction: nodeEnv === 'production',
  isTest: nodeEnv === 'test',
  port: Number(optional('PORT', '4000')),

  mongoUri: required('MONGODB_URI'),
  mongoDbName: optional('MONGODB_DB_NAME', 'reprise'),

  jwtSecret: required('JWT_SECRET'),
  jwtExpiresIn: optional('JWT_EXPIRES_IN', '7d'),

  bcryptRounds: Number(optional('BCRYPT_ROUNDS', '10')),

  corsOrigin: optional('CORS_ORIGIN', '*'),

  // String "false" (any case) disables it; anything else enables it.
  enableHttpLogs: optional('ENABLE_HTTP_LOGS', 'true').toLowerCase() !== 'false',
};

module.exports = env;
