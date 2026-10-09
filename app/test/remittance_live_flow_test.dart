import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:app/core/config/app_config.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/core/network/api_exceptions.dart';
import 'package:app/core/theme/app_theme.dart';
import 'package:app/features/data/mobile_repo.dart';
import 'package:app/features/remittance/remittance_screen.dart';
import 'package:app/features/tracking/tracking.dart';

/// Routes gateway calls to an in-test handler and records every request.
class RecordingGatewayClient extends http.BaseClient {
  final http.Response Function(http.BaseRequest request) handler;
  final List<http.BaseRequest> requests = [];
  RecordingGatewayClient(this.handler);

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    final response = handler(request);
    return http.StreamedResponse(
      Stream.value(response.bodyBytes),
      response.statusCode,
      headers: response.headers,
      request: request,
    );
  }
}

Map<String, dynamic> customerTimeline(String overallStatus) {
  final done = overallStatus == 'COMPLETED';
  return {
    'transactionId': '6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11',
    'referenceNo': 'TX-LIVE-0001',
    'trackingReference': 'TX-LIVE-0001',
    'status': done ? 'POSTED' : 'PROCESSING',
    'overallStatus': overallStatus,
    'view': 'CUSTOMER',
    'currentStage': done ? 3 : 2,
    'totalStages': 3,
    'stages': [
      {'stageNumber': 1, 'stageKey': 'SUBMITTED', 'title': 'Transfer Submitted', 'status': 'COMPLETED', 'outcome': 'PASSED'},
      {'stageNumber': 2, 'stageKey': 'IN_REVIEW', 'title': 'Security & Clearing', 'status': done ? 'COMPLETED' : 'IN_PROGRESS', 'outcome': done ? 'PASSED' : 'PENDING'},
      {'stageNumber': 3, 'stageKey': 'COMPLETED', 'title': 'Transfer Completed', 'status': done ? 'COMPLETED' : 'PENDING', 'outcome': done ? 'PASSED' : 'PENDING'},
    ],
  };
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late bool previousUseFixtures;
  setUp(() {
    previousUseFixtures = AppConfig.useFixtures;
    AppConfig.useFixtures = false;
  });
  tearDown(() => AppConfig.useFixtures = previousUseFixtures);

  group('Live remittance intake (M3 via mobile-bff)', () {
    test('POST carries the caller-supplied Idempotency-Key and X-Correlation-ID', () async {
      final client = RecordingGatewayClient((_) => http.Response(
            jsonEncode({'transactionId': 'tx-1', 'referenceNo': 'TX-LIVE-0001', 'status': 'INITIATED'}),
            202,
            headers: {'content-type': 'application/json'},
          ));
      final api = ApiClient(httpClient: client);

      final resp = await api.post(
        '/mobile/transactions/remittance',
        body: {'destinationAccountNumber': 'ACC_10000002', 'amount': '100.00'},
        isMoneyMovement: true,
        idempotencyKey: 'idem-fixed-123',
        correlationId: 'corr-fixed-456',
      );

      expect(resp['referenceNo'], 'TX-LIVE-0001');
      final req = client.requests.single;
      expect(req.method, 'POST');
      expect(req.url.path, '/api/v1/mobile/transactions/remittance');
      expect(req.headers['Idempotency-Key'], 'idem-fixed-123');
      expect(req.headers['X-Correlation-ID'], 'corr-fixed-456');
    });

    test('Only ambiguous network failures keep the Idempotency-Key for retry', () {
      expect(isOutcomeUnknown(NetworkTimeoutException()), isTrue);
      expect(isOutcomeUnknown(ConnectionException('socket closed')), isTrue);
      expect(isOutcomeUnknown(RateLimitException('slow down', 5)), isFalse);
      expect(isOutcomeUnknown(UnauthorizedException()), isFalse);
    });
  });

  group('Remittance limits (R1)', () {
    tearDown(() => MobileRepo.apiForTesting = ApiClient());

    test('Live mode takes amount limits from mobile-bff and keeps the bundled bank catalog', () async {
      final client = RecordingGatewayClient((_) => http.Response(
            jsonEncode({'currency': 'PHP', 'scale': 2, 'limits': {'minAmount': 0.01, 'maxAmount': 500000.0}}),
            200,
            headers: {'content-type': 'application/json'},
          ));
      MobileRepo.apiForTesting = ApiClient(httpClient: client);

      final opts = await MobileRepo.remitOptions();

      expect(client.requests.single.url.path, '/api/v1/mobile/transactions/remittance/options');
      expect(opts['limits'], {'minAmount': 0.01, 'maxAmount': 500000.0});
      expect(opts['limitsSource'], 'server');
      expect((opts['banks'] as List).map((b) => b['code']), contains('COOBS'));
    });

    test('Falls back to bundled limits when the options endpoint fails', () async {
      MobileRepo.apiForTesting = ApiClient(httpClient: RecordingGatewayClient((_) => http.Response('', 404)));

      final opts = await MobileRepo.remitOptions();

      expect(opts.containsKey('limitsSource'), isFalse);
      expect((opts['limits'] as Map)['maxAmount'], 500000.0, reason: 'bundled copy mirrors the BFF ceiling');
    });
  });

  group('Member transfer screen (fixture mode)', () {
    testWidgets('Sending to a CooBS account reaches the receipt with the live tracker', (tester) async {
      AppConfig.useFixtures = true;
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(MaterialApp(theme: AppTheme.lightTheme, home: const RemittanceScreen()));
      // FutureBuilder loads R1 options from the asset bundle (real I/O)
      await tester.runAsync(() => Future.delayed(const Duration(milliseconds: 300)));
      await tester.pump();

      await tester.enterText(find.widgetWithText(TextField, 'Recipient account number'), 'ACC_10000004');
      await tester.enterText(find.widgetWithText(TextField, 'Amount (₱)'), '100');
      await tester.tap(find.text('Send transfer'));
      await tester.runAsync(() => Future.delayed(const Duration(milliseconds: 300)));
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 200));
      }

      expect(tester.takeException(), isNull);
      expect(find.text('Transfer submitted'), findsOneWidget);
      expect(find.text('Transaction Milestones'), findsOneWidget);
    });
  });

  group('Live timeline polling (M2 via mobile-bff)', () {
    testWidgets('Polls once a second and stops when the transfer reaches COMPLETED', (tester) async {
      var polls = 0;
      final client = RecordingGatewayClient((request) {
        polls++;
        // First two reads still in flight, then the ledger posts
        final status = polls < 3 ? 'PROCESSING' : 'COMPLETED';
        return http.Response(jsonEncode(customerTimeline(status)), 200, headers: {'content-type': 'application/json'});
      });

      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: TrackingView(
            trackingReference: 'TX-LIVE-0001',
            apiClient: ApiClient(httpClient: client),
            animate: false,
          ),
        ),
      ));
      await tester.pump();
      expect(find.text('PROCESSING'), findsOneWidget);

      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      expect(find.text('COMPLETED'), findsWidgets);
      expect(client.requests.every((r) => r.url.path == '/api/v1/mobile/transactions/TX-LIVE-0001/timeline'), isTrue);

      final pollsAtTerminal = polls;
      await tester.pump(const Duration(seconds: 5));
      expect(polls, pollsAtTerminal, reason: 'polling must stop once a terminal status is reached');
    });

    testWidgets('Backs off to 5s after 30s and pauses after 2 minutes for a stuck transfer', (tester) async {
      var polls = 0;
      final client = RecordingGatewayClient((_) {
        polls++;
        return http.Response(jsonEncode(customerTimeline('PROCESSING')), 200, headers: {'content-type': 'application/json'});
      });

      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: TrackingView(trackingReference: 'TX-LIVE-0001', apiClient: ApiClient(httpClient: client), animate: false),
        ),
      ));
      await tester.pump();
      expect(polls, 1, reason: 'initial load');

      Future<void> advance(int seconds) async {
        for (var i = 0; i < seconds; i++) {
          await tester.pump(const Duration(seconds: 1));
        }
      }

      await advance(30);
      expect(polls, 31, reason: '30 one-second polls in the fast window');

      await advance(90);
      expect(polls, 49, reason: '18 five-second polls until the 2-minute budget');
      expect(find.text('Check again'), findsOneWidget);

      await advance(60);
      expect(polls, 49, reason: 'polling stays paused');

      await tester.tap(find.text('Check again'));
      await tester.pump();
      await advance(2);
      expect(polls, greaterThan(50), reason: 'manual check reloads and restarts the budget');
      expect(find.text('Check again'), findsNothing);
    });

    testWidgets('Embedded mode hides the reference search bar', (tester) async {
      final client = RecordingGatewayClient(
          (_) => http.Response(jsonEncode(customerTimeline('COMPLETED')), 200, headers: {'content-type': 'application/json'}));

      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: ListView(children: [
            TrackingView(
              trackingReference: 'TX-LIVE-0001',
              apiClient: ApiClient(httpClient: client),
              animate: false,
              embedded: true,
            ),
          ]),
        ),
      ));
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(find.text('Transaction Reference'), findsNothing);
      expect(find.text('Transaction Milestones'), findsOneWidget);
    });
  });
}
