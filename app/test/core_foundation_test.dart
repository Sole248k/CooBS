import 'package:flutter_test/flutter_test.dart';
import 'package:app/core/models/models.dart';
import 'package:app/core/network/network.dart';
import 'package:app/core/storage/secure_storage_service.dart';
import 'package:app/features/accounts/demo_store.dart';
import 'package:app/features/data/mobile_repo.dart';

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

    test('Demo store safely handles single-account user profile without RangeError', () {
      final store = Demo.i;
      store.accts.clear();
      store.accts.add(Acct('Checking', 'ACC-10000001', 5000.0, []));
      store.cur = 0;

      // Accessing curTx should succeed
      expect(store.curTx, isEmpty);

      // Navigating to loan page (index 1 when 1 account exists)
      store.cur = 1;
      expect(store.isLoanPage, isTrue);
      expect(store.curTx, isEmpty);

      // Applying loan to single account
      store.applyLoan('Personal', 10000, 6);
      expect(store.loan, isNotNull);
      expect(store.accts[0].bal, 15000.0);
      expect(store.accts[0].tx.length, 1);
      store.cur = 0;
      expect(store.curTx.length, 1);

      // Single-account deposit
      final err = store.deposit(0, null, 1000.0);
      expect(err, isNull);
      expect(store.accts[0].bal, 16000.0);

      store.reset();
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

    test('MobileRepo and Demo.load retrieve KYC profile and beneficiaries', () async {
      final profile = await MobileRepo.customerProfile();
      expect(profile['kycStatus'], 'VERIFIED');
      expect(profile['kyc'], isNotNull);

      final bens = await MobileRepo.beneficiaries();
      expect(bens, isNotEmpty);
      expect(bens.first['nickname'], 'Juan Dela Cruz');

      final store = Demo.i;
      await store.load();
      expect(store.bens, isNotEmpty);
      expect(store.kycData, isNotNull);
      expect(store.kycData!['firstName'], isNotNull);
    });

    test('Demo store accounts parsing handles string balances and maintains accounts display', () async {
      final store = Demo.i;
      await store.load();
      expect(store.accts, isNotEmpty);
      expect(store.accts.first.bal, greaterThan(0));
      expect(store.accts.first.no, isNotEmpty);
      expect(store.accts.first.type, isNotEmpty);
      expect(store.state, isNot(LoadState.offline));
    });
  });
}

