'use strict';

/**
 * User routes, mounted at /api/users. All require a valid token.
 *
 *   GET    /api/users/me                     read my profile
 *   PATCH  /api/users/me                     update my profile
 *   PUT    /api/users/me/photo               upload (or replace) my photo
 *   DELETE /api/users/me/photo               remove my photo
 *   GET    /api/users/search?username=<str>  find other users
 *
 * Route order note: the literal paths `/me` and `/search` are declared before
 * any `/:id` routes would be, so a user id can never shadow them.
 */

const express = require('express');
const env = require('../config/env');
const { PHOTO_CONTENT_TYPES } = require('../config/constants');
const { authenticate } = require('../middleware/authenticate');
const { validate } = require('../middleware/validate');
const { updateProfileSchema, searchUsersQuerySchema } = require('../validators/user.validators');
const usersController = require('../controllers/users.controller');

const router = express.Router();

// Every route in this file requires authentication.
router.use(authenticate);

router.get('/me', usersController.getMe);
router.patch('/me', validate({ body: updateProfileSchema }), usersController.updateMe);

/**
 * The photo arrives as the raw body, so the size limit is enforced by the
 * parser itself: an oversized upload is refused with 413 before a single byte
 * reaches disk. Authenticated first, so an anonymous request cannot make the
 * server buffer anything.
 */
router.put(
  '/me/photo',
  express.raw({ type: PHOTO_CONTENT_TYPES, limit: env.photoMaxBytes }),
  usersController.uploadPhoto
);
router.delete('/me/photo', usersController.deletePhoto);

router.get('/search', validate({ query: searchUsersQuerySchema }), usersController.searchUsers);

module.exports = router;
