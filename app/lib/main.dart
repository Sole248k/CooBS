import 'package:flutter/material.dart';

import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CooBSApp());
}

/// CooBS Mobile Application Root Widget.
class CooBSApp extends StatelessWidget {
  const CooBSApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CooBS Member Banking',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      initialRoute: AppRouter.initial,
      onGenerateRoute: AppRouter.onGenerateRoute,
    );
  }
}
