'use strict';

/**
 * Users controller — profile read/update and username search.
 * Every route here requires authentication.
 */

const userService = require('../services/user.service');
const photoService = require('../services/photo.service');
const ApiError = require('../utils/ApiError');

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
 * Friends screen (Phase 6a) to find someone to add.
 */
async function searchUsers(req, res) {
  const results = await userService.searchUsersByUsername(req.query.username, {
    excludeUserId: req.user._id,
  });
  res.json({ users: results.map(userService.toUserSummary) });
}

/**
 * PUT /api/users/me/photo  ->  200 OK
 *
 * Body: the image bytes, with an `image/*` Content-Type. There is no multipart
 * form: one image per request needs no framing, and this way the server needs
 * no multipart parser.
 *
 * The same endpoint REPLACES an existing photo, which is what makes it a PUT
 * rather than a POST — uploading is not a "create another one" operation.
 */
async function uploadPhoto(req, res) {
  // The parser only reads bodies whose Content-Type it recognises, so anything
  // else (a form post, a JSON body, no body at all) arrives as `{}` and is
  // refused here rather than being written to disk as a zero-byte file.
  if (!Buffer.isBuffer(req.body) || req.body.length === 0) {
    throw ApiError.unsupportedMediaType(
      'Send the image as the request body with an image/jpeg, image/png or image/webp Content-Type.'
    );
  }

  // The scheme and host the request arrived on, so the stored URL works for the
  // client that uploaded (a phone on the LAN, an emulator on 10.0.2.2).
  const origin = `${req.protocol}://${req.get('host')}`;

  const updated = await photoService.saveProfileImage(req.user._id, req.body, origin);
  res.json({ user: userService.toPublicUser(updated) });
}

/**
 * DELETE /api/users/me/photo  ->  200 OK
 *
 * Unlike the other DELETEs (which answer 204), this one returns the updated
 * user: every profile write does, so the client can adopt the new state from
 * one place instead of re-reading the profile afterwards.
 */
async function deletePhoto(req, res) {
  const updated = await photoService.clearProfileImage(req.user._id);
  res.json({ user: userService.toPublicUser(updated) });
}

module.exports = { getMe, updateMe, searchUsers, uploadPhoto, deletePhoto };
