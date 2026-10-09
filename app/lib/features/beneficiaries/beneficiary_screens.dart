import 'package:flutter/material.dart';

import '../../core/models/models.dart';
import '../../core/network/api_client.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';

class BeneficiariesPage extends StatefulWidget {
  final VoidCallback? onBack;
  const BeneficiariesPage({super.key, this.onBack});
  @override
  State<BeneficiariesPage> createState() => _BeneficiariesPageState();
}

class _BeneficiariesPageState extends State<BeneficiariesPage> {
  final _apiClient = ApiClient();
  List<BeneficiaryItem> _beneficiaries = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadBeneficiaries();
  }

  Future<void> _loadBeneficiaries() async {
    setState(() => _isLoading = true);
    try {
      final json = await _apiClient.get('/customers/me/beneficiaries');
      List<dynamic> list = [];
      if (json['data'] is List) {
        list = json['data'] as List;
      } else if (json is List) {
        list = json as List;
      }
      final items = list.map((e) => BeneficiaryItem.fromJson(e as Map<String, dynamic>)).toList();
      if (mounted) {
        setState(() {
          _beneficiaries = items;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _deleteBeneficiary(BeneficiaryItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Beneficiary'),
        content: Text('Are you sure you want to remove ${item.nickname}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: kRed),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      try {
        await _apiClient.delete('/customers/me/beneficiaries/${item.beneficiaryId}');
        _loadBeneficiaries();
        if (mounted) snack(context, '${item.nickname} removed');
      } catch (e) {
        if (mounted) snack(context, 'Failed to remove beneficiary');
      }
    }
  }

  @override
  Widget build(BuildContext context) => Pg(
        'Beneficiaries',
        [
          if (_isLoading)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 40),
              child: Center(child: CircularProgressIndicator(color: kNavy)),
            )
          else if (_beneficiaries.isEmpty)
            Padding(
              padding: const EdgeInsets.all(40),
              child: Center(
                child: Text('No saved beneficiaries yet', style: TextStyle(color: kMut)),
              ),
            )
          else
            Card(
              child: Column(children: [
                for (final b in _beneficiaries)
                  ListTile(
                    leading: CircleAvatar(
                      backgroundColor: kInBg,
                      child: Icon(Icons.person_outline, color: kNavyFg),
                    ),
                    title: Text(b.nickname, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text('${b.bankCode} · ${b.accountNumber}'),
                    trailing: IconButton(
                      icon: Icon(Icons.delete_outline, size: 20, color: kMut),
                      onPressed: () => _deleteBeneficiary(b),
                    ),
                  ),
              ]),
            ),
          bigBtn(
            'Add beneficiary',
            () async {
              final added = await Navigator.push<bool>(
                context,
                MaterialPageRoute(builder: (_) => const AddBeneficiaryPage()),
              );
              if (added == true) {
                _loadBeneficiaries();
              }
            },
          ),
        ],
        onBack: widget.onBack,
      );
}

/// 3-step form connecting to POST /api/v1/customers/me/beneficiaries.
class AddBeneficiaryPage extends StatefulWidget {
  const AddBeneficiaryPage({super.key});

  @override
  State<AddBeneficiaryPage> createState() => _AddBeneficiaryPageState();
}

class _AddBeneficiaryPageState extends State<AddBeneficiaryPage> {
  final _apiClient = ApiClient();
  int step = 0;
  bool _isSubmitting = false;
  final c = <String, TextEditingController>{};
  final v = <String, String?>{};

  TextEditingController t(String k) => c.putIfAbsent(k, TextEditingController.new);
  String s(String k) => t(k).text.trim();

  @override
  void dispose() {
    for (final controller in c.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> next() async {
    if (step == 0) {
      if (s('nick').isEmpty) {
        return snack(context, 'Enter a nickname for this beneficiary');
      }
      if (s('acct').isEmpty) {
        return snack(context, 'Enter beneficiary account number');
      }
      setState(() => step++);
      return;
    }

    if (step == 1) {
      if (s('first').isEmpty || s('last').isEmpty) {
        return snack(context, 'Enter first name and last name');
      }
      setState(() => step++);
      return;
    }

    if (step == 2) {
      setState(() => _isSubmitting = true);
      try {
        final payload = {
          'nickname': s('nick'),
          'accountNumber': s('acct'),
          'bankCode': v['bank'] ?? 'COOBS',
          'beneficiaryName': '${s('first')} ${s('last')}',
        };

        await _apiClient.post(
          '/customers/me/beneficiaries',
          body: payload,
        );

        Demo.i.addBen(Ben('${s('first')} ${s('last')}', v['rel'] ?? 'Contact', s('mob'), accountNumber: s('acct'), bankCode: v['bank'] ?? 'COOBS'));

        if (mounted) {
          snack(context, 'Beneficiary saved successfully');
          Navigator.pop(context, true);
        }
      } catch (e) {
        if (mounted) {
          snack(context, 'Failed to save beneficiary: ${e.toString().replaceAll("Exception: ", "")}');
          setState(() => _isSubmitting = false);
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final body = <List<Widget>>[
      [
        const Text(
          'Step 1: Account Information',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        const SizedBox(height: 6),
        Text(
          'Provide the destination account and quick reference name.',
          style: TextStyle(color: kMut, fontSize: 13),
        ),
        const SizedBox(height: 14),
        tf(t('nick'), 'Nickname (e.g., Mom Savings)'),
        tf(t('acct'), 'Account number (e.g., ACC-1002-9915)'),
        dd('Bank', const ['COOBS', 'BDO', 'BPI', 'Metrobank', 'Maya'], v['bank'] ?? 'COOBS', (x) => v['bank'] = x),
      ],
      [
        const Text(
          'Step 2: Personal Information',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        const SizedBox(height: 6),
        Text(
          'Provide the legal identity of the recipient.',
          style: TextStyle(color: kMut, fontSize: 13),
        ),
        const SizedBox(height: 14),
        tf(t('first'), 'First name'),
        tf(t('mid'), 'Middle name (optional)'),
        tf(t('last'), 'Last name'),
        dd('Relationship', const ['Spouse', 'Child', 'Parent', 'Sibling', 'Friend', 'Other'], v['rel'], (x) => v['rel'] = x),
        tf(t('mob'), 'Mobile number (optional)', k: TextInputType.phone),
      ],
      [
        const Text(
          'Step 3: Residence & Review',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        const SizedBox(height: 6),
        Text(
          'Provide optional residence notes or confirmation.',
          style: TextStyle(color: kMut, fontSize: 13),
        ),
        const SizedBox(height: 14),
        dd('Country of residence', const ['Philippines', 'Other'], v['cty'] ?? 'Philippines', (x) => v['cty'] = x),
        tf(t('a1'), 'Address (optional)'),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Summary', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                const SizedBox(height: 6),
                statRow('Nickname', s('nick')),
                statRow('Account', s('acct')),
                statRow('Bank', v['bank'] ?? 'COOBS'),
                statRow('Recipient', '${s('first')} ${s('last')}'),
              ],
            ),
          ),
        ),
      ],
    ];

    return Pg(
      'Add beneficiary',
      [
        ...body[step],
        _isSubmitting
            ? Padding(
                padding: const EdgeInsets.only(top: 20),
                child: Center(child: CircularProgressIndicator(color: kNavy)),
              )
            : bigBtn(step == 2 ? 'Save Beneficiary' : 'Next', next),
      ],
      chip: Text('${step + 1} / 3', style: TextStyle(color: kBlue, fontWeight: FontWeight.w700)),
      onBack: () => step == 0 ? Navigator.pop(context) : setState(() => step--),
    );
  }
}
