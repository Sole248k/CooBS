import 'package:flutter/material.dart';
import 'demo_store.dart';

const kBlue = Color(0xFF1E5BD8), kNavy = Color(0xFF14305F), kInk = Color(0xFF14231B);
const kMut = Color(0xFF6B7A72), kBg = Color(0xFFF3F5F4), kRed = Color(0xFFC0392B);
const grads = [
  [Color(0xFF0B2A6F), Color(0xFF1E5BD8), Color(0xFF4A8CFF)], // Checking
  [Color(0xFF6E0F1A), Color(0xFFC0272D), Color(0xFFEF5A52)], // Savings
  [Color(0xFF2F4A73), Color(0xFF4F6B94), Color(0xFF6F8BB3)], // Loan
];

void snack(BuildContext c, String m) => ScaffoldMessenger.of(c)
    .showSnackBar(SnackBar(content: Text(m), behavior: SnackBarBehavior.floating));

Widget tf(TextEditingController c, String l, {TextInputType? k, ValueChanged<String>? ch, bool obscure = false}) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextField(key: ValueKey(l), controller: c, keyboardType: k, onChanged: ch, obscureText: obscure, decoration: InputDecoration(labelText: l)));

Widget dd(String l, List<String> o, String? v, ValueChanged<String?> f) {
  final effectiveValue = (v != null && o.contains(v)) ? v : (o.isNotEmpty ? o.first : null);
  return Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: DropdownButtonFormField<String>(
        key: ValueKey('$l-$effectiveValue'),
        initialValue: effectiveValue,
        items: [for (final x in o) DropdownMenuItem(value: x, child: Text(x))],
        onChanged: f,
        decoration: InputDecoration(labelText: l)));
}

Widget bigBtn(String t, VoidCallback? f) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: ElevatedButton(
        onPressed: f,
        style: ElevatedButton.styleFrom(backgroundColor: kBlue, foregroundColor: Colors.white),
        child: Text(t)));

Widget tag(String t, int g) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
    decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        gradient: LinearGradient(colors: grads[g % grads.length])),
    child: Text(t, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)));

Widget statRow(String k, String v) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
      Text(k, style: const TextStyle(color: kMut)),
      const SizedBox(width: 12),
      Flexible(child: Text(v, textAlign: TextAlign.right, style: const TextStyle(fontWeight: FontWeight.w600))),
    ]));

Widget panel(List<Widget> kids) => Card(
    child: Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: kids)));

Widget logoBox() => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    decoration: BoxDecoration(color: kNavy, borderRadius: BorderRadius.circular(8)),
    child: const Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(Icons.account_balance_rounded, color: Color(0xFF00D4B2), size: 16),
      SizedBox(width: 6),
      Text('CooBS', style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold, letterSpacing: 0.5)),
    ]));

Widget miniTag(String t) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(color: const Color(0xFFE4EDFF), borderRadius: BorderRadius.circular(10)),
    child: Text(t, style: const TextStyle(color: kNavy, fontSize: 11, fontWeight: FontWeight.w600)));

Widget txRow(Tx x) {
  final out = x.amt < 0;
  final now = DateTime.now();
  final isToday = x.date.year == now.year && x.date.month == now.month && x.date.day == now.day;
  return ListTile(
    leading: CircleAvatar(
        backgroundColor: out ? const Color(0xFFFBE9E7) : const Color(0xFFE4EDFF),
        child: Icon(out ? Icons.north_east : Icons.south_west, size: 18, color: out ? kRed : kNavy)),
    title: Text(x.label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
    subtitle: Text('${x.ref} · ${isToday ? 'Today' : fmt(x.date)}'),
    trailing: Text('${out ? '-' : '+'}${peso(x.amt.abs())}',
        style: TextStyle(fontWeight: FontWeight.w600, color: out ? kRed : kNavy)),
  );
}

Widget txCard(List<Tx> l) => Card(child: Column(children: [for (final x in l) txRow(x)]));

/// Standard sub-page: back arrow, title, optional chip.
class Pg extends StatelessWidget {
  final String title;
  final Widget? chip;
  final List<Widget> kids;
  final VoidCallback? onBack;
  const Pg(this.title, this.kids, {super.key, this.chip, this.onBack});
  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        appBar: AppBar(
          backgroundColor: kBg,
          foregroundColor: kInk,
          centerTitle: false,
          leading: BackButton(onPressed: onBack ?? () => Navigator.pop(context)),
          title: Row(children: [
            Text(title, style: const TextStyle(color: kInk, fontSize: 18, fontWeight: FontWeight.w700)),
            if (chip != null) ...[const SizedBox(width: 10), chip!],
          ]),
        ),
        body: ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 32), children: kids),
      );
}
