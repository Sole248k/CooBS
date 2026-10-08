import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/models/remittance_models.dart';
import '../../core/network/api_client.dart';
import '../../core/network/api_exceptions.dart';
import '../../core/theme/app_theme.dart';
import 'tracking_models.dart';

/// Interactive Visual Stepper & Saga Tracker for Transaction Timelines.
/// Visualizes the 4-stage business pipeline:
/// Ingress Received -> AML Screened -> Ledger Posted -> Dispatched.
/// Adapts responsively between horizontal and vertical layouts, supports
/// auto-polling until terminal state, failure message rendering, and role-based view switching.
class TrackingView extends StatefulWidget {
  final String? trackingReference;
  final ApiClient? apiClient;
  final TransactionTimelineResponse? initialTimeline;
  final bool animate;
  final bool autoPoll;

  const TrackingView({
    super.key,
    this.trackingReference,
    this.apiClient,
    this.initialTimeline,
    this.animate = true,
    this.autoPoll = true,
  });

  @override
  State<TrackingView> createState() => _TrackingViewState();
}

class _TrackingViewState extends State<TrackingView> with SingleTickerProviderStateMixin {
  late final ApiClient _apiClient;
  late final TextEditingController _searchController;
  late final AnimationController _pulseController;

  // View state
  bool _isLoading = true;
  String? _errorMessage;
  TransactionTimelineResponse? _rawTimeline;
  List<TrackingMilestone> _milestones = [];
  int _selectedMilestoneIndex = 0; // 0 to 3
  bool _isStaffMode = false;
  Timer? _pollingTimer;
  bool _userSelectedMilestone = false;
  bool _isPollingInFlight = false;

  static final DateFormat _dateFormat = DateFormat('MMM dd, yyyy • HH:mm:ss');

  @override
  void initState() {
    super.initState();
    _apiClient = widget.apiClient ?? ApiClient();
    final initialRef = widget.trackingReference ?? 'TX-20261003-8F3A21C0';
    _searchController = TextEditingController(text: initialRef);

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );
    if (widget.animate) {
      _pulseController.repeat(reverse: true);
    }

    if (widget.initialTimeline != null) {
      _applyTimeline(widget.initialTimeline!);
      _isLoading = false;
    } else {
      _loadTimeline(initialRef);
    }
  }

  @override
  void didUpdateWidget(TrackingView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.trackingReference != null &&
        widget.trackingReference != oldWidget.trackingReference &&
        widget.trackingReference != _searchController.text) {
      _searchController.text = widget.trackingReference!;
      _loadTimeline(widget.trackingReference!);
    }
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    _pulseController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _applyTimeline(TransactionTimelineResponse timeline, {bool isAutoPoll = false}) {
    _rawTimeline = timeline;
    _milestones = MilestoneMapper.mapFromTimeline(timeline);

    if (!_userSelectedMilestone || !isAutoPoll) {
      final activeIdx = _milestones.indexWhere((m) => m.isInProgress);
      final failedIdx = _milestones.indexWhere((m) => m.isFailed);

      if (failedIdx != -1) {
        _selectedMilestoneIndex = failedIdx;
      } else if (activeIdx != -1) {
        _selectedMilestoneIndex = activeIdx;
      } else {
        _selectedMilestoneIndex = _milestones.isNotEmpty ? _milestones.length - 1 : 0;
      }
    }

    _managePolling(timeline);
  }

  void _managePolling(TransactionTimelineResponse timeline) {
    _pollingTimer?.cancel();
    if (!widget.autoPoll) return;
    // In accordance with Capstone Roadmap: Poll GET /timeline once a second until reaching terminal state
    if (!timeline.isTerminalSuccess && !timeline.isTerminalFailure) {
      _pollingTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!mounted) {
          timer.cancel();
          return;
        }
        _silentPollTimeline(_searchController.text.trim());
      });
    }
  }

  Future<void> _silentPollTimeline(String reference) async {
    if (_isPollingInFlight) return;
    _isPollingInFlight = true;
    try {
      final queryParams = _isStaffMode ? {'view': 'staff'} : <String, String>{};
      final safeRef = Uri.encodeComponent(reference);
      final data = await _apiClient.get(
        '/mobile/transactions/$safeRef/timeline',
        queryParams: queryParams,
      );
      if (mounted) {
        final timeline = TransactionTimelineResponse.fromJson(data);
        setState(() => _applyTimeline(timeline, isAutoPoll: true));
      }
    } catch (_) {
      // Background poll silently preserves existing UI state
    } finally {
      _isPollingInFlight = false;
    }
  }

  Future<void> _loadTimeline(String reference) async {
    _pollingTimer?.cancel();
    _userSelectedMilestone = false;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final queryParams = _isStaffMode ? {'view': 'staff'} : <String, String>{};
      final safeRef = Uri.encodeComponent(reference);
      final data = await _apiClient.get(
        '/mobile/transactions/$safeRef/timeline',
        queryParams: queryParams,
      );
      final timeline = TransactionTimelineResponse.fromJson(data);

      if (mounted) {
        setState(() {
          _applyTimeline(timeline);
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e is ApiException ? e.message : 'Unable to load timeline. Please try again.';
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final isWide = constraints.maxWidth >= 640;

        return Material(
          color: Colors.transparent,
          child: CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildSearchAndHeader(),
                      const SizedBox(height: 16),
                      if (_isLoading)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 60),
                          child: Center(
                            child: CircularProgressIndicator(color: AppTheme.navyPrimary),
                          ),
                        )
                      else if (_errorMessage != null)
                        _buildErrorCard(_errorMessage!)
                      else ...[
                        if (_rawTimeline?.failureMessage != null) ...[
                          _buildFailureBanner(_rawTimeline!.failureMessage!),
                          const SizedBox(height: 16),
                        ],
                        _buildHeaderBadgesAndModeToggle(),
                        const SizedBox(height: 16),
                        if (_isStaffMode && _rawTimeline != null && _rawTimeline!.stages.length >= 11)
                          _build11StageDeepDive()
                        else ...[
                          _buildStepperCard(isWide: isWide),
                          const SizedBox(height: 16),
                          _buildSelectedDetailInspector(),
                        ],
                      ],
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildSearchAndHeader() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: TextField(
            controller: _searchController,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
            decoration: InputDecoration(
              labelText: 'Transaction Reference',
              hintText: 'e.g. TX-20261003-8F3A21C0',
              prefixIcon: const Icon(Icons.search_rounded, size: 20, color: AppTheme.navyPrimary),
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              suffixIcon: IconButton(
                icon: const Icon(Icons.arrow_forward_rounded, size: 20),
                onPressed: () => _loadTimeline(_searchController.text.trim()),
              ),
            ),
            onSubmitted: (val) => _loadTimeline(val.trim()),
          ),
        ),
        const SizedBox(width: 12),
        IconButton.filledTonal(
          icon: const Icon(Icons.refresh_rounded),
          tooltip: 'Refresh Timeline',
          onPressed: () => _loadTimeline(_searchController.text.trim()),
        ),
      ],
    );
  }

  Widget _buildFailureBanner(String message) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.errorCarmine.withAlpha(20),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.errorCarmine.withAlpha(80)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.cancel_rounded, color: AppTheme.errorCarmine, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Transaction Processing Issue',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.errorCarmine,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  message,
                  style: const TextStyle(fontSize: 13, color: AppTheme.textPrimary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeaderBadgesAndModeToggle() {
    final status = _rawTimeline?.overallStatus ?? 'PROCESSING';
    final isSuccess = _rawTimeline?.isTerminalSuccess == true;
    final isFailed = _rawTimeline?.isTerminalFailure == true;

    Color badgeColor = AppTheme.infoBlue;
    if (isSuccess) badgeColor = AppTheme.successGreen;
    if (isFailed) badgeColor = AppTheme.errorCarmine;

    return Wrap(
      alignment: WrapAlignment.spaceBetween,
      crossAxisAlignment: WrapCrossAlignment.center,
      runSpacing: 10,
      spacing: 12,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 6,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: AppTheme.navyPrimary.withAlpha(20),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                'REF: ${_rawTimeline?.trackingReference ?? _searchController.text}',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.navyPrimary,
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: badgeColor.withAlpha(25),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                status,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: badgeColor,
                ),
              ),
            ),
            if (_pollingTimer != null && _pollingTimer!.isActive)
              const SizedBox(
                width: 14,
                height: 14,
                child: CircularProgressIndicator(strokeWidth: 2, color: AppTheme.navyPrimary),
              ),
          ],
        ),
        SegmentedButton<bool>(
          segments: const [
            ButtonSegment<bool>(
              value: false,
              label: Text('Customer (4)', style: TextStyle(fontSize: 12)),
            ),
            ButtonSegment<bool>(
              value: true,
              label: Text('Staff Audit (11)', style: TextStyle(fontSize: 12)),
            ),
          ],
          selected: {_isStaffMode},
          onSelectionChanged: (set) {
            final newMode = set.first;
            setState(() => _isStaffMode = newMode);
            if (newMode && (_rawTimeline == null || _rawTimeline!.stages.length < 11)) {
              _loadTimeline(_searchController.text.trim());
            }
          },
          style: ButtonStyle(
            visualDensity: VisualDensity.compact,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
        ),
      ],
    );
  }

  Widget _buildStepperCard({required bool isWide}) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.borderSubtle),
      ),
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Transaction Milestones',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppTheme.navyPrimary,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              'Tap any milestone checkpoint to inspect service telemetry and execution records.',
              style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
            ),
            const SizedBox(height: 24),
            isWide ? _buildHorizontalStepper() : _buildVerticalStepper(),
          ],
        ),
      ),
    );
  }

  Widget _buildHorizontalStepper() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: List.generate(_milestones.length * 2 - 1, (i) {
        if (i.isOdd) {
          final leftIdx = i ~/ 2;
          final isCompleted = _milestones[leftIdx].isCompleted;
          return Expanded(
            child: Container(
              margin: const EdgeInsets.only(top: 18),
              height: 3,
              decoration: BoxDecoration(
                color: isCompleted ? AppTheme.emeraldAccent : AppTheme.borderSubtle,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          );
        }

        final milestoneIdx = i ~/ 2;
        final milestone = _milestones[milestoneIdx];
        final isSelected = _selectedMilestoneIndex == milestoneIdx;

        return GestureDetector(
          onTap: () => setState(() {
            _selectedMilestoneIndex = milestoneIdx;
            _userSelectedMilestone = true;
          }),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildMilestoneNode(milestone, isSelected),
              const SizedBox(height: 10),
              SizedBox(
                width: 100,
                child: Text(
                  milestone.title,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.w600,
                    color: isSelected ? AppTheme.navyPrimary : AppTheme.textSecondary,
                  ),
                ),
              ),
            ],
          ),
        );
      }),
    );
  }

  Widget _buildVerticalStepper() {
    return Column(
      children: List.generate(_milestones.length, (idx) {
        final milestone = _milestones[idx];
        final isSelected = _selectedMilestoneIndex == idx;
        final isLast = idx == _milestones.length - 1;

        return InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () => setState(() {
            _selectedMilestoneIndex = idx;
            _userSelectedMilestone = true;
          }),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Column(
                  children: [
                    _buildMilestoneNode(milestone, isSelected),
                    if (!isLast)
                      Container(
                        width: 2,
                        height: 44,
                        color: milestone.isCompleted ? AppTheme.emeraldAccent : AppTheme.borderSubtle,
                      ),
                  ],
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              milestone.title,
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: isSelected ? AppTheme.navyPrimary : AppTheme.textPrimary,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          _buildStatusPill(milestone.status),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        milestone.description,
                        style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      }),
    );
  }

  Widget _buildMilestoneNode(TrackingMilestone milestone, bool isSelected) {
    Color bg = AppTheme.borderSubtle;
    Color border = AppTheme.borderSubtle;
    Widget icon = Text(
      '${milestone.index}',
      style: const TextStyle(color: AppTheme.textMuted, fontSize: 13, fontWeight: FontWeight.bold),
    );

    if (milestone.isCompleted) {
      bg = AppTheme.emeraldAccent;
      border = AppTheme.emeraldDark;
      icon = const Icon(Icons.check_rounded, color: AppTheme.navyDark, size: 20);
    } else if (milestone.isInProgress) {
      bg = AppTheme.warningAmber.withAlpha(40);
      border = AppTheme.warningAmber;
      icon = const SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(
          strokeWidth: 2.2,
          color: AppTheme.warningAmber,
        ),
      );
    } else if (milestone.isFailed) {
      bg = AppTheme.errorCarmine.withAlpha(30);
      border = AppTheme.errorCarmine;
      icon = const Icon(Icons.close_rounded, color: AppTheme.errorCarmine, size: 20);
    }

    Widget node = AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        color: bg,
        shape: BoxShape.circle,
        border: Border.all(
          color: isSelected ? AppTheme.navyPrimary : border,
          width: isSelected ? 2.5 : 1.5,
        ),
        boxShadow: isSelected
            ? [
                BoxShadow(
                  color: AppTheme.navyPrimary.withAlpha(40),
                  blurRadius: 8,
                  spreadRadius: 2,
                )
              ]
            : null,
      ),
      child: Center(child: icon),
    );

    if (milestone.isInProgress && widget.animate) {
      return AnimatedBuilder(
        animation: _pulseController,
        builder: (context, child) {
          return Container(
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: AppTheme.warningAmber.withAlpha((_pulseController.value * 60).toInt()),
                  blurRadius: 10 * _pulseController.value,
                  spreadRadius: 3 * _pulseController.value,
                )
              ],
            ),
            child: node,
          );
        },
      );
    }

    return node;
  }

  Widget _buildStatusPill(MilestoneStatus status) {
    Color color;
    String label;
    switch (status) {
      case MilestoneStatus.completed:
        color = AppTheme.successGreen;
        label = 'COMPLETED';
        break;
      case MilestoneStatus.inProgress:
        color = AppTheme.warningAmber;
        label = 'IN PROGRESS';
        break;
      case MilestoneStatus.failed:
        color = AppTheme.errorCarmine;
        label = 'FAILED';
        break;
      case MilestoneStatus.pending:
        color = AppTheme.textMuted;
        label = 'PENDING';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withAlpha(25),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.bold,
          color: color,
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  Widget _buildSelectedDetailInspector() {
    if (_milestones.isEmpty || _selectedMilestoneIndex >= _milestones.length) {
      return const SizedBox.shrink();
    }

    final m = _milestones[_selectedMilestoneIndex];

    return Card(
      elevation: 0,
      color: AppTheme.navyPrimary,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 8,
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.analytics_outlined, color: AppTheme.emeraldAccent, size: 20),
                    const SizedBox(width: 8),
                    Text(
                      'STAGE ${m.index} AUDIT INSPECTOR',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.emeraldAccent,
                        letterSpacing: 0.9,
                      ),
                    ),
                  ],
                ),
                _buildStatusPill(m.status),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              m.title,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              m.description,
              style: const TextStyle(color: Colors.white70, fontSize: 13),
            ),
            const Divider(color: Colors.white24, height: 28),
            _buildInspectorRow('Responsible Microservice:', m.service),
            if (m.details != null && m.details!.isNotEmpty) ...[
              const SizedBox(height: 8),
              _buildInspectorRow('Audit Telemetry:', m.details!),
            ],
            if (m.timestamp != null) ...[
              const SizedBox(height: 8),
              _buildInspectorRow('Execution Timestamp:', _dateFormat.format(m.timestamp!)),
            ],
            if (_isStaffMode && m.microserviceSubStages.isNotEmpty) ...[
              const SizedBox(height: 14),
              const Text(
                'Correlated Saga Sub-Stages:',
                style: TextStyle(color: Colors.white60, fontSize: 11, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: m.microserviceSubStages.map((sub) {
                  return Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.white.withAlpha(25),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: Colors.white24),
                    ),
                    child: Text(
                      sub,
                      style: const TextStyle(
                        color: AppTheme.emeraldAccent,
                        fontSize: 11,
                        fontFamily: 'monospace',
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildInspectorRow(String label, String value) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 320) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(color: Colors.white60, fontSize: 12),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  fontFamily: 'monospace',
                ),
              ),
            ],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 150,
              child: Text(
                label,
                style: const TextStyle(color: Colors.white60, fontSize: 12),
              ),
            ),
            Expanded(
              child: Text(
                value,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  fontFamily: 'monospace',
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _build11StageDeepDive() {
    final stages = _rawTimeline?.stages ?? [];

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.borderSubtle),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '11-Stage Microservice Saga Decomposition',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppTheme.navyPrimary,
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              'Complete internal choreography spanning transaction-service, orchestration-service, and ledger.',
              style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
            ),
            const Divider(height: 24),
            ...stages.map((stage) {
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    CircleAvatar(
                      radius: 12,
                      backgroundColor: stage.isCompleted
                          ? AppTheme.successGreen
                          : (stage.isInProgress ? AppTheme.warningAmber : AppTheme.borderSubtle),
                      child: Icon(
                        stage.isCompleted
                            ? Icons.check
                            : (stage.isInProgress ? Icons.hourglass_top : Icons.circle),
                        size: 12,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                '${stage.stageNumber}. ${stage.title}',
                                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                              ),
                              Text(
                                stage.status,
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.bold,
                                  color: stage.isCompleted ? AppTheme.successGreen : AppTheme.textMuted,
                                ),
                              ),
                            ],
                          ),
                          if (stage.details != null && stage.details!.isNotEmpty)
                            Text(
                              stage.details!,
                              style: const TextStyle(fontSize: 11, color: AppTheme.textSecondary),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorCard(String error) {
    return Card(
      color: AppTheme.errorCarmine.withAlpha(20),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            const Icon(Icons.error_outline_rounded, color: AppTheme.errorCarmine, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                error,
                style: const TextStyle(color: AppTheme.errorCarmine, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
