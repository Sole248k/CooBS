import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/main.dart';

void main() {
  testWidgets('CooBSApp renders banking shell with navigation', (WidgetTester tester) async {
    await tester.pumpWidget(const CooBSApp());
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
