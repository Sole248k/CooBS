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

    // Stage 1 & 2: Ingress & Preflight
    final s1 = stageMap['INGRESS_ACCEPTED'];
    final s2 = stageMap['PREFLIGHT_VALIDATED'];
    final s1Done = s1?.isCompleted == true && s2?.isCompleted == true;
    final s1Failed = s1?.isFailed == true || s2?.isFailed == true;
    final s1Active = s1?.isInProgress == true || s2?.isInProgress == true;

    // Stage 5 & 6: AML / Risk Evaluation
    final s6 = stageMap['RISK_EVALUATION_COMPLETED'];
    final s5 = stageMap['RISK_EVALUATION_REQUESTED'];
    final s6Done = s6?.isCompleted == true;
    final s6Failed = s6?.isFailed == true;
    final s6Active = s5?.isInProgress == true || s6?.isInProgress == true;

    // Stage 7 & 8: Ledger Hold & Posted
    final s7 = stageMap['LEDGER_HOLD_RESERVED'];
    final s8 = stageMap['LEDGER_POSTED'];
    final s8Done = s8?.isCompleted == true;
    final s8Failed = s8?.isFailed == true;
    final s8Active = s7?.isInProgress == true || s8?.isInProgress == true;

    // Stage 9, 10, 11: Domain Events, Notifications, Final Completion
    final s10 = stageMap['NOTIFICATIONS_DISPATCHED'];
    final s11 = stageMap['COMPLETED'];
    final s9 = stageMap['EVENT_EMITTED'];
    final sDone = s10?.isCompleted == true || s11?.isCompleted == true;
    final sFailed = s10?.isFailed == true || s11?.isFailed == true;
    final sActive = s9?.isInProgress == true || s10?.isInProgress == true;

    return [
      TrackingMilestone(
        index: 1,
        key: 'INGRESS_RECEIVED',
        title: 'Ingress Received',
        description: 'Transfer request ingested, signature checked, and solvency validated.',
        status: s1Failed
            ? MilestoneStatus.failed
            : (s1Done ? MilestoneStatus.completed : (s1Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'transaction-service',
        timestamp: s1?.timestamp ?? s2?.timestamp,
        details: s2?.details ?? s1?.details,
        microserviceSubStages: const ['INGRESS_ACCEPTED', 'PREFLIGHT_VALIDATED', 'DISPATCHED_TO_ORCHESTRATOR'],
      ),
      TrackingMilestone(
        index: 2,
        key: 'AML_SCREENED',
        title: 'AML Screened',
        description: 'Automated AML sanctions screening and risk evaluation computed.',
        status: s6Failed
            ? MilestoneStatus.failed
            : (s6Done ? MilestoneStatus.completed : (s6Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'orchestration-service / risk-engine',
        timestamp: s6?.timestamp ?? s5?.timestamp,
        details: s6?.details,
        microserviceSubStages: const ['SAGA_INITIALIZED', 'RISK_EVALUATION_REQUESTED', 'RISK_EVALUATION_COMPLETED'],
      ),
      TrackingMilestone(
        index: 3,
        key: 'LEDGER_POSTED',
        title: 'Ledger Posted',
        description: 'Double-entry System of Record holds committed and journal finalized.',
        status: s8Failed
            ? MilestoneStatus.failed
            : (s8Done ? MilestoneStatus.completed : (s8Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'ledger-service',
        timestamp: s8?.timestamp ?? s7?.timestamp,
        details: s8?.details,
        microserviceSubStages: const ['LEDGER_HOLD_RESERVED', 'LEDGER_POSTED'],
      ),
      TrackingMilestone(
        index: 4,
        key: 'DISPATCHED',
        title: 'Dispatched',
        description: 'Kafka domain event emitted and member SMS/Push notifications sent.',
        status: sFailed
            ? MilestoneStatus.failed
            : (sDone ? MilestoneStatus.completed : (sActive ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'notification-service',
        timestamp: s10?.timestamp ?? s11?.timestamp ?? s9?.timestamp,
        details: s9?.details,
        microserviceSubStages: const ['EVENT_EMITTED', 'NOTIFICATIONS_DISPATCHED', 'COMPLETED'],
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
    final s2Active = s2?.isInProgress == true;

    final s3Done = s3?.isCompleted == true;
    final s3Failed = s3?.isFailed == true || (isFailed && s2Done && !s3Done);
    final s3Active = s3?.isInProgress == true;

    return [
      TrackingMilestone(
        index: 1,
        key: 'INGRESS_RECEIVED',
        title: 'Ingress Received',
        description: s1?.subtitle ?? 'Transfer request received and verified.',
        status: s1Failed
            ? MilestoneStatus.failed
            : (s1Done ? MilestoneStatus.completed : MilestoneStatus.inProgress),
        service: 'transaction-service',
        timestamp: s1?.timestamp,
        details: s1?.details,
        microserviceSubStages: const ['SUBMITTED'],
      ),
      TrackingMilestone(
        index: 2,
        key: 'AML_SCREENED',
        title: 'AML Screened',
        description: s2?.subtitle ?? 'Security screening and compliance verification.',
        status: s2Failed
            ? MilestoneStatus.failed
            : (s2Done ? MilestoneStatus.completed : (s2Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'orchestration-service',
        timestamp: s2?.timestamp,
        details: s2?.details,
        microserviceSubStages: const ['IN_REVIEW'],
      ),
      TrackingMilestone(
        index: 3,
        key: 'LEDGER_POSTED',
        title: 'Ledger Posted',
        description: 'Double-entry balance reservation and core settlement.',
        status: s3Failed
            ? MilestoneStatus.failed
            : (s3Done
                ? MilestoneStatus.completed
                : (s2Done ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'ledger-service',
        timestamp: s3?.timestamp,
        details: s3?.details,
        microserviceSubStages: const ['POSTED'],
      ),
      TrackingMilestone(
        index: 4,
        key: 'DISPATCHED',
        title: 'Dispatched',
        description: s3?.subtitle ?? 'Member notifications sent and transfer completed.',
        status: s3Failed
            ? MilestoneStatus.failed
            : (s3Done ? MilestoneStatus.completed : (s3Active ? MilestoneStatus.inProgress : MilestoneStatus.pending)),
        service: 'notification-service',
        timestamp: s3?.timestamp,
        details: s3?.details,
        microserviceSubStages: const ['COMPLETED'],
      ),
    ];
  }
}
