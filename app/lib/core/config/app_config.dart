/// Runtime configuration for the CooBS Mobile Application.
/// Handles environment toggles, gateway routing, and air-gapped fixture mode.
class AppConfig {
  /// Whether the app operates in offline air-gapped mock mode using local JSON fixtures.
  /// Set to true by default for zero-backend unit tests; overridden to false in main.dart for live gateway runtime.
  static bool useFixtures = const bool.fromEnvironment('USE_FIXTURES', defaultValue: true);

  /// Primary Production Gateway on Azure.
  static const String defaultAzureGatewayUrl = 'https://coobs.indonesiacentral.cloudapp.azure.com';

  /// Dynamic runtime override for gateway URL (configurable from app UI or persistent storage).
  static String? _customRuntimeGatewayUrl;

  static String get customRuntimeGatewayUrl => _customRuntimeGatewayUrl ?? '';
  static set customRuntimeGatewayUrl(String value) {
    _customRuntimeGatewayUrl = value.trim().isEmpty ? null : _normalize(value);
  }

  /// Normalizes URLs by trimming trailing slashes and correcting common typos.
  static String _normalize(String url) {
    var trimmed = url.trim();
    if (trimmed.endsWith('/')) {
      trimmed = trimmed.substring(0, trimmed.length - 1);
    }
    if (trimmed.contains('.coudapp.azure.com')) {
      trimmed = trimmed.replaceAll('.coudapp.azure.com', '.cloudapp.azure.com');
    }
    return trimmed;
  }

  /// Spring Cloud Gateway base host.
  /// Defaults to the deployed Azure production gateway (https://coobs.indonesiacentral.cloudapp.azure.com).
  /// Can be overridden at runtime via settings or at build time via --dart-define=GATEWAY_URL=...
  static String get gatewayBaseUrl {
    if (_customRuntimeGatewayUrl != null && _customRuntimeGatewayUrl!.isNotEmpty) {
      return _normalize(_customRuntimeGatewayUrl!);
    }
    const customGateway = String.fromEnvironment('GATEWAY_URL');
    if (customGateway.isNotEmpty) return _normalize(customGateway);

    return defaultAzureGatewayUrl;
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
