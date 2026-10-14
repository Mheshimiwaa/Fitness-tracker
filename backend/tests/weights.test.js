'use strict';

/**
 * Phase 3 backend tests: body-weight tracking, BMI and the dashboard.
 *
 * Structure mirrors auth.test.js:
 *   - pure schema tests run everywhere (no database needed)
 *   - `dbTest(...)` cases skip themselves when MongoDB is unreachable
 *
 * Every user created here has a username starting with "p3". Cleanup removes
 * those users AND their weight entries, so the suite is idempotent and leaves
 * no orphaned rows in `reprise_test`.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const {
  createWeightSchema,
  updateWeightSchema,
  listWeightsQuerySchema,
} = require('../src/validators/weight.validators');

const PREFIX = 'p3';

const ALEX = {
  displayName: 'Alex Lifter',
  username: 'p3_alex',
  email: 'p3_alex@example.com',
  password: 'GoodPass123',
};

const SAM = {
  displayName: 'Sam Spotter',
  username: 'p3_sam',
  email: 'p3_sam@example.com',
  password: 'GoodPass123',
};

let server;
let baseUrl;
let dbAvailable = false;

const DAY_MS = 24 * 60 * 60 * 1000;

before(async () => {
  server = await startServer();
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  dbAvailable = await db.tryConnect();
  if (dbAvailable) await db.deleteUserDataByPrefix(PREFIX);
});

after(async () => {
  if (dbAvailable) {
    await db.deleteUserDataByPrefix(PREFIX);
    await db.close();
  }
  await stopServer(server);
});

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

/** Register a fresh user and return { token, user }. */
async function createUser(profile) {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: profile,
  });
  assert.equal(status, 201, `expected registration to succeed: ${JSON.stringify(body)}`);
  return { token: body.token, user: body.user };
}

/** PATCH the signed-in user's profile. */
async function updateProfile(token, changes) {
  const { status, body } = await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token,
    body: changes,
  });
  assert.equal(status, 200, `expected profile update to succeed: ${JSON.stringify(body)}`);
  return body.user;
}

/** Log a weight entry, asserting the expected status. */
async function logWeight(token, entry, expectedStatus = 201) {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/weights', {
    token,
    body: entry,
  });
  assert.equal(
    status,
    expectedStatus,
    `unexpected status for ${JSON.stringify(entry)}: ${JSON.stringify(body)}`
  );
  return body;
}

// ---------------------------------------------------------------------------
// Schema validation (no database required)
// ---------------------------------------------------------------------------

test('schema rejects a weight that is implausible in the stated unit', () => {
  const result = createWeightSchema.safeParse({ weight: 5, unit: 'kg' });
  assert.equal(result.success, false);
  assert.ok(result.error.issues.some((issue) => issue.path.join('.') === 'weight'));
});

test('schema accepts the same number when the unit makes it plausible', () => {
  // 600 is above the kilogram ceiling but a normal mass in pounds.
  assert.equal(createWeightSchema.safeParse({ weight: 600, unit: 'kg' }).success, false);
  assert.equal(createWeightSchema.safeParse({ weight: 600, unit: 'lb' }).success, true);
});

test('schema rejects a measurement date in the future', () => {
  const tomorrow = new Date(Date.now() + DAY_MS).toISOString();
  const result = createWeightSchema.safeParse({ weight: 80, measuredAt: tomorrow });
  assert.equal(result.success, false);
  assert.ok(result.error.issues.some((issue) => issue.path.join('.') === 'measuredAt'));
});

test('schema accepts a back-dated measurement and normalises an empty note to null', () => {
  const lastWeek = new Date(Date.now() - 7 * DAY_MS).toISOString();
  const result = createWeightSchema.safeParse({
    weight: 80,
    unit: 'kg',
    measuredAt: lastWeek,
    note: '',
  });
  assert.equal(result.success, true);
  assert.equal(result.data.note, null);
});

test('schema rejects an empty update body', () => {
  const result = updateWeightSchema.safeParse({});
  assert.equal(result.success, false);
});

test('list query schema coerces numeric strings and rejects nonsense', () => {
  const ok = listWeightsQuerySchema.safeParse({ days: '30', limit: '50', order: 'asc' });
  assert.equal(ok.success, true);
  assert.equal(ok.data.days, 30);
  assert.equal(ok.data.limit, 50);

  assert.equal(listWeightsQuerySchema.safeParse({ order: 'sideways' }).success, false);
  assert.equal(listWeightsQuerySchema.safeParse({ days: '0' }).success, false);
});

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

test('weight endpoints require a token', async () => {
  const list = await apiRequest(baseUrl, 'GET', '/api/weights');
  const create = await apiRequest(baseUrl, 'POST', '/api/weights', { body: { weight: 80 } });
  const dashboard = await apiRequest(baseUrl, 'GET', '/api/dashboard');

  assert.equal(list.status, 401);
  assert.equal(create.status, 401);
  assert.equal(dashboard.status, 401);
});

// ---------------------------------------------------------------------------
// Creating entries
// ---------------------------------------------------------------------------

dbTest('logging a weight returns 201, saves it and defaults the unit to the preference', async () => {
  const { token, user } = await createUser(ALEX);
  await updateProfile(token, { weightUnit: 'kg' });

  const { entry } = await logWeight(token, { weight: 82.4 });

  assert.equal(entry.weight, 82.4);
  assert.equal(entry.unit, 'kg');
  assert.equal(entry.weightKg, 82.4);
  assert.equal(entry.note, null);
  assert.ok(entry.id);
  assert.ok(entry.measuredAt);

  // It must really be in the database, not just echoed back.
  const stored = await db.countWeightEntries(user.id);
  assert.equal(stored, 1);
});

dbTest('an entry logged in pounds is reported in the user\u2019s preferred unit', async () => {
  const { token } = await createUser(SAM);
  await updateProfile(token, { weightUnit: 'kg' });

  // 200 lb entered, but the account prefers kilograms.
  const { entry } = await logWeight(token, { weight: 200, unit: 'lb' });

  assert.equal(entry.unit, 'kg');
  // 200 lb = 90.7184... kg
  assert.equal(entry.weight, 90.7);
  assert.equal(entry.weightKg, 90.72);
});

dbTest('logging a weight rejects an implausible value with a 400 and a field detail', async () => {
  const { token } = await createUser({
    displayName: 'Range Tester',
    username: 'p3_range',
    email: 'p3_range@example.com',
    password: 'GoodPass123',
  });

  // Unit omitted -> defaults to kg -> 5 kg is below the human range.
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/weights', {
    token,
    body: { weight: 5 },
  });

  assert.equal(status, 400);
  assert.ok(
    body.error.details.some((detail) => detail.path === 'weight'),
    'expected a field-level detail for weight'
  );
});

// ---------------------------------------------------------------------------
// Listing, change and ordering
// ---------------------------------------------------------------------------

dbTest('history is newest first and reports the change across the window', async () => {
  const { token } = await createUser({
    displayName: 'History Tester',
    username: 'p3_hist',
    email: 'p3_hist@example.com',
    password: 'GoodPass123',
  });

  const now = Date.now();
  await logWeight(token, { weight: 80, measuredAt: new Date(now - 20 * DAY_MS).toISOString() });
  await logWeight(token, { weight: 79, measuredAt: new Date(now - 10 * DAY_MS).toISOString() });
  await logWeight(token, { weight: 78, measuredAt: new Date(now - DAY_MS).toISOString() });

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/weights?days=30', { token });
  assert.equal(status, 200);

  assert.equal(body.count, 3);
  assert.deepEqual(
    body.entries.map((entry) => entry.weight),
    [78, 79, 80],
    'entries should be sorted newest first'
  );

  // Newest (78) minus oldest in the window (80) = -2 kg.
  assert.equal(body.change.kg, -2);
  assert.equal(body.change.display, -2);
  assert.equal(body.change.unit, 'kg');
  assert.equal(body.days, 30);
});

dbTest('a single entry yields a null change (not a misleading zero)', async () => {
  const { token } = await createUser({
    displayName: 'Solo Tester',
    username: 'p3_solo',
    email: 'p3_solo@example.com',
    password: 'GoodPass123',
  });

  await logWeight(token, { weight: 70 });

  const { body } = await apiRequest(baseUrl, 'GET', '/api/weights', { token });
  assert.equal(body.count, 1);
  assert.equal(body.change, null);
});

dbTest('the days filter excludes older entries', async () => {
  const { token } = await createUser({
    displayName: 'Window Tester',
    username: 'p3_window',
    email: 'p3_window@example.com',
    password: 'GoodPass123',
  });

  const now = Date.now();
  await logWeight(token, { weight: 100, measuredAt: new Date(now - 100 * DAY_MS).toISOString() });
  await logWeight(token, { weight: 95, measuredAt: new Date(now - 2 * DAY_MS).toISOString() });

  const { body } = await apiRequest(baseUrl, 'GET', '/api/weights?days=7', { token });
  assert.equal(body.count, 1);
  assert.equal(body.entries[0].weight, 95);
});

dbTest('GET /api/weights/latest returns the newest entry, and null when empty', async () => {
  const { token } = await createUser({
    displayName: 'Latest Tester',
    username: 'p3_latest',
    email: 'p3_latest@example.com',
    password: 'GoodPass123',
  });

  const empty = await apiRequest(baseUrl, 'GET', '/api/weights/latest', { token });
  assert.equal(empty.status, 200);
  assert.equal(empty.body.entry, null);

  await logWeight(token, { weight: 60 });
  await logWeight(token, { weight: 61 });

  const { body } = await apiRequest(baseUrl, 'GET', '/api/weights/latest', { token });
  assert.equal(body.entry.weight, 61);
});

// ---------------------------------------------------------------------------
// Updating and deleting
// ---------------------------------------------------------------------------

dbTest('a weight entry can be edited and deleted', async () => {
  const { token } = await createUser({
    displayName: 'Edit Tester',
    username: 'p3_edit',
    email: 'p3_edit@example.com',
    password: 'GoodPass123',
  });

  const { entry } = await logWeight(token, { weight: 75, note: 'morning' });

  const patched = await apiRequest(baseUrl, 'PATCH', `/api/weights/${entry.id}`, {
    token,
    body: { weight: 74.5, note: 'after cardio' },
  });
  assert.equal(patched.status, 200);
  assert.equal(patched.body.entry.weight, 74.5);
  assert.equal(patched.body.entry.note, 'after cardio');

  const removed = await apiRequest(baseUrl, 'DELETE', `/api/weights/${entry.id}`, { token });
  assert.equal(removed.status, 204);

  const gone = await apiRequest(baseUrl, 'GET', `/api/weights/${entry.id}`, { token });
  assert.equal(gone.status, 404);
});

dbTest('editing to an implausible value is rejected', async () => {
  const { token } = await createUser({
    displayName: 'Edit Range Tester',
    username: 'p3_editrange',
    email: 'p3_editrange@example.com',
    password: 'GoodPass123',
  });

  const { entry } = await logWeight(token, { weight: 75 });

  const { status, body } = await apiRequest(baseUrl, 'PATCH', `/api/weights/${entry.id}`, {
    token,
    body: { weight: 2 },
  });

  assert.equal(status, 400);
  assert.ok(body.error.details.some((detail) => detail.path === 'weight'));
});

dbTest('one user cannot read, edit or delete another user\u2019s entry', async () => {
  const owner = await createUser({
    displayName: 'Owner',
    username: 'p3_owner',
    email: 'p3_owner@example.com',
    password: 'GoodPass123',
  });
  const stranger = await createUser({
    displayName: 'Stranger',
    username: 'p3_stranger',
    email: 'p3_stranger@example.com',
    password: 'GoodPass123',
  });

  const { entry } = await logWeight(owner.token, { weight: 88 });

  const read = await apiRequest(baseUrl, 'GET', `/api/weights/${entry.id}`, {
    token: stranger.token,
  });
  const edit = await apiRequest(baseUrl, 'PATCH', `/api/weights/${entry.id}`, {
    token: stranger.token,
    body: { weight: 99 },
  });
  const remove = await apiRequest(baseUrl, 'DELETE', `/api/weights/${entry.id}`, {
    token: stranger.token,
  });

  // 404 (not 403) so the API cannot be used to discover which ids exist.
  assert.equal(read.status, 404);
  assert.equal(edit.status, 404);
  assert.equal(remove.status, 404);

  // The owner's entry is untouched.
  const stillThere = await apiRequest(baseUrl, 'GET', `/api/weights/${entry.id}`, {
    token: owner.token,
  });
  assert.equal(stillThere.status, 200);
  assert.equal(stillThere.body.entry.weight, 88);
});

// ---------------------------------------------------------------------------
// Dashboard and BMI
// ---------------------------------------------------------------------------

dbTest('dashboard computes BMI from the latest weight and the profile height', async () => {
  const { token } = await createUser({
    displayName: 'BMI Tester',
    username: 'p3_bmi',
    email: 'p3_bmi@example.com',
    password: 'GoodPass123',
  });
  // 180 cm and 81 kg -> BMI 25.0 (the bottom of the "overweight" band).
  await updateProfile(token, { heightCm: 180, weightUnit: 'kg' });
  await logWeight(token, { weight: 81 });

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });
  assert.equal(status, 200);

  assert.equal(body.bmi.value, 25);
  assert.equal(body.bmi.category, 'overweight');
  assert.equal(body.bmiUnavailableReason, null);

  assert.equal(body.weight.latest.weight, 81);
  assert.equal(body.weight.entryCount, 1);
  assert.equal(body.weight.change, null);
});

dbTest('dashboard explains a missing BMI instead of inventing a number', async () => {
  const { token } = await createUser({
    displayName: 'No Data',
    username: 'p3_nodata',
    email: 'p3_nodata@example.com',
    password: 'GoodPass123',
  });

  // No height, no weight.
  const empty = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });
  assert.equal(empty.body.bmi, null);
  assert.equal(empty.body.bmiUnavailableReason, 'no_height');
  assert.equal(empty.body.weight.latest, null);

  // Height set, still no weight.
  await updateProfile(token, { heightCm: 175 });
  const noWeight = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });
  assert.equal(noWeight.body.bmi, null);
  assert.equal(noWeight.body.bmiUnavailableReason, 'no_weight');
});

dbTest('dashboard reports the 30-day change once two readings exist', async () => {
  const { token } = await createUser({
    displayName: 'Change Tester',
    username: 'p3_change',
    email: 'p3_change@example.com',
    password: 'GoodPass123',
  });

  const now = Date.now();
  await logWeight(token, { weight: 85, measuredAt: new Date(now - 15 * DAY_MS).toISOString() });
  await logWeight(token, { weight: 82, measuredAt: new Date(now - 1 * DAY_MS).toISOString() });

  const { body } = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });

  assert.equal(body.weight.entryCount, 2);
  assert.equal(body.weight.latest.weight, 82);
  assert.equal(body.weight.previous.weight, 85);
  assert.equal(body.weight.changeWindowDays, 30);
  assert.equal(body.weight.change.kg, -3);
  assert.equal(body.weight.change.display, -3);
});
