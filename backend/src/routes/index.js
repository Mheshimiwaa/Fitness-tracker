'use strict';

/**
 * The API router. Every feature's routes get mounted under /api here.
 *
 * Phase 1: only /api/health exists.
 * Later phases will add:
 *   router.use('/auth', require('./auth.routes'));
 *   router.use('/users', require('./users.routes'));
 *   router.use('/weights', require('./weights.routes'));
 *   router.use('/workouts', require('./workouts.routes'));
 *   router.use('/friends', require('./friends.routes'));
 *   router.use('/leaderboard', require('./leaderboard.routes'));
 *   router.use('/dashboard', require('./dashboard.routes'));
 */

const express = require('express');

const router = express.Router();

router.use('/health', require('./health.routes'));

module.exports = router;
