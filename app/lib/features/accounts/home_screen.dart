import 'package:flutter/material.dart';

import '../../core/storage/secure_storage_service.dart';
import '../../core/theme/theme_controller.dart';
import '../../shell/app_shell.dart';
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
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                              decoration: BoxDecoration(color: kNavy, borderRadius: BorderRadius.circular(20)),
                              child: Text('@$user', style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)));
                        },
                      ),
                      ]),
                    ]),
                  ),
                  Expanded(
                    child: IndexedStack(index: i, children: [
                      const HomeScreen(),
                      ScoresPage(onBack: _goHome),
                      const AlertsScreen(),
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
                    ListenableBuilder(
                      listenable: Demo.i,
                      builder: (context, _) => _AlertsNavButton(Demo.i.unread, i == _alerts, () => setState(() => i = _alerts)),
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
                    ? (kDark ? const Color(0xFF22324D) : const Color(0xFFE2E8F0))
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
                          ? (kDark ? const Color(0xFF6E9BFF) : const Color(0xFF14305F))
                          : (kDark ? const Color(0xFF8C98A8) : const Color(0xFF64748B)),
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
                      color: kDark ? const Color(0xFF6E9BFF) : const Color(0xFF14305F),
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
            ]),
          ),
        ),
      );
}

/// Center alerts button: always navy (same as the @username chip), with unread badge.
class _AlertsNavButton extends StatelessWidget {
  final int unread;
  final bool selected;
  final VoidCallback onTap;
  const _AlertsNavButton(this.unread, this.selected, this.onTap);

  @override
  Widget build(BuildContext context) => Tooltip(
        message: unread > 0 ? 'Alerts ($unread)' : 'Alerts',
        child: Semantics(
          button: true,
          selected: selected,
          label: unread > 0 ? 'Alerts, $unread unread' : 'Alerts',
          child: SizedBox(
            width: 52,
            height: 52,
            child: Stack(clipBehavior: Clip.none, alignment: Alignment.center, children: [
              Material(
                color: selected
                    ? (kDark ? const Color(0xFF386EE0) : const Color(0xFF0F264A))
                    : (kDark ? const Color(0xFF2B5BD0) : const Color(0xFF14305F)),
                shape: const CircleBorder(),
                elevation: selected ? 3 : 0,
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: onTap,
                  child: const SizedBox(
                    width: 52,
                    height: 52,
                    child: Icon(Icons.notifications_none, size: 24, color: Colors.white),
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
                      color: kDark ? const Color(0xFF6E9BFF) : const Color(0xFF14305F),
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
              if (unread > 0)
                Positioned(
                  top: 0,
                  right: 0,
                  child: IgnorePointer(
                    child: Container(
                      constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      alignment: Alignment.center,
                      decoration: BoxDecoration(color: const Color(0xFFC0392B), borderRadius: BorderRadius.circular(8)),
                      child: Text(unread > 9 ? '9+' : '$unread',
                          style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w700, height: 1)),
                    ),
                  ),
                ),
            ]),
          ),
        ),
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

  Widget shell(int g, String type, String status, List<Widget> kids) => Container(
        margin: const EdgeInsets.symmetric(horizontal: 6),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(22),
            gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: grads[g])),
        child: DefaultTextStyle(
          style: const TextStyle(color: Colors.white),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(12)),
                  child: Text(type.toUpperCase(), style: const TextStyle(fontSize: 11))),
              Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
                  child: Text(status, style: const TextStyle(fontSize: 11, color: Color(0xFF14231B), fontWeight: FontWeight.w600))),
            ]),
            ...kids,
          ]),
        ),
      );

  Widget balance(double v) => Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        Text(d.hide ? '₱ ••••••' : peso(v), style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
        IconButton(
          tooltip: d.hide ? 'Show balance' : 'Hide balance',
          onPressed: d.toggleHide,
          style: IconButton.styleFrom(backgroundColor: Colors.white24),
          icon: Icon(d.hide ? Icons.visibility_off_outlined : Icons.visibility_outlined, color: Colors.white),
        ),
      ]);

  Widget btn(String t, VoidCallback f, {bool dark = false}) => Expanded(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: TextButton(
            onPressed: f,
            style: TextButton.styleFrom(
                backgroundColor: dark ? kNavy : Colors.white24,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14))),
            child: Text(t, style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
        ),
      );

  Widget acctCard(int i) {
    if (i >= d.accts.length) return const SizedBox.shrink();
    final a = d.accts[i];
    final g = a.type.toUpperCase().contains('SAVINGS') ? 1 : (a.type.toUpperCase().contains('CHECKING') ? 0 : i % grads.length);
    return shell(g, a.no == d.primaryNo ? '${a.type} · primary' : a.type, 'ACTIVE', [
      const SizedBox(height: 14),
      const Text('Account number', style: TextStyle(fontSize: 11, color: Colors.white70)),
      Text(a.no, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, letterSpacing: 1.5)),
      const SizedBox(height: 8),
      const Text('Available balance', style: TextStyle(fontSize: 11, color: Colors.white70)),
      balance(a.bal),
      const SizedBox(height: 12),
      Row(children: [
        btn('Transfer', () => go(RemittanceScreen(from: i))),
      ]),
    ]);
  }

  Widget loanCard() {
    final l = d.loan;
    if (l == null) {
      return shell(2, 'Loan', 'INACTIVE', [
        const SizedBox(height: 14),
        const Text('Apply for a loan here', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
        const Text('Borrow and be liquid instantly', style: TextStyle(fontSize: 13)),
        const Spacer(),
        Row(children: [btn('Apply now', () => go(const LoanApplyPage()), dark: true)]),
        const SizedBox(height: 10),
        const Center(child: Text('Loans are subject for approval', style: TextStyle(fontSize: 11))),
      ]);
    }
    return shell(2, 'Loan', 'ACTIVE', [
      const SizedBox(height: 14),
      const Text('Account number', style: TextStyle(fontSize: 11, color: Colors.white70)),
      Text(l.ref, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, letterSpacing: 1.5)),
      const SizedBox(height: 8),
      const Text('Loaned balance', style: TextStyle(fontSize: 11, color: Colors.white70)),
      balance(l.balance),
      const SizedBox(height: 12),
      Row(children: [
        btn('My Bill', () => go(const MyBillPage()), dark: true),
        btn('Details', () => go(const LoanDetailsPage()), dark: true),
      ]),
    ]);
  }

  Widget circ(IconData ic, String t, VoidCallback? f) => InkWell(
        onTap: f,
        borderRadius: BorderRadius.circular(40),
        child: SizedBox(
          width: 100,
          child: Column(children: [
            CircleAvatar(radius: 32, backgroundColor: kDark ? const Color(0xFF222C3D) : const Color(0xFFCBD3DC), child: Icon(ic, color: kNavyFg, size: 26)),
            const SizedBox(height: 8),
            Text(t, textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: kInk)),
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
          final pageCount = d.accts.isEmpty ? 2 : d.accts.length + 1;
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
                      shell(0, 'Savings', 'ACTIVE', [
                        const SizedBox(height: 14),
                        const Text('Account number', style: TextStyle(fontSize: 11, color: Colors.white70)),
                        Text(d.primaryNo.isNotEmpty ? d.primaryNo : 'ACC_10000001', style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, letterSpacing: 1.5)),
                        const SizedBox(height: 8),
                        const Text('Available balance', style: TextStyle(fontSize: 11, color: Colors.white70)),
                        balance(0.0),
                        const SizedBox(height: 12),
                        Row(children: [
                          btn('Transfer', () => go(const RemittanceScreen(from: 0))),
                        ]),
                      ])
                    else
                      for (var i = 0; i < d.accts.length; i++) acctCard(i),
                    loanCard(),
                  ],
                ),
              ),
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
                    padding: const EdgeInsets.only(top: 20, bottom: 6),
                    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                      Text('Recent transactions', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: kInk)),
                      if (tx.isNotEmpty)
                        TextButton(onPressed: () => go(const SeeAllPage()), child: Text('See all', style: TextStyle(color: kBlue))),
                    ]),
                  ),
                  failed
                      ? _Warn(d.load)
                      : tx.isEmpty
                      ? Container(
                          width: double.infinity, padding: const EdgeInsets.symmetric(vertical: 44),
                          decoration: BoxDecoration(color: kSurface, borderRadius: BorderRadius.circular(16)),
                          child: Column(children: [
                            Text('Nothing to Show', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: kFaint)),
                            Text(isLoan ? 'Apply for a loan' : 'No transactions yet', style: TextStyle(color: kFaint)),
                          ]))
                      : txCard(tx.take(5).toList()),
                  const SizedBox(height: 12),
                  Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
                    circ(Icons.insights_outlined, 'Show\nScores & Habits', () => go(const ScoresPage())),
                    circ(Icons.groups_outlined, 'Show\nBeneficiaries', () => go(const BeneficiariesPage())),
                    circ(Icons.credit_card, 'Manage\nCards', () => snack(context, 'Card management coming soon in Phase 2')),
                  ]),
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
        Padding(padding: const EdgeInsets.only(top: 14, bottom: 8), child: Center(child: Text(fmt(e.key), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)))),
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
