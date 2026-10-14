'use strict';

/**
 * Auth service — the business rules for registering and signing in.
 *
 * Controllers call these functions and never touch passwords or tokens
 * directly, which keeps the security-critical logic in one reviewable place.
 */

const ApiError = require('../utils/ApiError');
const { hashPassword, verifyPassword } = require('../utils/password');
const { signToken } = require('../utils/token');
const userService = require('./user.service');

/**
 * A throwaway hash used only to waste time when a login username does not
 * exist. Without it, "unknown user" would return noticeably faster than "wrong
 * password", letting an attacker discover which usernames are registered.
 * Computed once, lazily, and reused.
 */
let dummyHashPromise = null;
function getDummyHash() {
  if (!dummyHashPromise) {
    dummyHashPromise = hashPassword('reprise-timing-equaliser');
  }
  return dummyHashPromise;
}

/**
 * Register a new account.
 *
 * @param {{ displayName: string, username: string, email: string, password: string }} input
 * @returns {Promise<{ user: object, token: string }>} the public user + a JWT
 * @throws {ApiError} 409 when the username or email is already taken
 */
async function registerUser(input) {
  // Friendly, specific pre-checks. These give a better message than the generic
  // duplicate-key error, but note the unique indexes below are still the real
  // guarantee if two requests arrive at the same moment.
  if (await userService.findUserByUsername(input.username)) {
    throw ApiError.conflict('That username is already taken.', {
      field: 'username',
    });
  }
  if (await userService.findUserByEmail(input.email)) {
    throw ApiError.conflict('An account with that email already exists.', {
      field: 'email',
    });
  }

  const passwordHash = await hashPassword(input.password);

  const user = await userService.createUser({
    displayName: input.displayName,
    username: input.username,
    email: input.email,
    passwordHash,
  });

  return { user: userService.toPublicUser(user), token: signToken(user) };
}

/**
 * Sign in with a username (or email) and password.
 *
 * Security: the SAME generic error is returned whether the account is missing
 * or the password is wrong. Revealing which one it was would let an attacker
 * enumerate valid usernames.
 *
 * @param {{ username: string, password: string }} input
 * @returns {Promise<{ user: object, token: string }>}
 * @throws {ApiError} 401 on bad credentials
 */
async function loginUser(input) {
  const user = await userService.findUserByUsernameOrEmail(input.username);

  if (!user) {
    // Still perform a hash comparison so the response time does not reveal
    // that the account is missing.
    await verifyPassword(input.password, await getDummyHash());
    throw ApiError.unauthorized('Invalid username or password.');
  }

  const passwordMatches = await verifyPassword(input.password, user.passwordHash);
  if (!passwordMatches) {
    throw ApiError.unauthorized('Invalid username or password.');
  }

  return { user: userService.toPublicUser(user), token: signToken(user) };
}

module.exports = { registerUser, loginUser };
