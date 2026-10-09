/// Beneficiary DTOs matching backend customer-service contracts
class BeneficiaryItem {
  final int beneficiaryId;
  final int customerId;
  final String nickname;
  final String accountNumber;
  final String bankCode;
  final bool active;
  final DateTime? createdAt;

  BeneficiaryItem({
    required this.beneficiaryId,
    required this.customerId,
    required this.nickname,
    required this.accountNumber,
    required this.bankCode,
    required this.active,
    this.createdAt,
  });

  factory BeneficiaryItem.fromJson(Map<String, dynamic> json) => BeneficiaryItem(
    beneficiaryId: json['beneficiaryId'] as int? ?? 0,
    customerId: json['customerId'] as int? ?? 0,
    nickname: json['nickname'] as String? ?? '',
    accountNumber: json['accountNumber'] as String? ?? '',
    bankCode: json['bankCode'] as String? ?? 'COOBS',
    active: json['active'] as bool? ?? true,
    createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
  );

  Map<String, dynamic> toJson() => {
    'beneficiaryId': beneficiaryId,
    'customerId': customerId,
    'nickname': nickname,
    'accountNumber': accountNumber,
    'bankCode': bankCode,
    'active': active,
    if (createdAt != null) 'createdAt': createdAt!.toIso8601String(),
  };
}

/// Customer profile & KYC DTOs matching backend customer-service contracts
class KycData {
  final int kycId;
  final String firstName;
  final String middleInitial;
  final String lastName;
  final String address;
  final String mobileNumber;
  final String civilStatus;
  final String occupation;
  final String status;
  final DateTime? createdAt;

  KycData({
    required this.kycId,
    required this.firstName,
    required this.middleInitial,
    required this.lastName,
    required this.address,
    required this.mobileNumber,
    required this.civilStatus,
    required this.occupation,
    required this.status,
    this.createdAt,
  });

  factory KycData.fromJson(Map<String, dynamic> json) => KycData(
    kycId: json['kycId'] as int? ?? 0,
    firstName: json['firstName'] as String? ?? '',
    middleInitial: json['middleInitial'] as String? ?? '',
    lastName: json['lastName'] as String? ?? '',
    address: json['address'] as String? ?? '',
    mobileNumber: json['mobileNumber'] as String? ?? '',
    civilStatus: json['civilStatus'] as String? ?? '',
    occupation: json['occupation'] as String? ?? '',
    status: json['status'] as String? ?? 'VERIFIED',
    createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
  );

  Map<String, dynamic> toJson() => {
    'kycId': kycId,
    'firstName': firstName,
    'middleInitial': middleInitial,
    'lastName': lastName,
    'address': address,
    'mobileNumber': mobileNumber,
    'civilStatus': civilStatus,
    'occupation': occupation,
    'status': status,
    if (createdAt != null) 'createdAt': createdAt!.toIso8601String(),
  };
}

class CustomerProfileData {
  final int customerId;
  final String username;
  final String email;
  final String kycStatus;
  final String displayName;
  final String membershipNumber;
  final String kycTier;
  final KycData? kyc;

  CustomerProfileData({
    required this.customerId,
    required this.username,
    required this.email,
    required this.kycStatus,
    required this.displayName,
    required this.membershipNumber,
    required this.kycTier,
    this.kyc,
  });

  factory CustomerProfileData.fromJson(Map<String, dynamic> json) => CustomerProfileData(
    customerId: json['customerId'] as int? ?? 0,
    username: json['username'] as String? ?? '',
    email: json['email'] as String? ?? '',
    kycStatus: json['kycStatus'] as String? ?? 'UNVERIFIED',
    displayName: json['displayName'] as String? ?? '',
    membershipNumber: json['membershipNumber'] as String? ?? '',
    kycTier: json['kycTier'] as String? ?? 'TIER_3_FULL',
    kyc: json['kyc'] != null ? KycData.fromJson(json['kyc'] as Map<String, dynamic>) : null,
  );

  Map<String, dynamic> toJson() => {
    'customerId': customerId,
    'username': username,
    'email': email,
    'kycStatus': kycStatus,
    'displayName': displayName,
    'membershipNumber': membershipNumber,
    'kycTier': kycTier,
    if (kyc != null) 'kyc': kyc!.toJson(),
  };
}
