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
  // Fingerprint login lives outside the session keys so it survives logout and app restarts.
  static const String _keyBioUsername = 'coobs_bio_username';
  static const String _keyBioRefreshToken = 'coobs_bio_refresh_token';
  static const String _keyBioOffered = 'coobs_bio_offered';

  final FlutterSecureStorage _storage;
  final Map<String, String> _memoryFallback = {};

  SecureStorageService({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(
                encryptedSharedPreferences: true,
                resetOnError: true,
              ),
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
    await _syncBiometricToken(refreshToken, username: username);
  }

  Future<void> updateTokens({
    required String accessToken,
    String? refreshToken,
  }) async {
    await write(_keyAccessToken, accessToken);
    if (refreshToken != null && refreshToken.isNotEmpty) {
      await write(_keyRefreshToken, refreshToken);
    }
    await _syncBiometricToken(refreshToken);
  }

  /// Keeps the fingerprint-login copy on the newest refresh token, since the server
  /// rotates it on every refresh and the previous one stops working.
  Future<void> _syncBiometricToken(String? refreshToken, {String? username}) async {
    final bioUser = await getBiometricUsername();
    if (bioUser == null) return;
    // Another member signed in with a password on this phone: drop the previous member's fingerprint login.
    if (username != null && username != bioUser) return disableBiometricLogin();
    if (refreshToken != null && refreshToken.isNotEmpty) await write(_keyBioRefreshToken, refreshToken);
  }

  Future<void> enableBiometricLogin({required String username, required String refreshToken}) async {
    await write(_keyBioUsername, username);
    await write(_keyBioRefreshToken, refreshToken);
  }

  Future<void> disableBiometricLogin() async {
    await delete(_keyBioUsername);
    await delete(_keyBioRefreshToken);
  }

  /// The member fingerprint login is enabled for, or null when it is off.
  Future<String?> getBiometricUsername() => read(_keyBioUsername);
  Future<String?> getBiometricRefreshToken() => read(_keyBioRefreshToken);

  /// The enable prompt is shown once per member; a "Not now" is remembered.
  Future<bool> wasBiometricOffered(String username) async => await read(_keyBioOffered) == username;
  Future<void> markBiometricOffered(String username) => write(_keyBioOffered, username);

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
    // Delete only session keys so device preferences (e.g. dark mode) survive logout.
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

  Future<void> delete(String key) async {
    _memoryFallback.remove(key);
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (kIsWeb || isTest) return;
    try {
      await _storage.delete(key: key);
    } catch (_) {}
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
