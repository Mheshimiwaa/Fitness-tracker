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

  // ---- photos ------------------------------------------------------------
  // A photo's `_id` IS its file name (`<24-hex userId>-<16-hex random>.<ext>`),
  // and the name is unique by construction — so MongoDB's built-in unique index
  // on `_id` already serves the only hot query there is: "give me this photo",
  // as a single point lookup. No extra index can improve on that.
  //
  // The one index worth adding is by owner, for the queries that sweep a user's
  // whole set rather than one photo at a time (deleting an account's pictures,
  // and the test suite's cleanup between runs).
  await db
    .collection(COLLECTIONS.PHOTOS)
    .createIndex({ userId: 1 }, { name: 'user_photos' });

  // ---- friendships -------------------------------------------------------
  // A friendship row is stored once, from the requester's perspective.
  // These indexes support "requests I sent / received" and "my friendships".
  await db
    .collection(COLLECTIONS.FRIENDSHIPS)
    .createIndex({ requesterId: 1, status: 1 }, { name: 'friendship_requester_status' });
  await db
    .collection(COLLECTIONS.FRIENDSHIPS)
    .createIndex({ recipientId: 1, status: 1 }, { name: 'friendship_recipient_status' });
  // Exactly ONE row per PAIR, enforced. `pairKey` is the two user ids sorted and
  // joined, so it is identical for A→B and B→A; the unique index is what makes a
  // duplicate friendship impossible even if two requests arrive at the same
  // instant and both pass the service's "does it already exist?" check.
  await db
    .collection(COLLECTIONS.FRIENDSHIPS)
    .createIndex({ pairKey: 1 }, { unique: true, name: 'uniq_friendship_pair' });

  logger.info('MongoDB indexes ensured.');
}

module.exports = { ensureIndexes };
