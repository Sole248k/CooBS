import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/config/app_config.dart';
import '../../core/models/auth_models.dart';
import '../../core/network/api_client.dart';
import '../../core/network/api_exceptions.dart';
import '../../core/session/biometric_authenticator.dart';
import '../../core/storage/secure_storage_service.dart';
import '../../core/theme/app_theme.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../data/mobile_repo.dart';
import 'auth_screens.dart';

/// Dedicated Biometric "Second Login Screen" for returning members.
/// Once a member has previously logged in and configured biometrics,
/// this screen is presented instead of the password form.
/// Prompts immediately for biometrics while offering options to switch accounts or use password.
class BiometricLoginScreen extends StatefulWidget {
  final ApiClient? apiClient;
  final SecureStorageService? storage;
  final BiometricAuthenticator? biometrics;
  final String? bioUser;
  final VoidCallback? onUseAnotherAccount;
  final VoidCallback? onUsePassword;
  final VoidCallback? onBiometricExpired;

  const BiometricLoginScreen({
    super.key,
    this.apiClient,
    this.storage,
    this.biometrics,
    this.bioUser,
    this.onUseAnotherAccount,
    this.onUsePassword,
    this.onBiometricExpired,
  });

  @override
  State<BiometricLoginScreen> createState() => _BiometricLoginScreenState();
}

class _BiometricLoginScreenState extends State<BiometricLoginScreen> with SingleTickerProviderStateMixin {
  late final ApiClient _apiClient = widget.apiClient ?? ApiClient();
  late final SecureStorageService _storage = widget.storage ?? SecureStorageService();
  late final BiometricAuthenticator _biometrics = widget.biometrics ?? LocalBiometricAuthenticator();

  String? _bioUser;
  bool _isLoading = false;
  bool _biometricsAvailable = true;

  late final AnimationController _pulseController;
  late final Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 0.95, end: 1.05).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    _initAndAuthenticate();
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  Future<void> _initAndAuthenticate() async {
    final bioUser = widget.bioUser ?? await _storage.getBiometricUsername();
    final available = await _biometrics.isAvailable();
    if (!mounted) return;

    setState(() {
      _bioUser = bioUser;
      _biometricsAvailable = available;
    });

    if (bioUser != null && available) {
      // Auto-trigger biometric prompt upon screen entrance
      await _handleBiometricLogin();
    }
  }

  /// Authenticate using biometrics, exchange stored refresh token, and open app.
  Future<void> _handleBiometricLogin() async {
    final bioUser = _bioUser;
    if (_isLoading || bioUser == null) return;
    setState(() => _isLoading = true);

    String? refreshToken;
    try {
      final authenticated = await _biometrics.authenticate('Log in to CooBS as $bioUser');
      if (!authenticated) return;

      refreshToken = await _storage.getBiometricRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        await _expireBiometricLogin();
        return;
      }

      final response = await _apiClient.post(
        '/auth/token/refresh',
        body: {'refreshToken': refreshToken},
      );
      final token = TokenResponse.fromJson(response);
      if (token.accessToken.isEmpty) {
        throw UnauthorizedException('Invalid refresh response');
      }

      final resolvedUid = token.userId > 0 ? token.userId : MobileRepo.resolveCustomerId(bioUser);
      await _storage.saveTokens(
        accessToken: token.accessToken.isNotEmpty ? token.accessToken : 'demo-token-$bioUser',
        refreshToken: token.refreshToken,
        userId: resolvedUid,
        username: bioUser,
      );

      await Demo.i.fetchScoresAndHabits(resolvedUid);
      await Demo.i.load();
      if (mounted) {
        toApp(context, 'Welcome back, $bioUser');
      }
    } on ApiException catch (e) {
      final unreachable = e is NetworkTimeoutException || e is ConnectionException || (e.statusCode ?? 0) >= 500;
      if (unreachable) {
        if (mounted) snack(context, 'Could not reach CooBS. Check your connection and try again.');
      } else {
        // Refresh token expired (7 days) or revoked
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
    snack(context, 'Fingerprint login has expired. Please log in with your password.');
    if (widget.onBiometricExpired != null) {
      widget.onBiometricExpired!();
    } else {
      _handleUsePassword();
    }
  }

  void _handleUseAnotherAccount() {
    if (widget.onUseAnotherAccount != null) {
      widget.onUseAnotherAccount!();
    } else {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => LoginScreen(
            apiClient: _apiClient,
            storage: _storage,
            biometrics: _biometrics,
            forcePassword: true,
            initialUsername: '',
          ),
        ),
      );
    }
  }

  void _handleUsePassword() {
    if (widget.onUsePassword != null) {
      widget.onUsePassword!();
    } else {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => LoginScreen(
            apiClient: _apiClient,
            storage: _storage,
            biometrics: _biometrics,
            forcePassword: true,
            initialUsername: _bioUser ?? '',
          ),
        ),
      );
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

  String _getInitials(String? username) {
    if (username == null || username.trim().isEmpty) return 'M';
    final parts = username.trim().split(RegExp(r'[\s._-]+'));
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return username.substring(0, username.length >= 2 ? 2 : 1).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final displayName = _bioUser ?? 'Member';
    final initials = _getInitials(_bioUser);

    return Theme(
      data: AppTheme.lightTheme,
      child: AnnotatedRegion<SystemUiOverlayStyle>(
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
              // Ambient cyan radial glow
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
                        const Color(0xFF0091AE).withAlpha(40),
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

              // Foreground content
              SafeArea(
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 440),
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // 1. Top Bar: CooBS Brand Logo & Server Settings
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              logoBox(height: 36, forceLight: true, forceDark: false),
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
                          const SizedBox(height: 14),

                          // 2. Frosted Glass Biometric Card
                          ClipRRect(
                            borderRadius: BorderRadius.circular(24),
                            child: BackdropFilter(
                              filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                                decoration: BoxDecoration(
                                  gradient: LinearGradient(
                                    begin: Alignment.topLeft,
                                    end: Alignment.bottomRight,
                                    colors: [
                                      Colors.white.withAlpha(240),
                                      Colors.white.withAlpha(200),
                                    ],
                                  ),
                                  borderRadius: BorderRadius.circular(24),
                                  border: Border.all(
                                    color: Colors.white,
                                    width: 1.5,
                                  ),
                                  boxShadow: [
                                    BoxShadow(
                                      color: const Color(0xFF0091AE).withAlpha(20),
                                      blurRadius: 30,
                                      offset: const Offset(0, 12),
                                    ),
                                    BoxShadow(
                                      color: Colors.black.withAlpha(8),
                                      blurRadius: 10,
                                      offset: const Offset(0, 2),
                                    ),
                                  ],
                                ),
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  crossAxisAlignment: CrossAxisAlignment.center,
                                  children: [
                                    // Member Initials Avatar
                                    Container(
                                      width: 58,
                                      height: 58,
                                      decoration: BoxDecoration(
                                        shape: BoxShape.circle,
                                        gradient: const LinearGradient(
                                          colors: [
                                            Color(0xFF0091AE),
                                            Color(0xFF00A2C2),
                                          ],
                                          begin: Alignment.topLeft,
                                          end: Alignment.bottomRight,
                                        ),
                                        boxShadow: [
                                          BoxShadow(
                                            color: const Color(0xFF0091AE).withAlpha(60),
                                            blurRadius: 14,
                                            offset: const Offset(0, 4),
                                          ),
                                        ],
                                      ),
                                      alignment: Alignment.center,
                                      child: Text(
                                        initials,
                                        style: const TextStyle(
                                          fontFamily: 'Poppins',
                                          color: Colors.white,
                                          fontSize: 20,
                                          fontWeight: FontWeight.w700,
                                          letterSpacing: 0.8,
                                        ),
                                      ),
                                    ),
                                    const SizedBox(height: 10),

                                    // Welcome greeting
                                    const Text(
                                      'Welcome back,',
                                      style: TextStyle(
                                        fontFamily: 'Poppins',
                                        color: Color(0xFF64748B),
                                        fontSize: 13,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      displayName,
                                      style: const TextStyle(
                                        fontFamily: 'Poppins',
                                        color: Color(0xFF000107),
                                        fontSize: 22,
                                        fontWeight: FontWeight.w700,
                                        letterSpacing: -0.3,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      _biometricsAvailable
                                          ? 'Use your biometric credential to access your account.'
                                          : 'Biometrics unavailable. Please log in with your password.',
                                      textAlign: TextAlign.center,
                                      style: const TextStyle(
                                        fontFamily: 'Poppins',
                                        color: Color(0xFF64748B),
                                        fontSize: 12,
                                        fontWeight: FontWeight.w400,
                                        height: 1.3,
                                      ),
                                    ),
                                    const SizedBox(height: 16),

                                    // Tactile Biometric Scanner Sensor Button
                                    GestureDetector(
                                      onTap: _isLoading ? null : _handleBiometricLogin,
                                      child: ScaleTransition(
                                        scale: _isLoading
                                            ? const AlwaysStoppedAnimation(1.0)
                                            : _pulseAnimation,
                                        child: Container(
                                          width: 76,
                                          height: 76,
                                          decoration: BoxDecoration(
                                            shape: BoxShape.circle,
                                            color: Colors.white,
                                            border: Border.all(
                                              color: const Color(0xFF0091AE).withAlpha(120),
                                              width: 1.8,
                                            ),
                                            boxShadow: [
                                              BoxShadow(
                                                color: const Color(0xFF0091AE).withAlpha(45),
                                                blurRadius: 16,
                                                spreadRadius: 2,
                                                offset: const Offset(0, 4),
                                              ),
                                            ],
                                          ),
                                          child: Center(
                                            child: _isLoading
                                                ? const SizedBox(
                                                    width: 32,
                                                    height: 32,
                                                    child: CircularProgressIndicator(
                                                      color: Color(0xFF0091AE),
                                                      strokeWidth: 2.5,
                                                    ),
                                                  )
                                                : const Icon(
                                                    Icons.fingerprint,
                                                    color: Color(0xFF0091AE),
                                                    size: 42,
                                                  ),
                                          ),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(height: 18),

                                    // Primary Action: Log in with fingerprint
                                    SizedBox(
                                      width: double.infinity,
                                      height: 44,
                                      child: Container(
                                        decoration: BoxDecoration(
                                          borderRadius: BorderRadius.circular(16),
                                          gradient: const LinearGradient(
                                            colors: [
                                              Color(0xFF0091AE),
                                              Color(0xFF00A2C2),
                                            ],
                                          ),
                                          boxShadow: [
                                            BoxShadow(
                                              color: const Color(0xFF0091AE).withAlpha(70),
                                              blurRadius: 14,
                                              offset: const Offset(0, 4),
                                            ),
                                          ],
                                        ),
                                        child: ElevatedButton.icon(
                                          onPressed: _isLoading ? null : _handleBiometricLogin,
                                          icon: const Icon(Icons.fingerprint, color: Colors.white, size: 18),
                                          label: const Text(
                                            'Log in with fingerprint',
                                            style: TextStyle(
                                              fontFamily: 'Poppins',
                                              color: Colors.white,
                                              fontSize: 14,
                                              fontWeight: FontWeight.w700,
                                              letterSpacing: 0.3,
                                            ),
                                          ),
                                          style: ElevatedButton.styleFrom(
                                            backgroundColor: Colors.transparent,
                                            shadowColor: Colors.transparent,
                                            shape: RoundedRectangleBorder(
                                              borderRadius: BorderRadius.circular(16),
                                            ),
                                          ),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(height: 10),

                                    // Divider with OR
                                    Row(
                                      children: [
                                        Expanded(
                                          child: Divider(
                                            color: const Color(0xFFE2E8F0).withAlpha(200),
                                            thickness: 1,
                                          ),
                                        ),
                                        const Padding(
                                          padding: EdgeInsets.symmetric(horizontal: 10),
                                          child: Text(
                                            'OR',
                                            style: TextStyle(
                                              fontFamily: 'Poppins',
                                              color: Color(0xFF94A3B8),
                                              fontSize: 10,
                                              fontWeight: FontWeight.w600,
                                              letterSpacing: 1.2,
                                            ),
                                          ),
                                        ),
                                        Expanded(
                                          child: Divider(
                                            color: const Color(0xFFE2E8F0).withAlpha(200),
                                            thickness: 1,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 10),

                                    // Secondary Action: Use another account
                                    SizedBox(
                                      width: double.infinity,
                                      height: 44,
                                      child: OutlinedButton.icon(
                                        onPressed: _handleUseAnotherAccount,
                                        icon: const Icon(
                                          Icons.switch_account_outlined,
                                          color: Color(0xFF000107),
                                          size: 18,
                                        ),
                                        label: const Text(
                                          'Use another account',
                                          style: TextStyle(
                                            fontFamily: 'Poppins',
                                            color: Color(0xFF000107),
                                            fontSize: 13,
                                            fontWeight: FontWeight.w600,
                                          ),
                                        ),
                                        style: OutlinedButton.styleFrom(
                                          backgroundColor: Colors.white.withAlpha(200),
                                          side: const BorderSide(color: Color(0xFFCBD5E1), width: 1.2),
                                          shape: RoundedRectangleBorder(
                                            borderRadius: BorderRadius.circular(16),
                                          ),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(height: 4),

                                    // Tertiary Option: Log in with password
                                    TextButton.icon(
                                      onPressed: _handleUsePassword,
                                      icon: const Icon(
                                        Icons.lock_outline_rounded,
                                        color: Color(0xFF64748B),
                                        size: 15,
                                      ),
                                      label: const Text(
                                        'Log in with password',
                                        style: TextStyle(
                                          fontFamily: 'Poppins',
                                          color: Color(0xFF64748B),
                                          fontSize: 12,
                                          fontWeight: FontWeight.w500,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
