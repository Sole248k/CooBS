import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';

import '../data/mobile_repo.dart';

import '../../core/config/app_config.dart';
import '../../core/models/dashboard_models.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/secure_storage_service.dart';

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
  final String status;
  final String? failureCode;
  Tx(this.label, this.ref, this.date, this.amt, {this.status = 'POSTED', this.failureCode});
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

/// Model for Cooperative Virtual "Ipon" Vaults / Sub-Savings Goals
class IponVault {
  final String id;
  final String name;
  final double targetAmount;
  double currentAmount;
  final String targetDate;
  final String category; // 'Emergency', 'Community', 'Cooperative', 'Education'
  final int iconCode;
  final int colorValue;

  IponVault({
    required this.id,
    required this.name,
    required this.targetAmount,
    required this.currentAmount,
    required this.targetDate,
    required this.category,
    required this.iconCode,
    required this.colorValue,
  });

  double get progress => targetAmount > 0 ? (currentAmount / targetAmount).clamp(0.0, 1.0) : 0.0;
  bool get isCompleted => currentAmount >= targetAmount - 0.01;
  double get remaining => (targetAmount - currentAmount).clamp(0.0, double.infinity);
}

/// Model for Cooperative Member Share Capital Subscription and Equity
class ShareCapitalModel {
  final int shareCapitalId;
  final String certificateNo;
  int subscribedShares;
  int paidUpShares;
  final double parValue;
  double totalPaidAmount;
  final double subscribedAmount;
  final int minimumRequiredShares;
  final double dividendRate;
  String membershipType;
  bool hasVotingRights;
  double completionRatio;
  final String status;

  ShareCapitalModel({
    required this.shareCapitalId,
    required this.certificateNo,
    required this.subscribedShares,
    required this.paidUpShares,
    required this.parValue,
    required this.totalPaidAmount,
    required this.subscribedAmount,
    required this.minimumRequiredShares,
    required this.dividendRate,
    required this.membershipType,
    required this.hasVotingRights,
    required this.completionRatio,
    required this.status,
  });

  factory ShareCapitalModel.fromMap(Map<String, dynamic> m) {
    final subShares = (m['subscribedShares'] as num?)?.toInt() ?? 500;
    final paidShares = (m['paidUpShares'] as num?)?.toInt() ?? 250;
    final pVal = (m['parValue'] as num?)?.toDouble() ?? 100.0;
    final totalPaid = (m['totalPaidAmount'] as num?)?.toDouble() ?? (paidShares * pVal);
    final subAmt = (m['subscribedAmount'] as num?)?.toDouble() ?? (subShares * pVal);
    final minReq = (m['minimumRequiredShares'] as num?)?.toInt() ?? 20;

    return ShareCapitalModel(
      shareCapitalId: (m['shareCapitalId'] as num?)?.toInt() ?? 100,
      certificateNo: m['certificateNo']?.toString() ?? 'SC-2024-001001',
      subscribedShares: subShares,
      paidUpShares: paidShares,
      parValue: pVal,
      totalPaidAmount: totalPaid,
      subscribedAmount: subAmt,
      minimumRequiredShares: minReq,
      dividendRate: (m['dividendRate'] as num?)?.toDouble() ?? 7.25,
      membershipType: m['membershipType']?.toString() ?? (paidShares >= minReq ? 'REGULAR' : 'ASSOCIATE'),
      hasVotingRights: m['hasVotingRights'] == true || paidShares >= minReq,
      completionRatio: (m['completionRatio'] as num?)?.toDouble() ?? (subAmt > 0 ? (totalPaid / subAmt).clamp(0.0, 1.0) : 0.5),
      status: m['status']?.toString() ?? 'ACTIVE',
    );
  }
}

/// Model for Cooperative Patronage Refund and Share Capital Dividend Declarations
class DividendPayoutModel {
  final int dividendId;
  final int fiscalYear;
  final double shareCapitalDividend;
  final double patronageRefund;
  final double totalPayout;
  final String disposition;
  final String payoutDate;
  final String referenceNo;
  final String status;

  DividendPayoutModel({
    required this.dividendId,
    required this.fiscalYear,
    required this.shareCapitalDividend,
    required this.patronageRefund,
    required this.totalPayout,
    required this.disposition,
    required this.payoutDate,
    required this.referenceNo,
    required this.status,
  });

  factory DividendPayoutModel.fromMap(Map<String, dynamic> m) {
    return DividendPayoutModel(
      dividendId: (m['dividendId'] as num?)?.toInt() ?? 0,
      fiscalYear: (m['fiscalYear'] as num?)?.toInt() ?? DateTime.now().year,
      shareCapitalDividend: (m['shareCapitalDividend'] as num?)?.toDouble() ?? 0.0,
      patronageRefund: (m['patronageRefund'] as num?)?.toDouble() ?? 0.0,
      totalPayout: (m['totalPayout'] as num?)?.toDouble() ?? 0.0,
      disposition: m['disposition']?.toString() ?? 'CREDITED_TO_SAVINGS',
      payoutDate: m['payoutDate']?.toString() ?? '',
      referenceNo: m['referenceNo']?.toString() ?? '',
      status: m['status']?.toString() ?? 'PAID',
    );
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
                  status: t.status.isNotEmpty ? t.status : 'POSTED',
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
  final Map<String, String> accountNicknames = {};

  String nicknameFor(String accountNo) => accountNicknames[accountNo] ?? '';

  Future<void> setNickname(String accountNo, String nick) async {
    final clean = nick.trim();
    if (clean.isEmpty) {
      accountNicknames.remove(accountNo);
      try {
        await SecureStorageService().delete('coobs_nick_$accountNo');
      } catch (_) {}
    } else {
      accountNicknames[accountNo] = clean;
      try {
        await SecureStorageService().write('coobs_nick_$accountNo', clean);
      } catch (_) {}
    }
    notifyListeners();
  }

  // --- Feature 1: Smart Envelopes Category Budgeting ---
  final Map<String, double> categoryBudgets = {
    'Groceries & Food': 10000.0,
    'Utilities & Bills': 5000.0,
    'Shopping & Retail': 4000.0,
    'Transportation': 2500.0,
    'Entertainment': 1500.0,
    'Other': 2000.0,
    'FOOD': 10000.0,
    'UTILITIES': 5000.0,
    'SHOPPING': 4000.0,
    'TRANSPORT': 2500.0,
    'ENTERTAINMENT': 1500.0,
  };

  double getBudgetForCategory(String cat) {
    if (categoryBudgets.containsKey(cat)) return categoryBudgets[cat]!;
    for (final e in categoryBudgets.entries) {
      if (e.key.toLowerCase().contains(cat.toLowerCase()) || cat.toLowerCase().contains(e.key.toLowerCase())) {
        return e.value;
      }
    }
    return 3000.0;
  }

  void updateCategoryBudget(String cat, double newLimit) {
    categoryBudgets[cat] = newLimit;
    notifyListeners();
  }

  // --- Feature 2: Virtual "Ipon" Vaults ---
  final List<IponVault> iponVaults = [
    IponVault(
      id: 'VAULT-01',
      name: 'Emergency Buffer',
      targetAmount: 30000.0,
      currentAmount: 18500.0,
      targetDate: 'Dec 2026',
      category: 'Emergency',
      iconCode: 0xe5ce, // Icons.shield_rounded
      colorValue: 0xFF0D9488, // Co-op Teal
    ),
    IponVault(
      id: 'VAULT-02',
      name: 'Paluwagan & Holidays',
      targetAmount: 15000.0,
      currentAmount: 9200.0,
      targetDate: 'Nov 2026',
      category: 'Community',
      iconCode: 0xe59c, // Icons.savings_rounded
      colorValue: 0xFF2563EB, // Sapphire
    ),
    IponVault(
      id: 'VAULT-03',
      name: 'Co-op Share Capital',
      targetAmount: 20000.0,
      currentAmount: 7300.0,
      targetDate: 'Jan 2027',
      category: 'Cooperative',
      iconCode: 0xe06e, // Icons.account_balance_rounded
      colorValue: 0xFFF59E0B, // Amber
    ),
  ];

  double get totalEarmarkedInVaults => iponVaults.fold(0.0, (sum, v) => sum + v.currentAmount);

  Future<String?> allocateToVault(String vaultId, double amount, {String? notes}) async {
    if (amount <= 0) return 'Enter an amount greater than ₱0.00';
    final v = iponVaults.firstWhere((x) => x.id == vaultId, orElse: () => throw 'Vault not found');
    if (accts.isEmpty) return 'No accounts available';
    final savings = accts.length > 1 ? accts[1] : accts[0];
    if (savings.bal < amount) {
      return 'Insufficient funds in ${savings.type} (${peso(savings.bal)})';
    }

    if (!AppConfig.useFixtures) {
      try {
        final res = await MobileRepo.allocateToVault(vaultId, amount, notes: notes);
        if (res['currentAmount'] != null) {
          v.currentAmount = (res['currentAmount'] as num).toDouble();
        } else {
          v.currentAmount = r2(v.currentAmount + amount);
        }
      } catch (_) {
        v.currentAmount = r2(v.currentAmount + amount);
      }
    } else {
      v.currentAmount = r2(v.currentAmount + amount);
    }

    savings.bal = r2(savings.bal - amount);
    savings.tx.insert(0, Tx('Earmarked for ${v.name}', 'VAULT-${DateTime.now().millisecondsSinceEpoch % 10000}', today, -amount));
    _alert('${peso(amount)} earmarked for ${v.name}');
    notifyListeners();
    return null;
  }

  Future<String?> releaseFromVault(String vaultId, double amount, {String? notes}) async {
    if (amount <= 0) return 'Enter an amount greater than ₱0.00';
    final v = iponVaults.firstWhere((x) => x.id == vaultId, orElse: () => throw 'Vault not found');
    if (v.currentAmount < amount) {
      return 'Vault only holds ${peso(v.currentAmount)}';
    }
    if (accts.isEmpty) return 'No accounts available';
    final savings = accts.length > 1 ? accts[1] : accts[0];

    if (!AppConfig.useFixtures) {
      try {
        final res = await MobileRepo.releaseFromVault(vaultId, amount, notes: notes);
        if (res['currentAmount'] != null) {
          v.currentAmount = (res['currentAmount'] as num).toDouble();
        } else {
          v.currentAmount = r2(v.currentAmount - amount);
        }
      } catch (_) {
        v.currentAmount = r2(v.currentAmount - amount);
      }
    } else {
      v.currentAmount = r2(v.currentAmount - amount);
    }

    savings.bal = r2(savings.bal + amount);
    savings.tx.insert(0, Tx('Released from ${v.name}', 'VAULT-${DateTime.now().millisecondsSinceEpoch % 10000}', today, amount));
    _alert('${peso(amount)} released back to ${savings.type}');
    notifyListeners();
    return null;
  }

  Future<void> createVault({
    required String name,
    required double targetAmount,
    double initialAmount = 0.0,
    required String targetDate,
    required String category,
    int? iconCode,
    int? colorValue,
  }) async {
    String id = 'VAULT-${DateTime.now().millisecondsSinceEpoch % 10000}';
    final resolvedIcon = iconCode ?? 0xe59c;
    final resolvedColor = colorValue ?? 0xFF0D9488;

    if (!AppConfig.useFixtures) {
      try {
        final res = await MobileRepo.createVault({
          'name': name,
          'targetAmount': targetAmount,
          'initialAmount': initialAmount,
          'targetDate': targetDate,
          'category': category,
          'iconCode': resolvedIcon,
          'colorValue': resolvedColor,
          if (accts.isNotEmpty) 'accountNumber': (accts.length > 1 ? accts[1] : accts[0]).no,
        });
        if (res['id'] != null || res['vaultReference'] != null) {
          id = '${res['id'] ?? res['vaultReference']}';
        }
      } catch (_) {}
    }

    final v = IponVault(
      id: id,
      name: name,
      targetAmount: targetAmount,
      currentAmount: 0.0,
      targetDate: targetDate,
      category: category,
      iconCode: resolvedIcon,
      colorValue: resolvedColor,
    );
    iponVaults.add(v);
    if (initialAmount > 0) {
      await allocateToVault(id, initialAmount);
    } else {
      notifyListeners();
    }
  }

  Future<void> closeVault(String vaultId) async {
    final v = iponVaults.firstWhere((x) => x.id == vaultId, orElse: () => throw 'Vault not found');
    if (!AppConfig.useFixtures) {
      try {
        await MobileRepo.closeVault(vaultId);
      } catch (_) {}
    }
    if (v.currentAmount > 0 && accts.isNotEmpty) {
      final savings = accts.length > 1 ? accts[1] : accts[0];
      savings.bal = r2(savings.bal + v.currentAmount);
      savings.tx.insert(0, Tx('Vault Closed: ${v.name}', 'VAULT-CLS', today, v.currentAmount));
    }
    iponVaults.removeWhere((x) => x.id == vaultId);
    _alert('${v.name} closed');
    notifyListeners();
  }

  // --- Feature: Co-op Share Capital & Patronage Dividends ---
  ShareCapitalModel shareCapital = ShareCapitalModel(
    shareCapitalId: 100,
    certificateNo: 'SC-2024-001001',
    subscribedShares: 500,
    paidUpShares: 250,
    parValue: 100.0,
    totalPaidAmount: 25000.0,
    subscribedAmount: 50000.0,
    minimumRequiredShares: 20,
    dividendRate: 7.25,
    membershipType: 'REGULAR',
    hasVotingRights: true,
    completionRatio: 0.5,
    status: 'ACTIVE',
  );

  List<DividendPayoutModel> dividendHistory = [
    DividendPayoutModel(
      dividendId: 1,
      fiscalYear: 2025,
      shareCapitalDividend: 1812.50,
      patronageRefund: 940.00,
      totalPayout: 2752.50,
      disposition: 'CREDITED_TO_SAVINGS',
      payoutDate: '2026-03-31T10:00:00',
      referenceNo: 'DIV-2025-9921',
      status: 'PAID',
    ),
    DividendPayoutModel(
      dividendId: 2,
      fiscalYear: 2024,
      shareCapitalDividend: 1500.00,
      patronageRefund: 750.00,
      totalPayout: 2250.00,
      disposition: 'CREDITED_TO_SAVINGS',
      payoutDate: '2025-03-31T10:00:00',
      referenceNo: 'DIV-2024-8142',
      status: 'PAID',
    ),
  ];

  Future<String?> topUpShareCapital(double amount, {String? notes}) async {
    if (amount <= 0) return 'Enter an amount greater than ₱0.00';
    if (accts.isEmpty) return 'No accounts available';
    final savings = accts.length > 1 ? accts[1] : accts[0];
    if (savings.bal < amount) {
      return 'Insufficient funds in ${savings.type} account (Available: ₱${savings.bal.toStringAsFixed(2)})';
    }

    // Call live gateway / backend
    final res = await MobileRepo.topUpShareCapital(
      amount: amount,
      sourceAccountId: savings.no,
      notes: notes,
    );

    savings.bal = r2(savings.bal - amount);
    final sharesAdded = (res['sharesAdded'] as num?)?.toInt() ?? (amount / shareCapital.parValue).floor();
    shareCapital.paidUpShares += sharesAdded;
    shareCapital.totalPaidAmount = r2(shareCapital.totalPaidAmount + amount);
    if (shareCapital.paidUpShares > shareCapital.subscribedShares) {
      shareCapital.subscribedShares = shareCapital.paidUpShares;
    }
    shareCapital.hasVotingRights = shareCapital.paidUpShares >= shareCapital.minimumRequiredShares;
    shareCapital.membershipType = shareCapital.hasVotingRights ? 'REGULAR' : 'ASSOCIATE';
    final subTotal = shareCapital.subscribedShares * shareCapital.parValue;
    shareCapital.completionRatio = subTotal > 0 ? (shareCapital.totalPaidAmount / subTotal).clamp(0.0, 1.0) : 1.0;

    savings.tx.insert(0, Tx(
      'Capital Build-Up: +$sharesAdded Shares',
      res['referenceNo']?.toString() ?? 'SC-TOP-${DateTime.now().millisecondsSinceEpoch % 100000}',
      today,
      -amount,
      status: 'POSTED',
    ));
    _alert('Subscribed to $sharesAdded additional shares in Co-op Share Capital');
    notifyListeners();
    return null;
  }


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
          final rawStatus = (t['status'] ?? 'POSTED').toString().toUpperCase();
          final failureCode = t['failureCode']?.toString();
          txList.add(Tx(
            label,
            ref,
            DateTime(d.year, d.month, d.day),
            isOut ? -amt : amt,
            status: rawStatus,
            failureCode: failureCode,
          ));
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
        // Remove pending local transfers once server lists them
        _pendingLocalTx.removeWhere((pTx) => parsed[0].tx.any((t) => t.ref == pTx.ref));
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

      // Load saved account nicknames from secure storage
      for (final a in accts) {
        try {
          final savedNick = await SecureStorageService().read('coobs_nick_${a.no}');
          if (savedNick != null && savedNick.isNotEmpty) {
            accountNicknames[a.no] = savedNick;
          }
        } catch (_) {}
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

      // Fetch Live Ipon Vaults (MOB-02)
      try {
        final vList = await MobileRepo.fetchVaults();
        if (vList.isNotEmpty) {
          iponVaults
            ..clear()
            ..addAll([
              for (final v in vList)
                IponVault(
                  id: '${v['id'] ?? v['vaultId'] ?? ''}',
                  name: '${v['name'] ?? ''}',
                  targetAmount: parseBal(v['targetAmount']),
                  currentAmount: parseBal(v['currentAmount']),
                  targetDate: '${v['targetDate'] ?? ''}',
                  category: '${v['category'] ?? 'Emergency'}',
                  iconCode: (v['iconCode'] as num?)?.toInt() ?? 0xe59c,
                  colorValue: (v['colorValue'] as num?)?.toInt() ?? 0xFF0D9488,
                )
            ]);
        }
      } catch (_) {}

      // Fetch Live Share Capital & Dividends
      try {
        final sc = await MobileRepo.fetchShareCapital();
        if (sc.isNotEmpty) {
          shareCapital = ShareCapitalModel.fromMap(sc);
        }
        final divs = await MobileRepo.fetchDividendHistory();
        if (divs.isNotEmpty) {
          dividendHistory = [
            for (final d in divs)
              if (d is Map) DividendPayoutModel.fromMap(Map<String, dynamic>.from(d))
          ];
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
    final tx = Tx('Transfer to $bank', ref, today, -amt, status: 'PROCESSING');
    _pendingLocalTx.insert(0, tx);
    accts[from].bal = r2(accts[from].bal - amt - fee);
    accts[from].tx.insert(0, tx);
    if (fee > 0) {
      final feeTx = Tx('Transfer fee', ref, today, -fee, status: 'PROCESSING');
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
    accountNicknames.clear();
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
    iponVaults.clear();
    iponVaults.addAll([
      IponVault(
        id: 'VAULT-01',
        name: 'Emergency Buffer',
        targetAmount: 30000.0,
        currentAmount: 18500.0,
        targetDate: 'Dec 2026',
        category: 'Emergency',
        iconCode: 0xe5ce,
        colorValue: 0xFF0D9488,
      ),
      IponVault(
        id: 'VAULT-02',
        name: 'Paluwagan & Holidays',
        targetAmount: 15000.0,
        currentAmount: 9200.0,
        targetDate: 'Nov 2026',
        category: 'Community',
        iconCode: 0xe59c,
        colorValue: 0xFF2563EB,
      ),
      IponVault(
        id: 'VAULT-03',
        name: 'Co-op Share Capital',
        targetAmount: 20000.0,
        currentAmount: 7300.0,
        targetDate: 'Jan 2027',
        category: 'Cooperative',
        iconCode: 0xe06e,
        colorValue: 0xFFF59E0B,
      ),
    ]);
    notifyListeners();
  }
}
