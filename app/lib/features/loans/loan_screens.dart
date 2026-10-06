import 'package:flutter/material.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';

/// Apply flow: privacy -> details 1 (with estimate) -> details 2 -> preview.
class LoanApplyPage extends StatefulWidget {
  const LoanApplyPage({super.key});
  @override
  State<LoanApplyPage> createState() => _LoanApplyPageState();
}

class _LoanApplyPageState extends State<LoanApplyPage> {
  int step = 0;
  bool privacy = false, terms = false;
  final c = <String, TextEditingController>{};
  final v = <String, String?>{};
  TextEditingController t(String k) => c.putIfAbsent(k, TextEditingController.new);
  String s(String k) => t(k).text.trim();
  void rebuild([_]) => setState(() {});

  void next() {
    if (step == 0 && !privacy) return snack(context, 'Please confirm the Data Privacy notice');
    if (step == 1 && (v['rs'] == null || v['term'] == null || (double.tryParse(s('amt')) ?? 0) < 1000)) {
      return snack(context, 'Choose a reason, a term and an amount of at least ₱1,000');
    }
    if (step == 2) {
      if (s('pay').isEmpty || s('co').isEmpty || s('tin').isEmpty) return snack(context, 'Enter your pay, company and TIN');
      if (!terms) return snack(context, 'Please accept the Terms & Conditions');
      Demo.i.applyLoan(v['rs']!, double.parse(s('amt')), int.parse(v['term']!));
    }
    setState(() => step++);
  }

  @override
  Widget build(BuildContext context) {
    final p = double.tryParse(s('amt')) ?? 0, n = int.tryParse(v['term'] ?? '') ?? 0;
    final body = <List<Widget>>[
      [
        panel([const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Text('Data privacy\n\nWe collect your personal and financial details only to assess this loan request and meet banking regulations. They are kept confidential and shared only as the law requires.'))]),
        CheckboxListTile(contentPadding: EdgeInsets.zero, controlAffinity: ListTileControlAffinity.leading, value: privacy, onChanged: (x) => setState(() => privacy = x ?? false), title: const Text('I have read and agree to the Data Privacy notice')),
      ],
      [
        dd('Reason for loan', const ['Medical', 'Education', 'Home improvement', 'Business', 'Debt consolidation', 'Others'], v['rs'], (x) => v['rs'] = x),
        tf(t('amt'), 'Loan amount (₱)', k: TextInputType.number, ch: rebuild),
        dd('Loan term (months)', const ['6', '12', '18', '24', '36'], v['term'], (x) { v['term'] = x; rebuild(); }),
        panel([Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Text(p > 0 && n > 0 ? 'Estimated monthly installment\n${peso(monthly(p, n))}\n12% p.a. flat, demo rate' : 'Enter an amount and term to preview your monthly installment.'),
        )]),
      ],
      [
        tf(t('pay'), 'Monthly pay after tax (₱)', k: TextInputType.number),
        dd('Nature of work', const ['Employed', 'Self-employed', 'Freelance', 'Other'], v['work'], (x) => v['work'] = x),
        dd('Source of income', const ['Salary', 'Business', 'Remittance', 'Other'], v['src'], (x) => v['src'] = x),
        tf(t('co'), 'Name of company / business'),
        tf(t('ten'), 'Employment tenure (months)', k: TextInputType.number),
        tf(t('mail'), 'Email', k: TextInputType.emailAddress),
        dd('Sex', const ['Female', 'Male'], v['sx'], (x) => v['sx'] = x),
        dd('Civil status', const ['Single', 'Married', 'Widowed', 'Separated'], v['civ'], (x) => v['civ'] = x),
        tf(t('tin'), 'TIN no.'),
        tf(t('mf'), "Mother's maiden first name"),
        tf(t('mm'), "Mother's maiden middle name"),
        tf(t('ml'), "Mother's maiden last name"),
        CheckboxListTile(contentPadding: EdgeInsets.zero, controlAffinity: ListTileControlAffinity.leading, value: terms, onChanged: (x) => setState(() => terms = x ?? false), title: const Text('I accept the Terms & Conditions')),
      ],
      if (Demo.i.loan == null) <Widget>[] else [
        panel([const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Text('APPROVED (demo)\nYour loan is approved and the proceeds were credited to your Checking account.'))]),
        LoanFacts(Demo.i.loan!),
      ],
    ];
    final labels = ['Continue', 'Next', 'Request approval', 'Done'];
    return Pg(step == 0 ? 'Apply for a loan' : step == 3 ? 'Loan preview' : 'Loan details',
        [...body[step], bigBtn(labels[step], step == 3 ? () => Navigator.pop(context) : next)],
        chip: step == 1 || step == 2 ? Text('$step / 2', style: const TextStyle(color: kBlue, fontWeight: FontWeight.w700)) : null,
        onBack: () => step == 0 || step == 3 ? Navigator.pop(context) : setState(() => step--));
  }
}

class LoanFacts extends StatelessWidget {
  final Loan l;
  const LoanFacts(this.l, {super.key});
  @override
  Widget build(BuildContext context) => panel([
        statRow('Reference no.', l.ref),
        statRow('Reason', l.reason),
        statRow('Loan amount', peso(l.amt)),
        statRow('Term', '${l.term} months'),
        statRow('Interest rate', '12% p.a. flat (demo)'),
        statRow('Monthly installment', peso(l.m)),
        statRow('Total payable', peso(l.total)),
        statRow('Start date', fmt(today)),
        statRow('Maturity date', fmt(l.due(l.term))),
      ]);
}

class LoanDetailsPage extends StatelessWidget {
  const LoanDetailsPage({super.key});
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Demo.i,
        builder: (context, _) {
          final l = Demo.i.loan;
          if (l == null) return const Pg('Loan details', [Text('No active loan')]);
          return Pg('Loan details', [
            panel([statRow('Status', 'Active')]),
            LoanFacts(l),
            panel([statRow('Amount paid', peso(l.paid)), statRow('Outstanding balance', peso(l.balance))]),
          ]);
        },
      );
}

class MyBillPage extends StatelessWidget {
  const MyBillPage({super.key});
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Demo.i,
        builder: (context, _) {
          final l = Demo.i.loan;
          if (l == null) return const Pg('My Bill', [Text('No active loan')]);
          final next = l.next;
          return Pg('My Bill', [
            panel([Padding(padding: const EdgeInsets.symmetric(vertical: 10), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              const Text('Outstanding balance'), Text(peso(l.balance), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800))]))]),
            Card(child: Column(children: [
              for (final b in l.bills)
                ListTile(
                  title: Text('Bill ${b.k} of ${l.term}', style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('Due ${fmt(b.due)}'),
                  trailing: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
                    Text(peso(b.amt), style: const TextStyle(fontWeight: FontWeight.w600)),
                    Text(b.isPaid ? 'Paid' : (b.k == next?.k ? 'Next' : 'Upcoming'),
                        style: TextStyle(fontSize: 12, color: b.isPaid ? kBlue : kMut)),
                  ]),
                ),
            ])),
          ]);
        },
      );
}

class PayAdvanceSheet extends StatefulWidget {
  const PayAdvanceSheet({super.key});
  @override
  State<PayAdvanceSheet> createState() => _PayAdvanceSheetState();
}

class _PayAdvanceSheetState extends State<PayAdvanceSheet> {
  late final amt = TextEditingController(text: '${r2(Demo.i.loan!.next!.amt - Demo.i.loan!.next!.paid)}');
  String src = 'Checking';

  @override
  Widget build(BuildContext context) {
    final d = Demo.i;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 22, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Pay in Advance', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        const Text('Pay now to settle your next bill early. Any extra goes to the following bills.', style: TextStyle(color: kMut, fontSize: 13)),
        const SizedBox(height: 14),
        dd('Pay from', [for (final a in d.accts) a.type], src, (x) => src = x ?? src),
        tf(amt, 'Amount (₱)', k: const TextInputType.numberWithOptions(decimal: true)),
        Text('Outstanding balance: ${peso(d.loan!.balance)}', style: const TextStyle(color: kMut, fontSize: 12)),
        bigBtn('Pay', () {
          final v = double.tryParse(amt.text) ?? 0;
          final err = d.payAdvance(d.accts.indexWhere((a) => a.type == src), v);
          if (err != null) return snack(context, err);
          Navigator.pop(context);
          snack(context, 'Paid ${peso(v)} toward your loan');
        }),
      ]),
    );
  }
}
