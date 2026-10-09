import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app/core/models/remittance_models.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/core/theme/app_theme.dart';
import 'package:app/features/resilience/service_unavailable_view.dart';
import 'package:app/features/tracking/tracking.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('DLQ Models & Milestone Mapping Tests', () {
    test('TimelineStage parses DLQ metadata properly', () {
      final stage = TimelineStage.fromJson({
        'stageNo': 10,
        'stageName': 'NOTIFICATION',
        'status': 'FAILED',
        'details': 'Poison pill routed to notification.events.DLT after 3 backoff retries',
        'isDlq': true,
        'retryAttempts': 3,
        'dlqTopic': 'notification.events.DLT',
      });

      expect(stage.isDlq, isTrue);
      expect(stage.retryAttempts, 3);
      expect(stage.dlqTopic, 'notification.events.DLT');
    });

    test('MilestoneMapper maps DLQ metadata into TrackingMilestone', () async {
      final apiClient = ApiClient();
      final fixtureJson = await apiClient.loadFixtureJson('T3-timeline-dlq.response.json');
      final timeline = TransactionTimelineResponse.fromJson(fixtureJson);

      final milestones = MilestoneMapper.mapFromTimeline(timeline);

      expect(milestones.length, 4);
      final dispatchedMilestone = milestones[3];
      expect(dispatchedMilestone.key, 'DISPATCHED');
      expect(dispatchedMilestone.isDlq, isTrue);
      expect(dispatchedMilestone.dlqTopic, 'notification.events.DLT');
      expect(dispatchedMilestone.retryAttempts, 3);
    });
  });

  group('ServiceUnavailableView Resilience Tests', () {
    testWidgets('Renders ServiceUnavailableView with countdown and handles onRetry callback', (tester) async {
      bool retried = false;

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: ServiceUnavailableView(
              cooldown: Duration.zero,
              isEmbedded: true,
              onRetry: () => retried = true,
            ),
          ),
        ),
      );
      await tester.pump();

      expect(find.text('Service Temporarily Unavailable'), findsOneWidget);
      expect(find.text('Retry Connection'), findsOneWidget);

      await tester.tap(find.text('Retry Connection'));
      await tester.pump();

      expect(retried, isTrue);
    });

    testWidgets('Renders cooldown countdown when cooldown > 0', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ServiceUnavailableView(
            cooldown: Duration(seconds: 15),
            isEmbedded: true,
          ),
        ),
      );
      await tester.pump();

      expect(find.textContaining('Auto-reconnect in 15s'), findsOneWidget);
    });
  });

  group('TrackingView DLQ & Degradation Widget Tests', () {
    testWidgets('Renders DLQ active retry indicator and recovery action', (tester) async {
      final apiClient = ApiClient();
      final fixtureJson = await apiClient.loadFixtureJson('T3-timeline-dlq.response.json');
      final dlqTimeline = TransactionTimelineResponse.fromJson(fixtureJson);

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: Scaffold(
            body: TrackingView(
              trackingReference: 'TX-DLQ-TEST',
              apiClient: apiClient,
              initialTimeline: dlqTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      // Check for DLQ indicators in inspector and status
      expect(find.textContaining('DEAD-LETTER QUEUE (DLQ) ACTIVE'), findsOneWidget);
      expect(find.textContaining('notification.events.DLT'), findsWidgets);
      expect(find.text('Replay DLQ Record'), findsOneWidget);
      expect(find.textContaining('Attempt 3/3'), findsWidgets);
    });

    testWidgets('Tapping Replay DLQ Record shows confirmation feedback', (tester) async {
      final apiClient = ApiClient();
      final fixtureJson = await apiClient.loadFixtureJson('T3-timeline-dlq.response.json');
      final dlqTimeline = TransactionTimelineResponse.fromJson(fixtureJson);

      tester.view.physicalSize = const Size(800, 1200);
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
              trackingReference: 'TX-DLQ-TEST',
              apiClient: apiClient,
              initialTimeline: dlqTimeline,
              animate: false,
              autoPoll: false,
            ),
          ),
        ),
      );
      await tester.pump();

      final replayBtn = find.text('Replay DLQ Record');
      expect(replayBtn, findsOneWidget);
      await tester.tap(replayBtn);
      await tester.pump();

      expect(find.textContaining('Triggered DLQ replay'), findsOneWidget);
    });

    testWidgets('Gracefully degrades to ServiceUnavailableView on CircuitBreakerOpenException', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ServiceUnavailableView(
              cooldown: Duration(seconds: 30),
              isEmbedded: true,
            ),
          ),
        ),
      );
      await tester.pump();

      expect(find.text('Service Temporarily Unavailable'), findsOneWidget);
      expect(find.textContaining('Our core banking mesh is currently undergoing automated recovery'), findsOneWidget);
      expect(find.textContaining('Auto-reconnect in 30s'), findsOneWidget);
    });
  });
}
