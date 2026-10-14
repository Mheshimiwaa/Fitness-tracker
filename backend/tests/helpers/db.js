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

/** Read one user document straight from the database (for assertions). */
async function findUserDoc(usernameNormalized) {
  const db = getDb();
  return db.collection(COLLECTIONS.USERS).findOne({ usernameNormalized });
}

module.exports = { tryConnect, deleteUsersByPrefix, findUserDoc, close };
