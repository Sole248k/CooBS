import 'package:flutter/material.dart';

import '../../core/storage/secure_storage_service.dart';
import '../../core/theme/theme_controller.dart';
import '../beneficiaries/beneficiary_screens.dart';
import '../kyc/kyc_intake_screen.dart';
import '../remittance/remittance_screen.dart';
import '../loans/loan_screens.dart';
import '../profile/tab_screens.dart';
import '../resilience/service_unavailable_view.dart';
import '../scores/scores_screen.dart';
import 'demo_store.dart';
import 'ui.dart';

/// Mobile shell: fixed logo header + floating pill navbar (icons only).
/// Tabs: Home, Scores & Habits, Alerts (center), Beneficiaries, Profile.
class MobileShell extends StatefulWidget {
  const MobileShell({super.key});
  @override
  State<MobileShell> createState() => _MobileShellState();
}

class _MobileShellState extends State<MobileShell> {
  int i = 0;
  static const _home = 0, _scores = 1, _alerts = 2, _bens = 3, _profile = 4;
  late final Future<String?> _usernameFuture;

  @override
  void initState() {
    super.initState();
    _usernameFuture = SecureStorageService().getUsername();
  }

  void _goHome() => setState(() => i = _home);

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: ThemeController.i,
        builder: (context, _) => Scaffold(
          backgroundColor: kBg,
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 600),
                child: Column(children: [
                  if (i != _scores && i != _bens)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                      logoBox(),
                      Row(children: [
                        AnimatedBuilder(
                          animation: Demo.i,
                          builder: (context, _) => InkWell(
                              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const KycIntakeScreen())),
                              child: kycChip(Demo.i.kyc)),
                        ),
                        const SizedBox(width: 8),
                        FutureBuilder<String?>(
                          future: _usernameFuture,
                          builder: (context, snapshot) {
                            final user = snapshot.data ?? 'john_doe';
                            return Container(
                                padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
                                decoration: BoxDecoration(color: kNavy, borderRadius: BorderRadius.circular(20)),
                                child: Text('@$user', style: const TextStyle(fontFamily: 'Poppins', color: Colors.white, fontSize: 11.5, fontWeight: FontWeight.w500)));
                          },
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
                      ]),
                    ]),
                  ),
                  Expanded(
                    child: IndexedStack(index: i, children: [
                      const HomeScreen(),
                      ScoresPage(onBack: _goHome),
                      AlertsPage(onBack: _goHome),
                      BeneficiariesPage(onBack: _goHome),
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
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  decoration: BoxDecoration(
                    color: kSurface,
                    borderRadius: BorderRadius.circular(36),
                    border: Border.all(color: kLine, width: 0.5),
                    boxShadow: const [BoxShadow(color: Color(0x14000000), blurRadius: 12, offset: Offset(0, 2))],
                  ),
                  child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                    _NavCircle(Icons.home_outlined, 'Home', i == _home, () => setState(() => i = _home)),
                    _NavCircle(Icons.bar_chart_rounded, 'Scores and habits', i == _scores, () => setState(() => i = _scores)),
                    _TransferNavButton(
                      () {
                        final curAccIdx = Demo.i.cur < Demo.i.accts.length ? Demo.i.cur : 0;
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => RemittanceScreen(from: curAccIdx)),
                        );
                      },
                    ),
                    _NavCircle(Icons.groups_outlined, 'Beneficiaries', i == _bens, () => setState(() => i = _bens)),
                    _NavCircle(Icons.person_outline, 'Profile', i == _profile, () => setState(() => i = _profile)),
                  ]),
                ),
              ),
            ),
          ),
        ),
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

/// Center main action button: prominent brand-colored button for Transfer (add button).
class _TransferNavButton extends StatelessWidget {
  final VoidCallback onTap;
  const _TransferNavButton(this.onTap);

  @override
  Widget build(BuildContext context) => Tooltip(
        message: 'Transfer',
        child: Semantics(
          button: true,
          label: 'Transfer money',
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
                  child: Icon(Icons.add_rounded, size: 28, color: Colors.white),
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
          foregroundColor: kInk,
          elevation: 0,
          leading: BackButton(onPressed: onBack ?? () => Navigator.pop(context)),
          title: Text(
            'Notifications',
            style: TextStyle(
              fontFamily: 'Poppins',
              color: kInk,
              fontSize: 16,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        body: const AlertsScreen(),
      );
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final pc = PageController(viewportFraction: .92);
  final d = Demo.i;

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

  Widget shell(int g, String type, String status, List<Widget> kids, {Color? accentGlow}) => Container(
        margin: const EdgeInsets.symmetric(horizontal: 6),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(24),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: grads[g % grads.length],
          ),
          border: Border.all(color: Colors.white.withAlpha(50), width: 1.2),
          boxShadow: [
            BoxShadow(
              color: (accentGlow ?? grads[g % grads.length].last).withAlpha(75),
              blurRadius: 24,
              offset: const Offset(0, 8),
            ),
            BoxShadow(
              color: Colors.black.withAlpha(30),
              blurRadius: 10,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: Stack(
            children: [
              // 1. Organic Glowing Aurora & Luminous Curve
              Positioned.fill(
                child: CustomPaint(
                  painter: _CardAuroraPainter(
                    glowColor: accentGlow ?? grads[g % grads.length].last,
                  ),
                ),
              ),

              // 2. Card Content
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 14, 18, 14),
                child: DefaultTextStyle(
                  style: const TextStyle(fontFamily: 'Poppins', color: Colors.white),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      // Top Row: Category Pill + Status & CooBS Bank badge
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
              fontWeight: FontWeight.w700,
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

  Widget acctCard(int i) {
    if (i >= d.accts.length) return const SizedBox.shrink();
    final a = d.accts[i];
    final g = a.type.toUpperCase().contains('SAVINGS') ? 1 : (a.type.toUpperCase().contains('CHECKING') ? 0 : i % grads.length);
    return shell(
      g,
      a.no == d.primaryNo ? '${a.type} · primary' : a.type,
      'ACTIVE',
      [
        const SizedBox(height: 8),
        Row(
          children: [
            _emvChip(),
            const SizedBox(width: 8),
            Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
            const Spacer(),
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
            balance(a.bal),
          ],
        ),
        const SizedBox(height: 8),
        Row(children: [
          btn('Transfer', () => go(RemittanceScreen(from: i))),
        ]),
      ],
      accentGlow: g == 1 ? const Color(0xFF00B4D8) : const Color(0xFF0091AE),
    );
  }

  Widget loanCard() {
    final l = d.loan;
    if (l == null) {
      return shell(
        2,
        'Loan',
        'INACTIVE',
        [
          const SizedBox(height: 8),
          Row(
            children: [
              _emvChip(),
              const SizedBox(width: 8),
              Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
              const Spacer(),
              const Text(
                'CREDIT LINE',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 1.5,
                  color: Colors.white70,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text('Apply for a loan here', style: TextStyle(fontFamily: 'Poppins', fontSize: 16, fontWeight: FontWeight.w600)),
          const Text('Borrow and be liquid instantly', style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: Colors.white70)),
          const SizedBox(height: 8),
          Row(children: [btn('Apply now', () => go(const LoanApplyPage()), dark: true)]),
          const Center(child: Text('Loans are subject for approval', style: TextStyle(fontFamily: 'Poppins', fontSize: 10, color: Colors.white60))),
        ],
        accentGlow: const Color(0xFF3A506B),
      );
    }
    return shell(
      2,
      'Loan',
      'ACTIVE',
      [
        const SizedBox(height: 8),
        Row(
          children: [
            _emvChip(),
            const SizedBox(width: 8),
            Icon(Icons.contactless_rounded, color: Colors.white.withAlpha(200), size: 20),
            const Spacer(),
            Text(l.ref, style: const TextStyle(fontFamily: 'Poppins', fontSize: 13.5, fontWeight: FontWeight.w600, letterSpacing: 1.6, color: Colors.white)),
          ],
        ),
        const SizedBox(height: 8),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Loaned balance', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: Colors.white70)),
            const SizedBox(height: 2),
            balance(l.balance),
          ],
        ),
        const SizedBox(height: 8),
        Row(children: [
          btn('My Bill', () => go(const MyBillPage()), dark: true),
          btn('Details', () => go(const LoanDetailsPage()), dark: true),
        ]),
      ],
      accentGlow: const Color(0xFF0091AE),
    );
  }

  Widget circ(IconData ic, String t, VoidCallback? f) => InkWell(
        onTap: f,
        borderRadius: BorderRadius.circular(40),
        child: SizedBox(
          width: 100,
          child: Column(children: [
            CircleAvatar(radius: 32, backgroundColor: kDark ? const Color(0xFF082F3B) : const Color(0xFFE0F4F8), child: Icon(ic, color: kNavyFg, size: 26)),
            const SizedBox(height: 8),
            Text(t, textAlign: TextAlign.center, style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w500, color: kInk)),
          ]),
        ),
      );
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
          final pageCount = d.accts.isEmpty ? 1 : d.accts.length;
          return RefreshIndicator(
            onRefresh: () => d.load(),
            child: ListView(padding: const EdgeInsets.only(top: 4, bottom: 16), children: [
              SizedBox(
                height: 262,
                child: PageView(
                  controller: pc,
                  onPageChanged: d.setCur,
                  children: [
                    if (d.accts.isEmpty)
                      shell(
                        0,
                        'Savings',
                        'ACTIVE',
                        [
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              _emvChip(),
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
                        accentGlow: const Color(0xFF0091AE),
                      )
                    else
                      for (var i = 0; i < d.accts.length; i++) acctCard(i),
                  ],
                ),
              ),
              if (pageCount > 1)
                Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  for (var i = 0; i < pageCount; i++)
                    Container(
                        margin: const EdgeInsets.fromLTRB(3, 12, 3, 0), width: d.cur == i ? 18 : 7, height: 7,
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
                    Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: nb == null
                          ? Text('All bills are fully paid.', style: TextStyle(color: kInk))
                          : Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Text('Next Bill to pay', style: TextStyle(fontSize: 12, color: kMut)),
                                Text(peso(r2(nb.amt - nb.paid)), style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: kInk)),
                                Text('Due Date on ${fmt(nb.due)}', style: TextStyle(fontSize: 12, color: kMut)),
                              ]),
                              SizedBox(
                                  width: 150,
                                  child: ElevatedButton(
                                      style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(48), backgroundColor: kNavy),
                                      onPressed: () => showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => const PayAdvanceSheet()),
                                      child: const Text('Pay in Advance'))),
                            ]),
                    ),
                  Padding(
                    padding: const EdgeInsets.only(top: 20, bottom: 10),
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
                  const SizedBox(height: 14),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      circ(Icons.insights_outlined, 'Scores & Habits', () => go(const ScoresPage())),
                      circ(Icons.groups_outlined, 'Beneficiaries', () => go(const BeneficiariesPage())),
                    ],
                  ),
                  if (isLoan && l != null)
                    Center(child: TextButton(onPressed: d.resetLoan, child: const Text('Reset loan (tester only)'))),
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

Widget _emvChip() {
  return Container(
    width: 32,
    height: 24,
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(5),
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          Color(0xFFFFDF7A),
          Color(0xFFE5B842),
          Color(0xFFC79824),
        ],
      ),
      border: Border.all(color: const Color(0xFFD4A328), width: 0.6),
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
              border: Border.all(color: const Color(0xFF9E7212), width: 0.6),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
        Positioned(
          top: 11,
          left: 0,
          right: 0,
          child: Container(height: 0.6, color: const Color(0xFF9E7212)),
        ),
        Positioned(
          left: 15.5,
          top: 0,
          bottom: 0,
          child: Container(width: 0.6, color: const Color(0xFF9E7212)),
        ),
      ],
    ),
  );
}

