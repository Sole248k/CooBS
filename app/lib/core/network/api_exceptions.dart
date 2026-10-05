import '../models/problem_details.dart';

/// Base exception for all network and API failures.
abstract class ApiException implements Exception {
  final String message;
  final int? statusCode;

  ApiException(this.message, [this.statusCode]);

  @override
  String toString() => message;
}

/// Thrown when the server returns an RFC-7807 application/problem+json error envelope.
class ProblemDetailsException extends ApiException {
  final ProblemDetails error;

  ProblemDetailsException(this.error)
      : super(error.detail, error.status);

  String get errorCode => error.errorCode;

  @override
  String toString() => '[$errorCode] ${error.title}: ${error.detail}';
}

/// Thrown on HTTP 401 Unauthorized or expired JWT token.
class UnauthorizedException extends ApiException {
  UnauthorizedException([String message = 'Session expired or unauthenticated'])
      : super(message, 401);
}

/// Thrown on HTTP 429 Too Many Requests (Redis rate limiter burst exceeded).
class RateLimitException extends ApiException {
  final int? retryAfterSeconds;

  RateLimitException([String message = 'Rate limit exceeded. Please wait a moment.', this.retryAfterSeconds])
      : super(message, 429);
}

/// Thrown when the client-side Circuit Breaker is in the OPEN state.
class CircuitBreakerOpenException extends ApiException {
  final Duration remainingCooldown;

  CircuitBreakerOpenException(this.remainingCooldown)
      : super('Service temporarily unavailable. Circuit breaker is cooling down ($remainingCooldown left).', 503);
}

/// Thrown when request execution exceeds the timeout budget.
class NetworkTimeoutException extends ApiException {
  NetworkTimeoutException([String message = 'Network connection timed out'])
      : super(message, 408);
}

/// Thrown on generic transport or connection failure.
class ConnectionException extends ApiException {
  ConnectionException([String message = 'Unable to establish connection to gateway'])
      : super(message, 502);
}
