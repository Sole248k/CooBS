import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:app/core/network/api_client.dart';
import 'package:app/core/network/api_exceptions.dart';
import 'package:app/core/storage/secure_storage_service.dart';

/// Lightweight custom HTTP client for mocking gateway endpoints and interceptor testing.
class MockGatewayClient extends http.BaseClient {
  final Future<http.Response> Function(http.BaseRequest request) handler;
  MockGatewayClient(this.handler);

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final response = await handler(request);
    return http.StreamedResponse(
      Stream.value(response.bodyBytes),
      response.statusCode,
      headers: response.headers,
      request: request,
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Automatic 401 Token Refresh Interceptor & Encrypted Storage', () {
    late SecureStorageService storage;

    setUp(() async {
      storage = SecureStorageService();
      await storage.clearSession();
      await storage.saveTokens(
        accessToken: 'initial-expired-access-token',
        refreshToken: 'valid-refresh-token-xyz',
        userId: 101,
        username: 'john_doe',
      );
    });

    test('401 on GET triggers silent refresh, rotates tokens in storage, and retries request', () async {
      int requestCount = 0;
      int refreshCount = 0;

      final mockClient = MockGatewayClient((request) async {
        final path = request.url.path;

        // Token refresh endpoint
        if (path.endsWith('/auth/token/refresh')) {
          refreshCount++;
          final body = jsonDecode((request as http.Request).body);
          expect(body['refreshToken'], 'valid-refresh-token-xyz');

          return http.Response(
            jsonEncode({
              'accessToken': 'rotated-new-access-token-999',
              'refreshToken': 'rotated-new-refresh-token-000',
              'tokenType': 'Bearer',
              'expiresIn': 900,
              'userId': 101,
              'username': 'john_doe',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }

        // Target API endpoint
        if (path.endsWith('/my-accounts')) {
          requestCount++;
          final authHeader = request.headers['authorization'];

          if (requestCount == 1) {
            // First attempt with initial token -> simulate 401 Unauthorized
            expect(authHeader, 'Bearer initial-expired-access-token');
            return http.Response(
              jsonEncode({
                'type': 'https://api.coobs.local/errors/AUTH_TOKEN_EXPIRED',
                'title': 'Token Expired',
                'status': 401,
                'detail': 'Access token has expired',
              }),
              401,
              headers: {'content-type': 'application/problem+json'},
            );
          } else {
            // Second attempt (retry) with rotated token -> 200 OK
            expect(authHeader, 'Bearer rotated-new-access-token-999');
            return http.Response(
              jsonEncode({'accountId': 501, 'status': 'ACTIVE', 'balance': 75000.0}),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
        }

        return http.Response('Not Found', 404);
      });

      final apiClient = ApiClient(httpClient: mockClient, storage: storage);

      // Execute request with useFixtures: false to test live gateway behavior
      final result = await apiClient.get('/my-accounts', useFixtures: false);

      expect(result['accountId'], 501);
      expect(result['balance'], 75000.0);
      expect(refreshCount, 1);
      expect(requestCount, 2);

      // Verify tokens were updated in SecureStorageService
      expect(await storage.getAccessToken(), 'rotated-new-access-token-999');
      expect(await storage.getRefreshToken(), 'rotated-new-refresh-token-000');
      expect(await storage.getUserId(), 101);
      expect(await storage.getUsername(), 'john_doe');
    });

    test('401 on POST retries with identical idempotency key and updated token', () async {
      String? firstIdempotencyKey;
      int postCount = 0;

      final mockClient = MockGatewayClient((request) async {
        final path = request.url.path;

        if (path.endsWith('/auth/token/refresh')) {
          return http.Response(
            jsonEncode({
              'accessToken': 'brand-new-access-token',
              'refreshToken': 'brand-new-refresh-token',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }

        if (path.endsWith('/transactions/transfers')) {
          postCount++;
          final idempotency = request.headers['idempotency-key'];

          if (postCount == 1) {
            firstIdempotencyKey = idempotency;
            expect(idempotency, isNotNull);
            return http.Response('Unauthorized', 401);
          } else {
            // Ensure retry preserves identical idempotency key
            expect(request.headers['idempotency-key'], firstIdempotencyKey);
            expect(request.headers['authorization'], 'Bearer brand-new-access-token');
            return http.Response(
              jsonEncode({'referenceNo': 'TX-998877', 'status': 'INITIATED'}),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
        }

        return http.Response('Not Found', 404);
      });

      final apiClient = ApiClient(httpClient: mockClient, storage: storage);

      final response = await apiClient.post(
        '/transactions/transfers',
        body: {'amount': 1500.0},
        isMoneyMovement: true,
        useFixtures: false,
      );

      expect(response['referenceNo'], 'TX-998877');
      expect(postCount, 2);
    });

    test('Concurrent 401 requests trigger exactly ONE refresh call (mutex lock)', () async {
      int refreshCallCount = 0;
      int getCallCount = 0;

      final mockClient = MockGatewayClient((request) async {
        final path = request.url.path;

        if (path.endsWith('/auth/token/refresh')) {
          refreshCallCount++;
          // Simulate slight network delay during refresh
          await Future.delayed(const Duration(milliseconds: 20));
          return http.Response(
            jsonEncode({
              'accessToken': 'synchronized-access-token',
              'refreshToken': 'synchronized-refresh-token',
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }

        if (path.endsWith('/data')) {
          getCallCount++;
          if (request.headers['authorization'] != 'Bearer synchronized-access-token') {
            return http.Response('Unauthorized', 401);
          }
          return http.Response(
            jsonEncode({'status': 'ok'}),
            200,
            headers: {'content-type': 'application/json'},
          );
        }

        return http.Response('Not Found', 404);
      });

      final apiClient = ApiClient(httpClient: mockClient, storage: storage);

      // Fire 3 simultaneous requests
      final results = await Future.wait([
        apiClient.get('/data', useFixtures: false),
        apiClient.get('/data', useFixtures: false),
        apiClient.get('/data', useFixtures: false),
      ]);

      expect(results.length, 3);
      for (final res in results) {
        expect(res['status'], 'ok');
      }

      // Exactly ONE refresh request must have occurred
      expect(refreshCallCount, 1);
      // 3 initial 401s + 3 retried successes = 6 total
      expect(getCallCount, 6);
    });

    test('Failed refresh (401 from gateway) clears storage and throws UnauthorizedException', () async {
      final mockClient = MockGatewayClient((request) async {
        final path = request.url.path;

        if (path.endsWith('/auth/token/refresh')) {
          // Gateway rejects expired/blacklisted refresh token
          return http.Response(
            jsonEncode({'errorCode': 'AUTH_TOKEN_REVOKED', 'detail': 'Refresh token revoked'}),
            401,
            headers: {'content-type': 'application/problem+json'},
          );
        }

        return http.Response('Unauthorized', 401);
      });

      final apiClient = ApiClient(httpClient: mockClient, storage: storage);

      expect(
        () => apiClient.get('/accounts', useFixtures: false),
        throwsA(isA<UnauthorizedException>()),
      );

      // Wait a moment for async cleanup to complete
      await Future.delayed(const Duration(milliseconds: 10));

      // Session must be wiped
      expect(await storage.getAccessToken(), isNull);
      expect(await storage.hasValidSession(), isFalse);
    });

    test('Auth path does not attempt token refresh recursion on 401', () async {
      int refreshCallCount = 0;

      final mockClient = MockGatewayClient((request) async {
        final path = request.url.path;

        if (path.endsWith('/auth/token/refresh')) {
          refreshCallCount++;
          return http.Response('Unexpected refresh', 200);
        }

        if (path.contains('/auth/customers/login')) {
          return http.Response(
            jsonEncode({'detail': 'Invalid credentials'}),
            401,
            headers: {'content-type': 'application/json'},
          );
        }

        return http.Response('Not Found', 404);
      });

      final apiClient = ApiClient(httpClient: mockClient, storage: storage);

      expect(
        () => apiClient.post('/auth/customers/login', body: {}, useFixtures: false),
        throwsA(isA<UnauthorizedException>()),
      );

      expect(refreshCallCount, 0);
    });

    test('Default fixture mode remains functional without touching live gateway', () async {
      final mockClient = MockGatewayClient((request) async {
        fail('Live HTTP client should not be invoked when useFixtures is true');
      });

      final apiClient = ApiClient(httpClient: mockClient, storage: storage);

      // Default call without useFixtures flag respects AppConfig.useFixtures (true)
      final dashboard = await apiClient.get('/mobile/dashboard');
      expect(dashboard['member'], isNotNull);
      expect(dashboard['primaryAccount'], isNotNull);
    });
  });
}
