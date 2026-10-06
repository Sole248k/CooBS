import 'dart:async';
import 'api_exceptions.dart';

enum CircuitState { closed, open, halfOpen }

/// 3-State Fail-Fast Client Circuit Breaker.
/// Protects mobile client responsiveness by immediately rejecting calls when infrastructure degrades.
class CircuitBreaker {
  final int failureThreshold;
  final Duration cooldownDuration;

  CircuitState _state = CircuitState.closed;
  int _consecutiveFailures = 0;
  DateTime? _lastStateChange;

  CircuitBreaker({
    this.failureThreshold = 3,
    this.cooldownDuration = const Duration(seconds: 30),
  });

  CircuitState get state {
    if (_state == CircuitState.open && _lastStateChange != null) {
      if (DateTime.now().difference(_lastStateChange!) >= cooldownDuration) {
        _state = CircuitState.halfOpen;
      }
    }
    return _state;
  }

  int get failureCount => _consecutiveFailures;

  Duration get remainingCooldown {
    if (_state != CircuitState.open || _lastStateChange == null) {
      return Duration.zero;
    }
    final elapsed = DateTime.now().difference(_lastStateChange!);
    final remaining = cooldownDuration - elapsed;
    return remaining.isNegative ? Duration.zero : remaining;
  }

  /// Executes an operation wrapped in the circuit breaker state machine.
  Future<T> execute<T>(Future<T> Function() operation) async {
    final currentState = state;

    if (currentState == CircuitState.open) {
      throw CircuitBreakerOpenException(remainingCooldown);
    }

    try {
      final result = await operation();
      onSuccess();
      return result;
    } catch (e) {
      // 4xx client errors (e.g. 422 limit exceeded, 401 unauth, 429 rate limit) do not trip the circuit breaker.
      // Only 5xx server outages, dropped sockets, and network timeouts count toward tripping.
      if (e is UnauthorizedException || e is RateLimitException) {
        rethrow;
      }
      if (e is ProblemDetailsException && e.statusCode != null && e.statusCode! < 500) {
        rethrow;
      }
      onFailure();
      rethrow;
    }
  }

  void onSuccess() {
    _consecutiveFailures = 0;
    _state = CircuitState.closed;
    _lastStateChange = DateTime.now();
  }

  void onFailure() {
    _consecutiveFailures++;
    if (_consecutiveFailures >= failureThreshold || _state == CircuitState.halfOpen) {
      _state = CircuitState.open;
      _lastStateChange = DateTime.now();
    }
  }

  void reset() {
    _consecutiveFailures = 0;
    _state = CircuitState.closed;
    _lastStateChange = DateTime.now();
  }
}
