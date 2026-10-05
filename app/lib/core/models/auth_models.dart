/// Authentication & Authorization Request / Response Models
class LoginRequest {
  final String username;
  final String password;

  LoginRequest({required this.username, required this.password});

  Map<String, dynamic> toJson() => {
    'username': username,
    'password': password,
  };
}

class LoginResponse {
  final String authData;
  final String mfaToken;
  final String deliveryChannel;
  final String maskedDestination;

  LoginResponse({
    required this.authData,
    required this.mfaToken,
    required this.deliveryChannel,
    required this.maskedDestination,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) => LoginResponse(
    authData: json['authData'] as String? ?? '',
    mfaToken: json['mfaToken'] as String? ?? '',
    deliveryChannel: json['deliveryChannel'] as String? ?? 'SMS',
    maskedDestination: json['maskedDestination'] as String? ?? '',
  );
}

class MfaVerifyRequest {
  final String mfaToken;
  final String code;

  MfaVerifyRequest({required this.mfaToken, required this.code});

  Map<String, dynamic> toJson() => {
    'mfaToken': mfaToken,
    'code': code,
  };
}

class TokenResponse {
  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;
  final int userId;
  final String userType;

  TokenResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.expiresIn,
    required this.userId,
    required this.userType,
  });

  factory TokenResponse.fromJson(Map<String, dynamic> json) => TokenResponse(
    accessToken: json['accessToken'] as String? ?? json['token'] as String? ?? '',
    refreshToken: json['refreshToken'] as String? ?? '',
    tokenType: json['tokenType'] as String? ?? 'Bearer',
    expiresIn: json['expiresIn'] as int? ?? 86400,
    userId: json['userId'] as int? ?? json['customerId'] as int? ?? 0,
    userType: json['userType'] as String? ?? 'CUSTOMER',
  );

  Map<String, dynamic> toJson() => {
    'accessToken': accessToken,
    'refreshToken': refreshToken,
    'tokenType': tokenType,
    'expiresIn': expiresIn,
    'userId': userId,
    'userType': userType,
  };
}
