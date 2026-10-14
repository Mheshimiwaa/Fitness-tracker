'use strict';

/**
 * Progression service — the rule-based progressive-overload assistant.
 *
 * Design notes
 * ------------
 * 1. There is NO machine learning here, and that is a feature: every verdict can
 *    be explained to the user in one sentence, and reproduced by hand. The rules
 *    are:
 *
 *      - did the heaviest set get heavier?
 *      - was more total work (weight x reps) done, beyond a small tolerance?
 *      - were more reps done without losing volume?
 *
 *    Any of those is progress; none, with a drop in volume, is a regression;
 *    anything else is "holding".
 *
 * 2. The rules live in PURE functions (`summariseExercise`, `classify`,
 *    `buildComparison`) that take plain objects. Everything a database would
 *    provide is passed in, so the interesting logic is unit-tested without a
 *    database, and the storage layer is a thin aggregation on top.
 *
 * 3. Volume is always compared in KILOGRAMS. A comparison must not change just
 *    because the user flipped their display preference from kg to lb.
 *
 * 4. One "session" for an exercise is one OCCURRENCE of that exercise in one
 *    workout. If a user logs the same exercise twice in a single workout (a top
 *    set then a back-off set, say), they are two entries — which is honest, since
 *    that is how they were recorded.
 */

const { getDb } = require('../database/connection');
const { COLLECTIONS } = require('../database/collections');
const { toObjectId } = require('../utils/objectId');
const { toKilograms, fromKilograms, round } = require('../utils/units');
const {
  PROGRESSION_VOLUME_TOLERANCE,
  PROGRESSION_INCREMENT_KG,
  PROGRESSION_INCREMENT_LB,
  PROGRESSION_DEFAULT_LIMIT,
  PROGRESSION_MAX_LIMIT,
} = require('../config/constants');

/** The workouts collection. Called lazily so the DB is connected first. */
function workouts() {
  return getDb().collection(COLLECTIONS.WORKOUTS);
}

/** The verdicts the assistant can reach. */
const VERDICT = Object.freeze({
  PROGRESSING: 'progressing',
  HOLDING: 'holding',
  REGRESSING: 'regressing',
  INSUFFICIENT_DATA: 'insufficient_data',
});

/**
 * Render a load for a human: at most one decimal, with no pointless ".0".
 * `60` stays "60"; `62.5` stays "62.5".
 */
function formatLoad(value) {
  const rounded = round(value, 1);
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

// ---------------------------------------------------------------------------
// Pure comparison rules
// ---------------------------------------------------------------------------

/**
 * Reduce a raw `$unwind`ed workout row into one comparable session.
 *
 * @param {object} row a workout whose `exercises` is a single object (post-$unwind)
 * @param {string} preferredUnit the unit to DISPLAY weights in
 */
function summariseExercise(row, preferredUnit) {
  const exercise = row.exercises;

  const sets = exercise.sets.map((set) => {
    const weightKg = toKilograms(set.weight, exercise.unit);
    return {
      reps: set.reps,
      weight: round(fromKilograms(weightKg, preferredUnit), 1),
      weightKg: round(weightKg, 2),
    };
  });

  const totalReps = sets.reduce((sum, set) => sum + set.reps, 0);
  const totalVolumeKg = round(
    sets.reduce((sum, set) => sum + set.weightKg * set.reps, 0),
    1
  );
  const topWeightKg = sets.reduce((max, set) => Math.max(max, set.weightKg), 0);

  return {
    workoutId: String(row._id),
    workoutName: row.name,
    workoutDate: row.workoutDate,
    setCount: sets.length,
    totalReps,
    totalVolumeKg,
    topWeightKg: round(topWeightKg, 2),
    topWeight: round(fromKilograms(topWeightKg, preferredUnit), 1),
    sets,
  };
}

/**
 * Compare the latest session against the previous one.
 *
 * Pure: takes two session summaries and returns the deltas plus a verdict. The
 * tolerance is relative to the PREVIOUS volume, so it scales with the size of
 * the session (2% of a light session is a fraction of 2% of a heavy one).
 */
function classify(latest, previous) {
  const volumeDeltaKg = round(latest.totalVolumeKg - previous.totalVolumeKg, 1);
  const topWeightDeltaKg = round(latest.topWeightKg - previous.topWeightKg, 2);
  const totalRepsDelta = latest.totalReps - previous.totalReps;
  const threshold = Math.abs(previous.totalVolumeKg) * PROGRESSION_VOLUME_TOLERANCE;

  const moreVolume = volumeDeltaKg > threshold;
  const heavierTopSet = topWeightDeltaKg > 0;
  const moreRepsWithoutLosingVolume = totalRepsDelta > 0 && volumeDeltaKg >= -threshold;
  const lessVolume = volumeDeltaKg < -threshold;
  const lighterAndNoMoreReps = topWeightDeltaKg < 0 && totalRepsDelta <= 0;

  let verdict = VERDICT.HOLDING;
  if (moreVolume || heavierTopSet || moreRepsWithoutLosingVolume) {
    verdict = VERDICT.PROGRESSING;
  } else if (lessVolume || lighterAndNoMoreReps) {
    verdict = VERDICT.REGRESSING;
  }

  return { verdict, volumeDeltaKg, topWeightDeltaKg, totalRepsDelta };
}

/** A one-sentence, rule-derived coaching hint. Never generated by a model. */
function buildSuggestion(verdict, latest, previous, unit) {
  const increment = unit === 'lb' ? PROGRESSION_INCREMENT_LB : PROGRESSION_INCREMENT_KG;

  if (verdict === VERDICT.INSUFFICIENT_DATA) {
    if (!latest) return 'Log this exercise to start tracking your progress.';
    return 'Log this exercise again to see whether you are moving forward.';
  }

  const nextTop = round(latest.topWeight + increment, 1);

  if (verdict === VERDICT.PROGRESSING) {
    return (
      `Progress: your top set went from ${formatLoad(previous.topWeight)} to ` +
      `${formatLoad(latest.topWeight)} ${unit}. Next session, aim for ` +
      `${formatLoad(nextTop)} ${unit} for the same reps — or add one rep per set first.`
    );
  }

  if (verdict === VERDICT.REGRESSING) {
    return (
      `Down since last time: your top set went from ${formatLoad(previous.topWeight)} ` +
      `to ${formatLoad(latest.topWeight)} ${unit}. That can simply be fatigue or a ` +
      `deliberate deload. Repeat ${formatLoad(previous.topWeight)} ${unit} until it ` +
      `feels strong, then build up again.`
    );
  }

  return (
    `Holding steady at ${formatLoad(latest.topWeight)} ${unit}. Try adding one rep to ` +
    `each set; once you hit your target reps, move up to ${formatLoad(nextTop)} ${unit}.`
  );
}

/**
 * Build the whole comparison block from sessions ordered NEWEST FIRST.
 *
 * `sessions[0]` is the latest, `sessions[1]` the previous. Fewer than two
 * sessions cannot show a trend, and the payload says so plainly rather than
 * inventing a verdict.
 */
function buildComparison(sessions, unit) {
  const latest = sessions[0] || null;
  const previous = sessions[1] || null;

  if (!latest || !previous) {
    return {
      hasPrevious: false,
      latest,
      previous,
      topWeightDelta: 0,
      totalRepsDelta: 0,
      totalVolumeDeltaKg: 0,
      verdict: VERDICT.INSUFFICIENT_DATA,
      suggestion: buildSuggestion(VERDICT.INSUFFICIENT_DATA, latest, previous, unit),
    };
  }

  const verdicts = classify(latest, previous);

  return {
    hasPrevious: true,
    latest,
    previous,
    // Deltas are shown in the user's unit; the verdict was decided in kilograms.
    topWeightDelta: round(fromKilograms(verdicts.topWeightDeltaKg, unit), 1),
    totalRepsDelta: verdicts.totalRepsDelta,
    totalVolumeDeltaKg: verdicts.volumeDeltaKg,
    verdict: verdicts.verdict,
    suggestion: buildSuggestion(verdicts.verdict, latest, previous, unit),
  };
}

// ---------------------------------------------------------------------------
// Database reads
// ---------------------------------------------------------------------------

/**
 * The progression for one exercise: the recent sessions plus the comparison.
 *
 * An exercise that has never been logged is not an error — it simply has no
 * sessions, and the comparison reports `insufficient_data`. That keeps the app's
 * logic simple: it can always render the same shape.
 */
async function getExerciseProgression(userId, exerciseName, preferredUnit, options = {}) {
  const { limit = PROGRESSION_DEFAULT_LIMIT } = options;

  const rows = await workouts()
    .aggregate([
      { $match: { userId: toObjectId(userId) } },
      { $unwind: '$exercises' },
      { $match: { 'exercises.name': exerciseName } },
      // Newest first; _id breaks ties for two sessions logged on the same day.
      { $sort: { workoutDate: -1, _id: -1 } },
      { $limit: Math.min(limit, PROGRESSION_MAX_LIMIT) },
    ])
    .toArray();

  const sessions = rows.map((row) => summariseExercise(row, preferredUnit));

  return {
    exercise: exerciseName,
    unit: preferredUnit,
    sessionCount: sessions.length,
    sessions,
    comparison: buildComparison(sessions, preferredUnit),
  };
}

/**
 * Every exercise the user has logged, with how often and when last.
 *
 * `timesLogged` counts occurrences, so an exercise recorded twice inside one
 * workout counts twice — which matches what the progression screen then shows.
 */
async function listExerciseSummaries(userId) {
  return workouts()
    .aggregate([
      { $match: { userId: toObjectId(userId) } },
      { $unwind: '$exercises' },
      {
        $group: {
          _id: '$exercises.name',
          timesLogged: { $sum: 1 },
          lastPerformedAt: { $max: '$workoutDate' },
        },
      },
      { $project: { _id: 0, name: '$_id', timesLogged: 1, lastPerformedAt: 1 } },
      { $sort: { name: 1 } },
    ])
    .toArray();
}

module.exports = {
  VERDICT,
  summariseExercise,
  classify,
  buildComparison,
  buildSuggestion,
  getExerciseProgression,
  listExerciseSummaries,
};
