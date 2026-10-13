import 'package:flutter/material.dart';

import '../../core/router/app_router.dart';
import '../../core/storage/secure_storage_service.dart';
import '../accounts/ui.dart';

/// Animated Boot Splash Screen.
/// Flashes the official CooBS logo with an organic entrance animation upon launch,
/// then smoothly transitions to BiometricLoginScreen if previously logged in,
/// or LoginScreen otherwise.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnimation;
  late final Animation<double> _opacityAnimation;
  late final Animation<double> _glowAnimation;

  @override
  void initState() {
    super.initState();
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');

    _controller = AnimationController(
      vsync: this,
      duration: isTest ? const Duration(milliseconds: 10) : const Duration(milliseconds: 1400),
    );

    _scaleAnimation = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: 0.82, end: 1.06).chain(CurveTween(curve: Curves.easeOutCubic)),
        weight: 60,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: 1.06, end: 1.00).chain(CurveTween(curve: Curves.easeInOut)),
        weight: 40,
      ),
    ]).animate(_controller);

    _opacityAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.0, 0.6, curve: Curves.easeIn),
      ),
    );

    _glowAnimation = Tween<double>(begin: 0.3, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: Curves.easeInOut,
      ),
    );

    _controller.forward().then((_) async {
      if (!mounted) return;
      final bioUser = await SecureStorageService().getBiometricUsername();
      if (!mounted) return;
      if (bioUser != null && bioUser.isNotEmpty) {
        Navigator.pushReplacementNamed(context, AppRouter.biometricLogin);
      } else {
        Navigator.pushReplacementNamed(context, AppRouter.login);
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Ambient Pacific Cyan radial glow
          AnimatedBuilder(
            animation: _glowAnimation,
            builder: (context, _) => Center(
              child: Container(
                width: 320,
                height: 320,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: [
                      const Color(0xFF0091AE).withAlpha((35 * _glowAnimation.value).toInt()),
                      const Color(0xFFE0F4F8).withAlpha((25 * _glowAnimation.value).toInt()),
                      Colors.transparent,
                    ],
                    stops: const [0.0, 0.55, 1.0],
                  ),
                ),
              ),
            ),
          ),

          // Animated CooBS Brand Logo & Subtitle
          Center(
            child: AnimatedBuilder(
              animation: _controller,
              builder: (context, _) => Opacity(
                opacity: _opacityAnimation.value,
                child: Transform.scale(
                  scale: _scaleAnimation.value,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Seamless transparent CooBS logo (light mode)
                      logoBox(height: 64, forceLight: true, forceDark: false),
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                        decoration: BoxDecoration(
                          color: const Color(0xFF0091AE).withAlpha(20),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: const Color(0xFF0091AE).withAlpha(60),
                            width: 0.8,
                          ),
                        ),
                        child: const Text(
                          'CORE BANKING SYSTEM',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            color: Color(0xFF0091AE),
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 2.2,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
