'use strict';

/**
 * Phase 2 backend tests: registration, login, JWT protection and profiles.
 *
 * These run against the "reprise_test" database (see helpers/setup.js), never
 * the real database. Every user created here has a username starting with
 * "p2", and that prefix is deleted before and after the run so the suite is
 * idempotent.
 *
 * If MongoDB is unreachable the database-backed tests are SKIPPED rather than
 * failed, so the pure-validation tests still run anywhere.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');

const PREFIX = 'p2';

const ALEX = {
  displayName: 'Alex Lifter',
  username: 'p2_alex',
  email: 'p2_alex@example.com',
  password: 'GoodPass123',
};

const SAM = {
  displayName: 'Sam Spotter',
  username: 'p2_sam',
  email: 'p2_sam@example.com',
  password: 'GoodPass123',
};

let server;
let baseUrl;
let dbAvailable = false;

before(async () => {
  server = await startServer();
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  dbAvailable = await db.tryConnect();
  if (dbAvailable) await db.deleteUsersByPrefix(PREFIX);
});

after(async () => {
  if (dbAvailable) {
    await db.deleteUsersByPrefix(PREFIX);
    await db.close();
  }
  await stopServer(server);
});

/** Register a test user, or log in if a previous run already created it. */
async function registerOrLogin(user) {
  const created = await apiRequest(baseUrl, 'POST', '/api/auth/register', { body: user });
  if (created.status !== 409) return created;
  return apiRequest(baseUrl, 'POST', '/api/auth/login', {
    body: { username: user.username, password: user.password },
  });
}

/** Assert the DB is available, otherwise skip this test. */
function dbTest(name, fn) {
  return test(name, async (t) => {
    if (!dbAvailable) {
      t.skip('MongoDB not reachable');
      return;
    }
    await fn(t);
  });
}

// ---------------------------------------------------------------------------
// Validation (no database required)
// ---------------------------------------------------------------------------

test('register rejects an invalid email with a 400 and a field-level detail', async () => {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: { ...ALEX, email: 'not-an-email' },
  });

  assert.equal(status, 400);
  assert.equal(body.error.message, 'Validation failed.');
  assert.ok(
    body.error.details.some((d) => d.path === 'email'),
    'expected a validation detail for the email field'
  );
});

test('register rejects a weak password', async () => {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: { ...ALEX, password: 'short' },
  });

  assert.equal(status, 400);
  assert.ok(body.error.details.some((d) => d.path === 'password'));
});

test('register rejects a username that does not start with a letter', async () => {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: { ...ALEX, username: '1badname' },
  });

  assert.equal(status, 400);
  assert.ok(body.error.details.some((d) => d.path === 'username'));
});

// ---------------------------------------------------------------------------
// Registration
// ---------------------------------------------------------------------------

dbTest('register creates the account and never exposes the password', async () => {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: ALEX,
  });

  assert.equal(status, 201);
  assert.ok(typeof body.token === 'string' && body.token.length > 0, 'expected a JWT');

  assert.equal(body.user.username, ALEX.username);
  assert.equal(body.user.displayName, ALEX.displayName);
  assert.equal(body.user.email, ALEX.email);
  assert.equal(body.user.fitnessGoal, 'maintain');
  assert.equal(body.user.weightUnit, 'kg');
  assert.equal(body.user.heightCm, null);
  assert.equal(body.user.passwordHash, undefined);

  // The plain-text password must not appear anywhere in the response.
  assert.equal(JSON.stringify(body).includes(ALEX.password), false);

  // In the database it must be a bcrypt hash, not the password.
  const doc = await db.findUserDoc(ALEX.username);
  assert.ok(doc, 'user document should exist');
  assert.match(doc.passwordHash, /^\$2[aby]\$/, 'expected a bcrypt hash');
  assert.notEqual(doc.passwordHash, ALEX.password);

  // And that hash must genuinely verify the password.
  const { verifyPassword } = require('../src/utils/password');
  assert.equal(await verifyPassword(ALEX.password, doc.passwordHash), true);
  assert.equal(await verifyPassword('wrong-password', doc.passwordHash), false);
});

dbTest('register rejects a duplicate username with a 409', async () => {
  await registerOrLogin(SAM);

  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: { ...SAM, email: 'p2_sam_other@example.com' },
  });

  assert.equal(status, 409);
  assert.match(body.error.message, /username/i);
});

dbTest('register treats emails case-insensitively (no duplicate accounts)', async () => {
  const username = 'p2_case';
  await registerOrLogin({
    displayName: 'Case Test',
    username,
    email: 'P2_Case@Example.com',
    password: 'GoodPass123',
  });

  const doc = await db.findUserDoc(username);
  assert.equal(doc.emailNormalized, 'p2_case@example.com', 'normalised email must be lower-case');
  assert.equal(doc.email, 'P2_Case@Example.com', 'the typed email is kept for display');

  // A second account with the same email in a different case must be rejected.
  const { status } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: {
      displayName: 'Impostor',
      username: 'p2_impostor',
      email: 'p2_case@EXAMPLE.com',
      password: 'GoodPass123',
    },
  });

  assert.equal(status, 409);
});

// ---------------------------------------------------------------------------
// Login
// ---------------------------------------------------------------------------

dbTest('login returns a token for correct credentials', async () => {
  await registerOrLogin(ALEX);

  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/login', {
    body: { username: ALEX.username, password: ALEX.password },
  });

  assert.equal(status, 200);
  assert.ok(body.token);
  assert.equal(body.user.username, ALEX.username);
});

dbTest('login rejects a wrong password and an unknown user with the SAME message', async () => {
  await registerOrLogin(ALEX);

  const wrongPassword = await apiRequest(baseUrl, 'POST', '/api/auth/login', {
    body: { username: ALEX.username, password: 'WrongPass123' },
  });
  const unknownUser = await apiRequest(baseUrl, 'POST', '/api/auth/login', {
    body: { username: 'p2_nobody', password: 'WrongPass123' },
  });

  assert.equal(wrongPassword.status, 401);
  assert.equal(unknownUser.status, 401);
  // Identical wording prevents username enumeration.
  assert.equal(wrongPassword.body.error.message, unknownUser.body.error.message);
});

// ---------------------------------------------------------------------------
// Token protection
// ---------------------------------------------------------------------------

test('protected routes reject a missing token with a 401', async () => {
  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/auth/me');

  assert.equal(status, 401);
  assert.match(body.error.message, /Authorization/i);
});

test('protected routes reject a malformed token with a 401', async () => {
  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/auth/me', {
    token: 'not-a-real-jwt',
  });

  assert.equal(status, 401);
  assert.match(body.error.message, /token/i);
});

dbTest('GET /api/auth/me returns the signed-in user for a valid token', async () => {
  const { body: session } = await registerOrLogin(ALEX);

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/auth/me', {
    token: session.token,
  });

  assert.equal(status, 200);
  assert.equal(body.user.username, ALEX.username);
  assert.equal(body.user.passwordHash, undefined);
});

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

dbTest('PATCH /api/users/me updates the profile and persists it', async () => {
  const { body: session } = await registerOrLogin(ALEX);

  const { status, body } = await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token: session.token,
    body: { displayName: 'Alex The Lifter', fitnessGoal: 'gain_muscle', weightUnit: 'lb', heightCm: 180 },
  });

  assert.equal(status, 200);
  assert.equal(body.user.displayName, 'Alex The Lifter');
  assert.equal(body.user.fitnessGoal, 'gain_muscle');
  assert.equal(body.user.weightUnit, 'lb');
  assert.equal(body.user.heightCm, 180);

  // Confirm it really reached the database.
  const doc = await db.findUserDoc(ALEX.username);
  assert.equal(doc.displayName, 'Alex The Lifter');
  assert.equal(doc.heightCm, 180);

  // Restore the original values so other tests are unaffected.
  await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token: session.token,
    body: { displayName: ALEX.displayName, fitnessGoal: 'maintain', weightUnit: 'kg', heightCm: null },
  });
});

dbTest('PATCH /api/users/me rejects an empty body', async () => {
  const { body: session } = await registerOrLogin(ALEX);

  const { status, body } = await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token: session.token,
    body: {},
  });

  assert.equal(status, 400);
  assert.equal(body.error.message, 'Validation failed.');
});

dbTest('PATCH /api/users/me rejects an impossible height', async () => {
  const { body: session } = await registerOrLogin(ALEX);

  const { status, body } = await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token: session.token,
    body: { heightCm: 500 },
  });

  assert.equal(status, 400);
  assert.ok(body.error.details.some((d) => d.path === 'heightCm'));
});

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

dbTest('GET /api/users/search finds other users but excludes the caller', async () => {
  const { body: alexSession } = await registerOrLogin(ALEX);
  await registerOrLogin(SAM);

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/users/search?username=p2_', {
    token: alexSession.token,
  });

  assert.equal(status, 200);
  const usernames = body.users.map((u) => u.username);
  assert.ok(usernames.includes(SAM.username), 'expected to find the other user');
  assert.equal(usernames.includes(ALEX.username), false, 'the caller must be excluded');

  // Privacy: search results must not leak email or other private fields.
  assert.equal(Object.prototype.hasOwnProperty.call(body.users[0], 'email'), false);
  assert.equal(Object.prototype.hasOwnProperty.call(body.users[0], 'fitnessGoal'), false);
});

test('GET /api/users/search requires authentication', async () => {
  const { status } = await apiRequest(baseUrl, 'GET', '/api/users/search?username=p2_');
  assert.equal(status, 401);
});
