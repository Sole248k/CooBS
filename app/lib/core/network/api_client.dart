import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';

import '../config/app_config.dart';
import '../models/auth_models.dart';
import '../models/problem_details.dart';
import '../storage/secure_storage_service.dart';
import 'api_exceptions.dart';
import 'circuit_breaker.dart';

/// Unified API Client for the CooBS Mobile Application.
/// Features dual-mode execution (Air-Gapped Fixtures vs. Live Gateway),
/// automatic UUID idempotency tagging, RFC-7807 error parsing, automatic 401 token refresh,
/// and Circuit Breaker fail-fast resilience.
class ApiClient {
  final http.Client _httpClient;
  final SecureStorageService _storage;
  final CircuitBreaker circuitBreaker;
  final Uuid _uuid;

  /// Active refresh lock to serialize concurrent token refresh calls.
  Completer<String?>? _refreshCompleter;

  /// Global callback triggered when session is invalidated due to rejected/expired token.
  static void Function()? onSessionExpired;

  ApiClient({
    http.Client? httpClient,
    SecureStorageService? storage,
    CircuitBreaker? circuitBreaker,
  })  : _httpClient = httpClient ?? http.Client(),
        _storage = storage ?? SecureStorageService(),
        circuitBreaker = circuitBreaker ?? CircuitBreaker(),
        _uuid = const Uuid();

  SecureStorageService get storage => _storage;

  /// Executes an HTTP GET request with fixture interceptor.
  Future<Map<String, dynamic>> get(
    String path, {
    Map<String, String>? queryParams,
    String? correlationId,
    bool? useFixtures,
  }) async {
    final effectiveUseFixtures = useFixtures ?? AppConfig.useFixtures;

    if (effectiveUseFixtures) {
      return _interceptWithFixture('GET', path, queryParams: queryParams);
    }

    return circuitBreaker.execute(() async {
      return _executeWithAuthRefresh(
        path: path,
        correlationId: correlationId,
        send: (headers) => _httpClient
            .get(_buildUri(path, queryParams), headers: headers)
            .timeout(AppConfig.requestTimeout),
      );
    });
  }

  /// Executes an HTTP POST request with fixture interceptor.
  Future<Map<String, dynamic>> post(
    String path, {
    dynamic body,
    bool isMoneyMovement = false,
    String? idempotencyKey,
    String? correlationId,
    bool? useFixtures,
  }) async {
    final effectiveUseFixtures = useFixtures ?? AppConfig.useFixtures;

    if (effectiveUseFixtures) {
      return _interceptWithFixture('POST', path, body: body);
    }

    return circuitBreaker.execute(() async {
      final encodedBody = body != null ? jsonEncode(body) : null;
      return _executeWithAuthRefresh(
        path: path,
        isMoneyMovement: isMoneyMovement,
        idempotencyKey: idempotencyKey,
        correlationId: correlationId,
        send: (headers) => _httpClient
            .post(
              _buildUri(path),
              headers: headers,
              body: encodedBody,
            )
            .timeout(AppConfig.requestTimeout),
      );
    });
  }

  /// Executes an HTTP PATCH request with fixture interceptor.
  Future<Map<String, dynamic>> patch(
    String path, {
    dynamic body,
    bool? useFixtures,
  }) async {
    final effectiveUseFixtures = useFixtures ?? AppConfig.useFixtures;

    if (effectiveUseFixtures) {
      return _interceptWithFixture('PATCH', path, body: body);
    }

    return circuitBreaker.execute(() async {
      final encodedBody = body != null ? jsonEncode(body) : null;
      return _executeWithAuthRefresh(
        path: path,
        send: (headers) => _httpClient
            .patch(
              _buildUri(path),
              headers: headers,
              body: encodedBody,
            )
            .timeout(AppConfig.requestTimeout),
      );
    });
  }

  /// Executes an HTTP DELETE request with fixture interceptor.
  Future<Map<String, dynamic>> delete(
    String path, {
    bool? useFixtures,
  }) async {
    final effectiveUseFixtures = useFixtures ?? AppConfig.useFixtures;

    if (effectiveUseFixtures) {
      return _interceptWithFixture('DELETE', path);
    }

    return circuitBreaker.execute(() async {
      return _executeWithAuthRefresh(
        path: path,
        send: (headers) => _httpClient
            .delete(
              _buildUri(path),
              headers: headers,
            )
            .timeout(AppConfig.requestTimeout),
      );
    });
  }

  /// Evaluates whether a route is an authentication lifecycle endpoint to prevent refresh recursion.
  bool _isAuthPath(String path) {
    final lower = path.toLowerCase();
    return lower.contains('/auth/token/refresh') ||
        lower.contains('/auth/token/revoke') ||
        lower.contains('/auth/customers/login') ||
        lower.contains('/auth/staff/login') ||
        lower.contains('/login');
  }

  /// Executes an HTTP request with automatic 401 token refresh and retry.
  Future<Map<String, dynamic>> _executeWithAuthRefresh({
    required String path,
    required Future<http.Response> Function(Map<String, String> headers) send,
    bool isMoneyMovement = false,
    String? idempotencyKey,
    String? correlationId,
  }) async {
    final resolvedIdempotency = idempotencyKey ?? (isMoneyMovement ? _uuid.v4() : null);
    // Keep one correlation ID across the 401 refresh retry so both attempts trace together
    final resolvedCorrelation = (correlationId != null && correlationId.isNotEmpty) ? correlationId : _uuid.v4();
    final initialHeaders = await _buildHeaders(
      isMoneyMovement: isMoneyMovement,
      idempotencyKey: resolvedIdempotency,
      correlationId: resolvedCorrelation,
    );

    try {
      var response = await send(initialHeaders);

      // Intercept 401 Unauthorized for non-auth endpoints and attempt silent token refresh
      if (response.statusCode == 401 && !_isAuthPath(path)) {
        final newAccessToken = await _performTokenRefresh();
        if (newAccessToken != null && newAccessToken.isNotEmpty) {
          final retryHeaders = await _buildHeaders(
            isMoneyMovement: isMoneyMovement,
            idempotencyKey: resolvedIdempotency,
            correlationId: resolvedCorrelation,
          );
          response = await send(retryHeaders);
        }
      }

      return _processResponse(response);
    } on TimeoutException {
      throw NetworkTimeoutException();
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ConnectionException(e.toString());
    }
  }

  /// Exchanges the stored refresh token for a fresh access token at POST /api/v1/auth/token/refresh.
  /// Deduplicates concurrent callers using an active Completer lock.
  Future<String?> _performTokenRefresh() async {
    if (_refreshCompleter != null) {
      return _refreshCompleter!.future;
    }

    final completer = Completer<String?>();
    _refreshCompleter = completer;

    try {
      final refreshToken = await _storage.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        await _storage.clearSession();
        onSessionExpired?.call();
        completer.complete(null);
        return null;
      }

      final refreshUri = _buildUri('/auth/token/refresh');
      final response = await _httpClient.post(
        refreshUri,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json, application/problem+json',
          'X-Correlation-ID': _uuid.v4(),
        },
        body: jsonEncode({'refreshToken': refreshToken}),
      ).timeout(AppConfig.fastTimeout);

      if (response.statusCode >= 200 && response.statusCode < 300) {
        final decoded = jsonDecode(response.body);
        final tokenData = TokenResponse.fromJson(
          decoded is Map<String, dynamic> ? decoded : <String, dynamic>{},
        );

        if (tokenData.accessToken.isNotEmpty) {
          await _storage.updateTokens(
            accessToken: tokenData.accessToken,
            refreshToken: tokenData.refreshToken.isNotEmpty ? tokenData.refreshToken : refreshToken,
          );
          completer.complete(tokenData.accessToken);
          return tokenData.accessToken;
        }
      }

      // Refresh token rejected or expired -> invalidate local session
      await _storage.clearSession();
      onSessionExpired?.call();
      completer.complete(null);
      return null;
    } catch (_) {
      await _storage.clearSession();
      onSessionExpired?.call();
      completer.complete(null);
      return null;
    } finally {
      _refreshCompleter = null;
    }
  }

  /// Builds query URI from relative path or absolute URL.
  Uri _buildUri(String path, [Map<String, String>? queryParams]) {
    if (path.startsWith('http://') || path.startsWith('https://')) {
      final parsed = Uri.parse(path);
      if (queryParams != null && queryParams.isNotEmpty) {
        return parsed.replace(queryParameters: {...parsed.queryParameters, ...queryParams});
      }
      return parsed;
    }

    final normalizedPath = path.startsWith('/') ? path : '/$path';
    final apiPath = normalizedPath.startsWith('/api/v1')
        ? normalizedPath
        : '/api/v1$normalizedPath';
    final fullUrl = '${AppConfig.gatewayBaseUrl}$apiPath';
    final parsed = Uri.parse(fullUrl);
    if (queryParams != null && queryParams.isNotEmpty) {
      return parsed.replace(queryParameters: queryParams);
    }
    return parsed;
  }

  /// Injects authentication tokens, tracing correlation IDs, and money movement idempotency keys.
  Future<Map<String, String>> _buildHeaders({bool isMoneyMovement = false, String? idempotencyKey, String? correlationId}) async {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json, application/problem+json',
      'X-Correlation-ID': (correlationId != null && correlationId.isNotEmpty) ? correlationId : _uuid.v4(),
    };

    final token = await _storage.getAccessToken();
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }

    if (idempotencyKey != null && idempotencyKey.isNotEmpty) {
      headers['Idempotency-Key'] = idempotencyKey;
    } else if (isMoneyMovement) {
      headers['Idempotency-Key'] = _uuid.v4();
    }

    return headers;
  }

  /// Parses HTTP response into JSON map or throws structured RFC-7807 ProblemDetailsException.
  Map<String, dynamic> _processResponse(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isEmpty) return <String, dynamic>{};
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, dynamic>) return decoded;
      return {'data': decoded};
    }

    if (response.statusCode == 401) {
      try {
        final errorJson = jsonDecode(response.body);
        if (errorJson is Map<String, dynamic>) {
          final detail = errorJson['detail'] ?? errorJson['message'] ?? errorJson['title'];
          if (detail != null && '$detail'.isNotEmpty) {
            throw UnauthorizedException('$detail');
          }
        }
      } catch (e) {
        if (e is UnauthorizedException) rethrow;
      }
      throw UnauthorizedException();
    }

    if (response.statusCode == 429) {
      final retryAfter = int.tryParse(response.headers['retry-after'] ?? '');
      throw RateLimitException('Rate limit exceeded. Please wait.', retryAfter);
    }

    try {
      final errorJson = jsonDecode(response.body);
      if (errorJson is Map<String, dynamic>) {
        final problem = ProblemDetails.fromJson(errorJson);
        throw ProblemDetailsException(problem);
      }
    } catch (e) {
      if (e is ApiException) rethrow;
    }

    throw ProblemDetailsException(
      ProblemDetails(
        type: 'https://api.coobs.local/errors/HTTP_${response.statusCode}',
        title: 'HTTP Error',
        status: response.statusCode,
        detail: response.body.isNotEmpty ? response.body : 'Request failed with status ${response.statusCode}',
        instance: response.request?.url.path ?? '',
        errorCode: 'HTTP_${response.statusCode}',
        timestamp: DateTime.now(),
      ),
    );
  }

  /// Intercepts network calls in offline air-gapped fixture mode (Handshake H15).
  Future<Map<String, dynamic>> _interceptWithFixture(
    String method,
    String path, {
    Map<String, String>? queryParams,
    dynamic body,
  }) async {
    // Artificial 50ms realistic network latency simulation outside test runners
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (!isTest) {
      await Future.delayed(const Duration(milliseconds: 50));
    }

    final fixtureFile = _resolveFixtureFilename(path, queryParams);
    return loadFixtureJson(fixtureFile);
  }

  /// Resolves the corresponding JSON mock fixture filename based on the API route.
  String _resolveFixtureFilename(String path, [Map<String, String>? queryParams]) {
    final lower = path.toLowerCase();

    if (lower.contains('login') || lower.contains('/auth/token')) {
      return 'A2-A4-login.response.json';
    }
    if (lower.contains('my-accounts')) {
      return 'K5-my-accounts.response.json';
    }
    if (lower.contains('/accounts')) {
      return 'K3-K4-account.response.json';
    }
    if (lower.contains('remittance/options') || lower.contains('remittance-options')) {
      return 'R1-remittance-options.response.json';
    }
    if (lower.contains('kyc/options') || lower.contains('kyc-intake-options')) {
      return 'K1-kyc-intake-options.response.json';
    }
    if (lower.contains('/kyc')) {
      return 'K2-kyc-intake.response.json';
    }
    if (lower.contains('/mobile/dashboard')) {
      return 'M1-mobile-dashboard.response.json';
    }
    if (lower.contains('/mobile/transactions/remittance') || lower.contains('mobile-remittance')) {
      return 'M3-mobile-remittance.response.json';
    }
    if (lower.contains('/transfers') || lower.contains('/remittance')) {
      return 'T1-transfer.response.json';
    }
    if (lower.contains('/timeline')) {
      if (lower.contains('dlq') || queryParams?['dlq'] == 'true') {
        return 'T3-timeline-dlq.response.json';
      }
      final view = queryParams?['view']?.toLowerCase();
      if (view == 'staff') {
        return 'T3-timeline-staff.response.json';
      }
      return 'T3-timeline-customer.response.json';
    }
    if (lower.contains('/dlq') || lower.contains('replay')) {
      return 'T7-dlq-replay.response.json';
    }
    if (lower.contains('/transactions/')) {
      return 'T2-transaction.response.json';
    }
    if (lower.contains('/notifications')) {
      return 'N1-notifications.response.json';
    }

    // Default fallback to composite dashboard fixture
    return 'M1-mobile-dashboard.response.json';
  }

  /// Loads and parses a curated fixture JSON from assets/fixtures/.
  Future<Map<String, dynamic>> loadFixtureJson(String filename) async {
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (isTest) {
      return _getEmbeddedFixtureFallback(filename);
    }
    try {
      final jsonString = await rootBundle.loadString('assets/fixtures/$filename');
      final decoded = jsonDecode(jsonString);
      if (decoded is Map<String, dynamic>) return decoded;
      if (decoded is List) return {'items': decoded, 'data': decoded};
      return <String, dynamic>{};
    } catch (_) {
      // In headless unit tests where rootBundle asset manifests may not be initialized,
      // return a verified fallback representation matching the contract fixture.
      return _getEmbeddedFixtureFallback(filename);
    }
  }

  /// Headless test fallback matching frozen contract fixtures.
  Map<String, dynamic> _getEmbeddedFixtureFallback(String filename) {
    if (filename.contains('login')) {
      return {
        'authData': {
          'accessToken': 'dummy-jwt-access-token-coobs',
          'refreshToken': 'dummy-refresh-token',
          'tokenType': 'Bearer',
          'expiresIn': 900,
          'userId': 1,
          'username': 'john_doe',
          'roles': ['ROLE_CUSTOMER'],
          'userType': 'CUSTOMER'
        }
      };
    }
    if (filename.contains('dashboard')) {
      return {
        'member': {
          'customerId': 101,
          'displayName': 'John Doe',
          'email': 'john.doe@coobs.local',
          'kycStatus': 'VERIFIED'
        },
        'primaryAccount': {
          'accountId': 501,
          'accountNumber': 'ACC_10000005',
          'accountType': 'SAVINGS',
          'currency': 'PHP',
          'availableBalance': 50000.0,
          'formattedBalance': '₱50,000.00',
          'status': 'ACTIVE'
        },
        'unreadNotifications': 2,
        'recentTransactions': []
      };
    }
    if (filename.contains('timeline-customer')) {
      return {
        'transactionId': '6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11',
        'trackingReference': 'TX-20261003-8F3A21C0',
        'status': 'PROCESSING',
        'overallStatus': 'PROCESSING',
        'view': 'CUSTOMER',
        'currentStage': 1,
        'totalStages': 3,
        'stages': [
          {
            'stageNumber': 1,
            'stageKey': 'SUBMITTED',
            'title': 'Transfer Submitted',
            'status': 'COMPLETED',
            'occurredAt': '2026-10-03T11:40:00.010Z'
          },
          {
            'stageNumber': 2,
            'stageKey': 'IN_REVIEW',
            'title': 'Security & Clearing',
            'status': 'IN_PROGRESS',
            'occurredAt': null
          },
          {
            'stageNumber': 3,
            'stageKey': 'COMPLETED',
            'title': 'Transfer Completed',
            'status': 'PENDING',
            'occurredAt': null
          }
        ]
      };
    }
    if (filename.contains('timeline-staff')) {
      return {
        'transactionId': '6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11',
        'trackingReference': 'TX-20261003-8F3A21C0',
        'status': 'POSTED',
        'view': 'STAFF',
        'currentStage': 11,
        'totalStages': 11,
        'overallStatus': 'COMPLETED',
        'stages': [
          {'stageNo': 1, 'stageNumber': 1, 'stageKey': 'INGRESS_ACCEPTED', 'title': 'Ingress Accepted', 'service': 'transaction-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.010Z'},
          {'stageNo': 2, 'stageNumber': 2, 'stageKey': 'PREFLIGHT_VALIDATED', 'title': 'Pre-flight Solvency Checked', 'service': 'transaction-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.020Z'},
          {'stageNo': 3, 'stageNumber': 3, 'stageKey': 'DISPATCHED_TO_ORCHESTRATOR', 'title': 'Dispatched to Orchestrator', 'service': 'transaction-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.030Z'},
          {'stageNo': 4, 'stageNumber': 4, 'stageKey': 'SAGA_INITIALIZED', 'title': 'Saga State Machine Initialized', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.040Z'},
          {'stageNo': 5, 'stageNumber': 5, 'stageKey': 'RISK_EVALUATION_REQUESTED', 'title': 'Risk Evaluation Requested', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.050Z'},
          {'stageNo': 6, 'stageNumber': 6, 'stageKey': 'RISK_EVALUATION_COMPLETED', 'title': 'Risk Screening Passed', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.060Z', 'detail': 'score 0.15 (Low Risk)'},
          {'stageNo': 7, 'stageNumber': 7, 'stageKey': 'LEDGER_HOLD_RESERVED', 'title': 'Ledger Balance Hold Reserved', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.120Z'},
          {'stageNo': 8, 'stageNumber': 8, 'stageKey': 'LEDGER_POSTED', 'title': 'Double-Entry SoR Committed', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.210Z', 'detail': 'Journal Ref: TRF-TX20261003'},
          {'stageNo': 9, 'stageNumber': 9, 'stageKey': 'EVENT_EMITTED', 'title': 'Domain Event Emitted', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:00.450Z', 'detail': 'Topic: ledger.transfer.completed.v1'},
          {'stageNo': 10, 'stageNumber': 10, 'stageKey': 'NOTIFICATIONS_DISPATCHED', 'title': 'Member Notifications Sent', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:01.100Z'},
          {'stageNo': 11, 'stageNumber': 11, 'stageKey': 'COMPLETED', 'title': 'Saga Fully Reconciled', 'service': 'orchestration-service', 'status': 'COMPLETED', 'occurredAt': '2026-10-03T11:40:01.400Z'}
        ]
      };
    }
    if (filename.contains('timeline-dlq')) {
      return {
        'transactionId': '6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11',
        'trackingReference': 'TX-20261003-8F3A21C0',
        'status': 'FAILED',
        'overallStatus': 'FAILED',
        'view': 'STAFF',
        'currentStage': 10,
        'totalStages': 11,
        'failureMessage': 'Notification dispatch poison pill routed to dead-letter queue (DLQ)',
        'stages': [
          {'stageNo': 1, 'stageNumber': 1, 'stageKey': 'INGRESS_ACCEPTED', 'title': 'Ingress Accepted', 'service': 'transaction-service', 'status': 'COMPLETED'},
          {'stageNo': 2, 'stageNumber': 2, 'stageKey': 'PREFLIGHT_VALIDATED', 'title': 'Pre-flight Solvency Checked', 'service': 'transaction-service', 'status': 'COMPLETED'},
          {'stageNo': 3, 'stageNumber': 3, 'stageKey': 'DISPATCHED_TO_ORCHESTRATOR', 'title': 'Dispatched to Orchestrator', 'service': 'transaction-service', 'status': 'COMPLETED'},
          {'stageNo': 4, 'stageNumber': 4, 'stageKey': 'SAGA_INITIALIZED', 'title': 'Saga State Machine Initialized', 'service': 'orchestration-service', 'status': 'COMPLETED'},
          {'stageNo': 5, 'stageNumber': 5, 'stageKey': 'RISK_EVALUATION_REQUESTED', 'title': 'Risk Evaluation Requested', 'service': 'orchestration-service', 'status': 'COMPLETED'},
          {'stageNo': 6, 'stageNumber': 6, 'stageKey': 'RISK_EVALUATION_COMPLETED', 'title': 'Risk Screening Passed', 'service': 'orchestration-service', 'status': 'COMPLETED'},
          {'stageNo': 7, 'stageNumber': 7, 'stageKey': 'LEDGER_HOLD_RESERVED', 'title': 'Ledger Balance Hold Reserved', 'service': 'ledger-service', 'status': 'COMPLETED'},
          {'stageNo': 8, 'stageNumber': 8, 'stageKey': 'LEDGER_POSTED', 'title': 'Double-Entry SoR Committed', 'service': 'ledger-service', 'status': 'COMPLETED'},
          {'stageNo': 9, 'stageNumber': 9, 'stageKey': 'EVENT_EMITTED', 'title': 'Domain Event Emitted', 'service': 'notification-service', 'status': 'COMPLETED'},
          {
            'stageNo': 10,
            'stageNumber': 10,
            'stageKey': 'NOTIFICATIONS_DISPATCHED',
            'title': 'Member Notifications Sent',
            'service': 'notification-service',
            'status': 'FAILED',
            'detail': 'Poison pill routed to notification.events.DLT after 3 backoff retries exhausted',
            'isDlq': true,
            'retryAttempts': 3,
            'dlqTopic': 'notification.events.DLT'
          },
          {'stageNo': 11, 'stageNumber': 11, 'stageKey': 'COMPLETED', 'title': 'Saga Fully Reconciled', 'service': 'orchestration-service', 'status': 'PENDING'}
        ]
      };
    }
    if (filename.contains('replay') || filename.contains('dlq-replay')) {
      return {
        'status': 'REPLAYED',
        'topic': 'notification.events.DLT',
        'replayedCount': 1,
        'message': 'Successfully replayed DLQ record to original Kafka topic',
      };
    }
    if (filename.contains('transfer') || filename.contains('remittance')) {
      return {
        'transactionId': '6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11',
        'referenceNo': 'TX-20261003-8F3A21C0',
        'status': 'INITIATED',
        'amount': '2500.00',
        'currency': 'PHP',
        'createdAt': '2026-10-03T11:40:00Z',
        'timelineUrl': '/api/v1/transactions/6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11/timeline',
      };
    }
    if (filename.contains('account')) {
      return {
        'accountId': 1,
        'customerId': 1,
        'customerName': 'John Doe',
        'accountNumber': 'ACC_10000001',
        'accountType': 'SAVINGS',
        'currency': 'PHP',
        'status': 'ACTIVE',
        'availableBalance': 49625.0,
        'createdAt': '2026-01-15T08:00:00',
        'message': null,
      };
    }
    if (filename.contains('notification')) {
      return {
        'unreadCount': 2,
        'items': [
          {
            'id': 'notif-1',
            'title': 'Transfer Initiated',
            'message': 'Your transfer of PHP 2,500.00 is being processed.',
            'createdAt': '2026-10-03T11:40:00Z',
            'isRead': false,
          }
        ]
      };
    }
    return <String, dynamic>{};
  }
}
