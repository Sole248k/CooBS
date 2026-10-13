import 'package:app/features/accounts/demo_store.dart';
import 'package:app/features/vaults/share_capital_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Co-op Share Capital & Equity Ledger Model Tests', () {
    test('ShareCapitalModel initializes with correct default metrics and voting rights', () {
      final store = Demo.i;
      final sc = store.shareCapital;

      expect(sc.certificateNo, 'SC-2024-001001');
      expect(sc.subscribedShares, 500);
      expect(sc.paidUpShares, 250);
      expect(sc.parValue, 100.0);
      expect(sc.totalPaidAmount, 25000.0);
      expect(sc.subscribedAmount, 50000.0);
      expect(sc.minimumRequiredShares, 20);
      expect(sc.hasVotingRights, isTrue);
      expect(sc.membershipType, 'REGULAR');
      expect(sc.dividendRate, 7.25);
    });

    test('DividendPayoutModel contains patronage and share capital dividend declarations', () {
      final store = Demo.i;
      final divs = store.dividendHistory;

      expect(divs, isNotEmpty);
      final d2025 = divs.firstWhere((d) => d.fiscalYear == 2025);
      expect(d2025.shareCapitalDividend, 1812.50);
      expect(d2025.patronageRefund, 940.00);
      expect(d2025.totalPayout, 2752.50);
      expect(d2025.status, 'PAID');
    });

    test('topUpShareCapital: successfully purchases shares and debits savings account', () async {
      final store = Demo.i;
      final savings = store.accts[1];
      final initialSavings = savings.bal;
      final initialPaidShares = store.shareCapital.paidUpShares;
      final initialPaidAmt = store.shareCapital.totalPaidAmount;

      final err = await store.topUpShareCapital(2000.0);

      expect(err, isNull);
      expect(savings.bal, initialSavings - 2000.0);
      expect(store.shareCapital.paidUpShares, initialPaidShares + 20);
      expect(store.shareCapital.totalPaidAmount, initialPaidAmt + 2000.0);
      expect(store.shareCapital.hasVotingRights, isTrue);
      expect(savings.tx.first.label, contains('Capital Build-Up: +20 Shares'));
    });

    test('topUpShareCapital: returns error when amount exceeds available balance', () async {
      final store = Demo.i;
      final err = await store.topUpShareCapital(9999999.0);

      expect(err, isNotNull);
      expect(err, contains('Insufficient funds'));
    });
  });

  group('ShareCapitalScreen Widget Tests', () {
    testWidgets('Renders digital certificate with member name, voting rights badge, and metrics', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ShareCapitalScreen(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Co-op Share Capital'), findsOneWidget);
      expect(find.text('Member Share Certificate'), findsOneWidget);
      expect(find.text('SC-2024-001001'), findsOneWidget);
      expect(find.textContaining('Voting Rights'), findsOneWidget);
      expect(find.text('Capital Build-Up (CBU)'), findsOneWidget);
      expect(find.text('Dividend & Patronage Yield'), findsOneWidget);
      expect(find.text('7.25% p.a.'), findsOneWidget);
      expect(find.text('Annual Dividend Distribution History', skipOffstage: false), findsOneWidget);
    });

    testWidgets('Tapping Top Up button opens subscription sheet', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ShareCapitalScreen(),
        ),
      );
      await tester.pumpAndSettle();

      final topUpBtn = find.text('Top Up');
      expect(topUpBtn, findsOneWidget);

      await tester.tap(topUpBtn);
      await tester.pumpAndSettle();

      expect(find.text('Top-Up Co-op Share Capital'), findsOneWidget);
      expect(find.text('Confirm Subscription'), findsOneWidget);
    });
  });
}
