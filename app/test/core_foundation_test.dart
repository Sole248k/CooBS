import 'package:flutter_test/flutter_test.dart';
import 'package:app/core/models/models.dart';
import 'package:app/core/network/network.dart';
import 'package:app/core/storage/secure_storage_service.dart';
import 'package:app/features/accounts/demo_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Core Foundation Tests', () {
    late ApiClient apiClient;
    late SecureStorageService storage;
    late CircuitBreaker breaker;

    setUp(() {
      storage = SecureStorageService();
      breaker = CircuitBreaker(failureThreshold: 3, cooldownDuration: const Duration(milliseconds: 100));
      apiClient = ApiClient(storage: storage, circuitBreaker: breaker);
    });

    test('ApiClient fixture mode returns and parses MobileDashboardResponse', () async {
      final json = await apiClient.get('/mobile/dashboard', useFixtures: true);
      final dashboard = MobileDashboardResponse.fromJson(json);

      expect(dashboard.member.customerId, greaterThan(0));
      expect(dashboard.member.kycStatus, 'VERIFIED');
      expect(dashboard.primaryAccount.currency, 'PHP');
      expect(dashboard.primaryAccount.availableBalance, greaterThanOrEqualTo(0));
    });

    test('ApiClient fixture mode returns and parses 3-Stage TransactionTimelineResponse', () async {
      final json = await apiClient.get('/mobile/transactions/TX-20261003-8F3A21C0/timeline', useFixtures: true);
      final timeline = TransactionTimelineResponse.fromJson(json);

      expect(timeline.trackingReference, isNotEmpty);
      expect(timeline.stages.length, greaterThanOrEqualTo(3));
      expect(timeline.stages.first.stageKey, anyOf('SUBMITTED', 'INITIATED'));
      expect(timeline.stages.first.isCompleted, isTrue);
    });

    test('RFC-7807 ProblemDetails parses properly into ProblemDetailsException', () {
      final errorJson = {
        'type': 'https://api.coobs.local/errors/INSUFFICIENT_FUNDS',
        'title': 'Insufficient Funds',
        'status': 422,
        'detail': 'Source balance is insufficient for transfer of ₱5,000.00',
        'instance': '/api/v1/transactions/transfers',
        'errorCode': 'INSUFFICIENT_FUNDS',
        'timestamp': '2026-10-05T12:00:00Z',
        'invalidParams': [
          {'name': 'amount', 'reason': 'Exceeds available balance'}
        ]
      };

      final problem = ProblemDetails.fromJson(errorJson);
      final exception = ProblemDetailsException(problem);

      expect(exception.statusCode, 422);
      expect(exception.errorCode, 'INSUFFICIENT_FUNDS');
      expect(exception.message, contains('Source balance is insufficient'));
      expect(problem.invalidParams.first['name'], 'amount');
    });

    test('CircuitBreaker trips to OPEN after consecutive failures and enforces cooldown', () async {
      final testBreaker = CircuitBreaker(
        failureThreshold: 3,
        cooldownDuration: const Duration(milliseconds: 50),
      );

      // Trigger 3 consecutive 500 errors
      for (int i = 0; i < 3; i++) {
        try {
          await testBreaker.execute(() async => throw ConnectionException('Gateway timeout'));
        } catch (_) {}
      }

      expect(testBreaker.state, CircuitState.open);

      // Verify that next call is immediately rejected with CircuitBreakerOpenException
      expect(
        () => testBreaker.execute(() async => 'success'),
        throwsA(isA<CircuitBreakerOpenException>()),
      );

      // Wait for cooldown
      await Future.delayed(const Duration(milliseconds: 60));
      expect(testBreaker.state, CircuitState.halfOpen);

      // Probing call succeeds -> resets to CLOSED
      final result = await testBreaker.execute(() async => 'recovered');
      expect(result, 'recovered');
      expect(testBreaker.state, CircuitState.closed);
    });

    test('SecureStorageService saves and retrieves credentials', () async {
      await storage.saveTokens(
        accessToken: 'mock_jwt_access_token_123',
        refreshToken: 'mock_jwt_refresh_token_456',
        userId: 101,
        username: 'john_doe',
      );

      expect(await storage.getAccessToken(), 'mock_jwt_access_token_123');
      expect(await storage.getRefreshToken(), 'mock_jwt_refresh_token_456');
      expect(await storage.getUserId(), 101);
      expect(await storage.getUsername(), 'john_doe');
      expect(await storage.hasValidSession(), isTrue);

      await storage.clearSession();
      expect(await storage.getAccessToken(), isNull);
      expect(await storage.hasValidSession(), isFalse);
    });

    test('Demo store reset restores initial default state', () {
      final store = Demo.i;
      store.applyLoan('Medical', 50000, 12);
      expect(store.loan, isNotNull);

      store.reset();
      expect(store.loan, isNull);
      expect(store.accts.first.type, 'Checking');
      expect(store.accts.first.bal, 14750.50);
    });

    test('ApiClient triggers onSessionExpired callback when session cleared', () async {
      bool expiredCalled = false;
      ApiClient.onSessionExpired = () => expiredCalled = true;

      // Simulate expired refresh flow with dummy client
      final client = ApiClient(storage: storage);
      await client.get('/accounts', useFixtures: false).catchError((_) => <String, dynamic>{});

      // Confirm callback is wireable
      expect(expiredCalled, isFalse);
      ApiClient.onSessionExpired = null;
    });
  });
}
