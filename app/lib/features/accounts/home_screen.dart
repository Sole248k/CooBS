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

/// Mobile shell: fixed logo header + floating pill bottom bar (icon-only tabs).
class MobileShell extends StatefulWidget {
  const MobileShell({super.key});
  @override
  State<MobileShell> createState() => _MobileShellState();
}

class _MobileShellState extends State<MobileShell> {
  int i = 0;
  // Pay QR (PayQrScreen in tab_screens.dart) is kept but not in the bar for now;
  // re-add it here and in the IndexedStack below if it comes back in scope.
  static const _tabs = [
    (Icons.home_outlined, 'Home'),
    (Icons.leaderboard_outlined, 'Score & Habits'),
    (Icons.notifications_none, 'Notifications'),
    (Icons.people_outline, 'Beneficiaries'),
    (Icons.person_outline, 'Profile'),
  ];
  static const _alerts = 2;

  Widget _navBtn(int k) {
    final sel = i == k, bell = k == _alerts;
    final ico = kDark ? const Color(0xFFE6EAF0) : const Color(0xFF111111);
    final btn = Container(
      width: bell ? 64 : 48,
      height: bell ? 64 : 48,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: bell
            ? (kDark ? const Color(0xFF2B5BD0) : const Color(0xFF14305F))
            : sel
                ? (kDark ? const Color(0xFF33415A) : const Color(0xFFDCDFE3))
                : (kDark ? const Color(0xFF222C3D) : const Color(0xFFF0F1F2)),
      ),
      child: Icon(_tabs[k].$1, size: bell ? 30 : 24, color: bell ? Colors.white : ico),
    );
    return Tooltip(
      message: _tabs[k].$2,
      child: InkResponse(
        onTap: () => setState(() => i = k),
        radius: bell ? 34 : 26,
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          bell
              ? ListenableBuilder(
                  listenable: Demo.i,
                  builder: (context, _) => Badge(
                    isLabelVisible: Demo.i.unread > 0,
                    label: Text('${Demo.i.unread}'),
                    backgroundColor: const Color(0xFFC0392B),
                    offset: const Offset(-2, 2),
                    child: btn,
                  ),
                )
              : btn,
          if (!bell) ...[
            const SizedBox(height: 4),
            Container(width: 4, height: 4, decoration: BoxDecoration(shape: BoxShape.circle, color: sel ? ico : Colors.transparent)),
          ],
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 600),
              child: Column(children: [
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
                  child: IndexedStack(index: i, children: const [
                    HomeScreen(),
                    ScoresPage(tab: true),
                    AlertsScreen(),
                    BeneficiariesPage(tab: true),
                    ProfileScreen(),
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
                margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  color: kSurface,
                  borderRadius: BorderRadius.circular(44),
                  border: Border.all(color: kLine),
                  boxShadow: const [BoxShadow(color: Color(0x14000000), blurRadius: 12, offset: Offset(0, 2))],
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [for (var k = 0; k < _tabs.length; k++) _navBtn(k)],
                ),
              ),
            ),
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
    final a = d.accts[i];
    return shell(i, a.no == d.primaryNo ? '${a.type} · primary' : a.type, 'ACTIVE', [
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

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: d,
        builder: (context, _) {
          if (d.state == LoadState.loading) return const _Skeleton();
          if (d.state == LoadState.offline) return _Offline(d.load);
          final failed = d.state == LoadState.partial && d.unavailable.contains('recentTransactions');
          final tx = d.curTx, l = d.loan;
          final nb = l?.next;
          return ListView(padding: const EdgeInsets.only(top: 4, bottom: 16), children: [
            SizedBox(
              height: 262,
              child: PageView(controller: pc, onPageChanged: d.setCur, children: [acctCard(0), acctCard(1), loanCard()]),
            ),
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              for (var i = 0; i < 3; i++)
                Container(
                    margin: const EdgeInsets.fromLTRB(3, 12, 3, 0), width: d.cur == i ? 18 : 7, height: 7,
                    decoration: BoxDecoration(color: d.cur == i ? kInk : kDot, borderRadius: BorderRadius.circular(4))),
            ]),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (d.cur == 2 && l != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 16),
                    child: nb == null
                        ? const Text('All bills are fully paid.')
                        : Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text('Next Bill to pay', style: TextStyle(fontSize: 12, color: kMut)),
                              Text(peso(r2(nb.amt - nb.paid)), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
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
                    const Text('Recent transactions', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
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
                          Text(d.cur == 2 ? 'Apply for a loan' : 'No transactions yet', style: TextStyle(color: kFaint)),
                        ]))
                    : txCard(tx.take(5).toList()),
                const SizedBox(height: 12),
                if (d.cur == 2 && l != null)
                  Center(child: TextButton(onPressed: d.resetLoan, child: const Text('Reset loan (tester only)'))),
              ]),
            ),
          ]);
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
    final d = Demo.i, a = d.accts[widget.from], to = d.accts[1 - widget.from];
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 22, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(widget.transfer ? 'Transfer' : 'Deposit', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        Text(widget.transfer ? 'From ${a.no} (${peso(a.bal)}) to another bank.' : 'Move money from ${a.no} to your other account.',
            style: TextStyle(color: kMut, fontSize: 13)),
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
        ] else
          dd('Deposit to', ['${to.type} · ${to.no}'], '${to.type} · ${to.no}', (_) {}),
        tf(amt, 'Amount (₱)', k: const TextInputType.numberWithOptions(decimal: true)),
        bigBtn(widget.transfer ? 'Transfer' : 'Deposit', () {
          final v = double.tryParse(amt.text) ?? 0;
          final err = widget.transfer ? d.send(widget.from, bank, acct.text.trim(), v) : d.deposit(widget.from, 1 - widget.from, v);
          if (err != null) { snack(context, err); return; }
          Navigator.pop(context);
          snack(context, widget.transfer ? 'Sent ${peso(v)} to $bank' : 'Deposited ${peso(v)} to ${to.type}');
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
    return Pg('Recent transactions', [
      for (final e in g.entries) ...[
        Padding(padding: const EdgeInsets.only(top: 14, bottom: 8), child: Center(child: Text(fmt(e.key), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)))),
        txCard(e.value),
      ],
    ], chip: tag(const ['CURRENT', 'SAVINGS', 'LOAN'][d.cur], d.cur));
  }
}


class _Skeleton extends StatelessWidget {
  const _Skeleton();
  Widget bar(double h, [double? w]) => Container(height: h, width: w, decoration: BoxDecoration(color: kSkel, borderRadius: BorderRadius.circular(14)));
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
          Icon(Icons.wifi_off, size: 40, color: kMut),
          const SizedBox(height: 14),
          const Text('Service Temporarily Unavailable', textAlign: TextAlign.center, style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          Text("We can't reach the bank right now. Please try again.", textAlign: TextAlign.center, style: TextStyle(color: kMut)),
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
