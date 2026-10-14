'use strict';

/**
 * MongoDB ObjectId helpers.
 *
 * Every collection that belongs to a user stores `userId` as an ObjectId, so
 * services need to turn the id string from the JWT into one. Doing that with a
 * bare `new ObjectId(...)` throws on malformed input — which, on a path like
 * `/api/workouts/not-an-id`, would surface as a confusing 500 instead of an
 * honest 404.
 */

const { ObjectId } = require('mongodb');

/**
 * Cast a value to an ObjectId, returning `null` instead of throwing when the
 * input is missing or malformed.
 *
 * Values that are already ObjectIds are passed straight through, so this is
 * safe to call on `req.user._id`.
 *
 * @param {string|import('mongodb').ObjectId|null|undefined} value
 * @returns {import('mongodb').ObjectId|null}
 */
function toObjectId(value) {
  if (!value) return null;
  try {
    return typeof value === 'string' ? new ObjectId(value) : value;
  } catch {
    return null;
  }
}

module.exports = { toObjectId };
