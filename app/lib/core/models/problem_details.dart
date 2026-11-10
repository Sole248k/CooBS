/// RFC-7807 Standard Error Envelope
class ProblemDetails {
  final String type;
  final String title;
  final int status;
  final String detail;
  final String instance;
  final String errorCode;
  final DateTime timestamp;
  final List<Map<String, String>> invalidParams;

  ProblemDetails({
    required this.type,
    required this.title,
    required this.status,
    required this.detail,
    required this.instance,
    required this.errorCode,
    required this.timestamp,
    this.invalidParams = const [],
  });

  factory ProblemDetails.fromJson(Map<String, dynamic> json) {
    var params = <Map<String, String>>[];
    if (json['invalidParams'] != null && json['invalidParams'] is List) {
      params = (json['invalidParams'] as List).map((p) {
        if (p is Map) {
          return {
            'name': p['name']?.toString() ?? '',
            'reason': p['reason']?.toString() ?? '',
          };
        }
        return <String, String>{};
      }).toList();
    }
    return ProblemDetails(
      type: json['type'] as String? ?? 'about:blank',
      title: json['title'] as String? ?? 'Error',
      status: json['status'] as int? ?? 500,
      detail: json['detail'] as String? ?? 'An unexpected error occurred.',
      instance: json['instance'] as String? ?? '',
      errorCode: json['errorCode'] as String? ?? 'UNKNOWN_ERROR',
      timestamp: DateTime.tryParse(json['timestamp']?.toString() ?? '') ?? DateTime.now(),
      invalidParams: params,
    );
  }

  Map<String, dynamic> toJson() => {
    'type': type,
    'title': title,
    'status': status,
    'detail': detail,
    'instance': instance,
    'errorCode': errorCode,
    'timestamp': timestamp.toIso8601String(),
    'invalidParams': invalidParams,
  };

  @override
  String toString() => '[$errorCode] $title: $detail';
}
