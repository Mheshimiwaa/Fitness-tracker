'use strict';

/**
 * Workout routes, mounted at /api/workouts. All require a valid token.
 *
 *   POST   /api/workouts            log a session                  -> 201
 *   GET    /api/workouts            history (+ window)             -> 200
 *   GET    /api/workouts/exercises  distinct exercise names         -> 200
 *   GET    /api/workouts/:id        one workout                     -> 200
 *   PATCH  /api/workouts/:id        edit a workout                  -> 200
 *   DELETE /api/workouts/:id        delete a workout                -> 204
 *
 * Route order matters: `/exercises` is declared BEFORE `/:id`, otherwise
 * Express would treat the literal string "exercises" as an id.
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const { validate } = require('../middleware/validate');
const {
  createWorkoutSchema,
  updateWorkoutSchema,
  listWorkoutsQuerySchema,
  workoutIdParamSchema,
} = require('../validators/workout.validators');
const workoutsController = require('../controllers/workouts.controller');

const router = express.Router();

// Every route in this file requires authentication.
router.use(authenticate);

router.post('/', validate({ body: createWorkoutSchema }), workoutsController.createWorkout);
router.get('/', validate({ query: listWorkoutsQuerySchema }), workoutsController.listWorkouts);
router.get('/exercises', workoutsController.listExercises);

router.get(
  '/:id',
  validate({ params: workoutIdParamSchema }),
  workoutsController.getWorkout
);
router.patch(
  '/:id',
  validate({ params: workoutIdParamSchema, body: updateWorkoutSchema }),
  workoutsController.updateWorkout
);
router.delete(
  '/:id',
  validate({ params: workoutIdParamSchema }),
  workoutsController.deleteWorkout
);

module.exports = router;
