'use strict';

/**
 * Phase 1 backend tests: health check + 404 handling.
 *
 * Run with:  npm test
 */

// NOTE: helpers/setup is pulled in by helpers/app and must run before the app
// is imported, so the test environment is configured first.
const test = require('node:test');
const assert = require('node:assert/strict');

const { withServer, apiRequest } = require('./helpers/app');

test('unknown routes return a 404 with the standard error shape', async () => {
  await withServer(async (baseUrl) => {
    const { status, body } = await apiRequest(baseUrl, 'GET', '/api/definitely-not-a-route');

    assert.equal(status, 404);
    assert.ok(body.error, 'response should contain an "error" object');
    assert.match(body.error.message, /Route not found/);
  });
});

test('health check reports "degraded" when the database is not connected', async () => {
  // This test process has not called connect() yet, so the ping must fail.
  await withServer(async (baseUrl) => {
    const { status, body } = await apiRequest(baseUrl, 'GET', '/api/health');

    assert.equal(status, 503);
    assert.equal(body.status, 'degraded');
    assert.equal(body.database.connected, false);
  });
});

test('health check reports "ok" when the database is reachable', async (t) => {
  const { tryConnect, close } = require('./helpers/db');

  const connected = await tryConnect();
  if (!connected) {
    t.skip('MongoDB not reachable');
    return;
  }

  try {
    await withServer(async (baseUrl) => {
      const { status, body } = await apiRequest(baseUrl, 'GET', '/api/health');

      assert.equal(status, 200);
      assert.equal(body.status, 'ok');
      assert.equal(body.database.connected, true);
      assert.equal(body.service, 'reprise-backend');
    });
  } finally {
    await close();
  }
});
