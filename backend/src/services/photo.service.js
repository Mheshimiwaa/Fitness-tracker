'use strict';

/**
 * Profile photos: the bytes live in MongoDB, and the URL points at this service.
 *
 * Three rules shape everything here:
 *
 *  1. NEVER TRUST THE CLIENT'S CONTENT-TYPE. A request can claim to be a PNG
 *     and carry anything at all — and a browser would then be handed that file
 *     back with our name on it. The format is decided by the file's MAGIC
 *     NUMBERS, and the content type we store is the one we sniffed.
 *  2. FILE NAMES ARE BUILT HERE, NEVER TAKEN FROM THE CLIENT. A name is the
 *     user's id plus random hex, so nobody can choose a path, overwrite an
 *     unrelated file, or reach outside the `photos` collection. The random part
 *     is also what makes a photo URL unguessable — and what makes it change on
 *     every upload, so caches can never serve a stale picture.
 *  3. THE BYTES ARE STORED WITH THE DATA, NOT ON THE SERVER'S DISK. A hosted
 *     deployment gets an EPHEMERAL filesystem: anything written to it is lost on
 *     the next deploy or restart, which would silently break every avatar. The
 *     database is the one part of the deployment that is guaranteed to survive,
 *     so a photo is a document (`photos`, `_id` = the file name) and the only
 *     thing on the user's profile is its URL.
 *
 * Photos are the one thing this API serves WITHOUT a token: the URL is handed
 * to an image loader, and the name carries the randomness that keeps it private
 * in practice. Nothing secret (email, goal, height) is ever in a URL.
 */

const crypto = require('crypto');

const { ObjectId } = require('mongodb');

const { COLLECTIONS } = require('../database/collections');
const { getDb } = require('../database/connection');
const { PHOTO_URL_PREFIX } = require('../config/constants');
const ApiError = require('../utils/ApiError');
const logger = require('../utils/logger');
const userService = require('./user.service');

// The formats we accept, with the extension each is stored under, and the
// content type we serve it back with.
const JPEG = { type: 'image/jpeg', extension: '.jpg' };
const PNG = { type: 'image/png', extension: '.png' };
const WEBP = { type: 'image/webp', extension: '.webp' };

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const JPEG_MAGIC = Buffer.from([0xff, 0xd8, 0xff]);

/**
 * `"<24 hex user id>-<16 hex random><ext>"` — the ONLY shape of file name this
 * service ever writes, and therefore the only shape it will ever read or
 * delete. Used as a guard so a stored URL that points somewhere else (a user
 * may legitimately PATCH in any URL) can never make us delete an arbitrary
 * document.
 */
const STORED_PHOTO_NAME = /^[a-f0-9]{24}-[a-f0-9]{16}\.(?:jpg|png|webp)$/;

/**
 * Identify an image from its leading bytes, or return null.
 *
 * Deliberately shallow: this decides which extension a photo is stored under
 * and which content type it is served with. Whether the pixels inside are a
 * valid picture is the app's business — it re-encodes every photo itself before
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
 * The name of OUR photo that a stored URL refers to, or null when the URL is
 * not one of ours (an external URL a user typed in, an empty value, junk).
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

  const name = parsed.pathname.slice(parsed.pathname.lastIndexOf('/') + 1);
  return STORED_PHOTO_NAME.test(name) ? name : null;
}

/**
 * Store the bytes, deciding the format from the bytes themselves.
 *
 * The document's `_id` IS the file name: the name is unique by construction, so
 * MongoDB's built-in unique index on `_id` already guarantees no two uploads can
 * collide, and serving a photo is a single indexed point lookup.
 *
 * @returns {Promise<string>} the URL PATH the photo is served from, e.g. "/uploads/ab.jpg".
 * @throws {ApiError} 415 when the bytes are not one of the supported formats.
 */
async function writePhoto(userId, buffer) {
  const format = sniffImageFormat(buffer);
  if (!format) {
    throw ApiError.unsupportedMediaType('That file is not a JPEG, PNG or WebP image.');
  }

  const name = fileNameFor(userId, format.extension);

  await getDb().collection(COLLECTIONS.PHOTOS).insertOne({
    _id: name,
    userId: new ObjectId(userId),
    contentType: format.type,
    size: buffer.length,
    // A Node Buffer is stored as BSON binary and comes back as a Buffer, so the
    // bytes are never re-encoded on the way through the database.
    data: buffer,
    createdAt: new Date(),
  });

  return `${PHOTO_URL_PREFIX}/${name}`;
}

/**
 * Read a stored photo by its file name, or null when there is none.
 *
 * The name is put through the SAME guard that protects deletion, so a request
 * for anything that is not one of our own names is answered without touching
 * the database at all.
 *
 * @param {string} name
 * @returns {Promise<{ _id: string, contentType: string, size: number, data: Buffer }|null>}
 */
async function findPhoto(name) {
  if (typeof name !== 'string' || !STORED_PHOTO_NAME.test(name)) return null;

  const doc = await getDb()
    .collection(COLLECTIONS.PHOTOS)
    .findOne({ _id: name }, { projection: { contentType: 1, size: 1, data: 1 } });

  if (!doc) return null;

  // The driver hands a BSON Binary back as a `Binary` object, not a Node
  // Buffer — and `res.send()` does not recognise that as binary, so it would
  // serialise `{"_bsontype":"Binary",…}` as the response body and still answer
  // 200: a corrupt image that looks like a success. Normalising HERE is what
  // guarantees the bytes that were uploaded are the bytes that are served.
  return {
    ...doc,
    data: Buffer.isBuffer(doc.data) ? doc.data : Buffer.from(doc.data.buffer),
  };
}

/**
 * Delete a photo this service previously wrote.
 *
 * Housekeeping must NEVER fail the request that triggered it: a leftover
 * document wastes space, whereas a 500 would tell the user their upload failed
 * when it succeeded. So "nothing matched" is "nothing to do", and any other
 * problem is logged and swallowed.
 *
 * @returns {Promise<boolean>} whether a photo was actually removed.
 */
async function deletePhoto(url) {
  const name = storedFileName(url);
  if (!name) return false;

  try {
    const result = await getDb().collection(COLLECTIONS.PHOTOS).deleteOne({ _id: name });
    return result.deletedCount > 0;
  } catch (error) {
    logger.warn(`Could not delete profile photo "${name}": ${error.message}`);
    return false;
  }
}

/**
 * Store a new profile photo and point the user's profile at it.
 *
 * `origin` is the scheme and host the request arrived on (e.g.
 * "https://reprise-api.onrender.com"), so the URL we store works for the client
 * that just uploaded — a phone on the LAN, an emulator on 10.0.2.2, a hosted
 * deployment behind its proxy. Behind a proxy, `app.set('trust proxy', ...)`
 * is what makes the scheme `https` rather than `http`.
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
  // instead of leaving it in the database forever.
  await deletePhoto(previous.profileImageUrl);

  return updated;
}

/**
 * Remove the user's photo, from both the profile and the database.
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
  findPhoto,
  deletePhoto,
  saveProfileImage,
  clearProfileImage,
};
