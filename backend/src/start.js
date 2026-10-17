'use strict';

/**
 * Entry point — the startup used on a normal machine (`npm start`).
 *
 * Startup order matters:
 *   1. Connect to MongoDB (fail fast if this does not work).
 *   2. Ensure indexes exist.
 *   3. Start listening for HTTP requests.
 *
 * This ordering means the API never accepts a request while the database is
 * unreachable — so a failed write is reported honestly instead of being faked.
 *
 * WHY THIS FILE IS NOT CALLED `server.js`:
 * Vercel deploys an Express app with no configuration, and to find the app it
 * looks for a file named `app`, `index` or `server` — at the project root or in
 * `src/`. This file must NOT be one of those names, or Vercel could pick it as
 * the entry point instead of `src/app.js`, and then:
 *   - the process would run the fail-fast sequence below inside a serverless
 *     function, where a database hiccup calls process.exit(1) and takes the
 *     whole function with it — every request would answer Vercel's bare
 *     "FUNCTION_INVOCATION_FAILED" instead of this API's own JSON error; and
 *   - it would be ambiguous which of the two files Vercel chose.
 * With this file named `start.js`, `src/app.js` is the ONLY recognised entry,
 * the connection opens lazily per instance (middleware/ensureDatabase.js), and
 * an unreachable database is reported as an honest 503 `degraded` on
 * `/api/health` rather than as a crash. Renaming this file back would undo that.
 */

const env = require('./config/env');
const logger = require('./utils/logger');
const { connect, close } = require('./database/connection');
const { ensureIndexes } = require('./database/indexes');
const app = require('./app');

async function start() {
  try {
    await connect();
    await ensureIndexes();
  } catch (error) {
    logger.error('Failed to connect to MongoDB. Server will not start.');
    logger.error(error.message);
    process.exit(1);
  }

  const server = app.listen(env.port, () => {
    logger.info(`RepRise API listening on http://localhost:${env.port}`);
    logger.info(`Health check:  http://localhost:${env.port}/api/health`);
  });

  // Graceful shutdown: stop accepting requests, close the DB, then exit.
  const shutdown = (signal) => {
    logger.info(`${signal} received — shutting down gracefully...`);
    server.close(async () => {
      await close();
      process.exit(0);
    });
  };

  ['SIGINT', 'SIGTERM'].forEach((signal) => process.on(signal, () => shutdown(signal)));
}

start();
