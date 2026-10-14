'use strict';

/**
 * Friendship service — friend requests, friendships, and their removal.
 *
 * Design notes
 * ------------
 * 1. A friendship is ONE row per PAIR of users, not two. The row records who
 *    asked (`requesterId`) and who was asked (`recipientId`) so a pending
 *    request has an obvious direction, but once it is accepted the relationship
 *    is symmetric and both users are simply "friends".
 *
 * 2. The pair is identified by a canonical `pairKey` — the two ids sorted and
 *    joined. That is what the unique index is on, so "A asks B" and "B asks A"
 *    can never create two rows, and a double-tap on Send cannot either.
 *
 * 3. Every "is this allowed?" rule lives HERE, not in the controller or the
 *    schema: you cannot befriend yourself, you cannot request someone who is
 *    already a friend, and you cannot accept a request that was not sent to you.
 */

const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('../database/collections');
const { toObjectId } = require('../utils/objectId');
const ApiError = require('../utils/ApiError');
const { FRIEND_LIST_MAX } = require('../config/constants');
const { toUserSummary, findUserByUsername, findUserById } = require('./user.service');

/** The two states a friendship row can be in. */
const STATUS = Object.freeze({
  PENDING: 'pending',
  ACCEPTED: 'accepted',
});

/** The friendships collection. Called lazily so the DB is connected first. */
function friendships() {
  return getDb().collection(COLLECTIONS.FRIENDSHIPS);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * A canonical, order-independent key for a pair of users.
 *
 * Sorting the two ids means `pairKeyOf(a, b) === pairKeyOf(b, a)`, which is
 * exactly what lets a single unique index forbid both duplicate rows.
 */
function pairKeyOf(idA, idB) {
  return [String(idA), String(idB)].sort().join(':');
}

/** The id of the OTHER user in a row, from `viewerId`'s point of view. */
function otherPartyId(friendship, viewerId) {
  return String(friendship.requesterId) === String(viewerId)
    ? friendship.recipientId
    : friendship.requesterId;
}

/**
 * Load lightweight public views for a set of user ids, in ONE query.
 *
 * Returns a Map keyed by the string id. A user that has since been deleted is
 * simply absent from the map, which the callers skip rather than crash on.
 */
async function summariseUsers(ids) {
  if (ids.length === 0) return new Map();

  const docs = await getDb()
    .collection(COLLECTIONS.USERS)
    .find({ _id: { $in: ids } }, { projection: { username: 1, displayName: 1, profileImageUrl: 1 } })
    .toArray();

  return new Map(docs.map((doc) => [String(doc._id), toUserSummary(doc)]));
}

/**
 * The wire view of a pending request: its id (needed to accept or decline),
 * which way it points, the other person, and when it was sent.
 */
function toRequestView(friendship, otherUser, direction) {
  return {
    id: String(friendship._id),
    direction,
    user: otherUser,
    createdAt: friendship.createdAt,
  };
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

/**
 * All pending requests involving the user, split by direction.
 *
 * One query fetches every relevant row, then one more fetches the people
 * involved — so this stays two round-trips regardless of how many requests
 * there are.
 *
 * @returns {Promise<{ incoming: object[], outgoing: object[] }>}
 */
async function listRequests(userId) {
  const id = toObjectId(userId);

  const rows = await friendships()
    .find({
      status: STATUS.PENDING,
      $or: [{ requesterId: id }, { recipientId: id }],
    })
    .sort({ createdAt: -1, _id: -1 })
    .limit(FRIEND_LIST_MAX)
    .toArray();

  const summaries = await summariseUsers(rows.map((row) => otherPartyId(row, id)));

  const incoming = [];
  const outgoing = [];

  for (const row of rows) {
    const isIncoming = String(row.recipientId) === String(id);
    const other = summaries.get(String(otherPartyId(row, id)));
    if (!other) continue; // the other account was deleted

    const request = toRequestView(row, other, isIncoming ? 'incoming' : 'outgoing');
    (isIncoming ? incoming : outgoing).push(request);
  }

  return { incoming, outgoing };
}

/**
 * The user's friends, alphabetically by username.
 *
 * Only ACCEPTED rows count — a pending request is not a friendship yet.
 *
 * @returns {Promise<object[]>} public user summaries
 */
async function listFriends(userId) {
  const id = toObjectId(userId);

  const rows = await friendships()
    .find({
      status: STATUS.ACCEPTED,
      $or: [{ requesterId: id }, { recipientId: id }],
    })
    .limit(FRIEND_LIST_MAX)
    .toArray();

  const summaries = await summariseUsers(rows.map((row) => otherPartyId(row, id)));

  return rows
    .map((row) => summaries.get(String(otherPartyId(row, id))))
    .filter(Boolean)
    .sort((a, b) => a.username.localeCompare(b.username));
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------

/**
 * Send a friend request to `username`.
 *
 * @returns {Promise<object>} the created request, as the SENDER sees it
 */
async function sendRequest(requesterId, username) {
  const requester = toObjectId(requesterId);
  if (!requester) throw ApiError.unauthorized();

  const target = await findUserByUsername(username);
  if (!target) {
    // Deliberately a 404: this is a public "add by username" flow, so it is fine
    // for the sender to learn that a username does not exist.
    throw ApiError.notFound('No user with that username.');
  }

  if (String(target._id) === String(requester)) {
    throw ApiError.badRequest('You cannot add yourself as a friend.');
  }

  const pairKey = pairKeyOf(requester, target._id);
  const existing = await friendships().findOne({ pairKey });

  if (existing) {
    if (existing.status === STATUS.ACCEPTED) {
      throw ApiError.conflict('You are already friends.');
    }
    throw ApiError.conflict('There is already a pending friend request between you.');
  }

  const now = new Date();
  const document = {
    pairKey,
    requesterId: requester,
    recipientId: toObjectId(target._id),
    status: STATUS.PENDING,
    createdAt: now,
    updatedAt: now,
    respondedAt: null,
  };

  try {
    const result = await friendships().insertOne(document);
    document._id = result.insertedId;
  } catch (error) {
    // The unique pairKey index is the real guard: if two requests raced past the
    // check above, one insert wins and the other lands here.
    if (error && error.code === 11000) {
      throw ApiError.conflict('There is already a pending friend request between you.');
    }
    throw error;
  }

  return toRequestView(document, toUserSummary(target), 'outgoing');
}

/**
 * Accept a pending request. Only the RECIPIENT may accept.
 *
 * @returns {Promise<object>} the new friend, as a public user summary
 */
async function acceptRequest(userId, requestId) {
  const id = toObjectId(userId);
  const requestObjectId = toObjectId(requestId);
  if (!requestObjectId) throw ApiError.notFound('Friend request not found.');

  const request = await friendships().findOne({ _id: requestObjectId });

  // An unrelated user gets a 404, not a 403: there is no reason to confirm that
  // somebody else's request exists.
  if (!request || String(request.recipientId) !== String(id)) {
    throw ApiError.notFound('Friend request not found.');
  }
  if (request.status !== STATUS.PENDING) {
    throw ApiError.conflict('This request has already been answered.');
  }

  const now = new Date();
  const result = await friendships().updateOne(
    { _id: request._id, status: STATUS.PENDING },
    { $set: { status: STATUS.ACCEPTED, respondedAt: now, updatedAt: now } }
  );

  if (result.modifiedCount === 0) {
    // Lost a race with another accept/decline; the state is no longer pending.
    throw ApiError.conflict('This request has already been answered.');
  }

  const requester = await findUserById(request.requesterId);
  if (!requester) throw ApiError.notFound('That account no longer exists.');

  return toUserSummary(requester);
}

/**
 * Delete a PENDING request.
 *
 * Used for both "decline" (the recipient says no) and "withdraw" (the sender
 * changes their mind), because in both cases the row simply goes away — and
 * either party is entitled to do it.
 */
async function deleteRequest(userId, requestId) {
  const id = toObjectId(userId);
  const requestObjectId = toObjectId(requestId);
  if (!requestObjectId) throw ApiError.notFound('Friend request not found.');

  const result = await friendships().deleteOne({
    _id: requestObjectId,
    status: STATUS.PENDING,
    $or: [{ requesterId: id }, { recipientId: id }],
  });

  if (result.deletedCount === 0) {
    throw ApiError.notFound('Friend request not found.');
  }
}

/**
 * Remove an accepted friendship. Either friend may do it.
 */
async function removeFriend(userId, otherUserId) {
  const id = toObjectId(userId);
  const other = toObjectId(otherUserId);
  if (!other) throw ApiError.notFound('Friendship not found.');

  const result = await friendships().deleteOne({
    pairKey: pairKeyOf(id, other),
    status: STATUS.ACCEPTED,
  });

  if (result.deletedCount === 0) {
    throw ApiError.notFound('Friendship not found.');
  }
}

module.exports = {
  STATUS,
  pairKeyOf,
  sendRequest,
  listRequests,
  listFriends,
  acceptRequest,
  deleteRequest,
  removeFriend,
};
