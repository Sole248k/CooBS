import 'package:flutter/material.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../data/mobile_repo.dart';

(String, Color) kycBadge(String s) => switch (s) {
      'VERIFIED' => ('VERIFIED', kBlue),
      'PENDING_REVIEW' => ('PENDING REVIEW', const Color(0xFFB7791F)),
      'REJECTED' => ('REJECTED', kRed),
      _ => ('UNVERIFIED', kMut),
    };

Widget kycChip(String s) {
  final b = kycBadge(s);
  return Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(color: b.$2.withAlpha(30), borderRadius: BorderRadius.circular(10)),
    child: Text(b.$1, style: TextStyle(color: b.$2, fontSize: 11, fontWeight: FontWeight.w700)),
  );
}

/// Simulated KYC intake (fixtures K1, K2). A "teller" decision is simulated with buttons.
class KycIntakeScreen extends StatefulWidget {
  const KycIntakeScreen({super.key});
  @override
  State<KycIntakeScreen> createState() => _KycIntakeScreenState();
}

class _KycIntakeScreenState extends State<KycIntakeScreen> {
  late final Future<List<Map<String, dynamic>>> init = Future.wait([MobileRepo.kycOptions(), MobileRepo.kycSample()]);
  final c = <String, TextEditingController>{};
  final v = <String, String?>{};
  Map<String, String> err = {};
  Map<String, dynamic>? result;
  bool busy = false, editing = false, seeded = false;

  TextEditingController t(String k) => c.putIfAbsent(k, TextEditingController.new);
  String s(String k) => t(k).text.trim();

  void seed(Map<String, dynamic> x) {
    seeded = true;
    t('firstName').text = '${x['firstName']}';
    t('middleInitial').text = '${x['middleInitial']}';
    t('lastName').text = '${x['lastName']}';
    t('address').text = '${x['address']}';
    t('occupation').text = '${x['occupation']}';
    t('mobileNumber').text = '${x['mobileNumber']}';
    t('idNumber').text = '${x['idNumber']}';
    v['civilStatus'] = '${x['civilStatus']}';
    v['idType'] = '${x['idType']}';
  }

  Widget f(String k, String l, {TextInputType? kb}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextField(controller: t(k), keyboardType: kb, decoration: InputDecoration(labelText: l, errorText: err[k])),
      );

  Future<void> submit() async {
    final e = <String, String>{};
    for (final k in ['firstName', 'lastName', 'address', 'occupation']) { if (s(k).isEmpty) e[k] = 'Required'; }
    if (s('middleInitial').length > 1) e['middleInitial'] = 'One letter only';
    if (!RegExp(r'^(\+639|09)\d{9}$').hasMatch(s('mobileNumber'))) e['mobileNumber'] = 'Use +639XXXXXXXXX or 09XXXXXXXXX';
    if (s('idNumber').length < 5) e['idNumber'] = 'At least 5 characters';
    if (v['civilStatus'] == null || v['idType'] == null) { snack(context, 'Choose a civil status and an ID type'); }
    setState(() => err = e);
    if (e.isNotEmpty || v['civilStatus'] == null || v['idType'] == null) return;
    setState(() => busy = true);
    final r = await MobileRepo.kycResult('PENDING_REVIEW');
    Demo.i.setKyc('PENDING_REVIEW');
    if (mounted) setState(() { result = r; busy = false; editing = false; });
  }

  Future<void> decide(String state) async {
    setState(() => busy = true);
    final r = await MobileRepo.kycResult(state);
    Demo.i.setKyc(state);
    if (mounted) setState(() { result = r; busy = false; });
  }

  Widget status(String st) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Center(child: Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: kycChip(st))),
        panel([
          if (result != null) ...[
            statRow('Submission', '${result!['submissionId']}'),
            statRow('Submitted', '${result!['submittedAt']}'),
            if (result!['reviewedAt'] != null) statRow('Reviewed', '${result!['reviewedAt']}'),
            if (result!['reviewNote'] != null) statRow('Note', '${result!['reviewNote']}'),
          ] else
            statRow('Status', kycBadge(st).$1),
          statRow('Mode', 'Simulated (no real verification)'),
        ]),
        if (st == 'PENDING_REVIEW') ...[
          Padding(padding: EdgeInsets.only(top: 16, bottom: 4), child: Text('Tester: simulate the teller decision', style: TextStyle(color: kMut, fontSize: 12))),
          Row(children: [
            Expanded(child: OutlinedButton(onPressed: busy ? null : () => decide('VERIFIED'), child: const Text('Approve'))),
            const SizedBox(width: 10),
            Expanded(child: OutlinedButton(onPressed: busy ? null : () => decide('REJECTED'), child: const Text('Reject'))),
          ]),
        ],
        if (st == 'REJECTED') bigBtn('Resubmit', () => setState(() => editing = true)),
        if (st == 'VERIFIED') bigBtn('Done', () => Navigator.pop(context)),
      ]);

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Demo.i,
        builder: (context, _) => FutureBuilder<List<Map<String, dynamic>>>(
          future: init,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) {
              return const Pg('Verify identity', [Padding(padding: EdgeInsets.all(60), child: Center(child: CircularProgressIndicator()))]);
            }
            if (snap.hasError) return const Pg('Verify identity', [Padding(padding: EdgeInsets.all(30), child: Text('Could not load KYC options.'))]);
            final opt = snap.data![0];
            if (!seeded) seed(snap.data![1]);
            final st = Demo.i.kyc;
            if (!editing && st != 'UNVERIFIED') return Pg('Verify identity', [status(st)]);
            return Pg('Verify identity', [
              Padding(padding: EdgeInsets.only(bottom: 14), child: Text('Sample details are prefilled. A teller review is simulated.', style: TextStyle(color: kMut, fontSize: 13))),
              f('firstName', 'First name'),
              f('middleInitial', 'Middle initial'),
              f('lastName', 'Last name'),
              f('address', 'Address'),
              dd('Civil status', [for (final x in opt['civilStatuses'] as List) '$x'], v['civilStatus'], (x) => v['civilStatus'] = x),
              f('occupation', 'Occupation'),
              f('mobileNumber', 'Mobile number', kb: TextInputType.phone),
              dd('ID type', [for (final x in opt['idTypes'] as List) '$x'], v['idType'], (x) => v['idType'] = x),
              f('idNumber', 'ID number'),
              bigBtn(busy ? 'Submitting…' : 'Submit for review', busy ? null : submit),
            ]);
          },
        ),
      );
}
