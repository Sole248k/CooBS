import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// App-wide light/dark preference for the mobile app.
/// Persisted on-device so it survives logout and app restarts.
class ThemeController extends ChangeNotifier {
  static final ThemeController i = ThemeController._();
  ThemeController._();

  static const String _key = 'coobs_dark_mode';
  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  bool dark = false;
  ThemeMode get mode => dark ? ThemeMode.dark : ThemeMode.light;

  Future<void> load() async {
    try {
      dark = await _storage.read(key: _key) == 'true';
    } catch (_) {
      dark = false;
    }
    notifyListeners();
  }

  Future<void> setDark(bool v) async {
    dark = v;
    notifyListeners();
    try {
      await _storage.write(key: _key, value: '$v');
    } catch (_) {
      // Storage unavailable (e.g. tests): keep the in-memory value for this run.
    }
  }
}
