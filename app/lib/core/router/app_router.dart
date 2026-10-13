import 'package:flutter/material.dart';

import '../../features/accounts/home_screen.dart';
import '../../features/auth/auth_screens.dart';
import '../../features/auth/splash_screen.dart';
import '../../features/resilience/service_unavailable_view.dart';
import '../../shell/app_shell.dart';

/// Declarative Named Routing & Route Registry.
class AppRouter {
  static final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();
  static const String initial = '/';
  static const String login = '/login';
  static const String biometricLogin = '/biometric-login';
  static const String mobile = '/mobile';
  static const String dashboard = '/dashboard';
  static const String remittance = '/remittance';
  static const String tracking = '/tracking';
  static const String serviceUnavailable = '/service-unavailable';
  static const String teller = '/teller';

  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case initial:
        return MaterialPageRoute(
          builder: (_) => const SplashScreen(),
          settings: settings,
        );
      case login:
        return MaterialPageRoute(
          builder: (_) => const LoginScreen(),
          settings: settings,
        );
      case biometricLogin:
        return MaterialPageRoute(
          builder: (_) => const BiometricLoginScreen(),
          settings: settings,
        );
      case mobile:
        return MaterialPageRoute(
          builder: (_) => const MobileShell(),
          settings: settings,
        );
      case teller:
      case dashboard:
        return MaterialPageRoute(
          builder: (_) => const AppShell(initialIndex: 0),
          settings: settings,
        );
      case remittance:
        return MaterialPageRoute(
          builder: (_) => const AppShell(initialIndex: 1),
          settings: settings,
        );
      case tracking:
        final trackingRef = settings.arguments as String?;
        return MaterialPageRoute(
          builder: (_) => AppShell(initialIndex: 2, trackingReference: trackingRef),
          settings: settings,
        );
      case serviceUnavailable:
        final retryCooldown = settings.arguments as Duration?;
        return MaterialPageRoute(
          builder: (_) => ServiceUnavailableView(cooldown: retryCooldown),
          settings: settings,
        );
      default:
        return MaterialPageRoute(
          builder: (_) => const AppShell(initialIndex: 0),
          settings: settings,
        );
    }
  }
}
