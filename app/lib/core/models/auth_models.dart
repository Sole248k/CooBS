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

  factory TokenResponse.fromJson(Map<String, dynamic> json) {
    final data = json['authData'] is Map<String, dynamic>
        ? json['authData'] as Map<String, dynamic>
        : json;
    return TokenResponse(
      accessToken: data['accessToken'] as String? ?? data['token'] as String? ?? '',
      refreshToken: data['refreshToken'] as String? ?? '',
      tokenType: data['tokenType'] as String? ?? 'Bearer',
      expiresIn: data['expiresIn'] as int? ?? 86400,
      userId: data['userId'] as int? ?? data['customerId'] as int? ?? 0,
      userType: data['userType'] as String? ?? 'CUSTOMER',
    );
  }

  Map<String, dynamic> toJson() => {
    'accessToken': accessToken,
    'refreshToken': refreshToken,
    'tokenType': tokenType,
    'expiresIn': expiresIn,
    'userId': userId,
    'userType': userType,
  };
}
