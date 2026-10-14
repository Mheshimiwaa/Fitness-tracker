'use strict';

/**
 * ApiError is an Error that also carries an HTTP status code.
 *
 * Controllers throw `ApiError.badRequest('...')` and the central error handler
 * turns it into a clean JSON response. This keeps controllers free of repeated
 * `res.status(...).json(...)` error boilerplate.
 */
class ApiError extends Error {
  /**
   * @param {number} statusCode HTTP status code, e.g. 400, 401, 404.
   * @param {string} message    Safe, human-readable message for the client.
   * @param {*}      [details]  Optional extra info (e.g. validation issues).
   */
  constructor(statusCode, message, details) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.details = details;
    // Marks the error as "expected" so the handler knows it is safe to expose.
    this.isOperational = true;
  }

  static badRequest(message, details) {
    return new ApiError(400, message, details);
  }

  static unauthorized(message = 'Authentication required.') {
    return new ApiError(401, message);
  }

  static forbidden(message = 'You do not have access to this resource.') {
    return new ApiError(403, message);
  }

  static notFound(message = 'Resource not found.') {
    return new ApiError(404, message);
  }

  static conflict(message, details) {
    return new ApiError(409, message, details);
  }

  static tooManyRequests(message = 'Too many requests. Please try again later.') {
    return new ApiError(429, message);
  }

  static internal(message = 'Something went wrong on the server.') {
    return new ApiError(500, message);
  }
}

module.exports = ApiError;
