'use strict';

/**
 * Phase 1 backend tests.
 *
 * Run with:  npm test
 *
 * These use Node's built-in test runner (`node:test`) and the built-in `fetch`,
 * so no extra test dependencies are required. Each test starts the Express app
 * on an ephemeral port (port 0 = "pick any free port"), which avoids clashing
 * with a running dev server on port 4000.
 */

const test = require('node:test');
const assert = require('node:assert/strict');

const app = require('../src/app');

/** Start the app on a random free port and resolve once it is listening. */
function startServer() {
  return new Promise((resolve) => {
    const server = app.listen(0, '127.0.0.1', () => resolve(server));
  });
}

/** Small helper: start server, run `fn(baseUrl)`, always stop the server. */
async function withServer(fn) {
  const server = await startServer();
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  try {
    return await fn(baseUrl);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

test('unknown routes return a 404 with the standard error shape', async () => {
  await withServer(async (baseUrl) => {
    const res = await fetch(`${baseUrl}/api/definitely-not-a-route`);
    assert.equal(res.status, 404);

    const body = await res.json();
    assert.ok(body.error, 'response should contain an "error" object');
    assert.match(body.error.message, /Route not found/);
  });
});

test('health check reports "degraded" when the database is not connected', async () => {
  // This test process never calls connect(), so the ping must fail.
  await withServer(async (baseUrl) => {
    const res = await fetch(`${baseUrl}/api/health`);
    assert.equal(res.status, 503);

    const body = await res.json();
    assert.equal(body.status, 'degraded');
    assert.equal(body.database.connected, false);
  });
});

test('health check reports "ok" when the database is reachable', async (t) => {
  // Integration test: needs a running MongoDB. If it is not reachable we skip
  // rather than fail, so unit-only CI setups still pass.
  const { connect, close } = require('../src/database/connection');
  try {
    await connect();
  } catch (error) {
    t.skip(`MongoDB not reachable, skipping: ${error.message}`);
    return;
  }

  try {
    await withServer(async (baseUrl) => {
      const res = await fetch(`${baseUrl}/api/health`);
      assert.equal(res.status, 200);

      const body = await res.json();
      assert.equal(body.status, 'ok');
      assert.equal(body.database.connected, true);
      assert.equal(body.service, 'reprise-backend');
    });
  } finally {
    await close();
  }
});
