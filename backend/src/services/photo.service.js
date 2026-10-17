'use strict';

/**
 * Profile photos: writing the uploaded bytes to disk and serving URLs for them.
 *
 * Two rules shape everything here:
 *
 *  1. NEVER TRUST THE CLIENT'S CONTENT-TYPE. A request can claim to be a PNG
 *     and carry anything at all — and a browser would then be handed that file
 *     back with our name on it. The format is decided by the file's MAGIC
 *     NUMBERS, and the extension we store is the one we sniffed.
 *  2. FILE NAMES ARE BUILT HERE, NEVER TAKEN FROM THE CLIENT. A name is the
 *     user's id plus random hex, so nobody can choose a path, overwrite an
 *     unrelated file, or reach outside the uploads directory. The random part
 *     is also what makes a photo URL unguessable — and what makes it change on
 *     every upload, so caches can never serve a stale picture.
 *
 * Photos are the one thing this API serves WITHOUT a token: the URL is handed
 * to an image loader, and the file name carries the randomness that keeps it
 * private in practice. Nothing secret (email, goal, height) is ever in a URL.
 */

const crypto = require('crypto');
const fs = require('fs/promises');
const path = require('path');

const env = require('../config/env');
const { PHOTO_URL_PREFIX } = require('../config/constants');
const ApiError = require('../utils/ApiError');
const logger = require('../utils/logger');
const userService = require('./user.service');

// The formats we accept, with the extension each is stored under.
const JPEG = { type: 'image/jpeg', extension: '.jpg' };
const PNG = { type: 'image/png', extension: '.png' };
const WEBP = { type: 'image/webp', extension: '.webp' };

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const JPEG_MAGIC = Buffer.from([0xff, 0xd8, 0xff]);

/**
 * `"<24 hex user id>-<16 hex random><ext>"` — the ONLY shape of file name this
 * service ever writes, and therefore the only shape it will ever delete. Used
 * as a guard so a stored URL that points somewhere else (a user may legitimately
 * PATCH in any URL) can never make us unlink an arbitrary file.
 */
const STORED_PHOTO_NAME = /^[a-f0-9]{24}-[a-f0-9]{16}\.(?:jpg|png|webp)$/;

/**
 * Identify an image from its leading bytes, or return null.
 *
 * Deliberately shallow: this decides which extension a file is stored under and
 * which Content-Type it is served with. Whether the pixels inside are a valid
 * picture is the app's business — it re-encodes every photo itself before
 * uploading (see the Android `AvatarImage`), so the bytes it sends are always a
 * real JPEG.
 *
 * @param {Buffer} buffer
 * @returns {{ type: string, extension: string }|null}
 */
function sniffImageFormat(buffer) {
  if (!Buffer.isBuffer(buffer)) return null;

  if (buffer.length >= JPEG_MAGIC.length && buffer.subarray(0, 3).equals(JPEG_MAGIC)) {
    return JPEG;
  }
  if (buffer.length >= PNG_MAGIC.length && buffer.subarray(0, 8).equals(PNG_MAGIC)) {
    return PNG;
  }
  // WebP is a RIFF container: "RIFF" <4-byte size> "WEBP".
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('latin1') === 'RIFF' &&
    buffer.subarray(8, 12).toString('latin1') === 'WEBP'
  ) {
    return WEBP;
  }

  return null;
}

/**
 * The file name a photo for this user would be stored under. Random, so two
 * uploads in the same millisecond cannot collide.
 */
function fileNameFor(userId, extension) {
  return `${String(userId)}-${crypto.randomBytes(8).toString('hex')}${extension}`;
}

/**
 * The name of OUR file that a stored URL refers to, or null when the URL is not
 * one of ours (an external URL a user typed in, an empty value, junk).
 *
 * @param {string|null|undefined} url
 * @returns {string|null}
 */
function storedFileName(url) {
  if (typeof url !== 'string') return null;

  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return null; // Not a URL at all — nothing of ours to delete.
  }

  if (!parsed.pathname.startsWith(`${PHOTO_URL_PREFIX}/`)) return null;

  const name = path.basename(parsed.pathname);
  return STORED_PHOTO_NAME.test(name) ? name : null;
}

/**
 * Write the bytes to disk, deciding the format from the bytes themselves.
 *
 * @returns {Promise<string>} the URL PATH the file is served from, e.g. "/uploads/ab.jpg".
 * @throws {ApiError} 415 when the bytes are not one of the supported formats.
 */
async function writePhoto(userId, buffer) {
  const format = sniffImageFormat(buffer);
  if (!format) {
    throw ApiError.unsupportedMediaType('That file is not a JPEG, PNG or WebP image.');
  }

  // Created on first use so a fresh clone needs no setup step.
  await fs.mkdir(env.uploadsDir, { recursive: true });

  const name = fileNameFor(userId, format.extension);
  await fs.writeFile(path.join(env.uploadsDir, name), buffer);

  return `${PHOTO_URL_PREFIX}/${name}`;
}

/**
 * Delete a photo this service previously wrote.
 *
 * Housekeeping must NEVER fail the request that triggered it: a leftover file
 * wastes disk space, whereas a 500 would tell the user their upload failed when
 * it succeeded. So a missing file is "nothing to do", and any other problem is
 * logged and swallowed.
 *
 * @returns {Promise<boolean>} whether a file was actually removed.
 */
async function deletePhoto(url) {
  const name = storedFileName(url);
  if (!name) return false;

  try {
    await fs.unlink(path.join(env.uploadsDir, name));
    return true;
  } catch (error) {
    if (error.code !== 'ENOENT') {
      logger.warn(`Could not delete profile photo "${name}": ${error.message}`);
    }
    return false;
  }
}

/**
 * Store a new profile photo and point the user's profile at it.
 *
 * `origin` is the scheme and host the request arrived on (e.g.
 * "http://192.168.8.102:4000"), so the URL we store works for the client that
 * just uploaded — the phone reaches the server on its LAN address while an
 * emulator reaches it on 10.0.2.2.
 *
 * @param {string} userId
 * @param {Buffer} buffer the raw request body
 * @param {string} origin e.g. "http://localhost:4000"
 * @returns {Promise<object>} the updated user document
 */
async function saveProfileImage(userId, buffer, origin) {
  const relativePath = await writePhoto(userId, buffer);

  const { previous, updated } = await userService.setProfileImageUrl(
    userId,
    `${origin}${relativePath}`
  );

  // The replaced photo is now unreachable from the profile, so remove it
  // instead of leaving it on disk forever.
  await deletePhoto(previous.profileImageUrl);

  return updated;
}

/**
 * Remove the user's photo, from both the profile and the disk.
 *
 * @returns {Promise<object>} the updated user document
 */
async function clearProfileImage(userId) {
  const { previous, updated } = await userService.setProfileImageUrl(userId, null);
  await deletePhoto(previous.profileImageUrl);
  return updated;
}

module.exports = {
  sniffImageFormat,
  storedFileName,
  fileNameFor,
  writePhoto,
  deletePhoto,
  saveProfileImage,
  clearProfileImage,
};
