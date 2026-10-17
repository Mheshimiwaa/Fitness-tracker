'use strict';

/**
 * The lazy database connection (middleware/ensureDatabase.js) must stay OFF
 * during tests.
 *
 * `src/start.js` connects before it listens and the test helpers connect
 * explicitly, so nothing here needs the middleware to help. If it ever started
 * connecting on its own, every test that deliberately runs WITHOUT a database
 * would first wait ten seconds for a connection that cannot succeed — turning a
 * fast suite into a slow, confusing one. This test fails loudly if that guard
 * is dropped.
 */

require('./helpers/setup');

const test = require('node:test');
const assert = require('node:assert/strict');

const env = require('../src/config/env');
const ensureDatabase = require('../src/middleware/ensureDatabase');
const { getDb } = require('../src/database/connection');

test('ensureDatabase passes requests straight through during tests', () => {
  assert.equal(env.isTest, true, 'this file must run as a test');

  let nextCalls = 0;
  ensureDatabase({}, {}, () => {
    nextCalls += 1;
  });

  assert.equal(nextCalls, 1, 'next() must be called exactly once, synchronously');
});

test('ensureDatabase does not open a connection during tests', () => {
  ensureDatabase({}, {}, () => {});

  // A connection would make this succeed; it must still refuse because nothing
  // connected, proving the middleware did not open one behind our back.
  assert.throws(() => getDb(), /not connected/);
});
