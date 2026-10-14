'use strict';

/**
 * MongoDB connection management.
 *
 * We keep a SINGLE MongoClient for the whole process and reuse it. Creating a
 * new client per request would exhaust the connection pool.
 *
 * Lifecycle:
 *   connect()  -> called once at startup
 *   getDb()    -> used by repositories/services to access collections
 *   ping()     -> used by the health-check endpoint
 *   close()    -> called on graceful shutdown
 */

const { MongoClient } = require('mongodb');
const env = require('../config/env');
const logger = require('../utils/logger');

let client = null;
let db = null;

async function connect() {
  if (db) {
    return db; // Already connected — reuse the existing client.
  }

  client = new MongoClient(env.mongoUri, {
    // Fail fast (10s) instead of hanging forever if Atlas is unreachable.
    serverSelectionTimeoutMS: 10000,
    // App metadata that shows up in MongoDB logs — handy when demonstrating.
    appName: 'reprise-backend',
  });

  await client.connect();
  db = client.db(env.mongoDbName);

  // `ping` proves the connection really works, not just that the client object
  // was created.
  await db.command({ ping: 1 });

  logger.info(
    `Connected to MongoDB (db="${env.mongoDbName}", host="${client.options.hosts?.[0] ?? 'unknown'}")`
  );
  return db;
}

function getDb() {
  if (!db) {
    throw new Error('MongoDB is not connected yet. Call connect() during startup.');
  }
  return db;
}

/** Returns true when the database responds to a ping, false otherwise. */
async function ping() {
  if (!db) return false;
  try {
    await db.command({ ping: 1 });
    return true;
  } catch (error) {
    logger.error('MongoDB ping failed:', error.message);
    return false;
  }
}

async function close() {
  if (client) {
    await client.close();
    client = null;
    db = null;
    logger.info('MongoDB connection closed.');
  }
}

module.exports = { connect, getDb, ping, close };
