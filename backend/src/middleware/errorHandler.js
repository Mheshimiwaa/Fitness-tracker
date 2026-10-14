'use strict';

/**
 * Central error handler — the LAST middleware in the chain.
 *
 * Every error in the app ends up here, so clients always receive the same
 * JSON shape:
 *
 *   { "error": { "message": "...", "details": ... } }
 *
 * Security: stack traces and internal error details are never sent to the
 * client in production. They ARE logged on the server so developers can debug.
 */

const env = require('../config/env');
const logger = require('../utils/logger');
const ApiError = require('../utils/ApiError');

// eslint-disable-next-line no-unused-vars -- Express needs the 4-arg signature.
function errorHandler(err, req, res, next) {
  let statusCode = err.statusCode || 500;
  let message = err.message || 'Something went wrong on the server.';
  let details = err.details;

  // Zod validation errors arrive as a plain Error with an `issues` array.
  if (err.name === 'ZodError' && Array.isArray(err.issues)) {
    statusCode = 400;
    message = 'Validation failed.';
    details = err.issues.map((issue) => ({
      path: issue.path.join('.'),
      message: issue.message,
    }));
  }

  // MongoDB duplicate-key error (e.g. username already taken).
  if (err.code === 11000) {
    statusCode = 409;
    const field = Object.keys(err.keyPattern || {})[0] || 'field';
    message = `A record with that ${field} already exists.`;
  }

  const isServerError = statusCode >= 500;

  if (isServerError) {
    logger.error(`${req.method} ${req.originalUrl} -> ${statusCode}`, err);
  }

  // Never leak internal details (or stacks) for unexpected errors.
  if (isServerError && !err.isOperational && env.isProduction) {
    message = 'Something went wrong on the server.';
    details = undefined;
  }

  const body = { error: { message } };
  if (details !== undefined) body.error.details = details;

  res.status(statusCode).json(body);
}

module.exports = errorHandler;
