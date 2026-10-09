import 'package:flutter/material.dart';

import '../../core/storage/secure_storage_service.dart';
import '../../shell/app_shell.dart';
import '../beneficiaries/beneficiary_screens.dart';
import '../kyc/kyc_intake_screen.dart';
import '../remittance/remittance_screen.dart';
import '../loans/loan_screens.dart';
import '../profile/tab_screens.dart';
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

  void _goHome() => setState(() => i = _home);

  @override
  Widget build(BuildContext context) => Scaffold(
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
                      future: SecureStorageService().getUsername(),
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
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(36),
                  border: Border.all(color: const Color(0xFFE6E9E8), width: 0.5),
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
      );
}

/// Round, icon-only nav button. Selected = darker shade + small dot below.
class _NavCircle extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _NavCircle(this.icon, this.label, this.selected, this.onTap);

  @override
  Widget build(BuildContext context) => Semantics(
        button: true,
        selected: selected,
        label: label,
        child: SizedBox(
          width: 44,
          height: 44,
          child: Stack(clipBehavior: Clip.none, alignment: Alignment.center, children: [
            Material(
              color: selected ? const Color(0xFFDADDE1) : const Color(0xFFF4F4F5),
              shape: const CircleBorder(),
              child: InkWell(
                customBorder: const CircleBorder(),
                onTap: onTap,
                child: SizedBox(width: 44, height: 44, child: Icon(icon, size: 20, color: Colors.black)),
              ),
            ),
            if (selected)
              Positioned(
                bottom: -8,
                child: Container(width: 4, height: 4, decoration: const BoxDecoration(color: Colors.black, shape: BoxShape.circle)),
              ),
          ]),
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
  Widget build(BuildContext context) => Semantics(
        button: true,
        selected: selected,
        label: unread > 0 ? 'Alerts, $unread unread' : 'Alerts',
        child: SizedBox(
          width: 60,
          height: 60,
          child: Stack(clipBehavior: Clip.none, children: [
            Material(
              color: kNavy,
              shape: const CircleBorder(),
              child: InkWell(
                customBorder: const CircleBorder(),
                onTap: onTap,
                child: const SizedBox(width: 60, height: 60, child: Icon(Icons.notifications_none, size: 26, color: Colors.white)),
              ),
            ),
            if (unread > 0)
              Positioned(
                top: 2,
                right: 2,
                child: IgnorePointer(
                  child: Container(
                    constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: kRed, borderRadius: BorderRadius.circular(8)),
                    child: Text(unread > 9 ? '9+' : '$unread',
                        style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w700, height: 1)),
                  ),
                ),
              ),
          ]),
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
                  child: Text(status, style: const TextStyle(fontSize: 11, color: kInk, fontWeight: FontWeight.w600))),
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
        btn('Deposit', () => showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => MoneySheet(i, false))),
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
            CircleAvatar(radius: 32, backgroundColor: const Color(0xFFCBD3DC), child: Icon(ic, color: kNavy, size: 26)),
            const SizedBox(height: 8),
            Text(t, textAlign: TextAlign.center, style: const TextStyle(fontSize: 12)),
          ]),
        ),
      );

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: d,
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
                          btn('Deposit', () => showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => const MoneySheet(0, false))),
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
                      decoration: BoxDecoration(color: d.cur == i ? kInk : const Color(0xFFC3CCC7), borderRadius: BorderRadius.circular(4))),
              ]),
              if (d.kyc != 'VERIFIED')
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
                  child: Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: d.kyc == 'PENDING_REVIEW' ? const Color(0xFFFEF3C7) : const Color(0xFFFEE2E2),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: d.kyc == 'PENDING_REVIEW' ? const Color(0xFFF59E0B) : const Color(0xFFEF4444), width: 0.8),
                    ),
                    child: Row(children: [
                      Icon(d.kyc == 'PENDING_REVIEW' ? Icons.hourglass_top : Icons.verified_user_outlined,
                          color: d.kyc == 'PENDING_REVIEW' ? const Color(0xFFB45309) : const Color(0xFFB91C1C)),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(d.kyc == 'PENDING_REVIEW' ? 'KYC Under Review' : 'Identity Verification Required',
                              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: Colors.black87)),
                          Text(d.kyc == 'PENDING_REVIEW' ? 'Your verification is being reviewed by a branch teller.' : 'Complete your KYC intake to activate higher transfer limits.',
                              style: const TextStyle(fontSize: 11, color: Colors.black54)),
                        ]),
                      ),
                      TextButton(
                        onPressed: () => go(const KycIntakeScreen()),
                        child: Text(d.kyc == 'PENDING_REVIEW' ? 'Check' : 'Verify', style: const TextStyle(fontWeight: FontWeight.w700)),
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
                          ? const Text('All bills are fully paid.')
                          : Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                const Text('Next Bill to pay', style: TextStyle(fontSize: 12, color: kMut)),
                                Text(peso(r2(nb.amt - nb.paid)), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
                                Text('Due Date on ${fmt(nb.due)}', style: const TextStyle(fontSize: 12, color: kMut)),
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
                      const Text('Recent transactions', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                      if (tx.isNotEmpty)
                        TextButton(onPressed: () => go(const SeeAllPage()), child: const Text('See all', style: TextStyle(color: kBlue))),
                    ]),
                  ),
                  failed
                      ? _Warn(d.load)
                      : tx.isEmpty
                      ? Container(
                          width: double.infinity, padding: const EdgeInsets.symmetric(vertical: 44),
                          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)),
                          child: Column(children: [
                            const Text('Nothing to Show', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Color(0xFF9AA5AD))),
                            Text(isLoan ? 'Apply for a loan' : 'No transactions yet', style: const TextStyle(color: Color(0xFF9AA5AD))),
                          ]))
                      : txCard(tx.take(5).toList()),
                  const SizedBox(height: 22),
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

class MoneySheet extends StatefulWidget {
  final int from;
  final bool transfer;
  const MoneySheet(this.from, this.transfer, {super.key});
  @override
  State<MoneySheet> createState() => _MoneySheetState();
}

class _MoneySheetState extends State<MoneySheet> {
  final amt = TextEditingController(), acct = TextEditingController();
  String bank = 'BDO';

  @override
  Widget build(BuildContext context) {
    final d = Demo.i;
    final a = widget.from < d.accts.length ? d.accts[widget.from] : (d.accts.isNotEmpty ? d.accts[0] : Acct('Checking', 'ACC-0000', 0, []));
    final otherIndex = d.accts.length > 1 ? (widget.from == 0 ? 1 : 0) : null;
    final to = otherIndex != null ? d.accts[otherIndex] : null;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 22, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(widget.transfer ? 'Transfer' : 'Deposit', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        Text(widget.transfer ? 'From ${a.no} (${peso(a.bal)}) to another bank.' : (to != null ? 'Move money from ${a.no} to your other account.' : 'Cash deposit to ${a.no}.'),
            style: const TextStyle(color: kMut, fontSize: 13)),
        const SizedBox(height: 14),
        if (widget.transfer) ...[
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: OutlinedButton.icon(
              icon: const Icon(Icons.hub_outlined, size: 18),
              label: const Text('Live Distributed Saga Transfer'),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(42),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              onPressed: () {
                Navigator.pop(context);
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const AppShell(initialIndex: 1)),
                );
              },
            ),
          ),
          dd('Destination bank', const ['BDO', 'BPI', 'UnionBank', 'Metrobank', 'Landbank'], bank, (v) => bank = v ?? bank),
          tf(acct, 'Recipient account number', k: TextInputType.number),
        ] else if (to != null) ...[
          dd('Deposit to', ['${to.type} · ${to.no}'], '${to.type} · ${to.no}', (_) {}),
        ] else ...[
          dd('Deposit channel', const ['Cash Deposit (Branch / ATM)', 'Check Deposit', 'Online Bank Transfer'], 'Cash Deposit (Branch / ATM)', (_) {}),
        ],
        tf(amt, 'Amount (₱)', k: const TextInputType.numberWithOptions(decimal: true)),
        bigBtn(widget.transfer ? 'Transfer' : 'Deposit', () {
          final v = double.tryParse(amt.text) ?? 0;
          final err = widget.transfer ? d.send(widget.from, bank, acct.text.trim(), v) : d.deposit(widget.from, otherIndex, v);
          if (err != null) { snack(context, err); return; }
          Navigator.pop(context);
          snack(context, widget.transfer ? 'Sent ${peso(v)} to $bank' : (to != null ? 'Deposited ${peso(v)} to ${to.type}' : 'Deposited ${peso(v)} to ${a.type}'));
        }),
      ]),
    );
  }
}

/// Full recent-transactions list for whichever card is on screen.
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
  Widget bar(double h, [double? w]) => Container(height: h, width: w, decoration: BoxDecoration(color: const Color(0xFFE3E8EC), borderRadius: BorderRadius.circular(14)));
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          bar(250),
          const SizedBox(height: 22),
          bar(18, 160),
          const SizedBox(height: 10),
          bar(160),
        ]),
      );
}

class _Offline extends StatelessWidget {
  final VoidCallback retry;
  const _Offline(this.retry);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(32),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          const Icon(Icons.wifi_off, size: 40, color: kMut),
          const SizedBox(height: 14),
          const Text('Service Temporarily Unavailable', textAlign: TextAlign.center, style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          const Text("We can't reach the bank right now. Please try again.", textAlign: TextAlign.center, style: TextStyle(color: kMut)),
          bigBtn('Retry connection', retry),
        ]),
      );
}

class _Warn extends StatelessWidget {
  final VoidCallback retry;
  const _Warn(this.retry);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(color: const Color(0xFFFDF0D5), borderRadius: BorderRadius.circular(12)),
        child: Row(children: [
          const Expanded(child: Text('Recent transactions could not be loaded.', style: TextStyle(color: Color(0xFF6B4700)))),
          TextButton(onPressed: retry, child: const Text('Retry')),
        ]),
      );
}
