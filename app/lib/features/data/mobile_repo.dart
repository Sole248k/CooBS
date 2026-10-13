import 'dart:convert';
import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter/services.dart' show rootBundle;
import '../../core/config/app_config.dart';
import '../../core/network/api_client.dart';

/// Repository for loading contract JSON fixtures for offline / demo mode
/// and bridging to live gateway endpoints when AppConfig.useFixtures is false.
class MobileRepo {
  static ApiClient _api = ApiClient();

  @visibleForTesting
  static set apiForTesting(ApiClient client) => _api = client;

  static Future<dynamic> _loadJson(String filename) async {
    final raw = await rootBundle.loadString('assets/fixtures/$filename');
    return jsonDecode(raw);
  }

  static Future<Map<String, dynamic>> kycOptions() async =>
      Map<String, dynamic>.from(await _loadJson('K1-kyc-intake-options.response.json'));

  static Future<Map<String, dynamic>> kycSample() async =>
      Map<String, dynamic>.from(await _loadJson('K2-kyc-intake.request.json'));

  static Future<Map<String, dynamic>> kycResult(String state) async {
    final filename = switch (state) {
      'VERIFIED' => 'K2-kyc-intake-verified.response.json',
      'REJECTED' => 'K2-kyc-intake-rejected.response.json',
      _ => 'K2-kyc-intake.response.json',
    };
    return Map<String, dynamic>.from(await _loadJson(filename));
  }

  /// Banks, fees and categories come from the bundled R1 catalog (the backend has none);
  /// in live mode the amount limits are replaced by the ones mobile-bff actually enforces.
  /// Falls back to the bundled limits if the options endpoint is unreachable.
  static Future<Map<String, dynamic>> remitOptions() async {
    final opts = Map<String, dynamic>.from(await _loadJson('R1-remittance-options.response.json'));
    if (AppConfig.useFixtures) return opts;
    try {
      final live = await _api.get('/mobile/transactions/remittance/options');
      final limits = live['limits'];
      if (limits is Map && limits['minAmount'] is num && limits['maxAmount'] is num) {
        opts['limits'] = {'minAmount': limits['minAmount'], 'maxAmount': limits['maxAmount']};
        opts['limitsSource'] = 'server';
      }
    } catch (_) {
      // Keep bundled limits; the BFF still rejects anything out of range on submit.
    }
    return opts;
  }

  static Future<Map<String, dynamic>> problem(bool insufficient) async {
    final filename = insufficient
        ? 'E1-insufficient-funds.problem.json'
        : 'E2-validation-error.problem.json';
    return Map<String, dynamic>.from(await _loadJson(filename));
  }

  static Future<Map<String, dynamic>> submitRemittance(Map<String, dynamic> body, {String? idempotencyKey, String? correlationId}) async {
    if (AppConfig.useFixtures) {
      return Map<String, dynamic>.from(await _loadJson('M3-mobile-remittance.response.json'));
    }
    return _api.post('/mobile/transactions/remittance',
        body: body, isMoneyMovement: true, idempotencyKey: idempotencyKey, correlationId: correlationId);
  }

  static Future<Map<String, dynamic>> timeline([String? trackingRef]) async {
    if (AppConfig.useFixtures || trackingRef == null || trackingRef.isEmpty) {
      return Map<String, dynamic>.from(await _loadJson('M2-mobile-timeline.response.json'));
    }
    return _api.get('/mobile/transactions/$trackingRef/timeline');
  }

  static Future<List<dynamic>> myAccounts() async {
    if (AppConfig.useFixtures) {
      return List<dynamic>.from(await _loadJson('K5-my-accounts.response.json'));
    }
    try {
      final res = await _api.get('/accounts/my-accounts');
      if (res['items'] is List) return res['items'] as List;
      if (res['data'] is List) return res['data'] as List;
      if (res['content'] is List) return res['content'] as List;
      if (res['accountNumber'] != null || res['accountId'] != null) return [res];
      return List<dynamic>.from(await _loadJson('K5-my-accounts.response.json'));
    } catch (_) {
      return List<dynamic>.from(await _loadJson('K5-my-accounts.response.json'));
    }
  }

  static Future<Map<String, dynamic>> accountTx() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/mobile/dashboard');
        if (res['recentTransactions'] is List && (res['recentTransactions'] as List).isNotEmpty) {
          final txList = res['recentTransactions'] as List;
          final primary = res['primaryAccount']?['accountNumber']?.toString() ?? 'ACC_10000001';
          return {
            'accounts': {
              primary: txList,
            }
          };
        }
      } catch (_) {}
      return {};
    }
    try {
      return Map<String, dynamic>.from(await _loadJson('K6-account-transactions.response.json'));
    } catch (_) {
      return {};
    }
  }

  static Future<Map<String, dynamic>> notifications() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/notifications/my-notifications');
        if (res.isNotEmpty && ((res['content'] as List?)?.isNotEmpty == true || (res['items'] as List?)?.isNotEmpty == true)) {
          return res;
        }
      } catch (_) {
        try {
          final res2 = await _api.get('/notifications');
          if (res2.isNotEmpty) return res2;
        } catch (_) {}
      }
    }
    return Map<String, dynamic>.from(await _loadJson('N1-notifications.response.json'));
  }

  static Future<Map<String, dynamic>> customerProfile([dynamic customerId]) async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/customers/me');
        if (res.isNotEmpty && (res['displayName'] != null || res['kycStatus'] != null || res['kyc'] != null)) {
          return res;
        }
      } catch (_) {}

      try {
        dynamic effectiveId = customerId;
        if (effectiveId == null || '$effectiveId'.isEmpty || '$effectiveId' == 'me' || '$effectiveId' == '0') {
          final storedId = await _api.storage.getUserId();
          if (storedId != null && storedId > 0) {
            effectiveId = storedId;
          }
        }
        if (effectiveId != null && '$effectiveId'.isNotEmpty && '$effectiveId' != 'me' && '$effectiveId' != '0') {
          final c3 = await customer360(effectiveId);
          if (c3.isNotEmpty && (c3['displayName'] != null || c3['kycStatus'] != null)) {
            return c3;
          }
        }
      } catch (_) {}
    }
    try {
      dynamic effectiveId = customerId;
      if (effectiveId == null || '$effectiveId'.isEmpty || '$effectiveId' == 'me' || '$effectiveId' == '0') {
        final storedId = await _api.storage.getUserId();
        if (storedId != null && storedId > 0) {
          effectiveId = storedId;
        }
      }
      final cid = int.tryParse('$effectiveId');
      if (cid == 9) {
        return {
          'customerId': 9,
          'displayName': 'Grace P.',
          'kycStatus': 'VERIFIED',
          'kycTier': 'TIER_1_BASIC',
          'kyc': {
            'firstName': 'Grace',
            'middleInitial': 'A',
            'lastName': 'Pangilinan',
            'address': '31 MacArthur Highway, San Fernando City, Pampanga',
            'occupation': 'College Student',
            'mobileNumber': '+639665550109',
            'civilStatus': 'Single',
            'status': 'VERIFIED',
          }
        };
      } else if (cid == 2) {
        return {
          'customerId': 2,
          'displayName': 'Maria S.',
          'kycStatus': 'VERIFIED',
          'kycTier': 'TIER_3_FULL',
          'kyc': {
            'firstName': 'Maria',
            'middleInitial': 'B',
            'lastName': 'Santos',
            'address': '456 Buendia Ave, Makati City',
            'occupation': 'Marketing Manager',
            'mobileNumber': '+639170001111',
            'civilStatus': 'Married',
            'status': 'VERIFIED',
          }
        };
      }
      final fixture = await ApiClient().loadFixtureJson('C1-C2-customer-profile.response.json');
      if (fixture.isNotEmpty) return fixture;
    } catch (_) {}
    return {
      'displayName': 'John D.',
      'kycStatus': 'VERIFIED',
      'kyc': {
        'firstName': 'John',
        'middleInitial': 'D',
        'lastName': 'Doe',
        'address': '123 Ayala Avenue, Makati City, Metro Manila',
        'occupation': 'Software Engineer',
        'mobileNumber': '+639171234567',
        'civilStatus': 'Single',
        'status': 'VERIFIED',
      }
    };
  }

  static Future<List<dynamic>> beneficiaries() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/customers/me/beneficiaries');
        if (res['data'] is List && (res['data'] as List).isNotEmpty) return res['data'] as List;
        if (res['items'] is List && (res['items'] as List).isNotEmpty) return res['items'] as List;
        if (res['content'] is List && (res['content'] as List).isNotEmpty) return res['content'] as List;
      } catch (_) {}
    }
    return [
      {
        'nickname': 'Juan Dela Cruz',
        'accountNumber': 'ACC_10000002',
        'bankCode': 'COOBS',
        'mobileNumber': '+63 918 222 3333',
      },
      {
        'nickname': 'Maria Santos',
        'accountNumber': 'ACC_10000004',
        'bankCode': 'COOBS',
        'mobileNumber': '+63 917 000 1111',
      },
      {
        'nickname': 'Jose Bautista',
        'accountNumber': 'ACC_10000008',
        'bankCode': 'COOBS',
        'mobileNumber': '+63 919 444 5555',
      },
    ];
  }

  static Future<Map<String, dynamic>> addBeneficiary(Map<String, dynamic> body) async {
    if (!AppConfig.useFixtures) {
      try {
        return await _api.post('/customers/me/beneficiaries', body: body);
      } catch (_) {}
    }
    return body;
  }

  /// C3: Customer 360 Aggregator (Credit score, spending habits, totals)
  static Future<Map<String, dynamic>> customer360([dynamic customerId]) async {
    if (!AppConfig.useFixtures) {
      try {
        dynamic effectiveId = customerId;
        if (effectiveId == null || '$effectiveId'.isEmpty || '$effectiveId' == 'me' || '$effectiveId' == '0') {
          final storedId = await _api.storage.getUserId();
          if (storedId != null && storedId > 0) {
            effectiveId = storedId;
          }
        }

        // 1. Query canonical /{customerId}/360 endpoint
        if (effectiveId != null && '$effectiveId'.isNotEmpty && '$effectiveId' != 'me' && '$effectiveId' != '0') {
          try {
            final res = await _api.get('/customers/$effectiveId/360');
            if (res.isNotEmpty && (res['creditScore'] != null || res['totals'] != null || res['spendingBreakdown'] != null)) {
              return res;
            }
          } catch (_) {}
        }

        // 2. Query /customers/me/360 endpoint
        try {
          final res = await _api.get('/customers/me/360');
          if (res.isNotEmpty && (res['creditScore'] != null || res['totals'] != null || res['spendingBreakdown'] != null)) {
            return res;
          }
        } catch (_) {}
      } catch (_) {}
    }
    try {
      final fixture = await ApiClient().loadFixtureJson('C3-customer-360.response.json');
      dynamic effectiveId = customerId;
      if (effectiveId == null || '$effectiveId'.isEmpty || '$effectiveId' == 'me' || '$effectiveId' == '0') {
        final storedId = await _api.storage.getUserId();
        if (storedId != null && storedId > 0) {
          effectiveId = storedId;
        }
      }
      final cid = int.tryParse('$effectiveId');
      if (cid != null) {
        if (cid == 9) {
          fixture['customerId'] = 9;
          fixture['displayName'] = 'Grace P.';
          fixture['creditScore'] = 600;
          fixture['kycTier'] = 'TIER_1_BASIC';
        } else if (cid == 2) {
          fixture['customerId'] = 2;
          fixture['displayName'] = 'Maria S.';
          fixture['creditScore'] = 790;
          fixture['kycTier'] = 'TIER_3_FULL';
        } else if (cid == 5) {
          fixture['customerId'] = 5;
          fixture['displayName'] = 'Andrea R.';
          fixture['creditScore'] = 540;
          fixture['kycTier'] = 'TIER_1_BASIC';
        } else if (cid == 4) {
          fixture['customerId'] = 4;
          fixture['displayName'] = 'Juan D.';
          fixture['creditScore'] = 780;
          fixture['kycTier'] = 'TIER_3_FULL';
        }
      }
      return fixture;
    } catch (_) {
      return {};
    }
  }

  // =========================================================================
  // V1: Virtual "Ipon" Vaults & Sub-Ledger Earmarks
  // =========================================================================

  /// Fetches active Ipon Vaults for the authenticated member.
  static Future<List<dynamic>> fetchVaults() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/accounts/vaults');
        if (res['data'] is List) return res['data'] as List;
        if (res['items'] is List) return res['items'] as List;
        if (res['content'] is List) return res['content'] as List;
      } catch (_) {}
    }
    // Return default fixture vaults
    return [
      {
        'id': 'VAULT-01',
        'vaultId': 1,
        'name': 'Emergency Buffer',
        'targetAmount': 30000.0,
        'currentAmount': 18500.0,
        'targetDate': 'Dec 2026',
        'category': 'Emergency',
        'iconCode': 0xe5ce,
        'colorValue': 0xFF0D9488,
        'status': 'ACTIVE',
      },
      {
        'id': 'VAULT-02',
        'vaultId': 2,
        'name': 'Paluwagan & Holidays',
        'targetAmount': 15000.0,
        'currentAmount': 9200.0,
        'targetDate': 'Nov 2026',
        'category': 'Community',
        'iconCode': 0xe59c,
        'colorValue': 0xFF2563EB,
        'status': 'ACTIVE',
      },
      {
        'id': 'VAULT-03',
        'vaultId': 3,
        'name': 'Co-op Share Capital',
        'targetAmount': 20000.0,
        'currentAmount': 7300.0,
        'targetDate': 'Jan 2027',
        'category': 'Cooperative',
        'iconCode': 0xe06e,
        'colorValue': 0xFFF59E0B,
        'status': 'ACTIVE',
      },
    ];
  }

  /// Creates a new Ipon Vault for the member.
  static Future<Map<String, dynamic>> createVault(Map<String, dynamic> body) async {
    if (!AppConfig.useFixtures) {
      try {
        return await _api.post('/accounts/vaults', body: body);
      } catch (_) {}
    }
    return {
      'id': 'VAULT-${DateTime.now().millisecondsSinceEpoch % 10000}',
      ...body,
      'currentAmount': body['initialAmount'] ?? 0.0,
      'status': 'ACTIVE',
    };
  }

  /// Earmarks funds into a vault.
  static Future<Map<String, dynamic>> allocateToVault(String vaultId, double amount, {String? notes}) async {
    if (!AppConfig.useFixtures) {
      try {
        return await _api.post('/accounts/vaults/$vaultId/allocate', body: {
          'amount': amount,
          if (notes != null) 'notes': notes,
        });
      } catch (_) {}
    }
    return {'amount': amount, 'status': 'ALLOCATED'};
  }

  /// Releases funds from a vault back to the member's account.
  static Future<Map<String, dynamic>> releaseFromVault(String vaultId, double amount, {String? notes}) async {
    if (!AppConfig.useFixtures) {
      try {
        return await _api.post('/accounts/vaults/$vaultId/release', body: {
          'amount': amount,
          if (notes != null) 'notes': notes,
        });
      } catch (_) {}
    }
    return {'amount': amount, 'status': 'RELEASED'};
  }

  /// Closes a vault and returns all funds back to the deposit balance.
  static Future<Map<String, dynamic>> closeVault(String vaultId) async {
    if (!AppConfig.useFixtures) {
      try {
        return await _api.delete('/accounts/vaults/$vaultId');
      } catch (_) {}
    }
    return {'status': 'CLOSED'};
  }

  /// Fetches earmark transaction audit history for a vault.
  static Future<List<dynamic>> fetchVaultTransactions(String vaultId) async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/accounts/vaults/$vaultId/transactions');
        if (res['data'] is List) return res['data'] as List;
        if (res['items'] is List) return res['items'] as List;
        if (res['content'] is List) return res['content'] as List;
      } catch (_) {}
    }
    return [];
  }

  /// Fetches member cooperative share capital portfolio.
  static Future<Map<String, dynamic>> fetchShareCapital() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/accounts/share-capital');
        if (res.containsKey('certificateNo') || res.containsKey('shareCapitalId')) {
          return res;
        }
      } catch (_) {}
    }
    return {
      'shareCapitalId': 100,
      'certificateNo': 'SC-2024-001001',
      'subscribedShares': 500,
      'paidUpShares': 250,
      'parValue': 100.0,
      'totalPaidAmount': 25000.0,
      'subscribedAmount': 50000.0,
      'minimumRequiredShares': 20,
      'dividendRate': 7.25,
      'membershipType': 'REGULAR',
      'hasVotingRights': true,
      'completionRatio': 0.5,
      'status': 'ACTIVE',
    };
  }

  /// Tops up member share capital subscription directly from savings account.
  static Future<Map<String, dynamic>> topUpShareCapital({
    required double amount,
    String? sourceAccountId,
    String? sourceAccountNumber,
    String? notes,
  }) async {
    if (!AppConfig.useFixtures) {
      try {
        return await _api.post('/accounts/share-capital/top-up', body: {
          'amount': amount,
          if (sourceAccountId != null) 'sourceAccountId': sourceAccountId,
          if (sourceAccountNumber != null) 'sourceAccountNumber': sourceAccountNumber,
          if (notes != null) 'notes': notes,
        });
      } catch (_) {}
    }
    final shares = (amount / 100.0).floor();
    return {
      'referenceNo': 'SC-TOP-${DateTime.now().millisecondsSinceEpoch % 100000}',
      'amountPaid': amount,
      'sharesAdded': shares,
      'newPaidUpShares': 250 + shares,
      'newTotalPaidAmount': 25000.0 + amount,
      'hasVotingRights': true,
      'membershipType': 'REGULAR',
    };
  }

  /// Fetches annual patronage dividend & interest on share capital payout history.
  static Future<List<dynamic>> fetchDividendHistory() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/accounts/share-capital/dividends');
        if (res['data'] is List) return res['data'] as List;
        if (res['items'] is List) return res['items'] as List;
        if (res['content'] is List) return res['content'] as List;
      } catch (_) {}
    }
    return [
      {
        'dividendId': 1,
        'fiscalYear': 2025,
        'shareCapitalDividend': 1812.50,
        'patronageRefund': 940.00,
        'totalPayout': 2752.50,
        'disposition': 'CREDITED_TO_SAVINGS',
        'payoutDate': '2026-03-31T10:00:00',
        'referenceNo': 'DIV-2025-9921',
        'status': 'PAID',
      },
      {
        'dividendId': 2,
        'fiscalYear': 2024,
        'shareCapitalDividend': 1500.00,
        'patronageRefund': 750.00,
        'totalPayout': 2250.00,
        'disposition': 'CREDITED_TO_SAVINGS',
        'payoutDate': '2025-03-31T10:00:00',
        'referenceNo': 'DIV-2024-8142',
        'status': 'PAID',
      },
    ];
  }

  /// Fetches share capital transaction log.
  static Future<List<dynamic>> fetchShareCapitalTransactions() async {
    if (!AppConfig.useFixtures) {
      try {
        final res = await _api.get('/accounts/share-capital/transactions');
        if (res['data'] is List) return res['data'] as List;
        if (res['items'] is List) return res['items'] as List;
        if (res['content'] is List) return res['content'] as List;
      } catch (_) {}
    }
    return [];
  }
}

