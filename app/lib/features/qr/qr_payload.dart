import 'dart:convert';

import '../accounts/demo_store.dart';
import '../remittance/remittance_screen.dart';

/// What a CooBS payment QR carries: the receiving account and, optionally, an amount.
/// Nothing else (no name, no balance, no token) is ever encoded.
class QrPayment {
  final String account;
  final int? amountCentavos;
  const QrPayment(this.account, [this.amountCentavos]);
}

const _type = 'COOBS_PAY';
const _maxCentavos = 50000000; // ₱500,000.00, the per-transaction limit in R1 options.
final _acctPattern = RegExp(r'^ACC_\d{8}$');

/// Plain JSON text, not a URL: a phone's own camera app shows it as text and opens nothing.
/// Only the in-app scanner acts on it.
String encodeQrPayment(QrPayment p) => jsonEncode({
      't': _type,
      'v': 1,
      'acct': p.account,
      if (p.amountCentavos != null) 'amt': two(p.amountCentavos!),
    });

/// Null when [raw] is not a CooBS payment QR. A bad or missing amount drops only the amount.
QrPayment? decodeQrPayment(String? raw) {
  if (raw == null) return null;
  final Object? data;
  try {
    data = jsonDecode(raw);
  } catch (_) {
    return null;
  }
  if (data is! Map || data['t'] != _type || data['acct'] is! String) return null;
  final acct = normalizeAccountNumber(data['acct'] as String);
  if (!_acctPattern.hasMatch(acct)) return null;
  final amt = data['amt'] is String ? centavos(data['amt'] as String) : null;
  return QrPayment(acct, amt != null && amt > 0 && amt <= _maxCentavos ? amt : null);
}
