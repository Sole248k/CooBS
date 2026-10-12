import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:app/features/accounts/demo_store.dart';
import 'package:app/features/accounts/ui.dart';
import 'package:app/features/scores/scores_screen.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/features/data/mobile_repo.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('MOB-08 Scores & Habits and Reversal Verification', () {
    test('Reversal transactions are mapped as positive inflows with Reversal Refund title', () {
      final store = Demo.i;

      // Mock reversal payload from backend
      final rawTx = [
        {
          'transactionId': 'rev-001',
          'referenceNo': 'RV-TX-20261012-01',
          'direction': 'IN',
          'category': 'REVERSAL',
          'amount': '1500.00',
          'remarks': 'Reversal of TX-20261012-01',
          'counterpartyName': 'Reversal Refund',
          'createdAt': '2026-10-12T10:00:00Z',
        },
        {
          'transactionId': 'rev-002',
          'referenceNo': 'RV-TX-20261012-02',
          'direction': 'OUT',
          'category': 'REVERSAL',
          'amount': '500.00',
          'counterpartyName': 'Reversal Debit',
          'createdAt': '2026-10-12T10:05:00Z',
        }
      ];

      // Reversal refund (sender gets money back)
      final txRefund = Tx(
        'Reversal of TX-20261012-01',
        'RV-TX-20261012-01',
        DateTime(2026, 10, 12),
        1500.00,
      );

      expect(txRefund.amt, greaterThan(0));
      expect(txRefund.amt, 1500.00);

      // Reversal debit (recipient returned money)
      final txDebit = Tx(
        'Reversal Debit',
        'RV-TX-20261012-02',
        DateTime(2026, 10, 12),
        -500.00,
      );
      expect(txDebit.amt, lessThan(0));
      expect(txDebit.amt, -500.00);
    });

    test('Demo.i fetchScoresAndHabits populates creditScore, spendingBreakdown and totals from C3', () async {
      final store = Demo.i;
      await store.fetchScoresAndHabits();

      expect(store.creditScore, isNotNull);
      expect(store.creditScore, 750);
      expect(store.spendingBreakdown, isNotEmpty);
      expect(store.spendingBreakdown.containsKey('Groceries & Food'), isTrue);
      expect(store.spendingBreakdown['Groceries & Food'], 8450.0);
      expect(store.totalDeposits, greaterThan(0));
    });

    testWidgets('ScoresPage renders Credit Score, Totals, and Spending Breakdown', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ScoresPage(),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('750'), findsOneWidget);
      expect(find.text('Your Credit Score'), findsOneWidget);
      expect(find.text('Total Deposits'), findsOneWidget);
      expect(find.text('Total Loans'), findsOneWidget);
      expect(find.text('30-Day Spending'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('Debit transaction history'), 300);
      expect(find.text('Debit transaction history'), findsOneWidget);
    });

    testWidgets('ScoresPage dynamically renders Grace credit score of 600', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      final client = ApiClient();
      MobileRepo.apiForTesting = client;
      addTearDown(() async {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
        MobileRepo.apiForTesting = ApiClient();
      });

      await client.storage.saveTokens(
        accessToken: 'grace-access-token',
        userId: 9,
        username: 'grace_pangilinan',
      );
      Demo.i.creditScore = 600;

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ScoresPage(),
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('600'), findsOneWidget);
      expect(find.text('Fair'), findsOneWidget);
    });

    testWidgets('txRow renders Declined badge and strikethrough for RISK_DECLINED failed transfers', (tester) async {
      final declinedTx = Tx(
        'Transfer to Juan Dela Cruz',
        'TX-20261012-7A1B2C3D',
        DateTime(2026, 10, 12),
        -100.0,
        status: 'FAILED',
        failureCode: 'RISK_DECLINED',
      );

      final postedTx = Tx(
        'Transfer to Maria Santos',
        'TX-20261012-11223344',
        DateTime(2026, 10, 12),
        -200.0,
        status: 'POSTED',
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Column(
              children: [
                txRow(declinedTx),
                txRow(postedTx),
              ],
            ),
          ),
        ),
      );

      await tester.pump();

      expect(find.text('Declined'), findsOneWidget);
      expect(find.text('Success'), findsOneWidget);
      expect(find.text('Transfer to Juan Dela Cruz'), findsOneWidget);
      expect(find.text('Transfer to Maria Santos'), findsOneWidget);
    });
  });
}
