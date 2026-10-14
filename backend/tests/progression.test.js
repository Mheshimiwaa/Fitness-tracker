'use strict';

/**
 * Phase 5 backend tests: the progressive-overload assistant.
 *
 * Structure mirrors workouts.test.js:
 *   - the pure rule tests run everywhere (no database needed)
 *   - `dbTest(...)` cases skip themselves when MongoDB is unreachable
 *
 * Every user created here has a username starting with "p5". Cleanup removes
 * those users AND their workouts, so the suite is idempotent.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const { progressionQuerySchema } = require('../src/validators/progression.validators');
const {
  classify,
  buildComparison,
  buildSuggestion,
  VERDICT,
} = require('../src/services/progression.service');

const PREFIX = 'p5';

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
  assert.equal(status, 201, `expected registration to succeed, got ${status}`);
  return { token: body.token, user: body.user };
}

/** Log a one-exercise workout for a user, asserting it was created. */
async function logExercise(token, { name, sets, daysAgo = 0 }) {
  const workoutDate = new Date(Date.now() - daysAgo * DAY_MS).toISOString();
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/workouts', {
    token,
    body: {
      name: 'Session',
      workoutDate,
      exercises: [{ name, sets }],
    },
  });
  assert.equal(status, 201, `expected the workout to be logged, got ${status}`);
  return body.workout;
}

/** Build a session summary for the pure rules. */
function session({ topWeight = 60, topWeightKg = 60, totalVolumeKg = 900, totalReps = 18 } = {}) {
  return { topWeight, topWeightKg, totalVolumeKg, totalReps };
}

// ---------------------------------------------------------------------------
// Pure rules (no database)
// ---------------------------------------------------------------------------

test('a heavier top set is progress', () => {
  const latest = session({ topWeight: 65, topWeightKg: 65 });
  const previous = session({ topWeight: 60, topWeightKg: 60 });

  assert.equal(classify(latest, previous).verdict, VERDICT.PROGRESSING);
});

test('more total volume is progress, even at the same top weight', () => {
  const previous = session({ totalVolumeKg: 900 });
  const latest = session({ totalVolumeKg: 1100 });

  assert.equal(classify(latest, previous).verdict, VERDICT.PROGRESSING);
});

test('more reps without losing volume is progress', () => {
  const previous = session({ totalVolumeKg: 900, totalReps: 15 });
  const latest = session({ totalVolumeKg: 900, totalReps: 18 });

  assert.equal(classify(latest, previous).verdict, VERDICT.PROGRESSING);
});

test('a wobble inside the tolerance is holding, not progress', () => {
  // 1000 -> 1005 is +0.5%, below the 2% tolerance, with no other change.
  const previous = session({ totalVolumeKg: 1000 });
  const latest = session({ totalVolumeKg: 1005 });

  const result = classify(latest, previous);
  assert.equal(result.verdict, VERDICT.HOLDING);
  assert.equal(result.volumeDeltaKg, 5);
});

test('clearly less volume is a regression', () => {
  const previous = session({ totalVolumeKg: 1000 });
  const latest = session({ totalVolumeKg: 900 });

  assert.equal(classify(latest, previous).verdict, VERDICT.REGRESSING);
});

test('a lighter top set with no extra reps is a regression', () => {
  const previous = session({ topWeight: 65, topWeightKg: 65, totalVolumeKg: 900, totalReps: 18 });
  const latest = session({ topWeight: 60, topWeightKg: 60, totalVolumeKg: 900, totalReps: 18 });

  assert.equal(classify(latest, previous).verdict, VERDICT.REGRESSING);
});

test('one session is not enough for a trend, and says so', () => {
  const comparison = buildComparison([session()], 'kg');

  assert.equal(comparison.verdict, VERDICT.INSUFFICIENT_DATA);
  assert.equal(comparison.hasPrevious, false);
  assert.match(comparison.suggestion, /again/i);
});

test('no sessions at all is handled without inventing data', () => {
  const comparison = buildComparison([], 'kg');

  assert.equal(comparison.verdict, VERDICT.INSUFFICIENT_DATA);
  assert.equal(comparison.latest, null);
  assert.equal(comparison.previous, null);
  assert.match(comparison.suggestion, /start tracking/i);
});

test('a kilogram account is told the next 2.5 kg step', () => {
  const comparison = buildComparison(
    [session({ topWeight: 65, topWeightKg: 65 }), session({ topWeight: 60, topWeightKg: 60 })],
    'kg'
  );

  assert.equal(comparison.verdict, VERDICT.PROGRESSING);
  // 65 + 2.5 = 67.5
  assert.match(comparison.suggestion, /67\.5 kg/);
});

test('a pound account is told the next 5 lb step, in pounds', () => {
  const comparison = buildComparison(
    [
      session({ topWeight: 143, topWeightKg: 64.9, totalVolumeKg: 1000 }),
      session({ topWeight: 135, topWeightKg: 61.2, totalVolumeKg: 900 }),
    ],
    'lb'
  );

  assert.equal(comparison.verdict, VERDICT.PROGRESSING);
  // 143 + 5 = 148, and the hint is expressed in pounds, never kilograms.
  assert.match(comparison.suggestion, /148 lb/);
  assert.doesNotMatch(comparison.suggestion, /kg/);
});

test('the reported deltas are in the display unit', () => {
  const comparison = buildComparison(
    [
      session({ topWeight: 143, topWeightKg: 64.9 }),
      session({ topWeight: 135, topWeightKg: 61.2 }),
    ],
    'lb'
  );

  // 64.9 kg - 61.2 kg = 3.7 kg = ~8.2 lb
  assert.ok(comparison.topWeightDelta > 0);
  assert.ok(Math.abs(comparison.topWeightDelta - 8.2) < 0.2);
});

test('the suggestion for a regression is encouraging, not scolding', () => {
  const suggestion = buildSuggestion(
    VERDICT.REGRESSING,
    session({ topWeight: 55, topWeightKg: 55 }),
    session({ topWeight: 60, topWeightKg: 60 }),
    'kg'
  );

  assert.match(suggestion, /fatigue|deload/i);
  assert.match(suggestion, /60 kg/);
});

// ---------------------------------------------------------------------------
// Query validation (no database)
// ---------------------------------------------------------------------------

test('the progression query requires an exercise name', () => {
  assert.equal(progressionQuerySchema.safeParse({}).success, false);
  assert.equal(progressionQuerySchema.safeParse({ exercise: '   ' }).success, false);
  assert.equal(progressionQuerySchema.safeParse({ exercise: 'Bench Press' }).success, true);
});

test('the progression query rejects a silly limit', () => {
  assert.equal(progressionQuerySchema.safeParse({ exercise: 'Squat', limit: 0 }).success, false);
  assert.equal(progressionQuerySchema.safeParse({ exercise: 'Squat', limit: 999 }).success, false);
  assert.equal(progressionQuerySchema.safeParse({ exercise: 'Squat', limit: '5' }).success, true);
});

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

test('progression requires a token', async () => {
  const { status } = await apiRequest(baseUrl, 'GET', '/api/progression?exercise=Squat');
  assert.equal(status, 401);

  const { status: listStatus } = await apiRequest(baseUrl, 'GET', '/api/progression/exercises');
  assert.equal(listStatus, 401);
});

// ---------------------------------------------------------------------------
// End-to-end
// ---------------------------------------------------------------------------

dbTest('an unknown exercise is an empty trend, not an error', async () => {
  const { token } = await createUser({
    displayName: 'Fresh Lifter',
    username: 'p5_fresh',
    email: 'p5_fresh@example.com',
    password: 'GoodPass123',
  });

  const { status, body } = await apiRequest(
    baseUrl,
    'GET',
    `/api/progression?exercise=${encodeURIComponent('Bench Press')}`,
    { token }
  );

  assert.equal(status, 200);
  assert.equal(body.exercise, 'Bench Press');
  assert.equal(body.sessionCount, 0);
  assert.deepEqual(body.sessions, []);
  assert.equal(body.comparison.verdict, VERDICT.INSUFFICIENT_DATA);
});

dbTest('progressing is detected from two real sessions', async () => {
  const { token } = await createUser({
    displayName: 'Progress Tester',
    username: 'p5_progress',
    email: 'p5_progress@example.com',
    password: 'GoodPass123',
  });

  // Older session first: 3x8 @ 60 kg = 1440 kg of volume.
  await logExercise(token, {
    name: 'Bench Press',
    daysAgo: 7,
    sets: [
      { reps: 8, weight: 60 },
      { reps: 8, weight: 60 },
      { reps: 8, weight: 60 },
    ],
  });
  // Newer session: 3x8 @ 65 kg — a heavier top set.
  await logExercise(token, {
    name: 'Bench Press',
    daysAgo: 1,
    sets: [
      { reps: 8, weight: 65 },
      { reps: 8, weight: 65 },
      { reps: 8, weight: 65 },
    ],
  });

  const { status, body } = await apiRequest(
    baseUrl,
    'GET',
    `/api/progression?exercise=${encodeURIComponent('Bench Press')}`,
    { token }
  );

  assert.equal(status, 200);
  assert.equal(body.sessionCount, 2);
  // Newest first.
  assert.equal(body.sessions[0].topWeight, 65);
  assert.equal(body.sessions[1].topWeight, 60);
  assert.equal(body.sessions[0].totalVolumeKg, 1560);
  assert.equal(body.comparison.verdict, VERDICT.PROGRESSING);
  assert.equal(body.comparison.topWeightDelta, 5);
  assert.match(body.comparison.suggestion, /67\.5 kg/);
});

dbTest('a regression is detected too', async () => {
  const { token } = await createUser({
    displayName: 'Deload Tester',
    username: 'p5_deload',
    email: 'p5_deload@example.com',
    password: 'GoodPass123',
  });

  await logExercise(token, {
    name: 'Squat',
    daysAgo: 7,
    sets: [
      { reps: 5, weight: 100 },
      { reps: 5, weight: 100 },
    ],
  });
  await logExercise(token, {
    name: 'Squat',
    daysAgo: 1,
    sets: [
      { reps: 5, weight: 80 },
      { reps: 5, weight: 80 },
    ],
  });

  const { body } = await apiRequest(
    baseUrl,
    'GET',
    `/api/progression?exercise=Squat`,
    { token }
  );

  assert.equal(body.comparison.verdict, VERDICT.REGRESSING);
  assert.match(body.comparison.suggestion, /fatigue|deload/i);
});

dbTest('the exercise list counts how often each was logged', async () => {
  const { token } = await createUser({
    displayName: 'List Tester',
    username: 'p5_list',
    email: 'p5_list@example.com',
    password: 'GoodPass123',
  });

  await logExercise(token, { name: 'Deadlift', daysAgo: 3, sets: [{ reps: 5, weight: 120 }] });
  await logExercise(token, { name: 'Deadlift', daysAgo: 1, sets: [{ reps: 5, weight: 125 }] });
  await logExercise(token, { name: 'Bench Press', daysAgo: 1, sets: [{ reps: 8, weight: 60 }] });

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/progression/exercises', {
    token,
  });

  assert.equal(status, 200);
  assert.deepEqual(
    body.exercises.map((e) => e.name),
    ['Bench Press', 'Deadlift'] // alphabetical
  );
  const deadlift = body.exercises.find((e) => e.name === 'Deadlift');
  assert.equal(deadlift.timesLogged, 2);
  assert.ok(deadlift.lastPerformedAt);
});

dbTest('one user cannot see another user progression', async () => {
  const { token: ownerToken } = await createUser({
    displayName: 'Owner',
    username: 'p5_owner',
    email: 'p5_owner@example.com',
    password: 'GoodPass123',
  });
  const { token: otherToken } = await createUser({
    displayName: 'Other',
    username: 'p5_other',
    email: 'p5_other@example.com',
    password: 'GoodPass123',
  });

  await logExercise(ownerToken, {
    name: 'Overhead Press',
    daysAgo: 1,
    sets: [{ reps: 8, weight: 40 }],
  });

  const { body } = await apiRequest(
    baseUrl,
    'GET',
    `/api/progression?exercise=${encodeURIComponent('Overhead Press')}`,
    { token: otherToken }
  );

  // The other user has logged nothing, so the list is empty — the owner's data
  // is not merely hidden, it is never read.
  assert.equal(body.sessionCount, 0);
  assert.equal(body.comparison.verdict, VERDICT.INSUFFICIENT_DATA);
});
