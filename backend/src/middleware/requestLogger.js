'use strict';

/**
 * Development-only HTTP request logging.
 *
 * Logs: HTTP method, route, status code, duration and a request id.
 * Does NOT log: request bodies, Authorization headers, passwords or JWTs.
 *
 * Gated behind ENABLE_HTTP_LOGS so production deployments can turn it off.
 */

const crypto = require('crypto');
const env = require('../config/env');
const logger = require('../utils/logger');

function requestLogger(req, res, next) {
  if (!env.enableHttpLogs) {
    return next();
  }

  // A random id lets us correlate the "request received" and "response sent"
  // log lines, even when several requests are in flight at once.
  const requestId = crypto.randomUUID();
  req.requestId = requestId;
  res.setHeader('X-Request-Id', requestId);

  const startedAt = process.hrtime.bigint();

  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - startedAt) / 1e6;
    logger.info(
      `${req.method} ${req.originalUrl} ${res.statusCode} ${durationMs.toFixed(1)}ms requestId=${requestId}`
    );
  });

  return next();
}

module.exports = requestLogger;
