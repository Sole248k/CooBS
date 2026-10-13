import 'dart:ui';
import 'package:flutter/material.dart';

import '../../core/storage/secure_storage_service.dart';
import '../../core/theme/theme_controller.dart';
import '../beneficiaries/beneficiary_screens.dart';
import '../kyc/kyc_intake_screen.dart';
import '../remittance/remittance_screen.dart';
import '../loans/loan_screens.dart';
import '../profile/tab_screens.dart';
import '../qr/qr_screens.dart';
import '../resilience/service_unavailable_view.dart';
import '../scores/scores_screen.dart';
import '../shop/shop_screen.dart';
import '../shop/shop_store.dart';
import '../vaults/ipon_vaults_screen.dart';
import 'demo_store.dart';
import 'ui.dart';
import '../ads/ad_models.dart';
import '../ads/ad_service.dart';

/// Palette for ambient spot gradients across card swaps.
class AmbientPalette {
  final Color primary;
  final Color secondary;
  final Color accent;

  const AmbientPalette({
    required this.primary,
    required this.secondary,
    required this.accent,
  });
}

AmbientPalette getAmbientPalette(int cur, Demo d) {
  if (cur >= d.accts.length) {
    // Loan / Credit Line: Warm Amber & Rose Coral
    return const AmbientPalette(
      primary: Color(0xFFF59E0B),
      secondary: Color(0xFFFB7185),
      accent: Color(0xFFFDE68A),
    );
  }
  if (d.accts.isEmpty) {
    return const AmbientPalette(
      primary: Color(0xFF0091AE),
      secondary: Color(0xFF38BDF8),
      accent: Color(0xFF2DD4BF),
    );
  }

  final type = d.accts[cur].type.toUpperCase();
  if (type.contains('CHECKING') || type.contains('CURRENT') || cur == 1) {
    // Royal Sapphire Blue & Electric Indigo
    return const AmbientPalette(
      primary: Color(0xFF2563EB),
      secondary: Color(0xFF818CF8),
      accent: Color(0xFF06B6D4),
    );
  } else if (cur == 0) {
    // Primary Savings: Vibrant Pacific Cyan-Teal & Sky Blue
    return const AmbientPalette(
      primary: Color(0xFF0091AE),
      secondary: Color(0xFF38BDF8),
      accent: Color(0xFF2DD4BF),
    );
  } else if (cur == 2) {
    // Third Card: Deep Emerald Jade & Spring Mint
    return const AmbientPalette(
      primary: Color(0xFF059669),
      secondary: Color(0xFF10B981),
      accent: Color(0xFF34D399),
    );
  } else {
    // Additional cards: Violet & Orchid
    return const AmbientPalette(
      primary: Color(0xFF7C3AED),
      secondary: Color(0xFFA855F7),
      accent: Color(0xFFEC4899),
    );
  }
}

/// Dynamic spot gradient background with dominant white base (inspired by soft watercolor mesh orbs).
class _SpotAmbientBackground extends StatefulWidget {
  final AmbientPalette palette;
  final bool isDark;

  const _SpotAmbientBackground({
    required this.palette,
    required this.isDark,
  });

  @override
  State<_SpotAmbientBackground> createState() => _SpotAmbientBackgroundState();
}

class _SpotAmbientBackgroundState extends State<_SpotAmbientBackground>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;
  late Color _oldPrimary, _newPrimary;
  late Color _oldSecondary, _newSecondary;
  late Color _oldAccent, _newAccent;

  @override
  void initState() {
    super.initState();
    _oldPrimary = _newPrimary = widget.palette.primary;
    _oldSecondary = _newSecondary = widget.palette.secondary;
    _oldAccent = _newAccent = widget.palette.accent;
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );
    _animation = CurvedAnimation(parent: _controller, curve: Curves.easeInOutCubic);
  }

  @override
  void didUpdateWidget(covariant _SpotAmbientBackground oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.palette.primary != widget.palette.primary ||
        oldWidget.palette.secondary != widget.palette.secondary ||
        oldWidget.palette.accent != widget.palette.accent ||
        oldWidget.isDark != widget.isDark) {
      _oldPrimary = Color.lerp(_oldPrimary, _newPrimary, _animation.value) ?? _newPrimary;
      _oldSecondary = Color.lerp(_oldSecondary, _newSecondary, _animation.value) ?? _newSecondary;
      _oldAccent = Color.lerp(_oldAccent, _newAccent, _animation.value) ?? _newAccent;

      _newPrimary = widget.palette.primary;
      _newSecondary = widget.palette.secondary;
      _newAccent = widget.palette.accent;

      _controller.forward(from: 0.0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, _) {
        final t = _animation.value;
        final curPrimary = Color.lerp(_oldPrimary, _newPrimary, t) ?? _newPrimary;
        final curSecondary = Color.lerp(_oldSecondary, _newSecondary, t) ?? _newSecondary;
        final curAccent = Color.lerp(_oldAccent, _newAccent, t) ?? _newAccent;

        return CustomPaint(
          size: Size.infinite,
          painter: _SpotPainter(
            primary: curPrimary,
            secondary: curSecondary,
            accent: curAccent,
            isDark: widget.isDark,
          ),
        );
      },
    );
  }
}

class _SpotPainter extends CustomPainter {
  final Color primary;
  final Color secondary;
  final Color accent;
  final bool isDark;

  _SpotPainter({
    required this.primary,
    required this.secondary,
    required this.accent,
    required this.isDark,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // 1. Dominant background base: Pure clean white in light mode, deep obsidian in dark mode.
    final baseColor = isDark ? const Color(0xFF000107) : Colors.white;
    canvas.drawColor(baseColor, BlendMode.src);

    // 2. Spot 1: Upper-Left / Top-Center spot (behind header and cards)
    final spot1Center = Offset(size.width * 0.32, size.height * 0.16);
    final spot1Radius = size.width * 0.76;
    final spot1Paint = Paint()
      ..shader = RadialGradient(
        colors: [
          primary.withAlpha(isDark ? 68 : 45),
          primary.withAlpha(isDark ? 28 : 18),
          Colors.transparent,
        ],
        stops: const [0.0, 0.45, 1.0],
      ).createShader(Rect.fromCircle(center: spot1Center, radius: spot1Radius));
    canvas.drawCircle(spot1Center, spot1Radius, spot1Paint);

    // 3. Spot 2: Middle-Right spot (softly illuminating Recent Transactions area)
    final spot2Center = Offset(size.width * 0.92, size.height * 0.52);
    final spot2Radius = size.width * 0.64;
    final spot2Paint = Paint()
      ..shader = RadialGradient(
        colors: [
          secondary.withAlpha(isDark ? 55 : 34),
          secondary.withAlpha(isDark ? 22 : 14),
          Colors.transparent,
        ],
        stops: const [0.0, 0.45, 1.0],
      ).createShader(Rect.fromCircle(center: spot2Center, radius: spot2Radius));
    canvas.drawCircle(spot2Center, spot2Radius, spot2Paint);

    // 4. Spot 3: Bottom-Left spot (behind bottom area & floating navbar)
    final spot3Center = Offset(size.width * 0.12, size.height * 0.86);
    final spot3Radius = size.width * 0.58;
    final spot3Paint = Paint()
      ..shader = RadialGradient(
        colors: [
          accent.withAlpha(isDark ? 45 : 26),
          accent.withAlpha(isDark ? 16 : 9),
          Colors.transparent,
        ],
        stops: const [0.0, 0.45, 1.0],
      ).createShader(Rect.fromCircle(center: spot3Center, radius: spot3Radius));
    canvas.drawCircle(spot3Center, spot3Radius, spot3Paint);
  }

  @override
  bool shouldRepaint(covariant _SpotPainter oldDelegate) {
    return oldDelegate.primary != primary ||
        oldDelegate.secondary != secondary ||
        oldDelegate.accent != accent ||
        oldDelegate.isDark != isDark;
  }
}

/// Mobile shell: fixed logo header + floating pill navbar (icons only).
/// Tabs: Home, Scores & Habits, Alerts (center), Beneficiaries, Profile.
class MobileShell extends StatefulWidget {
  const MobileShell({super.key});
  @override
  State<MobileShell> createState() => _MobileShellState();
}

class _MobileShellState extends State<MobileShell> {
  int i = 0;
  static const _home = 0, _scores = 1, _alerts = 2, _vaults = 3, _profile = 4;
  late final Future<String?> _usernameFuture;

  @override
  void initState() {
    super.initState();
    _usernameFuture = SecureStorageService().getUsername();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        AdService.maybeShowAd(context);
      }
    });
  }

  void _goHome() => setState(() => i = _home);

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: Listenable.merge([ThemeController.i, Demo.i]),
        builder: (context, _) {
          final palette = getAmbientPalette(Demo.i.cur, Demo.i);

          return Stack(
            children: [
              Positioned.fill(
                child: _SpotAmbientBackground(
                  palette: palette,
                  isDark: kDark,
                ),
              ),
              Scaffold(
                backgroundColor: Colors.transparent,
                body: SafeArea(
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 600),
                      child: Column(children: [
                        if (i != _scores && i != _vaults && i != _alerts)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                          child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                            InkWell(
                              onTap: () => AdService.showAd(context),
                              borderRadius: BorderRadius.circular(8),
                              child: Tooltip(
                                message: 'CooBS Display (Tap to view ad banner)',
                                child: logoBox(height: 20),
                              ),
                            ),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                AnimatedBuilder(
                                  animation: Demo.i,
                                  builder: (context, _) => Tooltip(
                                    message: Demo.i.kyc == 'VERIFIED' ? 'Verified Account' : 'Verification Required',
                                    child: InkWell(
                                      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const KycIntakeScreen())),
                                      borderRadius: BorderRadius.circular(20),
                                      child: Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                        decoration: BoxDecoration(
                                          color: kDark ? const Color(0xFF0C141F).withAlpha(190) : Colors.white.withAlpha(210),
                                          borderRadius: BorderRadius.circular(20),
                                          border: Border.all(
                                            color: kDark ? Colors.white.withAlpha(24) : Colors.white.withAlpha(220),
                                            width: 1.0,
                                          ),
                                          boxShadow: [
                                            BoxShadow(
                                              color: Colors.black.withAlpha(kDark ? 22 : 6),
                                              blurRadius: 6,
                                              offset: const Offset(0, 2),
                                            ),
                                          ],
                                        ),
                                        child: Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            Icon(
                                              Demo.i.kyc == 'VERIFIED'
                                                  ? Icons.verified_rounded
                                                  : (Demo.i.kyc == 'PENDING_REVIEW' ? Icons.hourglass_top_rounded : Icons.shield_outlined),
                                              size: 15,
                                              color: Demo.i.kyc == 'VERIFIED'
                                                  ? const Color(0xFF0091AE)
                                                  : (Demo.i.kyc == 'PENDING_REVIEW' ? const Color(0xFFF59E0B) : const Color(0xFFEF4444)),
                                            ),
                                            const SizedBox(width: 5),
                                            FutureBuilder<String?>(
                                              future: _usernameFuture,
                                              builder: (context, snapshot) {
                                                final user = snapshot.data ?? 'john_doe';
                                                return ConstrainedBox(
                                                  constraints: const BoxConstraints(maxWidth: 125),
                                                  child: Text(
                                                    '@$user',
                                                    maxLines: 1,
                                                    overflow: TextOverflow.ellipsis,
                                                    style: TextStyle(
                                                      fontFamily: 'Poppins',
                                                      color: kInk,
                                                      fontSize: 12,
                                                      fontWeight: FontWeight.w500,
                                                    ),
                                                  ),
                                                );
                                              },
                                            ),
                                          ],
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                ListenableBuilder(
                                  listenable: Demo.i,
                                  builder: (context, _) => _TopNotificationBell(
                                    unread: Demo.i.unread,
                                    onTap: () => Navigator.push(
                                      context,
                                      MaterialPageRoute(builder: (_) => const AlertsPage()),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ]),
                        ),
                        Expanded(
                          child: IndexedStack(index: i, children: [
                            HomeScreen(onOpenVaults: () => setState(() => i = _vaults)),
                            ScoresPage(onBack: _goHome),
                            AlertsPage(onBack: _goHome),
                            IponVaultsPage(onBack: _goHome),
                            const ProfileScreen(),
                          ]),
                        ),
                      ]),
                    ),
                  ),
                ),
                bottomNavigationBar: SafeArea(
                  child: Center(
                    heightFactor: 1.0,
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 600),
                      child: Container(
                        height: 72,
                        margin: const EdgeInsets.fromLTRB(20, 0, 20, 14),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(36),
                          boxShadow: [
                            BoxShadow(
                              color: (kDark ? Colors.black : const Color(0xFF0091AE)).withAlpha(kDark ? 70 : 16),
                              blurRadius: 18,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(36),
                          child: BackdropFilter(
                            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 14),
                              decoration: BoxDecoration(
                                color: kDark
                                    ? const Color(0xFF0C141F).withAlpha(190)
                                    : Colors.white.withAlpha(205),
                                borderRadius: BorderRadius.circular(36),
                                border: Border.all(
                                  color: kDark
                                      ? Colors.white.withAlpha(24)
                                      : Colors.white.withAlpha(210),
                                  width: 1.2,
                                ),
                              ),
                              child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                                _NavCircle(Icons.home_outlined, 'Home', i == _home, () => setState(() => i = _home)),
                                _NavCircle(Icons.bar_chart_rounded, 'Scores and habits', i == _scores, () => setState(() => i = _scores)),
                                _QrNavButton(
                                  () {
                                    final curAccIdx = Demo.i.cur < Demo.i.accts.length ? Demo.i.cur : 0;
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(builder: (_) => QrHomeScreen(from: curAccIdx)),
                                    );
                                  },
                                ),
                                _NavCircle(Icons.savings_outlined, 'Co-op Ipon Vaults', i == _vaults, () => setState(() => i = _vaults)),
                                _NavCircle(Icons.person_outline, 'Profile', i == _profile, () => setState(() => i = _profile)),
                              ]),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      );
}

/// Round, icon-only nav button. Selected = subtle tint + active accent color + small dot below.
class _NavCircle extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _NavCircle(this.icon, this.label, this.selected, this.onTap);

  @override
  Widget build(BuildContext context) => Tooltip(
        message: label,
        child: Semantics(
          button: true,
          selected: selected,
          label: label,
          child: SizedBox(
            width: 44,
            height: 44,
            child: Stack(clipBehavior: Clip.none, alignment: Alignment.center, children: [
              Material(
                color: selected
                    ? (kDark ? const Color(0xFF082F3B) : const Color(0xFFE0F4F8))
                    : Colors.transparent,
                shape: const CircleBorder(),
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: onTap,
                  child: SizedBox(
                    width: 44,
                    height: 44,
                    child: Icon(
                      icon,
                      size: 20,
                      color: selected
                          ? (kDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE))
                          : (kDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B)),
                    ),
                  ),
                ),
              ),
              if (selected)
                Positioned(
                  bottom: -6,
                  child: Container(
                    width: 4,
                    height: 4,
                    decoration: BoxDecoration(
                      color: kDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE),
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
            ]),
          ),
        ),
      );
}

/// Center main action button: prominent brand-colored button for QR transactions.
class _QrNavButton extends StatelessWidget {
  final VoidCallback onTap;
  const _QrNavButton(this.onTap);

  @override
  Widget build(BuildContext context) => Tooltip(
        message: 'QR transactions',
        child: Semantics(
          button: true,
          label: 'QR transactions',
          child: SizedBox(
            width: 52,
            height: 52,
            child: Material(
              color: kDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE),
              shape: const CircleBorder(),
              elevation: 4,
              shadowColor: (kDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE)).withAlpha(100),
              child: InkWell(
                customBorder: const CircleBorder(),
                onTap: onTap,
                child: const SizedBox(
                  width: 52,
                  height: 52,
                  child: Icon(Icons.qr_code_scanner_rounded, size: 26, color: Colors.white),
                ),
              ),
            ),
          ),
        ),
      );
}

/// Top notification bell button with unread badge counter.
class _TopNotificationBell extends StatelessWidget {
  final int unread;
  final VoidCallback onTap;
  const _TopNotificationBell({required this.unread, required this.onTap});

  @override
  Widget build(BuildContext context) => Tooltip(
        message: unread > 0 ? 'Notifications ($unread)' : 'Notifications',
        child: Semantics(
          button: true,
          label: unread > 0 ? 'Notifications, $unread unread' : 'Notifications',
          child: SizedBox(
            width: 38,
            height: 38,
            child: Stack(clipBehavior: Clip.none, alignment: Alignment.center, children: [
              Material(
                color: kDark ? const Color(0xFF0D1722) : const Color(0xFFF1F5F9),
                shape: const CircleBorder(),
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: onTap,
                  child: SizedBox(
                    width: 38,
                    height: 38,
                    child: Icon(
                      Icons.notifications_outlined,
                      size: 20,
                      color: kDark ? const Color(0xFFF1F5F9) : const Color(0xFF000107),
                    ),
                  ),
                ),
              ),
              if (unread > 0)
                Positioned(
                  top: -2,
                  right: -2,
                  child: IgnorePointer(
                    child: Container(
                      constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: const Color(0xFFEF4444),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: kDark ? const Color(0xFF000107) : Colors.white,
                          width: 1.5,
                        ),
                      ),
                      child: Text(
                        unread > 9 ? '9+' : '$unread',
                        style: const TextStyle(
                          fontFamily: 'Poppins',
                          color: Colors.white,
                          fontSize: 9.5,
                          fontWeight: FontWeight.w600,
                          height: 1,
                        ),
                      ),
                    ),
                  ),
                ),
            ]),
          ),
        ),
      );
}

/// Standalone Notifications page with back button and theme support.
class AlertsPage extends StatelessWidget {
  final VoidCallback? onBack;
  const AlertsPage({super.key, this.onBack});

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        appBar: AppBar(
          backgroundColor: kBg,
          surfaceTintColor: Colors.transparent,
          foregroundColor: kInk,
          centerTitle: false,
          automaticallyImplyLeading: false,
          elevation: 0,
          scrolledUnderElevation: 0,
          toolbarHeight: 58,
          leadingWidth: 64,
          leading: Padding(
            padding: const EdgeInsets.only(left: 16),
            child: ModernBackButton(onPressed: onBack ?? () => Navigator.pop(context)),
          ),
          titleSpacing: 12,
          title: Text(
            'Notifications',
            style: TextStyle(
              fontFamily: 'Poppins',
              color: kInk,
              fontSize: 18,
              fontWeight: FontWeight.w500,
              letterSpacing: -0.3,
            ),
          ),
        ),
        body: const AlertsScreen(),
      );
}

class HomeScreen extends StatefulWidget {
  final VoidCallback? onOpenVaults;
  const HomeScreen({super.key, this.onOpenVaults});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final pc = PageController(viewportFraction: .92);
  final d = Demo.i;
  final shop = ShopStore();

  @override
  void dispose() {
    shop.dispose();
    pc.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && Demo.i.state == LoadState.loading) {
        Demo.i.load();
      }
    });
  }

  void go(Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => w));

  Widget shell(List<Color> colors, String type, String status, List<Widget> kids, {required CustomPainter painter, Color? accentGlow}) => Container(
        margin: const EdgeInsets.fromLTRB(6, 4, 6, 12),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(24),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: colors,
          ),
          border: Border.all(color: Colors.white.withAlpha(50), width: 1.2),
          boxShadow: [
            BoxShadow(
              color: (accentGlow ?? colors.last).withAlpha(35),
              blurRadius: 16,
              offset: const Offset(0, 6),
            ),
            BoxShadow(
              color: Colors.black.withAlpha(kDark ? 40 : 16),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: Stack(
            children: [
              Positioned.fill(
                child: CustomPaint(
                  painter: painter,
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 14, 18, 14),
                child: DefaultTextStyle(
                  style: const TextStyle(fontFamily: 'Poppins', color: Colors.white),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3.5),
                            decoration: BoxDecoration(
                              color: Colors.white.withAlpha(35),
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: Colors.white.withAlpha(55), width: 0.8),
                            ),
                            child: Text(
                              type.toUpperCase(),
                              style: const TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                                letterSpacing: 0.8,
                              ),
                            ),
                          ),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                                decoration: BoxDecoration(
                                  color: Colors.white.withAlpha(225),
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Container(
                                      width: 5,
                                      height: 5,
                                      decoration: BoxDecoration(
                                        color: status == 'ACTIVE' ? const Color(0xFF059669) : const Color(0xFF64748B),
                                        shape: BoxShape.circle,
                                      ),
                                    ),
                                    const SizedBox(width: 4),
                                    Text(
                                      status,
                                      style: TextStyle(
                                        fontFamily: 'Poppins',
                                        fontSize: 9.5,
                                        color: status == 'ACTIVE' ? const Color(0xFF065F46) : const Color(0xFF475569),
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 8),
                              const Text(
                                'CooBS',
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 14,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 1.0,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      ...kids,
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      );

  Widget balance(double v) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            d.hide ? '₱ ••••••' : peso(v),
            style: const TextStyle(
              fontFamily: 'Poppins',
              fontSize: 25,
              fontWeight: FontWeight.w500,
              letterSpacing: -0.5,
            ),
          ),
          IconButton(
            tooltip: d.hide ? 'Show balance' : 'Hide balance',
            onPressed: d.toggleHide,
            style: IconButton.styleFrom(
              backgroundColor: Colors.white.withAlpha(35),
              padding: const EdgeInsets.all(6),
              minimumSize: const Size(34, 34),
            ),
            icon: Icon(
              d.hide ? Icons.visibility_off_outlined : Icons.visibility_outlined,
              color: Colors.white,
              size: 19,
            ),
          ),
        ],
      );

  Widget btn(String t, VoidCallback f, {bool dark = false}) => Expanded(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: TextButton(
            onPressed: f,
            style: TextButton.styleFrom(
              backgroundColor: dark ? const Color(0xFF000107).withAlpha(150) : Colors.white.withAlpha(45),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 10),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
                side: BorderSide(color: Colors.white.withAlpha(50), width: 0.8),
              ),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (t == 'Transfer') ...[
                  const Icon(Icons.swap_horiz_rounded, size: 18, color: Colors.white),
                  const SizedBox(width: 6),
                ],
                Text(
                  t,
                  style: const TextStyle(
                    fontFamily: 'Poppins',
                    fontWeight: FontWeight.w600,
                    fontSize: 13.5,
                    letterSpacing: 0.2,
                  ),
                ),
              ],
            ),
          ),
        ),
      );

  void _showNicknameDialog(BuildContext context, String accountNo, String currentNick) {
    final controller = TextEditingController(text: currentNick);
    const presets = [
      'Daily Allowance',
      'Emergency Fund',
      'Vacation & Travel',
      'Bills & Utilities',
      'Savings Goal',
    ];

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: kSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(ctx).viewInsets.bottom + 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(7),
                      decoration: BoxDecoration(
                        color: kInBg,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(Icons.edit_note_rounded, size: 20, color: kBlue),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      'Account Nickname & Goal',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: kInk,
                      ),
                    ),
                  ],
                ),
                IconButton(
                  icon: const Icon(Icons.close_rounded),
                  onPressed: () => Navigator.pop(ctx),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              'Personalize $accountNo with a goal or custom label',
              style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: kMut),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              autofocus: true,
              style: TextStyle(fontFamily: 'Poppins', fontSize: 14, color: kInk, fontWeight: FontWeight.w500),
              decoration: InputDecoration(
                hintText: 'e.g. Daily Allowance, Vacation Fund',
                hintStyle: TextStyle(fontFamily: 'Poppins', fontSize: 13, color: kMut),
                filled: true,
                fillColor: kInBg,
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide(color: kLine),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide(color: kLine),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide(color: kBlue, width: 1.5),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'SUGGESTED GOALS',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: kMut,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final p in presets)
                  InkWell(
                    onTap: () => controller.text = p,
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(
                        color: kInBg,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: kLine),
                      ),
                      child: Text(
                        p,
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 11.5, color: kInk, fontWeight: FontWeight.w400),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                if (currentNick.isNotEmpty) ...[
                  Expanded(
                    child: OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        side: BorderSide(color: kLine),
                      ),
                      onPressed: () {
                        Demo.i.setNickname(accountNo, '');
                        Navigator.pop(ctx);
                      },
                      child: Text('Reset', style: TextStyle(fontFamily: 'Poppins', color: kMut, fontWeight: FontWeight.w500)),
                    ),
                  ),
                  const SizedBox(width: 10),
                ],
                Expanded(
                  flex: 2,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 13),
                      backgroundColor: kBlue,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    onPressed: () {
                      Demo.i.setNickname(accountNo, controller.text);
                      Navigator.pop(ctx);
                    },
                    child: const Text('Save Nickname', style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600)),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget acctCard(int i) {
    if (i >= d.accts.length) return const SizedBox.shrink();
    final a = d.accts[i];
    final isChecking = a.type.toUpperCase().contains('CHECKING') || a.type.toUpperCase().contains('CURRENT');
    final isPrimary = a.no == d.primaryNo || i == 0;
    final nick = d.nicknameFor(a.no);

    Widget buildNoWithEdit() => InkWell(
          onTap: () => _showNicknameDialog(context, a.no, nick),
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  a.no,
                  style: const TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 13.5,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1.6,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(width: 4),
                Icon(
                  Icons.edit_outlined,
                  size: 13,
                  color: Colors.white.withAlpha(210),
                ),
              ],
            ),
          ),
        );

    if (isChecking || i == 1) {
      final baseLabel = isChecking ? 'Current' : a.type;
      final displayLabel = nick.isNotEmpty
          ? (isPrimary ? '$nick · primary' : nick)
          : (isPrimary ? '$baseLabel · primary' : baseLabel);
      return shell(
        const [Color(0xFF0A192F), Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF38BDF8)],
        displayLabel,
        'ACTIVE',
        [
          const SizedBox(height: 8),
          Row(
            children: [
              _emvChip(chipType: 'silver'),
              const SizedBox(width: 8),
              Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
              const Spacer(),
              buildNoWithEdit(),
            ],
          ),
          const SizedBox(height: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Available balance', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: Colors.white70, fontWeight: FontWeight.w400)),
              const SizedBox(height: 2),
              balance(a.bal),
            ],
          ),
          const SizedBox(height: 8),
          Row(children: [
            btn('Transfer', () => go(RemittanceScreen(from: i))),
          ]),
        ],
        painter: const _CurrentCardPainter(glowColor: Color(0xFF38BDF8)),
        accentGlow: const Color(0xFF2563EB),
      );
    } else if (i == 2) {
      final displayLabel = nick.isNotEmpty
          ? (isPrimary ? '$nick · primary' : nick)
          : (isPrimary ? '${a.type} · primary' : a.type);
      return shell(
        const [Color(0xFF042F24), Color(0xFF065F46), Color(0xFF059669), Color(0xFF10B981)],
        displayLabel,
        'ACTIVE',
        [
          const SizedBox(height: 8),
          Row(
            children: [
              _emvChip(chipType: 'silver'),
              const SizedBox(width: 8),
              Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
              const Spacer(),
              buildNoWithEdit(),
            ],
          ),
          const SizedBox(height: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Available balance', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: Colors.white70, fontWeight: FontWeight.w400)),
              const SizedBox(height: 2),
              balance(a.bal),
            ],
          ),
          const SizedBox(height: 8),
          Row(children: [
            btn('Transfer', () => go(RemittanceScreen(from: i))),
          ]),
        ],
        painter: const _CurrentCardPainter(glowColor: Color(0xFF34D399)),
        accentGlow: const Color(0xFF059669),
      );
    } else {
      final displayLabel = nick.isNotEmpty
          ? (isPrimary ? '$nick · primary' : nick)
          : (isPrimary ? '${a.type} · primary' : a.type);
      return shell(
        const [Color(0xFF003847), Color(0xFF006B7A), Color(0xFF0091AE), Color(0xFF00B4D8)],
        displayLabel,
        'ACTIVE',
        [
          const SizedBox(height: 8),
          Row(
            children: [
              _emvChip(chipType: 'gold'),
              const SizedBox(width: 8),
              Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
              const Spacer(),
              buildNoWithEdit(),
            ],
          ),
          const SizedBox(height: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Available balance', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: Colors.white70, fontWeight: FontWeight.w400)),
              const SizedBox(height: 2),
              balance(a.bal),
            ],
          ),
          const SizedBox(height: 8),
          Row(children: [
            btn('Transfer', () => go(RemittanceScreen(from: i))),
          ]),
        ],
        painter: const _CardAuroraPainter(glowColor: Color(0xFF00B4D8)),
        accentGlow: const Color(0xFF00B4D8),
      );
    }
  }


  Widget loanCard() {
    final l = d.loan;
    final balanceAmt = l?.balance ?? 50000.0;
    final refStr = l?.ref ?? 'LN_10000006';
    return shell(
      const [Color(0xFF0A0F1D), Color(0xFF1E293B), Color(0xFF334155)],
      'Loan · Credit Line',
      'ACTIVE',
      [
        const SizedBox(height: 8),
        Row(
          children: [
            _emvChip(chipType: 'bronze'),
            const SizedBox(width: 8),
            Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
            const Spacer(),
            Text(
              refStr,
              style: const TextStyle(
                fontFamily: 'Poppins',
                fontSize: 13.5,
                fontWeight: FontWeight.w600,
                letterSpacing: 1.6,
                color: Colors.white,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Outstanding balance', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: Colors.white70, fontWeight: FontWeight.w400)),
            const SizedBox(height: 2),
            balance(balanceAmt),
          ],
        ),
        const SizedBox(height: 8),
        Row(children: [
          btn('My Bill', () => go(const MyBillPage()), dark: true),
          btn('Details', () => go(const LoanDetailsPage()), dark: true),
        ]),
      ],
      painter: const _LoanCardPainter(glowColor: Color(0xFFF59E0B)),
      accentGlow: const Color(0xFFF59E0B),
    );
  }

  Widget _buildQuickSendBeneficiaries() {
    final bensList = d.bens.isNotEmpty
        ? d.bens
        : const [
            Ben('Maria Santos', 'COOBS · ACC_10000002', '+63 917 000 1111', accountNumber: 'ACC_10000002', bankCode: 'COOBS'),
            Ben('Juan Dela Cruz', 'COOBS · ACC_10000004', '+63 918 222 3333', accountNumber: 'ACC_10000004', bankCode: 'COOBS'),
            Ben('Jose Bautista', 'COOBS · ACC_10000008', '+63 918 222 3333', accountNumber: 'ACC_10000008', bankCode: 'COOBS'),
            Ben('Andrea', 'COOBS · ACC_10000007', '+63 919 444 5555', accountNumber: 'ACC_10000007', bankCode: 'COOBS'),
          ];

    final addBtn = SizedBox(
      width: 58,
      child: InkWell(
        onTap: () async {
          final added = await Navigator.push<bool>(
            context,
            MaterialPageRoute(builder: (_) => const AddBeneficiaryPage()),
          );
          if (added == true) {
            d.load();
          }
        },
        borderRadius: BorderRadius.circular(18),
        child: Column(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: kDark ? const Color(0xFF16202E) : const Color(0xFFF1F5F9),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: kDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                  width: 1.0,
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withAlpha(kDark ? 14 : 5),
                    blurRadius: 4,
                    offset: const Offset(0, 1),
                  ),
                ],
              ),
              child: Icon(
                Icons.add_rounded,
                size: 22,
                color: kDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Add',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: kMut,
              ),
            ),
          ],
        ),
      ),
    );

    final benWidgets = <Widget>[
      for (final b in bensList)
        Builder(
          builder: (context) {
            final rawBank = b.bankCode ?? (b.rel.contains('·') ? b.rel.split('·').first.trim() : 'COOBS');
            final extractedAccount = b.accountNumber ?? (b.rel.contains('·') ? b.rel.split('·').last.trim() : '');
            final rawAccount = normalizeAccountNumber(extractedAccount, rawBank);

            final cleanName = b.name.replaceAll(RegExp(r'\([^)]*\)'), '').trim();
            final words = cleanName.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).toList();
            final firstName = words.isNotEmpty ? words.first : b.name;
            final initials = words.take(2).map((w) {
              final cleanWord = w.replaceAll(RegExp(r'[^A-Za-z]'), '');
              return cleanWord.isNotEmpty ? cleanWord[0].toUpperCase() : '';
            }).join();

            double? lastAmount;
            for (final a in d.accts) {
              for (final tx in a.tx) {
                if (tx.amt < 0 && (tx.label.contains(rawAccount) || tx.label.contains(b.name))) {
                  lastAmount = -tx.amt;
                  break;
                }
              }
              if (lastAmount != null) break;
            }

            return SizedBox(
              width: 58,
              child: InkWell(
                onTap: () => go(
                  RemittanceScreen(
                    from: d.cur < d.accts.length ? d.cur : 0,
                    initialDest: rawAccount,
                    initialBank: rawBank,
                    initialRemarks: 'Transfer to ${b.name}',
                    initialAmount: lastAmount,
                  ),
                ),
                borderRadius: BorderRadius.circular(18),
                child: Column(
                  children: [
                    Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        color: kDark ? const Color(0xFF16202E) : const Color(0xFFF1F5F9),
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(
                          color: kDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                          width: 1.0,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withAlpha(kDark ? 14 : 5),
                            blurRadius: 4,
                            offset: const Offset(0, 1),
                          ),
                        ],
                      ),
                      child: Center(
                        child: initials.isNotEmpty
                            ? Text(
                                initials,
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: kDark ? const Color(0xFFE2E8F0) : const Color(0xFF334155),
                                ),
                              )
                            : Icon(
                                Icons.person_outline_rounded,
                                size: 20,
                                color: kDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
                              ),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      firstName,
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        color: kInk,
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
    ];

    final allItems = <Widget>[addBtn, ...benWidgets];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 18),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Send again',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: kInk,
              ),
            ),
            InkWell(
              onTap: () => go(BeneficiariesPage(onBack: () => Navigator.pop(context))),
              borderRadius: BorderRadius.circular(20),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                decoration: BoxDecoration(
                  color: kSurface,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: kLine, width: 0.8),
                ),
                child: Text(
                  'Manage',
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: kBlue,
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        LayoutBuilder(
          builder: (context, constraints) {
            const itemWidth = 58.0;
            final availableWidth = constraints.maxWidth;
            final totalWidth = allItems.length * itemWidth;

            // When items fit comfortably across the width, space them evenly so
            // the first item aligns with 'Send again' (left) and the last item aligns with 'Manage' (right)
            final canSpaceBetween = allItems.length >= 4 &&
                totalWidth <= availableWidth &&
                ((availableWidth - totalWidth) / (allItems.length - 1)) >= 8 &&
                ((availableWidth - totalWidth) / (allItems.length - 1)) <= 52;

            if (canSpaceBetween) {
              return Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: allItems,
              );
            }

            return SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              clipBehavior: Clip.none,
              child: Row(
                children: [
                  for (var i = 0; i < allItems.length; i++) ...[
                    if (i > 0) const SizedBox(width: 14),
                    allItems[i],
                  ],
                ],
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _buildCustomer360FinancialPulse() {
    double spentTotal = 0;
    double transferShare = 0;
    for (final a in d.accts) {
      for (final t in a.tx) {
        if (t.amt < 0) {
          final val = t.amt.abs();
          spentTotal += val;
          if (t.label.toLowerCase().contains('transfer') || t.label.toLowerCase().contains('padala')) {
            transferShare += val;
          }
        }
      }
    }
    if (spentTotal == 0) {
      spentTotal = 24646.00;
      transferShare = 16800.00;
    }
    final transferRatio = (transferShare / spentTotal).clamp(0.2, 0.9);

    return Padding(
      padding: const EdgeInsets.only(top: 20),
      child: InkWell(
        onTap: () => go(const ScoresPage()),
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
          decoration: BoxDecoration(
            color: kDark ? const Color(0xFF0C141F).withAlpha(220) : Colors.white.withAlpha(225),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
              width: 0.9,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withAlpha(kDark ? 16 : 6),
                blurRadius: 10,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(7),
                        decoration: BoxDecoration(
                          color: kInBg,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(Icons.insights_rounded, size: 16, color: kNavyFg),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Financial Pulse',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: kInk,
                        ),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      Text(
                        'View breakdown',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: kBlue,
                        ),
                      ),
                      const SizedBox(width: 2),
                      Icon(Icons.chevron_right_rounded, size: 16, color: kBlue),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Monthly spending',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 11,
                      fontWeight: FontWeight.w400,
                      color: kMut,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    peso(spentTotal),
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 22,
                      fontWeight: FontWeight.w500,
                      color: kInk,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: Row(
                  children: [
                    Expanded(
                      flex: (transferRatio * 100).round(),
                      child: Container(height: 5, color: kBlue),
                    ),
                    const SizedBox(width: 2),
                    Expanded(
                      flex: ((1.0 - transferRatio) * 100).round(),
                      child: Container(
                        height: 5,
                        color: kDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Wrap(
                alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center,
                runSpacing: 4,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: BoxDecoration(shape: BoxShape.circle, color: kBlue),
                      ),
                      const SizedBox(width: 5),
                      Text(
                        'Transfers (${(transferRatio * 100).round()}%)',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 11,
                          color: kMut,
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: kDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
                        ),
                      ),
                      const SizedBox(width: 5),
                      Text(
                        'Withdrawals & Other (${((1.0 - transferRatio) * 100).round()}%)',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 11,
                          color: kMut,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              InkWell(
                onTap: () => go(const GeoDealsPage()),
                borderRadius: BorderRadius.circular(12),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: kInBg,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: kLine.withAlpha(kDark ? 40 : 100)),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.local_offer_outlined, size: 16, color: kBlue),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '25 partner discounts in Makati & Mandaluyong tailored to your habits',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                            color: kInk,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Icon(Icons.arrow_forward_ios_rounded, size: 11, color: kMut),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Interactive Liquid Crystal Display promotional card on the home screen.
  Widget _buildPromoAdBanner() {
    return Container(
      margin: const EdgeInsets.only(top: 14),
      decoration: BoxDecoration(
        color: kDark ? const Color(0xFF0C141F) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: kDark
              ? const Color(0xFF0091AE).withAlpha(80)
              : const Color(0xFF0091AE).withAlpha(60),
          width: 1.0,
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0091AE).withAlpha(kDark ? 25 : 12),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => AdService.showAd(context),
          onLongPress: () => _showAdPickerSheet(context),
          borderRadius: BorderRadius.circular(16),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: const Color(0xFF0091AE).withAlpha(kDark ? 45 : 25),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: const Color(0xFF0091AE).withAlpha(70),
                      width: 0.8,
                    ),
                  ),
                  child: const Icon(Icons.campaign_rounded, size: 20, color: Color(0xFF0091AE)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            'Featured Announcement',
                            style: TextStyle(
                              fontFamily: 'Poppins',
                              fontSize: 12.5,
                              fontWeight: FontWeight.w600,
                              color: kInk,
                            ),
                          ),
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                            decoration: BoxDecoration(
                              color: const Color(0xFF0091AE).withAlpha(kDark ? 40 : 25),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text(
                              'LCD DISPLAY',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 8.5,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF0091AE),
                                letterSpacing: 0.5,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Sit, bank, and relax with CooBS · Tap to view',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 11,
                          color: kMut,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded, size: 20, color: Color(0xFF94A3B8)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showAdPickerSheet(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: kSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Ad Display Tester (Debug)',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: kInk,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Select an ad banner or roll with the live probability engine',
                style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: kMut),
              ),
              const SizedBox(height: 14),
              ListTile(
                leading: const Icon(Icons.casino_outlined, color: Color(0xFF0091AE)),
                title: const Text('Live Probability Roll', style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600)),
                subtitle: const Text('1/1000 Duo, 1/2000 Umamusume, 1997/2000 Solo', style: TextStyle(fontSize: 11)),
                onTap: () {
                  Navigator.pop(ctx);
                  AdService.showAd(context);
                },
              ),
              ListTile(
                leading: const Icon(Icons.star_rounded, color: Color(0xFFF59E0B)),
                title: const Text('Umamusume Pretty Derby', style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600)),
                subtitle: const Text('1 in 2,000 Ultra Rare Easter Egg', style: TextStyle(fontSize: 11)),
                onTap: () {
                  Navigator.pop(ctx);
                  AdService.showAd(context, forcedAd: AdItem.umamusume);
                },
              ),
              ListTile(
                leading: const Icon(Icons.groups_outlined, color: Color(0xFF0091AE)),
                title: const Text('CooBS Duo (Two Members)', style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600)),
                subtitle: const Text('1 in 1,000 Lucky Draw', style: TextStyle(fontSize: 11)),
                onTap: () {
                  Navigator.pop(ctx);
                  AdService.showAd(context, forcedAd: AdItem.duo);
                },
              ),
              ListTile(
                leading: const Icon(Icons.person_outline, color: Color(0xFF0091AE)),
                title: const Text('CooBS Solo (Member on Sofa)', style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600)),
                subtitle: const Text('Standard Most Frequent Edition', style: TextStyle(fontSize: 11)),
                onTap: () {
                  Navigator.pop(ctx);
                  AdService.showAd(context, forcedAd: AdItem.solo);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNextBillSection(dynamic nb) {
    if (nb == null) {
      return Container(
        margin: const EdgeInsets.only(top: 16),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        decoration: BoxDecoration(
          color: kDark ? const Color(0xFF0C141F).withAlpha(220) : Colors.white.withAlpha(225),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
            width: 1.0,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withAlpha(kDark ? 16 : 6),
              blurRadius: 10,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: const Color(0xFF10B981).withAlpha(kDark ? 40 : 25),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.check_circle_rounded, color: Color(0xFF10B981), size: 22),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'All bills are fully paid',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: kInk,
                    ),
                  ),
                  Text(
                    'No upcoming loan installment due at this time.',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 12,
                      color: kMut,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    final remaining = r2(nb.amt - nb.paid);
    final dueDateText = fmt(nb.due);

    return Container(
      margin: const EdgeInsets.only(top: 16),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: kDark ? const Color(0xFF0C141F).withAlpha(220) : Colors.white.withAlpha(235),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 1.0,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(kDark ? 20 : 8),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: kBlue.withAlpha(kDark ? 35 : 20),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(Icons.receipt_long_rounded, size: 16, color: kBlue),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    'Next Bill to pay',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: kMut,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: kDark ? const Color(0xFF16202E) : const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: kLine.withAlpha(kDark ? 40 : 120),
                    width: 0.8,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.event_available_rounded, size: 13, color: kBlue),
                    const SizedBox(width: 5),
                    Text(
                      'Due $dueDateText',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: kInk,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            peso(remaining),
            style: TextStyle(
              fontFamily: 'Poppins',
              fontSize: 28,
              fontWeight: FontWeight.w500,
              color: kInk,
              letterSpacing: -0.6,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            'Principal & interest installment',
            style: TextStyle(
              fontFamily: 'Poppins',
              fontSize: 11.5,
              color: kMut,
            ),
          ),
          const SizedBox(height: 16),
          Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: () => showModalBottomSheet(
                context: context,
                isScrollControlled: true,
                builder: (_) => const PayAdvanceSheet(),
              ),
              borderRadius: BorderRadius.circular(14),
              child: Container(
                width: double.infinity,
                height: 46,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: kDark
                        ? [const Color(0xFF2563EB), const Color(0xFF1D4ED8)]
                        : [kNavy, const Color(0xFF1E293B)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(14),
                  boxShadow: [
                    BoxShadow(
                      color: (kDark ? const Color(0xFF2563EB) : kNavy).withAlpha(kDark ? 70 : 40),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.flash_on_rounded, size: 17, color: Colors.white),
                    const SizedBox(width: 8),
                    const Text(
                      'Pay in Advance',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                        letterSpacing: 0.1,
                      ),
                    ),
                    const SizedBox(width: 6),
                    const Icon(Icons.arrow_forward_rounded, size: 15, color: Colors.white70),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Listenable.merge([d, ThemeController.i]),
        builder: (context, _) {
          if (d.state == LoadState.loading) return const _Skeleton();
          if (d.state == LoadState.offline) return _Offline(d.load);
          final failed = d.state == LoadState.partial && d.unavailable.contains('recentTransactions');
          final tx = d.curTx, l = d.loan;
          final nb = l?.next;
          final isLoan = d.isLoanPage;
          final pageCount = (d.accts.isEmpty ? 1 : d.accts.length) + 1;
          return RefreshIndicator(
            onRefresh: () => d.load(),
            child: ListView(
              padding: const EdgeInsets.only(top: 4, bottom: 16),
              children: [
                SizedBox(
                  height: 272,
                  child: PageView(
                    controller: pc,
                    clipBehavior: Clip.none,
                    onPageChanged: (idx) {
                      setState(() {
                        d.setCur(idx);
                      });
                    },
                    children: [
                      if (d.accts.isEmpty)
                        shell(
                          const [Color(0xFF004E64), Color(0xFF0091AE), Color(0xFF25A2BE)],
                          'Savings',
                          'ACTIVE',
                          [
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                _emvChip(chipType: 'gold'),
                                const SizedBox(width: 8),
                                Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
                                const Spacer(),
                                Text(
                                  d.primaryNo.isNotEmpty ? d.primaryNo : 'ACC_10000001',
                                  style: const TextStyle(
                                    fontFamily: 'Poppins',
                                    fontSize: 13.5,
                                    fontWeight: FontWeight.w600,
                                    letterSpacing: 1.6,
                                    color: Colors.white,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text(
                                  'Available balance',
                                  style: TextStyle(
                                    fontFamily: 'Poppins',
                                    fontSize: 11,
                                    color: Colors.white70,
                                    fontWeight: FontWeight.w400,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                balance(0.0),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Row(children: [
                              btn('Transfer', () => go(const RemittanceScreen(from: 0))),
                            ]),
                          ],
                          painter: const _CardAuroraPainter(glowColor: Color(0xFF00B4D8)),
                          accentGlow: const Color(0xFF0091AE),
                        )
                      else
                        for (var i = 0; i < d.accts.length; i++) acctCard(i),
                      loanCard(),
                    ],
                  ),
                ),
                if (pageCount > 1)
                  Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                    for (var i = 0; i < pageCount; i++)
                      Container(
                          margin: const EdgeInsets.fromLTRB(3, 4, 3, 0), width: d.cur == i ? 18 : 7, height: 7,
                          decoration: BoxDecoration(color: d.cur == i ? kInk : kDot, borderRadius: BorderRadius.circular(4))),
                  ]),
              if (d.kyc != 'VERIFIED')
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
                  child: Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: kDark
                          ? (d.kyc == 'PENDING_REVIEW' ? const Color(0xFF2E2413) : const Color(0xFF331A1A))
                          : (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFFEF3C7) : const Color(0xFFFEE2E2)),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: kDark
                            ? (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFB45309) : const Color(0xFFDC2626))
                            : (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFF59E0B) : const Color(0xFFEF4444)),
                        width: 0.8,
                      ),
                    ),
                    child: Row(children: [
                      Icon(
                        d.kyc == 'PENDING_REVIEW' ? Icons.hourglass_top : Icons.verified_user_outlined,
                        color: kDark
                            ? (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFFBBF24) : const Color(0xFFF87171))
                            : (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFB45309) : const Color(0xFFB91C1C)),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(
                            d.kyc == 'PENDING_REVIEW' ? 'KYC Under Review' : 'Identity Verification Required',
                            style: TextStyle(
                              fontWeight: FontWeight.w700,
                              fontSize: 13,
                              color: kDark
                                  ? (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFFDE68A) : const Color(0xFFFECACA))
                                  : Colors.black87,
                            ),
                          ),
                          Text(
                            d.kyc == 'PENDING_REVIEW' ? 'Your verification is being reviewed by a branch teller.' : 'Complete your KYC intake to activate higher transfer limits.',
                            style: TextStyle(
                              fontSize: 11,
                              color: kDark
                                  ? (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFFCD34D) : const Color(0xFFFCA5A5))
                                  : Colors.black54,
                            ),
                          ),
                        ]),
                      ),
                      TextButton(
                        onPressed: () => go(const KycIntakeScreen()),
                        child: Text(
                          d.kyc == 'PENDING_REVIEW' ? 'Check' : 'Verify',
                          style: TextStyle(
                            fontWeight: FontWeight.w700,
                            color: kDark
                                ? (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFFBBF24) : const Color(0xFFF87171))
                                : (d.kyc == 'PENDING_REVIEW' ? const Color(0xFFB45309) : const Color(0xFFB91C1C)),
                          ),
                        ),
                      ),
                    ]),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  if (isLoan && l != null)
                    _buildNextBillSection(nb),
                  if (!isLoan) ...[
                    _buildQuickSendBeneficiaries(),
                    PureSilverEntry(store: shop),
                    _buildCustomer360FinancialPulse(),
                    _buildPromoAdBanner(),
                  ],
                  Padding(
                    padding: const EdgeInsets.only(top: 24, bottom: 10),
                    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                      Text(
                        'Recent transactions',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: kInk,
                        ),
                      ),
                      if (tx.isNotEmpty)
                        InkWell(
                          onTap: () => go(const SeeAllPage()),
                          borderRadius: BorderRadius.circular(20),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                            decoration: BoxDecoration(
                              color: kSurface,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(color: kLine, width: 0.8),
                            ),
                            child: Text(
                              'See all',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                                color: kBlue,
                              ),
                            ),
                          ),
                        ),
                    ]),
                  ),
                  failed
                      ? _Warn(d.load)
                      : tx.isEmpty
                      ? Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 40),
                          decoration: BoxDecoration(
                            color: kSurface,
                            borderRadius: BorderRadius.circular(18),
                            border: Border.all(color: kLine, width: 0.8),
                          ),
                          child: Column(children: [
                            Text('Nothing to Show', style: TextStyle(fontFamily: 'Poppins', fontSize: 15, fontWeight: FontWeight.w500, color: kFaint)),
                            const SizedBox(height: 4),
                            Text('No transactions yet', style: TextStyle(fontFamily: 'Poppins', fontSize: 13, fontWeight: FontWeight.w400, color: kFaint)),
                          ]))
                      : txCard(tx.take(5).toList()),
                  const SizedBox(height: 16),
                  if (isLoan && l != null)
                    Center(child: TextButton(onPressed: d.resetLoan, child: const Text('Reset loan (tester only)'))),
                  const SizedBox(height: 20),
                ]),
              ),
            ]),
          );
        },
      );
  }

class SeeAllPage extends StatelessWidget {
  const SeeAllPage({super.key});
  @override
  Widget build(BuildContext context) {
    final d = Demo.i;
    final g = <DateTime, List<Tx>>{};
    for (final x in d.curTx) { g.putIfAbsent(x.date, () => []).add(x); }
    final tagLabel = d.isLoanPage
        ? 'LOAN'
        : (d.cur < d.accts.length ? d.accts[d.cur].type.toUpperCase() : 'ACCOUNT');
    return Pg('Recent transactions', [
      for (final e in g.entries) ...[
        Padding(
          padding: const EdgeInsets.only(top: 14, bottom: 8),
          child: Center(
            child: Text(
              fmt(e.key),
              style: TextStyle(
                fontFamily: 'Poppins',
                fontWeight: FontWeight.w500,
                fontSize: 12,
                color: kMut,
              ),
            ),
          ),
        ),
        txCard(e.value),
      ],
    ], chip: tag(tagLabel, d.isLoanPage ? 2 : (d.cur % 2)));
  }
}


class _Skeleton extends StatelessWidget {
  const _Skeleton();
  Widget bar(double h, [double? w]) => Container(height: h, width: w, decoration: BoxDecoration(color: kSkel, borderRadius: BorderRadius.circular(14)));
  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        physics: const NeverScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          bar(230),
          const SizedBox(height: 18),
          bar(18, 160),
          const SizedBox(height: 10),
          bar(140),
        ]),
      );
}

class _Offline extends StatelessWidget {
  final VoidCallback retry;
  const _Offline(this.retry);
  @override
  Widget build(BuildContext context) => ServiceUnavailableView(
        isEmbedded: true,
        onRetry: retry,
      );
}

class _Warn extends StatelessWidget {
  final VoidCallback retry;
  const _Warn(this.retry);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: kDark ? const Color(0xFF2E2413) : const Color(0xFFFDF0D5),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: kDark ? const Color(0xFFB45309) : const Color(0xFFF59E0B),
            width: 0.8,
          ),
        ),
        child: Row(children: [
          Expanded(
            child: Text(
              'Recent transactions could not be loaded.',
              style: TextStyle(color: kDark ? const Color(0xFFFDE68A) : const Color(0xFF6B4700)),
            ),
          ),
          TextButton(onPressed: retry, child: Text('Retry', style: TextStyle(color: kBlue, fontWeight: FontWeight.bold))),
        ]),
      );
}

class _CardAuroraPainter extends CustomPainter {
  final Color glowColor;
  const _CardAuroraPainter({this.glowColor = const Color(0xFF00B4D8)});

  @override
  void paint(Canvas canvas, Size size) {
    // 1. Ambient soft radial gradient blooms
    final paintGlow1 = Paint()
      ..shader = RadialGradient(
        colors: [glowColor.withAlpha(90), Colors.transparent],
        stops: const [0.0, 1.0],
      ).createShader(Rect.fromCircle(center: Offset(size.width * 0.8, size.height * 0.2), radius: 110));
    canvas.drawCircle(Offset(size.width * 0.8, size.height * 0.2), 110, paintGlow1);

    final paintGlow2 = Paint()
      ..shader = RadialGradient(
        colors: [const Color(0xFF0091AE).withAlpha(65), Colors.transparent],
        stops: const [0.0, 1.0],
      ).createShader(Rect.fromCircle(center: Offset(size.width * 0.15, size.height * 0.9), radius: 95));
    canvas.drawCircle(Offset(size.width * 0.15, size.height * 0.9), 95, paintGlow2);

    // 2. Luminous S-curve glowing light ribbon (inspired by RevoPay inspiration card)
    final path = Path();
    path.moveTo(-20, size.height * 0.12);
    path.cubicTo(
      size.width * 0.38, size.height * 0.05,
      size.width * 0.42, size.height * 0.98,
      size.width + 20, size.height * 0.42,
    );

    // Soft outer neon glow of the light wave
    final waveGlow = Paint()
      ..color = Colors.white.withAlpha(45)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 6.0
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5.0);
    canvas.drawPath(path, waveGlow);

    // Crisp inner light line
    final waveLine = Paint()
      ..color = Colors.white.withAlpha(150)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;
    canvas.drawPath(path, waveLine);
  }

  @override
  bool shouldRepaint(covariant _CardAuroraPainter oldDelegate) => oldDelegate.glowColor != glowColor;
}

class _CurrentCardPainter extends CustomPainter {
  final Color glowColor;
  const _CurrentCardPainter({this.glowColor = const Color(0xFF00B4D8)});

  @override
  void paint(Canvas canvas, Size size) {
    // 1. Soft cybernetic ambient glow
    final paintGlow = Paint()
      ..shader = RadialGradient(
        colors: [glowColor.withAlpha(70), Colors.transparent],
        stops: const [0.0, 1.0],
      ).createShader(Rect.fromCircle(center: Offset(size.width * 0.85, size.height * 0.25), radius: 100));
    canvas.drawCircle(Offset(size.width * 0.85, size.height * 0.25), 100, paintGlow);

    // 2. Concentric geometric radar / circuit rings at top right
    final ringCenter = Offset(size.width * 0.9, size.height * 0.15);
    final ringPaint1 = Paint()
      ..color = Colors.white.withAlpha(35)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;
    canvas.drawCircle(ringCenter, 45, ringPaint1);

    final ringPaint2 = Paint()
      ..color = Colors.white.withAlpha(22)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;
    canvas.drawCircle(ringCenter, 80, ringPaint2);

    final ringPaint3 = Paint()
      ..color = Colors.white.withAlpha(14)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;
    canvas.drawCircle(ringCenter, 120, ringPaint3);

    // 3. Technical holographic laser trace across card
    final laserPaint = Paint()
      ..shader = LinearGradient(
        colors: [
          Colors.transparent,
          Colors.white.withAlpha(120),
          glowColor.withAlpha(160),
          Colors.transparent,
        ],
        stops: const [0.0, 0.45, 0.55, 1.0],
      ).createShader(Rect.fromLTWH(0, size.height * 0.68, size.width, 1.2));
    canvas.drawLine(
      Offset(0, size.height * 0.68),
      Offset(size.width, size.height * 0.68),
      laserPaint,
    );

    // 4. Subtle corner target ticks
    final tickPaint = Paint()
      ..color = Colors.white.withAlpha(45)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;
    canvas.drawLine(Offset(size.width * 0.82, size.height * 0.15), Offset(size.width * 0.98, size.height * 0.15), tickPaint);
    canvas.drawLine(Offset(size.width * 0.9, size.height * 0.07), Offset(size.width * 0.9, size.height * 0.23), tickPaint);
  }

  @override
  bool shouldRepaint(covariant _CurrentCardPainter oldDelegate) => oldDelegate.glowColor != glowColor;
}

class _LoanCardPainter extends CustomPainter {
  final Color glowColor;
  const _LoanCardPainter({this.glowColor = const Color(0xFFF59E0B)});

  @override
  void paint(Canvas canvas, Size size) {
    // 1. Warm amber radial bloom in top right and bottom left
    final paintGlow1 = Paint()
      ..shader = RadialGradient(
        colors: [glowColor.withAlpha(75), Colors.transparent],
        stops: const [0.0, 1.0],
      ).createShader(Rect.fromCircle(center: Offset(size.width * 0.82, size.height * 0.2), radius: 105));
    canvas.drawCircle(Offset(size.width * 0.82, size.height * 0.2), 105, paintGlow1);

    final paintGlow2 = Paint()
      ..shader = RadialGradient(
        colors: [const Color(0xFFD97706).withAlpha(45), Colors.transparent],
        stops: const [0.0, 1.0],
      ).createShader(Rect.fromCircle(center: Offset(size.width * 0.1, size.height * 0.85), radius: 85));
    canvas.drawCircle(Offset(size.width * 0.1, size.height * 0.85), 85, paintGlow2);

    // 2. Elegant diagonal faceted crystal geometric beams
    final facetPaint = Paint()
      ..color = const Color(0xFFFDE68A).withAlpha(30)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;

    final path1 = Path();
    path1.moveTo(size.width * 0.2, 0);
    path1.lineTo(size.width, size.height * 0.8);
    canvas.drawPath(path1, facetPaint);

    final path2 = Path();
    path2.moveTo(size.width * 0.45, 0);
    path2.lineTo(size.width, size.height * 0.55);
    canvas.drawPath(path2, facetPaint);

    final path3 = Path();
    path3.moveTo(0, size.height * 0.35);
    path3.lineTo(size.width * 0.75, size.height);
    canvas.drawPath(path3, facetPaint);

    // 3. Golden luminous accent shimmer line
    final shimmerPaint = Paint()
      ..shader = LinearGradient(
        colors: [
          Colors.transparent,
          const Color(0xFFFBBF24).withAlpha(160),
          Colors.white.withAlpha(180),
          Colors.transparent,
        ],
        stops: const [0.0, 0.4, 0.6, 1.0],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height))
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;

    final shimmerPath = Path();
    shimmerPath.moveTo(size.width * 0.15, 0);
    shimmerPath.lineTo(size.width, size.height * 0.85);
    canvas.drawPath(shimmerPath, shimmerPaint);
  }

  @override
  bool shouldRepaint(covariant _LoanCardPainter oldDelegate) => oldDelegate.glowColor != glowColor;
}

Widget _emvChip({String chipType = 'gold'}) {
  final List<Color> colors;
  final Color borderColor;
  final Color innerBorderColor;

  if (chipType == 'silver') {
    colors = const [
      Color(0xFFE2E8F0),
      Color(0xFF94A3B8),
      Color(0xFF64748B),
    ];
    borderColor = const Color(0xFF94A3B8);
    innerBorderColor = const Color(0xFF475569);
  } else if (chipType == 'bronze') {
    colors = const [
      Color(0xFFFDE68A),
      Color(0xFFD97706),
      Color(0xFF92400E),
    ];
    borderColor = const Color(0xFFB45309);
    innerBorderColor = const Color(0xFF78350F);
  } else {
    // gold
    colors = const [
      Color(0xFFFFDF7A),
      Color(0xFFE5B842),
      Color(0xFFC79824),
    ];
    borderColor = const Color(0xFFD4A328);
    innerBorderColor = const Color(0xFF9E7212);
  }

  return Container(
    width: 32,
    height: 24,
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(5),
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: colors,
      ),
      border: Border.all(color: borderColor, width: 0.6),
      boxShadow: const [
        BoxShadow(
          color: Color(0x33000000),
          blurRadius: 3,
          offset: Offset(0, 1),
        ),
      ],
    ),
    child: Stack(
      children: [
        Center(
          child: Container(
            width: 13,
            height: 14,
            decoration: BoxDecoration(
              border: Border.all(color: innerBorderColor, width: 0.6),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
        Positioned(
          top: 11,
          left: 0,
          right: 0,
          child: Container(height: 0.6, color: innerBorderColor),
        ),
        Positioned(
          left: 15.5,
          top: 0,
          bottom: 0,
          child: Container(width: 0.6, color: innerBorderColor),
        ),
      ],
    ),
  );
}

