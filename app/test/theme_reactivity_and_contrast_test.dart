import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/core/theme/theme_controller.dart';
import 'package:app/features/accounts/home_screen.dart';
import 'package:app/features/accounts/ui.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    await ThemeController.i.setDark(false);
  });

  tearDown(() async {
    await ThemeController.i.setDark(false);
  });

  testWidgets('Theme toggle immediately updates MobileShell and child tabs without refresh', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MobileShell(),
      ),
    );
    for (var i = 0; i < 5; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    // In light mode initially
    expect(ThemeController.i.dark, isFalse);
    expect(kDark, isFalse);

    // Verify initial Home tab is visible
    expect(find.text('Recent transactions'), findsOneWidget);

    // Toggle dark mode
    await ThemeController.i.setDark(true);
    await tester.pump();

    // Verify theme controller and top-level tokens updated
    expect(ThemeController.i.dark, isTrue);
    expect(kDark, isTrue);

    // Verify recent transactions text is rendered in dark mode ink color
    final txText = tester.widget<Text>(find.text('Recent transactions'));
    expect(txText.style?.color, equals(kInk));

    // Swap to Profile tab (tab 4)
    await tester.tap(find.byTooltip('Profile'));
    await tester.pump();

    // Verify profile settings is rendered and switch reflects dark mode
    expect(find.text('Profile & settings'), findsOneWidget);
    expect(find.text('Dark mode'), findsOneWidget);
    expect(find.text('On'), findsOneWidget);

    // Toggle back to light mode via ThemeController
    await ThemeController.i.setDark(false);
    await tester.pump();

    expect(ThemeController.i.dark, isFalse);
    expect(kDark, isFalse);
    expect(find.text('Off'), findsOneWidget);

    // Swap back to Home tab without manual refresh
    await tester.tap(find.byTooltip('Home'));
    await tester.pump();

    // Verify Home tab has updated light mode ink color immediately
    final txTextLight = tester.widget<Text>(find.text('Recent transactions'));
    expect(txTextLight.style?.color, equals(kInk));
  });

  testWidgets('Bottom navigation tabs switch active state immediately', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MobileShell(),
      ),
    );
    for (var i = 0; i < 5; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    // Tap Scores and habits tab
    await tester.tap(find.byTooltip('Scores and habits'));
    await tester.pump();

    expect(find.text('Scores and Habits'), findsOneWidget);
    expect(find.text('Your Credit Score'), findsOneWidget);

    // Tap Co-op Ipon Vaults tab
    await tester.tap(find.byTooltip('Co-op Ipon Vaults'));
    await tester.pump();

    expect(find.text('Co-op Ipon Vaults'), findsWidgets);

    // Tap Profile tab and verify Beneficiaries is under Profile & settings
    await tester.tap(find.byTooltip('Profile'));
    await tester.pump();

    expect(find.text('Beneficiaries'), findsOneWidget);

    // Tap Home tab
    await tester.tap(find.byTooltip('Home'));
    await tester.pump();

    expect(find.text('Recent transactions'), findsOneWidget);
  });
}
