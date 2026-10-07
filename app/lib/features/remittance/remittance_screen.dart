import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../data/mobile_repo.dart';

/// Parses "1234.5" into centavos (123450) without floating-point math. Null if invalid.
int? centavos(String t) {
  final m = RegExp(r'^(\d{1,9})(?:\.(\d{1,2}))?$').firstMatch(t.trim());
  if (m == null) return null;
  return int.parse(m[1]!) * 100 + int.parse((m[2] ?? '').padRight(2, '0'));
}

String two(int c) => '${c ~/ 100}.${(c % 100).toString().padLeft(2, '0')}';
String money(int c) => peso(c / 100);

/// P2P remittance form (contract M3 request) running on mock data.
class RemittanceScreen extends StatefulWidget {
  final int from;
  const RemittanceScreen({super.key, this.from = 0});
  @override
  State<RemittanceScreen> createState() => _RemittanceScreenState();
}

class _RemittanceScreenState extends State<RemittanceScreen> {
  late final Future<Map<String, dynamic>> opts = _init();
  late int src = widget.from;
  final idemKey = const Uuid().v4(), corrId = const Uuid().v4();
  final dest = TextEditingController(), amt = TextEditingController(), rem = TextEditingController();
  String bank = 'COOBS', cat = 'TRANSFER';
  Map<String, String> err = {};
  Map<String, dynamic>? problem;
  bool busy = false;

  Future<Map<String, dynamic>> _init() async {
    if (Demo.i.state == LoadState.loading) await Demo.i.load();
    return MobileRepo.remitOptions();
  }

  Future<void> submit(Map o) async {
    final a = Demo.i.accts[src];
    final b = (o['banks'] as List).firstWhere((x) => x['code'] == bank) as Map;
    final lim = o['limits'] as Map;
    final e = <String, String>{};
    final cents = centavos(amt.text);
    final minC = ((lim['minAmount'] as num) * 100).round(), maxC = ((lim['maxAmount'] as num) * 100).round();
    if (cents == null) {
      e['amt'] = 'Enter an amount with up to 2 decimals';
    } else if (cents < minC) {
      e['amt'] = 'Minimum is ${money(minC)}';
    } else if (cents > maxC) {
      e['amt'] = 'Maximum per transfer is ${money(maxC)}';
    }
    final d = dest.text.trim();
    if (!RegExp(b['accountPattern'] as String).hasMatch(d)) e['dest'] = 'Format: ${b['accountHint']}';
    setState(() { err = e; problem = null; });
    if (e.isNotEmpty) return;
    setState(() => busy = true);
    final fee = ((b['fee'] as num) * 100).round(), bal = (a.bal * 100).round();
    if (d == a.no) {
      problem = await MobileRepo.problem(false);
    } else if (cents! + fee > bal) {
      problem = await MobileRepo.problem(true);
    } else {
      final body = {
        'sourceAccountNumber': a.no,
        'destinationAccountNumber': d,
        'destinationBankCode': bank,
        'amount': two(cents),
        'currency': 'PHP',
        'category': cat,
        'remarks': rem.text.trim(),
      };
      final resp = await MobileRepo.submitRemittance(body);
      final tl = await MobileRepo.timeline();
      Demo.i.applyRemittance(src, cents / 100, fee / 100, b['name'] as String, d, resp['referenceNo'] as String);
      if (!mounted) return;
      setState(() => busy = false);
      await Navigator.push(context, MaterialPageRoute(builder: (_) => RemittanceResultPage(resp, tl, cents, fee, b['name'] as String, d, idemKey, corrId)));
      return;
    }
    if (mounted) setState(() => busy = false);
  }

  Widget banner(Map<String, dynamic> p) => Card(
        color: const Color(0xFFFBE9E7),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${p['title']} (${p['errorCode']})', style: const TextStyle(color: kRed, fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text('${p['detail']}'),
            for (final x in (p['invalidParams'] as List)) Text('• ${x['name']}: ${x['reason']}', style: const TextStyle(fontSize: 12)),
          ]),
        ),
      );

  Widget field(TextEditingController c, String l, {String? error, String? hint, TextInputType? k, List<TextInputFormatter>? f}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextField(controller: c, keyboardType: k, inputFormatters: f, onChanged: (_) => setState(() {}), decoration: InputDecoration(labelText: l, hintText: hint, errorText: error)),
      );

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
        future: opts,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Pg('Transfer', [Padding(padding: EdgeInsets.all(60), child: Center(child: CircularProgressIndicator()))]);
          }
          if (snap.hasError) return const Pg('Transfer', [Padding(padding: EdgeInsets.all(30), child: Text('Could not load transfer options.'))]);
          final o = snap.data!, banks = o['banks'] as List;
          final accts = Demo.i.accts;
          final labels = [for (final a in accts) '${a.type} · ${a.no} (${peso(a.bal)})'];
          final b = banks.firstWhere((x) => x['code'] == bank) as Map;
          final cents = centavos(amt.text), fee = ((b['fee'] as num) * 100).round();
          final bal = (accts[src].bal * 100).round();
          return Pg('Transfer', [
            if (problem != null) banner(problem!),
            const SizedBox(height: 4),
            dd('From account', labels, labels[src], (x) => setState(() => src = labels.indexOf(x ?? labels[src]))),
            dd('Destination bank', [for (final x in banks) x['name'] as String], b['name'] as String,
                (x) => setState(() { bank = banks.firstWhere((y) => y['name'] == x)['code'] as String; err = {}; })),
            field(dest, 'Recipient account number', error: err['dest'], hint: b['accountHint'] as String),
            field(amt, 'Amount (₱)', error: err['amt'], k: const TextInputType.numberWithOptions(decimal: true), f: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))]),
            dd('Category', [for (final x in o['categories'] as List) '$x'], cat, (x) => cat = x ?? cat),
            field(rem, 'Remarks (optional)'),
            panel([
              statRow('Fee', money(fee)),
              statRow('Total to be debited', cents == null ? '—' : money(cents + fee)),
              statRow('Balance after', cents == null ? '—' : money(bal - cents - fee)),
            ]),
            bigBtn(busy ? 'Sending…' : 'Send transfer', busy ? null : () => submit(o)),
          ]);
        },
      );
}

class RemittanceResultPage extends StatelessWidget {
  final Map<String, dynamic> resp, timeline;
  final int cents, fee;
  final String bank, dest, idemKey, corr;
  const RemittanceResultPage(this.resp, this.timeline, this.cents, this.fee, this.bank, this.dest, this.idemKey, this.corr, {super.key});

  @override
  Widget build(BuildContext context) => Pg('Transfer submitted', [
        Center(child: Column(children: [
          const Icon(Icons.check_circle_outline, size: 56, color: kBlue),
          const SizedBox(height: 8),
          Text(money(cents), style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
          Text('to $dest · $bank', style: const TextStyle(color: kMut)),
        ])),
        const SizedBox(height: 12),
        panel([
          statRow('Reference', '${resp['referenceNo']}'),
          statRow('Status', '${resp['status']}'),
          statRow('Fee', money(fee)),
          statRow('Idempotency-Key', idemKey),
          statRow('Correlation ID', corr),
        ]),
        const Padding(padding: EdgeInsets.only(top: 14, bottom: 4), child: Text('Transfer status', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
        Card(
          child: Column(children: [
            for (final s in timeline['stages'] as List)
              ListTile(
                leading: Icon(s['outcome'] == 'PASSED' ? Icons.check_circle : Icons.radio_button_unchecked, color: s['outcome'] == 'PASSED' ? kBlue : kMut),
                title: Text('${s['title']}', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                subtitle: Text('${s['subtitle']}'),
              ),
          ]),
        ),
        bigBtn('Done', () { Navigator.pop(context); Navigator.pop(context); }),
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Make another transfer')),
      ], onBack: () { Navigator.pop(context); Navigator.pop(context); });
}
