'use strict';

/**
 * Weights controller — body-weight tracking CRUD.
 * Every route here requires authentication (see weights.routes.js).
 *
 * The controller layer is intentionally thin: validate → call the service →
 * shape the HTTP response. All the rules live in weight.service.js.
 */

const weightService = require('../services/weight.service');

/**
 * POST /api/weights  ->  201 Created
 * Body: { weight, unit?, measuredAt?, note? }
 */
async function createWeight(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const entry = await weightService.createEntry(req.user._id, req.body, preferredUnit);

  res.status(201).json({ entry: weightService.toPublicEntry(entry, preferredUnit) });
}

/**
 * GET /api/weights?days=&limit=&order=  ->  200 OK
 *
 * Returns the user's history in their CURRENT preferred unit, plus the change
 * across the returned window (null when there is nothing to compare).
 */
async function listWeights(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const { days, limit, order } = req.query;

  const history = await weightService.getHistory(req.user._id, preferredUnit, {
    days,
    limit,
    order,
  });

  res.json({
    entries: history.entries,
    count: history.entries.length,
    change: history.change,
    days: history.days,
  });
}

/** GET /api/weights/latest  ->  200 OK  (entry is null when nothing is logged) */
async function getLatestWeight(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  // limit 1, newest first.
  const [entry] = await weightService.listEntries(req.user._id, { limit: 1, order: 'desc' });

  res.json({
    entry: entry ? weightService.toPublicEntry(entry, preferredUnit) : null,
  });
}

/** GET /api/weights/:id  ->  200 OK */
async function getWeight(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const entry = await weightService.getEntryOrThrow(req.user._id, req.params.id);

  res.json({ entry: weightService.toPublicEntry(entry, preferredUnit) });
}

/** PATCH /api/weights/:id  ->  200 OK */
async function updateWeight(req, res) {
  const preferredUnit = req.user.weightUnit || 'kg';
  const entry = await weightService.updateEntry(req.user._id, req.params.id, req.body);

  res.json({ entry: weightService.toPublicEntry(entry, preferredUnit) });
}

/** DELETE /api/weights/:id  ->  204 No Content */
async function deleteWeight(req, res) {
  await weightService.deleteEntry(req.user._id, req.params.id);
  res.status(204).end();
}

module.exports = {
  createWeight,
  listWeights,
  getLatestWeight,
  getWeight,
  updateWeight,
  deleteWeight,
};
