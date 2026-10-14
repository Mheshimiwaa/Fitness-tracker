'use strict';

/**
 * Weekly leaderboard service.
 *
 * "Who trained most consistently this week, among my friends?" — answered with
 * ONE aggregation instead of fetching every workout and counting in JavaScript.
 *
 * Scoring (see `config/constants.js`)
 * -----------------------------------
 *   - 10 points for each DISTINCT day on which a workout was logged, so a
 *     second session on the same day adds nothing (consistency, not volume).
 *   - a flat 5-point bonus once that reaches 3 days.
 *
 * A week is Monday 00:00 UTC to the next Monday 00:00 UTC. The two pure helpers
 * below (`startOfWeekUtc`, `scoreFor`) are exported so that boundary and the
 * points table can be unit-tested without a database.
 */

const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('../database/collections');
const { toObjectId } = require('../utils/objectId');
const ApiError = require('../utils/ApiError');
const { toUserSummary } = require('./user.service');
const { friendIdsOf } = require('./friendship.service');
const {
  LEADERBOARD_POINTS_PER_DAY,
  LEADERBOARD_BONUS_DAYS,
  LEADERBOARD_BONUS_POINTS,
  MS_PER_DAY,
} = require('../config/constants');

/**
 * The Monday 00:00:00 UTC that starts the week containing `now`.
 *
 * JS weekdays are 0=Sunday..6=Saturday, so `(day + 6) % 7` is "days since
 * Monday": 0 on a Monday, 6 on a Sunday — which is exactly the Sunday that
 * closes a Monday-start week.
 *
 * @param {Date} [now]
 * @returns {Date}
 */
function startOfWeekUtc(now = new Date()) {
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const daysSinceMonday = (start.getUTCDay() + 6) % 7;
  start.setUTCDate(start.getUTCDate() - daysSinceMonday);
  return start;
}

/**
 * Points for a number of active days.
 *
 * 0 days -> 0; otherwise 10 per day, plus the bonus from 3 days onwards.
 *
 * @param {number} activeDays
 * @returns {number}
 */
function scoreFor(activeDays) {
  if (activeDays <= 0) return 0;
  const bonus = activeDays >= LEADERBOARD_BONUS_DAYS ? LEADERBOARD_BONUS_POINTS : 0;
  return activeDays * LEADERBOARD_POINTS_PER_DAY + bonus;
}

/**
 * Count each user's distinct active days (and total workouts) in the window.
 *
 * @param {import('mongodb').ObjectId[]} userIds
 * @param {Date} weekStart inclusive
 * @param {Date} weekEnd exclusive
 * @returns {Promise<Map<string, {activeDays: number, workoutCount: number}>>}
 */
async function weeklyTotals(userIds, weekStart, weekEnd) {
  if (userIds.length === 0) return new Map();

  const rows = await getDb()
    .collection(COLLECTIONS.WORKOUTS)
    .aggregate([
      { $match: { userId: { $in: userIds }, workoutDate: { $gte: weekStart, $lt: weekEnd } } },
      // Collapse each user's workouts to one bucket per calendar day...
      {
        $group: {
          _id: {
            userId: '$userId',
            day: { $dateToString: { format: '%Y-%m-%d', date: '$workoutDate', timezone: 'UTC' } },
          },
          workoutsThatDay: { $sum: 1 },
        },
      },
      // ...then count those buckets: distinct days, and total workouts.
      {
        $group: {
          _id: '$_id.userId',
          activeDays: { $sum: 1 },
          workoutCount: { $sum: '$workoutsThatDay' },
        },
      },
    ])
    .toArray();

  return new Map(
    rows.map((row) => [
      String(row._id),
      { activeDays: row.activeDays, workoutCount: row.workoutCount },
    ])
  );
}

/**
 * The weekly leaderboard for a user: them plus their accepted friends, ranked.
 *
 * A friend who has not trained still appears, on zero points — the board is
 * about the people, not just the ones who did something.
 *
 * @param {string|import('mongodb').ObjectId} userId
 * @returns {Promise<object>}
 */
async function weeklyLeaderboard(userId) {
  const id = toObjectId(userId);
  if (!id) throw ApiError.unauthorized();

  const friendIds = await friendIdsOf(id);
  const participantIds = [id, ...friendIds];

  const weekStart = startOfWeekUtc();
  const weekEnd = new Date(weekStart.getTime() + 7 * MS_PER_DAY);

  const [totals, users] = await Promise.all([
    weeklyTotals(participantIds, weekStart, weekEnd),
    getDb()
      .collection(COLLECTIONS.USERS)
      .find(
        { _id: { $in: participantIds } },
        { projection: { username: 1, displayName: 1, profileImageUrl: 1 } }
      )
      .toArray(),
  ]);

  const entries = users.map((doc) => {
    const total = totals.get(String(doc._id)) || { activeDays: 0, workoutCount: 0 };
    return {
      user: toUserSummary(doc),
      score: scoreFor(total.activeDays),
      activeDays: total.activeDays,
      workoutCount: total.workoutCount,
      isMe: String(doc._id) === String(id),
    };
  });

  // Highest score first. Ties fall back to who trained more often, then to the
  // username so the order is stable rather than merely "whatever Mongo returned".
  entries.sort(
    (a, b) =>
      b.score - a.score ||
      b.workoutCount - a.workoutCount ||
      a.user.username.localeCompare(b.user.username)
  );

  // Standard competition ranking: equal scores share the better rank (1, 1, 3).
  let rank = 0;
  let previousScore = null;
  entries.forEach((entry, index) => {
    if (entry.score !== previousScore) rank = index + 1;
    entry.rank = rank;
    previousScore = entry.score;
  });

  return {
    week: { startDate: weekStart, endDate: weekEnd },
    scoring: {
      pointsPerDay: LEADERBOARD_POINTS_PER_DAY,
      bonusDays: LEADERBOARD_BONUS_DAYS,
      bonusPoints: LEADERBOARD_BONUS_POINTS,
    },
    entries,
    me: entries.find((entry) => entry.isMe) || null,
  };
}

module.exports = {
  startOfWeekUtc,
  scoreFor,
  weeklyLeaderboard,
};
