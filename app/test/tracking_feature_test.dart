import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/core/models/remittance_models.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/core/theme/app_theme.dart';
import 'package:app/features/tracking/tracking.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('MilestoneMapper Unit Tests', () {
    test('Correctly maps 11-stage staff fixture into 4 canonical milestones', () async {
      final apiClient = ApiClient();
      final fixtureJson = await apiClient.loadFixtureJson('T3-timeline-staff.response.json');
      final staffTimeline = TransactionTimelineResponse.fromJson(fixtureJson);

      final milestones = MilestoneMapper.mapFromTimeline(staffTimeline);

      expect(milestones.length, 4);
      expect(milestones[0].title, 'Ingress Received');
      expect(milestones[0].status, MilestoneStatus.completed);
      expect(milestones[0].service, 'transaction-service');

      expect(milestones[1].title, 'Risk & AML Screened');
      expect(milestones[1].status, MilestoneStatus.completed);
      expect(milestones[1].details, contains('score 0.15'));

      expect(milestones[2].title, 'Ledger Posted');
      expect(milestones[2].status, MilestoneStatus.completed);
      expect(milestones[2].details, contains('TRF-TX20261003'));

      expect(milestones[3].title, 'Dispatched');
      expect(milestones[3].status, MilestoneStatus.completed);
      expect(milestones[3].details, contains('ledger.transfer.completed.v1'));
    });

    test('Correctly maps 3-stage customer fixture into 4 milestones', () async {
      final apiClient = ApiClient();
      final fixtureJson = await apiClient.loadFixtureJson('T3-timeline-customer.response.json');
      final customerTimeline = TransactionTimelineResponse.fromJson(fixtureJson);

      final milestones = MilestoneMapper.mapFromTimeline(customerTimeline);

      expect(milestones.length, 4);
      expect(milestones[0].status, MilestoneStatus.completed);
      expect(milestones[1].status, MilestoneStatus.inProgress);
      expect(milestones[2].status, MilestoneStatus.pending);
      expect(milestones[3].status, MilestoneStatus.pending);
    });

    test('Correctly maps terminal failure state into failed milestone', () {
      final failedTimeline = TransactionTimelineResponse(
        trackingReference: 'TX-FAIL-999',
        overallStatus: 'FAILED',
        view: 'CUSTOMER',
        currentStage: 2,
        totalStages: 3,
        failureMessage: 'Automated AML sanctions screening blocked transaction',
        stages: [
          TimelineStage(
            stageNumber: 1,
            stageKey: 'SUBMITTED',
            title: 'Transfer Submitted',
            status: 'COMPLETED',
          ),
          TimelineStage(
            stageNumber: 2,
            stageKey: 'IN_REVIEW',
            title: 'Security Screening',
            status: 'FAILED',
          ),
          TimelineStage(
            stageNumber: 3,
            stageKey: 'COMPLETED',
            title: 'Transfer Completed',
            status: 'PENDING',
          ),
        ],
      );

      final milestones = MilestoneMapper.mapFromTimeline(failedTimeline);

      expect(milestones.length, 4);
      expect(milestones[0].status, MilestoneStatus.completed);
      expect(milestones[1].status, MilestoneStatus.failed);
      expect(failedTimeline.isTerminalFailure, isTrue);
    });
  });

  group('TrackingView Widget Tests', () {
    late TransactionTimelineResponse staffTimeline;
    late ApiClient apiClient;

    setUp(() async {
      apiClient = ApiClient();
      final fixtureJson = await apiClient.loadFixtureJson('T3-timeline-staff.response.json');
      staffTimeline = TransactionTimelineResponse.fromJson(fixtureJson);
    });

    testWidgets('Renders 4-stage interactive visual stepper headers and milestones', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-20261003-8F3A21C0',
              apiClient: apiClient,
              initialTimeline: staffTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      // Verify header elements and 4 milestone titles
      expect(find.text('Transaction Milestones'), findsOneWidget);
      expect(find.text('Ingress Received'), findsWidgets);
      expect(find.text('Risk & AML Screened'), findsWidgets);
      expect(find.text('Ledger Posted'), findsWidgets);
      expect(find.text('Dispatched'), findsWidgets);
      expect(find.text('Customer (4)'), findsOneWidget);
      expect(find.text('Staff Audit (11)'), findsOneWidget);
      expect(find.textContaining('AUDIT INSPECTOR'), findsOneWidget);
    });

    testWidgets('Tapping a milestone node updates the audit inspector', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-20261003-8F3A21C0',
              apiClient: apiClient,
              initialTimeline: staffTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      // Tap on Risk & AML Screened node
      final amlNode = find.text('Risk & AML Screened').first;
      await tester.tap(amlNode);
      await tester.pump();

      expect(find.textContaining('STAGE 2 AUDIT INSPECTOR'), findsOneWidget);
      expect(find.textContaining('orchestration-service / risk-engine'), findsOneWidget);

      // Tap on Ledger Posted node
      final ledgerNode = find.text('Ledger Posted').first;
      await tester.tap(ledgerNode);
      await tester.pump();

      expect(find.textContaining('STAGE 3 AUDIT INSPECTOR'), findsOneWidget);
      expect(find.textContaining('ledger-service'), findsOneWidget);
    });

    testWidgets('Switching mode displays 11-stage saga decomposition and switches back', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-20261003-8F3A21C0',
              apiClient: apiClient,
              initialTimeline: staffTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      // Toggle to 11-stage staff view
      final staffToggle = find.text('Staff Audit (11)');
      await tester.tap(staffToggle);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pump();

      expect(find.text('11-Stage Microservice Saga Decomposition'), findsOneWidget);
      expect(find.textContaining('Ingress Accepted'), findsOneWidget);

      // Toggle back to 4-stage customer view
      final customerToggle = find.text('Customer (4)');
      await tester.tap(customerToggle);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pump();

      expect(find.text('Transaction Milestones'), findsOneWidget);
    });

    testWidgets('Renders failure banner when timeline has failureMessage', (tester) async {
      final failureTimeline = TransactionTimelineResponse(
        trackingReference: 'TX-FAIL-456',
        overallStatus: 'FAILED',
        view: 'CUSTOMER',
        currentStage: 2,
        totalStages: 3,
        failureMessage: 'Sanctions check flagged: high-risk PEP identified',
        stages: [
          TimelineStage(
            stageNumber: 1,
            stageKey: 'SUBMITTED',
            title: 'Transfer Submitted',
            status: 'COMPLETED',
          ),
          TimelineStage(
            stageNumber: 2,
            stageKey: 'IN_REVIEW',
            title: 'Security Screening',
            status: 'FAILED',
          ),
        ],
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-FAIL-456',
              apiClient: apiClient,
              initialTimeline: failureTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      expect(find.text('Transaction Processing Issue'), findsOneWidget);
      expect(find.text('Sanctions check flagged: high-risk PEP identified'), findsOneWidget);
    });

    testWidgets('Renders responsive vertical stepper layout on narrow screens', (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-20261003-8F3A21C0',
              apiClient: apiClient,
              initialTimeline: staffTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      expect(find.text('Transaction Milestones'), findsOneWidget);
      expect(find.text('Ingress Received'), findsWidgets);
      expect(find.text('Risk & AML Screened'), findsWidgets);
    });

    testWidgets('Customer transaction mode hides staff toggle and only displays 4 milestone steps', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-20261003-8F3A21C0',
              apiClient: apiClient,
              initialTimeline: staffTimeline,
              animate: false,
              autoPoll: false,
              allowStaffMode: false,
            ),
          ),
        ),
      );
      await tester.pump();

      // Customer sees 4 milestones
      expect(find.text('Transaction Milestones'), findsOneWidget);
      expect(find.text('Ingress Received'), findsWidgets);
      expect(find.text('Risk & AML Screened'), findsWidgets);
      expect(find.text('Ledger Posted'), findsWidgets);
      expect(find.text('Dispatched'), findsWidgets);

      // Customer MUST NOT see the 11-stage staff audit toggle or deep dive
      expect(find.text('Staff Audit (11)'), findsNothing);
      expect(find.text('Customer (4)'), findsNothing);
      expect(find.text('11-Stage Microservice Saga Decomposition'), findsNothing);
      expect(find.textContaining('AUDIT INSPECTOR'), findsNothing);
      expect(find.textContaining('MILESTONE 4 STATUS'), findsOneWidget);
    });

    test('Customer timeline mapping surfaces Risk & AML Screened with explicit risk explanation on RISK_DECLINED', () {
      final customerFailedTimeline = TransactionTimelineResponse(
        trackingReference: 'TX-20261012-452F2E61',
        view: 'CUSTOMER',
        currentStage: 2,
        totalStages: 3,
        overallStatus: 'FAILED',
        failureMessage: 'RISK_DECLINED',
        stages: [
          TimelineStage(
            stageNumber: 1,
            stageKey: 'SUBMITTED',
            title: 'Transfer Submitted',
            subtitle: 'Your transfer request has been received and verified.',
            status: 'COMPLETED',
          ),
          TimelineStage(
            stageNumber: 2,
            stageKey: 'IN_REVIEW',
            title: 'Security & Clearing',
            subtitle: 'Automated security screening and cooperative network clearing.',
            status: 'FAILED',
          ),
          TimelineStage(
            stageNumber: 3,
            stageKey: 'COMPLETED',
            title: 'Transfer Completed',
            subtitle: 'Funds have been successfully deposited into the recipient account.',
            status: 'PENDING',
          ),
        ],
      );

      final milestones = MilestoneMapper.mapFromTimeline(customerFailedTimeline);
      expect(milestones.length, 4);
      expect(milestones[1].title, 'Risk & AML Screened');
      expect(milestones[1].status, MilestoneStatus.failed);
      expect(milestones[1].description, contains('Declined by automated risk engine'));
    });
  });
}
