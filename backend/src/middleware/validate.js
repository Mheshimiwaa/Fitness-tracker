'use strict';

/**
 * Request validation middleware factory.
 *
 * Usage in a route:
 *
 *   router.post('/register', validate({ body: registerSchema }), controller)
 *   router.get('/search', validate({ query: searchUsersQuerySchema }), controller)
 *
 * What it does:
 *   1. Runs Zod over the chosen parts of the request (body / query / params).
 *   2. On success, REPLACES the raw values with the parsed ones, so downstream
 *      code works with trimmed, lower-cased, type-correct data.
 *   3. On failure, forwards the ZodError to the central error handler, which
 *      turns it into:
 *
 *          { "error": { "message": "Validation failed.", "details": [...] } }
 *
 * Express 5 automatically forwards a rejected promise from an async middleware
 * to the error handler, so no try/catch or `next(err)` is needed here.
 */

/**
 * Express 5 defines `req.query` (and sometimes `req.params`) as prototype
 * getters, which cannot be assigned to directly. `Object.defineProperty` adds a
 * normal own property that shadows the getter, so controllers can keep using
 * `req.query` / `req.params` as usual.
 */
function replaceRequestValue(req, key, value) {
  Object.defineProperty(req, key, {
    value,
    writable: true,
    enumerable: true,
    configurable: true,
  });
}

function validate(schemas) {
  return async (req, _res, next) => {
    if (schemas.params) {
      replaceRequestValue(req, 'params', await schemas.params.parseAsync(req.params));
    }
    if (schemas.query) {
      replaceRequestValue(req, 'query', await schemas.query.parseAsync(req.query));
    }
    if (schemas.body) {
      replaceRequestValue(req, 'body', await schemas.body.parseAsync(req.body));
    }
    next();
  };
}

module.exports = { validate };
