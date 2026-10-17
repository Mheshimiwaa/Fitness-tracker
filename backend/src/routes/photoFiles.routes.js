'use strict';

/**
 * `GET /uploads/:name` — the stored profile pictures.
 *
 * This is deliberately NOT under `/api`, and deliberately NOT authenticated: it
 * is an `<img src>` URL, which cannot carry a bearer token, and the random part
 * of the file name is what makes it unguessable (see services/photo.service.js).
 *
 * The photo is streamed from MongoDB rather than from a static directory
 * because a hosted deployment's filesystem is ephemeral — a file written there
 * is gone after the next deploy, while the database survives.
 *
 * A name that is not one of ours, or one that no longer exists (its photo was
 * replaced or removed), falls through to the normal JSON 404 so every "not
 * found" in this API looks the same.
 */

const express = require('express');

const { PHOTO_CACHE_CONTROL } = require('../config/constants');
const photoService = require('../services/photo.service');

const router = express.Router();

router.get('/:name', async (req, res, next) => {
  const photo = await photoService.findPhoto(req.params.name);
  if (!photo) return next();

  // Content-Length and an ETag are added by res.send(); the content type comes
  // from what was sniffed at upload time, never from the request.
  res.set('Cache-Control', PHOTO_CACHE_CONTROL);
  res.type(photo.contentType);
  res.send(photo.data);
});

module.exports = router;
