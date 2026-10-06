import 'package:flutter/material.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';

class BeneficiariesPage extends StatelessWidget {
  const BeneficiariesPage({super.key});
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Demo.i,
        builder: (context, _) => Pg('Beneficiaries', [
          if (Demo.i.bens.isEmpty)
            const Padding(padding: EdgeInsets.all(40), child: Center(child: Text('No beneficiaries yet', style: TextStyle(color: kMut))))
          else
            Card(
              child: Column(children: [
                for (final b in Demo.i.bens)
                  ListTile(
                    leading: const CircleAvatar(backgroundColor: Color(0xFFE4EDFF), child: Icon(Icons.person_outline, color: kNavy)),
                    title: Text(b.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text('${b.rel} · ${b.mobile.isEmpty ? 'No mobile' : b.mobile}'),
                  ),
              ]),
            ),
          bigBtn('Add beneficiary', () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AddBeneficiaryPage()))),
        ]),
      );
}

/// 3-step form. FATCA intentionally excluded (out of project scope).
class AddBeneficiaryPage extends StatefulWidget {
  const AddBeneficiaryPage({super.key});
  @override
  State<AddBeneficiaryPage> createState() => _AddBeneficiaryPageState();
}

class _AddBeneficiaryPageState extends State<AddBeneficiaryPage> {
  int step = 0;
  final c = <String, TextEditingController>{};
  final v = <String, String?>{};
  TextEditingController t(String k) => c.putIfAbsent(k, TextEditingController.new);
  String s(String k) => t(k).text.trim();

  void next() {
    if (step == 0 && (s('first').isEmpty || s('last').isEmpty || v['rel'] == null)) return snack(context, 'Enter first name, last name and relationship');
    if (step == 1 && (v['cty'] == null || s('a1').isEmpty)) return snack(context, 'Enter country and address line 1');
    if (step == 2) {
      if (v['dt'] == null || s('dn').isEmpty) return snack(context, 'Choose a document type and enter its number');
      Demo.i.addBen(Ben('${s('first')} ${s('last')}', v['rel']!, s('mob')));
      Navigator.pop(context);
      return snack(context, 'Beneficiary saved');
    }
    setState(() => step++);
  }

  @override
  Widget build(BuildContext context) {
    final body = <List<Widget>>[
      [
        tf(t('first'), 'First name'),
        tf(t('mid'), 'Middle name (optional)'),
        tf(t('last'), 'Last name'),
        dd('Nationality', const ['Filipino', 'American', 'Other'], v['nat'], (x) => v['nat'] = x),
        dd('Sex', const ['Female', 'Male'], v['sex'], (x) => v['sex'] = x),
        tf(t('dob'), 'Date of birth (dd/mm/yyyy)', k: TextInputType.datetime),
        dd('Relationship', const ['Spouse', 'Child', 'Parent', 'Sibling', 'Other'], v['rel'], (x) => v['rel'] = x),
        tf(t('pob'), 'Place of birth'),
      ],
      [
        const Text('Additional supporting information. Provide the beneficiary\'s current residence address and mobile number.', style: TextStyle(color: kMut, fontSize: 13)),
        const SizedBox(height: 14),
        dd('Country of residence', const ['Philippines', 'Other'], v['cty'], (x) => v['cty'] = x),
        tf(t('a1'), 'Address line 1'),
        tf(t('a2'), 'Address line 2'),
        tf(t('mob'), 'Mobile number', k: TextInputType.phone),
        tf(t('em'), 'Email address', k: TextInputType.emailAddress),
      ],
      [
        const Text('Upload supporting documents. Make sure the information is clearly visible.', style: TextStyle(color: kMut, fontSize: 13)),
        const SizedBox(height: 14),
        Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton(
                onPressed: () => snack(context, 'Upload is a placeholder'),
                style: OutlinedButton.styleFrom(minimumSize: const Size(80, 80)),
                child: const Icon(Icons.add))),
        const SizedBox(height: 14),
        dd('Document type', const ['Passport', "Driver's license", 'UMID', 'National ID', 'Birth certificate'], v['dt'], (x) => v['dt'] = x),
        tf(t('dn'), 'Document number'),
      ],
    ];
    return Pg('Add beneficiary', [...body[step], bigBtn(step == 2 ? 'Save' : 'Next', next)],
        chip: Text('${step + 1} / 3', style: const TextStyle(color: kBlue, fontWeight: FontWeight.w700)),
        onBack: () => step == 0 ? Navigator.pop(context) : setState(() => step--));
  }
}
