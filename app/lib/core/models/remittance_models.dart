/// Remittance & Timeline Stepper Models (Endpoints T1, T2, T3, M2)
class RemittanceSubmissionRequest {
  final int? sourceAccountId;
  final String? sourceAccountNumber;
  final String destinationAccountNumber;
  final String destinationBankCode;
  final double amount;
  final String currency;
  final String category;
  final String remarks;

  RemittanceSubmissionRequest({
    this.sourceAccountId,
    this.sourceAccountNumber,
    required this.destinationAccountNumber,
    this.destinationBankCode = 'COOBS',
    required this.amount,
    this.currency = 'PHP',
    this.category = 'TRANSFER',
    this.remarks = '',
  });

  Map<String, dynamic> toJson() => {
    if (sourceAccountId != null) 'sourceAccountId': sourceAccountId,
    if (sourceAccountNumber != null) 'sourceAccountNumber': sourceAccountNumber,
    'destinationAccountNumber': destinationAccountNumber,
    'destinationBankCode': destinationBankCode,
    'amount': amount.toStringAsFixed(2),
    'currency': currency,
    'category': category,
    'remarks': remarks,
  };
}

class RemittanceSubmissionResponse {
  final String transactionId;
  final String referenceNo;
  final String status;
  final double amount;
  final String currency;
  final DateTime createdAt;
  final String timelineUrl;

  RemittanceSubmissionResponse({
    required this.transactionId,
    required this.referenceNo,
    required this.status,
    required this.amount,
    required this.currency,
    required this.createdAt,
    required this.timelineUrl,
  });

  factory RemittanceSubmissionResponse.fromJson(Map<String, dynamic> json) => RemittanceSubmissionResponse(
    transactionId: json['transactionId'] as String? ?? '',
    referenceNo: json['referenceNo'] as String? ?? json['trackingReference'] as String? ?? '',
    status: json['status'] as String? ?? 'INITIATED',
    amount: (json['amount'] is num)
        ? (json['amount'] as num).toDouble()
        : double.tryParse(json['amount']?.toString() ?? '0') ?? 0.0,
    currency: json['currency'] as String? ?? 'PHP',
    createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
    timelineUrl: json['timelineUrl'] as String? ?? '',
  );
}

class TimelineStage {
  final int stageNumber;
  final String stageKey;
  final String title;
  final String? subtitle;
  final String? service;
  final String status; // COMPLETED, IN_PROGRESS, PENDING, FAILED, REJECTED
  final DateTime? timestamp;
  final String? details;

  TimelineStage({
    required this.stageNumber,
    required this.stageKey,
    required this.title,
    this.subtitle,
    this.service,
    required this.status,
    this.timestamp,
    this.details,
  });

  bool get isCompleted => status == 'COMPLETED' || status == 'PASSED';
  bool get isInProgress => status == 'IN_PROGRESS' || status == 'PROCESSING';
  bool get isFailed => status == 'FAILED' || status == 'REJECTED';
  bool get isPending => status == 'PENDING';

  factory TimelineStage.fromJson(Map<String, dynamic> json) {
    final rawKey = (json['stageKey'] ?? json['stageName'] ?? 'UNKNOWN').toString();
    final defaultTitle = rawKey.replaceAll('_', ' ');
    return TimelineStage(
      stageNumber: (json['stageNumber'] ?? json['stageNo'] ?? 1) as int,
      stageKey: rawKey,
      title: json['title'] as String? ?? defaultTitle,
      subtitle: json['subtitle'] as String?,
      service: json['service'] as String?,
      status: (json['status'] ?? json['outcome'] ?? 'PENDING').toString(),
      timestamp: DateTime.tryParse(json['timestamp']?.toString() ?? json['occurredAt']?.toString() ?? ''),
      details: (json['details'] ?? json['detail']) as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'stageNumber': stageNumber,
    'stageKey': stageKey,
    'title': title,
    'subtitle': subtitle,
    'service': service,
    'status': status,
    'timestamp': timestamp?.toIso8601String(),
    'details': details,
  };
}

class TransactionTimelineResponse {
  final String trackingReference;
  final String view; // CUSTOMER (3 stages) or STAFF (11 stages)
  final int currentStage;
  final int totalStages;
  final String overallStatus;
  final List<TimelineStage> stages;
  final String? failureMessage;

  TransactionTimelineResponse({
    required this.trackingReference,
    required this.view,
    required this.currentStage,
    required this.totalStages,
    required this.overallStatus,
    required this.stages,
    this.failureMessage,
  });

  bool get isTerminalSuccess => overallStatus == 'COMPLETED' || overallStatus == 'POSTED';
  bool get isTerminalFailure => overallStatus == 'FAILED' || overallStatus == 'REJECTED';

  factory TransactionTimelineResponse.fromJson(Map<String, dynamic> json) {
    final rawStages = json['stages'] as List<dynamic>? ?? [];
    return TransactionTimelineResponse(
      trackingReference: json['trackingReference'] as String? ?? json['referenceNo'] as String? ?? json['transactionId'] as String? ?? '',
      view: json['view'] as String? ?? 'CUSTOMER',
      currentStage: json['currentStage'] as int? ?? 1,
      totalStages: json['totalStages'] as int? ?? rawStages.length,
      overallStatus: json['overallStatus'] as String? ?? json['status'] as String? ?? 'PROCESSING',
      stages: rawStages.map((e) => TimelineStage.fromJson(e as Map<String, dynamic>)).toList(),
      failureMessage: json['failureMessage'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'trackingReference': trackingReference,
    'view': view,
    'currentStage': currentStage,
    'totalStages': totalStages,
    'overallStatus': overallStatus,
    'stages': stages.map((s) => s.toJson()).toList(),
    'failureMessage': failureMessage,
  };
}
