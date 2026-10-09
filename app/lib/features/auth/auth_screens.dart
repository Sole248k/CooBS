import 'package:flutter/material.dart';

import '../../core/config/app_config.dart';
import '../../core/models/auth_models.dart';
import '../../core/network/api_client.dart';
import '../../core/network/api_exceptions.dart';
import '../../core/session/biometric_authenticator.dart';
import '../../core/storage/secure_storage_service.dart';
import '../accounts/demo_store.dart';
import '../accounts/home_screen.dart';
import '../accounts/ui.dart';

/// Go to the main app (clears the login/register stack).
void toApp(BuildContext c, [String? msg]) {
  final m = ScaffoldMessenger.of(c);
  Navigator.pushAndRemoveUntil(c, MaterialPageRoute(builder: (_) => const MobileShell()), (_) => false);
  if (msg != null) m.showSnackBar(SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating));
}

class LoginScreen extends StatefulWidget {
  /// Injected in tests; the screen builds its own otherwise.
  final ApiClient? apiClient;
  final SecureStorageService? storage;
  final BiometricAuthenticator? biometrics;
  const LoginScreen({super.key, this.apiClient, this.storage, this.biometrics});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final u = TextEditingController(), p = TextEditingController();
  late final ApiClient _apiClient = widget.apiClient ?? ApiClient();
  late final SecureStorageService _storage = widget.storage ?? SecureStorageService();
  late final BiometricAuthenticator _biometrics = widget.biometrics ?? LocalBiometricAuthenticator();
  bool _isLoading = false;
  int _failedAttempts = 0;
  DateTime? _lockoutUntil;

  /// Member with fingerprint login turned on for this phone, when the sensor is usable.
  String? _bioUser;

  @override
  void initState() {
    super.initState();
    _loadBiometricLogin();
  }

  Future<void> _loadBiometricLogin() async {
    final bioUser = await _storage.getBiometricUsername();
    if (bioUser == null || !await _biometrics.isAvailable() || !mounted) return;
    setState(() {
      _bioUser = bioUser;
      u.text = bioUser;
    });
    // Prompt straight away; the password form stays on screen as the fallback.
    await _handleBiometricLogin();
  }

  /// Fingerprint, then exchange the stored refresh token for a fresh session.
  Future<void> _handleBiometricLogin() async {
    final bioUser = _bioUser;
    if (_isLoading || bioUser == null) return;
    setState(() => _isLoading = true);
    try {
      if (!await _biometrics.authenticate('Log in to CooBS as $bioUser')) return;
      final refreshToken = await _storage.getBiometricRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) return await _expireBiometricLogin();

      final response = await _apiClient.post('/auth/token/refresh', body: {'refreshToken': refreshToken});
      final token = TokenResponse.fromJson(response);
      if (token.accessToken.isEmpty) throw UnauthorizedException('Invalid refresh response');
      await _storage.saveTokens(
        accessToken: token.accessToken,
        refreshToken: token.refreshToken,
        userId: token.userId > 0 ? token.userId : 1,
        username: bioUser,
      );
      await Demo.i.load();
      if (mounted) toApp(context, 'Welcome back, $bioUser');
    } on ApiException catch (e) {
      final unreachable = e is NetworkTimeoutException || e is ConnectionException || (e.statusCode ?? 0) >= 500;
      if (unreachable) {
        if (mounted) snack(context, 'Could not reach CooBS. Check your connection and try again.');
      } else {
        // Refresh token expired (7 days) or revoked: only the password can start a new session.
        await _expireBiometricLogin();
      }
    } catch (_) {
      if (mounted) snack(context, 'Fingerprint login failed. Please log in with your password.');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _expireBiometricLogin() async {
    await _storage.disableBiometricLogin();
    if (!mounted) return;
    setState(() => _bioUser = null);
    snack(context, 'Fingerprint login has expired. Please log in with your password.');
  }

  /// Asked once per member after a password login; "Not now" is remembered.
  Future<void> _offerBiometricLogin(String username, String refreshToken) async {
    if (refreshToken.isEmpty || _bioUser == username) return;
    if (await _storage.wasBiometricOffered(username) || !await _biometrics.isAvailable() || !mounted) return;
    await _storage.markBiometricOffered(username);
    if (!mounted) return;
    final enable = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.fingerprint, size: 36),
        title: const Text('Enable fingerprint login?'),
        content: const Text('Next time, log in with your fingerprint instead of your password. You can turn this off in Profile.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Not now')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Enable')),
        ],
      ),
    );
    if (enable == true) await _storage.enableBiometricLogin(username: username, refreshToken: refreshToken);
  }

  Future<void> _handleLogin() async {
    // Re-tap guard: a second tap can arrive before the rebuild swaps in the spinner.
    if (_isLoading) return;
    if (_lockoutUntil != null && DateTime.now().isBefore(_lockoutUntil!)) {
      final remaining = _lockoutUntil!.difference(DateTime.now()).inSeconds;
      snack(context, 'Security lock active: Please wait $remaining seconds before trying again.');
      return;
    }

    final username = u.text.trim();
    final password = p.text;
    if (username.isEmpty || password.isEmpty) {
      snack(context, 'Enter your username and password');
      return;
    }

    setState(() => _isLoading = true);
    try {
      final response = await _apiClient.post(
        '/auth/customers/login',
        body: {'username': username, 'password': password},
      );
      final token = TokenResponse.fromJson(response);
      if (token.accessToken.isEmpty) {
        throw UnauthorizedException('Invalid login credentials received');
      }
      _failedAttempts = 0;
      _lockoutUntil = null;
      await _storage.saveTokens(
        accessToken: token.accessToken,
        refreshToken: token.refreshToken,
        userId: token.userId > 0 ? token.userId : 1,
        username: username,
      );
      await _offerBiometricLogin(username, token.refreshToken);
      // Immediately load live Customer 360 data from Docker for this newly authenticated user
      await Demo.i.load();
      if (mounted) {
        toApp(context, 'Welcome back, $username');
      }
    } catch (e) {
      _failedAttempts++;
      if (_failedAttempts >= 5) {
        _lockoutUntil = DateTime.now().add(const Duration(seconds: 30));
        _failedAttempts = 0;
        if (mounted) {
          snack(context, 'Account security shield: 5 failed attempts. Please wait 30 seconds.');
        }
      } else if (mounted) {
        final message = e is ApiException ? e.message : 'Authentication failed. Please verify your credentials.';
        snack(context, message);
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showServerSettings() {
    final c = TextEditingController(text: AppConfig.gatewayBaseUrl);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + MediaQuery.of(ctx).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Backend Gateway URL', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Text('Configure the Spring Cloud Gateway address running in Docker.', style: TextStyle(color: kMut, fontSize: 13)),
            const SizedBox(height: 14),
            tf(c, 'Gateway Base URL (e.g. http://192.168.254.126:8080)'),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => c.text = 'http://10.0.2.2:8080',
                    child: const Text('Emulator (10.0.2.2)'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => c.text = 'http://localhost:8080',
                    child: const Text('Localhost:8080'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            bigBtn('Save & Apply', () async {
              final newUrl = c.text.trim();
              if (newUrl.isNotEmpty) {
                AppConfig.customRuntimeGatewayUrl = newUrl;
                await _storage.saveGatewayUrl(newUrl);
                if (ctx.mounted) Navigator.pop(ctx);
                if (mounted) snack(context, 'Gateway updated to $newUrl');
              }
            }),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: ListView(padding: const EdgeInsets.fromLTRB(22, 30, 22, 30), children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    logoBox(),
                    IconButton(
                      tooltip: 'Server Connection',
                      icon: Icon(Icons.dns_outlined, color: kNavy),
                      onPressed: _showServerSettings,
                    ),
                  ],
                ),
                const SizedBox(height: 30),
                Text(_bioUser == null ? 'Welcome back' : 'Welcome back, $_bioUser', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
                Padding(
                    padding: EdgeInsets.only(top: 4, bottom: 20),
                    child: Text(_bioUser == null ? 'Log in to see your accounts.' : 'Use your fingerprint or password to log in.', style: TextStyle(color: kMut))),
                tf(u, 'Username'),
                tf(p, 'Password', obscure: true),
                const SizedBox(height: 12),
                _isLoading
                    ? const Center(child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator()))
                    : bigBtn('Log in', _handleLogin),
                if (_bioUser != null && !_isLoading)
                  Padding(
                    padding: const EdgeInsets.only(top: 10),
                    child: OutlinedButton.icon(
                      onPressed: _handleBiometricLogin,
                      icon: const Icon(Icons.fingerprint),
                      label: const Text('Log in with fingerprint'),
                    ),
                  ),
              ]),
            ),
          ),
        ),
      );
}
