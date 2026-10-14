#!/usr/bin/env node
'use strict';

/**
 * Read-only web viewer for the RepRise database (development tool).
 *
 * A browser is the most convenient way to eyeball the data, and the usual
 * options (Compass, mongo-express) are not installable in this environment, so
 * this serves a small server-rendered HTML view using the SAME express + mongodb
 * dependencies the backend already ships.
 *
 * It is strictly READ-ONLY: every handler is a `find`/`countDocuments`, and
 * there are no routes that write, update or delete anything.
 *
 * Usage (run from `backend/`):
 *
 *   node scripts/serve-db.js
 *
 * Then open http://localhost:8081. Basic auth is required; override the
 * defaults with DBVIEW_USER / DBVIEW_PASSWORD.
 *
 * It binds to 127.0.0.1 by default, so only this machine can reach it. To open
 * it from a phone on the same network, set DBVIEW_HOST=0.0.0.0 AND a real
 * DBVIEW_PASSWORD (never leave the default password on a network-exposed port):
 *
 *   DBVIEW_HOST=0.0.0.0 DBVIEW_PASSWORD=<something-strong> node scripts/serve-db.js
 */

const express = require('express');
const { connect, getDb, close } = require('../src/database/connection');
const { COLLECTIONS } = require('../src/database/collections');
const { summariseWorkout, toPublicWorkout } = require('../src/services/workout.service');
const { toPublicEntry } = require('../src/services/weight.service');
const { findUserByUsername } = require('../src/services/user.service');
const { weeklyLeaderboard } = require('../src/services/leaderboard.service');
const { calculateBmi } = require('../src/services/bmi.service');
const { listFriends, listRequests } = require('../src/services/friendship.service');

const PORT = Number(process.argv[2] || process.env.DBVIEW_PORT || 8081);
const HOST = process.env.DBVIEW_HOST || '127.0.0.1';
const AUTH_USER = process.env.DBVIEW_USER || 'reprise';
const AUTH_PASSWORD = process.env.DBVIEW_PASSWORD || 'reprise123';

// ---------------------------------------------------------------------------
// Rendering helpers
// ---------------------------------------------------------------------------

const escapeHtml = (value) =>
  String(value ?? '').replace(
    /[&<>"']/g,
    (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]
  );

const when = (value) => (value ? new Date(value).toISOString().replace('T', ' ').slice(0, 16) : '—');

function layout(title, body) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} · RepRise DB</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #0b0b0b; color: #fff;
         font: 15px/1.5 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
  .wrap { max-width: 900px; margin: 0 auto; padding: 24px 16px 64px; }
  a { color: #ffd600; text-decoration: none; }
  a:hover { text-decoration: underline; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  h2 { font-size: 15px; margin: 28px 0 8px; color: #a3a3a3;
       text-transform: uppercase; letter-spacing: .06em; }
  .card { background: #171717; border: 1px solid #303030; border-radius: 12px;
          padding: 14px 16px; margin: 10px 0; }
  table { border-collapse: collapse; width: 100%; }
  th, td { text-align: left; padding: 6px 10px 6px 0; border-bottom: 1px solid #262626;
           vertical-align: top; }
  th { color: #a3a3a3; font-weight: 600; }
  tr:last-child td { border-bottom: none; }
  pre { background: #101010; border: 1px solid #303030; border-radius: 8px;
        padding: 12px; overflow-x: auto; font-size: 13px; }
  code { color: #f5c400; }
  .muted { color: #a3a3a3; }
  .pill { display: inline-block; background: #262600; color: #ffd600;
          border-radius: 999px; padding: 1px 10px; font-size: 12px; }
  .nav { margin-top: 24px; color: #a3a3a3; }
</style>
</head>
<body><div class="wrap">
<a href="/">← RepRise DB</a>
<h1>${escapeHtml(title)}</h1>
${body}
</div></body></html>`;
}

const table = (headers, rows) =>
  `<table><thead><tr>${headers.map((h) => `<th>${escapeHtml(h)}</th>`).join('')}</tr></thead>` +
  `<tbody>${rows.join('')}</tbody></table>`;

// ---------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------

async function overviewPage() {
  const db = getDb();

  const counts = [];
  for (const name of Object.values(COLLECTIONS)) {
    counts.push(`<tr><td>${escapeHtml(name)}</td><td>${await db.collection(name).countDocuments()}</td></tr>`);
  }

  const users = await db
    .collection(COLLECTIONS.USERS)
    .find({})
    .sort({ username: 1 })
    .toArray();

  const userRows = users.map(
    (user) =>
      `<tr>` +
      `<td><a href="/u/${encodeURIComponent(user.username)}">${escapeHtml(user.username)}</a></td>` +
      `<td>${escapeHtml(user.displayName)}</td>` +
      `<td class="muted">${escapeHtml(user.email)}</td>` +
      `<td>${escapeHtml(user.fitnessGoal)} / ${escapeHtml(user.weightUnit)}</td>` +
      `</tr>`
  );

  return (
    `<h2>Collections</h2><div class="card">${table(['Collection', 'Documents'], counts)}</div>` +
    `<h2>Users (${users.length})</h2><div class="card">` +
    (users.length ? table(['Username', 'Name', 'Email', 'Goal / unit'], userRows) : '<p>No users yet.</p>') +
    `</div>`
  );
}

async function userPage(username) {
  const user = await findUserByUsername(username);
  if (!user) return { status: 404, body: '<p>No user with that username.</p>' };

  const sections = [];

  // Raw document first — this is the "credentials" view.
  sections.push(
    `<h2>Raw document <span class="muted">(as stored, incl. passwordHash)</span></h2>` +
      `<pre>${escapeHtml(JSON.stringify(user, null, 2))}</pre>`
  );

  // Weight entries, with BMI where a height exists.
  const entries = await db_entries(user._id);
  const entryRows = entries.map((entry) => {
    const shown = toPublicEntry(entry, user.weightUnit);
    const bmi = calculateBmi(shown.weightKg, user.heightCm);
    return (
      `<tr><td>${when(entry.measuredAt)}</td>` +
      `<td>${entry.weight} ${escapeHtml(entry.unit)}</td>` +
      `<td>${shown.weight} ${escapeHtml(shown.unit)}</td>` +
      `<td>${bmi ? `${bmi.value} (${escapeHtml(bmi.category)})` : '—'}</td>` +
      `<td class="muted">${escapeHtml(shown.note || '')}</td></tr>`
    );
  });
  sections.push(
    `<h2>Weight entries (${entries.length})</h2><div class="card">` +
      (entries.length ? table(['Measured', 'Stored', 'Converted', 'BMI', 'Note'], entryRows) : '<p>None.</p>') +
      `</div>`
  );

  // Workouts, with derived totals and every set.
  const workouts = await db_workouts(user._id);
  const workoutCards = workouts.map((workout) => {
    const totals = summariseWorkout(workout);
    const shown = toPublicWorkout(workout, user.weightUnit);
    const sets = shown.exercises
      .map(
        (exercise) =>
          `<div>· ${escapeHtml(exercise.name)}: ` +
          escapeHtml(exercise.sets.map((set) => `${set.reps}×${set.weight}${exercise.unit}`).join(', ')) +
          `</div>`
      )
      .join('');
    return (
      `<div class="card"><strong>${escapeHtml(shown.name)}</strong> ` +
      `<span class="muted">${when(shown.workoutDate)}` +
      (shown.durationMinutes != null ? ` · ${shown.durationMinutes} min` : '') +
      `</span><div class="muted">${totals.exerciseCount} exercises · ${totals.setCount} sets · ` +
      `${totals.totalReps} reps · ${totals.totalVolumeKg} kg volume</div>${sets}</div>`
    );
  });
  sections.push(
    `<h2>Workouts (${workouts.length})</h2>` +
      (workouts.length ? workoutCards.join('') : '<div class="card"><p>None.</p></div>')
  );

  // Friends + pending requests.
  const [friends, requests] = await Promise.all([listFriends(user._id), listRequests(user._id)]);
  const requestRows = [
    ...requests.incoming.map((r) => `<tr><td>IN</td><td>${escapeHtml(r.user.username)}</td><td>${when(r.createdAt)}</td></tr>`),
    ...requests.outgoing.map((r) => `<tr><td>OUT</td><td>${escapeHtml(r.user.username)}</td><td>${when(r.createdAt)}</td></tr>`),
  ];
  sections.push(
    `<h2>Friends (${friends.length})</h2><div class="card">` +
      (friends.length
        ? friends.map((friend) => `<div>${escapeHtml(friend.username)} — ${escapeHtml(friend.displayName)}</div>`).join('')
        : '<p>None.</p>') +
      `</div>` +
      `<h2>Pending requests (${requestRows.length})</h2><div class="card">` +
      (requestRows.length ? table(['Dir', 'User', 'Sent'], requestRows) : '<p>None.</p>') +
      `</div>`
  );

  // Weekly leaderboard, computed by the API's own service.
  const board = await weeklyLeaderboard(user._id);
  const boardRows = board.entries.map(
    (entry) =>
      `<tr><td>#${entry.rank}</td><td>${escapeHtml(entry.user.username)}` +
      (entry.isMe ? ' <span class="pill">you</span>' : '') +
      `</td><td>${entry.score}</td><td>${entry.activeDays}</td><td>${entry.workoutCount}</td></tr>`
  );
  sections.push(
    `<h2>Weekly leaderboard</h2><div class="card">` +
      `<p class="muted">Week ${when(board.week.startDate)} → ${when(board.week.endDate)} UTC · ` +
      `${board.scoring.pointsPerDay}/day, +${board.scoring.bonusPoints} at ${board.scoring.bonusDays}+ days</p>` +
      table(['Rank', 'User', 'Score', 'Active days', 'Workouts'], boardRows) +
      `</div>`
  );

  return { status: 200, body: sections.join('') };
}

const db_entries = (userId) =>
  getDb().collection(COLLECTIONS.WEIGHT_ENTRIES).find({ userId }).sort({ measuredAt: -1 }).toArray();

const db_workouts = (userId) =>
  getDb().collection(COLLECTIONS.WORKOUTS).find({ userId }).sort({ workoutDate: -1 }).toArray();

// ---------------------------------------------------------------------------
// Server
// ---------------------------------------------------------------------------

function requireAuth(req, res, next) {
  const [scheme, encoded] = (req.headers.authorization || '').split(' ');
  if (scheme === 'Basic' && encoded) {
    const [user, password] = Buffer.from(encoded, 'base64').toString('utf8').split(':');
    if (user === AUTH_USER && password === AUTH_PASSWORD) return next();
  }
  res.set('WWW-Authenticate', 'Basic realm="RepRise DB"');
  res.status(401).send('Authentication required.');
}

async function main() {
  await connect();
  const app = express();

  app.use(requireAuth);

  app.get('/', async (_req, res, next) => {
    try {
      res.send(layout('Overview', await overviewPage()));
    } catch (error) {
      next(error);
    }
  });

  app.get('/u/:username', async (req, res, next) => {
    try {
      const { status, body } = await userPage(req.params.username);
      res.status(status).send(layout(req.params.username, body));
    } catch (error) {
      next(error);
    }
  });

  app.use((error, _req, res, _next) => {
    console.error(error);
    res.status(500).send(layout('Error', `<pre>${escapeHtml(error.message)}</pre>`));
  });

  app.listen(PORT, HOST, () => {
    console.log(`RepRise DB viewer (read-only) running:`);
    console.log(`  http://localhost:${PORT}`);
    console.log(`  http://<this-machine-ip>:${PORT}   (from a phone on the same network)`);
    console.log(`  login: ${AUTH_USER} / ${AUTH_PASSWORD}`);
  });
}

main().catch(async (error) => {
  console.error(`Could not start: ${error.message}`);
  await close().catch(() => {});
  process.exitCode = 1;
});
