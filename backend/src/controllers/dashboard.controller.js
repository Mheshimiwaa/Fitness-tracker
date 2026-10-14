'use strict';

/**
 * Dashboard controller — one aggregated payload for the Home tab.
 */

const dashboardService = require('../services/dashboard.service');

/** GET /api/dashboard  ->  200 OK */
async function getDashboard(req, res) {
  const dashboard = await dashboardService.getDashboard(req.user);
  res.json(dashboard);
}

module.exports = { getDashboard };
