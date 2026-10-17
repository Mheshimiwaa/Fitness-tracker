'use strict';

/**
 * Builds and configures the Express application.
 *
 * Kept separate from server.js so tests can import the app WITHOUT opening a
 * real network port (`server.js` is the file that calls app.listen()).
 */

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');

const env = require('./config/env');
const { PHOTO_URL_PREFIX } = require('./config/constants');
const requestLogger = require('./middleware/requestLogger');
const notFound = require('./middleware/notFound');
const errorHandler = require('./middleware/errorHandler');
const apiRoutes = require('./routes');

const app = express();

// Don't advertise that we run Express.
app.disable('x-powered-by');

// Sensible security headers (no secrets involved).
app.use(helmet());

// CORS. "*" allows any origin during local development.
app.use(
  cors({
    origin: env.corsOrigin === '*' ? true : env.corsOrigin.split(',').map((s) => s.trim()),
  })
);

// Parse JSON bodies, with a request size limit so a client cannot send a huge
// payload and exhaust server memory.
app.use(express.json({ limit: '100kb' }));

// Development request logging (method, route, status, duration, request id).
app.use(requestLogger);

// All application routes live under /api.
app.use('/api', apiRoutes);

/**
 * Uploaded profile photos, straight off disk (see services/photo.service.js).
 *
 * These are the one thing served without a token: the URL is what the app hands
 * to an image loader, and its random file name is what keeps it unguessable.
 *
 * helmet's default `Cross-Origin-Resource-Policy: same-origin` would stop a web
 * page from embedding a picture it was legitimately given the URL for, so this
 * route relaxes it to `cross-origin`. The native app ignores the header
 * entirely; this is for a browser.
 *
 * The file name changes on every upload, so a cached copy can never be a stale
 * version of the same URL — which is what makes `immutable` safe here.
 */
app.use(
  PHOTO_URL_PREFIX,
  helmet.crossOriginResourcePolicy({ policy: 'cross-origin' }),
  express.static(env.uploadsDir, { immutable: true, maxAge: '7d', index: false })
);

// Unmatched routes -> 404, then the central error handler.
app.use(notFound);
app.use(errorHandler);

module.exports = app;
