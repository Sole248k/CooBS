import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';

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
  const Ben(this.name, this.rel, this.mobile);
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
  bool hide = false;
  int cur = 0;
  final bens = <Ben>[const Ben('Juan Dela Cruz', 'Brother', '+63 917 000 1111')];
  final alerts = <List<String>>[
    ['₱2,500.00 received from Maria Santos', 'Ref REF-UI-312972 · 2 min ago'],
    ['₱150.00 transferred to ACC-1002-9915', 'Ref TXN-9021 · Today'],
    ['KYC update approved', 'Yesterday'],
  ];
  void _alert(String m) => alerts.insert(0, [m, 'Just now']);

  void toggleHide() { hide = !hide; notifyListeners(); }
  void setCur(int v) { cur = v; notifyListeners(); }
  List<Tx> get curTx => cur == 2 ? (loan?.tx ?? []) : accts[cur].tx;

  String? _check(int from, double a) {
    if (a <= 0) return 'Enter an amount above ₱0.00';
    if (a > accts[from].bal) return 'Insufficient funds in ${accts[from].no}';
    return null;
  }

  String? deposit(int from, int to, double a) {
    final e = _check(from, a);
    if (e != null) return e;
    accts[from].bal = r2(accts[from].bal - a);
    accts[to].bal = r2(accts[to].bal + a);
    accts[from].tx.insert(0, Tx('Deposit to ${accts[to].no}', 'Just now', today, -a));
    accts[to].tx.insert(0, Tx('Deposit from ${accts[from].no}', 'Just now', today, a));
    _alert('${peso(a)} moved from ${accts[from].no} to ${accts[to].no}');
    notifyListeners();
    return null;
  }

  String? send(int from, String bank, String no, double a) {
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
    accts[0].bal = r2(accts[0].bal + amt);
    accts[0].tx.insert(0, Tx('Loan proceeds', l.ref, today, amt));
    _alert('Loan ${l.ref} approved: ${peso(amt)} credited to Checking');
    cur = 2;
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
