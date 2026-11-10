/// Member Deposit Account Models (Endpoint K3/K4)
class AccountSummary {
  final int accountId;
  final String accountNumber;
  final int customerId;
  final String customerName;
  final double availableBalance;
  final String currency;
  final String status;
  final String? message;

  AccountSummary({
    required this.accountId,
    required this.accountNumber,
    required this.customerId,
    required this.customerName,
    required this.availableBalance,
    required this.currency,
    required this.status,
    this.message,
  });

  bool get isActive => status.toUpperCase() == 'ACTIVE';

  factory AccountSummary.fromJson(Map<String, dynamic> json) => AccountSummary(
    accountId: json['accountId'] as int? ?? 0,
    accountNumber: json['accountNumber'] as String? ?? '',
    customerId: json['customerId'] as int? ?? 0,
    customerName: json['customerName'] as String? ?? '',
    availableBalance: (json['availableBalance'] is num)
        ? (json['availableBalance'] as num).toDouble()
        : double.tryParse(json['availableBalance']?.toString() ?? '0') ?? 0.0,
    currency: json['currency'] as String? ?? 'PHP',
    status: json['status'] as String? ?? 'ACTIVE',
    message: json['message'] as String?,
  );

  Map<String, dynamic> toJson() => {
    'accountId': accountId,
    'accountNumber': accountNumber,
    'customerId': customerId,
    'customerName': customerName,
    'availableBalance': availableBalance,
    'currency': currency,
    'status': status,
    'message': message,
  };
}

class AccountLookupResponse {
  final String accountNumber;
  final String accountHolderName;
  final String bankCode;
  final bool isValid;

  AccountLookupResponse({
    required this.accountNumber,
    required this.accountHolderName,
    required this.bankCode,
    required this.isValid,
  });

  factory AccountLookupResponse.fromJson(Map<String, dynamic> json) => AccountLookupResponse(
    accountNumber: json['accountNumber'] as String? ?? '',
    accountHolderName: json['accountHolderName'] as String? ?? json['customerName'] as String? ?? '',
    bankCode: json['bankCode'] as String? ?? 'COOBS',
    isValid: json['isValid'] as bool? ?? (json['status'] == 'ACTIVE'),
  );
}
