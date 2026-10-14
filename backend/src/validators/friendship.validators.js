'use strict';

/**
 * Zod schemas for the friends endpoints.
 *
 * The bodies and parameters are deliberately small: a request names a username,
 * and the accept/decline routes name a request id. Everything else (are you
 * already friends? was this request sent to YOU?) is a question about the data,
 * so it is answered in friendship.service.js rather than here.
 */

const { z } = require('zod');
const { usernameField, objectIdField } = require('./common.fields');

/** POST /api/friends/requests */
const sendFriendRequestSchema = z.object({
  username: usernameField,
});

/** `:id` on the request accept/decline routes — a friendship row id. */
const friendRequestIdParamSchema = z.object({
  id: objectIdField,
});

/** `:userId` on DELETE /api/friends/:userId — the other person's user id. */
const friendUserIdParamSchema = z.object({
  userId: objectIdField,
});

module.exports = {
  sendFriendRequestSchema,
  friendRequestIdParamSchema,
  friendUserIdParamSchema,
};
