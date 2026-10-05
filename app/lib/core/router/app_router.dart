import 'package:flutter/material.dart';

import '../../features/resilience/service_unavailable_view.dart';
import '../../shell/app_shell.dart';

/// Declarative Named Routing & Route Registry.
class AppRouter {
  static const String initial = '/';
  static const String login = '/login';
  static const String dashboard = '/dashboard';
  static const String remittance = '/remittance';
  static const String tracking = '/tracking';
  static const String serviceUnavailable = '/service-unavailable';

  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case initial:
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
