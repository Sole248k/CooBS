import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

/// Repository for loading contract JSON fixtures for offline / demo mode.
class MobileRepo {
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

  static Future<Map<String, dynamic>> remitOptions() async =>
      Map<String, dynamic>.from(await _loadJson('R1-remittance-options.response.json'));

  static Future<Map<String, dynamic>> problem(bool insufficient) async {
    final filename = insufficient
        ? 'E1-insufficient-funds.problem.json'
        : 'E2-validation-error.problem.json';
    return Map<String, dynamic>.from(await _loadJson(filename));
  }

  static Future<Map<String, dynamic>> submitRemittance(Map<String, dynamic> body) async =>
      Map<String, dynamic>.from(await _loadJson('M3-mobile-remittance.response.json'));

  static Future<Map<String, dynamic>> timeline() async =>
      Map<String, dynamic>.from(await _loadJson('M2-mobile-timeline.response.json'));

  static Future<List<dynamic>> myAccounts() async =>
      List<dynamic>.from(await _loadJson('K5-my-accounts.response.json'));

  static Future<Map<String, dynamic>> accountTx() async =>
      Map<String, dynamic>.from(await _loadJson('K6-account-transactions.response.json'));

  static Future<Map<String, dynamic>> notifications() async =>
      Map<String, dynamic>.from(await _loadJson('N1-notifications.response.json'));
}
