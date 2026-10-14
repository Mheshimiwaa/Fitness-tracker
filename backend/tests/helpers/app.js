'use strict';

/**
 * Helpers for exercising the Express app over real HTTP.
 *
 * We start the app on port 0 ("any free port") and talk to it with the built-in
 * `fetch`, so tests exercise the same middleware chain as production without
 * needing an extra HTTP client dependency.
 */

require('./setup');

const app = require('../../src/app');

/** Start the app on a random free port; resolves with the http.Server. */
function startServer() {
  return new Promise((resolve) => {
    const server = app.listen(0, '127.0.0.1', () => resolve(server));
  });
}

/** Stop a server started by startServer(). */
function stopServer(server) {
  return new Promise((resolve) => server.close(resolve));
}

/** Start a server, run `fn(baseUrl)`, and always stop the server afterwards. */
async function withServer(fn) {
  const server = await startServer();
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  try {
    return await fn(baseUrl);
  } finally {
    await stopServer(server);
  }
}

/**
 * Make an API request and return { status, body }.
 *
 * @param {string} baseUrl
 * @param {string} method  e.g. 'GET', 'POST'
 * @param {string} path    e.g. '/api/auth/login'
 * @param {{ token?: string, body?: object }} [options]
 */
async function apiRequest(baseUrl, method, path, options = {}) {
  const { token, body } = options;

  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  let parsed = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null; // Non-JSON body (should not happen for our API).
    }
  }

  return { status: response.status, body: parsed, text };
}

module.exports = { startServer, stopServer, withServer, apiRequest };
