'use strict';

/**
 * Dashboard service.
 *
 * The Home tab needs ONE request that answers "where am I right now?" so the
 * screen does not fire half a dozen calls and stitch the results together on a
 * slow connection.
 *
 * Phase 3 populates the weight and BMI sections. Phase 4 adds the workouts
 * section. Phase 7 adds the streak section. Later phases extend this same
 * payload (leaderboard) so the app never has to be rewired — it simply starts
 * receiving more fields.
 *
 * Sections with no data yet are returned as `null` rather than omitted, so the
 * client can show an honest empty state instead of guessing.
 */

const weightService = require('./weight.service');
const workoutService = require('./workout.service');
const streakService = require('./streak.service');
const { calculateBmi } = require('./bmi.service');

/** How many days back the "recent change" number looks. */
const CHANGE_WINDOW_DAYS = 30;

/**
 * Build the dashboard payload for one user.
 *
 * @param {object} user the authenticated user document
 */
async function getDashboard(user) {
  const preferredUnit = user.weightUnit || 'kg';

  const [summary, workouts, streak] = await Promise.all([
    weightService.getSummary(user._id, preferredUnit, { days: CHANGE_WINDOW_DAYS }),
    workoutService.getRecentSummary(user._id),
    streakService.streakFor(user._id),
  ]);

  const latestWeightKg = summary.latest ? summary.latest.weightKg : null;
  const bmi = calculateBmi(latestWeightKg, user.heightCm);

  // Explain WHY there is no BMI, so the app can prompt for the missing piece
  // ("Add your height" vs "Log your weight") instead of a vague dash.
  let bmiUnavailableReason = null;
  if (!bmi) {
    if (user.heightCm === null || user.heightCm === undefined) {
      bmiUnavailableReason = 'no_height';
    } else {
      bmiUnavailableReason = 'no_weight';
    }
  }

  return {
    weight: {
      latest: summary.latest,
      previous: summary.previous,
      entryCount: summary.entryCount,
      changeWindowDays: summary.changeWindowDays,
      change: summary.change,
    },
    workouts: {
      totalCount: workouts.totalCount,
      recentCount: workouts.recentCount,
      recentDays: workouts.recentDays,
      recentVolumeKg: workouts.recentVolumeKg,
      lastWorkoutAt: workouts.lastWorkoutAt,
    },
    streak,
    bmi,
    bmiUnavailableReason,
    generatedAt: new Date(),
  };
}

module.exports = { getDashboard, CHANGE_WINDOW_DAYS };
