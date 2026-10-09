import 'package:flutter/foundation.dart';

/// Runtime configuration for the CooBS Mobile Application.
/// Handles environment toggles, gateway routing, and air-gapped fixture mode.
class AppConfig {
  /// Whether the app operates in offline air-gapped mock mode using local JSON fixtures.
  /// Set to true by default for zero-backend unit tests; overridden to false in main.dart for live gateway runtime.
  static bool useFixtures = const bool.fromEnvironment('USE_FIXTURES', defaultValue: true);

  /// Dynamic runtime override for gateway URL (configurable from app UI or persistent storage).
  static String? _customRuntimeGatewayUrl;

  static String get customRuntimeGatewayUrl => _customRuntimeGatewayUrl ?? '';
  static set customRuntimeGatewayUrl(String value) {
    _customRuntimeGatewayUrl = value.trim().isEmpty ? null : value.trim();
  }

  /// Spring Cloud Gateway base host.
  /// Android Emulator uses 10.0.2.2 to reach host localhost; Web/Desktop uses localhost.
  static String get gatewayBaseUrl {
    if (_customRuntimeGatewayUrl != null && _customRuntimeGatewayUrl!.isNotEmpty) {
      return _customRuntimeGatewayUrl!;
    }
    const customGateway = String.fromEnvironment('GATEWAY_URL');
    if (customGateway.isNotEmpty) return customGateway;
    if (kIsWeb) {
      return 'http://localhost:8080';
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return 'http://10.0.2.2:8080';
      default:
        return 'http://localhost:8080';
    }
  }

  /// Mobile BFF base path routed via Spring Cloud Gateway.
  static String get mobileBffBaseUrl => '$gatewayBaseUrl/api/v1/mobile';

  /// Core Public API base path routed via Spring Cloud Gateway.
  static String get apiBaseUrl => '$gatewayBaseUrl/api/v1';

  /// Default HTTP timeout budget.
  static const Duration requestTimeout = Duration(seconds: 10);

  /// Fast-fail timeout budget for risk evaluation / status queries.
  static const Duration fastTimeout = Duration(milliseconds: 2500);

  /// Default currency code for retail transactions.
  static const String defaultCurrency = 'PHP';
}
