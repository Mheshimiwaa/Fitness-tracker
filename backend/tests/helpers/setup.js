'use strict';

/**
 * Test environment setup.
 *
 * This module MUST be required before anything that imports
 * `src/config/env.js`, because dotenv does not overwrite variables that are
 * already present in `process.env`. Setting them here therefore wins over the
 * values in `.env`.
 *
 * The important line is MONGODB_DB_NAME: tests run against a SEPARATE database
 * ("reprise_test") on the same cluster, so they can never read or destroy the
 * real data in the "reprise" database.
 */

process.env.NODE_ENV = 'test';
process.env.MONGODB_DB_NAME = 'reprise_test';
process.env.ENABLE_HTTP_LOGS = 'false';
