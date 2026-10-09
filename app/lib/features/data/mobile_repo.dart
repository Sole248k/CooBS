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
    if (AppConfig.useFixtures) {
      return Map<String, dynamic>.from(await _loadJson('K6-account-transactions.response.json'));
    }
    return {};
  }

  static Future<Map<String, dynamic>> notifications() async {
    if (AppConfig.useFixtures) {
      return Map<String, dynamic>.from(await _loadJson('N1-notifications.response.json'));
    }
    try {
      return await _api.get('/notifications/my-notifications');
    } catch (_) {
      try {
        return await _api.get('/notifications');
      } catch (_) {
        return Map<String, dynamic>.from(await _loadJson('N1-notifications.response.json'));
      }
    }
  }

  static Future<Map<String, dynamic>> customerProfile() async {
    if (AppConfig.useFixtures) {
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
    try {
      return await _api.get('/customers/me');
    } catch (_) {
      return {};
    }
  }

  static Future<List<dynamic>> beneficiaries() async {
    if (AppConfig.useFixtures) {
      return [
        {
          'nickname': 'Juan Dela Cruz',
          'accountNumber': 'ACC-1002-9915',
          'bankCode': 'COOBS',
          'mobileNumber': '+63 917 000 1111',
        }
      ];
    }
    try {
      final res = await _api.get('/customers/me/beneficiaries');
      if (res['data'] is List) return res['data'] as List;
      if (res['items'] is List) return res['items'] as List;
      if (res['content'] is List) return res['content'] as List;
      return [];
    } catch (_) {
      return [];
    }
  }

  static Future<Map<String, dynamic>> addBeneficiary(Map<String, dynamic> body) async {
    if (AppConfig.useFixtures) {
      return body;
    }
    return _api.post('/customers/me/beneficiaries', body: body);
  }
}
