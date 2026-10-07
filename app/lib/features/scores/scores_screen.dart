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

const _mock = [
  ['Transfer', 'BDO ••4521'],
  ['Bills payment', 'Meralco'],
  ['Bills payment', 'Maynilad'],
  ['Load purchase', 'Globe Telecom'],
  ['Card payment', 'Maya'],
  ['Transfer', 'BPI ••9034'],
  ['Grocery', 'Puregold'],
  ['Food delivery', 'GrabFood'],
];

/// Live debits from the demo store first, then generated older mock history (>20 per account).
List<Debit> debitsFor(int a) {
  final out = <Debit>[];
  for (final x in Demo.i.accts[a].tx) {
    if (x.amt >= 0) continue;
    final p = x.label.split(' to ');
    out.add(Debit(p.first, p.length > 1 ? p.last : x.ref, x.ref, x.date, -x.amt, a));
  }
  final n = a == 0 ? 27 : 22;
  for (var i = 0; i < n; i++) {
    final m = _mock[(i * 3 + a) % _mock.length];
    out.add(Debit(m[0], m[1], 'TXN-${8800 - i - a * 40}', DateTime(2026, 10, 5).subtract(Duration(days: (i * 3) ~/ 2 + a)),
        ((i * 137 + a * 61) % 900 + 120).toDouble(), a));
  }
  return out;
}

class ScoresPage extends StatefulWidget {
  const ScoresPage({super.key});
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

  Widget row(Debit d) => ListTile(
        isThreeLine: true,
        leading: const CircleAvatar(backgroundColor: Color(0xFFFBE9E7), child: Icon(Icons.north_east, size: 18, color: kRed)),
        title: Text(d.activity, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
        subtitle: Text('To ${d.receiver}\n${d.ref} · ${Demo.i.accts[d.acct].type}'),
        trailing: Text('-${peso(d.amt)}', style: const TextStyle(fontWeight: FontWeight.w600, color: kRed)),
      );

  @override
  Widget build(BuildContext context) {
    final all = debitsFor(acct);
    final pages = (all.length / per).ceil();
    if (page >= pages) page = pages - 1;
    final g = <DateTime, List<Debit>>{};
    for (final d in all.skip(page * per).take(per)) { g.putIfAbsent(d.date, () => []).add(d); }
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
              const PopupMenuItem(value: 0, child: Text('Checking')),
              const PopupMenuItem(value: 1, child: Text('Savings')),
            ],
            child: Container(
              padding: const EdgeInsets.only(left: 14, right: 6, top: 6, bottom: 6),
              decoration: BoxDecoration(color: acct == 0 ? kBlue : kRed, borderRadius: BorderRadius.circular(16)),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                Text(acct == 0 ? 'CHECKING' : 'SAVINGS', style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)),
                const Icon(Icons.arrow_drop_down, color: Colors.white),
              ]),
            ),
          ),
        ]),
      ),
      for (final e in g.entries) ...[
        Padding(padding: const EdgeInsets.only(top: 12, bottom: 8), child: Center(child: Text(fmt(e.key), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)))),
        Card(child: Column(children: [for (final d in e.value) row(d)])),
      ],
      Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          IconButton(icon: const Icon(Icons.arrow_left, size: 38, color: kNavy), onPressed: page > 0 ? () => setState(() => page--) : null),
          Text('${page + 1} / $pages', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
          IconButton(icon: const Icon(Icons.arrow_right, size: 38, color: kNavy), onPressed: page < pages - 1 ? () => setState(() => page++) : null),
        ]),
      ),
    ]);
  }
}
