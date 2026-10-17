'use strict';

/**
 * Connects to MongoDB on demand, when nothing did it at startup.
 *
 * `src/start.js` is how the API runs on a normal machine: it connects BEFORE
 * it starts listening and refuses to start if the database is unreachable.
 *
 * A serverless host (Vercel) has no equivalent startup step. It wraps the
 * Express app itself — it may import `src/app.js` directly and never run a
 * single line of `start.js` — so `connect()` would never be called and every
 * request would fail with "MongoDB is not connected yet". The app would look
 * broken for a reason that has nothing to do with the app.
 *
 * This middleware removes that dependency on how the process starts. The first
 * request that needs the database triggers the connection:
 *   - the MongoClient and its pool live in module scope, so a warm instance
 *     reuses them and pays the TLS handshake only once per cold start;
 *   - concurrent requests await ONE in-flight attempt instead of each opening
 *     their own connection;
 *   - a FAILED attempt is not remembered, so a later request tries again and
 *     the API recovers by itself once the database is back.
 *
 * It cannot hide a database problem: a failure is logged, the request carries
 * on regardless, and the route that needed the database fails with its own
 * honest error while `/api/health` still reports `degraded`.
 *
 * Deliberately OFF during tests. There the process manages its own connection
 * (tests/helpers/db.js), and the suite is designed to still run when MongoDB is
 * unreachable — connecting here would make every one of those tests wait ten
 * seconds for a connection that cannot succeed.
 *
 * The rule is "connect only if nobody else has", rather than "connect only in
 * production", so the app also works on a host that does not set NODE_ENV.
 */

const env = require('../config/env');
const logger = require('../utils/logger');
const { connect, isConnected } = require('../database/connection');
const { ensureIndexes } = require('../database/indexes');

/** The in-flight connect attempt, shared by every request while it runs. */
let inFlight = null;

/** Connect and create the indexes once. Never rejects — a failure is logged. */
async function connectOnce() {
  try {
    await connect();
    await ensureIndexes();
  } catch (error) {
    logger.error(`MongoDB connection failed for this request: ${error.message}`);
  }
}

function ensureDatabase(_req, _res, next) {
  if (env.isTest || isConnected()) {
    return next();
  }

  if (!inFlight) {
    inFlight = connectOnce().finally(() => {
      // Clear it either way: a success is already cached by connection.js, and
      // a failure must be retired so the next request can retry.
      inFlight = null;
    });
  }

  // connectOnce() cannot reject, so this always reaches next().
  inFlight.then(() => next());
}

module.exports = ensureDatabase;
