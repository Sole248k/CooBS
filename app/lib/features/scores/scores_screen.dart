import 'package:flutter/material.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';

/// One debit transaction: activity, receiver, txn no., date, amount, account used.
class Debit {
  final String activity, receiver, ref;
  final DateTime date;
  final double amt; // positive number; shown as a negative
  final int acct;
  const Debit(this.activity, this.receiver, this.ref, this.date, this.amt, this.acct);
}

/// Live debits for the given account index from the store.
List<Debit> debitsFor(int a) {
  final out = <Debit>[];
  if (Demo.i.accts.isEmpty || a >= Demo.i.accts.length) return out;
  for (final x in Demo.i.accts[a].tx) {
    if (x.amt >= 0) continue;
    final p = x.label.split(' to ');
    final receiver = p.length > 1 ? p.last : (x.ref.isNotEmpty ? x.ref : 'Recipient');
    out.add(Debit(p.first, receiver, x.ref, x.date, -x.amt, a));
  }
  return out;
}

class ScoresPage extends StatefulWidget {
  /// Back action when shown as a navbar tab (null = pop the route).
  final VoidCallback? onBack;
  const ScoresPage({super.key, this.onBack});
  @override
  State<ScoresPage> createState() => _ScoresPageState();
}

class _ScoresPageState extends State<ScoresPage> {
  int acct = 0, page = 0;
  static const per = 6;
  static const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  // Monthly spending in thousands of pesos (mock). Nov/Dec are still empty.
  static const chk = [32.0, 28, 41, 25, 30, 36, 29, 33, 38, 22];
  static const sav = [12.0, 10, 14, 8, 11, 9, 15, 12, 10, 6];

  Widget scoreCard() => Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 28),
          child: Column(children: const [
            Text('832', style: TextStyle(fontSize: 84, fontWeight: FontWeight.w800, color: Color(0xFF1B3F6E), height: 1.1)),
            SizedBox(height: 8),
            Text('Your Credit Score', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Color(0xFF1B3F6E))),
          ]),
        ),
      );

  Widget bar(int i, double h) => Column(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 14, height: sav[i] / 60 * h, color: kRed),
        Container(width: 14, height: chk[i] / 60 * h, color: kBlue),
      ]);

  Widget dot(Color c, String t) => Row(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 10, height: 10, color: c),
        const SizedBox(width: 6),
        Text(t, style: const TextStyle(fontSize: 12, color: kMut)),
        const SizedBox(width: 14),
      ]);

  Widget chart() {
    const h = 170.0;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 16, 12, 14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Spending habits (₱ thousands per month)', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 14),
          SizedBox(
            height: h + 22,
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SizedBox(
                  width: 28, height: h,
                  child: Column(mainAxisAlignment: MainAxisAlignment.spaceBetween, crossAxisAlignment: CrossAxisAlignment.start, children: [
                    for (final t in [60, 40, 20, 0]) Text('$t', style: const TextStyle(fontSize: 11, color: kMut)),
                  ])),
              Expanded(
                child: Column(children: [
                  SizedBox(
                    height: h,
                    child: Stack(children: [
                      for (final f in [0.0, 1 / 3, 2 / 3, 1.0])
                        Positioned(left: 0, right: 0, top: f * (h - 1), child: Container(height: 1, color: const Color(0xFFE6EAE8))),
                      Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                        for (var i = 0; i < 12; i++)
                          Expanded(child: Align(alignment: Alignment.bottomCenter, child: i < chk.length ? bar(i, h) : const SizedBox())),
                      ]),
                    ]),
                  ),
                  const SizedBox(height: 6),
                  Row(children: [
                    for (final m in months) Expanded(child: Text(m, textAlign: TextAlign.center, style: const TextStyle(fontSize: 9, color: kMut))),
                  ]),
                ]),
              ),
            ]),
          ),
          const SizedBox(height: 8),
          Row(children: [dot(kBlue, 'Checking'), dot(kRed, 'Savings')]),
        ]),
      ),
    );
  }

  Widget row(Debit d) {
    final typeName = (d.acct < Demo.i.accts.length) ? Demo.i.accts[d.acct].type : 'Account';
    return ListTile(
        isThreeLine: true,
        leading: const CircleAvatar(backgroundColor: Color(0xFFFBE9E7), child: Icon(Icons.north_east, size: 18, color: kRed)),
        title: Text(d.activity, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
        subtitle: Text('To ${d.receiver}\n${d.ref} · $typeName'),
        trailing: Text('-${peso(d.amt)}', style: const TextStyle(fontWeight: FontWeight.w600, color: kRed)),
      );
  }

  @override
  void initState() {
    super.initState();
    if (Demo.i.state == LoadState.loading) {
      Demo.i.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Demo.i,
      builder: (context, _) {
        final safeAcct = Demo.i.accts.isEmpty ? 0 : (acct < Demo.i.accts.length ? acct : 0);
        final all = debitsFor(safeAcct);
        final pages = all.isEmpty ? 1 : (all.length / per).ceil();
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        final g = <DateTime, List<Debit>>{};
        for (final d in all.skip(page * per).take(per)) {
          g.putIfAbsent(d.date, () => []).add(d);
        }
        return Pg('Scores and Habits', [
          scoreCard(),
          const SizedBox(height: 4),
          chart(),
          Padding(
            padding: const EdgeInsets.only(top: 20, bottom: 4),
            child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              const Text('Debit transaction history', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Color(0xFF1B3F6E))),
              PopupMenuButton<int>(
                onSelected: (v) => setState(() { acct = v; page = 0; }),
                itemBuilder: (_) => <PopupMenuEntry<int>>[
                  for (var i = 0; i < Demo.i.accts.length; i++)
                    PopupMenuItem(value: i, child: Text(Demo.i.accts[i].type)),
                ],
                child: Container(
                  padding: const EdgeInsets.only(left: 14, right: 6, top: 6, bottom: 6),
                  decoration: BoxDecoration(color: safeAcct == 0 ? kBlue : kRed, borderRadius: BorderRadius.circular(16)),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    Text(
                      safeAcct < Demo.i.accts.length ? Demo.i.accts[safeAcct].type.toUpperCase() : 'ACCOUNT',
                      style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    if (Demo.i.accts.length > 1) const Icon(Icons.arrow_drop_down, color: Colors.white),
                  ]),
                ),
              ),
            ]),
          ),
          if (all.isEmpty)
            const Card(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 36),
                child: Center(
                  child: Text('No debit transactions found', style: TextStyle(color: kMut, fontSize: 14)),
                ),
              ),
            )
          else
            for (final e in g.entries) ...[
              Padding(padding: const EdgeInsets.only(top: 12, bottom: 8), child: Center(child: Text(fmt(e.key), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)))),
              Card(child: Column(children: [for (final d in e.value) row(d)])),
            ],
          if (all.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                IconButton(icon: const Icon(Icons.arrow_left, size: 38, color: kNavy), onPressed: page > 0 ? () => setState(() => page--) : null),
                Text('${page + 1} / $pages', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                IconButton(icon: const Icon(Icons.arrow_right, size: 38, color: kNavy), onPressed: page < pages - 1 ? () => setState(() => page++) : null),
              ]),
            ),
        ], onBack: widget.onBack);
      },
    );
  }
}
