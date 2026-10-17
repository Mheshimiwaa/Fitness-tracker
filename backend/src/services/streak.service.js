'use strict';

/**
 * Streak service.
 *
 * A streak is consecutive UTC calendar days on which at least one workout was
 * logged. Days are bucketed in UTC — the same fixed boundary the weekly
 * leaderboard uses — so "a day" never depends on where the server happens to
 * run, and two people always agree on what "today" means.
 *
 * Two deliberate rules make the number feel fair rather than cruel:
 *
 *   1. A MISSED DAY ENDS THE STREAK. That is the whole point; a streak that
 *      survives anything is just a workout count.
 *   2. TODAY IS NOT OVER YET. If the user trained yesterday but has not trained
 *      today, the run is still standing and `trainedToday` is false, so the app
 *      can nudge ("train today to keep your 5-day streak") instead of silently
 *      dropping them to zero in the morning.
 *
 * The counting logic is kept in small PURE functions so the tricky part — the
 * arithmetic of consecutive days — is unit-testable without a database.
 */

const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('../database/collections');
const { toObjectId } = require('../utils/objectId');
const { MS_PER_DAY } = require('../config/constants');

/**
 * The UTC calendar day that `date` falls on, as an integer (days since
 * 1970-01-01). Integers are what make "is this the next day?" a simple `+1`
 * check, immune to daylight-saving shifts and month lengths.
 *
 * @param {Date} date
 * @returns {number}
 */
function utcDayNumber(date) {
  return (
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()) / MS_PER_DAY
  );
}

/**
 * Convert a `"YYYY-MM-DD"` day key — the format the aggregation below produces —
 * into the same integer as [utcDayNumber].
 *
 * The parts are parsed manually rather than passed to `new Date(string)`, whose
 * handling of date-only strings is not something we want to depend on.
 *
 * @param {string} key
 * @returns {number}
 */
function dayNumberFromKey(key) {
  const [year, month, day] = key.split('-').map(Number);
  return Date.UTC(year, month - 1, day) / MS_PER_DAY;
}

/**
 * The length of the run of consecutive days ending at the user's most recent
 * active day — or 0 when that day is neither today nor yesterday (a whole day
 * was missed).
 *
 * @param {number[]} days active day numbers (any order, duplicates allowed)
 * @param {number} today the day number of "now", in UTC
 * @returns {number}
 */
function currentStreak(days, today) {
  if (days.length === 0) return 0;

  const active = new Set(days);
  const newest = Math.max(...active);

  // A day can only overshoot "today" by the measurement clock-skew tolerance
  // (a workout logged just before midnight UTC). Counting it would claim a day
  // that has not happened yet, so it is treated as today.
  const start = Math.min(newest, today);
  if (start < today - 1) return 0;

  let streak = 0;
  let day = start;
  while (active.has(day)) {
    streak += 1;
    day -= 1;
  }
  return streak;
}

/**
 * The longest run of consecutive active days the user has ever had.
 *
 * @param {number[]} days
 * @returns {number}
 */
function longestStreak(days) {
  if (days.length === 0) return 0;

  const sorted = [...new Set(days)].sort((a, b) => a - b);

  let longest = 1;
  let run = 1;
  for (let i = 1; i < sorted.length; i += 1) {
    run = sorted[i] === sorted[i - 1] + 1 ? run + 1 : 1;
    if (run > longest) longest = run;
  }
  return longest;
}

/**
 * Every distinct UTC day on which this user logged a workout, newest first.
 *
 * One aggregation rather than "fetch every workout and count in JavaScript":
 * only the day keys travel back from the database, so the cost is the number of
 * DAYS trained, not the number of sets ever lifted.
 *
 * @param {string|import('mongodb').ObjectId} userId
 * @returns {Promise<number[]>}
 */
async function activeDayNumbers(userId) {
  const rows = await getDb()
    .collection(COLLECTIONS.WORKOUTS)
    .aggregate([
      { $match: { userId: toObjectId(userId) } },
      {
        $group: {
          _id: { $dateToString: { format: '%Y-%m-%d', date: '$workoutDate', timezone: 'UTC' } },
        },
      },
      { $sort: { _id: -1 } },
    ])
    .toArray();

  return rows.map((row) => dayNumberFromKey(row._id));
}

/**
 * The dashboard's streak section.
 *
 * @param {string|import('mongodb').ObjectId} userId
 * @param {Date} [now]
 * @returns {Promise<{current: number, longest: number, trainedToday: boolean, activeDays: number}>}
 */
async function streakFor(userId, now = new Date()) {
  const days = await activeDayNumbers(userId);
  const today = utcDayNumber(now);

  return {
    current: currentStreak(days, today),
    longest: longestStreak(days),
    trainedToday: days.includes(today),
    activeDays: days.length,
  };
}

module.exports = {
  utcDayNumber,
  dayNumberFromKey,
  currentStreak,
  longestStreak,
  activeDayNumbers,
  streakFor,
};
