'use strict';

/**
 * Database helpers for tests.
 *
 * Tests use the "reprise_test" database (see helpers/setup.js). Each test file
 * cleans up the users it created by deleting documents whose username starts
 * with a known prefix, which keeps runs idempotent and works even when test
 * files run in parallel processes.
 */

require('./setup');

const { ObjectId } = require('mongodb');
const { connect, close, getDb } = require('../../src/database/connection');
const { COLLECTIONS } = require('../../src/database/collections');

/**
 * Connect to MongoDB. Returns true on success, false when the database is
 * unreachable (so tests can skip instead of failing on a machine with no
 * network).
 */
async function tryConnect() {
  try {
    await connect();
    return true;
  } catch (error) {
    console.warn(`[tests] MongoDB not reachable, DB tests will be skipped: ${error.message}`);
    return false;
  }
}

/** Delete every test user whose normalised username starts with `prefix`. */
async function deleteUsersByPrefix(prefix) {
  const db = getDb();
  return db
    .collection(COLLECTIONS.USERS)
    .deleteMany({ usernameNormalized: { $regex: `^${prefix}` } });
}

/**
 * Delete every test user whose normalised username starts with `prefix`, ALONG
 * WITH everything that points at them (weight entries, workouts, friendships).
 *
 * Tests that exercise the user-owned collections must use this one instead of
 * `deleteUsersByPrefix`, otherwise orphaned rows pile up in `reprise_test` run
 * after run.
 *
 * @returns {Promise<Record<string, number>>} documents deleted, per collection.
 */
async function deleteUserDataByPrefix(prefix) {
  const db = getDb();

  const userDocs = await db
    .collection(COLLECTIONS.USERS)
    .find(
      { usernameNormalized: { $regex: `^${prefix}` } },
      { projection: { _id: 1 } }
    )
    .toArray();
  const userIds = userDocs.map((user) => user._id);

  if (userIds.length === 0) {
    return { [COLLECTIONS.USERS]: 0 };
  }

  const deleted = {};
  for (const collection of [COLLECTIONS.WEIGHT_ENTRIES, COLLECTIONS.WORKOUTS]) {
    deleted[collection] = (
      await db.collection(collection).deleteMany({ userId: { $in: userIds } })
    ).deletedCount;
  }
  deleted[COLLECTIONS.FRIENDSHIPS] = (
    await db.collection(COLLECTIONS.FRIENDSHIPS).deleteMany({
      $or: [{ requesterId: { $in: userIds } }, { recipientId: { $in: userIds } }],
    })
  ).deletedCount;
  deleted[COLLECTIONS.USERS] = (
    await db.collection(COLLECTIONS.USERS).deleteMany({ _id: { $in: userIds } })
  ).deletedCount;

  return deleted;
}

/** Count a user's weight entries straight from the database (for assertions). */
async function countWeightEntries(userId) {
  const db = getDb();
  return db
    .collection(COLLECTIONS.WEIGHT_ENTRIES)
    .countDocuments({ userId: new ObjectId(userId) });
}

/** Count a user's workouts straight from the database (for assertions). */
async function countWorkouts(userId) {
  const db = getDb();
  return db
    .collection(COLLECTIONS.WORKOUTS)
    .countDocuments({ userId: new ObjectId(userId) });
}

/** Read one user document straight from the database (for assertions). */
async function findUserDoc(usernameNormalized) {
  const db = getDb();
  return db.collection(COLLECTIONS.USERS).findOne({ usernameNormalized });
}

module.exports = {
  tryConnect,
  deleteUsersByPrefix,
  deleteUserDataByPrefix,
  countWeightEntries,
  countWorkouts,
  findUserDoc,
  close,
};
