'use strict';

/**
 * Index creation — run once at startup.
 *
 * Indexes make queries fast and enforce important rules:
 *   - A UNIQUE index on the normalised username stops two accounts from
 *     claiming the same username even under a race condition.
 *   - A UNIQUE index on the normalised email does the same for emails.
 *   - Compound indexes like { userId, measuredAt } make "my weight history
 *     newest first" queries efficient.
 *
 * Indexes are idempotent: running createIndex() twice is harmless.
 */

const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('./collections');
const logger = require('../utils/logger');

async function ensureIndexes() {
  const db = getDb();

  // ---- users -------------------------------------------------------------
  // Usernames/emails are stored twice: the raw value for display, and a
  // normalised (lower-cased, trimmed) value for case-insensitive uniqueness.
  await db
    .collection(COLLECTIONS.USERS)
    .createIndex({ usernameNormalized: 1 }, { unique: true, name: 'uniq_username' });
  await db
    .collection(COLLECTIONS.USERS)
    .createIndex({ emailNormalized: 1 }, { unique: true, name: 'uniq_email' });

  // ---- weight_entries ----------------------------------------------------
  // Main query: "all weight entries for user X, newest first".
  await db
    .collection(COLLECTIONS.WEIGHT_ENTRIES)
    .createIndex({ userId: 1, measuredAt: -1 }, { name: 'user_weight_recent' });

  // ---- workouts ----------------------------------------------------------
  // Main query: "workout history for user X, newest first".
  await db
    .collection(COLLECTIONS.WORKOUTS)
    .createIndex({ userId: 1, workoutDate: -1 }, { name: 'user_workout_recent' });
  // Per-exercise history ("every session that included Bench Press"), which
  // powers the progression advice in Phase 5. `exercises.name` is a multikey
  // index; a compound index may contain at most ONE array field, and this is it.
  await db
    .collection(COLLECTIONS.WORKOUTS)
    .createIndex(
      { userId: 1, 'exercises.name': 1, workoutDate: -1 },
      { name: 'user_exercise_recent' }
    );

  // ---- friendships -------------------------------------------------------
  // A friendship row is stored once, from the requester's perspective.
  // These indexes support "requests I sent / received" and "my friendships".
  await db
    .collection(COLLECTIONS.FRIENDSHIPS)
    .createIndex({ requesterId: 1, status: 1 }, { name: 'friendship_requester_status' });
  await db
    .collection(COLLECTIONS.FRIENDSHIPS)
    .createIndex({ recipientId: 1, status: 1 }, { name: 'friendship_recipient_status' });

  logger.info('MongoDB indexes ensured.');
}

module.exports = { ensureIndexes };
