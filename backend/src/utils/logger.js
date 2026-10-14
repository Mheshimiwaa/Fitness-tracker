'use strict';

/**
 * Tiny logger so every log line has a timestamp and a level.
 *
 * We deliberately avoid a logging library for the MVP: fewer dependencies means
 * the code is easier for a student to read. Never log secrets (passwords, JWTs,
 * Authorization headers) — callers are responsible for keeping those out.
 */

function timestamp() {
  return new Date().toISOString();
}

const logger = {
  info(...args) {
    console.log(`[${timestamp()}] INFO  -`, ...args);
  },
  warn(...args) {
    console.warn(`[${timestamp()}] WARN  -`, ...args);
  },
  error(...args) {
    console.error(`[${timestamp()}] ERROR -`, ...args);
  },
  debug(...args) {
    if (process.env.NODE_ENV !== 'production') {
      console.log(`[${timestamp()}] DEBUG -`, ...args);
    }
  },
};

module.exports = logger;
