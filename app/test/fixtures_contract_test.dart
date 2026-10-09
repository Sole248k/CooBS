import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

dynamic read(String n) => jsonDecode(File('assets/fixtures/$n').readAsStringSync());

void main() {
  test('every fixture is valid JSON', () {
    for (final f in Directory('assets/fixtures').listSync().whereType<File>().where((f) => f.path.endsWith('.json'))) {
      expect(() => jsonDecode(f.readAsStringSync()), returnsNormally, reason: f.path);
    }
  });

  test('accounts, transactions and remittance options have the fields the screens read', () {
    final accts = read('K5-my-accounts.response.json') as List;
    expect(accts.length, 2);
    for (final a in accts) {
      expect(a.keys, containsAll(['accountNumber', 'accountType', 'availableBalance', 'status']));
    }
    final tx = read('K6-account-transactions.response.json')['accounts'] as Map;
    for (final a in accts) {
      expect(tx[a['accountNumber']], isNotEmpty);
    }
    final r1 = read('R1-remittance-options.response.json');
    expect(r1['banks'].first.keys, containsAll(['code', 'name', 'fee', 'accountPattern', 'accountHint']));
    expect(r1['limits'].keys, containsAll(['minAmount', 'maxAmount']));
  });

  test('problem and KYC fixtures have the expected shape', () {
    for (final n in ['E1-insufficient-funds.problem.json', 'E2-validation-error.problem.json']) {
      expect((read(n) as Map).keys, containsAll(['title', 'status', 'detail', 'errorCode', 'invalidParams']));
    }
    expect(read('K2-kyc-intake.response.json')['status'], 'PENDING_REVIEW');
    expect(read('K2-kyc-intake-verified.response.json')['status'], 'VERIFIED');
    expect(read('K2-kyc-intake-rejected.response.json')['status'], 'REJECTED');
    expect(read('K1-kyc-intake-options.response.json').keys, containsAll(['idTypes', 'civilStatuses']));
  });

  test('transfer request fixture matches the M3 request keys', () {
    expect((read('M3-mobile-remittance.request.json') as Map).keys,
        containsAll(['sourceAccountNumber', 'destinationAccountNumber', 'destinationBankCode', 'amount', 'currency', 'category', 'remarks']));
  });
}
