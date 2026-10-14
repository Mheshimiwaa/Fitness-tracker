'use strict';

/**
 * Central place for MongoDB collection names.
 *
 * Using constants (instead of typing the string "users" everywhere) prevents
 * typos and makes it obvious which collections the app actually uses.
 */
const COLLECTIONS = Object.freeze({
  USERS: 'users',
  WEIGHT_ENTRIES: 'weight_entries',
  WORKOUTS: 'workouts',
  FRIENDSHIPS: 'friendships',
});

module.exports = { COLLECTIONS };
