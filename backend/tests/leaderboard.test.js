'use strict';

/**
 * Phase 6b backend tests: the weekly leaderboard.
 *
 * Structure mirrors the other suites:
 *   - the pure tests (week boundary, points table) run everywhere
 *   - `dbTest(...)` cases skip themselves when MongoDB is unreachable
 *
 * Every user created here has a username starting with "p7". Cleanup removes
 * those users AND everything that points at them (weights, workouts,
 * friendships), so the suite is idempotent.
 *
 * Scoring is time-dependent by nature, so the DB tests deliberately train on
 * EVERY day of the current week so far and then assert against
 * `scoreFor(daysSoFar)`. That way they are correct whatever weekday they run on
 * — including a Monday, when the week so far is a single day.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const { startOfWeekUtc, scoreFor } = require('../src/services/leaderboard.service');

const PREFIX = 'p7';
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
      displayName: `P7 ${suffix}`,
      username: `${PREFIX}_${suffix}`,
      email: `${PREFIX}_${suffix}@example.com`,
      password: 'GoodPass123',
    },
  });
  assert.equal(status, 201, `expected registration to succeed, got ${status}`);
  return { token: body.token, user: body.user };
}

/** Make two users friends (a asks, b accepts). */
async function befriend(a, b) {
  await apiRequest(baseUrl, 'POST', '/api/friends/requests', {
    token: a.token,
    body: { username: b.user.username },
  });
  const { body } = await apiRequest(baseUrl, 'GET', '/api/friends/requests', { token: b.token });
  await apiRequest(baseUrl, 'POST', `/api/friends/requests/${body.incoming[0].id}/accept`, {
    token: b.token,
  });
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

/** How many days of the current Monday-start week have happened, incl. today. */
function daysSoFarThisWeek() {
  return ((new Date().getUTCDay() + 6) % 7) + 1;
}

/** The midnight-UTC start of day `index` (0 = Monday) of the current week. */
function dayOfThisWeek(index) {
  return new Date(startOfWeekUtc().getTime() + index * DAY_MS);
}

async function weeklyBoard(token) {
  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/leaderboard/weekly', { token });
  assert.equal(status, 200, `expected 200 fetching the leaderboard, got ${status}`);
  return body;
}

// ---------------------------------------------------------------------------
// Pure helpers (no database)
// ---------------------------------------------------------------------------

test('the week starts on Monday at midnight UTC', () => {
  // Wednesday 14 Oct 2026 falls in the week beginning Monday 12 Oct.
  assert.equal(
    startOfWeekUtc(new Date('2026-10-14T15:30:00.000Z')).toISOString(),
    '2026-10-12T00:00:00.000Z'
  );
  // A Monday is its own week start.
  assert.equal(
    startOfWeekUtc(new Date('2026-10-12T09:00:00.000Z')).toISOString(),
    '2026-10-12T00:00:00.000Z'
  );
  // Sunday night still belongs to the week that began six days earlier...
  assert.equal(
    startOfWeekUtc(new Date('2026-10-18T23:59:59.000Z')).toISOString(),
    '2026-10-12T00:00:00.000Z'
  );
  // ...and the next Monday opens a fresh week.
  assert.equal(
    startOfWeekUtc(new Date('2026-10-19T00:00:00.000Z')).toISOString(),
    '2026-10-19T00:00:00.000Z'
  );
});

test('points are per active day, with a bonus from three days', () => {
  assert.equal(scoreFor(0), 0);
  assert.equal(scoreFor(1), 10);
  assert.equal(scoreFor(2), 20);
  assert.equal(scoreFor(3), 35); // 3 x 10, plus the 5-point bonus
  assert.equal(scoreFor(4), 45);
  assert.equal(scoreFor(7), 75);
});

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

test('the weekly leaderboard requires a token', async () => {
  const { status } = await apiRequest(baseUrl, 'GET', '/api/leaderboard/weekly');
  assert.equal(status, 401);
});

// ---------------------------------------------------------------------------
// The board
// ---------------------------------------------------------------------------

dbTest('a user with no friends sees only themselves, on zero points', async () => {
  const solo = await createUser('solo');

  const board = await weeklyBoard(solo.token);

  assert.equal(board.entries.length, 1);
  assert.equal(board.entries[0].isMe, true);
  assert.equal(board.entries[0].user.username, 'p7_solo');
  assert.equal(board.entries[0].score, 0);
  assert.equal(board.entries[0].rank, 1);

  // `me` mirrors my own entry.
  assert.equal(board.me.user.username, 'p7_solo');
  assert.equal(board.me.score, 0);

  // The window and the rules travel with the data, so the app need not hard-code them.
  assert.ok(board.week.startDate, 'the week window is returned');
  assert.ok(board.week.endDate);
  assert.equal(board.scoring.pointsPerDay, 10);
  assert.equal(board.scoring.bonusDays, 3);
  assert.equal(board.scoring.bonusPoints, 5);
});

dbTest('the board is me and my friends — nobody else', async () => {
  const me = await createUser('me');
  const pal = await createUser('pal');
  await createUser('stranger'); // registered, but deliberately not a friend

  await befriend(me, pal);

  const board = await weeklyBoard(me.token);
  const names = board.entries.map((entry) => entry.user.username).sort();

  assert.deepEqual(names, ['p7_me', 'p7_pal']);
  // A friend who has not trained is still on the board, on zero.
  const palEntry = board.entries.find((entry) => entry.user.username === 'p7_pal');
  assert.equal(palEntry.score, 0);
  assert.equal(palEntry.isMe, false);
});

dbTest('a second workout on the same day adds nothing; three days earn the bonus', async () => {
  const me = await createUser('days');

  // Train once on every day of the week so far...
  const days = daysSoFarThisWeek();
  for (let i = 0; i < days; i += 1) {
    await logWorkoutAt(me.token, dayOfThisWeek(i));
  }
  // ...and a second time on the most recent day, which must not score again.
  await logWorkoutAt(me.token, dayOfThisWeek(days - 1), 'Evening');

  const entry = (await weeklyBoard(me.token)).entries[0];

  assert.equal(entry.activeDays, days, 'one active day per day trained');
  assert.equal(entry.workoutCount, days + 1, 'both sessions count in the total');
  assert.equal(entry.score, scoreFor(days), 'but only the first per day scores');
});

dbTest('a workout from a previous week does not count', async () => {
  const me = await createUser('lastweek');

  // Eight days back is always before this week's Monday.
  await logWorkoutAt(me.token, new Date(Date.now() - 8 * DAY_MS));

  const entry = (await weeklyBoard(me.token)).entries[0];
  assert.equal(entry.activeDays, 0);
  assert.equal(entry.workoutCount, 0);
  assert.equal(entry.score, 0);
});

dbTest('a trainer ranks above someone who has not trained', async () => {
  const strong = await createUser('rank_a');
  const idle = await createUser('rank_b');
  await befriend(strong, idle);

  await logWorkoutAt(strong.token, dayOfThisWeek(daysSoFarThisWeek() - 1));

  const board = await weeklyBoard(strong.token);
  const strongEntry = board.entries.find((entry) => entry.user.username === 'p7_rank_a');
  const idleEntry = board.entries.find((entry) => entry.user.username === 'p7_rank_b');

  assert.equal(board.entries[0].user.username, 'p7_rank_a');
  assert.equal(strongEntry.rank, 1);
  assert.equal(idleEntry.rank, 2);
  assert.equal(strongEntry.score, 10);
  assert.equal(idleEntry.score, 0);
});

dbTest('two people on the same score share a rank', async () => {
  const a = await createUser('tie_a');
  const b = await createUser('tie_b');
  await befriend(a, b);

  // Both train on exactly the same days, so they score identically.
  const days = daysSoFarThisWeek();
  for (let i = 0; i < days; i += 1) {
    await logWorkoutAt(a.token, dayOfThisWeek(i));
    await logWorkoutAt(b.token, dayOfThisWeek(i));
  }

  const board = await weeklyBoard(a.token);

  assert.equal(new Set(board.entries.map((entry) => entry.score)).size, 1);
  assert.equal(new Set(board.entries.map((entry) => entry.rank)).size, 1);
});
