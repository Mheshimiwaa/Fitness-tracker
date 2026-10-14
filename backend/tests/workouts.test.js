'use strict';

/**
 * Phase 4 backend tests: workout logging.
 *
 * Structure mirrors weights.test.js:
 *   - pure schema tests run everywhere (no database needed)
 *   - `dbTest(...)` cases skip themselves when MongoDB is unreachable
 *
 * Every user created here has a username starting with "p4". Cleanup removes
 * those users AND their workouts, so the suite is idempotent.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const {
  createWorkoutSchema,
  updateWorkoutSchema,
  exerciseSchema,
} = require('../src/validators/workout.validators');

const PREFIX = 'p4';

const ALEX = {
  displayName: 'Alex Lifter',
  username: 'p4_alex',
  email: 'p4_alex@example.com',
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

/** A minimal valid workout body. */
function sampleWorkout(overrides = {}) {
  return {
    name: 'Push day',
    exercises: [
      {
        name: 'Bench Press',
        sets: [
          { reps: 8, weight: 60 },
          { reps: 8, weight: 60 },
          { reps: 6, weight: 65 },
        ],
      },
      {
        name: 'Overhead Press',
        sets: [{ reps: 10, weight: 30 }],
      },
    ],
    ...overrides,
  };
}

/** Log a workout, asserting the expected status. */
async function logWorkout(token, workout, expectedStatus = 201) {
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/workouts', {
    token,
    body: workout,
  });
  assert.equal(
    status,
    expectedStatus,
    `unexpected status for ${JSON.stringify(workout)}: ${JSON.stringify(body)}`
  );
  return body;
}

// ---------------------------------------------------------------------------
// Schema validation (no database required)
// ---------------------------------------------------------------------------

test('a workout needs a name and at least one exercise', () => {
  const noName = createWorkoutSchema.safeParse({ exercises: sampleWorkout().exercises });
  assert.equal(noName.success, false);

  const noExercises = createWorkoutSchema.safeParse({ name: 'Push day', exercises: [] });
  assert.equal(noExercises.success, false);
  assert.ok(
    noExercises.error.issues.some((issue) => issue.path.join('.') === 'exercises')
  );
});

test('an exercise needs at least one set, and a set needs whole reps', () => {
  const noSets = exerciseSchema.safeParse({ name: 'Squat', sets: [] });
  assert.equal(noSets.success, false);

  const fractionalReps = createWorkoutSchema.safeParse(
    sampleWorkout({
      exercises: [{ name: 'Squat', sets: [{ reps: 5.5, weight: 100 }] }],
    })
  );
  assert.equal(fractionalReps.success, false);
  // The failing path must point at the exact offending field.
  assert.ok(
    fractionalReps.error.issues.some(
      (issue) => issue.path.join('.') === 'exercises.0.sets.0.reps'
    ),
    'expected a nested path for the set reps'
  );
});

test('a set of zero weight is allowed (bodyweight and empty-bar work)', () => {
  const result = createWorkoutSchema.safeParse(
    sampleWorkout({
      exercises: [{ name: 'Pull-up', sets: [{ reps: 8, weight: 0 }] }],
    })
  );
  assert.equal(result.success, true);
  assert.equal(result.data.exercises[0].sets[0].weight, 0);
});

test('a negative weight is rejected', () => {
  const result = createWorkoutSchema.safeParse(
    sampleWorkout({
      exercises: [{ name: 'Squat', sets: [{ reps: 5, weight: -20 }] }],
    })
  );
  assert.equal(result.success, false);
});

test('workout numbers arrive as strings from a form and are coerced', () => {
  const result = createWorkoutSchema.safeParse({
    name: 'Leg day',
    durationMinutes: '45',
    exercises: [{ name: 'Squat', restSeconds: '90', sets: [{ reps: '5', weight: '100' }] }],
  });

  assert.equal(result.success, true);
  assert.equal(result.data.durationMinutes, 45);
  assert.equal(result.data.exercises[0].restSeconds, 90);
  assert.equal(result.data.exercises[0].sets[0].reps, 5);
  assert.equal(result.data.exercises[0].sets[0].weight, 100);
});

test('an empty note is normalised to null', () => {
  const result = createWorkoutSchema.safeParse(sampleWorkout({ notes: '   ' }));
  assert.equal(result.success, true);
  assert.equal(result.data.notes, null);
});

test('a workout date in the future is rejected, a back-dated one is accepted', () => {
  const tomorrow = new Date(Date.now() + DAY_MS).toISOString();
  const lastWeek = new Date(Date.now() - 7 * DAY_MS).toISOString();

  assert.equal(createWorkoutSchema.safeParse(sampleWorkout({ workoutDate: tomorrow })).success, false);

  const backDated = createWorkoutSchema.safeParse(
    sampleWorkout({ workoutDate: lastWeek })
  );
  assert.equal(backDated.success, true);
});

test('an empty update body is rejected', () => {
  assert.equal(updateWorkoutSchema.safeParse({}).success, false);
});

test('an update accepts an explicit null to CLEAR the duration and note', () => {
  // Without this, a field could be set but never removed: PATCH would have no
  // way to say "make this blank again".
  const result = updateWorkoutSchema.safeParse({ durationMinutes: null, notes: null });

  assert.equal(result.success, true);
  assert.equal(result.data.durationMinutes, null);
  assert.equal(result.data.notes, null);
});

test('an update still rejects a nonsense duration', () => {
  assert.equal(updateWorkoutSchema.safeParse({ durationMinutes: 0 }).success, false);
  assert.equal(updateWorkoutSchema.safeParse({ durationMinutes: 'soon' }).success, false);
});

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

test('workout endpoints require a token', async () => {
  const list = await apiRequest(baseUrl, 'GET', '/api/workouts');
  const create = await apiRequest(baseUrl, 'POST', '/api/workouts', {
    body: sampleWorkout(),
  });
  const exercises = await apiRequest(baseUrl, 'GET', '/api/workouts/exercises');

  assert.equal(list.status, 401);
  assert.equal(create.status, 401);
  assert.equal(exercises.status, 401);
});

// ---------------------------------------------------------------------------
// Creating and reading
// ---------------------------------------------------------------------------

dbTest('logging a workout returns 201, saves it and derives the summary', async () => {
  const { token, user } = await createUser(ALEX);
  await updateProfile(token, { weightUnit: 'kg' });

  const { workout } = await logWorkout(token, sampleWorkout());

  assert.equal(workout.name, 'Push day');
  assert.ok(workout.id);
  assert.equal(workout.exercises.length, 2);
  assert.equal(workout.exercises[0].name, 'Bench Press');
  // Unit defaulted from the profile preference.
  assert.equal(workout.exercises[0].unit, 'kg');
  assert.equal(workout.exercises[0].sets[0].weight, 60);
  assert.equal(workout.exercises[0].sets[0].weightKg, 60);

  // 3 bench sets + 1 press set, and 8*60 + 8*60 + 6*65 + 10*30 = 1650 kg.
  assert.equal(workout.summary.exerciseCount, 2);
  assert.equal(workout.summary.setCount, 4);
  assert.equal(workout.summary.totalReps, 32);
  assert.equal(workout.summary.totalVolumeKg, 1650);

  // It must really be in the database, not just echoed back.
  assert.equal(await db.countWorkouts(user.id), 1);
});

dbTest('a workout logged in pounds is reported in the user\u2019s preferred unit', async () => {
  const { token } = await createUser({
    displayName: 'Pound Lifter',
    username: 'p4_pounds',
    email: 'p4_pounds@example.com',
    password: 'GoodPass123',
  });
  await updateProfile(token, { weightUnit: 'kg' });

  // 100 lb on the bar, but the account prefers kilograms.
  const { workout } = await logWorkout(
    token,
    { name: 'Volume', exercises: [{ name: 'Row', unit: 'lb', sets: [{ reps: 10, weight: 100 }] }] }
  );

  assert.equal(workout.exercises[0].unit, 'kg');
  // 100 lb = 45.359237 kg
  assert.equal(workout.exercises[0].sets[0].weight, 45.4);
  assert.equal(workout.exercises[0].sets[0].weightKg, 45.36);
  // Volume is always computed in kilograms.
  assert.equal(workout.summary.totalVolumeKg, 453.6);
});

dbTest('an implausible load is rejected with a 400 naming the exact set', async () => {
  const { token } = await createUser({
    displayName: 'Range Tester',
    username: 'p4_range',
    email: 'p4_range@example.com',
    password: 'GoodPass123',
  });

  // Unit omitted -> defaults to kg -> 5000 kg is not a real bar load.
  const { status, body } = await apiRequest(baseUrl, 'POST', '/api/workouts', {
    token,
    body: {
      name: 'Impossible',
      exercises: [
        { name: 'Bench Press', sets: [{ reps: 5, weight: 5000 }] },
      ],
    },
  });

  assert.equal(status, 400);
  assert.ok(
    body.error.details.some(
      (detail) => detail.path === 'exercises.0.sets.0.weight'
    ),
    'expected a field-level detail pointing at the offending set'
  );
});

dbTest('history is newest first and the days filter excludes older sessions', async () => {
  const { token } = await createUser({
    displayName: 'History Tester',
    username: 'p4_hist',
    email: 'p4_hist@example.com',
    password: 'GoodPass123',
  });

  const now = Date.now();
  await logWorkout(
    token,
    sampleWorkout({ name: 'Old', workoutDate: new Date(now - 100 * DAY_MS).toISOString() })
  );
  await logWorkout(
    token,
    sampleWorkout({ name: 'Recent', workoutDate: new Date(now - 2 * DAY_MS).toISOString() })
  );

  const all = await apiRequest(baseUrl, 'GET', '/api/workouts', { token });
  assert.equal(all.status, 200);
  assert.equal(all.body.count, 2);
  assert.deepEqual(
    all.body.workouts.map((workout) => workout.name),
    ['Recent', 'Old'],
    'workouts should be sorted newest first'
  );

  const windowed = await apiRequest(baseUrl, 'GET', '/api/workouts?days=7', { token });
  assert.equal(windowed.body.count, 1);
  assert.equal(windowed.body.workouts[0].name, 'Recent');
  assert.equal(windowed.body.days, 7);
});

dbTest('the exercise list returns this user\u2019s distinct names, alphabetically', async () => {
  const { token } = await createUser({
    displayName: 'Catalogue Tester',
    username: 'p4_catalogue',
    email: 'p4_catalogue@example.com',
    password: 'GoodPass123',
  });

  await logWorkout(token, sampleWorkout());
  await logWorkout(
    token,
    {
      name: 'Pull day',
      exercises: [{ name: 'Deadlift', sets: [{ reps: 5, weight: 120 }] }],
    }
  );

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/workouts/exercises', {
    token,
  });

  assert.equal(status, 200);
  assert.deepEqual(body.exercises, ['Bench Press', 'Deadlift', 'Overhead Press']);
});

dbTest('the exercise list is empty for a user who has never trained', async () => {
  const { token } = await createUser({
    displayName: 'Fresh Start',
    username: 'p4_fresh',
    email: 'p4_fresh@example.com',
    password: 'GoodPass123',
  });

  const { body } = await apiRequest(baseUrl, 'GET', '/api/workouts/exercises', { token });
  assert.deepEqual(body.exercises, []);
});

// ---------------------------------------------------------------------------
// Updating and deleting
// ---------------------------------------------------------------------------

dbTest('a workout can be edited, including replacing its exercises', async () => {
  const { token } = await createUser({
    displayName: 'Edit Tester',
    username: 'p4_edit',
    email: 'p4_edit@example.com',
    password: 'GoodPass123',
  });

  const { workout } = await logWorkout(token, sampleWorkout({ durationMinutes: 60, notes: 'felt strong' }));

  const patched = await apiRequest(baseUrl, 'PATCH', `/api/workouts/${workout.id}`, {
    token,
    body: {
      name: 'Push day (deload)',
      durationMinutes: 40,
      notes: 'taking it easy',
      exercises: [
        { name: 'Bench Press', sets: [{ reps: 12, weight: 40 }] },
      ],
    },
  });

  assert.equal(patched.status, 200);
  assert.equal(patched.body.workout.name, 'Push day (deload)');
  assert.equal(patched.body.workout.durationMinutes, 40);
  assert.equal(patched.body.workout.notes, 'taking it easy');
  assert.equal(patched.body.workout.exercises.length, 1);
  assert.equal(patched.body.workout.exercises[0].sets[0].weight, 40);
  assert.equal(patched.body.workout.summary.totalVolumeKg, 480);
});

dbTest('an explicit null clears the duration and the note', async () => {
  const { token } = await createUser({
    displayName: 'Clear Tester',
    username: 'p4_clear',
    email: 'p4_clear@example.com',
    password: 'GoodPass123',
  });

  const { workout } = await logWorkout(
    token,
    sampleWorkout({ durationMinutes: 52, notes: 'felt strong' })
  );
  assert.equal(workout.durationMinutes, 52);
  assert.equal(workout.notes, 'felt strong');

  const cleared = await apiRequest(baseUrl, 'PATCH', `/api/workouts/${workout.id}`, {
    token,
    body: { durationMinutes: null, notes: null },
  });

  assert.equal(cleared.status, 200);
  assert.equal(cleared.body.workout.durationMinutes, null);
  assert.equal(cleared.body.workout.notes, null);

  // And it sticks: the fields really are gone, not merely hidden in the reply.
  const reread = await apiRequest(baseUrl, 'GET', `/api/workouts/${workout.id}`, { token });
  assert.equal(reread.body.workout.durationMinutes, null);
  assert.equal(reread.body.workout.notes, null);
});

dbTest('a workout can be deleted, after which it is a 404', async () => {
  const { token } = await createUser({
    displayName: 'Delete Tester',
    username: 'p4_delete',
    email: 'p4_delete@example.com',
    password: 'GoodPass123',
  });

  const { workout } = await logWorkout(token, sampleWorkout());

  const removed = await apiRequest(baseUrl, 'DELETE', `/api/workouts/${workout.id}`, {
    token,
  });
  assert.equal(removed.status, 204);

  const gone = await apiRequest(baseUrl, 'GET', `/api/workouts/${workout.id}`, { token });
  assert.equal(gone.status, 404);
});

dbTest('one user cannot read, edit or delete another user\u2019s workout', async () => {
  const owner = await createUser({
    displayName: 'Owner',
    username: 'p4_owner',
    email: 'p4_owner@example.com',
    password: 'GoodPass123',
  });
  const stranger = await createUser({
    displayName: 'Stranger',
    username: 'p4_stranger',
    email: 'p4_stranger@example.com',
    password: 'GoodPass123',
  });

  const { workout } = await logWorkout(owner.token, sampleWorkout());

  const read = await apiRequest(baseUrl, 'GET', `/api/workouts/${workout.id}`, {
    token: stranger.token,
  });
  const edit = await apiRequest(baseUrl, 'PATCH', `/api/workouts/${workout.id}`, {
    token: stranger.token,
    body: { name: 'Hijacked' },
  });
  const remove = await apiRequest(baseUrl, 'DELETE', `/api/workouts/${workout.id}`, {
    token: stranger.token,
  });

  // 404 (not 403) so the API cannot be used to discover which ids exist.
  assert.equal(read.status, 404);
  assert.equal(edit.status, 404);
  assert.equal(remove.status, 404);

  // The owner's workout is untouched.
  const stillThere = await apiRequest(baseUrl, 'GET', `/api/workouts/${workout.id}`, {
    token: owner.token,
  });
  assert.equal(stillThere.status, 200);
  assert.equal(stillThere.body.workout.name, 'Push day');
});

dbTest('a malformed workout id is a 400, an unknown one is a 404', async () => {
  const { token } = await createUser({
    displayName: 'Id Tester',
    username: 'p4_id',
    email: 'p4_id@example.com',
    password: 'GoodPass123',
  });

  const bad = await apiRequest(baseUrl, 'GET', '/api/workouts/not-an-id', { token });
  const unknown = await apiRequest(
    baseUrl,
    'GET',
    '/api/workouts/000000000000000000000000',
    { token }
  );

  assert.equal(bad.status, 400);
  assert.equal(unknown.status, 404);
});

// ---------------------------------------------------------------------------
// Dashboard integration
// ---------------------------------------------------------------------------

dbTest('the dashboard reports recent training volume', async () => {
  const { token } = await createUser({
    displayName: 'Dashboard Tester',
    username: 'p4_dash',
    email: 'p4_dash@example.com',
    password: 'GoodPass123',
  });

  const now = Date.now();
  // Inside the recent window: 10 reps x 100 kg = 1000 kg of volume.
  await logWorkout(
    token,
    {
      name: 'This week',
      workoutDate: new Date(now - DAY_MS).toISOString(),
      exercises: [{ name: 'Squat', sets: [{ reps: 10, weight: 100 }] }],
    }
  );
  // Outside the window: counted in the total, excluded from the recent numbers.
  await logWorkout(
    token,
    {
      name: 'Long ago',
      workoutDate: new Date(now - 60 * DAY_MS).toISOString(),
      exercises: [{ name: 'Squat', sets: [{ reps: 5, weight: 100 }] }],
    }
  );

  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });
  assert.equal(status, 200);

  assert.equal(body.workouts.totalCount, 2);
  assert.equal(body.workouts.recentDays, 7);
  assert.equal(body.workouts.recentCount, 1);
  assert.equal(body.workouts.recentVolumeKg, 1000);
  assert.ok(body.workouts.lastWorkoutAt);
});

dbTest('the dashboard reports honest zeros when nothing has been logged', async () => {
  const { token } = await createUser({
    displayName: 'No Training',
    username: 'p4_notraining',
    email: 'p4_notraining@example.com',
    password: 'GoodPass123',
  });

  const { body } = await apiRequest(baseUrl, 'GET', '/api/dashboard', { token });

  assert.deepEqual(body.workouts, {
    totalCount: 0,
    recentCount: 0,
    recentDays: 7,
    recentVolumeKg: 0,
    lastWorkoutAt: null,
  });
});
