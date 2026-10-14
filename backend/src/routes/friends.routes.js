'use strict';

/**
 * Friends routes, mounted at /api/friends. All require a valid token.
 *
 *   POST   /api/friends/requests            send a request        -> 201
 *   GET    /api/friends/requests            incoming + outgoing   -> 200
 *   POST   /api/friends/requests/:id/accept accept a request      -> 200
 *   DELETE /api/friends/requests/:id        decline or withdraw   -> 204
 *   GET    /api/friends                     my friends            -> 200
 *   DELETE /api/friends/:userId             remove a friend       -> 204
 *
 * Route order matters: the literal `/requests` paths are declared BEFORE the
 * `/:userId` route, so "requests" is never read as a user id.
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const { validate } = require('../middleware/validate');
const {
  sendFriendRequestSchema,
  friendRequestIdParamSchema,
  friendUserIdParamSchema,
} = require('../validators/friendship.validators');
const friendshipController = require('../controllers/friendship.controller');

const router = express.Router();

// Every route in this file requires authentication.
router.use(authenticate);

router.post(
  '/requests',
  validate({ body: sendFriendRequestSchema }),
  friendshipController.sendRequest
);
router.get('/requests', friendshipController.listRequests);
router.post(
  '/requests/:id/accept',
  validate({ params: friendRequestIdParamSchema }),
  friendshipController.acceptRequest
);
router.delete(
  '/requests/:id',
  validate({ params: friendRequestIdParamSchema }),
  friendshipController.deleteRequest
);

router.get('/', friendshipController.listFriends);
router.delete(
  '/:userId',
  validate({ params: friendUserIdParamSchema }),
  friendshipController.removeFriend
);

module.exports = router;
