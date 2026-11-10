import 'dart:convert';

import 'package:app/features/qr/qr_payload.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('QR payload', () {
    test('round-trips account and amount', () {
      final p = decodeQrPayment(encodeQrPayment(const QrPayment('ACC_10000002', 50000)))!;
      expect(p.account, 'ACC_10000002');
      expect(p.amountCentavos, 50000);
    });

    test('carries only type, version, account and amount', () {
      final m = jsonDecode(encodeQrPayment(const QrPayment('ACC_10000002', 12345))) as Map;
      expect(m, {'t': 'COOBS_PAY', 'v': 1, 'acct': 'ACC_10000002', 'amt': '123.45'});
    });

    test('leaves the amount out when none is set', () {
      final raw = encodeQrPayment(const QrPayment('ACC_10000002'));
      expect((jsonDecode(raw) as Map).containsKey('amt'), isFalse);
      expect(decodeQrPayment(raw)!.amountCentavos, isNull);
    });

    test('is plain text, not a link a camera app would open', () {
      final raw = encodeQrPayment(const QrPayment('ACC_10000002', 100));
      expect(Uri.tryParse(raw)?.hasScheme ?? false, isFalse);
    });

    test('rejects codes that are not CooBS payment QRs', () {
      expect(decodeQrPayment(null), isNull);
      expect(decodeQrPayment('https://example.com/pay?acct=ACC_10000002'), isNull);
      expect(decodeQrPayment('{"t":"OTHER","acct":"ACC_10000002"}'), isNull);
      expect(decodeQrPayment('{"t":"COOBS_PAY"}'), isNull);
      expect(decodeQrPayment('{"t":"COOBS_PAY","acct":"12345"}'), isNull);
      expect(decodeQrPayment('{"t":"COOBS_PAY","acct":"ACC_1234"}'), isNull);
    });

    test('drops a bad amount but keeps the account', () {
      for (final amt in ['"0"', '"-5"', '"abc"', '"1.234"', '"600000.00"', '500']) {
        final p = decodeQrPayment('{"t":"COOBS_PAY","v":1,"acct":"ACC_10000002","amt":$amt}');
        expect(p?.account, 'ACC_10000002', reason: amt);
        expect(p?.amountCentavos, isNull, reason: amt);
      }
    });
  });
}
