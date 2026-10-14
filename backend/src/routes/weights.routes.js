'use strict';

/**
 * Weight routes, mounted at /api/weights. All require a valid token.
 *
 *   POST   /api/weights          log a weigh-in                  -> 201
 *   GET    /api/weights          history (+ change over window)  -> 200
 *   GET    /api/weights/latest   most recent entry (or null)     -> 200
 *   GET    /api/weights/:id      one entry                       -> 200
 *   PATCH  /api/weights/:id      edit an entry                   -> 200
 *   DELETE /api/weights/:id      delete an entry                -> 204
 *
 * Route order matters: `/latest` is declared BEFORE `/:id`, otherwise Express
 * would treat the literal string "latest" as an id.
 */

const express = require('express');
const { authenticate } = require('../middleware/authenticate');
const { validate } = require('../middleware/validate');
const {
  createWeightSchema,
  updateWeightSchema,
  listWeightsQuerySchema,
  weightIdParamSchema,
} = require('../validators/weight.validators');
const weightsController = require('../controllers/weights.controller');

const router = express.Router();

// Every route in this file requires authentication.
router.use(authenticate);

router.post('/', validate({ body: createWeightSchema }), weightsController.createWeight);
router.get('/', validate({ query: listWeightsQuerySchema }), weightsController.listWeights);
router.get('/latest', weightsController.getLatestWeight);

router.get(
  '/:id',
  validate({ params: weightIdParamSchema }),
  weightsController.getWeight
);
router.patch(
  '/:id',
  validate({ params: weightIdParamSchema, body: updateWeightSchema }),
  weightsController.updateWeight
);
router.delete(
  '/:id',
  validate({ params: weightIdParamSchema }),
  weightsController.deleteWeight
);

module.exports = router;
