'use strict';

/**
 * User routes, mounted at /api/users. All require a valid token.
 *
 *   GET   /api/users/me                     read my profile
 *   PATCH /api/users/me                     update my profile
 *   GET   /api/users/search?username=<str>  find other users
 *
 * Route order note: the literal paths `/me` and `/search` are declared before
 * any `/:id` routes would be, so a user id can never shadow them.
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const { validate } = require('../middleware/validate');
const { updateProfileSchema, searchUsersQuerySchema } = require('../validators/user.validators');
const usersController = require('../controllers/users.controller');

const router = express.Router();

// Every route in this file requires authentication.
router.use(authenticate);

router.get('/me', usersController.getMe);
router.patch('/me', validate({ body: updateProfileSchema }), usersController.updateMe);
router.get('/search', validate({ query: searchUsersQuerySchema }), usersController.searchUsers);

module.exports = router;
