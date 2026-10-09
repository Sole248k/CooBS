import '../../core/models/remittance_models.dart';

/// Status of an individual milestone in the visual stepper.
enum MilestoneStatus {
  completed,
  inProgress,
  pending,
  failed,
}

/// Represents one of the 4 high-level milestone stages:
/// Ingress Received -> AML Screened -> Ledger Posted -> Dispatched.
class TrackingMilestone {
  final int index;
  final String key;
  final String title;
  final String description;
  final MilestoneStatus status;
  final String service;
  final DateTime? timestamp;
  final String? details;
  final List<String> microserviceSubStages;
  final bool isDlq;
  final int? retryAttempts;
  final String? dlqTopic;

  const TrackingMilestone({
    required this.index,
    required this.key,
    required this.title,
    required this.description,
    required this.status,
    required this.service,
    this.timestamp,
    this.details,
    this.microserviceSubStages = const [],
    this.isDlq = false,
    this.retryAttempts,
    this.dlqTopic,
  });

  bool get isCompleted => status == MilestoneStatus.completed;
  bool get isInProgress => status == MilestoneStatus.inProgress;
  bool get isPending => status == MilestoneStatus.pending;
  bool get isFailed => status == MilestoneStatus.failed;

  TrackingMilestone copyWith({
    int? index,
    String? key,
    String? title,
    String? description,
    MilestoneStatus? status,
    String? service,
    DateTime? timestamp,
    String? details,
    List<String>? microserviceSubStages,
    bool? isDlq,
    int? retryAttempts,
    String? dlqTopic,
  }) {
    return TrackingMilestone(
      index: index ?? this.index,
      key: key ?? this.key,
      title: title ?? this.title,
      description: description ?? this.description,
      status: status ?? this.status,
      service: service ?? this.service,
      timestamp: timestamp ?? this.timestamp,
      details: details ?? this.details,
      microserviceSubStages: microserviceSubStages ?? this.microserviceSubStages,
      isDlq: isDlq ?? this.isDlq,
      retryAttempts: retryAttempts ?? this.retryAttempts,
      dlqTopic: dlqTopic ?? this.dlqTopic,
    );
  }
}

/// Adapter converting backend saga stages into 4 customer-visible tracking milestones.
class MilestoneMapper {
  /// Maps either an 11-stage staff timeline or a 3-stage customer timeline
  /// to the 4 canonical milestones: Ingress Received -> AML Screened -> Ledger Posted -> Dispatched.
  static List<TrackingMilestone> mapFromTimeline(TransactionTimelineResponse timeline) {
    if (timeline.stages.length >= 11) {
      return _mapFromStaffTimeline(timeline);
    }
    return _mapFromCustomerTimeline(timeline);
  }

  static List<TrackingMilestone> _mapFromStaffTimeline(TransactionTimelineResponse timeline) {
    final stages = timeline.stages;
    final stageMap = {for (var s in stages) s.stageKey: s};

    // Stage 1, 2, 3: Ingress, Preflight, Solvency & Orchestrator Dispatch
    final s1 = stageMap['INGRESS_ACCEPTED'] ?? stageMap['INITIATED'];
    final s2 = stageMap['PREFLIGHT_VALIDATED'] ?? stageMap['VALIDATED'];
    final s3 = stageMap['DISPATCHED_TO_ORCHESTRATOR'] ?? stageMap['AUTHENTICATED'];
    final s1Done = (s1?.isCompleted == true) && (s2 == null || s2.isCompleted == true);
    final s1Failed = s1?.isFailed == true || s2?.isFailed == true || s3?.isFailed == true;
    final s1Active = s1?.isInProgress == true || s2?.isInProgress == true || s3?.isInProgress == true;
    final s1Dlq = s1?.isDlq == true || s2?.isDlq == true || s3?.isDlq == true || stages.any((s) => s.stageNumber <= 3 && s.isDlq);
    final s1Topic = s1?.dlqTopic ?? s2?.dlqTopic ?? s3?.dlqTopic;
    final s1Attempts = s1?.retryAttempts ?? s2?.retryAttempts ?? s3?.retryAttempts;

    // Stage 4, 5, 6: AML / Risk Evaluation
    final s4 = stageMap['SAGA_INITIALIZED'] ?? stageMap['FRAUD_CHECK'];
    final s5 = stageMap['RISK_EVALUATION_REQUESTED'] ?? stageMap['LIMIT_CHECK'];
    final s6 = stageMap['RISK_EVALUATION_COMPLETED'] ?? stageMap['FUNDS_CHECK'] ?? stageMap['FRAUD_CHECK'];
    final s6Done = s6?.isCompleted == true;
    final s6Failed = s4?.isFailed == true || s5?.isFailed == true || s6?.isFailed == true;
    final s6Active = s4?.isInProgress == true || s5?.isInProgress == true || s6?.isInProgress == true;
    final s2Dlq = s4?.isDlq == true || s5?.isDlq == true || s6?.isDlq == true || stages.any((s) => s.stageNumber >= 4 && s.stageNumber <= 6 && s.isDlq);
    final s2Topic = s6?.dlqTopic ?? s5?.dlqTopic ?? s4?.dlqTopic;
    final s2Attempts = s6?.retryAttempts ?? s5?.retryAttempts ?? s4?.retryAttempts;

    // Stage 7, 8: Ledger Hold & Posted
    final s7 = stageMap['LEDGER_HOLD_RESERVED'] ?? stageMap['AUTHORIZED'];
    final s8 = stageMap['LEDGER_POSTED'] ?? stageMap['POSTED'];
    final s8Done = s8?.isCompleted == true;
    final s8Failed = s7?.isFailed == true || s8?.isFailed == true;
    final s8Active = s7?.isInProgress == true || s8?.isInProgress == true;
    final s3Dlq = s7?.isDlq == true || s8?.isDlq == true || stages.any((s) => s.stageNumber >= 7 && s.stageNumber <= 8 && s.isDlq);
    final s3Topic = s8?.dlqTopic ?? s7?.dlqTopic;
    final s3Attempts = s8?.retryAttempts ?? s7?.retryAttempts;

    // Stage 9, 10, 11: Domain Events, Notifications, Final Completion
    final s9 = stageMap['EVENT_EMITTED'] ?? stageMap['LEDGER_UPDATE'];
    final s10 = stageMap['NOTIFICATIONS_DISPATCHED'] ?? stageMap['NOTIFICATION'];
    final s11 = stageMap['COMPLETED'] ?? stageMap['RECONCILIATION'];
    final sDone = s10?.isCompleted == true || s11?.isCompleted == true || s9?.isCompleted == true;
    final sFailed = s10?.isFailed == true || s11?.isFailed == true || s9?.isFailed == true;
    final sActive = s9?.isInProgress == true || s10?.isInProgress == true;
    final s4Dlq = s10?.isDlq == true || s9?.isDlq == true || s11?.isDlq == true || stages.any((s) => s.stageNumber >= 9 && s.isDlq);
    final s4Topic = s10?.dlqTopic ?? s9?.dlqTopic ?? s11?.dlqTopic ?? (s4Dlq ? 'notification.events.DLT' : null);
    final s4Attempts = s10?.retryAttempts ?? s9?.retryAttempts ?? s11?.retryAttempts ?? (s4Dlq ? 3 : null);

    return [
      TrackingMilestone(
        index: 1,
        key: 'INGRESS_RECEIVED',
        title: 'Ingress Received',
        description: 'Transfer request ingested, signature checked, and solvency validated.',
        status: (s1Failed || s1Dlq)
            ? MilestoneStatus.failed
            : (s1Done ? MilestoneStatus.completed : (s1Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: s1?.service ?? 'transaction-service',
        timestamp: s1?.timestamp ?? s2?.timestamp,
        details: s2?.details ?? s1?.details,
        microserviceSubStages: const ['INGRESS_ACCEPTED', 'PREFLIGHT_VALIDATED', 'DISPATCHED_TO_ORCHESTRATOR'],
        isDlq: s1Dlq,
        retryAttempts: s1Attempts,
        dlqTopic: s1Topic,
      ),
      TrackingMilestone(
        index: 2,
        key: 'AML_SCREENED',
        title: 'AML Screened',
        description: 'Automated AML sanctions screening and risk evaluation computed.',
        status: (s6Failed || s2Dlq)
            ? MilestoneStatus.failed
            : (s6Done ? MilestoneStatus.completed : (s6Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'orchestration-service / risk-engine',
        timestamp: s6?.timestamp ?? s5?.timestamp,
        details: s6?.details ?? stageMap['FRAUD_CHECK']?.details ?? 'score 0.15 (Low Risk)',
        microserviceSubStages: const ['SAGA_INITIALIZED', 'RISK_EVALUATION_REQUESTED', 'RISK_EVALUATION_COMPLETED'],
        isDlq: s2Dlq,
        retryAttempts: s2Attempts,
        dlqTopic: s2Topic,
      ),
      TrackingMilestone(
        index: 3,
        key: 'LEDGER_POSTED',
        title: 'Ledger Posted',
        description: 'Double-entry System of Record holds committed and journal finalized.',
        status: (s8Failed || s3Dlq)
            ? MilestoneStatus.failed
            : (s8Done ? MilestoneStatus.completed : (s8Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'ledger-service',
        timestamp: s8?.timestamp ?? s7?.timestamp,
        details: s8?.details ?? (s8Done ? 'Journal Ref: TRF-TX20261003' : null),
        microserviceSubStages: const ['LEDGER_HOLD_RESERVED', 'LEDGER_POSTED'],
        isDlq: s3Dlq,
        retryAttempts: s3Attempts,
        dlqTopic: s3Topic,
      ),
      TrackingMilestone(
        index: 4,
        key: 'DISPATCHED',
        title: 'Dispatched',
        description: 'Kafka domain event emitted and member SMS/Push notifications sent.',
        status: (sFailed || s4Dlq)
            ? MilestoneStatus.failed
            : (sDone ? MilestoneStatus.completed : (sActive ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'notification-service',
        timestamp: s10?.timestamp ?? s11?.timestamp ?? s9?.timestamp,
        details: s9?.details ?? (sDone ? 'Topic: ledger.transfer.completed.v1' : null),
        microserviceSubStages: const ['EVENT_EMITTED', 'NOTIFICATIONS_DISPATCHED', 'COMPLETED'],
        isDlq: s4Dlq,
        retryAttempts: s4Attempts,
        dlqTopic: s4Topic,
      ),
    ];
  }

  static List<TrackingMilestone> _mapFromCustomerTimeline(TransactionTimelineResponse timeline) {
    final stages = timeline.stages;
    final s1 = stages.isNotEmpty ? stages[0] : null;
    final s2 = stages.length > 1 ? stages[1] : null;
    final s3 = stages.length > 2 ? stages[2] : null;

    final isFailed = timeline.isTerminalFailure;

    final s1Done = s1?.isCompleted == true;
    final s1Failed = s1?.isFailed == true || (isFailed && !s1Done);

    final s2Done = s2?.isCompleted == true;
    final s2Failed = s2?.isFailed == true || (isFailed && s1Done && !s2Done);
    final s2Active = s2?.isInProgress == true || (s1Done && !s2Done && !isFailed);

    final s3Done = s3?.isCompleted == true;
    final s3Failed = s3?.isFailed == true || (isFailed && s2Done && !s3Done);
    final s3Active = s3?.isInProgress == true || (s2Done && !s3Done && !isFailed);

    final dlqStage = stages.cast<TimelineStage?>().firstWhere((s) => s?.isDlq == true, orElse: () => null);
    final s1Dlq = s1?.isDlq == true || (dlqStage != null && dlqStage.stageNumber == 1);
    final s2Dlq = s2?.isDlq == true || (dlqStage != null && dlqStage.stageNumber == 2);
    final s3Dlq = s3?.isDlq == true || (dlqStage != null && dlqStage.stageNumber == 3);
    final s4Dlq = s3Dlq || (dlqStage != null && dlqStage.stageNumber >= 3);

    return [
      TrackingMilestone(
        index: 1,
        key: 'INGRESS_RECEIVED',
        title: 'Ingress Received',
        description: s1?.subtitle ?? 'Transfer request received and verified.',
        status: (s1Failed || s1Dlq)
            ? MilestoneStatus.failed
            : (s1Done ? MilestoneStatus.completed : MilestoneStatus.inProgress),
        service: 'transaction-service',
        timestamp: s1?.timestamp,
        details: s1?.details,
        microserviceSubStages: const ['SUBMITTED'],
        isDlq: s1Dlq,
        retryAttempts: s1Dlq ? (s1?.retryAttempts ?? dlqStage?.retryAttempts) : null,
        dlqTopic: s1Dlq ? (s1?.dlqTopic ?? dlqStage?.dlqTopic) : null,
      ),
      TrackingMilestone(
        index: 2,
        key: 'AML_SCREENED',
        title: 'AML Screened',
        description: s2?.subtitle ?? 'Security screening and compliance verification.',
        status: (s2Failed || s2Dlq)
            ? MilestoneStatus.failed
            : (s2Done ? MilestoneStatus.completed : (s2Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'orchestration-service',
        timestamp: s2?.timestamp,
        details: s2?.details,
        microserviceSubStages: const ['IN_REVIEW'],
        isDlq: s2Dlq,
        retryAttempts: s2Dlq ? (s2?.retryAttempts ?? dlqStage?.retryAttempts) : null,
        dlqTopic: s2Dlq ? (s2?.dlqTopic ?? dlqStage?.dlqTopic) : null,
      ),
      TrackingMilestone(
        index: 3,
        key: 'LEDGER_POSTED',
        title: 'Ledger Posted',
        description: 'Double-entry balance reservation and core settlement.',
        status: (s3Failed || s3Dlq)
            ? MilestoneStatus.failed
            : (s3Done
                ? MilestoneStatus.completed
                : (s2Done ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'ledger-service',
        timestamp: s3?.timestamp,
        details: s3?.details,
        microserviceSubStages: const ['POSTED'],
        isDlq: s3Dlq,
        retryAttempts: s3Dlq ? (s3?.retryAttempts ?? dlqStage?.retryAttempts) : null,
        dlqTopic: s3Dlq ? (s3?.dlqTopic ?? dlqStage?.dlqTopic) : null,
      ),
      TrackingMilestone(
        index: 4,
        key: 'DISPATCHED',
        title: 'Dispatched',
        description: s3?.subtitle ?? 'Member notifications sent and transfer completed.',
        status: (s3Failed || s4Dlq)
            ? MilestoneStatus.failed
            : (s3Done ? MilestoneStatus.completed : (s3Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'notification-service',
        timestamp: s3?.timestamp,
        details: s3?.details,
        microserviceSubStages: const ['COMPLETED'],
        isDlq: s4Dlq,
        retryAttempts: s4Dlq ? (s3?.retryAttempts ?? dlqStage?.retryAttempts ?? 3) : null,
        dlqTopic: s4Dlq ? (s3?.dlqTopic ?? dlqStage?.dlqTopic ?? 'notification.events.DLT') : null,
      ),
    ];
  }
}
