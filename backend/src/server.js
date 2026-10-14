'use strict';

/**
 * Entry point.
 *
 * Startup order matters:
 *   1. Connect to MongoDB (fail fast if this does not work).
 *   2. Ensure indexes exist.
 *   3. Start listening for HTTP requests.
 *
 * This ordering means the API never accepts a request while the database is
 * unreachable — so a failed write is reported honestly instead of being faked.
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
