'use strict';

/**
 * Leaderboard routes, mounted at /api/leaderboard. Requires a valid token.
 *
 *   GET /api/leaderboard/weekly   my weekly board (me + friends)   -> 200
 *
 * The week window and the scoring rules are computed on the server and returned
 * with the data; there are no query parameters to validate.
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const leaderboardController = require('../controllers/leaderboard.controller');

const router = express.Router();

router.use(authenticate);

router.get('/weekly', leaderboardController.getWeekly);

module.exports = router;
