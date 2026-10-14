'use strict';

/**
 * Falls through to here when no route matched the request.
 * Produces the same JSON error shape as the central error handler.
 */
function notFound(req, res) {
  res.status(404).json({
    error: {
      message: `Route not found: ${req.method} ${req.originalUrl}`,
    },
  });
}

module.exports = notFound;
