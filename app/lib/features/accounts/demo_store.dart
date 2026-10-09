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
  final String ref = 'LN-2026-0001';
  final String reason;
  final double amt, m, total;
  final int term;
  double paid = 0;
  final List<Tx> tx = [];
  Loan(this.reason, this.amt, this.term, this.m) : total = r2(m * term);
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
                final isOut = t.direction.toUpperCase() == 'OUT';
                final signAmt = isOut ? -t.amount.abs() : t.amount.abs();
                final label = t.remarks.isNotEmpty
                    ? t.remarks
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

  final accts = <Acct>[
    Acct('Checking', 'ACC-1001-8842', 14750.50, [
      Tx('Transfer to ACC-1002-9915', 'TXN-9021', today, -150),
      Tx('Payroll deposit', 'CORP-PAYROLL · TXN-9018', today, 3250),
    ]),
    Acct('Savings', 'ACC-1002-9915', 85200, [
      Tx('Transfer from ACC-1001-8842', 'TXN-9020', today, 150),
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
  final bens = <Ben>[const Ben('Juan Dela Cruz', 'Brother', '+63 917 000 1111')];
  final alerts = <List<String>>[
    ['₱2,500.00 received from Maria Santos', 'Ref REF-UI-312972 · 2 min ago'],
    ['₱150.00 transferred to ACC-1002-9915', 'Ref TXN-9021 · Today'],
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
        } catch (_) {
          // Gracefully fallback to composite dashboard fixture if gateway is offline
          m1 = await ApiClient().loadFixtureJson('M1-mobile-dashboard.response.json');
        }
      }
      final k5 = await MobileRepo.myAccounts();
      final k6 = (await MobileRepo.accountTx())['accounts'] as Map<String, dynamic>? ?? {};
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
          final isOut = (t['direction'] ?? 'OUT').toString().toUpperCase() == 'OUT';
          final label = (t['remarks'] != null && '${t['remarks']}'.isNotEmpty)
              ? '${t['remarks']}'
              : (t['counterpartyName'] != null && '${t['counterpartyName']}'.isNotEmpty)
                  ? 'Transfer ${isOut ? 'to' : 'from'} ${t['counterpartyName']}'
                  : (t['category'] != null && '${t['category']}'.isNotEmpty)
                      ? '${t['category']}'
                      : '${t['referenceNo'] ?? t['trackingReference'] ?? 'Transaction'}';
          final ref = '${t['referenceNo'] ?? t['trackingReference'] ?? t['transactionId'] ?? ''}';
          txList.add(Tx(label, ref, DateTime(d.year, d.month, d.day), isOut ? -amt : amt));
        }
        return txList;
      }

      final parsed = <Acct>[];
      final liveRecent = (m1['recentTransactions'] as List? ?? []);
      for (final a in k5) {
        if (a is! Map) continue;
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

      if (parsed.isNotEmpty) {
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
                  '${b['bankCode'] ?? 'COOBS'} · ${b['accountNumber'] ?? ''}',
                  (b['mobileNumber'] as String?) ?? (b['mobile'] as String?) ?? '',
                  accountNumber: b['accountNumber'] as String?,
                  bankCode: b['bankCode'] as String?,
                )
            ]);
        }
      } catch (_) {}

      state = unavailable.isEmpty ? LoadState.ready : LoadState.partial;
    } catch (_) {
      state = LoadState.offline;
    }
    notifyListeners();
  }

  void simulate(String? mode) { forced = mode; load(); }
  void setKyc(String s) { kyc = s; _kycTouched = true; notifyListeners(); }

  /// Local effect of a successful (mock) remittance so the cards and history stay consistent.
  void applyRemittance(int from, double amt, double fee, String bank, String dest, String ref) {
    accts[from].bal = r2(accts[from].bal - amt - fee);
    accts[from].tx.insert(0, Tx('Transfer to $bank', ref, today, -amt));
    if (fee > 0) accts[from].tx.insert(0, Tx('Transfer fee', ref, today, -fee));
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
    accts.clear();
    accts.addAll([
      Acct('Checking', 'ACC-1001-8842', 14750.50, [
        Tx('Transfer to ACC-1002-9915', 'TXN-9021', today, -150),
        Tx('Payroll deposit', 'CORP-PAYROLL · TXN-9018', today, 3250),
      ]),
      Acct('Savings', 'ACC-1002-9915', 85200, [
        Tx('Transfer from ACC-1001-8842', 'TXN-9020', today, 150),
        Tx('Cash deposit', 'TXN-9004', DateTime(2026, 10, 2), 5000),
        Tx('Interest credit', 'INT-0930', DateTime(2026, 9, 30), 42.60),
      ]),
    ]);
    alerts.clear();
    alerts.addAll([
      ['₱2,500.00 received from Maria Santos', 'Ref REF-UI-312972 · 2 min ago'],
      ['₱150.00 transferred to ACC-1002-9915', 'Ref TXN-9021 · Today'],
      ['KYC update approved', 'Yesterday'],
    ]);
    notifyListeners();
  }
}
