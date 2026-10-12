import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';

import '../data/mobile_repo.dart';

import '../../core/config/app_config.dart';
import '../../core/models/dashboard_models.dart';
import '../../core/network/api_client.dart';

String peso(double v) => '₱${NumberFormat('#,##0.00').format(v)}';
String fmt(DateTime d) => DateFormat('dd MMM yyyy').format(d);
double r2(double v) => (v * 100).round() / 100;
final today = DateTime(2026, 10, 6);

/// Flat 12% p.a. demo rate (placeholder until the real loan contract exists).
double monthly(double p, int n) => r2(r2(p * (1 + .12 * n / 12)) / n);

/// Normalizes an account number to the canonical format (ACC_XXXXXXXX for CooBS).
String normalizeAccountNumber(String raw, [String bank = 'COOBS']) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty) return trimmed;
  if (bank.toUpperCase() == 'COOBS') {
    if (RegExp(r'^ACC_\d{4,8}$').hasMatch(trimmed)) return trimmed;
    if (RegExp(r'^[aA][cC][cC]_\d{4,8}$').hasMatch(trimmed)) return trimmed.toUpperCase();
    if (RegExp(r'^\d{4,8}$').hasMatch(trimmed)) return 'ACC_$trimmed';
    final m = RegExp(r'^[aA][cC][cC][-_ ]?(\d{4,8})$').firstMatch(trimmed);
    if (m != null) return 'ACC_${m.group(1)}';
    final m2 = RegExp(r'^[aA][cC][cC][-_ ]?(\d{4})[-_ ]?(\d{4})$').firstMatch(trimmed);
    if (m2 != null) return 'ACC_${m2.group(1)}${m2.group(2)}';
    final digits = trimmed.replaceAll(RegExp(r'[^0-9]'), '');
    if (digits.length == 8) return 'ACC_$digits';
  }
  return trimmed;
}

class Tx {
  final String label, ref;
  final DateTime date;
  final double amt;
  Tx(this.label, this.ref, this.date, this.amt);
}

class Acct {
  final String type, no;
  double bal;
  final List<Tx> tx;
  Acct(this.type, this.no, this.bal, this.tx);
}

class Ben {
  final String name, rel, mobile;
  final String? accountNumber;
  final String? bankCode;
  const Ben(this.name, this.rel, this.mobile, {this.accountNumber, this.bankCode});
}

class Bill {
  final int k;
  final DateTime due;
  final double amt, paid;
  const Bill(this.k, this.due, this.amt, this.paid);
  bool get isPaid => paid >= amt - 0.005;
}

class Loan {
  final String ref;
  final String reason;
  final double amt, m, total;
  final int term;
  double paid = 0;
  final List<Tx> tx = [];
  Loan(this.reason, this.amt, this.term, this.m, {this.ref = 'LN_10000006'}) : total = r2(m * term);
  double get balance => r2(total - paid);
  DateTime due(int k) => DateTime(today.year, today.month + k, 25);
  List<Bill> get bills => [
        for (var k = 1; k <= term; k++)
          Bill(k, due(k), m, (paid - (k - 1) * m).clamp(0, m).toDouble())
      ];
  Bill? get next {
    for (final b in bills) {
      if (!b.isPaid) return b;
    }
    return null;
  }
}

enum LoadState { loading, ready, partial, offline }

/// In-memory demo state (backed by ApiClient fixtures/network with local mutations).
class Demo extends ChangeNotifier {
  static final Demo i = Demo();
  bool isInitializedFromApi = false;

  Future<void> initFromApi({ApiClient? client, bool force = false}) async {
    if (isInitializedFromApi && !force) return;
    final api = client ?? ApiClient();
    try {
      final json = await api.get('/mobile/dashboard');
      final dash = MobileDashboardResponse.fromJson(json);
      if (accts.isNotEmpty && dash.primaryAccount.availableBalance > 0) {
        final mappedTx = dash.recentTransactions.isNotEmpty
            ? dash.recentTransactions.map((t) {
                final isReversal = t.trackingReference.toUpperCase().startsWith('RV-') || t.remarks.toUpperCase().contains('REVERSAL');
                final isOut = isReversal ? (t.direction.toUpperCase() == 'OUT') : (t.direction.toUpperCase() == 'OUT');
                final signAmt = isOut ? -t.amount.abs() : t.amount.abs();
                final label = t.remarks.isNotEmpty
                    ? t.remarks
                    : isReversal
                        ? (isOut ? 'Reversal Debit' : 'Reversal Refund')
                        : (isOut ? 'Transfer Out' : 'Received Funds');
                return Tx(
                  label,
                  t.trackingReference.isNotEmpty ? t.trackingReference : 'TXN',
                  t.submittedAt,
                  signAmt,
                );
              }).toList()
            : accts[0].tx;

        accts[0] = Acct(
          dash.primaryAccount.accountType.isNotEmpty ? dash.primaryAccount.accountType : accts[0].type,
          dash.primaryAccount.accountNumber.isNotEmpty ? dash.primaryAccount.accountNumber : accts[0].no,
          dash.primaryAccount.availableBalance,
          mappedTx,
        );
      }
      isInitializedFromApi = true;
      notifyListeners();
    } catch (_) {
      // Gracefully retain default demo values
    }
  }

  final List<Tx> _pendingLocalTx = [];
  final accts = <Acct>[
    Acct('Checking', 'ACC_10000001', 14750.50, [
      Tx('Transfer to ACC_10000002', 'TXN-9021', today, -150),
      Tx('Payroll deposit', 'CORP-PAYROLL · TXN-9018', today, 3250),
    ]),
    Acct('Savings', 'ACC_10000002', 85200, [
      Tx('Transfer from ACC_10000001', 'TXN-9020', today, 150),
      Tx('Cash deposit', 'TXN-9004', DateTime(2026, 10, 2), 5000),
      Tx('Interest credit', 'INT-0930', DateTime(2026, 9, 30), 42.60),
    ]),
  ];
  Loan? loan;
  LoadState state = LoadState.loading;
  String memberName = 'John D.', kyc = 'VERIFIED', primaryNo = '';
  Map<String, dynamic>? kycData;
  int unread = 0;
  List<String> unavailable = [];
  String? forced; // tester: 'partial' | 'offline'
  bool _kycTouched = false;
  bool hide = false;
  int cur = 0;
  int? creditScore;
  Map<String, double> spendingBreakdown = {};
  double totalDeposits = 0;
  double totalLoans = 0;
  final bens = <Ben>[
    const Ben('Juan Dela Cruz', 'COOBS · ACC_10000002', '+63 918 222 3333', accountNumber: 'ACC_10000002', bankCode: 'COOBS'),
    const Ben('Maria Santos', 'COOBS · ACC_10000004', '+63 917 000 1111', accountNumber: 'ACC_10000004', bankCode: 'COOBS'),
    const Ben('Jose Bautista', 'COOBS · ACC_10000008', '+63 919 444 5555', accountNumber: 'ACC_10000008', bankCode: 'COOBS'),
  ];
  final alerts = <List<String>>[
    ['₱2,500.00 received from Maria Santos', 'Ref REF-UI-312972 · 2 min ago'],
    ['₱150.00 transferred to ACC_10000002', 'Ref TXN-9021 · Today'],
    ['KYC update approved', 'Yesterday'],
  ];
  void _alert(String m) => alerts.insert(0, [m, 'Just now']);

  /// Loads Customer 360 data from the fixtures (M1, K5, K6, N1) or live gateway.
  Future<void> load() async {
    state = LoadState.loading;
    notifyListeners();
    try {
      if (forced == 'offline') throw Exception('simulated outage');
      Map<String, dynamic> m1;
      if (AppConfig.useFixtures) {
        m1 = await ApiClient().loadFixtureJson('M1-mobile-dashboard.response.json');
      } else {
        try {
          m1 = await ApiClient().get('/mobile/dashboard');
          if (m1.isEmpty || m1['member'] == null) {
            m1 = await ApiClient().loadFixtureJson('M1-mobile-dashboard.response.json');
          }
        } catch (_) {
          // Gracefully fallback to composite dashboard fixture if gateway is offline
          m1 = await ApiClient().loadFixtureJson('M1-mobile-dashboard.response.json');
        }
      }
      final k5 = await MobileRepo.myAccounts();
      var k6 = (await MobileRepo.accountTx())['accounts'] as Map<String, dynamic>? ?? {};
      if (k6.isEmpty) {
        try {
          k6 = (await ApiClient().loadFixtureJson('K6-account-transactions.response.json'))['accounts'] as Map<String, dynamic>? ?? {};
        } catch (_) {}
      }
      final n1 = await MobileRepo.notifications();
      if (m1['member'] is Map) {
        memberName = (m1['member']['displayName'] as String?) ?? memberName;
        if (!_kycTouched) kyc = (m1['member']['kycStatus'] as String?) ?? kyc;
      }
      if (m1['primaryAccount'] is Map) {
        primaryNo = (m1['primaryAccount']['accountNumber'] as String?) ?? primaryNo;
      }
      unread = (m1['unreadNotifications'] as num?)?.toInt() ?? 0;
      unavailable = [for (final x in (m1['unavailable'] as List? ?? [])) '$x'];
      if (forced == 'partial') unavailable = ['recentTransactions'];
      double parseBal(dynamic val) {
        if (val is num) return val.toDouble();
        if (val != null) {
          final clean = val.toString().replaceAll(RegExp(r'[^0-9.]'), '');
          return double.tryParse(clean) ?? 0.0;
        }
        return 0.0;
      }

      String parseType(dynamic raw) {
        final s = (raw ?? 'Checking').toString().trim();
        if (s.isEmpty) return 'Checking';
        return s[0].toUpperCase() + (s.length > 1 ? s.substring(1).toLowerCase() : '');
      }

      List<Tx> parseTxList(List rawList) {
        final txList = <Tx>[];
        for (final t in rawList) {
          if (t is! Map) continue;
          final rawDate = (t['createdAt'] ?? t['submittedAt']) as String?;
          final d = rawDate != null ? DateTime.tryParse(rawDate)?.toLocal() ?? today : today;
          final amt = parseBal(t['amount']);
          final ref = '${t['referenceNo'] ?? t['trackingReference'] ?? t['transactionId'] ?? ''}';
          final categoryUpper = (t['category'] ?? '').toString().toUpperCase();
          final isReversal = categoryUpper == 'REVERSAL' || ref.toUpperCase().startsWith('RV-');
          final rawDir = (t['direction'] ?? '').toString().toUpperCase();
          final isOut = isReversal ? (rawDir == 'OUT') : (rawDir == 'OUT' || rawDir.isEmpty);
          final label = (t['remarks'] != null && '${t['remarks']}'.isNotEmpty)
              ? '${t['remarks']}'
              : (t['counterpartyName'] != null && '${t['counterpartyName']}'.isNotEmpty)
                  ? (isReversal ? '${t['counterpartyName']}' : 'Transfer ${isOut ? 'to' : 'from'} ${t['counterpartyName']}')
                  : isReversal
                      ? (isOut ? 'Reversal Debit' : 'Reversal Refund')
                      : (t['category'] != null && '${t['category']}'.isNotEmpty)
                          ? '${t['category']}'
                          : '${t['referenceNo'] ?? t['trackingReference'] ?? 'Transaction'}';
          txList.add(Tx(label, ref, DateTime(d.year, d.month, d.day), isOut ? -amt : amt));
        }
        return txList;
      }

      final parsed = <Acct>[];
      final liveRecent = (m1['recentTransactions'] as List? ?? []);
      Map? loanAccountData;
      for (final a in k5) {
        if (a is! Map) continue;
        final rawType = (a['accountType'] ?? '').toString().toUpperCase();
        if (rawType == 'LOAN') {
          loanAccountData = a;
          continue; // Loan is a liability/credit product; rendered exclusively in the Credit Line card
        }
        final acctNo = (a['accountNumber'] as String?) ?? '';
        final acctType = parseType(a['accountType']);
        final bal = parseBal(a['availableBalance'] ?? a['balance']);
        final rawTxList = (!AppConfig.useFixtures && liveRecent.isNotEmpty && (acctNo == primaryNo || k5.indexOf(a) == 0))
            ? liveRecent
            : (((k6[acctNo] as List?)?.isNotEmpty == true)
                ? (k6[acctNo] as List)
                : (acctNo == primaryNo ? liveRecent : []));
        final txList = parseTxList(rawTxList);
        parsed.add(Acct(acctType, acctNo, bal, txList));
      }

      if (loanAccountData != null) {
        final lNo = (loanAccountData['accountNumber'] as String?) ?? 'LN_10000006';
        final lBal = parseBal(loanAccountData['availableBalance'] ?? loanAccountData['balance']);
        final rawLoanTx = ((k6[lNo] as List?)?.isNotEmpty == true) ? (k6[lNo] as List) : [];
        final parsedLoanTx = parseTxList(rawLoanTx);
        final defaultLoanAmt = lBal > 0 ? lBal : 50000.0;
        if (loan == null) {
          final lObj = Loan('Credit Line', defaultLoanAmt, 12, monthly(defaultLoanAmt, 12), ref: lNo);
          lObj.tx.addAll(parsedLoanTx);
          loan = lObj;
        }
      }

      if (parsed.isNotEmpty) {
        // Ensure primary account is first in carousel if present
        if (primaryNo.isNotEmpty) {
          final primIdx = parsed.indexWhere((a) => a.no == primaryNo);
          if (primIdx > 0) {
            final prim = parsed.removeAt(primIdx);
            parsed.insert(0, prim);
          }
        }
        // Retain any pending local transfers not yet in server list
        for (final pTx in _pendingLocalTx) {
          if (!parsed[0].tx.any((t) => t.ref == pTx.ref)) {
            parsed[0].tx.insert(0, pTx);
          }
        }
        accts
          ..clear()
          ..addAll(parsed);
      } else if (m1['primaryAccount'] is Map) {
        final p = m1['primaryAccount'] as Map;
        final pNo = (p['accountNumber'] as String?) ?? '';
        if (pNo.isNotEmpty) {
          final pType = parseType(p['accountType']);
          final pBal = parseBal(p['availableBalance'] ?? p['balance']);
          final txList = parseTxList(liveRecent);
          accts
            ..clear()
            ..add(Acct(pType, pNo, pBal, txList));
        }
      }
      final notifList = (n1['content'] as List?) ?? (n1['items'] as List?) ?? [];
      for (final n in notifList.reversed) {
        final rawCreated = n['createdAt'] as String?;
        final dt = rawCreated != null ? DateTime.tryParse(rawCreated)?.toLocal() ?? today : today;
        alerts.insert(0, ['${n['message'] ?? n['title'] ?? 'Alert'}', fmt(dt)]);
      }

      // Fetch Live Customer Profile & KYC
      try {
        final profile = await MobileRepo.customerProfile();
        if (profile.isNotEmpty) {
          if (profile['displayName'] != null && '${profile['displayName']}'.isNotEmpty) {
            memberName = '${profile['displayName']}';
          }
          if (profile['kycStatus'] != null && !_kycTouched) {
            kyc = '${profile['kycStatus']}';
          }
          if (profile['kyc'] is Map) {
            kycData = Map<String, dynamic>.from(profile['kyc'] as Map);
          }
        }
      } catch (_) {}

      // Fetch Live Beneficiaries
      try {
        final bList = await MobileRepo.beneficiaries();
        if (bList.isNotEmpty) {
          bens
            ..clear()
            ..addAll([
              for (final b in bList)
                Ben(
                  (b['nickname'] as String?) ?? (b['beneficiaryName'] as String?) ?? (b['name'] as String?) ?? 'Beneficiary',
                  '${b['bankCode'] ?? 'COOBS'} · ${normalizeAccountNumber((b['accountNumber'] as String?) ?? '', (b['bankCode'] as String?) ?? 'COOBS')}',
                  (b['mobileNumber'] as String?) ?? (b['mobile'] as String?) ?? '',
                  accountNumber: normalizeAccountNumber((b['accountNumber'] as String?) ?? '', (b['bankCode'] as String?) ?? 'COOBS'),
                  bankCode: b['bankCode'] as String?,
                )
            ]);
        }
      } catch (_) {}

      // Fetch Live Scores, Habits & 360 Totals (MOB-08)
      try {
        final c3 = await MobileRepo.customer360();
        if (c3['creditScore'] != null) {
          creditScore = (c3['creditScore'] as num?)?.toInt();
        }
        if (c3['kycStatus'] != null && !_kycTouched) {
          kyc = '${c3['kycStatus']}';
        }
        if (c3['displayName'] != null && '${c3['displayName']}'.isNotEmpty && (memberName.isEmpty || memberName == 'Member' || memberName == 'John D.')) {
          memberName = '${c3['displayName']}';
        }
        if (c3['totals'] is Map) {
          final tot = c3['totals'] as Map;
          totalDeposits = parseBal(tot['deposits']);
          totalLoans = parseBal(tot['loans']);
        }
        if (c3['spendingBreakdown'] is Map && c3['spendingBreakdown']['items'] is List) {
          final items = c3['spendingBreakdown']['items'] as List;
          final map = <String, double>{};
          for (final it in items) {
            if (it is Map) {
              final cat = it['category']?.toString() ?? 'Other';
              final amt = parseBal(it['amount']);
              map[cat] = amt;
            }
          }
          spendingBreakdown = map;
        }
      } catch (_) {}

      state = unavailable.isEmpty ? LoadState.ready : LoadState.partial;
    } catch (_) {
      state = LoadState.offline;
    }
    notifyListeners();
  }

  /// On-demand fetch of credit score, spending habits breakdown, and totals (MOB-08)
  Future<void> fetchScoresAndHabits([dynamic id]) async {
    try {
      final c3 = await MobileRepo.customer360(id);
      if (c3['creditScore'] != null) {
        creditScore = (c3['creditScore'] as num?)?.toInt();
      }
      if (c3['kycStatus'] != null && !_kycTouched) {
        kyc = '${c3['kycStatus']}';
      }
      if (c3['displayName'] != null && '${c3['displayName']}'.isNotEmpty && (memberName.isEmpty || memberName == 'Member' || memberName == 'John D.')) {
        memberName = '${c3['displayName']}';
      }
      if (c3['totals'] is Map) {
        final tot = c3['totals'] as Map;
        final cleanD = tot['deposits']?.toString().replaceAll(RegExp(r'[^0-9.]'), '') ?? '0';
        final cleanL = tot['loans']?.toString().replaceAll(RegExp(r'[^0-9.]'), '') ?? '0';
        totalDeposits = double.tryParse(cleanD) ?? 0.0;
        totalLoans = double.tryParse(cleanL) ?? 0.0;
      }
      if (c3['spendingBreakdown'] is Map && c3['spendingBreakdown']['items'] is List) {
        final items = c3['spendingBreakdown']['items'] as List;
        final map = <String, double>{};
        for (final it in items) {
          if (it is Map) {
            final cat = it['category']?.toString() ?? 'Other';
            final cleanAmt = it['amount']?.toString().replaceAll(RegExp(r'[^0-9.]'), '') ?? '0';
            map[cat] = double.tryParse(cleanAmt) ?? 0.0;
          }
        }
        spendingBreakdown = map;
      }
      notifyListeners();
    } catch (_) {}
  }

  void simulate(String? mode) { forced = mode; load(); }
  void setKyc(String s) { kyc = s; _kycTouched = true; notifyListeners(); }

  /// Local effect of a successful remittance so the cards and history stay consistent.
  void applyRemittance(int from, double amt, double fee, String bank, String dest, String ref) {
    if (from < 0 || from >= accts.length) return;
    final tx = Tx('Transfer to $bank', ref, today, -amt);
    _pendingLocalTx.insert(0, tx);
    accts[from].bal = r2(accts[from].bal - amt - fee);
    accts[from].tx.insert(0, tx);
    if (fee > 0) {
      final feeTx = Tx('Transfer fee', ref, today, -fee);
      _pendingLocalTx.insert(0, feeTx);
      accts[from].tx.insert(0, feeTx);
    }
    _alert('${peso(amt)} transferred to $dest ($bank). Ref $ref');
    notifyListeners();
  }

  void toggleHide() { hide = !hide; notifyListeners(); }
  void setCur(int v) { cur = v; notifyListeners(); }
  bool get isLoanPage => cur >= accts.length;
  List<Tx> get curTx {
    if (isLoanPage) return loan?.tx ?? [];
    if (cur >= 0 && cur < accts.length) return accts[cur].tx;
    return [];
  }

  String? _check(int from, double a) {
    if (from < 0 || from >= accts.length) return 'Account not found';
    if (a <= 0) return 'Enter an amount above ₱0.00';
    if (a > accts[from].bal) return 'Insufficient funds in ${accts[from].no}';
    return null;
  }

  String? deposit(int from, int? to, double a) {
    if (from < 0 || from >= accts.length) return 'Source account not found';
    if (a <= 0) return 'Enter an amount above ₱0.00';
    if (to != null && to >= 0 && to < accts.length && to != from) {
      final e = _check(from, a);
      if (e != null) return e;
      accts[from].bal = r2(accts[from].bal - a);
      accts[to].bal = r2(accts[to].bal + a);
      accts[from].tx.insert(0, Tx('Deposit to ${accts[to].no}', 'Just now', today, -a));
      accts[to].tx.insert(0, Tx('Deposit from ${accts[from].no}', 'Just now', today, a));
      _alert('${peso(a)} moved from ${accts[from].no} to ${accts[to].no}');
    } else {
      // Cash/check cash-in directly to account
      accts[from].bal = r2(accts[from].bal + a);
      accts[from].tx.insert(0, Tx('Cash deposit', 'Just now', today, a));
      _alert('${peso(a)} deposited to ${accts[from].no}');
    }
    notifyListeners();
    return null;
  }

  String? send(int from, String bank, String no, double a) {
    if (from < 0 || from >= accts.length) return 'Source account not found';
    if (no.isEmpty) return 'Enter the recipient account number';
    final e = _check(from, a);
    if (e != null) return e;
    accts[from].bal = r2(accts[from].bal - a);
    accts[from].tx.insert(0, Tx('Transfer to $bank', no, today, -a));
    _alert('${peso(a)} sent to $bank ($no)');
    notifyListeners();
    return null;
  }

  void applyLoan(String reason, double amt, int term) {
    final l = Loan(reason, amt, term, monthly(amt, term));
    loan = l;
    if (accts.isNotEmpty) {
      accts[0].bal = r2(accts[0].bal + amt);
      accts[0].tx.insert(0, Tx('Loan proceeds', l.ref, today, amt));
      _alert('Loan ${l.ref} approved: ${peso(amt)} credited to ${accts[0].type}');
    } else {
      _alert('Loan ${l.ref} approved for ${peso(amt)}');
    }
    cur = accts.length;
    notifyListeners();
  }

  String? payAdvance(int from, double a) {
    final l = loan!;
    if (a > l.balance + 0.005) return 'Amount is more than the outstanding balance';
    final e = _check(from, a);
    if (e != null) return e;
    accts[from].bal = r2(accts[from].bal - a);
    l.paid = r2(l.paid + a);
    accts[from].tx.insert(0, Tx('Loan payment', l.ref, today, -a));
    l.tx.insert(0, Tx('Loan payment', l.ref, today, -a));
    _alert('${peso(a)} paid toward loan ${l.ref}');
    notifyListeners();
    return null;
  }

  void resetLoan() { loan = null; notifyListeners(); }
  void addBen(Ben b) { bens.add(b); notifyListeners(); }

  void reset() {
    isInitializedFromApi = false;
    loan = null;
    hide = false;
    cur = 0;
    state = LoadState.loading;
    _kycTouched = false;
    primaryNo = '';
    unread = 0;
    unavailable.clear();
    _pendingLocalTx.clear();
    accts.clear();
    accts.addAll([
      Acct('Checking', 'ACC_10000001', 14750.50, [
        Tx('Transfer to ACC_10000002', 'TXN-9021', today, -150),
        Tx('Payroll deposit', 'CORP-PAYROLL · TXN-9018', today, 3250),
      ]),
      Acct('Savings', 'ACC_10000002', 85200, [
        Tx('Transfer from ACC_10000001', 'TXN-9020', today, 150),
        Tx('Cash deposit', 'TXN-9004', DateTime(2026, 10, 2), 5000),
        Tx('Interest credit', 'INT-0930', DateTime(2026, 9, 30), 42.60),
      ]),
    ]);
    alerts.clear();
    alerts.addAll([
      ['₱2,500.00 received from Maria Santos', 'Ref REF-UI-312972 · 2 min ago'],
      ['₱150.00 transferred to ACC_10000002', 'Ref TXN-9021 · Today'],
      ['KYC update approved', 'Yesterday'],
    ]);
    notifyListeners();
  }
}
