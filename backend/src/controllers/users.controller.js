'use strict';

/**
 * Users controller — profile read/update and username search.
 * Every route here requires authentication.
 */

const userService = require('../services/user.service');

/**
 * GET /api/users/me  ->  200 OK
 * Returns the signed-in user's own profile.
 */
async function getMe(req, res) {
  res.json({ user: userService.toPublicUser(req.user) });
}

/**
 * PATCH /api/users/me  ->  200 OK
 * Body: any subset of { displayName, username, fitnessGoal, weightUnit, heightCm, profileImageUrl }
 */
async function updateMe(req, res) {
  const updated = await userService.updateUserProfile(req.user._id, req.body);
  res.json({ user: userService.toPublicUser(updated) });
}

/**
 * GET /api/users/search?username=<text>  ->  200 OK
 *
 * Prefix search over usernames, excluding the caller themself. Used by the
 * Friends screen (Phase 6) to find someone to add.
 */
async function searchUsers(req, res) {
  const results = await userService.searchUsersByUsername(req.query.username, {
    excludeUserId: req.user._id,
  });
  res.json({ users: results.map(userService.toUserSummary) });
}

module.exports = { getMe, updateMe, searchUsers };
