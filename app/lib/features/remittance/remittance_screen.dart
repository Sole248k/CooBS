import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

import '../../core/models/models.dart';
import '../../core/network/network.dart';
import '../../core/theme/app_theme.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../data/mobile_repo.dart';
import '../resilience/service_unavailable_view.dart';

/// Parses "1234.5" into centavos (123450) without floating-point math. Null if invalid.
int? centavos(String t) {
  final m = RegExp(r'^(\d{1,9})(?:\.(\d{1,2}))?$').firstMatch(t.trim());
  if (m == null) return null;
  return int.parse(m[1]!) * 100 + int.parse((m[2] ?? '').padRight(2, '0'));
}

String two(int c) => '${c ~/ 100}.${(c % 100).toString().padLeft(2, '0')}';
String money(int c) => peso(c / 100);

/// P2P remittance form (contract M3 request) running on mock data / fixtures.
class RemittanceScreen extends StatefulWidget {
  final int from;
  const RemittanceScreen({super.key, this.from = 0});
  @override
  State<RemittanceScreen> createState() => _RemittanceScreenState();
}

class _RemittanceScreenState extends State<RemittanceScreen> {
  late final Future<Map<String, dynamic>> opts = _init();
  late int src = widget.from;
  String idemKey = const Uuid().v4(), corrId = const Uuid().v4();
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
    if (Demo.i.accts.isEmpty) return;
    final safeSrc = src < Demo.i.accts.length ? src : 0;
    final a = Demo.i.accts[safeSrc];
    final b = (o['banks'] as List).firstWhere((x) => x['code'] == bank) as Map;
    final lim = o['limits'] as Map;
    final e = <String, String>{};
    final cents = centavos(amt.text);
    final minC = ((lim['minAmount'] as num) * 100).round(), maxC = ((lim['maxAmount'] as num) * 100).round();
    var d = dest.text.trim();
    if (bank == 'COOBS' && RegExp(r'^\d{8}$').hasMatch(d)) {
      d = 'ACC_$d';
      dest.text = d;
    }
    bool isValidDest(String pattern, String value) {
      try {
        var p = pattern;
        var caseSensitive = true;
        if (p.contains('(?i)')) {
          p = p.replaceAll('(?i)', '');
          caseSensitive = false;
        }
        return RegExp(p, caseSensitive: caseSensitive).hasMatch(value);
      } catch (_) {
        return RegExp(r'^[A-Za-z0-9_-]{4,34}$').hasMatch(value);
      }
    }
    if (d.isEmpty) {
      e['dest'] = 'Please enter a recipient account number';
    } else if (d == a.no) {
      e['dest'] = 'Self-transfer is not allowed. Select a different recipient account';
    } else if (!isValidDest(b['accountPattern'] as String, d)) {
      e['dest'] = 'Invalid format. Hint: ${b['accountHint']} (e.g. ACC_10000002)';
    }

    if (cents == null) {
      e['amt'] = amt.text.trim().isEmpty ? 'Please enter a transfer amount' : 'Enter a valid amount with up to 2 decimal places';
    } else if (cents < minC) {
      e['amt'] = 'Minimum transfer amount is ${money(minC)}';
    } else if (cents > maxC) {
      e['amt'] = 'Maximum transfer limit per transaction is ${money(maxC)}';
    } else {
      final fee = ((b['fee'] as num) * 100).round(), bal = (a.bal * 100).round();
      if (cents + fee > bal) {
        e['amt'] = 'Insufficient balance. Available: ${peso(a.bal)} (Transfer + fee: ${money(cents + fee)})';
      }
    }

    setState(() { err = e; problem = null; });
    if (e.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.values.first),
          backgroundColor: kRed,
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }
    setState(() => busy = true);
    final fee = ((b['fee'] as num) * 100).round(), bal = (a.bal * 100).round();
    if (d == a.no) {
      problem = await MobileRepo.problem(false);
    } else if (cents! + fee > bal) {
      problem = await MobileRepo.problem(true);
    } else {
      final activeIdemKey = idemKey;
      final activeCorrId = corrId;
      // Refresh idempotency and correlation identifiers so subsequent transfers never reuse keys
      idemKey = const Uuid().v4();
      corrId = const Uuid().v4();

      final body = {
        'sourceAccountNumber': a.no,
        'destinationAccountNumber': d,
        'destinationBankCode': bank,
        'amount': two(cents),
        'currency': 'PHP',
        'category': cat,
        'remarks': rem.text.trim(),
      };

      final Map<String, dynamic> resp;
      Map<String, dynamic> tl;
      try {
        resp = await MobileRepo.submitRemittance(body, idempotencyKey: activeIdemKey);
        final ref = resp['referenceNo'] ?? resp['trackingReference'] ?? '';
        try {
          tl = await MobileRepo.timeline(ref.toString());
        } catch (_) {
          tl = {
            'stages': [
              {
                'title': 'Transfer submitted',
                'subtitle': 'Reference: $ref',
                'outcome': 'PASSED',
              }
            ]
          };
        }
      } on CircuitBreakerOpenException catch (cbe) {
        if (!mounted) return;
        setState(() => busy = false);
        Navigator.push(context, MaterialPageRoute(
          builder: (_) => ServiceUnavailableView(cooldown: cbe.remainingCooldown),
        ));
        return;
      } on ProblemDetailsException catch (pde) {
        if (!mounted) return;
        if (pde.statusCode == 503) {
          setState(() => busy = false);
          Navigator.push(context, MaterialPageRoute(
            builder: (_) => const ServiceUnavailableView(),
          ));
          return;
        }
        setState(() {
          busy = false;
          problem = {
            'title': pde.error.title,
            'errorCode': pde.error.errorCode,
            'detail': pde.error.detail,
            'invalidParams': pde.error.invalidParams,
          };
        });
        return;
      } catch (ex) {
        if (!mounted) return;
        if (ex is ConnectionException || (ex is ApiException && ex.statusCode == 503)) {
          setState(() => busy = false);
          Navigator.push(context, MaterialPageRoute(
            builder: (_) => const ServiceUnavailableView(),
          ));
          return;
        }
        setState(() {
          busy = false;
          problem = {
            'title': 'Submission Failed',
            'errorCode': 'NETWORK_ERROR',
            'detail': ex.toString(),
            'invalidParams': [],
          };
        });
        return;
      }

      final ref = resp['referenceNo'] ?? resp['trackingReference'] ?? 'TXN-PENDING';
      Demo.i.applyRemittance(safeSrc, cents / 100, fee / 100, b['name'] as String, d, ref.toString());
      if (!mounted) return;
      setState(() => busy = false);
      await Navigator.push(context, MaterialPageRoute(builder: (_) => RemittanceResultPage(resp, tl, cents, fee, b['name'] as String, d, activeIdemKey, activeCorrId)));
      return;
    }
    if (mounted) setState(() => busy = false);
  }

  Widget banner(Map<String, dynamic> p) => Card(
        color: kOutBg,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${p['title']} (${p['errorCode']})', style: TextStyle(color: kRed, fontWeight: FontWeight.w700)),
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
          if (accts.isEmpty) {
            return const Pg('Transfer', [Padding(padding: EdgeInsets.all(30), child: Text('No accounts available for transfer.'))]);
          }
          final safeSrc = src < accts.length ? src : 0;
          final labels = [for (final a in accts) '${a.type} · ${a.no} (${peso(a.bal)})'];
          final b = banks.firstWhere((x) => x['code'] == bank) as Map;
          final cents = centavos(amt.text), fee = ((b['fee'] as num) * 100).round();
          final bal = (accts[safeSrc].bal * 100).round();
          return Pg('Transfer', [
            if (problem != null) banner(problem!),
            const SizedBox(height: 4),
            dd('From account', labels, labels[safeSrc], (x) => setState(() => src = x != null && labels.contains(x) ? labels.indexOf(x) : 0)),
            dd('Destination bank', [for (final x in banks) x['name'] as String], b['name'] as String,
                (x) => setState(() { bank = banks.firstWhere((y) => y['name'] == x)['code'] as String; err = {}; })),
            if (bank != 'COOBS')
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: kDark ? const Color(0xFF2E2413) : const Color(0xFFFFF8E1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: kDark ? const Color(0xFFB45309) : const Color(0xFFFFB300),
                      width: 0.8,
                    ),
                  ),
                  child: Row(children: [
                    Icon(
                      Icons.info_outline,
                      size: 16,
                      color: kDark ? const Color(0xFFFBBF24) : const Color(0xFFB45309),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Interbank clearing is currently limited. For real-time processing, select CooBS (same bank).',
                        style: TextStyle(
                          fontSize: 11,
                          color: kDark ? const Color(0xFFFDE68A) : const Color(0xFF78350F),
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ]),
                ),
              ),
            if (Demo.i.bens.isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.only(bottom: 4, top: 4),
                child: Text('Saved Beneficiaries', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: kMut)),
              ),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(children: [
                  for (final ben in Demo.i.bens)
                    Padding(
                      padding: const EdgeInsets.only(right: 8, bottom: 8),
                      child: ActionChip(
                        avatar: CircleAvatar(
                          backgroundColor: const Color(0xFFE4EDFF),
                          radius: 12,
                          child: Icon(Icons.person, size: 14, color: kNavy),
                        ),
                        label: Text(ben.name),
                        onPressed: () {
                          setState(() {
                            final acctNum = ben.accountNumber ?? (ben.rel.contains('·') ? ben.rel.split('·').last.trim() : '');
                            if (acctNum.isNotEmpty) dest.text = acctNum;
                            final bCode = ben.bankCode ?? (ben.rel.contains('·') ? ben.rel.split('·').first.trim() : 'COOBS');
                            if (banks.any((bk) => bk['code'] == bCode)) {
                              bank = bCode;
                            }
                            err = {};
                          });
                        },
                      ),
                    ),
                ]),
              ),
            ],
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
          Icon(Icons.check_circle_outline, size: 56, color: kBlue),
          const SizedBox(height: 8),
          Text(money(cents), style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
          Text('to $dest · $bank', style: TextStyle(color: kMut)),
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
            for (final s in (timeline['stages'] as List? ?? []))
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

/// Interactive Remittance Intake Form (Endpoint M3 via mobile-bff).
/// Dispatches money movement requests through Spring Cloud Gateway
/// with automatic UUID idempotency keys and error handling.
class RemittanceForm extends StatefulWidget {
  final String? defaultSourceAccount;
  final double? availableBalance;
  final void Function(String trackingReference)? onSuccess;

  const RemittanceForm({
    super.key,
    this.defaultSourceAccount,
    this.availableBalance,
    this.onSuccess,
  });

  @override
  State<RemittanceForm> createState() => _RemittanceFormState();
}

class _RemittanceFormState extends State<RemittanceForm> {
  final _formKey = GlobalKey<FormState>();
  final ApiClient _apiClient = ApiClient();

  late final TextEditingController _sourceController;
  late final TextEditingController _destinationController;
  final TextEditingController _amountController = TextEditingController(text: '100.00');
  final TextEditingController _remarksController = TextEditingController(text: 'Mobile Transfer');

  String _destinationBank = 'COOBS';
  bool _isSubmitting = false;

  List<Map<String, String>> get _quickRecipients {
    final src = _sourceController.text.trim();
    return [
      if (src != 'ACC_10000001') {'name': 'John Doe', 'account': 'ACC_10000001'},
      if (src != 'ACC_10000002') {'name': 'Jane Smith', 'account': 'ACC_10000002'},
      if (src != 'ACC_10000004') {'name': 'Juan Dela Cruz', 'account': 'ACC_10000004'},
      if (src != 'ACC_10000008') {'name': 'Jose Bautista', 'account': 'ACC_10000008'},
    ];
  }

  final List<double> _quickAmounts = [50.0, 100.0, 500.0, 1000.0, 2500.0];

  @override
  void initState() {
    super.initState();
    final defaultSource = widget.defaultSourceAccount ??
        (Demo.i.primaryNo.isNotEmpty ? Demo.i.primaryNo : (Demo.i.accts.isNotEmpty ? Demo.i.accts.first.no : 'ACC_10000001'));
    _sourceController = TextEditingController(text: defaultSource);
    final defaultDest = (defaultSource == 'ACC_10000002') ? 'ACC_10000004' : 'ACC_10000002';
    _destinationController = TextEditingController(text: defaultDest);
  }

  @override
  void dispose() {
    _sourceController.dispose();
    _destinationController.dispose();
    _amountController.dispose();
    _remarksController.dispose();
    super.dispose();
  }

  Future<void> _submitTransfer() async {
    if (!_formKey.currentState!.validate()) return;

    final source = _sourceController.text.trim();
    final dest = _destinationController.text.trim();

    if (source.toUpperCase() == dest.toUpperCase()) {
      _showSnackbar('Source and destination accounts must be distinct.', isError: true);
      return;
    }

    final parsedAmount = double.tryParse(_amountController.text.trim()) ?? 0.0;
    if (parsedAmount <= 0) {
      _showSnackbar('Please enter an amount greater than ₱0.00', isError: true);
      return;
    }

    if (parsedAmount > 500000.0) {
      _showSnackbar('Transfer exceeds maximum allowable single transaction limit (₱500,000.00)', isError: true);
      return;
    }

    if (widget.availableBalance != null && parsedAmount > widget.availableBalance!) {
      _showSnackbar('Insufficient balance: transfer exceeds available funds', isError: true);
      return;
    }

    setState(() => _isSubmitting = true);

    // Sanitize remarks: strip dangerous injection characters and clamp length
    final rawRemarks = _remarksController.text.trim();
    final sanitizedRemarks = rawRemarks
        .replaceAll(RegExp(r'[<>]'), '')
        .replaceAll(RegExp(r'[\r\n\t]'), ' ')
        .trim();

    try {
      final requestBody = {
        'sourceAccountNumber': source,
        'destinationAccountNumber': dest,
        'destinationBankCode': _destinationBank,
        'amount': parsedAmount,
        'currency': 'PHP',
        'category': 'TRANSFER',
        'remarks': sanitizedRemarks.isNotEmpty ? sanitizedRemarks : 'Mobile Remittance',
      };

      final response = await _apiClient.post(
        '/mobile/transactions/remittance',
        body: requestBody,
        isMoneyMovement: true,
      );

      final remittance = RemittanceSubmissionResponse.fromJson(response);
      final ref = remittance.referenceNo.isNotEmpty
          ? remittance.referenceNo
          : remittance.transactionId;

      final srcIdx = Demo.i.accts.indexWhere((a) => a.no == source);
      Demo.i.applyRemittance(
        srcIdx >= 0 ? srcIdx : 0,
        parsedAmount,
        0.0,
        _destinationBank,
        dest,
        ref,
      );

      if (mounted) {
        _showSnackbar('Transfer accepted! Ref: $ref', isError: false);
        widget.onSuccess?.call(ref);
      }
    } catch (e) {
      if (mounted) {
        if (e is CircuitBreakerOpenException) {
          Navigator.push(context, MaterialPageRoute(
            builder: (_) => ServiceUnavailableView(cooldown: e.remainingCooldown),
          ));
          return;
        } else if ((e is ApiException && e.statusCode == 503) || e is ConnectionException) {
          Navigator.push(context, MaterialPageRoute(
            builder: (_) => const ServiceUnavailableView(),
          ));
          return;
        }
        final message = e is ApiException
            ? e.message
            : 'Transfer failed. Please check network connection or account status.';
        _showSnackbar(message, isError: true);
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  void _showSnackbar(String message, {required bool isError}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? AppTheme.errorCarmine : AppTheme.successGreen,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 540),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppTheme.navyPrimary.withAlpha(20),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.send_rounded, color: AppTheme.navyPrimary, size: 24),
                    ),
                    const SizedBox(width: 14),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Send Money',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: kInk,
                          ),
                        ),
                        Text(
                          'Direct bank transfer via CooBS Core Ledger',
                          style: TextStyle(fontSize: 12, color: kMut),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                // Source Account Card
                Card(
                  color: AppTheme.navyDark,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'SOURCE ACCOUNT',
                          style: TextStyle(
                            color: AppTheme.surfaceWhite.withAlpha(160),
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 0.5,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              _sourceController.text,
                              style: const TextStyle(
                                color: AppTheme.surfaceWhite,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                fontFamily: 'monospace',
                              ),
                            ),
                            if (widget.availableBalance != null)
                              Text(
                                'Available: ₱${widget.availableBalance!.toStringAsFixed(2)}',
                                style: const TextStyle(
                                  color: AppTheme.emeraldAccent,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // Destination Bank & Account
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Destination Details',
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 14),
                        DropdownButtonFormField<String>(
                          initialValue: _destinationBank,
                          decoration: const InputDecoration(
                            labelText: 'Destination Bank',
                            prefixIcon: Icon(Icons.account_balance_outlined),
                          ),
                          items: const [
                            DropdownMenuItem(value: 'COOBS', child: Text('CooBS Core Banking (Internal)')),
                            DropdownMenuItem(value: 'BDO', child: Text('BDO Unibank')),
                            DropdownMenuItem(value: 'BPI', child: Text('Bank of the Philippine Islands')),
                            DropdownMenuItem(value: 'MBTC', child: Text('Metrobank')),
                          ],
                          onChanged: (val) {
                            if (val != null) setState(() => _destinationBank = val);
                          },
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _destinationController,
                          decoration: const InputDecoration(
                            labelText: 'Destination Account Number',
                            hintText: 'ACC_10000002',
                            prefixIcon: Icon(Icons.credit_card_outlined),
                          ),
                          validator: (val) {
                            if (val == null || val.trim().isEmpty) {
                              return 'Enter destination account number';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 12),

                        // Quick recipient pills
                        Wrap(
                          spacing: 8,
                          runSpacing: 6,
                          children: _quickRecipients.map((rec) {
                            final isSelected = _destinationController.text == rec['account'];
                            return ActionChip(
                              label: Text('${rec['name']} (${rec['account']})'),
                              backgroundColor: isSelected
                                  ? (kDark ? const Color(0xFF2B4C8C) : AppTheme.navyPrimary.withAlpha(25))
                                  : null,
                              labelStyle: TextStyle(
                                fontSize: 11,
                                color: isSelected ? (kDark ? Colors.white : AppTheme.navyPrimary) : kInk,
                                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                              ),
                              onPressed: () {
                                setState(() {
                                  _destinationController.text = rec['account']!;
                                });
                              },
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // Amount & Remarks
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Transfer Amount (PHP)',
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _amountController,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                          decoration: const InputDecoration(
                            prefixText: '₱ ',
                            prefixStyle: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                            hintText: '0.00',
                          ),
                          validator: (val) {
                            if (val == null || val.trim().isEmpty) return 'Enter an amount';
                            final parsed = double.tryParse(val.trim());
                            if (parsed == null || parsed <= 0) return 'Enter a valid positive amount';
                            return null;
                          },
                        ),
                        const SizedBox(height: 12),

                        // Quick amount chips
                        Wrap(
                          spacing: 8,
                          children: _quickAmounts.map((amt) {
                            final strAmt = amt.toStringAsFixed(2);
                            final isSelected = _amountController.text == strAmt;
                            return ActionChip(
                              label: Text('₱${amt.toStringAsFixed(0)}'),
                              backgroundColor: isSelected
                                  ? AppTheme.emeraldAccent.withAlpha(50)
                                  : null,
                              labelStyle: TextStyle(
                                fontSize: 12,
                                fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                              ),
                              onPressed: () => setState(() => _amountController.text = strAmt),
                            );
                          }).toList(),
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _remarksController,
                          decoration: const InputDecoration(
                            labelText: 'Remarks / Notes',
                            hintText: 'Payment description',
                            prefixIcon: Icon(Icons.notes_rounded),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 28),

                // Submit Button
                ElevatedButton(
                  onPressed: _isSubmitting ? null : _submitTransfer,
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    backgroundColor: AppTheme.navyPrimary,
                    foregroundColor: AppTheme.surfaceWhite,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: _isSubmitting
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.5,
                          color: Colors.white,
                        ),
                      )
                    : const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.send_rounded, size: 20),
                          SizedBox(width: 8),
                          Text(
                            'Submit Transfer',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
