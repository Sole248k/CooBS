import 'package:app/core/theme/app_theme.dart';
import 'package:app/core/theme/theme_controller.dart';
import 'package:app/features/accounts/demo_store.dart';
import 'package:app/features/shop/shop_screen.dart';
import 'package:app/features/shop/shop_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void fillBasket(ShopStore store) {
  for (final product in shopProducts) {
    store.setQuantity(product.id, 1);
  }
}

void main() {
  test('basket quantities, integer totals, removal and stock limits', () {
    final store = ShopStore();
    addTearDown(store.dispose);
    fillBasket(store);
    expect(store.cartCount, 3);
    expect(store.cartTotalCents, 53500);
    store.setQuantity('rice', 2);
    expect(store.cartTotalCents, 85500);
    store.setQuantity('milk', 0);
    expect(store.cartTotalCents, 76000);
    expect(() => store.setQuantity('rice', 11), throwsStateError);
    expect(() => store.setQuantity('rice', -1), throwsStateError);
  });

  test(
    'reserve replay and capture replay move sample funds once, never bank data',
    () {
      final store = ShopStore();
      addTearDown(store.dispose);
      final bankBalances = Demo.i.accts.map((a) => a.bal).toList();
      fillBasket(store);
      final quote = store.review();
      final order = store.placeOrder(quote);
      expect(identical(store.placeOrder(quote), order), isTrue);
      expect(store.orders, hasLength(1));
      expect(store.availableCents, 1196500);
      expect(store.ledgerCents, 1250000);
      expect(() => store.collect(order), throwsStateError);
      store.advance(order);
      store.advance(order);
      store.collect(order);
      store.collect(order);
      expect(store.ledgerCents, 1196500);
      expect(store.availableCents, 1196500);
      expect(() => store.cancel(order), throwsStateError);
      expect(Demo.i.accts.map((a) => a.bal).toList(), bankBalances);
    },
  );

  test('cancellation restores hold once and blocks later collection', () {
    final store = ShopStore();
    addTearDown(store.dispose);
    fillBasket(store);
    final order = store.placeOrder(store.review());
    store.cancel(order);
    store.cancel(order);
    expect(store.availableCents, 1250000);
    expect(store.ledgerCents, 1250000);
    expect(() => store.collect(order), throwsStateError);
    expect(() => store.advance(order), throwsStateError);
  });

  test('preparation and support case block cancellation and payment', () {
    final store = ShopStore();
    addTearDown(store.dispose);
    fillBasket(store);
    final order = store.placeOrder(store.review());
    store.advance(order);
    expect(() => store.cancel(order), throwsStateError);
    store.requestHelp(order);
    final caseId = order.supportCase;
    store.requestHelp(order);
    expect(order.supportCase, caseId);
    expect(() => store.advance(order), throwsStateError);
    expect(() => store.collect(order), throwsStateError);
    expect(store.availableCents, 1196500);
  });

  test('lost reservation and capture replies reconcile the same order', () {
    final store = ShopStore();
    addTearDown(store.dispose);
    fillBasket(store);
    final order = store.placeOrder(store.review(), loseReply: true);
    expect(order.paymentUnknown, isTrue);
    expect(() => store.cancel(order), throwsStateError);
    expect(() => store.advance(order), throwsStateError);
    store.reconcile(order);
    store.advance(order);
    store.advance(order);
    store.collect(order, loseReply: true);
    expect(order.paymentUnknown, isTrue);
    expect(() => store.cancel(order), throwsStateError);
    store.reconcile(order);
    expect(order.status, 'Collected & paid');
    expect(store.availableCents, 1196500);
    expect(store.orders, hasLength(1));
  });

  test(
    'empty, changed, foreign and insufficient baskets cannot create orders',
    () {
      final store = ShopStore(sampleBalanceCents: 10000);
      final other = ShopStore();
      addTearDown(store.dispose);
      addTearDown(other.dispose);
      expect(() => store.review(), throwsStateError);
      fillBasket(store);
      final stale = store.review();
      store.setQuantity('rice', 2);
      expect(() => store.placeOrder(stale), throwsStateError);
      expect(() => store.placeOrder(store.review()), throwsStateError);
      fillBasket(other);
      final foreign = other.review();
      expect(() => store.placeOrder(foreign), throwsStateError);
      final foreignOrder = other.placeOrder(foreign);
      expect(() => store.cancel(foreignOrder), throwsStateError);
      expect(store.orders, isEmpty);
      expect(store.cartCount, 4);
      expect(store.availableCents, 10000);
    },
  );

  test(
    'earlier order snapshot survives later basket edits and multiple holds',
    () {
      final store = ShopStore();
      addTearDown(store.dispose);
      fillBasket(store);
      final order = store.placeOrder(store.review());
      store.setQuantity('rice', 2);
      final second = store.placeOrder(store.review());
      expect(order.totalCents, 53500);
      expect(second.totalCents, 64000);
      expect(store.availableCents, 1132500);
      store.cancel(order);
      expect(store.availableCents, 1186000);
    },
  );

  Future<void> tapText(WidgetTester tester, String text) async {
    final target = find.text(text);
    await tester.scrollUntilVisible(
      target,
      250,
      scrollable: find.byType(Scrollable).last,
    );
    // Found in the list's cache area is not yet on screen.
    await tester.ensureVisible(target);
    await tester.pumpAndSettle();
    await tester.tap(target);
    await tester.pumpAndSettle();
  }

  Future<void> mount(WidgetTester tester, ShopStore store) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(
          body: ListView(children: [PureSilverEntry(store: store)]),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tapText(tester, 'Shop PureSilver');
  }

  testWidgets(
    'phone checkout, preparation, pickup confirmation and home re-entry',
    (tester) async {
      final store = ShopStore();
      fillBasket(store);
      await mount(tester, store);
      await tapText(tester, 'View basket (3) · ₱535.00');
      await tapText(tester, 'Continue to checkout');
      await tapText(tester, 'Reserve ₱535.00 · sample');
      expect(store.orders, hasLength(1));
      await tapText(tester, 'Demo: start preparation');
      await tapText(tester, 'Demo: mark ready for pickup');
      await tapText(tester, 'Demo: verify collection');
      await tapText(tester, 'Confirm sample collection & pay');
      expect(store.orders.single.stage, ShopStage.completed);
      await tapText(tester, 'Back to banking');
      await tapText(tester, 'My sample orders (1)');
      await tapText(tester, store.orders.single.reference);
      expect(find.text('Collected & paid'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      store.dispose();
    },
  );

  testWidgets(
    'product quantity, basket removal and system back remain usable',
    (tester) async {
      final store = ShopStore();
      await mount(tester, store);
      await tapText(tester, 'Premium rice');
      await tester.tap(find.byTooltip('Add one Premium rice'));
      await tester.pumpAndSettle();
      expect(store.cartTotalCents, 32000);
      await tapText(tester, 'View basket (1)');
      await tapText(tester, 'Remove Premium rice');
      expect(store.cartCount, 0);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Continue to checkout'),
            )
            .onPressed,
        isNull,
      );
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Product details'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      store.dispose();
    },
  );

  testWidgets(
    'dark theme and large text keep checkout scrollable without overflow',
    (tester) async {
      final store = ShopStore();
      fillBasket(store);
      await ThemeController.i.setDark(true);
      addTearDown(() => ThemeController.i.setDark(false));
      tester.view.physicalSize = const Size(360, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.darkTheme,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.5)),
            child: child!,
          ),
          home: PureSilverScreen(store: store),
        ),
      );
      await tapText(tester, 'View basket (3) · ₱535.00');
      await tapText(tester, 'Continue to checkout');
      await tapText(tester, 'Demo: lose reservation reply');
      await tapText(tester, 'Check sample payment status');
      expect(store.orders.single.paymentUnknown, isFalse);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      store.dispose();
    },
  );
}
