import 'package:flutter/material.dart';

import 'core/theme/app_theme.dart';
import 'features/auth/auth_screens.dart';

/// Demo entry point for the new mobile screens (mock data, no backend).
/// Run with:  flutter run -t lib/main_screens.dart
void main() => runApp(MaterialApp(
      title: 'CooBS Mobile Screens',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: const LoginScreen(),
    ));
