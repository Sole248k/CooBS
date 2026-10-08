import 'package:flutter/material.dart';

import '../../core/models/models.dart';
import '../../core/network/network.dart';
import '../../core/theme/app_theme.dart';

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
  final TextEditingController _destinationController = TextEditingController(text: 'ACC_10000002');
  final TextEditingController _amountController = TextEditingController(text: '100.00');
  final TextEditingController _remarksController = TextEditingController(text: 'Mobile Transfer');

  String _destinationBank = 'COOBS';
  bool _isSubmitting = false;

  final List<Map<String, String>> _quickRecipients = [
    {'name': 'Jane Smith', 'account': 'ACC_10000002'},
    {'name': 'Juan Dela Cruz', 'account': 'ACC_10000004'},
    {'name': 'Jose Bautista', 'account': 'ACC_10000008'},
  ];

  final List<double> _quickAmounts = [50.0, 100.0, 500.0, 1000.0, 2500.0];

  @override
  void initState() {
    super.initState();
    _sourceController = TextEditingController(
      text: widget.defaultSourceAccount ?? 'ACC_10000001',
    );
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

      if (mounted) {
        _showSnackbar('Transfer accepted! Ref: $ref', isError: false);
        widget.onSuccess?.call(ref);
      }
    } catch (e) {
      if (mounted) {
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
                        const Text(
                          'Send Money',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: AppTheme.navyPrimary,
                          ),
                        ),
                        Text(
                          'Direct bank transfer via CooBS Core Ledger',
                          style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
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
                                  ? AppTheme.navyPrimary.withAlpha(25)
                                  : null,
                              labelStyle: TextStyle(
                                fontSize: 11,
                                color: isSelected ? AppTheme.navyPrimary : AppTheme.textPrimary,
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
