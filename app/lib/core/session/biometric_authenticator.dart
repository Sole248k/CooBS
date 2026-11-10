import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:local_auth/local_auth.dart';

/// Device biometric check (fingerprint / face) used for fingerprint login.
/// Behind an interface so widget tests can swap in a fake.
abstract class BiometricAuthenticator {
  /// True when the device can prompt and the user has a biometric enrolled.
  Future<bool> isAvailable();

  /// Shows the system prompt. False when the user cancels, fails, or the prompt cannot be shown.
  Future<bool> authenticate(String reason);
}

class LocalBiometricAuthenticator implements BiometricAuthenticator {
  final LocalAuthentication _auth;
  LocalBiometricAuthenticator([LocalAuthentication? auth]) : _auth = auth ?? LocalAuthentication();

  @override
  Future<bool> isAvailable() async {
    // Under the test binding the plugin channel never answers; tests inject a fake instead.
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (kIsWeb || isTest) return false;
    try {
      if (!await _auth.isDeviceSupported() || !await _auth.canCheckBiometrics) return false;
      return (await _auth.getAvailableBiometrics()).isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> authenticate(String reason) async {
    try {
      // biometricOnly: the device PIN must not stand in for the fingerprint.
      return await _auth.authenticate(localizedReason: reason, biometricOnly: true, persistAcrossBackgrounding: true);
    } catch (_) {
      // Cancelled, locked out, or no UI available: fall back to the password form.
      return false;
    }
  }
}
