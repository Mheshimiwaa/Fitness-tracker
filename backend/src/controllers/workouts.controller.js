'use strict';

/**
 * Workouts controller — training-session CRUD.
 * Every route here requires authentication (see workouts.routes.js).
 *
 * The controller layer is intentionally thin: validate → call the service →
 * shape the HTTP response. All the rules live in workout.service.js.
 */

const workoutService = require('../services/workout.service');

/**
 * POST /api/workouts  ->  201 Created
 * Body: { name, workoutDate?, durationMinutes?, notes?, exercises[] }
 */
async function createWorkout(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const workout = await workoutService.createWorkout(
    req.user._id,
    req.body,
    preferredUnit
  );

  res
    .status(201)
    .json({ workout: workoutService.toPublicWorkout(workout, preferredUnit) });
}

/**
 * GET /api/workouts?days=&limit=&order=  ->  200 OK
 *
 * Returns the user's sessions newest first.
 */
async function listWorkouts(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const { days, limit, order } = req.query;

  const history = await workoutService.getHistory(req.user._id, preferredUnit, {
    days,
    limit,
    order,
  });

  res.json({
    workouts: history.workouts,
    count: history.workouts.length,
    days: history.days,
  });
}

/**
 * GET /api/workouts/exercises  ->  200 OK
 *
 * The distinct exercise names this user has logged before, alphabetically —
 * used for the editor's suggestion chips.
 */
async function listExercises(req, res) {
  const exercises = await workoutService.listExerciseNames(req.user._id);
  res.json({ exercises });
}

/** GET /api/workouts/:id  ->  200 OK */
async function getWorkout(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const workout = await workoutService.getWorkoutOrThrow(req.user._id, req.params.id);

  res.json({ workout: workoutService.toPublicWorkout(workout, preferredUnit) });
}

/** PATCH /api/workouts/:id  ->  200 OK */
async function updateWorkout(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const workout = await workoutService.updateWorkout(
    req.user._id,
    req.params.id,
    req.body,
    preferredUnit
  );

  res.json({ workout: workoutService.toPublicWorkout(workout, preferredUnit) });
}

/** DELETE /api/workouts/:id  ->  204 No Content */
async function deleteWorkout(req, res) {
  await workoutService.deleteWorkout(req.user._id, req.params.id);
  res.status(204).end();
}

module.exports = {
  createWorkout,
  listWorkouts,
  listExercises,
  getWorkout,
  updateWorkout,
  deleteWorkout,
};
