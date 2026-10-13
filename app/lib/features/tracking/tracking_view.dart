import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/models/remittance_models.dart';
import '../../core/network/api_client.dart';
import '../../core/network/api_exceptions.dart';
import '../../core/theme/app_theme.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../resilience/service_unavailable_view.dart';
import 'tracking_models.dart';

/// Interactive Visual Stepper & Saga Tracker for Transaction Timelines.
/// Visualizes the 4-stage business pipeline:
/// Ingress Received -> Risk & AML Screened -> Ledger Posted -> Dispatched.
/// Adapts responsively between horizontal and vertical layouts, supports
/// auto-polling until terminal state, failure message rendering, and role-based view switching.
class TrackingView extends StatefulWidget {
  final String? trackingReference;
  final ApiClient? apiClient;
  final TransactionTimelineResponse? initialTimeline;
  final bool animate;
  final bool autoPoll;

  /// Renders inside a parent scroll view (e.g. the post-transfer receipt): no own scrolling
  /// and no reference search bar, since the reference is fixed to the submitted transfer.
  final bool embedded;

  /// Controls whether the 11-stage staff audit mode is available.
  /// Customer transactions must never expose internal microservice telemetry.
  /// Defaults to false when embedded (e.g. remittance receipt or customer dialog),
  /// or true when standalone without explicit override (e.g. unit/widget test harnesses).
  final bool? allowStaffMode;

  const TrackingView({
    super.key,
    this.trackingReference,
    this.apiClient,
    this.initialTimeline,
    this.animate = true,
    this.autoPoll = true,
    this.embedded = false,
    this.allowStaffMode,
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
  bool _isUnavailable = false;
  Duration? _unavailableCooldown;
  TransactionTimelineResponse? _rawTimeline;
  List<TrackingMilestone> _milestones = [];
  int _selectedMilestoneIndex = 0; // 0 to 3
  bool _isStaffMode = false;
  Timer? _pollingTimer;
  bool _userSelectedMilestone = false;
  bool _isPollingInFlight = false;

  bool get _canShowStaffMode => widget.allowStaffMode ?? !widget.embedded;
  bool get _effectiveStaffMode => _canShowStaffMode && _isStaffMode;

  /// Poll fast right after submission, back off once the transfer takes a while, then stop,
  /// so a stuck saga does not hit the gateway every second for as long as the page is open.
  static const Duration _fastPollInterval = Duration(seconds: 1);
  static const Duration _slowPollInterval = Duration(seconds: 5);
  static const Duration _fastPollWindow = Duration(seconds: 30);
  static const Duration _maxPollDuration = Duration(minutes: 2);

  /// Sum of scheduled poll intervals since the last explicit load (deterministic under fake time).
  Duration _pollElapsed = Duration.zero;
  bool _pollingPaused = false;

  bool get _isAutoPolling => _pollingTimer?.isActive == true || _isPollingInFlight;

  static final DateFormat _dateFormat = DateFormat('MMM dd, yyyy • HH:mm:ss');

  bool _isReplayingDlq = false;

  @override
  void initState() {
    super.initState();
    _apiClient = widget.apiClient ?? ApiClient();
    final validTx = Demo.i.curTx.where((x) => x.ref.isNotEmpty && x.ref != 'TXN' && !x.ref.startsWith('Just now'));
    final defaultRef = validTx.isNotEmpty ? validTx.first.ref : 'TX-20261003-8F3A21C0';
    final initialRef = widget.trackingReference ?? defaultRef;
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

    // Sync terminal status to Demo store
    if (timeline.isTerminalSuccess) {
      Demo.i.updateTxStatus(timeline.trackingReference, 'POSTED');
    } else if (timeline.isTerminalFailure) {
      Demo.i.updateTxStatus(timeline.trackingReference, 'FAILED', failureCode: timeline.failureMessage);
    }

    if (!_userSelectedMilestone || !isAutoPoll) {
      final dlqIdx = _milestones.indexWhere((m) => m.isDlq);
      final failedIdx = _milestones.indexWhere((m) => m.isFailed);
      final activeIdx = _milestones.indexWhere((m) => m.isInProgress);

      if (dlqIdx != -1) {
        _selectedMilestoneIndex = dlqIdx;
      } else if (failedIdx != -1) {
        _selectedMilestoneIndex = failedIdx;
      } else if (activeIdx != -1) {
        _selectedMilestoneIndex = activeIdx;
      } else {
        _selectedMilestoneIndex = _milestones.isNotEmpty ? _milestones.length - 1 : 0;
      }
    }

    if (_milestones.isNotEmpty && _selectedMilestoneIndex >= _milestones.length) {
      _selectedMilestoneIndex = _milestones.length - 1;
    } else if (_selectedMilestoneIndex < 0) {
      _selectedMilestoneIndex = 0;
    }

    _managePolling(timeline);
  }

  void _managePolling(TransactionTimelineResponse timeline) {
    _pollingTimer?.cancel();
    if (!widget.autoPoll) return;
    if (timeline.isTerminalSuccess || timeline.isTerminalFailure) return;
    if (_pollElapsed >= _maxPollDuration) {
      _pollingPaused = true;
      return;
    }
    final interval = _pollElapsed < _fastPollWindow ? _fastPollInterval : _slowPollInterval;
    _pollingTimer = Timer(interval, () {
      if (!mounted) return;
      _pollElapsed += interval;
      _silentPollTimeline(_searchController.text.trim());
    });
  }

  Future<void> _silentPollTimeline(String reference) async {
    if (_isPollingInFlight) return;
    _isPollingInFlight = true;
    try {
      final queryParams = _effectiveStaffMode ? {'view': 'staff'} : <String, String>{};
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
      // Background poll silently preserves existing UI state; keep the schedule going
      if (mounted && _rawTimeline != null) setState(() => _managePolling(_rawTimeline!));
    } finally {
      _isPollingInFlight = false;
    }
  }

  Future<void> _loadTimeline(String reference) async {
    _pollingTimer?.cancel();
    _pollElapsed = Duration.zero;
    _pollingPaused = false;
    _userSelectedMilestone = false;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final queryParams = _effectiveStaffMode ? {'view': 'staff'} : <String, String>{};
      final safeRef = Uri.encodeComponent(reference);
      final data = await _apiClient.get(
        '/mobile/transactions/$safeRef/timeline',
        queryParams: queryParams,
      );
      final timeline = TransactionTimelineResponse.fromJson(data);

      if (mounted) {
        setState(() {
          _isUnavailable = false;
          _unavailableCooldown = null;
          _applyTimeline(timeline);
          _isLoading = false;
        });
      }
    } catch (e) {
      // Gracefully fallback to fixture timeline if live backend does not have this specific reference
      try {
        final queryParams = _effectiveStaffMode ? {'view': 'staff'} : <String, String>{};
        final data = await _apiClient.get(
          '/mobile/transactions/${Uri.encodeComponent(reference)}/timeline',
          queryParams: queryParams,
          useFixtures: true,
        );
        final timeline = TransactionTimelineResponse.fromJson(data);
        if (mounted) {
          setState(() {
            _isUnavailable = false;
            _unavailableCooldown = null;
            _applyTimeline(timeline);
            _isLoading = false;
          });
          return;
        }
      } catch (_) {}

      if (mounted) {
        final isCircuitOpen = e is CircuitBreakerOpenException;
        final is503 = (e is ApiException && e.statusCode == 503) || e is ConnectionException;
        final cooldown = e is CircuitBreakerOpenException ? e.remainingCooldown : null;
        setState(() {
          _isUnavailable = isCircuitOpen || is503;
          _unavailableCooldown = cooldown;
          _errorMessage = e is ApiException ? e.message : 'Unable to load timeline. Please try again.';
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _replayDlqRecord(String topic) async {
    if (_isReplayingDlq) return;
    setState(() => _isReplayingDlq = true);
    try {
      try {
        await _apiClient.post(
          '/api/v1/notifications/admin/dlq/replay',
          body: {'topic': topic},
        );
      } catch (_) {
        // Zero-backend / test fixture fallback
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Triggered DLQ replay for $topic'),
            backgroundColor: AppTheme.navyPrimary,
            duration: const Duration(seconds: 3),
          ),
        );
        await _loadTimeline(_searchController.text.trim());
      }
    } finally {
      if (mounted) setState(() => _isReplayingDlq = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark || kDark;

    return LayoutBuilder(
      builder: (context, constraints) {
        final isWide = constraints.maxWidth >= 720 &&
            !widget.embedded &&
            MediaQuery.of(context).orientation == Orientation.landscape;

        return Material(
          color: Colors.transparent,
          child: CustomScrollView(
            shrinkWrap: widget.embedded,
            physics: widget.embedded ? const NeverScrollableScrollPhysics() : null,
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: widget.embedded
                      ? EdgeInsets.zero
                      : const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (!widget.embedded) ...[
                        _buildSearchAndHeader(isDark: isDark),
                        const SizedBox(height: 16),
                      ],
                      if (_isLoading)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 40),
                          child: Center(
                            child: CircularProgressIndicator(
                              color: isDark ? const Color(0xFF00B4D8) : AppTheme.brandCyan,
                            ),
                          ),
                        )
                      else if (_isUnavailable)
                        ServiceUnavailableView(
                          isEmbedded: true,
                          cooldown: _unavailableCooldown,
                          onRetry: () {
                            setState(() {
                              _isUnavailable = false;
                              _errorMessage = null;
                            });
                            _loadTimeline(_searchController.text.trim());
                          },
                        )
                      else if (_errorMessage != null)
                        _buildErrorCard(_errorMessage!, isDark: isDark)
                      else ...[
                        if (_rawTimeline?.failureMessage != null) ...[
                          _buildFailureBanner(_rawTimeline!.failureMessage!, isDark: isDark),
                          const SizedBox(height: 16),
                        ],
                        _buildHeaderBadgesAndModeToggle(isDark: isDark),
                        const SizedBox(height: 14),
                        if (_pollingPaused) ...[
                          _buildPollingPausedNotice(isDark: isDark),
                          const SizedBox(height: 14),
                        ],
                        if (_effectiveStaffMode && _rawTimeline != null && _rawTimeline!.stages.length >= 11)
                          _build11StageDeepDive(isDark: isDark)
                        else ...[
                          _buildStepperCard(isWide: isWide, isDark: isDark),
                          if (isWide) ...[
                            const SizedBox(height: 16),
                            _buildSelectedDetailInspector(isDark: isDark),
                          ],
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

  Widget _buildSearchAndHeader({required bool isDark}) {
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: TextField(
            controller: _searchController,
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: textPrimary),
            decoration: InputDecoration(
              labelText: 'Transaction Reference',
              labelStyle: TextStyle(color: textSecondary, fontSize: 13),
              hintText: 'e.g. TX-20261003-8F3A21C0',
              hintStyle: TextStyle(color: isDark ? const Color(0xFF64748B) : const Color(0xFF94A3B8)),
              filled: true,
              fillColor: isDark ? const Color(0xFF0C141F) : Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide(color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0)),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide(color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0)),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide(color: brandCyan, width: 1.5),
              ),
              prefixIcon: Icon(Icons.search_rounded, size: 20, color: brandCyan),
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              suffixIcon: IconButton(
                icon: Icon(Icons.arrow_forward_rounded, size: 20, color: brandCyan),
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
          style: IconButton.styleFrom(
            backgroundColor: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
            foregroundColor: isDark ? const Color(0xFF38BDF8) : brandCyan,
          ),
          onPressed: () => _loadTimeline(_searchController.text.trim()),
        ),
      ],
    );
  }

  Widget _buildPollingPausedNotice({required bool isDark}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF78350F).withValues(alpha: 0.3) : const Color(0xFFFFFBEB),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? const Color(0xFFD97706).withValues(alpha: 0.5) : const Color(0xFFFDE68A),
        ),
      ),
      child: Row(
        children: [
          const Icon(Icons.pause_circle_outline_rounded, color: Color(0xFFF59E0B), size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Still processing. Automatic updates paused.',
              style: TextStyle(
                fontSize: 13,
                color: isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A),
              ),
            ),
          ),
          TextButton(
            onPressed: () => _loadTimeline(_searchController.text.trim()),
            child: const Text('Check again'),
          ),
        ],
      ),
    );
  }

  Widget _buildFailureBanner(String message, {required bool isDark}) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.3) : const Color(0xFFFEF2F2),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? const Color(0xFFDC2626).withValues(alpha: 0.5) : const Color(0xFFFECACA),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.cancel_rounded, color: Color(0xFFEF4444), size: 22),
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
                    color: Color(0xFFEF4444),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  message,
                  style: TextStyle(
                    fontSize: 13,
                    color: isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildProfessionalStatusPill(String status, {required bool isDark}) {
    final s = status.toUpperCase();
    final isSuccess = s == 'COMPLETED' || s == 'SUCCESS';
    final isFailed = s == 'FAILED' || s == 'CANCELLED';

    Color bg;
    Color border;
    Color fg;
    IconData icon;

    if (isSuccess) {
      bg = isDark ? const Color(0xFF064E3B).withValues(alpha: 0.45) : const Color(0xFFECFDF5);
      border = isDark ? const Color(0xFF059669).withValues(alpha: 0.55) : const Color(0xFFA7F3D0);
      fg = isDark ? const Color(0xFF34D399) : const Color(0xFF047857);
      icon = Icons.check_circle_rounded;
    } else if (isFailed) {
      bg = isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.4) : const Color(0xFFFEF2F2);
      border = isDark ? const Color(0xFFDC2626).withValues(alpha: 0.5) : const Color(0xFFFECACA);
      fg = isDark ? const Color(0xFFF87171) : const Color(0xFFB91C1C);
      icon = Icons.cancel_rounded;
    } else {
      bg = isDark ? const Color(0xFF78350F).withValues(alpha: 0.4) : const Color(0xFFFFFBEB);
      border = isDark ? const Color(0xFFD97706).withValues(alpha: 0.5) : const Color(0xFFFDE68A);
      fg = isDark ? const Color(0xFFFBBF24) : const Color(0xFFB45309);
      icon = Icons.sync_rounded;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4.5),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: border, width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: fg),
          const SizedBox(width: 5),
          Text(
            status,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: fg,
              letterSpacing: 0.6,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeaderBadgesAndModeToggle({required bool isDark}) {
    final status = _rawTimeline?.overallStatus ?? 'PROCESSING';
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);

    if (widget.embedded) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
            'Transfer status',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: textPrimary,
              letterSpacing: -0.2,
            ),
          ),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildProfessionalStatusPill(status, isDark: isDark),
              if (_isAutoPolling) ...[
                const SizedBox(width: 8),
                SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: brandCyan,
                  ),
                ),
              ],
            ],
          ),
        ],
      );
    }

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
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.tag_rounded,
                    size: 13,
                    color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    'REF: ${_rawTimeline?.trackingReference ?? _searchController.text}',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: isDark ? const Color(0xFF38BDF8) : brandCyan,
                      fontFamily: 'monospace',
                    ),
                  ),
                ],
              ),
            ),
            _buildProfessionalStatusPill(status, isDark: isDark),
            if (_isAutoPolling)
              SizedBox(
                width: 14,
                height: 14,
                child: CircularProgressIndicator(strokeWidth: 2, color: brandCyan),
              ),
          ],
        ),
        if (_canShowStaffMode)
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
              backgroundColor: WidgetStateProperty.resolveWith((states) {
                if (states.contains(WidgetState.selected)) {
                  return isDark ? const Color(0xFF1E3A5F) : const Color(0xFFE0F4F8);
                }
                return isDark ? const Color(0xFF0C141F) : Colors.white;
              }),
              foregroundColor: WidgetStateProperty.resolveWith((states) {
                if (states.contains(WidgetState.selected)) {
                  return isDark ? const Color(0xFF38BDF8) : brandCyan;
                }
                return isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);
              }),
              side: WidgetStateProperty.all(
                BorderSide(
                  color: isDark ? const Color(0xFF1E293B) : const Color(0xFFCBD5E1),
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildTimelineProgressIndicator({required bool isDark}) {
    final completedCount = _milestones.where((m) => m.isCompleted).length;
    final total = _milestones.length;
    final allDone = total > 0 && completedCount == total;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: allDone
            ? (isDark ? const Color(0xFF064E3B).withValues(alpha: 0.35) : const Color(0xFFECFDF5))
            : (isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9)),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: allDone
              ? (isDark ? const Color(0xFF059669).withValues(alpha: 0.45) : const Color(0xFFA7F3D0))
              : (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
        ),
      ),
      child: Text(
        '$completedCount of $total stages',
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: allDone
              ? (isDark ? const Color(0xFF34D399) : const Color(0xFF047857))
              : (isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B)),
        ),
      ),
    );
  }

  Widget _buildStepperCard({required bool isWide, required bool isDark}) {
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    return Card(
      elevation: 0,
      color: isDark ? const Color(0xFF0C141F) : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Expanded(
                  child: Text(
                    'Transaction Milestones',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: textPrimary,
                    ),
                  ),
                ),
                _buildTimelineProgressIndicator(isDark: isDark),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              'Tap any milestone checkpoint to inspect service telemetry and execution records.',
              style: TextStyle(
                fontSize: 12,
                color: textSecondary,
              ),
            ),
            const SizedBox(height: 20),
            isWide ? _buildHorizontalStepper(isDark: isDark) : _buildVerticalStepper(isDark: isDark),
          ],
        ),
      ),
    );
  }

  Widget _buildHorizontalStepper({required bool isDark}) {
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);

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
                color: isCompleted
                    ? brandCyan
                    : (isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0)),
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
              _buildMilestoneNode(milestone, isSelected, isDark: isDark),
              const SizedBox(height: 10),
              SizedBox(
                width: 100,
                child: Text(
                  milestone.title,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.w600,
                    color: isSelected
                        ? (isDark ? const Color(0xFF38BDF8) : brandCyan)
                        : textPrimary,
                  ),
                ),
              ),
            ],
          ),
        );
      }),
    );
  }

  Widget _buildVerticalStepper({required bool isDark}) {
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    return Column(
      children: List.generate(_milestones.length, (idx) {
        final milestone = _milestones[idx];
        final isSelected = _selectedMilestoneIndex == idx;
        final isLast = idx == _milestones.length - 1;

        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Left track: node + vertical connecting line
            Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GestureDetector(
                  onTap: () => setState(() {
                    _selectedMilestoneIndex = idx;
                    _userSelectedMilestone = true;
                  }),
                  child: _buildMilestoneNode(milestone, isSelected, isDark: isDark),
                ),
                if (!isLast)
                  Container(
                    width: 2.5,
                    height: isSelected ? 80 : 44,
                    margin: const EdgeInsets.symmetric(vertical: 4),
                    decoration: BoxDecoration(
                      color: milestone.isCompleted
                          ? brandCyan
                          : (isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0)),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 14),
            // Right content
            Expanded(
              child: Padding(
                padding: EdgeInsets.only(bottom: isLast ? 0 : 14),
                child: InkWell(
                  borderRadius: BorderRadius.circular(12),
                  onTap: () => setState(() {
                    _selectedMilestoneIndex = idx;
                    _userSelectedMilestone = true;
                  }),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? (isDark ? const Color(0xFF111E2E) : const Color(0xFFF8FAFC))
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isSelected
                            ? (isDark ? const Color(0xFF23354E) : const Color(0xFFCBD5E1))
                            : (isDark ? const Color(0xFF1E293B).withValues(alpha: 0.4) : const Color(0xFFF1F5F9)),
                        width: 1,
                      ),
                    ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            crossAxisAlignment: CrossAxisAlignment.center,
                            children: [
                              Expanded(
                                child: Text(
                                  milestone.title,
                                  style: TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.bold,
                                    color: isSelected
                                        ? (isDark ? const Color(0xFF38BDF8) : brandCyan)
                                        : textPrimary,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              _buildStatusPill(
                                milestone.status,
                                isDlq: milestone.isDlq,
                                retryAttempts: milestone.retryAttempts,
                                isDark: isDark,
                              ),
                            ],
                          ),
                          const SizedBox(height: 5),
                          Text(
                            milestone.description,
                            style: TextStyle(
                              fontSize: 12,
                              color: textSecondary,
                              height: 1.35,
                            ),
                          ),
                          if (milestone.timestamp != null) ...[
                            const SizedBox(height: 6),
                            Row(
                              children: [
                                Icon(
                                  Icons.schedule_rounded,
                                  size: 13,
                                  color: isDark ? const Color(0xFF64748B) : const Color(0xFF94A3B8),
                                ),
                                const SizedBox(width: 4),
                                Expanded(
                                  child: Text(
                                    _dateFormat.format(milestone.timestamp!),
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: isDark ? const Color(0xFF64748B) : const Color(0xFF94A3B8),
                                      fontWeight: FontWeight.w500,
                                    ),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ),
                          ],
                          if (milestone.isDlq && milestone.dlqTopic != null) ...[
                            const SizedBox(height: 6),
                            Text(
                              'DLQ Topic: ${milestone.dlqTopic}',
                              style: const TextStyle(
                                fontSize: 11,
                                color: Color(0xFFDC2626),
                                fontWeight: FontWeight.w600,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ],
                          if (isSelected) ...[
                            const SizedBox(height: 10),
                            _buildInlineAuditDetails(milestone, isDark: isDark),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          );
        }),
      );
  }

  Widget _buildMilestoneNode(TrackingMilestone milestone, bool isSelected, {required bool isDark}) {
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);
    Color bg = isDark ? const Color(0xFF0F172A) : const Color(0xFFF1F5F9);
    Color border = isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1);
    Widget icon = Text(
      '${milestone.index}',
      style: TextStyle(
        color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
        fontSize: 13,
        fontWeight: FontWeight.bold,
      ),
    );

    if (milestone.isCompleted) {
      bg = brandCyan;
      border = isDark ? const Color(0xFF38BDF8) : const Color(0xFF007A99);
      icon = const Icon(Icons.check_rounded, color: Colors.white, size: 20);
    } else if (milestone.isInProgress) {
      bg = isDark ? const Color(0xFF78350F).withValues(alpha: 0.4) : const Color(0xFFFFFBEB);
      border = isDark ? const Color(0xFFFBBF24) : const Color(0xFFD97706);
      icon = SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(
          strokeWidth: 2.2,
          color: isDark ? const Color(0xFFFBBF24) : const Color(0xFFD97706),
        ),
      );
    } else if (milestone.isFailed) {
      if (milestone.isDlq) {
        bg = isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.45) : const Color(0xFFFEE2E2);
        border = isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626);
        icon = Icon(
          Icons.sync_problem_rounded,
          color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
          size: 20,
        );
      } else {
        bg = isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.35) : const Color(0xFFFEF2F2);
        border = isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626);
        icon = Icon(
          Icons.close_rounded,
          color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
          size: 20,
        );
      }
    }

    Widget node = AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        color: bg,
        shape: BoxShape.circle,
        border: Border.all(
          color: isSelected ? (isDark ? const Color(0xFF38BDF8) : brandCyan) : border,
          width: isSelected ? 2.5 : 1.5,
        ),
        boxShadow: isSelected
            ? [
                BoxShadow(
                  color: (isDark ? const Color(0xFF00B4D8) : brandCyan).withValues(alpha: 0.35),
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
                  color: const Color(0xFFF59E0B).withValues(
                    alpha: (_pulseController.value * 0.4).clamp(0.0, 1.0),
                  ),
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

  Widget _buildStatusPill(
    MilestoneStatus status, {
    bool isDlq = false,
    int? retryAttempts,
    required bool isDark,
  }) {
    if (isDlq) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2.5),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.35) : const Color(0xFFFEF2F2),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isDark ? const Color(0xFFDC2626).withValues(alpha: 0.5) : const Color(0xFFFCA5A5),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.sync_problem_rounded,
              size: 11,
              color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
            ),
            const SizedBox(width: 4),
            Text(
              retryAttempts != null ? 'DLQ RETRY ($retryAttempts/3)' : 'DLQ ACTIVE',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.bold,
                color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
      );
    }

    Color color;
    Color bg;
    Color border;
    String label;
    switch (status) {
      case MilestoneStatus.completed:
        color = isDark ? const Color(0xFF34D399) : const Color(0xFF047857);
        bg = isDark ? const Color(0xFF064E3B).withValues(alpha: 0.35) : const Color(0xFFECFDF5);
        border = isDark ? const Color(0xFF059669).withValues(alpha: 0.45) : const Color(0xFFA7F3D0);
        label = 'COMPLETED';
        break;
      case MilestoneStatus.inProgress:
        color = isDark ? const Color(0xFFFBBF24) : const Color(0xFFB45309);
        bg = isDark ? const Color(0xFF78350F).withValues(alpha: 0.35) : const Color(0xFFFFFBEB);
        border = isDark ? const Color(0xFFD97706).withValues(alpha: 0.45) : const Color(0xFFFDE68A);
        label = 'IN PROGRESS';
        break;
      case MilestoneStatus.failed:
        color = isDark ? const Color(0xFFF87171) : const Color(0xFFB91C1C);
        bg = isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.35) : const Color(0xFFFEF2F2);
        border = isDark ? const Color(0xFFDC2626).withValues(alpha: 0.45) : const Color(0xFFFECACA);
        label = 'FAILED';
        break;
      case MilestoneStatus.pending:
        color = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);
        bg = isDark ? const Color(0xFF1E293B).withValues(alpha: 0.5) : const Color(0xFFF1F5F9);
        border = isDark ? const Color(0xFF334155).withValues(alpha: 0.5) : const Color(0xFFE2E8F0);
        label = 'PENDING';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2.5),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: border, width: 1),
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

  Widget _buildInlineAuditDetails(TrackingMilestone m, {required bool isDark}) {
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0C141F) : Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: isDark ? const Color(0xFF1E2F46) : const Color(0xFFE2E8F0),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.analytics_outlined,
                size: 15,
                color: isDark ? const Color(0xFF38BDF8) : brandCyan,
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  _canShowStaffMode
                      ? 'STAGE ${m.index} AUDIT INSPECTOR'
                      : 'MILESTONE ${m.index} STATUS',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: isDark ? const Color(0xFF38BDF8) : brandCyan,
                    letterSpacing: 0.8,
                  ),
                ),
              ),
            ],
          ),
          if (_canShowStaffMode) ...[
            const SizedBox(height: 8),
            _buildInspectorRow('Responsible Microservice:', m.service, isDark: isDark, stacked: true),
            if (m.details != null && m.details!.isNotEmpty) ...[
              const SizedBox(height: 6),
              _buildInspectorRow('Audit Telemetry:', m.details!, isDark: isDark, stacked: true),
            ],
          ] else ...[
            if (m.details != null && m.details!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                m.details!,
                style: TextStyle(fontSize: 12, color: textSecondary),
              ),
            ],
          ],
          if (m.timestamp != null) ...[
            const SizedBox(height: 6),
            _buildInspectorRow('Execution Timestamp:', _dateFormat.format(m.timestamp!), isDark: isDark, stacked: true),
          ],
          if (m.isDlq) ...[
            const SizedBox(height: 10),
            _buildDlqActionBanner(m, isDark: isDark),
          ],
          if (_isStaffMode && m.microserviceSubStages.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(
              'Correlated Saga Sub-Stages:',
              style: TextStyle(
                color: isDark ? const Color(0xFF64748B) : const Color(0xFF94A3B8),
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: m.microserviceSubStages.map((sub) {
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(
                      color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
                    ),
                  ),
                  child: Text(
                    sub,
                    style: TextStyle(
                      color: brandCyan,
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
    );
  }

  Widget _buildSelectedDetailInspector({required bool isDark}) {
    if (_milestones.isEmpty || _selectedMilestoneIndex >= _milestones.length) {
      return const SizedBox.shrink();
    }

    final m = _milestones[_selectedMilestoneIndex];
    final brandCyan = isDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    return Card(
      elevation: 0,
      color: isDark ? const Color(0xFF0C141F) : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
        ),
      ),
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
                    Icon(
                      Icons.analytics_outlined,
                      color: isDark ? const Color(0xFF38BDF8) : brandCyan,
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      _canShowStaffMode
                          ? 'STAGE ${m.index} AUDIT INSPECTOR'
                          : 'MILESTONE ${m.index} STATUS',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: isDark ? const Color(0xFF38BDF8) : brandCyan,
                        letterSpacing: 0.9,
                      ),
                    ),
                  ],
                ),
                _buildStatusPill(
                  m.status,
                  isDlq: m.isDlq,
                  retryAttempts: m.retryAttempts,
                  isDark: isDark,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              m.title,
              style: TextStyle(
                color: textPrimary,
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              m.description,
              style: TextStyle(color: textSecondary, fontSize: 13),
            ),
            Divider(
              color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
              height: 28,
            ),
            if (_canShowStaffMode) ...[
              _buildInspectorRow('Responsible Microservice:', m.service, isDark: isDark),
              if (m.details != null && m.details!.isNotEmpty) ...[
                const SizedBox(height: 8),
                _buildInspectorRow('Audit Telemetry:', m.details!, isDark: isDark),
              ],
            ],
            if (m.timestamp != null) ...[
              const SizedBox(height: 8),
              _buildInspectorRow('Execution Timestamp:', _dateFormat.format(m.timestamp!), isDark: isDark),
            ],
            if (m.isDlq) ...[
              const SizedBox(height: 16),
              _buildDlqActionBanner(m, isDark: isDark),
            ],
            if (_isStaffMode && m.microserviceSubStages.isNotEmpty) ...[
              const SizedBox(height: 14),
              Text(
                'Correlated Saga Sub-Stages:',
                style: TextStyle(color: textSecondary, fontSize: 11, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: m.microserviceSubStages.map((sub) {
                  return Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(
                        color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
                      ),
                    ),
                    child: Text(
                      sub,
                      style: TextStyle(
                        color: brandCyan,
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

  Widget _buildInspectorRow(String label, String value, {required bool isDark, bool stacked = false}) {
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    if (stacked) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: TextStyle(color: textSecondary, fontSize: 11),
            ),
            const SizedBox(height: 2),
            Text(
              value,
              style: TextStyle(
                color: textPrimary,
                fontSize: 12,
                fontWeight: FontWeight.w600,
                fontFamily: 'monospace',
              ),
            ),
          ],
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2.5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 135,
            child: Text(
              label,
              style: TextStyle(color: textSecondary, fontSize: 11),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                color: textPrimary,
                fontSize: 12,
                fontWeight: FontWeight.w600,
                fontFamily: 'monospace',
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDlqActionBanner(TrackingMilestone m, {required bool isDark}) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark
            ? const Color(0xFF7F1D1D).withValues(alpha: 0.35)
            : const Color(0xFFFEF2F2),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: isDark
              ? const Color(0xFFDC2626).withValues(alpha: 0.6)
              : const Color(0xFFF87171),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 6,
            runSpacing: 4,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: const [
                  Icon(Icons.sync_problem_rounded, color: Color(0xFFEF4444), size: 16),
                  SizedBox(width: 6),
                  Text(
                    'DEAD-LETTER QUEUE (DLQ) ACTIVE',
                    style: TextStyle(
                      color: Color(0xFFEF4444),
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 0.5,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFF991B1B),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  'Attempt ${m.retryAttempts ?? 3}/3',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'Kafka message routed to ${m.dlqTopic ?? "notification.events.DLT"}. Exponential backoff retries exhausted.',
            style: TextStyle(
              color: isDark ? const Color(0xFFFCA5A5) : const Color(0xFF7F1D1D),
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 10),
          ElevatedButton.icon(
            onPressed: _isReplayingDlq
                ? null
                : () => _replayDlqRecord(m.dlqTopic ?? 'notification.events.DLT'),
            icon: _isReplayingDlq
                ? const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Icon(Icons.replay_rounded, size: 14),
            label: Text(
              _isReplayingDlq ? 'Replaying...' : 'Replay DLQ Record',
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFDC2626),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            ),
          ),
        ],
      ),
    );
  }

  Widget _build11StageDeepDive({required bool isDark}) {
    final stages = _rawTimeline?.stages ?? [];
    final textPrimary = isDark ? const Color(0xFFF1F5F9) : const Color(0xFF0F172A);
    final textSecondary = isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);

    return Card(
      elevation: 0,
      color: isDark ? const Color(0xFF0C141F) : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '11-Stage Microservice Saga Decomposition',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Complete internal choreography spanning transaction-service, orchestration-service, and ledger.',
              style: TextStyle(
                fontSize: 12,
                color: textSecondary,
              ),
            ),
            Divider(
              height: 24,
              color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
            ),
            if (stages.any((s) => s.isDlq)) ...[
              Builder(
                builder: (context) {
                  final dlqStage = stages.firstWhere((s) => s.isDlq);
                  return Container(
                    margin: const EdgeInsets.only(bottom: 16),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.3) : const Color(0xFFFEF2F2),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: isDark ? const Color(0xFFDC2626).withValues(alpha: 0.6) : const Color(0xFFF87171),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.sync_problem_rounded, color: Color(0xFFDC2626), size: 18),
                            const SizedBox(width: 8),
                            const Text(
                              'DEAD-LETTER QUEUE (DLQ) DETECTED IN SAGA',
                              style: TextStyle(
                                color: Color(0xFFDC2626),
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 0.5,
                              ),
                            ),
                            const Spacer(),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: const Color(0xFFDC2626),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                'Stage ${dlqStage.stageNumber}',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Target DLT: ${dlqStage.dlqTopic ?? "notification.events.DLT"} • Retries: ${dlqStage.retryAttempts ?? 3}/3 exhausted',
                          style: TextStyle(
                            color: isDark ? const Color(0xFFFCA5A5) : const Color(0xFF7F1D1D),
                            fontSize: 12,
                          ),
                        ),
                        const SizedBox(height: 10),
                        ElevatedButton.icon(
                          onPressed: _isReplayingDlq
                              ? null
                              : () => _replayDlqRecord(dlqStage.dlqTopic ?? 'notification.events.DLT'),
                          icon: _isReplayingDlq
                              ? const SizedBox(
                                  width: 14,
                                  height: 14,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                )
                              : const Icon(Icons.replay_rounded, size: 14),
                          label: Text(
                            _isReplayingDlq ? 'Replaying...' : 'Replay DLQ Record',
                            style: const TextStyle(fontSize: 12),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFDC2626),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ],
            ...stages.map((stage) {
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    CircleAvatar(
                      radius: 12,
                      backgroundColor: stage.isDlq
                          ? const Color(0xFFEF4444)
                          : (stage.isCompleted
                              ? (isDark ? const Color(0xFF059669) : AppTheme.successGreen)
                              : (stage.isInProgress ? const Color(0xFFD97706) : (isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0)))),
                      child: Icon(
                        stage.isDlq
                            ? Icons.sync_problem
                            : (stage.isCompleted
                                ? Icons.check
                                : (stage.isInProgress ? Icons.hourglass_top : Icons.circle)),
                        size: 12,
                        color: stage.isCompleted || stage.isDlq || stage.isInProgress
                            ? Colors.white
                            : (isDark ? const Color(0xFF64748B) : const Color(0xFF94A3B8)),
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
                                style: TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.bold,
                                  color: textPrimary,
                                ),
                              ),
                              Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  if (stage.isDlq) ...[
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      margin: const EdgeInsets.only(right: 6),
                                      decoration: BoxDecoration(
                                        color: isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.4) : const Color(0xFFFEE2E2),
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: Text(
                                        'DLQ (${stage.retryAttempts ?? 3}/3)',
                                        style: TextStyle(
                                          fontSize: 9,
                                          fontWeight: FontWeight.bold,
                                          color: isDark ? const Color(0xFFF87171) : const Color(0xFFDC2626),
                                        ),
                                      ),
                                    ),
                                  ],
                                  Text(
                                    stage.isDlq ? 'DLQ ROUTED' : stage.status,
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                      color: stage.isDlq
                                          ? const Color(0xFFDC2626)
                                          : (stage.isCompleted
                                              ? (isDark ? const Color(0xFF34D399) : AppTheme.successGreen)
                                              : textSecondary),
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          if (stage.details != null && stage.details!.isNotEmpty)
                            Text(
                              stage.details!,
                              style: TextStyle(
                                fontSize: 11,
                                color: textSecondary,
                              ),
                            ),
                          if (stage.isDlq && stage.dlqTopic != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                'DLQ Topic: ${stage.dlqTopic}',
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  fontFamily: 'monospace',
                                  color: Color(0xFFDC2626),
                                ),
                              ),
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

  Widget _buildErrorCard(String error, {required bool isDark}) {
    return Card(
      elevation: 0,
      color: isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.25) : const Color(0xFFFEF2F2),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: isDark ? const Color(0xFFDC2626).withValues(alpha: 0.45) : const Color(0xFFFECACA),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            const Icon(Icons.error_outline_rounded, color: Color(0xFFEF4444), size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                error,
                style: TextStyle(
                  color: isDark ? const Color(0xFFFCA5A5) : const Color(0xFFDC2626),
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            if (widget.embedded)
              IconButton(
                icon: const Icon(Icons.refresh_rounded, color: Color(0xFFEF4444)),
                tooltip: 'Retry',
                onPressed: () => _loadTimeline(_searchController.text.trim()),
              ),
          ],
        ),
      ),
    );
  }
}
