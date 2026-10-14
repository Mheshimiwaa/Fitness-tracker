'use strict';

/**
 * Progression controller — the progressive-overload assistant.
 * Every route here requires authentication (see progression.routes.js).
 *
 * Thin, like the other controllers: read the request, call the service, return
 * its shape. The rules live in progression.service.js.
 */

const progressionService = require('../services/progression.service');

/**
 * GET /api/progression?exercise=&limit=  ->  200 OK
 *
 * One exercise's recent sessions plus a rule-based verdict. An exercise that has
 * never been logged returns an empty session list and an
 * `insufficient_data` verdict rather than a 404 — the app can always render it.
 */
async function getProgression(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const { exercise, limit } = req.query;

  const progression = await progressionService.getExerciseProgression(
    req.user._id,
    exercise,
    preferredUnit,
    { limit }
  );

  res.json(progression);
}

/**
 * GET /api/progression/exercises  ->  200 OK
 *
 * Every exercise the user has logged, with how often and when last — the list
 * the Progress screen is built from.
 */
async function listExercises(req, res) {
  const exercises = await progressionService.listExerciseSummaries(req.user._id);
  res.json({ exercises });
}

module.exports = { getProgression, listExercises };
