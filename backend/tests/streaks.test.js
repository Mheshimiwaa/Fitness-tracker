'use strict';

/**
 * Phase 7 backend tests: training streaks.
 *
 * A streak is consecutive UTC days with at least one workout, so the interesting
 * cases are all about the calendar: a gap of one day must break it, today must
 * not have to be trained *yet*, and the best run ever is remembered separately
 * from the current one.
 *
 * Structure mirrors the other suites:
 *   - the pure tests (day arithmetic) run everywhere
 *   - `dbTest(...)` cases skip themselves when MongoDB is unreachable
 *
 * Every user created here has a username starting with "p8". It is the next free
 * prefix: a prefix may not be a prefix of another suite's, or one suite's cleanup
 * would delete the other's users while it is still running (`p7` would have
 * matched `p7s…`). Cleanup removes those users AND their workouts.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const {
  utcDayNumber,
  dayNumberFromKey,
  currentStreak,
  longestStreak,
} = require('../src/services/streak.service');

const PREFIX = 'p8';
const DAY_MS = 24 * 60 * 60 * 1000;

let server;
let baseUrl;
let dbAvailable = false;

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
async function createUser(suffix) {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/auth/register', {
    body: {
      displayName: `P8 ${suffix}`,
      username: `${PREFIX}_${suffix}`,
      email: `${PREFIX}_${suffix}@example.com`,
      password: 'GoodPass123',
    },
  });
  assert.equal(status, 201, `expected registration to succeed, got ${status}`);
  return { token: body.token, user: body.user };
}

/** Log one workout at a chosen instant (back-dating is allowed). */
async function logWorkoutAt(token, date, name = 'Session') {
  const { status } = await apiRequest(baseUrl, 'POST', '/api/workouts', {
    token,
    body: {
      name,
      workoutDate: date.toISOString(),
      exercises: [{ name: 'Bench Press', sets: [{ reps: 5, weight: 60 }] }],
    },
  });
  assert.equal(status, 201, `expected workout creation to succeed, got ${status}`);
}

/**
 * A stable instant on the UTC day `daysAgoCount` days before today.
 *
 * "Today" is the current instant — midday would be in the future when the suite
 * runs in the morning (the server rejects a future `workoutDate`). Every other
 * day is midday, so the instant sits comfortably inside its own UTC day rather
 * than on a midnight boundary.
 */
function daysAgo(daysAgoCount) {
  if (daysAgoCount === 0) return new Date();
  const today = new Date();
  const midnightToday = Date.UTC(
    today.getUTCFullYear(),
    today.getUTCMonth(),
    today.getUTCDate()
  );
  return new Date(midnightToday - daysAgoCount * DAY_MS + 12 * 60 * 60 * 1000);
}

/** The dashboard's streak section for a token. */
async function streakOf(token) {
  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });
  assert.equal(status, 200, `expected 200 fetching the dashboard, got ${status}`);
  assert.ok(body.streak, 'the dashboard must carry a streak section');
  return body.streak;
}

// ---------------------------------------------------------------------------
// Pure helpers (no database)
// ---------------------------------------------------------------------------

test('a UTC calendar day is an integer, parsed the same from a day key', () => {
  const day = utcDayNumber(new Date('2026-10-17T00:00:00.000Z'));
  // Two instants on the same UTC day share a number, whichever side of midnight.
  assert.equal(utcDayNumber(new Date('2026-10-17T23:59:59.999Z')), day);
  assert.equal(utcDayNumber(new Date('2026-10-18T00:00:00.000Z')), day + 1);
  assert.equal(dayNumberFromKey('2026-10-17'), day);
  assert.equal(dayNumberFromKey('2026-10-18'), day + 1);
});

test('consecutive days count, and today does not have to be trained yet', () => {
  const today = utcDayNumber(new Date('2026-10-17T10:00:00.000Z'));
  const days = [today - 2, today - 3, today - 4];

  // Trained through yesterday: the run stands until today is over.
  assert.equal(currentStreak([...days, today - 1], today), 4);
  // Trained today as well...
  assert.equal(currentStreak([...days, today - 1, today], today), 5);
  // ...or today only.
  assert.equal(currentStreak([today], today), 1);
});

test('a missed day ends the streak, and an empty history is simply zero', () => {
  const today = utcDayNumber(new Date('2026-10-17T10:00:00.000Z'));
  assert.equal(currentStreak([], today), 0);
  // Newest workout was two days ago: yesterday was missed.
  assert.equal(currentStreak([today - 2, today - 3], today), 0);
  // An old run is not a current streak, however long it was.
  assert.equal(currentStreak([today - 30, today - 31, today - 32], today), 0);
});

test('only the run touching today counts, and repeated days are one day', () => {
  const today = utcDayNumber(new Date('2026-10-17T10:00:00.000Z'));
  // Two sessions today plus a run a week earlier: today's day counts once.
  assert.equal(currentStreak([today, today, today - 7, today - 8], today), 1);
});

test('the longest streak is the best run ever, not the current one', () => {
  assert.equal(longestStreak([]), 0);
  assert.equal(longestStreak([5]), 1);
  assert.equal(longestStreak([5, 6, 7]), 3);
  // A long-past run beats a shorter current one.
  assert.equal(longestStreak([1, 2, 3, 4, 5, 20, 21]), 5);
  // Duplicates and unordered input do not change the answer.
  assert.equal(longestStreak([21, 20, 5, 4, 3, 2, 1, 20]), 5);
  // A day that overshoots today (clock skew) is counted in the run.
  assert.equal(longestStreak([10, 11, 12]), 3);
});

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

test('the dashboard requires a token', async () => {
  const { status } = await apiRequest(baseUrl, 'GET', '/api/dashboard');
  assert.equal(status, 401);
});

// ---------------------------------------------------------------------------
// Streaks through the API
// ---------------------------------------------------------------------------

dbTest('a brand new user has no streak at all', async () => {
  const { token } = await createUser('fresh');
  assert.deepEqual(await streakOf(token), {
    current: 0,
    longest: 0,
    trainedToday: false,
    activeDays: 0,
  });
});

dbTest('training today starts a one-day streak', async () => {
  const { token } = await createUser('onestart');
  await logWorkoutAt(token, daysAgo(0));

  const streak = await streakOf(token);
  assert.equal(streak.current, 1);
  assert.equal(streak.longest, 1);
  assert.equal(streak.trainedToday, true);
  assert.equal(streak.activeDays, 1);
});

dbTest('three consecutive days, including today, make a three-day streak', async () => {
  const { token } = await createUser('three');
  await logWorkoutAt(token, daysAgo(0));
  await logWorkoutAt(token, daysAgo(1));
  await logWorkoutAt(token, daysAgo(2));

  const streak = await streakOf(token);
  assert.equal(streak.current, 3);
  assert.equal(streak.longest, 3);
  assert.equal(streak.trainedToday, true);
});

dbTest('two workouts on one day still only count as one day', async () => {
  const { token } = await createUser('twice');
  await logWorkoutAt(token, daysAgo(0), 'Morning');
  await logWorkoutAt(token, daysAgo(0), 'Evening');

  const streak = await streakOf(token);
  assert.equal(streak.current, 1, 'consistency is per day, not per session');
  assert.equal(streak.activeDays, 1);
});

dbTest('a rest day that was not taken resets the current streak to zero', async () => {
  const { token } = await createUser('broken');
  // Trained three days ago and the two days before that, then stopped: the
  // run ended, and yesterday was missed, so nothing is current.
  await logWorkoutAt(token, daysAgo(4));
  await logWorkoutAt(token, daysAgo(3));
  await logWorkoutAt(token, daysAgo(2));

  const streak = await streakOf(token);
  assert.equal(streak.current, 0, 'a missed day ends the streak');
  assert.equal(streak.longest, 3, 'but the best run is remembered');
  assert.equal(streak.trainedToday, false);
});

dbTest('the streak survives a day with no workout logged yet', async () => {
  const { token } = await createUser('pending');
  await logWorkoutAt(token, daysAgo(1));
  await logWorkoutAt(token, daysAgo(2));

  const streak = await streakOf(token);
  assert.equal(streak.current, 2, 'yesterday keeps it alive until today ends');
  assert.equal(streak.trainedToday, false, 'so the app can nudge for today');
});

dbTest('the longest streak is kept after the current one is broken', async () => {
  const { token } = await createUser('longest');
  // A 5-day run a while back...
  for (let i = 0; i < 5; i += 1) await logWorkoutAt(token, daysAgo(20 + i));
  // ...then a fresh 2-day run ending yesterday.
  await logWorkoutAt(token, daysAgo(1));
  await logWorkoutAt(token, daysAgo(2));

  const streak = await streakOf(token);
  assert.equal(streak.current, 2);
  assert.equal(streak.longest, 5);
  assert.equal(streak.activeDays, 7);
});

dbTest('someone else’s workouts do not feed my streak', async () => {
  const mine = await createUser('mine');
  const theirs = await createUser('theirs');

  await logWorkoutAt(theirs.token, daysAgo(0));
  await logWorkoutAt(theirs.token, daysAgo(1));

  const streak = await streakOf(mine.token);
  assert.equal(streak.current, 0, 'a streak is strictly personal');
  assert.equal(streak.activeDays, 0);
});
