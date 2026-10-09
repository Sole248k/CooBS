import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + MediaQuery.of(ctx).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Backend Gateway URL', style: TextStyle(fontFamily: 'Poppins', fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            const Text('Configure the Spring Cloud Gateway address running in Docker.', style: TextStyle(fontFamily: 'Poppins', color: Color(0xFF64748B), fontSize: 13)),
            const SizedBox(height: 14),
            tf(c, 'Gateway Base URL (e.g. http://192.168.254.126:8080)'),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => c.text = 'http://10.0.2.2:8080',
                    child: const Text('Emulator (10.0.2.2)', style: TextStyle(fontFamily: 'Poppins')),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => c.text = 'http://localhost:8080',
                    child: const Text('Localhost:8080', style: TextStyle(fontFamily: 'Poppins')),
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

  bool _obscurePassword = true;

  Widget _buildField({
    required TextEditingController controller,
    required String label,
    required IconData prefixIcon,
    bool obscure = false,
    Widget? suffixIcon,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        key: ValueKey(label),
        controller: controller,
        obscureText: obscure,
        style: const TextStyle(
          fontFamily: 'Poppins',
          color: Color(0xFF000107),
          fontSize: 15,
          fontWeight: FontWeight.w500,
        ),
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(
            fontFamily: 'Poppins',
            color: Color(0xFF64748B),
            fontSize: 14,
          ),
          floatingLabelStyle: const TextStyle(
            fontFamily: 'Poppins',
            color: Color(0xFF0091AE),
            fontSize: 13,
            fontWeight: FontWeight.w600,
          ),
          prefixIcon: Icon(prefixIcon, color: const Color(0xFF0091AE), size: 20),
          suffixIcon: suffixIcon,
          filled: true,
          fillColor: Colors.white.withAlpha(210),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.2),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.2),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Color(0xFF0091AE), width: 1.8),
          ),
        ),
      ),
    );
  }

  Widget _buildPrimaryButton(String text, VoidCallback onPressed) {
    return Container(
      height: 48,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          colors: [
            Color(0xFF0091AE), // Pacific Cyan
            Color(0xFF00A2C2), // Bright Cyan
          ],
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0091AE).withAlpha(80),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        ),
        child: Text(
          text,
          style: const TextStyle(
            fontFamily: 'Poppins',
            color: Colors.white,
            fontSize: 16,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.3,
          ),
        ),
      ),
    );
  }

  Widget _buildGlassBiometricButton() {
    return OutlinedButton.icon(
      onPressed: _handleBiometricLogin,
      style: OutlinedButton.styleFrom(
        backgroundColor: const Color(0xFF0091AE).withAlpha(16),
        side: const BorderSide(color: Color(0xFF0091AE), width: 1.2),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        padding: const EdgeInsets.symmetric(vertical: 12),
      ),
      icon: const Icon(Icons.fingerprint, color: Color(0xFF0091AE), size: 22),
      label: const Text(
        'Log in with fingerprint',
        style: TextStyle(
          fontFamily: 'Poppins',
          color: Color(0xFF0091AE),
          fontWeight: FontWeight.w600,
          fontSize: 14,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.dark.copyWith(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.dark,
        systemNavigationBarColor: const Color(0xFFF8FAFC),
      ),
      child: Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        body: Stack(
          fit: StackFit.expand,
          children: [
            // Soft ambient light gradients for frosted glass refraction (Light Pearl & Cyan)
            Positioned(
              top: -110,
              right: -70,
              child: Container(
                width: 340,
                height: 340,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: [
                      const Color(0xFF0091AE).withAlpha(35),
                      const Color(0xFFE0F4F8).withAlpha(25),
                      Colors.transparent,
                    ],
                  ),
                ),
              ),
            ),
            Positioned(
              bottom: -90,
              left: -70,
              child: Container(
                width: 320,
                height: 320,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: [
                      const Color(0xFFBAE6FD).withAlpha(45),
                      const Color(0xFFF1F5F9).withAlpha(20),
                      Colors.transparent,
                    ],
                  ),
                ),
              ),
            ),

            // Foreground Content
            SafeArea(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 440),
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // 1. Top Bar: Seamless Logo & Frosted Glass Server settings button
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            logoBox(height: 36, forceDark: false),
                            ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: BackdropFilter(
                                filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                                child: Container(
                                  decoration: BoxDecoration(
                                    color: Colors.white.withAlpha(210),
                                    borderRadius: BorderRadius.circular(12),
                                    border: Border.all(color: const Color(0xFFE2E8F0), width: 1),
                                    boxShadow: [
                                      BoxShadow(
                                        color: Colors.black.withAlpha(8),
                                        blurRadius: 8,
                                        offset: const Offset(0, 2),
                                      ),
                                    ],
                                  ),
                                  child: IconButton(
                                    tooltip: 'Server Connection',
                                    icon: const Icon(Icons.dns_outlined, color: Color(0xFF000107), size: 20),
                                    onPressed: _showServerSettings,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 20),

                        // 2. Welcome Title & Subtitle (Poppins - refined, not overly bold)
                        Text(
                          _bioUser == null ? 'Welcome back' : 'Welcome back, $_bioUser',
                          style: const TextStyle(
                            fontFamily: 'Poppins',
                            color: Color(0xFF000107),
                            fontSize: 28,
                            fontWeight: FontWeight.w500,
                            letterSpacing: -0.3,
                            height: 1.2,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          _bioUser == null
                              ? 'Log in to see your accounts.'
                              : 'Use your fingerprint or password to log in.',
                          style: const TextStyle(
                            fontFamily: 'Poppins',
                            color: Color(0xFF64748B),
                            fontSize: 14,
                            fontWeight: FontWeight.w400,
                          ),
                        ),
                        const SizedBox(height: 20),

                        // 3. Frosted Glassmorphism Card (Light luxury glass)
                        ClipRRect(
                          borderRadius: BorderRadius.circular(24),
                          child: BackdropFilter(
                            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                            child: Container(
                              padding: const EdgeInsets.all(22),
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [
                                    Colors.white.withAlpha(235),
                                    Colors.white.withAlpha(185),
                                  ],
                                ),
                                borderRadius: BorderRadius.circular(24),
                                border: Border.all(
                                  color: Colors.white,
                                  width: 1.5,
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: const Color(0xFF0091AE).withAlpha(18),
                                    blurRadius: 28,
                                    offset: const Offset(0, 10),
                                  ),
                                  BoxShadow(
                                    color: Colors.black.withAlpha(10),
                                    blurRadius: 8,
                                    offset: const Offset(0, 2),
                                  ),
                                ],
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  _buildField(
                                    controller: u,
                                    label: 'Username',
                                    prefixIcon: Icons.person_outline_rounded,
                                  ),
                                  _buildField(
                                    controller: p,
                                    label: 'Password',
                                    prefixIcon: Icons.lock_outline_rounded,
                                    obscure: _obscurePassword,
                                    suffixIcon: IconButton(
                                      icon: Icon(
                                        _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                                        color: const Color(0xFF64748B),
                                        size: 20,
                                      ),
                                      onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                                    ),
                                  ),
                                  const SizedBox(height: 18),
                                  _isLoading
                                      ? const Center(
                                          child: Padding(
                                            padding: EdgeInsets.all(12),
                                            child: CircularProgressIndicator(color: Color(0xFF0091AE)),
                                          ),
                                        )
                                      : _buildPrimaryButton('Log in', _handleLogin),
                                  if (_bioUser != null && !_isLoading) ...[
                                    const SizedBox(height: 12),
                                    _buildGlassBiometricButton(),
                                  ],
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

