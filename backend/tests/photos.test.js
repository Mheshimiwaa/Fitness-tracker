'use strict';

/**
 * Profile-photo tests.
 *
 * Structure mirrors the other suites: a real HTTP server, a real MongoDB, and
 * `dbTest(...)` cases that skip themselves when the database is unreachable.
 *
 * Two things are set up BEFORE anything imports `src/config/env.js`, because
 * that module reads the environment once:
 *
 *   - UPLOADS_DIR points at a throwaway directory, so a test run can never
 *     touch (or litter) the photos a real user uploaded, and every file the
 *     suite creates is deleted in one go afterwards.
 *   - PHOTO_MAX_BYTES is tiny, so the "too large" case can be exercised with a
 *     few kilobytes instead of five megabytes.
 *
 * The uploaded bytes are not real pictures: the server identifies a format from
 * its MAGIC NUMBERS and stores the file, and whether the pixels inside are a
 * valid image is the app's concern (it re-encodes every photo before sending
 * it). Building the fixtures from their signatures is therefore both enough and
 * exactly what is under test.
 */

const os = require('os');
const path = require('path');
const fs = require('fs/promises');

const UPLOADS_DIR = path.join(os.tmpdir(), 'reprise-test-uploads');
const MAX_BYTES = 2048;

process.env.UPLOADS_DIR = UPLOADS_DIR;
process.env.PHOTO_MAX_BYTES = String(MAX_BYTES);

const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { startServer, stopServer, apiRequest } = require('./helpers/app');
const db = require('./helpers/db');
const { sniffImageFormat, storedFileName, deletePhoto } = require('../src/services/photo.service');

const PREFIX = 'p9';

// A leading signature is all the server looks at; the tail is just payload.
function pngBytes(tail = 'png-body') {
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    Buffer.from(tail),
  ]);
}

function jpegBytes(tail = 'jpeg-body') {
  return Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.from(tail)]);
}

function webpBytes(tail = 'webp-body') {
  return Buffer.concat([
    Buffer.from('RIFF'),
    Buffer.from([0x00, 0x00, 0x00, 0x00]), // size field: not inspected
    Buffer.from('WEBP'),
    Buffer.from(tail),
  ]);
}

/**
 * Upload raw bytes, the way the Android app does.
 *
 * `apiRequest` only sends JSON, so this posts the buffer directly.
 */
async function uploadPhoto(baseUrl, token, bytes, contentType) {
  const response = await fetch(`${baseUrl}/api/users/me/photo`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': contentType },
    body: bytes,
  });

  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }

  return { status: response.status, body };
}

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
  // Every file this suite wrote lives in the throwaway directory.
  await fs.rm(UPLOADS_DIR, { recursive: true, force: true });
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
      displayName: `P9 ${suffix}`,
      username: `${PREFIX}_${suffix}`,
      email: `${PREFIX}_${suffix}@example.com`,
      password: 'GoodPass123',
    },
  });
  assert.equal(status, 201, `expected registration to succeed, got ${status}`);
  return { token: body.token, user: body.user };
}

/** Fetch an uploaded photo and return { status, contentType, bytes }. */
async function fetchPhoto(url) {
  const response = await fetch(url);
  const bytes = Buffer.from(await response.arrayBuffer());
  return { status: response.status, contentType: response.headers.get('content-type'), bytes };
}

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

dbTest('an anonymous upload is refused', async () => {
  const response = await fetch(`${baseUrl}/api/users/me/photo`, {
    method: 'PUT',
    headers: { 'Content-Type': 'image/png' },
    body: pngBytes(),
  });

  assert.equal(response.status, 401);
  // Nothing should have been written for an unauthenticated caller.
  assert.deepEqual(await filesInUploadsDir(), [], 'no file should be written');
});

// ---------------------------------------------------------------------------
// Rejections
// ---------------------------------------------------------------------------

dbTest('a request with no body is refused with 415', async () => {
  const { token } = await createUser('nobody');

  const { status, body } = await uploadPhoto(baseUrl, token, '', 'image/png');

  assert.equal(status, 415);
  assert.match(body.error.message, /image\/jpeg, image\/png or image\/webp/);
});

dbTest('a body that is not an image at all is refused with 415', async () => {
  const { token } = await createUser('notimage');

  // The Content-Type claims PNG; the bytes are plainly not one.
  const { status, body } = await uploadPhoto(
    baseUrl,
    token,
    Buffer.from('this is not an image'),
    'image/png'
  );

  assert.equal(status, 415);
  assert.match(body.error.message, /not a JPEG, PNG or WebP/);
});

dbTest('a JSON body is refused rather than stored', async () => {
  const { token } = await createUser('jsonbody');

  // A JSON content type is not one the raw parser reads, so `req.body` stays an
  // empty object and the controller refuses it.
  const { status } = await apiRequest(baseUrl, 'PUT', '/api/users/me/photo', {
    token,
    body: { photo: 'data' },
  });

  assert.equal(status, 415);
});

dbTest('a photo over the size limit is refused with 413', async () => {
  const { token } = await createUser('toobig');

  const oversized = pngBytes('x'.repeat(MAX_BYTES + 100));
  const { status, body } = await uploadPhoto(baseUrl, token, oversized, 'image/png');

  assert.equal(status, 413);
  assert.match(body.error.message, /too large/i);
});

// ---------------------------------------------------------------------------
// Storing, serving and replacing
// ---------------------------------------------------------------------------

dbTest('an uploaded photo is stored, served back and set on the profile', async () => {
  const { token } = await createUser('upload');
  const bytes = pngBytes('the-photo');

  const { status, body } = await uploadPhoto(baseUrl, token, bytes, 'image/png');

  assert.equal(status, 200);
  const url = body.user.profileImageUrl;
  assert.ok(url, 'the response should carry a photo URL');
  assert.ok(url.startsWith(baseUrl), `expected an absolute URL, got ${url}`);
  assert.ok(url.endsWith('.png'), `expected a .png name, got ${url}`);

  // The bytes come back exactly as they went in.
  const served = await fetchPhoto(url);
  assert.equal(served.status, 200);
  assert.equal(served.contentType, 'image/png');
  assert.deepEqual(served.bytes, bytes);

  // ...and the profile now reports it.
  const me = await apiRequest(baseUrl, 'GET', '/api/users/me', { token });
  assert.equal(me.body.user.profileImageUrl, url);
});

dbTest('the stored format comes from the bytes, not the Content-Type', async () => {
  const { token } = await createUser('liar');

  // Claims PNG, sends a JPEG.
  const { status, body } = await uploadPhoto(baseUrl, token, jpegBytes(), 'image/png');

  assert.equal(status, 200);
  assert.ok(
    body.user.profileImageUrl.endsWith('.jpg'),
    `expected the sniffed .jpg extension, got ${body.user.profileImageUrl}`
  );
});

dbTest('a WebP is accepted', async () => {
  const { token } = await createUser('webp');

  const { status, body } = await uploadPhoto(baseUrl, token, webpBytes(), 'image/webp');

  assert.equal(status, 200);
  assert.ok(body.user.profileImageUrl.endsWith('.webp'));
});

dbTest('replacing a photo deletes the one it replaced', async () => {
  const { token } = await createUser('replace');

  const first = await uploadPhoto(baseUrl, token, pngBytes('first'), 'image/png');
  const oldUrl = first.body.user.profileImageUrl;

  const second = await uploadPhoto(baseUrl, token, pngBytes('second'), 'image/png');
  const newUrl = second.body.user.profileImageUrl;

  assert.notEqual(newUrl, oldUrl, 'each upload must get its own URL');

  // The new one is served; the old one is gone rather than left on disk.
  assert.equal((await fetchPhoto(newUrl)).status, 200);
  assert.equal((await fetchPhoto(oldUrl)).status, 404);
});

dbTest('another user can see the photo URL through search', async () => {
  const { token } = await createUser('seen');
  const { token: otherToken } = await createUser('watcher');

  const { body } = await uploadPhoto(baseUrl, token, pngBytes(), 'image/png');
  const url = body.user.profileImageUrl;

  const search = await apiRequest(baseUrl, 'GET', `/api/users/search?username=${PREFIX}_seen`, {
    token: otherToken,
  });

  assert.equal(search.status, 200);
  const found = search.body.users.find((user) => user.username === `${PREFIX}_seen`);
  assert.ok(found, 'the user should be findable');
  assert.equal(found.profileImageUrl, url);
});

// ---------------------------------------------------------------------------
// Removing
// ---------------------------------------------------------------------------

dbTest('deleting the photo clears the profile and the file', async () => {
  const { token } = await createUser('delete');

  const { body } = await uploadPhoto(baseUrl, token, pngBytes(), 'image/png');
  const url = body.user.profileImageUrl;

  const deleted = await apiRequest(baseUrl, 'DELETE', '/api/users/me/photo', { token });

  // A profile write answers with the updated user (the DELETE convention here),
  // so the client never has to re-read the profile.
  assert.equal(deleted.status, 200);
  assert.equal(deleted.body.user.profileImageUrl, null);
  assert.equal((await fetchPhoto(url)).status, 404, 'the file should be gone');
});

dbTest('deleting when there is no photo is harmless', async () => {
  const { token } = await createUser('nophoto');

  const { status, body } = await apiRequest(baseUrl, 'DELETE', '/api/users/me/photo', { token });

  assert.equal(status, 200);
  assert.equal(body.user.profileImageUrl, null);
});

dbTest('deleting a photo leaves a URL some other user set alone', async () => {
  const { token } = await createUser('external');

  // A user may PATCH in any absolute URL — that picture is not ours to delete.
  const external = 'https://example.com/avatar.png';
  await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token,
    body: { profileImageUrl: external },
  });

  const { status, body } = await apiRequest(baseUrl, 'DELETE', '/api/users/me/photo', { token });

  assert.equal(status, 200);
  assert.equal(body.user.profileImageUrl, null);
});

dbTest('removing an external URL deletes no local file', async () => {
  const { token } = await createUser('external2');

  await apiRequest(baseUrl, 'PATCH', '/api/users/me', {
    token,
    body: { profileImageUrl: 'https://example.com/avatar.png' },
  });

  const before = await filesInUploadsDir();
  await apiRequest(baseUrl, 'DELETE', '/api/users/me/photo', { token });

  assert.deepEqual(await filesInUploadsDir(), before, 'nothing on disk should change');
});

// ---------------------------------------------------------------------------
// The name guard (pure — no server, no database)
//
// `storedFileName` is the only thing standing between a stored URL and an
// `fs.unlink`, so it is tested directly rather than only through the endpoint.
// ---------------------------------------------------------------------------

test('only our own generated file names are recognised', () => {
  const valid = 'aaaaaaaaaaaaaaaaaaaaaaaa-0123456789abcdef.jpg';

  assert.equal(storedFileName(`http://localhost:4000/uploads/${valid}`), valid);
  assert.equal(storedFileName(`http://localhost:4000/uploads/${valid.replace('.jpg', '.webp')}`), valid.replace('.jpg', '.webp'));
});

test('names that are not ours are refused', () => {
  const cases = [
    null,
    undefined,
    '',
    'not a url',
    'https://example.com/avatar.png',
    // Right directory, but a name we would never have written.
    'http://localhost:4000/uploads/other.png',
    // Walking out of the uploads directory.
    'http://localhost:4000/uploads/../../etc/passwd',
    'http://localhost:4000/uploads/..%2f..%2fetc%2fpasswd',
    // Right shape, unsupported extension.
    'http://localhost:4000/uploads/aaaaaaaaaaaaaaaaaaaaaaaa-0123456789abcdef.gif',
    // Too short / not hex.
    'http://localhost:4000/uploads/deadbeef-0123456789abcdef.jpg',
    'http://localhost:4000/uploads/GGGGGGGGGGGGGGGGGGGGGGGG-0123456789abcdef.jpg',
  ];

  for (const url of cases) {
    assert.equal(storedFileName(url), null, `expected to refuse ${url}`);
  }
});

test('deleting a name that is not ours reports that nothing was removed', async () => {
  assert.equal(await deletePhoto('https://example.com/avatar.png'), false);
  assert.equal(await deletePhoto('/uploads/../../etc/passwd'), false);
  assert.equal(await deletePhoto(null), false);
});

test('a format is identified from the leading bytes, or not at all', () => {
  assert.equal(sniffImageFormat(pngBytes())?.extension, '.png');
  assert.equal(sniffImageFormat(jpegBytes())?.extension, '.jpg');
  assert.equal(sniffImageFormat(webpBytes())?.extension, '.webp');

  assert.equal(sniffImageFormat(Buffer.from('not an image')), null);
  assert.equal(sniffImageFormat(Buffer.alloc(0)), null);
  // A RIFF container that is not a WebP (e.g. a WAV file).
  assert.equal(sniffImageFormat(Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WAVE')])), null);
});

/** The file names currently in the uploads directory. */
async function filesInUploadsDir() {
  try {
    return await fs.readdir(UPLOADS_DIR);
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
}
