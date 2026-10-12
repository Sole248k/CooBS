import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:app/features/accounts/demo_store.dart';
import 'package:app/features/accounts/home_screen.dart';
import 'package:app/features/scores/scores_screen.dart';

void main() {
  setUp(() {
    Demo.i.reset();
  });

  group('Feature 1: Smart Envelopes Category Budgeting', () {
    test('Default category budgets and getBudgetForCategory lookup', () {
      final store = Demo.i;
      expect(store.getBudgetForCategory('Groceries & Food'), 10000.0);
      expect(store.getBudgetForCategory('FOOD'), 10000.0);
      expect(store.getBudgetForCategory('Utilities & Bills'), 5000.0);
      expect(store.getBudgetForCategory('Shopping & Retail'), 4000.0);
      expect(store.getBudgetForCategory('Transportation'), 2500.0);
      expect(store.getBudgetForCategory('Entertainment'), 1500.0);
    });

    test('Updating category budget mutates store and notifies listeners', () {
      final store = Demo.i;
      bool notified = false;
      store.addListener(() {
        notified = true;
      });

      store.updateCategoryBudget('Groceries & Food', 15000.0);
      expect(store.getBudgetForCategory('Groceries & Food'), 15000.0);
      expect(notified, isTrue);
    });
  });

  group('Feature 2: Virtual "Ipon" Vaults / Sub-Savings Goals', () {
    test('Seeded vaults exist with correct earmark totals', () {
      final store = Demo.i;
      expect(store.iponVaults.length, greaterThanOrEqualTo(3));
      expect(store.iponVaults.any((v) => v.name == 'Emergency Buffer'), isTrue);
      expect(store.iponVaults.any((v) => v.name == 'Paluwagan & Holidays'), isTrue);
      expect(store.iponVaults.any((v) => v.name == 'Co-op Share Capital'), isTrue);

      final total = store.totalEarmarkedInVaults;
      expect(total, 18500.0 + 9200.0 + 7300.0);
    });

    test('Allocating to vault earmarks funds from savings account', () {
      final store = Demo.i;
      final savings = store.accts[1];
      final initialBal = savings.bal;
      final vault = store.iponVaults.firstWhere((v) => v.id == 'VAULT-01');
      final initialVaultAmt = vault.currentAmount;

      final err = store.allocateToVault('VAULT-01', 2000.0);
      expect(err, isNull);
      expect(savings.bal, initialBal - 2000.0);
      expect(vault.currentAmount, initialVaultAmt + 2000.0);
      expect(savings.tx.first.label, contains('Earmarked for Emergency Buffer'));
    });

    test('Allocating more than available savings returns error', () {
      final store = Demo.i;
      final err = store.allocateToVault('VAULT-01', 9999999.0);
      expect(err, isNotNull);
      expect(err, contains('Insufficient funds'));
    });

    test('Releasing funds from vault restores savings balance', () {
      final store = Demo.i;
      final savings = store.accts[1];
      final initialBal = savings.bal;
      final vault = store.iponVaults.firstWhere((v) => v.id == 'VAULT-01');
      final initialVaultAmt = vault.currentAmount;

      final err = store.releaseFromVault('VAULT-01', 1500.0);
      expect(err, isNull);
      expect(savings.bal, initialBal + 1500.0);
      expect(vault.currentAmount, initialVaultAmt - 1500.0);
      expect(savings.tx.first.label, contains('Released from Emergency Buffer'));
    });

    test('Creating a new custom Ipon vault adds to store', () {
      final store = Demo.i;
      final countBefore = store.iponVaults.length;

      store.createVault(
        name: 'Farm Fertilizer Fund',
        targetAmount: 25000.0,
        targetDate: 'Oct 2027',
        category: 'Community',
      );

      expect(store.iponVaults.length, countBefore + 1);
      final newVault = store.iponVaults.last;
      expect(newVault.name, 'Farm Fertilizer Fund');
      expect(newVault.targetAmount, 25000.0);
      expect(newVault.currentAmount, 0.0);
      expect(newVault.isCompleted, isFalse);
    });
  });

  group('UI Integration: Envelopes & Ipon Vaults Rendering', () {
    testWidgets('ScoresPage renders Smart Envelopes card', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: ScoresPage()),
        ),
      );
      await tester.pump();

      expect(find.text('Smart Envelopes'), findsOneWidget);
      expect(find.text('Total Budget'), findsOneWidget);
      expect(find.text('Remaining'), findsOneWidget);
    });

    testWidgets('HomeScreen renders Co-op Ipon Vaults section', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      Demo.i.state = LoadState.ready;

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: HomeScreen()),
        ),
      );
      await tester.pump();

      expect(find.text('Co-op Ipon Vaults'), findsOneWidget);
      expect(find.text('New Goal'), findsOneWidget);
      expect(find.text('Emergency Buffer'), findsOneWidget);
      expect(find.text('Paluwagan & Holidays'), findsOneWidget);
    });
  });
}
