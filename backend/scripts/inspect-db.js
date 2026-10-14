#!/usr/bin/env node
'use strict';

/**
 * Read-only database inspector for RepRise.
 *
 * Why this exists: `mongosh` is not installed in the project's environment, and
 * raw driver output is hard to read. This script gives a quick, human-readable
 * look at exactly what the app has stored — users, weight entries, workouts,
 * friendships, and the weekly leaderboard points.
 *
 * It NEVER writes: every call here is a `find` (or a `listIndexes`). It also
 * reuses the backend's OWN service functions for derived numbers (volume, BMI,
 * leaderboard score), so what you see cannot drift from what the API returns.
 *
 * Usage (run from the `backend/` directory so `require('mongodb')` resolves):
 *
 *   node scripts/inspect-db.js                    # overview: counts + users
 *   node scripts/inspect-db.js users
 *   node scripts/inspect-db.js user <username>    # everything about one person
 *   node scripts/inspect-db.js raw <username>     # the untouched document (incl. passwordHash)
 *   node scripts/inspect-db.js weights [username]
 *   node scripts/inspect-db.js workouts [username]
 *   node scripts/inspect-db.js friends [username]
 *   node scripts/inspect-db.js leaderboard [username]
 *   node scripts/inspect-db.js indexes
 *
 * Which database? The same one the API uses: `MONGODB_URI` from `.env`, or a
 * value already present in the environment. To inspect the local test server
 * while Atlas is down, pass it inline:
 *
 *   MONGODB_URI="mongodb://127.0.0.1:27017" node scripts/inspect-db.js
 */

const { connect, getDb, close } = require('../src/database/connection');
const { COLLECTIONS } = require('../src/database/collections');
const env = require('../src/config/env');
const { summariseWorkout, toPublicWorkout } = require('../src/services/workout.service');
const { toPublicEntry } = require('../src/services/weight.service');
const { findUserByUsername } = require('../src/services/user.service');
const { weeklyLeaderboard } = require('../src/services/leaderboard.service');
const { calculateBmi } = require('../src/services/bmi.service');
const { listFriends, listRequests } = require('../src/services/friendship.service');

// ---------------------------------------------------------------------------
// Tiny output helpers
// ---------------------------------------------------------------------------

function heading(text) {
  console.log(`\n${text}\n${'-'.repeat(text.length)}`);
}

function field(label, value) {
  console.log(`  ${label.padEnd(14)} ${value}`);
}

/** ISO timestamps, to the day — plenty for eyeballing stored data. */
function stamp(value) {
  if (!value) return '—';
  return new Date(value).toISOString().replace('T', ' ').slice(0, 16);
}

/** Hide any user:password@ in a connection string before printing it. */
function redactUri(uri) {
  return String(uri).replace(/\/\/[^@/]*@/, '//***:***@');
}

function collection(name) {
  return getDb().collection(name);
}

// ---------------------------------------------------------------------------
// Command: overview / users
// ---------------------------------------------------------------------------

async function printUsers() {
  const docs = await collection(COLLECTIONS.USERS)
    .find(
      {},
      {
        projection: {
          username: 1,
          displayName: 1,
          email: 1,
          fitnessGoal: 1,
          weightUnit: 1,
          heightCm: 1,
        },
      }
    )
    .sort({ username: 1 })
    .toArray();

  heading(`users (${docs.length})`);

  if (docs.length === 0) {
    console.log('  (none yet — register through the app)');
    return docs;
  }

  for (const user of docs) {
    const height = user.heightCm ? `, ${user.heightCm} cm` : '';
    console.log(
      `  ${user.username.padEnd(18)} ${user.displayName} <${user.email}>` +
        `  [${user.fitnessGoal}, ${user.weightUnit}${height}]`
    );
  }
  return docs;
}

async function overview() {
  heading('collections');
  for (const name of Object.values(COLLECTIONS)) {
    const count = await collection(name).countDocuments();
    field(name, count);
  }
  await printUsers();
}

// ---------------------------------------------------------------------------
// Command: user <username>
// ---------------------------------------------------------------------------

async function requireUser(username) {
  if (!username) {
    throw new Error(
      'This command needs a username, e.g. `node scripts/inspect-db.js user alex_lifter`.'
    );
  }
  const user = await findUserByUsername(username);
  if (!user) {
    throw new Error(`No user with username "${username}". Run ` + '`node scripts/inspect-db.js users` first.');
  }
  return user;
}

/** Print the weight history of one user, with a derived BMI per entry. */
async function printWeightsFor(user) {
  const entries = await collection(COLLECTIONS.WEIGHT_ENTRIES)
    .find({ userId: user._id })
    .sort({ measuredAt: -1 })
    .toArray();

  heading(`${user.username} — weight entries (${entries.length})`);

  if (entries.length === 0) {
    console.log('  (none)');
    return;
  }

  for (const entry of entries) {
    const shown = toPublicEntry(entry, user.weightUnit);
    const bmi = calculateBmi(shown.weightKg, user.heightCm);
    const bmiText = bmi ? `  BMI ${bmi.value} (${bmi.category})` : '';
    const note = shown.note ? `  "${shown.note}"` : '';
    // The STORED value is shown first: it proves the entry is kept exactly as
    // typed, even when the user's display unit has since changed.
    console.log(
      `  ${stamp(entry.measuredAt)}  stored ${entry.weight} ${entry.unit}` +
        `  ->  ${shown.weight} ${shown.unit}${bmiText}${note}`
    );
  }
}

/** Print the workouts of one user: name, date, volume, and every set. */
async function printWorkoutsFor(user) {
  const workouts = await collection(COLLECTIONS.WORKOUTS)
    .find({ userId: user._id })
    .sort({ workoutDate: -1 })
    .toArray();

  heading(`${user.username} — workouts (${workouts.length})`);

  if (workouts.length === 0) {
    console.log('  (none)');
    return;
  }

  for (const workout of workouts) {
    const totals = summariseWorkout(workout);
    const shown = toPublicWorkout(workout, user.weightUnit);
    const duration = shown.durationMinutes != null ? `${shown.durationMinutes} min` : 'no duration';
    console.log(
      `\n  ${stamp(shown.workoutDate)}  ${shown.name}  (${duration})` +
        `\n      ${totals.exerciseCount} exercises · ${totals.setCount} sets · ` +
        `${totals.totalReps} reps · ${totals.totalVolumeKg} kg volume`
    );
    if (shown.notes) console.log(`      note: "${shown.notes}"`);

    for (const exercise of shown.exercises) {
      const sets = exercise.sets
        .map((set) => `${set.reps}×${set.weight}${exercise.unit}`)
        .join(', ');
      console.log(`      · ${exercise.name}: ${sets}`);
    }
  }
}

/** Print friends and pending requests for one user. */
async function printFriendsFor(user) {
  const [friends, requests] = await Promise.all([
    listFriends(user._id),
    listRequests(user._id),
  ]);

  heading(`${user.username} — friends (${friends.length})`);
  if (friends.length === 0) {
    console.log('  (none)');
  } else {
    for (const friend of friends) console.log(`  ${friend.username.padEnd(18)} ${friend.displayName}`);
  }

  heading(`${user.username} — pending requests`);
  const describe = (list, arrow) =>
    list.map((request) => `  ${arrow} ${request.user.username.padEnd(16)} ${request.user.displayName}`);
  const incoming = describe(requests.incoming, 'IN ');
  const outgoing = describe(requests.outgoing, 'OUT');
  if (incoming.length === 0 && outgoing.length === 0) {
    console.log('  (none)');
  } else {
    [...incoming, ...outgoing].forEach((text) => console.log(text));
  }
}

/** Print the weekly leaderboard for one user, using the API's own service. */
async function printLeaderboardFor(user) {
  const board = await weeklyLeaderboard(user._id);

  heading(`weekly leaderboard — from ${user.username}'s point of view`);
  console.log(
    `  week:    ${stamp(board.week.startDate)} UTC .. ${stamp(board.week.endDate)} UTC (end exclusive)`
  );
  console.log(
    `  scoring: ${board.scoring.pointsPerDay}/active day, ` +
      `+${board.scoring.bonusPoints} at ${board.scoring.bonusDays}+ days`
  );

  console.log('');
  for (const entry of board.entries) {
    const me = entry.isMe ? '  <- you' : '';
    console.log(
      `  #${String(entry.rank).padEnd(2)} ${entry.user.username.padEnd(18)} ` +
        `${String(entry.score).padStart(3)} pts  ` +
        `(${entry.activeDays} active days, ${entry.workoutCount} workouts)${me}`
    );
  }
}

/**
 * Dump one user's document EXACTLY as stored — no formatting, no field hiding.
 *
 * This is the "show me the credentials" view: it includes `passwordHash` and the
 * `*Normalized` fields that the unique indexes sit on. Treat the output as a
 * secret (a bcrypt hash of a real password is still sensitive), which is also why
 * the connection string above is redacted.
 */
async function rawUser(username) {
  const user = await requireUser(username);
  console.log(`\nusers.findOne({ username: "${user.username}" })\n`);
  console.log(JSON.stringify(user, null, 2));
}

async function userDetail(username) {
  const user = await requireUser(username);

  heading(`user: ${user.username}`);
  field('display name', user.displayName);
  field('email', user.email);
  field('id', String(user._id));
  field('goal', user.fitnessGoal);
  field('unit', user.weightUnit);
  field('height', user.heightCm ? `${user.heightCm} cm` : '— (BMI unavailable)');
  field('created', stamp(user.createdAt));

  await printWeightsFor(user);
  await printWorkoutsFor(user);
  await printFriendsFor(user);
  await printLeaderboardFor(user);
}

// ---------------------------------------------------------------------------
// Commands: weights / workouts / friends / leaderboard (all users or one)
// ---------------------------------------------------------------------------

/** Either one named user, or every user (handy on a small test database). */
async function resolveUsers(username) {
  if (username) return [await requireUser(username)];
  return collection(COLLECTIONS.USERS).find({}).sort({ username: 1 }).toArray();
}

async function allWeights(username) {
  for (const user of await resolveUsers(username)) await printWeightsFor(user);
}

async function allWorkouts(username) {
  for (const user of await resolveUsers(username)) await printWorkoutsFor(user);
}

async function allFriends(username) {
  for (const user of await resolveUsers(username)) await printFriendsFor(user);
}

async function allLeaderboards(username) {
  for (const user of await resolveUsers(username)) await printLeaderboardFor(user);
}

// ---------------------------------------------------------------------------
// Command: indexes — the documented, enforced query guarantees
// ---------------------------------------------------------------------------

async function printIndexes() {
  for (const name of Object.values(COLLECTIONS)) {
    heading(`${name} — indexes`);
    const indexes = await collection(name).listIndexes().toArray();
    for (const index of indexes) {
      const keys = Object.entries(index.key)
        .map(([key, direction]) => `${key}:${direction}`)
        .join(', ');
      const unique = index.unique ? '  UNIQUE' : '';
      console.log(`  ${index.name.padEnd(28)} { ${keys} }${unique}`);
    }
  }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

const COMMANDS = {
  overview,
  users: printUsers,
  user: userDetail,
  raw: rawUser,
  weights: allWeights,
  workouts: allWorkouts,
  friends: allFriends,
  leaderboard: allLeaderboards,
  indexes: printIndexes,
};

async function main() {
  const [command = 'overview', argument] = process.argv.slice(2);

  if (command === 'help' || command === '--help' || command === '-h') {
    console.log('Commands: ' + Object.keys(COMMANDS).join(', '));
    return;
  }

  const run = COMMANDS[command];
  if (!run) {
    console.error(`Unknown command "${command}". Known: ${Object.keys(COMMANDS).join(', ')}`);
    process.exitCode = 1;
    return;
  }

  await connect();
  const db = getDb();
  console.log(`Connected to "${db.databaseName}"  (${redactUri(env.mongoUri)})`);

  await run(argument);
}

main()
  .then(close)
  .catch(async (error) => {
    console.error(`\nFailed: ${error.message}`);
    if (error.name === 'MongoServerSelectionError') {
      console.error(
        'Could not reach MongoDB. If Atlas is unreachable, inspect the local server with:\n' +
          '  MONGODB_URI="mongodb://127.0.0.1:27017" node scripts/inspect-db.js'
      );
    }
    await close().catch(() => {});
    process.exitCode = 1;
  });
