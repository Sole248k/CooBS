import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/core/models/models.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/core/storage/secure_storage_service.dart';
import 'package:app/features/accounts/home_screen.dart';
import 'package:app/features/auth/auth_screens.dart';
import 'package:app/features/tracking/tracking.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('End-to-End Client & Auth Flow (Zero-Backend Mode)', () {
    late ApiClient apiClient;
    late SecureStorageService storage;

    setUp(() {
      storage = SecureStorageService();
      apiClient = ApiClient(storage: storage);
    });

    test('1. Customer Login returns token and persists session', () async {
      final response = await apiClient.post(
        '/auth/customers/login',
        body: {'username': 'john_doe', 'password': 'Password123!'},
        useFixtures: true,
      );

      final tokenResp = TokenResponse.fromJson(response);
      expect(tokenResp.accessToken, isNotEmpty);
      expect(tokenResp.tokenType, 'Bearer');

      await storage.saveTokens(
        accessToken: tokenResp.accessToken,
        refreshToken: tokenResp.refreshToken,
        userId: tokenResp.userId,
        username: 'john_doe',
      );

      expect(await storage.hasValidSession(), isTrue);
      expect(await storage.getUsername(), 'john_doe');
    });

    test('2. Dashboard fetch retrieves composite member & balance fixture', () async {
      final json = await apiClient.get('/mobile/dashboard', useFixtures: true);
      final dashboard = MobileDashboardResponse.fromJson(json);

      expect(dashboard.member.displayName, contains('John'));
      expect(dashboard.member.kycStatus, 'VERIFIED');
      expect(dashboard.primaryAccount.currency, 'PHP');
      expect(dashboard.primaryAccount.availableBalance, greaterThan(0));
    });

    test('3. Remittance submission returns 202 receipt and tracking reference', () async {
      final json = await apiClient.post(
        '/mobile/transactions/remittance',
        body: {
          'destinationAccountNumber': 'ACC_1002_9915',
          'amount': 2500.00,
          'currency': 'PHP',
          'remarks': 'Lunch money',
        },
        useFixtures: true,
      );

      final receipt = RemittanceSubmissionResponse.fromJson(json);
      expect(receipt.referenceNo, 'TX-20261003-8F3A21C0');
      expect(receipt.status, anyOf('INITIATED', 'ACCEPTED'));
    });

    test('4. Tracking timeline maps 3-stage customer progression', () async {
      final json = await apiClient.get(
        '/mobile/transactions/TX-20261003-8F3A21C0/timeline',
        useFixtures: true,
      );
      final timeline = TransactionTimelineResponse.fromJson(json);
      final milestones = MilestoneMapper.mapFromTimeline(timeline);

      expect(milestones.length, 4);
      expect(milestones[0].status, MilestoneStatus.completed);
      expect(milestones[1].status, MilestoneStatus.inProgress);
    });

    testWidgets('5. LoginScreen UI flow handles authentication successfully', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: LoginScreen(),
        ),
      );

      expect(find.text('Welcome back'), findsOneWidget);
      expect(find.byType(TextField), findsNWidgets(2));

      // Enter credentials
      await tester.enterText(find.byType(TextField).first, 'john_doe');
      await tester.enterText(find.byType(TextField).last, 'Password123!');
      await tester.pump();

      // Tap Log in button
      await tester.tap(find.text('Log in'));
      for (var i = 0; i < 20; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }

      // Successfully transitioned to MobileShell
      expect(find.byType(MobileShell), findsOneWidget);
    });
  });
}
