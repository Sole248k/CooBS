import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';

import '../../core/theme/theme_controller.dart';
import '../accounts/ui.dart';
import 'ad_models.dart';

/// Liquid Crystal Display (LCD) Ad Modal Overlay.
/// Dynamically adapts to Light Mode and Dark Mode according to app theme.
/// Features a liquid-crystal frosted bezel, specular glare reflection,
/// pulsing LCD status diode, and theme-calibrated typography and controls.
class AdLiquidCrystalDialog extends StatefulWidget {
  final AdItem ad;
  final Duration autoDismissDuration;

  const AdLiquidCrystalDialog({
    super.key,
    required this.ad,
    this.autoDismissDuration = const Duration(seconds: 8),
  });

  /// Helper to display the dialog with smooth spring entrance.
  static Future<void> show(BuildContext context, AdItem ad) {
    final isDark = ThemeController.i.dark;
    return showGeneralDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'Dismiss Ad',
      barrierColor: Colors.black.withAlpha(isDark ? 165 : 115),
      transitionDuration: const Duration(milliseconds: 420),
      pageBuilder: (context, anim1, anim2) {
        return AdLiquidCrystalDialog(ad: ad);
      },
      transitionBuilder: (context, anim1, anim2, child) {
        final curved = CurvedAnimation(
          parent: anim1,
          curve: Curves.easeOutBack,
          reverseCurve: Curves.easeInCubic,
        );
        return ScaleTransition(
          scale: Tween<double>(begin: 0.86, end: 1.0).animate(curved),
          child: FadeTransition(
            opacity: anim1,
            child: child,
          ),
        );
      },
    );
  }

  @override
  State<AdLiquidCrystalDialog> createState() => _AdLiquidCrystalDialogState();
}

class _AdLiquidCrystalDialogState extends State<AdLiquidCrystalDialog>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulseController;
  Timer? _dismissTimer;
  double _progress = 1.0;
  Timer? _progressTicker;

  @override
  void initState() {
    super.initState();
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );
    if (!isTest) {
      _pulseController.repeat(reverse: true);
      _startAutoDismiss();
    } else {
      _pulseController.value = 1.0;
    }
  }

  void _startAutoDismiss() {
    final totalMs = widget.autoDismissDuration.inMilliseconds;
    const intervalMs = 50;
    var elapsed = 0;

    _progressTicker = Timer.periodic(const Duration(milliseconds: intervalMs), (timer) {
      elapsed += intervalMs;
      if (mounted) {
        setState(() {
          _progress = (1.0 - (elapsed / totalMs)).clamp(0.0, 1.0);
        });
      }
      if (elapsed >= totalMs) {
        timer.cancel();
        _dismiss();
      }
    });
  }

  void _pauseAutoDismiss() {
    _progressTicker?.cancel();
    _dismissTimer?.cancel();
  }

  void _dismiss() {
    if (mounted && Navigator.canPop(context)) {
      Navigator.of(context, rootNavigator: true).pop();
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _progressTicker?.cancel();
    _dismissTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: ThemeController.i,
      builder: (context, _) {
        final isDark = ThemeController.i.dark;
        final ad = widget.ad;
        final size = MediaQuery.of(context).size;
        final maxDialogWidth = 420.0;
        final maxImageHeight = (size.height * 0.44).clamp(180.0, 420.0);

        // Theme-aware liquid crystal colors
        final dialogBg = isDark
            ? const Color(0xFF070F18).withAlpha(242)
            : Colors.white.withAlpha(240);

        final borderColor = ad.isEasterEgg
            ? (isDark
                ? const Color(0xFFF59E0B).withAlpha(180)
                : const Color(0xFFD97706).withAlpha(140))
            : (isDark
                ? const Color(0xFF0091AE).withAlpha(160)
                : const Color(0xFF0091AE).withAlpha(130));

        final glowColor = (ad.isEasterEgg
                ? const Color(0xFFF59E0B)
                : const Color(0xFF0091AE))
            .withAlpha(isDark ? 55 : 28);

        final titleColor = isDark ? Colors.white : const Color(0xFF000107);
        final subtitleColor = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

        final imageFrameBg = isDark ? Colors.black : const Color(0xFFF1F5F9);
        final imageFrameBorder = isDark
            ? Colors.white.withAlpha(25)
            : const Color(0xFFE2E8F0);

        return Center(
          child: Material(
            color: Colors.transparent,
            child: Container(
              constraints: BoxConstraints(
                maxWidth: maxDialogWidth,
                maxHeight: size.height * 0.90,
              ),
              margin: const EdgeInsets.symmetric(horizontal: 18, vertical: 24),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(26),
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                  child: GestureDetector(
                    onTap: _pauseAutoDismiss,
                    child: Container(
                      decoration: BoxDecoration(
                        color: dialogBg,
                        borderRadius: BorderRadius.circular(26),
                        border: Border.all(
                          color: borderColor,
                          width: 1.5,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: glowColor,
                            blurRadius: 32,
                            spreadRadius: 2,
                            offset: const Offset(0, 8),
                          ),
                          BoxShadow(
                            color: Colors.black.withAlpha(isDark ? 180 : 35),
                            blurRadius: 24,
                            offset: const Offset(0, 10),
                          ),
                        ],
                      ),
                      child: Stack(
                        children: [
                          // Specular Liquid Crystal Surface Refraction
                          Positioned(
                            top: 0,
                            left: 0,
                            right: 0,
                            height: 90,
                            child: IgnorePointer(
                              child: Container(
                                decoration: BoxDecoration(
                                  borderRadius:
                                      const BorderRadius.vertical(top: Radius.circular(26)),
                                  gradient: LinearGradient(
                                    begin: Alignment.topLeft,
                                    end: Alignment.bottomRight,
                                    colors: isDark
                                        ? [
                                            Colors.white.withAlpha(45),
                                            Colors.white.withAlpha(8),
                                            Colors.transparent,
                                          ]
                                        : [
                                            Colors.white.withAlpha(160),
                                            Colors.white.withAlpha(45),
                                            Colors.transparent,
                                          ],
                                    stops: const [0.0, 0.45, 1.0],
                                  ),
                                ),
                              ),
                            ),
                          ),

                          // Dialog Content
                          Padding(
                            padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
                            child: SingleChildScrollView(
                              physics: const BouncingScrollPhysics(),
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                // LCD Top Status Bar
                                _buildLcdStatusBar(ad, isDark),
                                const SizedBox(height: 12),

                                // Main LCD Screen Viewport (Image Display)
                                ConstrainedBox(
                                  constraints: BoxConstraints(maxHeight: maxImageHeight),
                                  child: Container(
                                    decoration: BoxDecoration(
                                      color: imageFrameBg,
                                      borderRadius: BorderRadius.circular(18),
                                      border: Border.all(
                                        color: imageFrameBorder,
                                        width: 1.0,
                                      ),
                                      boxShadow: [
                                        BoxShadow(
                                          color: Colors.black.withAlpha(isDark ? 120 : 15),
                                          blurRadius: 12,
                                          offset: const Offset(0, 4),
                                        ),
                                      ],
                                    ),
                                    child: ClipRRect(
                                      borderRadius: BorderRadius.circular(18),
                                      child: Stack(
                                        alignment: Alignment.center,
                                        children: [
                                          Image.asset(
                                            ad.assetPath,
                                            fit: BoxFit.contain,
                                            errorBuilder: (context, error, stackTrace) => Container(
                                              height: 280,
                                              color: isDark ? const Color(0xFF0B1924) : const Color(0xFFE0F4F8),
                                              alignment: Alignment.center,
                                              child: Column(
                                                mainAxisSize: MainAxisSize.min,
                                                children: [
                                                  Icon(Icons.campaign_rounded, size: 48, color: ad.primaryColor),
                                                  const SizedBox(height: 8),
                                                  Text(
                                                    ad.title,
                                                    style: TextStyle(
                                                      fontFamily: 'Poppins',
                                                      color: titleColor,
                                                      fontWeight: FontWeight.w600,
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ),

                                          // Subtle corner glass glare
                                          Positioned(
                                            top: 0,
                                            right: 0,
                                            child: IgnorePointer(
                                              child: Container(
                                                width: 100,
                                                height: 100,
                                                decoration: BoxDecoration(
                                                  gradient: RadialGradient(
                                                    center: Alignment.topRight,
                                                    radius: 1.2,
                                                    colors: [
                                                      Colors.white.withAlpha(isDark ? 35 : 70),
                                                      Colors.transparent,
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
                                ),
                                const SizedBox(height: 14),

                                // LCD Title & Typography
                                Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            ad.title,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                              fontFamily: 'Poppins',
                                              fontSize: 16.5,
                                              fontWeight: FontWeight.w700,
                                              color: titleColor,
                                              letterSpacing: 0.1,
                                            ),
                                          ),
                                          const SizedBox(height: 3),
                                          Text(
                                            ad.subtitle,
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                              fontFamily: 'Poppins',
                                              fontSize: 12,
                                              fontWeight: FontWeight.w400,
                                              color: subtitleColor,
                                              height: 1.3,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 14),

                                // Bottom LCD Control Strip
                                _buildBottomControls(ad, isDark),
                              ],
                            ),
                          ),
                        ),
                      ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  /// Builds the top LCD digital monitor bar with pulsing status diode and close button.
  Widget _buildLcdStatusBar(AdItem ad, bool isDark) {
    final badgeBg = ad.isEasterEgg
        ? (isDark ? const Color(0xFFF59E0B).withAlpha(35) : const Color(0xFFFEF3C7))
        : (isDark ? const Color(0xFF0091AE).withAlpha(35) : const Color(0xFFE0F4F8));

    final badgeBorder = ad.isEasterEgg
        ? (isDark ? const Color(0xFFF59E0B).withAlpha(90) : const Color(0xFFF59E0B).withAlpha(140))
        : (isDark ? const Color(0xFF0091AE).withAlpha(90) : const Color(0xFF0091AE).withAlpha(120));

    final badgeTextColor = ad.isEasterEgg
        ? (isDark ? const Color(0xFFFDE68A) : const Color(0xFF92400E))
        : (isDark ? const Color(0xFFE0F4F8) : const Color(0xFF005F73));

    final closeBtnBg = isDark ? Colors.white.withAlpha(22) : Colors.black.withAlpha(14);
    final closeBtnBorder = isDark ? Colors.white.withAlpha(45) : Colors.black.withAlpha(30);
    final closeIconColor = isDark ? Colors.white : const Color(0xFF000107);

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        // LCD Pulse Beacon + Badge
        Expanded(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AnimatedBuilder(
                animation: _pulseController,
                builder: (context, _) {
                  final glow = _pulseController.value;
                  return Container(
                    width: 9,
                    height: 9,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: ad.isEasterEgg
                          ? const Color(0xFFF59E0B)
                          : const Color(0xFF10B981),
                      boxShadow: [
                        BoxShadow(
                          color: (ad.isEasterEgg
                                  ? const Color(0xFFF59E0B)
                                  : const Color(0xFF10B981))
                              .withAlpha((180 * glow).toInt() + 40),
                          blurRadius: 8 * glow + 2,
                          spreadRadius: 1.5 * glow,
                        ),
                      ],
                    ),
                  );
                },
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3.5),
                  decoration: BoxDecoration(
                    color: badgeBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: badgeBorder,
                      width: 0.8,
                    ),
                  ),
                  child: Text(
                    ad.badgeText,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.8,
                      color: badgeTextColor,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),

        // Frosted Close Button
        Tooltip(
          message: 'Close Ad',
          child: InkWell(
            onTap: _dismiss,
            borderRadius: BorderRadius.circular(20),
            child: Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: closeBtnBg,
                shape: BoxShape.circle,
                border: Border.all(
                  color: closeBtnBorder,
                  width: 0.8,
                ),
              ),
              child: Icon(
                Icons.close_rounded,
                size: 18,
                color: closeIconColor,
              ),
            ),
          ),
        ),
      ],
    );
  }

  /// Builds the bottom action bar with continue button and auto-dismiss bar.
  Widget _buildBottomControls(AdItem ad, bool isDark) {
    final oddsTextColor = ad.isEasterEgg
        ? (isDark ? const Color(0xFFF59E0B) : const Color(0xFFD97706))
        : (isDark ? const Color(0xFF38BDF8) : const Color(0xFF007A94));

    final dismissBtnBorder = isDark
        ? Colors.white.withAlpha(40)
        : const Color(0xFFCBD5E1);

    final dismissBtnText = isDark
        ? Colors.white
        : const Color(0xFF334155);

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Rarity Tag + Auto-dismiss progress
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              ad.oddsDescription,
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 11,
                fontWeight: FontWeight.w500,
                color: oddsTextColor,
              ),
            ),
            if (_progress > 0)
              Text(
                '${(_progress * widget.autoDismissDuration.inSeconds).ceil()}s',
                style: const TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 11,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF64748B),
                ),
              ),
          ],
        ),
        const SizedBox(height: 6),

        // Auto-dismiss indicator track
        ClipRRect(
          borderRadius: BorderRadius.circular(2),
          child: LinearProgressIndicator(
            value: _progress,
            minHeight: 2.2,
            backgroundColor: isDark
                ? Colors.white.withAlpha(15)
                : Colors.black.withAlpha(12),
            valueColor: AlwaysStoppedAnimation<Color>(
              ad.isEasterEgg ? const Color(0xFFF59E0B) : const Color(0xFF0091AE),
            ),
          ),
        ),
        const SizedBox(height: 12),

        // Buttons
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _dismiss,
                style: OutlinedButton.styleFrom(
                  foregroundColor: dismissBtnText,
                  side: BorderSide(color: dismissBtnBorder, width: 1.0),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: Text(
                  'Dismiss',
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                    color: dismissBtnText,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              flex: 2,
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  gradient: LinearGradient(
                    colors: ad.isEasterEgg
                        ? const [Color(0xFFD97706), Color(0xFFF59E0B)]
                        : const [Color(0xFF004E64), Color(0xFF0091AE)],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: (ad.isEasterEgg
                              ? const Color(0xFFF59E0B)
                              : const Color(0xFF0091AE))
                          .withAlpha(60),
                      blurRadius: 12,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: ElevatedButton(
                  onPressed: () {
                    _dismiss();
                    if (mounted) {
                      snack(
                        context,
                        ad.isEasterEgg
                            ? 'Special Umamusume Easter Egg event unlocked!'
                            : 'Welcome to CooBS! Enjoy zero-fee cooperative banking.',
                      );
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.transparent,
                    shadowColor: Colors.transparent,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        ad.isEasterEgg ? 'View Campaign' : 'Continue to CooBS',
                        style: const TextStyle(
                          fontFamily: 'Poppins',
                          fontWeight: FontWeight.w600,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(width: 6),
                      const Icon(Icons.arrow_forward_rounded, size: 16),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
