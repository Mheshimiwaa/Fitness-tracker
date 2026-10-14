'use strict';

/**
 * Progression routes, mounted at /api/progression. All require a valid token.
 *
 *   GET /api/progression/exercises        exercises I have logged  -> 200
 *   GET /api/progression?exercise=&limit= one exercise's trend     -> 200
 *
 * Route order matters: `/exercises` is declared BEFORE `/`, so it is never a
 * (missing) query parameter of the plain route.
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const { validate } = require('../middleware/validate');
const { progressionQuerySchema } = require('../validators/progression.validators');
const progressionController = require('../controllers/progression.controller');

const router = express.Router();

// Every route in this file requires authentication.
router.use(authenticate);

router.get('/exercises', progressionController.listExercises);
router.get('/', validate({ query: progressionQuerySchema }), progressionController.getProgression);

module.exports = router;
