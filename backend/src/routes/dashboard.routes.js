'use strict';

/**
 * Dashboard routes, mounted at /api/dashboard. Requires a valid token.
 *
 *   GET /api/dashboard   the Home tab's aggregated summary   -> 200
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const dashboardController = require('../controllers/dashboard.controller');

const router = express.Router();

router.use(authenticate);

router.get('/', dashboardController.getDashboard);

module.exports = router;
