'use strict';

/**
 * User service — all database access and business rules for user documents.
 *
 * Why a service layer instead of talking to MongoDB inside controllers?
 *   - One place knows how a user document is shaped.
 *   - Controllers stay tiny and only deal with HTTP.
 *   - The rules (normalisation, uniqueness, what is public) can be unit-tested
 *     without spinning up an HTTP server.
 *
 * CommonJS note: this file exports an object of functions.
 */

const { ObjectId } = require('mongodb');
const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('../database/collections');
const ApiError = require('../utils/ApiError');

/** The users collection. Called lazily so the DB is connected first. */
function users() {
  return getDb().collection(COLLECTIONS.USERS);
}

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

/**
 * Usernames are intentionally lower-case only (the validator enforces it), so
 * this is a safety net that also keeps the unique index authoritative.
 */
function normalizeUsername(username) {
  return String(username).trim().toLowerCase();
}

/**
 * Emails are case-insensitive in practice: "Alex@Example.com" and
 * "alex@example.com" must not create two accounts. We store the typed value for
 * display and this lower-cased value for the unique index.
 */
function normalizeEmail(email) {
  return String(email).trim().toLowerCase();
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

/**
 * Find a user by their _id string or ObjectId.
 * @returns {Promise<object|null>}
 */
async function findUserById(id) {
  if (!id) return null;
  // Casting a malformed id would throw; treat that as "not found" instead.
  let objectId;
  try {
    objectId = typeof id === 'string' ? new ObjectId(id) : id;
  } catch {
    return null;
  }
  return users().findOne({ _id: objectId });
}

/** Find a user by normalised username. */
async function findUserByUsername(username) {
  return users().findOne({ usernameNormalized: normalizeUsername(username) });
}

/** Find a user by normalised email. */
async function findUserByEmail(email) {
  return users().findOne({ emailNormalized: normalizeEmail(email) });
}

/**
 * Look up a user by username OR email. Handy for login, where the user may type
 * either one, and for "is this value already taken?" checks.
 */
async function findUserByUsernameOrEmail(identifier) {
  const value = String(identifier).trim().toLowerCase();
  return users().findOne({
    $or: [{ usernameNormalized: value }, { emailNormalized: value }],
  });
}

/**
 * Search for users whose username starts with the given text.
 *
 * Prefix search (not "contains") so the unique username index can be used, and
 * so results feel predictable. The caller's own id can be excluded.
 *
 * @param {string} term lower-cased search text
 * @param {{ excludeUserId?: string, limit?: number }} [options]
 */
async function searchUsersByUsername(term, options = {}) {
  const { excludeUserId, limit = 20 } = options;
  const escaped = String(term).trim().toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

  const filter = { usernameNormalized: { $regex: `^${escaped}` } };
  if (excludeUserId) {
    const excludeId = new ObjectId(excludeUserId);
    filter._id = { $ne: excludeId };
  }

  return users()
    .find(filter, { projection: { username: 1, displayName: 1, profileImageUrl: 1 } })
    .sort({ usernameNormalized: 1 })
    .limit(limit)
    .toArray();
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------

/**
 * Insert a brand-new user.
 *
 * The unique indexes on usernameNormalized/emailNormalized are the real guard
 * against duplicates: even if two requests race past our pre-checks, MongoDB
 * rejects the second insert with error code 11000, which the central error
 * handler converts into a 409.
 *
 * @param {{ displayName: string, username: string, email: string, passwordHash: string }} data
 * @returns {Promise<object>} the created user document
 */
async function createUser(data) {
  const now = new Date();
  const document = {
    displayName: data.displayName,
    username: normalizeUsername(data.username),
    usernameNormalized: normalizeUsername(data.username),
    email: data.email.trim(),
    emailNormalized: normalizeEmail(data.email),
    passwordHash: data.passwordHash,
    profileImageUrl: null,
    fitnessGoal: 'maintain',
    weightUnit: 'kg',
    heightCm: null,
    createdAt: now,
    updatedAt: now,
  };

  const result = await users().insertOne(document);
  return { ...document, _id: result.insertedId };
}

/**
 * Apply a partial profile update.
 *
 * Only the keys present in `changes` are written, and `updatedAt` is always
 * refreshed. Returns the UPDATED document.
 *
 * @param {string} userId
 * @param {object} changes already validated by Zod
 */
async function updateUserProfile(userId, changes) {
  const fields = {};

  if (changes.displayName !== undefined) fields.displayName = changes.displayName;
  if (changes.username !== undefined) {
    fields.username = normalizeUsername(changes.username);
    fields.usernameNormalized = normalizeUsername(changes.username);
  }
  if (changes.fitnessGoal !== undefined) fields.fitnessGoal = changes.fitnessGoal;
  if (changes.weightUnit !== undefined) fields.weightUnit = changes.weightUnit;
  if (changes.heightCm !== undefined) fields.heightCm = changes.heightCm;
  if (changes.profileImageUrl !== undefined) fields.profileImageUrl = changes.profileImageUrl;

  fields.updatedAt = new Date();

  const result = await users().findOneAndUpdate(
    { _id: new ObjectId(userId) },
    { $set: fields },
    { returnDocument: 'after' }
  );

  if (!result) {
    throw ApiError.notFound('User not found.');
  }
  return result;
}

/**
 * Point the user's profile image at `url`, or at nothing when `url` is null.
 *
 * Returns BOTH documents the caller needs:
 *   - `previous`, so the photo being replaced can be deleted from disk, and
 *   - `updated`, so the API can answer with the user's new state.
 *
 * It reads the old value and writes the new one in a SINGLE atomic update
 * (`returnDocument: 'before'`), so a photo can never be orphaned by two
 * uploads racing: each one learns exactly which file it replaced.
 *
 * @param {string} userId
 * @param {string|null} url
 */
async function setProfileImageUrl(userId, url) {
  const updatedAt = new Date();

  const previous = await users().findOneAndUpdate(
    { _id: new ObjectId(userId) },
    { $set: { profileImageUrl: url, updatedAt } },
    { returnDocument: 'before' }
  );

  if (!previous) {
    throw ApiError.notFound('User not found.');
  }

  return { previous, updated: { ...previous, profileImageUrl: url, updatedAt } };
}

// ---------------------------------------------------------------------------
// Serialisation
// ---------------------------------------------------------------------------
/**
 * Convert a stored user document into the shape the API returns.
 *
 * This is the ONLY way a user leaves the server, so `passwordHash` can never
 * leak by accident — we build a fresh object with just the safe fields.
 */
function toPublicUser(user) {
  return {
    id: String(user._id),
    displayName: user.displayName,
    username: user.username,
    email: user.email,
    profileImageUrl: user.profileImageUrl ?? null,
    fitnessGoal: user.fitnessGoal,
    weightUnit: user.weightUnit,
    heightCm: user.heightCm ?? null,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  };
}

/**
 * A deliberately smaller view of another user, used by search and (later) the
 * friends list.
 *
 * Privacy: it exposes only what another user needs in order to recognise you —
 * never your email, goal, height or weight unit.
 */
function toUserSummary(user) {
  return {
    id: String(user._id),
    username: user.username,
    displayName: user.displayName,
    profileImageUrl: user.profileImageUrl ?? null,
  };
}

module.exports = {
  normalizeUsername,
  normalizeEmail,
  findUserById,
  findUserByUsername,
  findUserByEmail,
  findUserByUsernameOrEmail,
  searchUsersByUsername,
  createUser,
  updateUserProfile,
  setProfileImageUrl,
  toPublicUser,
  toUserSummary,
};
