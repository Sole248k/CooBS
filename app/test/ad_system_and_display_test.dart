import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/core/theme/theme_controller.dart';
import 'package:app/features/ads/ad_liquid_crystal_dialog.dart';
import 'package:app/features/ads/ad_models.dart';
import 'package:app/features/ads/ad_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    AdService.resetSession();
    AdService.enableAutoShow = false;
  });

  tearDown(() {
    AdService.resetSession();
    AdService.enableAutoShow = false;
  });

  group('Ad Probability & Sampling Engine', () {
    test('Strict probability boundaries match required specifications', () {
      // 1 in 2,000 -> roll 0
      final roll0 = AdService.pickAd(forceRoll: 0);
      expect(roll0.id, equals(AdItem.umamusume.id));
      expect(roll0.isEasterEgg, isTrue);
      expect(roll0.oddsDescription, contains('1 in 2,000'));

      // 1 in 1,000 -> roll 1 or 2 (2 out of 2,000 = 1/1,000)
      final roll1 = AdService.pickAd(forceRoll: 1);
      final roll2 = AdService.pickAd(forceRoll: 2);
      expect(roll1.id, equals(AdItem.duo.id));
      expect(roll2.id, equals(AdItem.duo.id));
      expect(roll1.oddsDescription, contains('1 in 1,000'));

      // Most frequent remainder -> rolls 3 through 1999
      final roll3 = AdService.pickAd(forceRoll: 3);
      final roll1999 = AdService.pickAd(forceRoll: 1999);
      expect(roll3.id, equals(AdItem.solo.id));
      expect(roll1999.id, equals(AdItem.solo.id));
      expect(roll3.isEasterEgg, isFalse);
    });

    test('Empirical distribution over 20,000 rolls conforms to probability targets', () {
      final seededRng = Random(42);
      int countUmamusume = 0;
      int countDuo = 0;
      int countSolo = 0;
      const totalRolls = 20000;

      for (var i = 0; i < totalRolls; i++) {
        final ad = AdService.pickAd(random: seededRng);
        if (ad.id == AdItem.umamusume.id) {
          countUmamusume++;
        } else if (ad.id == AdItem.duo.id) {
          countDuo++;
        } else if (ad.id == AdItem.solo.id) {
          countSolo++;
        }
      }

      // Expected: Umamusume ~ 10 (1/2000 * 20000)
      // Expected: Duo ~ 20 (2/2000 * 20000)
      // Expected: Solo ~ 19970
      expect(countUmamusume, inInclusiveRange(3, 22));
      expect(countDuo, inInclusiveRange(10, 35));
      expect(countSolo, greaterThan(19900));
      expect(countSolo, greaterThan(countDuo * 100));
    });

    test('Session lifecycle marks shown and resets properly', () {
      expect(AdService.hasShownThisSession, isFalse);
      AdService.markShown();
      expect(AdService.hasShownThisSession, isTrue);
      AdService.resetSession();
      expect(AdService.hasShownThisSession, isFalse);
    });
  });

  group('Liquid Crystal Display (LCD) UI Widget', () {
    testWidgets('Renders Liquid Crystal Display dialog with Solo ad banner', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => AdLiquidCrystalDialog.show(context, AdItem.solo),
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Show Dialog'));
      await tester.pumpAndSettle();

      // Verify LCD status bar, badges and typography
      expect(find.text('COOBS EXCLUSIVE'), findsOneWidget);
      expect(find.text(AdItem.solo.title), findsOneWidget);
      expect(find.text(AdItem.solo.oddsDescription), findsOneWidget);
      expect(find.text('Continue to CooBS'), findsOneWidget);
      expect(find.text('Dismiss'), findsOneWidget);
      expect(find.byIcon(Icons.close_rounded), findsOneWidget);

      // Dismiss dialog
      await tester.tap(find.text('Dismiss'));
      await tester.pumpAndSettle();

      expect(find.text('COOBS EXCLUSIVE'), findsNothing);
    });

    testWidgets('Renders Ultra Rare Umamusume Easter Egg with golden LCD styling', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => AdLiquidCrystalDialog.show(context, AdItem.umamusume),
                  child: const Text('Show Easter Egg'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Show Easter Egg'));
      await tester.pumpAndSettle();

      expect(find.text(AdItem.umamusume.badgeText), findsOneWidget);
      expect(find.text(AdItem.umamusume.title), findsOneWidget);
      expect(find.text(AdItem.umamusume.oddsDescription), findsOneWidget);
      expect(find.text('View Campaign'), findsOneWidget);

      // Close via X button
      await tester.tap(find.byIcon(Icons.close_rounded));
      await tester.pumpAndSettle();

      expect(find.text(AdItem.umamusume.title), findsNothing);
    });

    testWidgets('Renders CooBS Duo (1/1000 Lucky Draw) ad banner cleanly', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => AdLiquidCrystalDialog.show(context, AdItem.duo),
                  child: const Text('Show Duo'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Show Duo'));
      await tester.pumpAndSettle();

      expect(find.text(AdItem.duo.badgeText), findsOneWidget);
      expect(find.text(AdItem.duo.title), findsOneWidget);
      expect(find.text(AdItem.duo.oddsDescription), findsOneWidget);
    });

    testWidgets('Liquid Crystal Display dynamically follows light and dark theme mode', (tester) async {
      await ThemeController.i.setDark(false);

      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => AdLiquidCrystalDialog.show(context, AdItem.solo),
                  child: const Text('Show Dialog'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Show Dialog'));
      await tester.pumpAndSettle();

      // In light mode, verify title text uses dark ink color (0xFF000107)
      final lightTitle = tester.widget<Text>(find.text(AdItem.solo.title));
      expect(lightTitle.style?.color, equals(const Color(0xFF000107)));

      // Toggle to dark mode
      await ThemeController.i.setDark(true);
      await tester.pump();

      // In dark mode, verify title text updates to pure white
      final darkTitle = tester.widget<Text>(find.text(AdItem.solo.title));
      expect(darkTitle.style?.color, equals(Colors.white));

      // Revert theme
      await ThemeController.i.setDark(false);
      await tester.pump();
      final revertedTitle = tester.widget<Text>(find.text(AdItem.solo.title));
      expect(revertedTitle.style?.color, equals(const Color(0xFF000107)));
    });
  });
}
