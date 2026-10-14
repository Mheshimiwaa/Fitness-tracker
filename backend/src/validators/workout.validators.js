'use strict';

/**
 * Zod schemas for the workout endpoints.
 *
 * A workout is a small tree: workout -> exercises -> sets. Zod validates that
 * tree in one pass, and `errorHandler` turns each issue's path into a dotted
 * string ("exercises.0.sets.1.reps") so the app can point at the exact field.
 *
 * Two rules are worth calling out:
 *
 *   1. A set's `weight` is checked against a wide, unit-agnostic bound here,
 *      and against the exact range for the EXERCISE's unit in the service —
 *      because the unit is usually omitted and defaulted from the user's
 *      preference. (Same split as weight entries.)
 *
 *   2. `workoutDate` may be back-dated (people log last night's session) but
 *      not set in the future beyond a small clock-skew tolerance.
 */

const { z } = require('zod');
const {
  WEIGHT_UNITS,
  WORKOUT_NAME_MAX_LENGTH,
  WORKOUT_NOTES_MAX_LENGTH,
  WORKOUT_MIN_MINUTES,
  WORKOUT_MAX_MINUTES,
  WORKOUT_MAX_EXERCISES,
  EXERCISE_NAME_MAX_LENGTH,
  EXERCISE_NOTES_MAX_LENGTH,
  EXERCISE_REST_MAX_SECONDS,
  EXERCISE_MAX_SETS,
  SET_REPS_MIN,
  SET_REPS_MAX,
  SET_WEIGHT_LB_MAX,
  MEASUREMENT_FUTURE_TOLERANCE_MS,
  MEASUREMENT_EARLIEST_YEAR,
} = require('../config/constants');

/** A workout timestamp that is neither far in the future nor ancient. */
const workoutDateField = z.coerce
  .date({ error: 'Workout date must be a valid date.' })
  .refine(
    (value) => value.getTime() <= Date.now() + MEASUREMENT_FUTURE_TOLERANCE_MS,
    { error: 'Workout date cannot be in the future.' }
  )
  .refine((value) => value.getFullYear() >= MEASUREMENT_EARLIEST_YEAR, {
    error: `Workout date must be after ${MEASUREMENT_EARLIEST_YEAR}.`,
  });

/** Optional free-text field. An empty string is normalised to null. */
function optionalNote(maxLength, label) {
  return z
    .string()
    .trim()
    .max(maxLength, { error: `${label} must be ${maxLength} characters or fewer.` })
    .transform((value) => (value === '' ? null : value));
}

const unitField = z.enum(WEIGHT_UNITS, {
  error: `Unit must be one of: ${WEIGHT_UNITS.join(', ')}.`,
});

/** One set: how many reps, at what load. */
const setSchema = z.object({
  reps: z.coerce
    .number({ error: 'Reps must be a number.' })
    .int({ error: 'Reps must be a whole number.' })
    .min(SET_REPS_MIN, { error: `Reps must be at least ${SET_REPS_MIN}.` })
    .max(SET_REPS_MAX, { error: `Reps must be at most ${SET_REPS_MAX}.` }),
  weight: z.coerce
    .number({ error: 'Weight must be a number.' })
    .min(0, { error: 'Weight cannot be negative.' })
    .max(SET_WEIGHT_LB_MAX, { error: 'Weight is unrealistically high.' }),
});

const setsField = z
  .array(setSchema, { error: 'Sets must be a list.' })
  .min(1, { error: 'Each exercise needs at least one set.' })
  .max(EXERCISE_MAX_SETS, {
    error: `An exercise can have at most ${EXERCISE_MAX_SETS} sets.`,
  });

/** One exercise and its sets. */
const exerciseSchema = z.object({
  name: z
    .string({ error: 'Exercise name is required.' })
    .trim()
    .min(1, { error: 'Exercise name is required.' })
    .max(EXERCISE_NAME_MAX_LENGTH, {
      error: `Exercise name must be ${EXERCISE_NAME_MAX_LENGTH} characters or fewer.`,
    }),
  unit: unitField.optional(),
  restSeconds: z.coerce
    .number({ error: 'Rest time must be a number of seconds.' })
    .int({ error: 'Rest time must be a whole number of seconds.' })
    .min(0, { error: 'Rest time cannot be negative.' })
    .max(EXERCISE_REST_MAX_SECONDS, {
      error: `Rest time must be at most ${EXERCISE_REST_MAX_SECONDS} seconds.`,
    })
    .optional(),
  notes: optionalNote(EXERCISE_NOTES_MAX_LENGTH, 'Exercise note').optional(),
  sets: setsField,
});

const exercisesField = z
  .array(exerciseSchema, { error: 'Exercises must be a list.' })
  .min(1, { error: 'Add at least one exercise.' })
  .max(WORKOUT_MAX_EXERCISES, {
    error: `A workout can have at most ${WORKOUT_MAX_EXERCISES} exercises.`,
  });

const durationField = z.coerce
  .number({ error: 'Duration must be a number of minutes.' })
  .int({ error: 'Duration must be a whole number of minutes.' })
  .min(WORKOUT_MIN_MINUTES, {
    error: `Duration must be at least ${WORKOUT_MIN_MINUTES} minute.`,
  })
  .max(WORKOUT_MAX_MINUTES, {
    error: `Duration must be at most ${WORKOUT_MAX_MINUTES} minutes.`,
  });

/** POST /api/workouts */
const createWorkoutSchema = z.object({
  name: z
    .string({ error: 'Workout name is required.' })
    .trim()
    .min(1, { error: 'Workout name is required.' })
    .max(WORKOUT_NAME_MAX_LENGTH, {
      error: `Workout name must be ${WORKOUT_NAME_MAX_LENGTH} characters or fewer.`,
    }),
  workoutDate: workoutDateField.optional(),
  durationMinutes: durationField.optional(),
  notes: optionalNote(WORKOUT_NOTES_MAX_LENGTH, 'Note').optional(),
  exercises: exercisesField,
});

/**
 * PATCH /api/workouts/:id
 *
 * At least one field must be present; an empty body is a client bug.
 *
 * Supplying `exercises` REPLACES the whole list — the editor always sends the
 * complete list it is showing, so a merge would only invite surprises.
 *
 * `durationMinutes` and `notes` additionally accept an explicit `null`, which
 * CLEARS them. Without that, a field could be set but never removed.
 */
const updateWorkoutSchema = z
  .object({
    name: z
      .string({ error: 'Workout name is required.' })
      .trim()
      .min(1, { error: 'Workout name is required.' })
      .max(WORKOUT_NAME_MAX_LENGTH, {
        error: `Workout name must be ${WORKOUT_NAME_MAX_LENGTH} characters or fewer.`,
      })
      .optional(),
    workoutDate: workoutDateField.optional(),
    durationMinutes: durationField.nullable().optional(),
    notes: optionalNote(WORKOUT_NOTES_MAX_LENGTH, 'Note').nullable().optional(),
    exercises: exercisesField.optional(),
  })
  .superRefine((data, ctx) => {
    const hasAnyField = Object.values(data).some((value) => value !== undefined);
    if (!hasAnyField) {
      ctx.addIssue({ code: 'custom', message: 'Provide at least one field to update.' });
    }
  });

/** GET /api/workouts?days=30&limit=50&order=desc */
const listWorkoutsQuerySchema = z.object({
  days: z.coerce
    .number({ error: 'Days must be a number.' })
    .int({ error: 'Days must be a whole number.' })
    .min(1, { error: 'Days must be at least 1.' })
    .max(3650, { error: 'Days must be at most 3650.' })
    .optional(),
  limit: z.coerce
    .number({ error: 'Limit must be a number.' })
    .int({ error: 'Limit must be a whole number.' })
    .min(1, { error: 'Limit must be at least 1.' })
    .max(200, { error: 'Limit must be at most 200.' })
    .optional(),
  order: z
    .enum(['asc', 'desc'], { error: "Order must be either 'asc' or 'desc'." })
    .optional(),
});

/** The `:id` path parameter. A 24-hex MongoDB id. */
const workoutIdParamSchema = z.object({
  id: z
    .string({ error: 'Workout id is required.' })
    .regex(/^[a-f\d]{24}$/i, { error: 'Workout id is not valid.' }),
});

module.exports = {
  createWorkoutSchema,
  updateWorkoutSchema,
  listWorkoutsQuerySchema,
  workoutIdParamSchema,
  // Exported for the schema-level tests.
  exerciseSchema,
  setSchema,
};
