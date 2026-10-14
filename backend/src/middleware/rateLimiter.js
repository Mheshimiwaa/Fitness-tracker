'use strict';

/**
 * Rate limiting.
 *
 * Auth endpoints are the classic brute-force target: an attacker can try
 * thousands of passwords per minute. A limiter caps how many requests a single
 * IP may make in a time window and replies 429 once the cap is reached.
 *
 * The limit is raised in the test environment so that running the test suite
 * does not trip it. (A dedicated test for the limiter sets its own limit.)
 */

const rateLimit = require('express-rate-limit');
const env = require('../config/env');

/** Standard error shape, so 429s look like every other API error. */
function limitReachedHandler(_req, res) {
  res.status(429).json({
    error: {
      message: 'Too many attempts. Please wait a few minutes and try again.',
    },
  });
}

/**
 * Login/register limiter: 20 requests per 15 minutes per IP in normal use.
 */
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: env.isTest ? 10_000 : 20,
  standardHeaders: true, // send RateLimit-* headers
  legacyHeaders: false, // do not send the old X-RateLimit-* headers
  handler: limitReachedHandler,
});

/**
 * A looser limiter for general authenticated API traffic, to blunt scripted
 * abuse without getting in the way of normal use.
 */
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: env.isTest ? 10_000 : 600,
  standardHeaders: true,
  legacyHeaders: false,
  handler: limitReachedHandler,
});

module.exports = { authLimiter, apiLimiter };
