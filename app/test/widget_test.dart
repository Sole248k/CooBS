import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/main.dart';

void main() {
  testWidgets('CooBSApp renders customer mobile login screen by default', (WidgetTester tester) async {
    await tester.pumpWidget(const CooBSApp());
    await tester.pumpAndSettle();

    // Verify customer mobile login UI
    expect(find.text('Welcome back'), findsOneWidget);
    expect(find.text('Log in to see your accounts.'), findsOneWidget);
    expect(find.text('Log in'), findsOneWidget);
    expect(find.text('Create account'), findsNothing);
  });

  testWidgets('CooBSApp renders teller shell when requested', (WidgetTester tester) async {
    await tester.pumpWidget(const CooBSApp(initialRoute: '/teller'));
    await tester.pumpAndSettle();

    // Verify app bar title
    expect(find.text('CooBS Banking'), findsOneWidget);

    // Verify navigation items exist
    expect(find.text('Dashboard'), findsWidgets);
    expect(find.text('Transfer'), findsWidgets);
    expect(find.text('Timeline'), findsWidgets);
    expect(find.byIcon(Icons.account_balance_rounded), findsOneWidget);
  });
}
