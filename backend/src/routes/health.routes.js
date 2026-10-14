'use strict';

/**
 * Health-check route: GET /api/health
 *
 * Purpose (why this exists):
 *   1. Confirms the API process is running.
 *   2. Confirms the MongoDB connection is alive by pinging the database.
 *   3. Gives a single URL we can hit to prove everything is wired up.
 *
 * Response:
 *   200 when the database responds, 503 when it does not.
 */

const express = require('express');
const env = require('../config/env');
const { ping } = require('../database/connection');
const pkg = require('../../package.json');

const router = express.Router();

router.get('/', async (req, res) => {
  const databaseConnected = await ping();

  res.status(databaseConnected ? 200 : 503).json({
    status: databaseConnected ? 'ok' : 'degraded',
    service: 'reprise-backend',
    version: pkg.version,
    environment: env.nodeEnv,
    timestamp: new Date().toISOString(),
    uptimeSeconds: Math.round(process.uptime()),
    database: {
      connected: databaseConnected,
      name: env.mongoDbName,
    },
  });
});

module.exports = router;
