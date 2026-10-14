'use strict';

/**
 * Authentication routes, mounted at /api/auth.
 *
 *   POST /api/auth/register   public   create an account
 *   POST /api/auth/login      public   obtain a JWT
 *   GET  /api/auth/me         private  who am I (validates a stored token)
 *
 * Middleware runs left to right:
 *   authLimiter  -> slows down brute-force attempts
 *   validate     -> rejects bad input with a 400 before any work is done
 *   authenticate -> loads req.user from the bearer token
 */

const express = require('express');
const { authLimiter } = require('../middleware/rateLimiter');
const { validate } = require('../middleware/validate');
const { authenticate } = require('../middleware/authenticate');
const { registerSchema, loginSchema } = require('../validators/auth.validators');
const authController = require('../controllers/auth.controller');

const router = express.Router();

router.post('/register', authLimiter, validate({ body: registerSchema }), authController.register);
router.post('/login', authLimiter, validate({ body: loginSchema }), authController.login);
router.get('/me', authenticate, authController.me);

module.exports = router;
