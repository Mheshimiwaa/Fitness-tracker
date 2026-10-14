'use strict';

/**
 * Weight service — all database access and business rules for body-weight
 * entries.
 *
 * Design notes
 * ------------
 * 1. An entry stores the value and unit EXACTLY as the user entered it. Nobody
 *    is forced to convert their own data, and changing the preferred unit later
 *    does not rewrite history.
 * 2. Reads convert to the user's CURRENT preferred unit, so the app can render
 *    a consistent chart without any client-side maths.
 * 3. Ownership is enforced in the query itself (`{ _id, userId }`), never by
 *    fetching a document and comparing afterwards. A missing document and
 *    somebody else's document are deliberately indistinguishable — both are a
 *    404 — so the API cannot be used to probe which ids exist.
 */

const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('../database/collections');
const ApiError = require('../utils/ApiError');
const { toObjectId } = require('../utils/objectId');
const { toKilograms, fromKilograms, round } = require('../utils/units');
const {
  WEIGHT_KG_MIN,
  WEIGHT_KG_MAX,
  WEIGHT_LB_MIN,
  WEIGHT_LB_MAX,
  MS_PER_DAY,
} = require('../config/constants');

const DEFAULT_HISTORY_LIMIT = 100;
const MAX_HISTORY_LIMIT = 500;
const DEFAULT_CHANGE_WINDOW_DAYS = 30;

/** The weight_entries collection. Called lazily so the DB is connected first. */
function weightEntries() {
  return getDb().collection(COLLECTIONS.WEIGHT_ENTRIES);
}

/**
 * Enforce the plausible range for the entry's unit.
 *
 * This runs in the SERVICE rather than in Zod because the unit is often not in
 * the request body at all — it is defaulted from the user's saved preference.
 */
function assertWeightInRange(weight, unit) {
  const [min, max] = unit === 'lb' ? [WEIGHT_LB_MIN, WEIGHT_LB_MAX] : [WEIGHT_KG_MIN, WEIGHT_KG_MAX];
  if (weight < min || weight > max) {
    const message = `Weight must be between ${min} and ${max} ${unit}.`;
    // `details` mirrors the Zod issue shape so the app highlights the field.
    throw ApiError.badRequest(message, [{ path: 'weight', message }]);
  }
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------

/**
 * Create a weight entry.
 *
 * @param {string} userId
 * @param {{ weight: number, unit?: string, measuredAt?: Date, note?: string }} data
 * @param {string} preferredUnit the user's saved preference, used when `unit` is omitted
 */
async function createEntry(userId, data, preferredUnit) {
  const unit = data.unit || preferredUnit || 'kg';
  assertWeightInRange(data.weight, unit);

  const now = new Date();
  const document = {
    userId: toObjectId(userId),
    weight: data.weight,
    unit,
    measuredAt: data.measuredAt || now,
    note: data.note ?? null,
    createdAt: now,
    updatedAt: now,
  };

  const result = await weightEntries().insertOne(document);
  return { ...document, _id: result.insertedId };
}

/**
 * Apply a partial update, returning the UPDATED document.
 *
 * The final weight/unit combination is re-validated, because changing only the
 * unit can push an existing value out of the plausible range.
 */
async function updateEntry(userId, id, changes) {
  const entryId = toObjectId(id);
  if (!entryId) throw ApiError.notFound('Weight entry not found.');

  const filter = { _id: entryId, userId: toObjectId(userId) };

  // Read first so we can validate the merged result.
  const existing = await weightEntries().findOne(filter);
  if (!existing) throw ApiError.notFound('Weight entry not found.');

  const fields = {};
  if (changes.weight !== undefined) fields.weight = changes.weight;
  if (changes.unit !== undefined) fields.unit = changes.unit;
  if (changes.measuredAt !== undefined) fields.measuredAt = changes.measuredAt;
  if (changes.note !== undefined) fields.note = changes.note;

  assertWeightInRange(
    fields.weight ?? existing.weight,
    fields.unit ?? existing.unit
  );

  fields.updatedAt = new Date();

  const updated = await weightEntries().findOneAndUpdate(
    filter,
    { $set: fields },
    { returnDocument: 'after' }
  );

  if (!updated) throw ApiError.notFound('Weight entry not found.');
  return updated;
}

/** Delete an entry owned by the user. */
async function deleteEntry(userId, id) {
  const entryId = toObjectId(id);
  if (!entryId) throw ApiError.notFound('Weight entry not found.');

  const result = await weightEntries().deleteOne({
    _id: entryId,
    userId: toObjectId(userId),
  });

  if (result.deletedCount === 0) {
    throw ApiError.notFound('Weight entry not found.');
  }
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

/**
 * Fetch the user's history, newest first.
 *
 * @param {string} userId
 * @param {{ days?: number, limit?: number, order?: 'asc'|'desc' }} [options]
 */
async function listEntries(userId, options = {}) {
  const { days, limit = DEFAULT_HISTORY_LIMIT, order = 'desc' } = options;

  const filter = { userId: toObjectId(userId) };
  if (days) {
    filter.measuredAt = { $gte: new Date(Date.now() - days * MS_PER_DAY) };
  }

  return weightEntries()
    .find(filter)
    .sort({ measuredAt: order === 'asc' ? 1 : -1 })
    .limit(Math.min(limit, MAX_HISTORY_LIMIT))
    .toArray();
}

/** Find one entry (owned by the user) or return null. */
async function findEntryById(userId, id) {
  const entryId = toObjectId(id);
  if (!entryId) return null;
  return weightEntries().findOne({ _id: entryId, userId: toObjectId(userId) });
}

/** Find one entry or throw a 404. */
async function getEntryOrThrow(userId, id) {
  const entry = await findEntryById(userId, id);
  if (!entry) throw ApiError.notFound('Weight entry not found.');
  return entry;
}

// ---------------------------------------------------------------------------
// Serialisation
// ---------------------------------------------------------------------------

/**
 * Convert a stored entry into the shape the API returns.
 *
 * `weight`/`unit` are what the app should DISPLAY (converted from whatever was
 * stored into the user's current preference); `weightKg` is the canonical value
 * used for BMI and any cross-unit maths.
 */
function toPublicEntry(entry, preferredUnit) {
  const weightKg = toKilograms(entry.weight, entry.unit);

  return {
    id: String(entry._id),
    weight: round(fromKilograms(weightKg, preferredUnit), 1),
    unit: preferredUnit,
    weightKg: round(weightKg, 2),
    measuredAt: entry.measuredAt,
    note: entry.note ?? null,
    createdAt: entry.createdAt,
    updatedAt: entry.updatedAt,
  };
}

/**
 * Compute the change across a list of public entries (newest first).
 *
 * Returns null when there are fewer than two entries to compare — an honest
 * "not enough data yet" rather than a misleading zero.
 */
function computeChange(entries, preferredUnit) {
  if (entries.length < 2) return null;

  const newest = entries[0];
  const oldest = entries[entries.length - 1];

  return {
    display: round(newest.weight - oldest.weight, 1),
    unit: preferredUnit,
    kg: round(newest.weightKg - oldest.weightKg, 2),
    from: { weight: oldest.weight, measuredAt: oldest.measuredAt },
    to: { weight: newest.weight, measuredAt: newest.measuredAt },
  };
}

/**
 * The history endpoint's payload: the entries plus the change across them.
 *
 * `days` (the requested window) is echoed back so the UI can label the number
 * correctly ("-2.4 kg this month").
 */
async function getHistory(userId, preferredUnit, options = {}) {
  const docs = await listEntries(userId, options);
  const entries = docs.map((doc) => toPublicEntry(doc, preferredUnit));

  return {
    entries,
    change: computeChange(entries, preferredUnit),
    days: options.days ?? null,
  };
}

/**
 * Summary used by the dashboard: the latest reading, the one before it, the
 * total number of entries, and the change over the default window.
 */
async function getSummary(userId, preferredUnit, options = {}) {
  const { days = DEFAULT_CHANGE_WINDOW_DAYS } = options;

  const allDocs = await listEntries(userId, { limit: MAX_HISTORY_LIMIT });
  const all = allDocs.map((doc) => toPublicEntry(doc, preferredUnit));

  const cutoff = Date.now() - days * MS_PER_DAY;
  const inWindow = all.filter(
    (entry) => new Date(entry.measuredAt).getTime() >= cutoff
  );

  return {
    latest: all[0] ?? null,
    previous: all[1] ?? null,
    entryCount: all.length,
    changeWindowDays: days,
    // null (not a misleading 0) when the window holds fewer than two readings.
    change: computeChange(inWindow, preferredUnit),
  };
}

module.exports = {
  createEntry,
  updateEntry,
  deleteEntry,
  listEntries,
  findEntryById,
  getEntryOrThrow,
  toPublicEntry,
  getHistory,
  getSummary,
};
