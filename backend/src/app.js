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
const ensureDatabase = require('./middleware/ensureDatabase');
const requestLogger = require('./middleware/requestLogger');
const notFound = require('./middleware/notFound');
const errorHandler = require('./middleware/errorHandler');
const apiRoutes = require('./routes');
const photoFilesRoutes = require('./routes/photoFiles.routes');

const app = express();

// Don't advertise that we run Express.
app.disable('x-powered-by');

/**
 * Trust exactly ONE proxy in front of us — which is what a hosting platform
 * (Render, Fly, nginx) puts there.
 *
 * This is not cosmetic. Behind that proxy the connection we see is always the
 * proxy's, so without this:
 *   - `req.ip` would be the proxy's address, and the rate limiter would lump
 *     EVERY user into one bucket (one busy friend could lock everyone out);
 *   - `req.protocol` would be "http", so a stored photo URL would be built as
 *     `http://…` on an https site and Android's cleartext policy would refuse
 *     to load it.
 *
 * The number (rather than `true`) is deliberate: trusting every hop lets a
 * client forge `X-Forwarded-For` and invent its own IP, which express-rate-limit
 * rightly warns about.
 */
app.set('trust proxy', 1);

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

/**
 * Make sure the database is reachable before a route touches it.
 *
 * On a normal machine `server.js` has already connected and this is a single
 * boolean check. On a serverless host (Vercel), where no startup step runs, this
 * is what actually opens the connection — see middleware/ensureDatabase.js.
 *
 * It sits before the routes (and before the /uploads mount below) so photos are
 * covered too, but AFTER body parsing, so a malformed or oversized request is
 * rejected without waking the database.
 */
app.use(ensureDatabase);

// All application routes live under /api.
app.use('/api', apiRoutes);

/**
 * Uploaded profile photos, served out of MongoDB (see services/photo.service.js).
 *
 * These are the one thing served without a token: the URL is what the app hands
 * to an image loader, and its random file name is what keeps it unguessable.
 *
 * helmet's default `Cross-Origin-Resource-Policy: same-origin` would stop a web
 * page from embedding a picture it was legitimately given the URL for, so this
 * route relaxes it to `cross-origin`. The native app ignores the header
 * entirely; this is for a browser.
 */
app.use(
  PHOTO_URL_PREFIX,
  helmet.crossOriginResourcePolicy({ policy: 'cross-origin' }),
  photoFilesRoutes
);

// Unmatched routes -> 404, then the central error handler.
app.use(notFound);
app.use(errorHandler);

module.exports = app;
