'use strict';

/**
 * The API router. Every feature's routes get mounted under /api here.
 *
 * Phase 1: /api/health
 * Phase 2: /api/auth, /api/users
 * Phase 3: /api/weights, /api/dashboard
 * Phase 4: /api/workouts
 * Phase 5: /api/progression
 *
 * Later phases will add:
 *   router.use('/friends', require('./friends.routes'));
 *   router.use('/leaderboard', require('./leaderboard.routes'));
 */

const express = require('express');
const { apiLimiter } = require('../middleware/rateLimiter');

const router = express.Router();

// A broad limiter across the whole API, on top of the tighter auth limiter.
router.use(apiLimiter);

router.use('/health', require('./health.routes'));
router.use('/auth', require('./auth.routes'));
router.use('/users', require('./users.routes'));
router.use('/weights', require('./weights.routes'));
router.use('/workouts', require('./workouts.routes'));
router.use('/progression', require('./progression.routes'));
router.use('/dashboard', require('./dashboard.routes'));

module.exports = router;
