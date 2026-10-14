'use strict';

/**
 * Friends controller — sends friend requests, answers them, and lists or removes
 * friendships. Every route here requires authentication (see friends.routes.js).
 *
 * Thin, like the other controllers: read the request, call the service, return
 * its shape. The rules live in friendship.service.js.
 */

const friendshipService = require('../services/friendship.service');

/**
 * POST /api/friends/requests  ->  201 Created
 * Body: { username }
 */
async function sendRequest(req, res) {
  const request = await friendshipService.sendRequest(req.user._id, req.body.username);
  res.status(201).json({ request });
}

/**
 * GET /api/friends/requests  ->  200 OK
 * Returns { incoming, outgoing } — the pending requests, split by direction.
 */
async function listRequests(req, res) {
  const requests = await friendshipService.listRequests(req.user._id);
  res.json(requests);
}

/**
 * POST /api/friends/requests/:id/accept  ->  200 OK
 * The recipient accepts a pending request; the response carries the new friend.
 */
async function acceptRequest(req, res) {
  const friend = await friendshipService.acceptRequest(req.user._id, req.params.id);
  res.json({ friend });
}

/**
 * DELETE /api/friends/requests/:id  ->  204 No Content
 *
 * Removes a pending request. From the recipient's side this is a decline; from
 * the sender's side it is a withdrawal. Both parties are allowed, so one route
 * covers both actions.
 */
async function deleteRequest(req, res) {
  await friendshipService.deleteRequest(req.user._id, req.params.id);
  res.status(204).end();
}

/**
 * GET /api/friends  ->  200 OK
 * Returns { friends } — accepted friendships, alphabetically by username.
 */
async function listFriends(req, res) {
  const friends = await friendshipService.listFriends(req.user._id);
  res.json({ friends });
}

/**
 * DELETE /api/friends/:userId  ->  204 No Content
 * Removes an accepted friendship with that user.
 */
async function removeFriend(req, res) {
  await friendshipService.removeFriend(req.user._id, req.params.userId);
  res.status(204).end();
}

module.exports = {
  sendRequest,
  listRequests,
  acceptRequest,
  deleteRequest,
  listFriends,
  removeFriend,
};
