import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:app/features/accounts/demo_store.dart';
import 'package:app/features/scores/deals_catalog.dart';
import 'package:app/features/scores/scores_screen.dart';
import 'package:app/features/accounts/home_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Personalization Feature 1: Geo-Tagged Partner Deals (25 spanning discounts)', () {
    test('Catalog contains exactly 25 partner deals across Makati CBD and Mandaluyong', () {
      expect(kPartnerDeals.length, 25);

      final makatiDeals = kPartnerDeals.where((d) => d.city == 'Makati CBD').toList();
      final mandaluyongDeals = kPartnerDeals.where((d) => d.city == 'Mandaluyong').toList();
      expect(makatiDeals.length, 13);
      expect(mandaluyongDeals.length, 12);

      final cafes = kPartnerDeals.where((d) => d.subcategory == 'Cafe').toList();
      final clothes = kPartnerDeals.where((d) => d.subcategory == 'Clothes').toList();
      expect(cafes.length, 14);
      expect(clothes.length, 11);

      // Verify all have valid promo codes, discounts, and coordinates
      for (final deal in kPartnerDeals) {
        expect(deal.promoCode, startsWith('COOBS'));
        expect(deal.discount, isNotEmpty);
        expect(deal.lat, inInclusiveRange(14.5, 14.65));
        expect(deal.lng, inInclusiveRange(121.0, 121.1));
      }
    });

    test('Haversine distance calculation is accurate between Makati and Mandaluyong', () {
      // Ayala Center (14.5512, 121.0240) to Megamall (14.5847, 121.0573) is roughly 5 km
      final dist = calculateDistanceKm(14.5512, 121.0240, 14.5847, 121.0573);
      expect(dist, inInclusiveRange(4.5, 5.5));
    });

    test('getRankedDeals prioritizes Food/Cafes when user top category is FOOD', () {
      final ranked = getRankedDeals(
        userLat: 14.5547,
        userLng: 121.0244,
        topCategory: 'FOOD',
        cityFilter: 'Makati CBD',
      );

      expect(ranked, isNotEmpty);
      expect(ranked.first.deal.category, 'FOOD');
      expect(ranked.first.isHabitMatch, isTrue);
    });

    test('getRankedDeals prioritizes Shopping/Clothes when user top category is SHOPPING', () {
      final ranked = getRankedDeals(
        userLat: 14.5840,
        userLng: 121.0568,
        topCategory: 'SHOPPING',
        cityFilter: 'Mandaluyong',
      );

      expect(ranked, isNotEmpty);
      expect(ranked.first.deal.category, 'SHOPPING');
      expect(ranked.first.isHabitMatch, isTrue);
    });

    test('getRankedDeals correctly applies subcategory filter', () {
      final cafesOnly = getRankedDeals(
        userLat: 14.5547,
        userLng: 121.0244,
        topCategory: 'FOOD',
        subcategoryFilter: 'Cafe',
      );
      expect(cafesOnly.every((d) => d.deal.subcategory == 'Cafe'), isTrue);

      final clothesOnly = getRankedDeals(
        userLat: 14.5547,
        userLng: 121.0244,
        topCategory: 'FOOD',
        subcategoryFilter: 'Clothes',
      );
      expect(clothesOnly.every((d) => d.deal.subcategory == 'Clothes'), isTrue);
    });
  });

  group('Personalization Feature 3: Custom Account Nicknames', () {
    test('Set and reset account nickname in Demo store', () async {
      final store = Demo.i;
      const accountNo = 'ACC_10000001';

      await store.setNickname(accountNo, 'Daily Allowance');
      expect(store.nicknameFor(accountNo), 'Daily Allowance');

      await store.setNickname(accountNo, 'Emergency Reserve');
      expect(store.nicknameFor(accountNo), 'Emergency Reserve');

      await store.setNickname(accountNo, '');
      expect(store.nicknameFor(accountNo), '');
    });
  });

  group('Personalization Feature 2 & 4: UI integration test', () {
    testWidgets('ScoresPage renders Personalized Health Nudges and Nearby Partner Deals', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      Demo.i.creditScore = 750;
      Demo.i.spendingBreakdown = {'FOOD': 5000.0, 'SHOPPING': 2000.0};

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ScoresPage(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Personalized Health Nudges'), findsOneWidget);
      expect(find.text('Dining & Cafe Affinity'), findsOneWidget);

      await tester.drag(find.byType(ListView), const Offset(0, -1800));
      await tester.pumpAndSettle();
      expect(find.text('Personalized Geo-Deals'), findsOneWidget);
      expect(find.text('Makati CBD'), findsWidgets);
    });
  });
}
