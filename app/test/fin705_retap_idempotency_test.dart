import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:app/core/config/app_config.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/core/storage/secure_storage_service.dart';
import 'package:app/core/theme/app_theme.dart';
import 'package:app/features/auth/auth_screens.dart';
import 'package:app/features/data/mobile_repo.dart';
import 'package:app/features/remittance/remittance_screen.dart';

final uuidV4 = RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$');

const remittancePath = '/api/v1/mobile/transactions/remittance';

http.Response jsonResponse(Object body, int status) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

http.Response accepted() => jsonResponse({'transactionId': 'tx-1', 'referenceNo': 'TX-LIVE-0001', 'status': 'INITIATED'}, 202);

Map<String, dynamic> completedTimeline() => {
      'transactionId': 'tx-1',
      'referenceNo': 'TX-LIVE-0001',
      'trackingReference': 'TX-LIVE-0001',
      'status': 'POSTED',
      'overallStatus': 'COMPLETED',
      'view': 'CUSTOMER',
      'currentStage': 3,
      'totalStages': 3,
      'stages': [
        {'stageNumber': 1, 'stageKey': 'SUBMITTED', 'title': 'Transfer Submitted', 'status': 'COMPLETED', 'outcome': 'PASSED'},
        {'stageNumber': 2, 'stageKey': 'IN_REVIEW', 'title': 'Security & Clearing', 'status': 'COMPLETED', 'outcome': 'PASSED'},
        {'stageNumber': 3, 'stageKey': 'COMPLETED', 'title': 'Transfer Completed', 'status': 'COMPLETED', 'outcome': 'PASSED'},
      ],
    };

/// Gateway double whose replies are decided per request and may be held open,
/// so a second tap can land while the first submit is still in flight.
class ScriptedGateway extends http.BaseClient {
  final Future<http.Response> Function(http.BaseRequest request) handler;
  final List<http.BaseRequest> requests = [];
  ScriptedGateway(this.handler);

  List<http.BaseRequest> to(String path) => requests.where((r) => r.url.path == path).toList();

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    final response = await handler(request);
    return http.StreamedResponse(Stream.value(response.bodyBytes), response.statusCode,
        headers: response.headers, request: request);
  }
}

/// Answers timeline polls at once and hands each remittance POST to [remit].
ScriptedGateway remittanceGateway(Future<http.Response> Function(int attempt) remit) {
  var attempt = 0;
  return ScriptedGateway((req) async {
    if (req.url.path == remittancePath) return remit(attempt++);
    if (req.url.path.endsWith('/timeline')) return jsonResponse(completedTimeline(), 200);
    return http.Response('', 404);
  });
}

Future<void> openTransferScreen(WidgetTester tester, ScriptedGateway gateway) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);
  MobileRepo.apiForTesting = ApiClient(httpClient: gateway);

  // Accounts and the R1 catalog load from the bundled fixtures; the submit itself goes live.
  AppConfig.useFixtures = true;
  await tester.pumpWidget(MaterialApp(theme: AppTheme.lightTheme, home: const RemittanceScreen()));
  final recipient = find.widgetWithText(TextField, 'Recipient account number');
  for (var i = 0; i < 50 && recipient.evaluate().isEmpty; i++) {
    await tester.runAsync(() => Future.delayed(const Duration(milliseconds: 100)));
    await tester.pump();
  }
  AppConfig.useFixtures = false;

  await tester.enterText(recipient, 'ACC_10000004');
  await tester.enterText(find.widgetWithText(TextField, 'Amount (₱)'), '100');
}

/// Lets the receipt's tracker finish polling, then tears the tree down so no timers leak.
Future<void> settleAndDispose(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 500));
  }
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 1));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late bool previousUseFixtures;
  setUp(() {
    previousUseFixtures = AppConfig.useFixtures;
    // Cached asset futures belong to the previous test's fake clock and would never resolve here.
    rootBundle.clear();
  });
  tearDown(() {
    AppConfig.useFixtures = previousUseFixtures;
    MobileRepo.apiForTesting = ApiClient();
  });

  group('Rapid re-tap guard', () {
    testWidgets('Member transfer screen: a double-tap sends exactly one remittance POST', (tester) async {
      final reply = Completer<http.Response>();
      final gateway = remittanceGateway((_) => reply.future);
      await openTransferScreen(tester, gateway);

      // Second tap lands before the next frame, while the button is still drawn as enabled.
      await tester.tap(find.text('Send transfer'));
      await tester.tap(find.text('Send transfer'), warnIfMissed: false);
      await tester.pump();

      expect(gateway.to(remittancePath), hasLength(1));
      expect(find.text('Sending…'), findsOneWidget, reason: 'button shows the in-flight state');

      reply.complete(accepted());
      await settleAndDispose(tester);
      expect(gateway.to(remittancePath), hasLength(1));
    });

    testWidgets('Transfer form: a double-tap sends exactly one remittance POST', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);
      AppConfig.useFixtures = false;
      final reply = Completer<http.Response>();
      final gateway = remittanceGateway((_) => reply.future);

      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        // The test font draws every glyph a full em wide; scale it down so the form fits like on a phone.
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(0.6)),
          child: child!,
        ),
        home: Scaffold(
          body: RemittanceForm(
            defaultSourceAccount: 'ACC_10000001',
            availableBalance: 10000,
            apiClient: ApiClient(httpClient: gateway),
          ),
        ),
      ));

      await tester.tap(find.text('Submit Transfer'));
      await tester.tap(find.text('Submit Transfer'), warnIfMissed: false);
      await tester.pump();

      expect(gateway.to(remittancePath), hasLength(1));

      reply.complete(accepted());
      await tester.pump(const Duration(seconds: 5));
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('Login: a double-tap sends exactly one login request', (tester) async {
      AppConfig.useFixtures = false;
      final reply = Completer<http.Response>();
      final gateway = ScriptedGateway((_) => reply.future);

      await tester.pumpWidget(MaterialApp(home: LoginScreen(apiClient: ApiClient(httpClient: gateway))));
      await tester.enterText(find.byType(TextField).first, 'john_doe');
      await tester.enterText(find.byType(TextField).last, 'wrong-password');

      await tester.tap(find.text('Log in'));
      await tester.tap(find.text('Log in'), warnIfMissed: false);
      await tester.pump();

      expect(gateway.to('/api/v1/auth/customers/login'), hasLength(1));

      reply.complete(jsonResponse({'detail': 'Invalid username or password'}, 401));
      await tester.pump(const Duration(seconds: 5));
    });
  });

  group('UUIDv4 client idempotency', () {
    test('Money movement without a caller key gets a UUIDv4 Idempotency-Key; reads and logins get none', () async {
      AppConfig.useFixtures = false;
      final gateway = ScriptedGateway((req) async => req.url.path == remittancePath ? accepted() : jsonResponse({}, 200));
      final api = ApiClient(httpClient: gateway);

      await api.post('/mobile/transactions/remittance', body: {'amount': '1.00'}, isMoneyMovement: true);
      await api.post('/mobile/transactions/remittance', body: {'amount': '1.00'}, isMoneyMovement: true);
      await api.get('/mobile/transactions/remittance/options');

      final first = gateway.requests[0].headers['Idempotency-Key']!;
      final second = gateway.requests[1].headers['Idempotency-Key']!;
      expect(first, matches(uuidV4));
      expect(second, matches(uuidV4));
      expect(second, isNot(first), reason: 'two independent submits are two different operations');
      expect(gateway.requests[2].headers.containsKey('Idempotency-Key'), isFalse);
      expect(gateway.requests.every((r) => uuidV4.hasMatch(r.headers['X-Correlation-ID']!)), isTrue);
    });

    test('The silent retry after a 401 token refresh reuses the same Idempotency-Key and correlation ID', () async {
      AppConfig.useFixtures = false;
      final storage = SecureStorageService();
      await storage.saveTokens(accessToken: 'expired-access', refreshToken: 'refresh-1');
      var remitCalls = 0;
      final gateway = ScriptedGateway((req) async {
        if (req.url.path == '/api/v1/auth/token/refresh') {
          return jsonResponse({'accessToken': 'fresh-access', 'refreshToken': 'refresh-2'}, 200);
        }
        return remitCalls++ == 0 ? jsonResponse({'detail': 'expired'}, 401) : accepted();
      });
      final api = ApiClient(httpClient: gateway, storage: storage);

      await api.post('/mobile/transactions/remittance', body: {'amount': '1.00'}, isMoneyMovement: true,
          idempotencyKey: '5b0e7c1e-2a7d-4f43-9a51-0c6d3c1f9e21');

      final attempts = gateway.to(remittancePath);
      expect(attempts, hasLength(2));
      expect(attempts[0].headers['Authorization'], 'Bearer expired-access');
      expect(attempts[1].headers['Authorization'], 'Bearer fresh-access');
      expect(attempts[1].headers['Idempotency-Key'], attempts[0].headers['Idempotency-Key']);
      expect(attempts[1].headers['X-Correlation-ID'], attempts[0].headers['X-Correlation-ID']);
      expect(await storage.getRefreshToken(), 'refresh-2', reason: 'rotated refresh token is stored');
    });

    testWidgets('Retrying after a timeout resends the same UUIDv4 key, so the server can deduplicate', (tester) async {
      final gateway = remittanceGateway((attempt) =>
          attempt == 0 ? Completer<http.Response>().future : Future.value(accepted()));
      await openTransferScreen(tester, gateway);

      await tester.tap(find.text('Send transfer'));
      await tester.pump(AppConfig.requestTimeout + const Duration(seconds: 1));
      await tester.pump();
      expect(find.textContaining('Submission Failed'), findsOneWidget);

      await tester.tap(find.text('Send transfer'));
      await settleAndDispose(tester);

      final attempts = gateway.to(remittancePath);
      expect(attempts, hasLength(2));
      expect(attempts[0].headers['Idempotency-Key'], matches(uuidV4));
      expect(attempts[1].headers['Idempotency-Key'], attempts[0].headers['Idempotency-Key']);
    });

    testWidgets('After a definite rejection the next submit uses a fresh UUIDv4 key', (tester) async {
      final gateway = remittanceGateway((attempt) => Future.value(attempt == 0
          ? jsonResponse({
              'type': 'https://api.coobs.local/errors/LIMIT_EXCEEDED',
              'title': 'Limit exceeded',
              'status': 422,
              'detail': 'Daily limit reached',
              'errorCode': 'LIMIT_EXCEEDED',
            }, 422)
          : accepted()));
      await openTransferScreen(tester, gateway);

      await tester.tap(find.text('Send transfer'));
      await tester.pump();
      await tester.pump();
      expect(find.textContaining('Limit exceeded (LIMIT_EXCEEDED)'), findsOneWidget);

      await tester.tap(find.text('Send transfer'));
      await settleAndDispose(tester);

      final attempts = gateway.to(remittancePath);
      expect(attempts, hasLength(2));
      expect(attempts[0].headers['Idempotency-Key'], matches(uuidV4));
      expect(attempts[1].headers['Idempotency-Key'], matches(uuidV4));
      expect(attempts[1].headers['Idempotency-Key'], isNot(attempts[0].headers['Idempotency-Key']));
    });
  });

  group('Mobile login', () {
    testWidgets('Five failed logins lock the form for 30 seconds without calling the server', (tester) async {
      AppConfig.useFixtures = false;
      final gateway = ScriptedGateway((_) async => jsonResponse({'detail': 'Invalid username or password'}, 401));

      await tester.pumpWidget(MaterialApp(home: LoginScreen(apiClient: ApiClient(httpClient: gateway))));
      await tester.enterText(find.byType(TextField).first, 'john_doe');
      await tester.enterText(find.byType(TextField).last, 'wrong-password');

      for (var i = 0; i < 5; i++) {
        await tester.tap(find.text('Log in'));
        await tester.pump();
        await tester.pump();
      }
      expect(gateway.requests, hasLength(5));

      // Sixth attempt inside the 30s window is refused locally.
      await tester.tap(find.text('Log in'));
      await tester.pump();
      expect(gateway.requests, hasLength(5), reason: 'the locked attempt never reaches the server');

      // Snackbars queue, so walk the queue and check both lockout messages are shown.
      var sawShield = false, sawLock = false;
      for (var i = 0; i < 40 && !(sawShield && sawLock); i++) {
        await tester.pump(const Duration(seconds: 1));
        sawShield |= find.textContaining('5 failed attempts').evaluate().isNotEmpty;
        sawLock |= find.textContaining('Security lock active').evaluate().isNotEmpty;
      }
      expect(sawShield, isTrue);
      expect(sawLock, isTrue);

      await tester.pump(const Duration(seconds: 30));
    });
  });
}
