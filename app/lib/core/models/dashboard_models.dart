/// Mobile Composite Dashboard Models (Endpoint M1 via mobile-bff)
class MobileMemberSummary {
  final int customerId;
  final String displayName;
  final String email;
  final String kycStatus;

  MobileMemberSummary({
    required this.customerId,
    required this.displayName,
    required this.email,
    required this.kycStatus,
  });

  bool get isVerified => kycStatus.toUpperCase() == 'VERIFIED';

  factory MobileMemberSummary.fromJson(Map<String, dynamic> json) => MobileMemberSummary(
    customerId: (json['customerId'] as num?)?.toInt() ?? int.tryParse(json['customerId']?.toString() ?? '') ?? 0,
    displayName: json['displayName'] as String? ?? '',
    email: json['email'] as String? ?? '',
    kycStatus: json['kycStatus'] as String? ?? 'UNVERIFIED',
  );

  Map<String, dynamic> toJson() => {
    'customerId': customerId,
    'displayName': displayName,
    'email': email,
    'kycStatus': kycStatus,
  };
}

class MobileAccountSummary {
  final int accountId;
  final String accountNumber;
  final String accountType;
  final String currency;
  final double availableBalance;
  final String formattedBalance;
  final String status;

  MobileAccountSummary({
    required this.accountId,
    required this.accountNumber,
    required this.accountType,
    required this.currency,
    required this.availableBalance,
    required this.formattedBalance,
    required this.status,
  });

  bool get isActive => status.toUpperCase() == 'ACTIVE';

  factory MobileAccountSummary.fromJson(Map<String, dynamic> json) => MobileAccountSummary(
    accountId: (json['accountId'] as num?)?.toInt() ?? int.tryParse(json['accountId']?.toString() ?? '') ?? 0,
    accountNumber: json['accountNumber'] as String? ?? '',
    accountType: json['accountType'] as String? ?? 'SAVINGS',
    currency: json['currency'] as String? ?? 'PHP',
    availableBalance: (json['availableBalance'] is num)
        ? (json['availableBalance'] as num).toDouble()
        : double.tryParse(json['availableBalance']?.toString() ?? '0') ?? 0.0,
    formattedBalance: json['formattedBalance'] as String? ?? '₱0.00',
    status: json['status'] as String? ?? 'ACTIVE',
  );

  Map<String, dynamic> toJson() => {
    'accountId': accountId,
    'accountNumber': accountNumber,
    'accountType': accountType,
    'currency': currency,
    'availableBalance': availableBalance,
    'formattedBalance': formattedBalance,
    'status': status,
  };
}

class MobileRecentTransaction {
  final String trackingReference;
  final String transactionId;
  final String direction;
  final double amount;
  final String formattedAmount;
  final String currency;
  final String status;
  final String remarks;
  final DateTime submittedAt;

  MobileRecentTransaction({
    required this.trackingReference,
    this.transactionId = '',
    this.direction = 'OUT',
    required this.amount,
    required this.formattedAmount,
    required this.currency,
    required this.status,
    required this.remarks,
    required this.submittedAt,
  });

  factory MobileRecentTransaction.fromJson(Map<String, dynamic> json) => MobileRecentTransaction(
    trackingReference: json['trackingReference'] as String? ?? json['referenceNo'] as String? ?? '',
    transactionId: json['transactionId'] as String? ?? '',
    direction: json['direction'] as String? ?? 'OUT',
    amount: (json['amount'] is num)
        ? (json['amount'] as num).toDouble()
        : double.tryParse(json['amount']?.toString() ?? '0') ?? 0.0,
    formattedAmount: json['formattedAmount'] as String? ?? '',
    currency: json['currency'] as String? ?? 'PHP',
    status: json['status'] as String? ?? 'COMPLETED',
    remarks: json['remarks'] as String? ?? '',
    submittedAt: DateTime.tryParse(json['submittedAt']?.toString() ?? json['createdAt']?.toString() ?? '') ?? DateTime.now(),
  );

  Map<String, dynamic> toJson() => {
    'trackingReference': trackingReference,
    'transactionId': transactionId,
    'direction': direction,
    'amount': amount,
    'formattedAmount': formattedAmount,
    'currency': currency,
    'status': status,
    'remarks': remarks,
    'submittedAt': submittedAt.toIso8601String(),
  };
}

class MobileDashboardResponse {
  final MobileMemberSummary member;
  final MobileAccountSummary primaryAccount;
  final int unreadNotifications;
  final List<MobileRecentTransaction> recentTransactions;

  MobileDashboardResponse({
    required this.member,
    required this.primaryAccount,
    required this.unreadNotifications,
    required this.recentTransactions,
  });

  factory MobileDashboardResponse.fromJson(Map<String, dynamic> json) {
    return MobileDashboardResponse(
      member: MobileMemberSummary.fromJson(json['member'] as Map<String, dynamic>? ?? {}),
      primaryAccount: MobileAccountSummary.fromJson(json['primaryAccount'] as Map<String, dynamic>? ?? {}),
      unreadNotifications: (json['unreadNotifications'] as num?)?.toInt() ?? int.tryParse(json['unreadNotifications']?.toString() ?? '') ?? 0,
      recentTransactions: (json['recentTransactions'] as List<dynamic>?)
          ?.map((e) => MobileRecentTransaction.fromJson(e as Map<String, dynamic>))
          .toList() ?? [],
    );
  }

  Map<String, dynamic> toJson() => {
    'member': member.toJson(),
    'primaryAccount': primaryAccount.toJson(),
    'unreadNotifications': unreadNotifications,
    'recentTransactions': recentTransactions.map((t) => t.toJson()).toList(),
  };
}
