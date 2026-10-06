import 'package:flutter/material.dart';

import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CooBSApp());
}

/// CooBS Mobile Application Root Widget.
class CooBSApp extends StatelessWidget {
  final String? initialRoute;
  const CooBSApp({super.key, this.initialRoute});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CooBS Member Banking',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      initialRoute: initialRoute ?? AppRouter.initial,
      onGenerateRoute: AppRouter.onGenerateRoute,
    );
  }
}
