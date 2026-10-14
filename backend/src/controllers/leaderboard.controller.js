'use strict';

/**
 * Leaderboard controller.
 *
 * Thin, like the others: the authenticated user goes in, the service's payload
 * comes back out. The scoring rules live in leaderboard.service.js.
 */

const leaderboardService = require('../services/leaderboard.service');

/**
 * GET /api/leaderboard/weekly  ->  200 OK
 *
 * Returns the caller and their friends, ranked by this week's consistency
 * score, together with the week window and the scoring rules the numbers came
 * from — so the app can explain them without hard-coding them.
 */
async function getWeekly(req, res) {
  const leaderboard = await leaderboardService.weeklyLeaderboard(req.user._id);
  res.json(leaderboard);
}

module.exports = { getWeekly };
