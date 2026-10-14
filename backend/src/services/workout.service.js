'use strict';

/**
 * Workout service — all database access and business rules for logged training
 * sessions.
 *
 * Design notes
 * ------------
 * 1. A workout is a small tree: workout -> exercises -> sets. Each SET is
 *    `{ reps, weight }`, and the weight belongs to the EXERCISE's unit. Nothing
 *    is flattened into a "total volume" column on write: totals are derived
 *    (`summariseWorkout`) so there is only ever one copy of the truth.
 * 2. Reads convert every set's weight into the user's CURRENT preferred unit
 *    and also expose the canonical `weightKg`, exactly like weight entries. So
 *    switching kg <-> lb never rewrites history and never changes a comparison.
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
  SET_WEIGHT_KG_MAX,
  SET_WEIGHT_LB_MAX,
  WORKOUT_RECENT_DAYS,
} = require('../config/constants');

const DEFAULT_LIST_LIMIT = 50;
const MAX_LIST_LIMIT = 200;
const MS_PER_DAY = 24 * 60 * 60 * 1000;

/** The workouts collection. Called lazily so the DB is connected first. */
function workouts() {
  return getDb().collection(COLLECTIONS.WORKOUTS);
}

/**
 * Enforce the plausible load range for each set, per the exercise's unit.
 *
 * This runs in the SERVICE rather than in Zod because the unit is often not in
 * the request body at all — it is defaulted from the user's saved preference.
 * The error names the exact offending set so the app can highlight that row.
 */
function assertSetsInRange(exercises, preferredUnit) {
  exercises.forEach((exercise, exerciseIndex) => {
    const unit = exercise.unit || preferredUnit || 'kg';
    const max = unit === 'lb' ? SET_WEIGHT_LB_MAX : SET_WEIGHT_KG_MAX;

    exercise.sets.forEach((set, setIndex) => {
      if (set.weight > max) {
        const message = `Weight must be at most ${max} ${unit}.`;
        throw ApiError.badRequest(message, [
          { path: `exercises.${exerciseIndex}.sets.${setIndex}.weight`, message },
        ]);
      }
    });
  });
}

/** Fill in the defaults and drop anything the client did not send. */
function normaliseExercises(exercises, preferredUnit) {
  return exercises.map((exercise) => ({
    name: exercise.name,
    unit: exercise.unit || preferredUnit || 'kg',
    restSeconds: exercise.restSeconds ?? null,
    notes: exercise.notes ?? null,
    sets: exercise.sets.map((set) => ({ reps: set.reps, weight: set.weight })),
  }));
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------

/**
 * Create a workout.
 *
 * @param {string} userId
 * @param {object} data validated request body
 * @param {string} preferredUnit the user's saved preference, used when an
 *   exercise omits its unit
 */
async function createWorkout(userId, data, preferredUnit) {
  assertSetsInRange(data.exercises, preferredUnit);

  const now = new Date();
  const document = {
    userId: toObjectId(userId),
    name: data.name,
    workoutDate: data.workoutDate || now,
    durationMinutes: data.durationMinutes ?? null,
    notes: data.notes ?? null,
    exercises: normaliseExercises(data.exercises, preferredUnit),
    createdAt: now,
    updatedAt: now,
  };

  const result = await workouts().insertOne(document);
  return { ...document, _id: result.insertedId };
}

/**
 * Apply a partial update, returning the UPDATED document.
 *
 * `exercises`, when present, replaces the whole list — re-validated as a set
 * because the unit may have changed.
 */
async function updateWorkout(userId, id, changes, preferredUnit) {
  const workoutId = toObjectId(id);
  if (!workoutId) throw ApiError.notFound('Workout not found.');

  const filter = { _id: workoutId, userId: toObjectId(userId) };

  const existing = await workouts().findOne(filter);
  if (!existing) throw ApiError.notFound('Workout not found.');

  const fields = {};
  if (changes.name !== undefined) fields.name = changes.name;
  if (changes.workoutDate !== undefined) fields.workoutDate = changes.workoutDate;
  if (changes.durationMinutes !== undefined) {
    fields.durationMinutes = changes.durationMinutes;
  }
  if (changes.notes !== undefined) fields.notes = changes.notes;
  if (changes.exercises !== undefined) {
    assertSetsInRange(changes.exercises, preferredUnit);
    fields.exercises = normaliseExercises(changes.exercises, preferredUnit);
  }

  fields.updatedAt = new Date();

  const updated = await workouts().findOneAndUpdate(
    filter,
    { $set: fields },
    { returnDocument: 'after' }
  );

  if (!updated) throw ApiError.notFound('Workout not found.');
  return updated;
}

/** Delete a workout owned by the user. */
async function deleteWorkout(userId, id) {
  const workoutId = toObjectId(id);
  if (!workoutId) throw ApiError.notFound('Workout not found.');

  const result = await workouts().deleteOne({
    _id: workoutId,
    userId: toObjectId(userId),
  });

  if (result.deletedCount === 0) {
    throw ApiError.notFound('Workout not found.');
  }
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

/**
 * Fetch the user's workouts, newest first.
 *
 * @param {string} userId
 * @param {{ days?: number, limit?: number, order?: 'asc'|'desc' }} [options]
 */
async function listWorkouts(userId, options = {}) {
  const { days, limit = DEFAULT_LIST_LIMIT, order = 'desc' } = options;

  const filter = { userId: toObjectId(userId) };
  if (days) {
    filter.workoutDate = { $gte: new Date(Date.now() - days * MS_PER_DAY) };
  }

  return workouts()
    .find(filter)
    .sort({ workoutDate: order === 'asc' ? 1 : -1 })
    .limit(Math.min(limit, MAX_LIST_LIMIT))
    .toArray();
}

/** Find one workout (owned by the user) or return null. */
async function findWorkoutById(userId, id) {
  const workoutId = toObjectId(id);
  if (!workoutId) return null;
  return workouts().findOne({ _id: workoutId, userId: toObjectId(userId) });
}

/** Find one workout or throw a 404. */
async function getWorkoutOrThrow(userId, id) {
  const workout = await findWorkoutById(userId, id);
  if (!workout) throw ApiError.notFound('Workout not found.');
  return workout;
}

/**
 * The distinct exercise names this user has ever logged, alphabetically.
 *
 * Powers the "exercises you have done before" suggestion chips in the editor.
 * A plain `distinct` is enough here — it is per-user, so the working set is
 * small, and it cannot go stale the way a denormalised list would.
 */
async function listExerciseNames(userId) {
  const names = await workouts().distinct('exercises.name', {
    userId: toObjectId(userId),
  });

  return names
    .filter((name) => typeof name === 'string' && name.trim() !== '')
    .sort((a, b) => a.localeCompare(b));
}

// ---------------------------------------------------------------------------
// Serialisation and derived numbers
// ---------------------------------------------------------------------------

/**
 * Derive the headline numbers for a stored workout.
 *
 * `totalVolumeKg` (a.k.a. tonnage) is the sum of `weight x reps` over every set,
 * expressed in kilograms so two workouts are comparable even if one was logged
 * in pounds.
 */
function summariseWorkout(workout) {
  let setCount = 0;
  let totalReps = 0;
  let totalVolumeKg = 0;

  for (const exercise of workout.exercises) {
    setCount += exercise.sets.length;
    for (const set of exercise.sets) {
      totalReps += set.reps;
      totalVolumeKg += toKilograms(set.weight, exercise.unit) * set.reps;
    }
  }

  return {
    exerciseCount: workout.exercises.length,
    setCount,
    totalReps,
    totalVolumeKg: round(totalVolumeKg, 1),
  };
}

/**
 * Convert a stored workout into the shape the API returns.
 *
 * Every set carries `weight` in the user's current preferred unit for display,
 * AND `weightKg` as the canonical value behind it.
 */
function toPublicWorkout(workout, preferredUnit) {
  return {
    id: String(workout._id),
    name: workout.name,
    workoutDate: workout.workoutDate,
    durationMinutes: workout.durationMinutes ?? null,
    notes: workout.notes ?? null,
    exercises: workout.exercises.map((exercise) => ({
      name: exercise.name,
      unit: preferredUnit,
      restSeconds: exercise.restSeconds ?? null,
      notes: exercise.notes ?? null,
      sets: exercise.sets.map((set) => {
        const weightKg = toKilograms(set.weight, exercise.unit);
        return {
          reps: set.reps,
          weight: round(fromKilograms(weightKg, preferredUnit), 1),
          weightKg: round(weightKg, 2),
        };
      }),
    })),
    summary: summariseWorkout(workout),
    createdAt: workout.createdAt,
    updatedAt: workout.updatedAt,
  };
}

/** The list endpoint's payload: the workouts plus the requested window. */
async function getHistory(userId, preferredUnit, options = {}) {
  const docs = await listWorkouts(userId, options);

  return {
    workouts: docs.map((doc) => toPublicWorkout(doc, preferredUnit)),
    days: options.days ?? null,
  };
}

/**
 * Summary used by the dashboard: how much the user has trained recently.
 *
 * "Recently" is a rolling window (`WORKOUT_RECENT_DAYS`), not a calendar week —
 * a Monday-morning dashboard should not claim zero workouts just because the
 * week has only just begun.
 */
async function getRecentSummary(userId, options = {}) {
  const { days = WORKOUT_RECENT_DAYS } = options;
  const owner = toObjectId(userId);

  const cutoff = new Date(Date.now() - days * MS_PER_DAY);

  const [totalCount, recent, latestList] = await Promise.all([
    workouts().countDocuments({ userId: owner }),
    workouts().find({ userId: owner, workoutDate: { $gte: cutoff } }).toArray(),
    workouts().find({ userId: owner }).sort({ workoutDate: -1 }).limit(1).toArray(),
  ]);

  const recentVolumeKg = recent.reduce(
    (sum, workout) => sum + summariseWorkout(workout).totalVolumeKg,
    0
  );

  return {
    totalCount,
    recentDays: days,
    recentCount: recent.length,
    recentVolumeKg: round(recentVolumeKg, 1),
    lastWorkoutAt: latestList[0] ? latestList[0].workoutDate : null,
  };
}

module.exports = {
  createWorkout,
  updateWorkout,
  deleteWorkout,
  listWorkouts,
  findWorkoutById,
  getWorkoutOrThrow,
  listExerciseNames,
  summariseWorkout,
  toPublicWorkout,
  getHistory,
  getRecentSummary,
};
