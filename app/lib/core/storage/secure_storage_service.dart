import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure token & session storage service backed by Android KeyStore.
/// Automatically falls back to an in-memory store in web development or tests.
class SecureStorageService {
  static const String _keyAccessToken = 'coobs_access_token';
  static const String _keyRefreshToken = 'coobs_refresh_token';
  static const String _keyUserId = 'coobs_user_id';
  static const String _keyUsername = 'coobs_username';
  static const String _keyGatewayUrl = 'coobs_gateway_url';

  final FlutterSecureStorage _storage;
  final Map<String, String> _memoryFallback = {};

  SecureStorageService({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  Future<void> saveTokens({
    required String accessToken,
    String? refreshToken,
    int? userId,
    String? username,
  }) async {
    await write(_keyAccessToken, accessToken);
    if (refreshToken != null) await write(_keyRefreshToken, refreshToken);
    if (userId != null) await write(_keyUserId, userId.toString());
    if (username != null) await write(_keyUsername, username);
  }

  Future<void> updateTokens({
    required String accessToken,
    String? refreshToken,
  }) async {
    await write(_keyAccessToken, accessToken);
    if (refreshToken != null && refreshToken.isNotEmpty) {
      await write(_keyRefreshToken, refreshToken);
    }
  }

  Future<String?> getAccessToken() => read(_keyAccessToken);
  Future<String?> getRefreshToken() => read(_keyRefreshToken);
  Future<int?> getUserId() async {
    final str = await read(_keyUserId);
    return str != null ? int.tryParse(str) : null;
  }
  Future<String?> getUsername() => read(_keyUsername);

  Future<void> saveGatewayUrl(String url) => write(_keyGatewayUrl, url);
  Future<String?> getGatewayUrl() => read(_keyGatewayUrl);

  Future<bool> hasValidSession() async {
    final token = await getAccessToken();
    return token != null && token.isNotEmpty;
  }

  Future<void> clearSession() async {
    if (kIsWeb) {
      _memoryFallback.remove(_keyAccessToken);
      _memoryFallback.remove(_keyRefreshToken);
      _memoryFallback.remove(_keyUserId);
      _memoryFallback.remove(_keyUsername);
      return;
    }
    try {
      await _storage.delete(key: _keyAccessToken);
      await _storage.delete(key: _keyRefreshToken);
      await _storage.delete(key: _keyUserId);
      await _storage.delete(key: _keyUsername);
    } catch (_) {
      _memoryFallback.remove(_keyAccessToken);
      _memoryFallback.remove(_keyRefreshToken);
      _memoryFallback.remove(_keyUserId);
      _memoryFallback.remove(_keyUsername);
    }
  }

  @visibleForTesting
  Future<void> write(String key, String value) async {
    _memoryFallback[key] = value;
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (!kIsWeb && !isTest) {
      try {
        await _storage.write(key: key, value: value);
      } catch (_) {
        // Fallback to memory if KeyStore is unavailable (e.g. headless unit tests)
      }
    }
  }

  @visibleForTesting
  Future<String?> read(String key) async {
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (kIsWeb || isTest || _memoryFallback.containsKey(key)) {
      return _memoryFallback[key];
    }
    try {
      return await _storage.read(key: key) ?? _memoryFallback[key];
    } catch (_) {
      return _memoryFallback[key];
    }
  }
}
