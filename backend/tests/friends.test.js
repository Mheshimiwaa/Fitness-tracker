'use strict';

/**
 * Phase 6a backend tests: friends.
 *
 * Structure mirrors the other suites:
 *   - the pure tests (pair key, schemas) run everywhere, with no database
 *   - `dbTest(...)` cases skip themselves when MongoDB is unreachable
 *
 * Every user created here has a username starting with "p6". Cleanup removes
 * those users AND their friendships, so the suite is idempotent.
 */

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const { pairKeyOf } = require('../src/services/friendship.service');
const {
  sendFriendRequestSchema,
  friendRequestIdParamSchema,
  friendUserIdParamSchema,
} = require('../src/validators/friendship.validators');

const PREFIX = 'p6';

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
      displayName: `P6 ${suffix}`,
      username: `${PREFIX}_${suffix}`,
      email: `${PREFIX}_${suffix}@example.com`,
      password: 'GoodPass123',
    },
  });
  assert.equal(status, 201, `expected registration to succeed, got ${status}`);
  return { token: body.token, user: body.user };
}

function sendRequest(token, username) {
  return apiRequest(baseUrl, 'POST', '/api/friends/requests', {
    token,
    body: { username },
  });
}

async function listRequests(token) {
  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/friends/requests', { token });
  assert.equal(status, 200, `expected 200 listing requests, got ${status}`);
  return body;
}

async function listFriends(token) {
  const { status, body } = await apiRequest(baseUrl, 'GET', '/api/friends', { token });
  assert.equal(status, 200, `expected 200 listing friends, got ${status}`);
  return body.friends;
}

// ---------------------------------------------------------------------------
// Pure helpers (no database)
// ---------------------------------------------------------------------------

test('the pair key is identical whichever way round the two users are', () => {
  const a = 'aaaaaaaaaaaaaaaaaaaaaaaa';
  const b = 'bbbbbbbbbbbbbbbbbbbbbbbb';

  assert.equal(pairKeyOf(a, b), pairKeyOf(b, a));
  // Different pairs must not collide.
  assert.notEqual(pairKeyOf(a, b), pairKeyOf(a, 'cccccccccccccccccccccccc'));
});

test('a friend request needs a valid username', () => {
  assert.equal(sendFriendRequestSchema.safeParse({}).success, false);
  assert.equal(sendFriendRequestSchema.safeParse({ username: 'ab' }).success, false); // too short
  assert.equal(sendFriendRequestSchema.safeParse({ username: '1bad' }).success, false); // must start with a letter
  assert.equal(sendFriendRequestSchema.safeParse({ username: 'good_user' }).success, true);
});

test('the request-id parameter must look like an id', () => {
  assert.equal(friendRequestIdParamSchema.safeParse({ id: 'nope' }).success, false);
  assert.equal(friendRequestIdParamSchema.safeParse({ id: '' }).success, false);
  assert.equal(
    friendRequestIdParamSchema.safeParse({ id: '5f1d7b9c2a4e6f8a0b1c2d3e' }).success,
    true
  );
  assert.equal(friendUserIdParamSchema.safeParse({ userId: 'nope' }).success, false);
});

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

test('every friends route requires a token', async () => {
  const id = '5f1d7b9c2a4e6f8a0b1c2d3e';

  const routes = [
    ['POST', '/api/friends/requests'],
    ['GET', '/api/friends/requests'],
    ['POST', `/api/friends/requests/${id}/accept`],
    ['DELETE', `/api/friends/requests/${id}`],
    ['GET', '/api/friends'],
    ['DELETE', `/api/friends/${id}`],
  ];

  for (const [method, path] of routes) {
    const { status } = await apiRequest(baseUrl, method, path, {
      body: method === 'POST' && path.endsWith('requests') ? { username: 'someone' } : undefined,
    });
    assert.equal(status, 401, `${method} ${path} should require a token`);
  }
});

// ---------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------

dbTest('a request shows up for the recipient and for the sender', async () => {
  const alice = await createUser('alice');
  const bob = await createUser('bob');

  const { status, body } = await sendRequest(alice.token, bob.user.username);
  assert.equal(status, 201);
  assert.equal(body.request.direction, 'outgoing');
  assert.equal(body.request.user.username, bob.user.username);

  const aliceView = await listRequests(alice.token);
  assert.equal(aliceView.outgoing.length, 1);
  assert.equal(aliceView.incoming.length, 0);
  assert.equal(aliceView.outgoing[0].user.username, bob.user.username);

  const bobView = await listRequests(bob.token);
  assert.equal(bobView.incoming.length, 1);
  assert.equal(bobView.outgoing.length, 0);
  assert.equal(bobView.incoming[0].user.username, alice.user.username);

  // The same row, seen from both sides.
  assert.equal(aliceView.outgoing[0].id, bobView.incoming[0].id);
});

dbTest('a pair can never hold more than one friendship row', async () => {
  const carol = await createUser('carol');
  const dave = await createUser('dave');

  await sendRequest(carol.token, dave.user.username);

  // Sending the same request again is a conflict...
  const { status } = await sendRequest(carol.token, dave.user.username);
  assert.equal(status, 409);

  // ...and so is the reverse direction while the first is still pending.
  const { status: reverseStatus } = await sendRequest(dave.token, carol.user.username);
  assert.equal(reverseStatus, 409);

  // Crucially, neither attempt added a row.
  assert.equal(await db.countFriendshipsFor(carol.user.id), 1);
});

dbTest('you cannot send a request to yourself', async () => {
  const erin = await createUser('erin');

  const { status } = await sendRequest(erin.token, erin.user.username);
  assert.equal(status, 400);
});

dbTest('a request to a username that does not exist is a 404', async () => {
  const frank = await createUser('frank');

  const { status } = await sendRequest(frank.token, 'p6_nobody_here');
  assert.equal(status, 404);
});

// ---------------------------------------------------------------------------
// Accepting
// ---------------------------------------------------------------------------

dbTest('accepting makes both users friends', async () => {
  const gina = await createUser('gina');
  const hugo = await createUser('hugo');

  await sendRequest(gina.token, hugo.user.username);
  const hugoView = await listRequests(hugo.token);
  const requestId = hugoView.incoming[0].id;

  const { status, body } = await apiRequest(
    baseUrl,
    'POST',
    `/api/friends/requests/${requestId}/accept`,
    { token: hugo.token }
  );
  assert.equal(status, 200);
  assert.equal(body.friend.username, gina.user.username);

  // The request is no longer pending for either of them...
  assert.equal((await listRequests(hugo.token)).incoming.length, 0);
  assert.equal((await listRequests(gina.token)).outgoing.length, 0);

  // ...and both now list each other as a friend.
  const ginaFriends = await listFriends(gina.token);
  const hugoFriends = await listFriends(hugo.token);
  assert.deepEqual(ginaFriends.map((f) => f.username), [hugo.user.username]);
  assert.deepEqual(hugoFriends.map((f) => f.username), [gina.user.username]);
});

dbTest('only the recipient can accept a request', async () => {
  const ivy = await createUser('ivy');
  const jack = await createUser('jack');
  const kate = await createUser('kate'); // unrelated

  const { body: sent } = await sendRequest(ivy.token, jack.user.username);
  const requestId = sent.request.id;

  // The sender cannot accept their own request...
  const { status: senderStatus } = await apiRequest(
    baseUrl,
    'POST',
    `/api/friends/requests/${requestId}/accept`,
    { token: ivy.token }
  );
  assert.equal(senderStatus, 404);

  // ...and neither can a complete outsider.
  const { status: outsiderStatus } = await apiRequest(
    baseUrl,
    'POST',
    `/api/friends/requests/${requestId}/accept`,
    { token: kate.token }
  );
  assert.equal(outsiderStatus, 404);

  // The request is untouched, so the real recipient can still accept it.
  const { status: recipientStatus } = await apiRequest(
    baseUrl,
    'POST',
    `/api/friends/requests/${requestId}/accept`,
    { token: jack.token }
  );
  assert.equal(recipientStatus, 200);
});

dbTest('accepting an already-answered request is a conflict', async () => {
  const liam = await createUser('liam');
  const mia = await createUser('mia');

  const { body: sent } = await sendRequest(liam.token, mia.user.username);
  const requestId = sent.request.id;

  await apiRequest(baseUrl, 'POST', `/api/friends/requests/${requestId}/accept`, {
    token: mia.token,
  });
  const { status } = await apiRequest(
    baseUrl,
    'POST',
    `/api/friends/requests/${requestId}/accept`,
    { token: mia.token }
  );

  assert.equal(status, 409);
});

dbTest('accepting an unknown request id is a 404', async () => {
  const nina = await createUser('nina');

  const { status } = await apiRequest(
    baseUrl,
    'POST',
    '/api/friends/requests/5f1d7b9c2a4e6f8a0b1c2d3e/accept',
    { token: nina.token }
  );
  assert.equal(status, 404);
});

// ---------------------------------------------------------------------------
// Declining and withdrawing
// ---------------------------------------------------------------------------

dbTest('the recipient can decline a request', async () => {
  const omar = await createUser('omar');
  const pia = await createUser('pia');

  const { body: sent } = await sendRequest(omar.token, pia.user.username);
  const requestId = sent.request.id;

  const { status } = await apiRequest(baseUrl, 'DELETE', `/api/friends/requests/${requestId}`, {
    token: pia.token,
  });
  assert.equal(status, 204);

  assert.equal((await listRequests(pia.token)).incoming.length, 0);
  assert.equal((await listRequests(omar.token)).outgoing.length, 0);
  assert.deepEqual(await listFriends(pia.token), []);

  // The request is gone, so a second decline is a 404.
  const { status: again } = await apiRequest(
    baseUrl,
    'DELETE',
    `/api/friends/requests/${requestId}`,
    { token: pia.token }
  );
  assert.equal(again, 404);
});

dbTest('the sender can withdraw a request', async () => {
  const quinn = await createUser('quinn');
  const rita = await createUser('rita');

  const { body: sent } = await sendRequest(quinn.token, rita.user.username);
  const requestId = sent.request.id;

  const { status } = await apiRequest(baseUrl, 'DELETE', `/api/friends/requests/${requestId}`, {
    token: quinn.token,
  });
  assert.equal(status, 204);

  assert.equal((await listRequests(quinn.token)).outgoing.length, 0);
  assert.equal((await listRequests(rita.token)).incoming.length, 0);
});

dbTest('an unrelated user cannot delete a request they are not part of', async () => {
  const sam = await createUser('sam');
  const tara = await createUser('tara');
  const uma = await createUser('uma');

  const { body: sent } = await sendRequest(sam.token, tara.user.username);
  const requestId = sent.request.id;

  const { status } = await apiRequest(baseUrl, 'DELETE', `/api/friends/requests/${requestId}`, {
    token: uma.token,
  });
  assert.equal(status, 404);

  // Still pending for the two people who matter.
  assert.equal((await listRequests(tara.token)).incoming.length, 1);
});

// ---------------------------------------------------------------------------
// Removing a friend
// ---------------------------------------------------------------------------

dbTest('a friendship can be removed by either person', async () => {
  const vic = await createUser('vic');
  const wes = await createUser('wes');

  await sendRequest(vic.token, wes.user.username);
  const wesView = await listRequests(wes.token);
  await apiRequest(baseUrl, 'POST', `/api/friends/requests/${wesView.incoming[0].id}/accept`, {
    token: wes.token,
  });

  // Vic removes the friendship, addressing Wes by his user id.
  const { status } = await apiRequest(baseUrl, 'DELETE', `/api/friends/${wes.user.id}`, {
    token: vic.token,
  });
  assert.equal(status, 204);

  assert.deepEqual(await listFriends(vic.token), []);
  assert.deepEqual(await listFriends(wes.token), []);
  assert.equal(await db.countFriendshipsFor(vic.user.id), 0);

  // Removing again is a 404 — there is nothing left to remove.
  const { status: again } = await apiRequest(baseUrl, 'DELETE', `/api/friends/${wes.user.id}`, {
    token: vic.token,
  });
  assert.equal(again, 404);
});

dbTest('becoming friends then requesting again is a conflict', async () => {
  const xena = await createUser('xena');
  const yuri = await createUser('yuri');

  await sendRequest(xena.token, yuri.user.username);
  const yuriView = await listRequests(yuri.token);
  await apiRequest(baseUrl, 'POST', `/api/friends/requests/${yuriView.incoming[0].id}/accept`, {
    token: yuri.token,
  });

  const { status } = await sendRequest(xena.token, yuri.user.username);
  assert.equal(status, 409);
  assert.equal(await db.countFriendshipsFor(xena.user.id), 1);
});

dbTest('friends are listed alphabetically', async () => {
  const zed = await createUser('zed');

  // Two friends, added in an order that is NOT alphabetical by username.
  for (const suffix of ['yolanda', 'wanda']) {
    const other = await createUser(suffix);
    await sendRequest(zed.token, other.user.username);
    const view = await listRequests(other.token);
    await apiRequest(baseUrl, 'POST', `/api/friends/requests/${view.incoming[0].id}/accept`, {
      token: other.token,
    });
  }

  const friends = await listFriends(zed.token);
  assert.deepEqual(
    friends.map((f) => f.username),
    ['p6_wanda', 'p6_yolanda']
  );
});
