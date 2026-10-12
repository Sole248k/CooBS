import 'package:flutter/material.dart';

import '../core/config/app_config.dart';
import '../core/models/models.dart';
import '../core/network/network.dart';
import '../core/router/app_router.dart';
import '../core/storage/secure_storage_service.dart';
import '../core/theme/app_theme.dart';
import '../features/accounts/demo_store.dart';
import '../features/accounts/ui.dart';
import '../features/remittance/remittance_screen.dart';
import '../features/resilience/service_unavailable_view.dart';
import '../features/tracking/tracking.dart';

/// Responsive Omnichannel Navigation Shell.
/// Adapts between a BottomNavigationBar (Mobile < 640px) and a NavigationRail (Tablet/Desktop >= 640px).
class AppShell extends StatefulWidget {
  final int initialIndex;
  final String? trackingReference;

  const AppShell({
    super.key,
    this.initialIndex = 0,
    this.trackingReference,
  });

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  late int _currentIndex;
  final ApiClient _apiClient = ApiClient();
  Future<Map<String, dynamic>>? _dashboardFuture;
  String? _selectedTrackingRef;
  MobileDashboardResponse? _latestDashboard;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
    _selectedTrackingRef = widget.trackingReference;
    _loadDashboard();
  }

  void _loadDashboard() {
    _dashboardFuture = _apiClient.get('/mobile/dashboard');
  }

  @override
  Widget build(BuildContext context) {
    // Teller/web shell stays on the light theme; dark mode is mobile-only.
    return Theme(
      data: AppTheme.lightTheme,
      child: LayoutBuilder(
      builder: (context, constraints) {
        final isWideScreen = constraints.maxWidth >= 640;

        return Scaffold(
          appBar: AppBar(
            title: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.account_balance_rounded, color: AppTheme.emeraldAccent, size: 22),
                const SizedBox(width: 8),
                const Text('CooBS Banking'),
                if (AppConfig.useFixtures) ...[
                  const SizedBox(width: 10),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppTheme.emeraldAccent.withAlpha(40),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppTheme.emeraldAccent, width: 1),
                    ),
                    child: const Text(
                      'FIXTURE MODE',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.emeraldAccent,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ),
                ],
              ],
            ),
            actions: [
              IconButton(
                tooltip: 'Refresh Dashboard',
                icon: const Icon(Icons.refresh_rounded),
                onPressed: () => setState(() => _loadDashboard()),
              ),
              IconButton(
                tooltip: 'Log out',
                icon: const Icon(Icons.logout_rounded),
                onPressed: () async {
                  try {
                    await ApiClient().post('/auth/token/revoke');
                  } catch (_) {}
                  await SecureStorageService().clearSession();
                  if (context.mounted) {
                    Navigator.pushNamedAndRemoveUntil(context, AppRouter.login, (_) => false);
                  }
                },
              ),
              const SizedBox(width: 8),
            ],
          ),
          body: isWideScreen
              ? Row(
                  children: [
                    NavigationRail(
                      selectedIndex: _currentIndex,
                      onDestinationSelected: (idx) => setState(() => _currentIndex = idx),
                      labelType: NavigationRailLabelType.all,
                      selectedIconTheme: const IconThemeData(color: AppTheme.navyPrimary),
                      selectedLabelTextStyle: const TextStyle(
                        color: AppTheme.navyPrimary,
                        fontWeight: FontWeight.w600,
                      ),
                      destinations: const [
                        NavigationRailDestination(
                          icon: Icon(Icons.dashboard_outlined),
                          selectedIcon: Icon(Icons.dashboard_rounded),
                          label: Text('Dashboard'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.send_outlined),
                          selectedIcon: Icon(Icons.send_rounded),
                          label: Text('Transfer'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.receipt_long_outlined),
                          selectedIcon: Icon(Icons.receipt_long_rounded),
                          label: Text('Timeline'),
                        ),
                      ],
                    ),
                    const VerticalDivider(width: 1, thickness: 1),
                    Expanded(child: _buildCurrentView()),
                  ],
                )
              : _buildCurrentView(),
          bottomNavigationBar: isWideScreen
              ? null
              : BottomNavigationBar(
                  currentIndex: _currentIndex,
                  onTap: (idx) => setState(() => _currentIndex = idx),
                  selectedItemColor: AppTheme.navyPrimary,
                  unselectedItemColor: AppTheme.textMuted,
                  items: const [
                    BottomNavigationBarItem(
                      icon: Icon(Icons.dashboard_outlined),
                      activeIcon: Icon(Icons.dashboard_rounded),
                      label: 'Dashboard',
                    ),
                    BottomNavigationBarItem(
                      icon: Icon(Icons.send_outlined),
                      activeIcon: Icon(Icons.send_rounded),
                      label: 'Transfer',
                    ),
                    BottomNavigationBarItem(
                      icon: Icon(Icons.receipt_long_outlined),
                      activeIcon: Icon(Icons.receipt_long_rounded),
                      label: 'Timeline',
                    ),
                  ],
                ),
        );
      },
      ),
    );
  }

  Widget _buildCurrentView() {
    switch (_currentIndex) {
      case 0:
        return _buildDashboardView();
      case 1:
        return _buildRemittanceForm();
      case 2:
        return _buildTimelineView();
      default:
        return _buildDashboardView();
    }
  }

  /// Dashboard view backed by M1 composite fixture
  Widget _buildDashboardView() {
    return FutureBuilder<Map<String, dynamic>>(
      future: _dashboardFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator(color: AppTheme.navyPrimary));
        }

        if (snapshot.hasError) {
          final error = snapshot.error;
          if (error is CircuitBreakerOpenException) {
            return ServiceUnavailableView(cooldown: error.remainingCooldown);
          }
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                error is ApiException ? error.message : 'Unable to connect to service. Please try again.',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppTheme.errorCarmine),
              ),
            ),
          );
        }

        final data = snapshot.data ?? {};
        final dashboard = MobileDashboardResponse.fromJson(data);
        _latestDashboard = dashboard;

        return RefreshIndicator(
          onRefresh: () async {
            setState(() => _loadDashboard());
            await _dashboardFuture;
          },
          child: ListView(
            padding: const EdgeInsets.all(20),
          children: [
            // Welcome Member Banner
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Welcome back,',
                      style: TextStyle(fontSize: 14, color: kMut),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      dashboard.member.displayName,
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        color: kInk,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.successGreen.withAlpha(25),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.verified_rounded, color: AppTheme.successGreen, size: 16),
                      const SizedBox(width: 4),
                      Text(
                        dashboard.member.kycStatus,
                        style: const TextStyle(
                          color: AppTheme.successGreen,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Account Balance Card
            Card(
              color: AppTheme.navyPrimary,
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          '${dashboard.primaryAccount.accountType} ACCOUNT',
                          style: TextStyle(
                            color: AppTheme.surfaceWhite.withAlpha(180),
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.0,
                          ),
                        ),
                        Text(
                          dashboard.primaryAccount.accountNumber,
                          style: TextStyle(
                            color: AppTheme.surfaceWhite.withAlpha(220),
                            fontSize: 13,
                            fontFamily: 'monospace',
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text(
                      dashboard.primaryAccount.formattedBalance,
                      style: const TextStyle(
                        color: AppTheme.surfaceWhite,
                        fontSize: 32,
                        fontWeight: FontWeight.bold,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        ElevatedButton.icon(
                          onPressed: () => setState(() => _currentIndex = 1),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppTheme.emeraldAccent,
                            foregroundColor: AppTheme.navyDark,
                            minimumSize: const Size(120, 42),
                          ),
                          icon: const Icon(Icons.send_rounded, size: 18),
                          label: const Text('Send Money'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Recent Transactions Section Header
            const Text(
              'Recent Activity',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppTheme.navyPrimary,
              ),
            ),
            const SizedBox(height: 12),
            if (dashboard.recentTransactions.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Center(
                  child: Text(
                    'No recent transactions found.',
                    style: TextStyle(color: kMut),
                  ),
                ),
              )
            else
              ...dashboard.recentTransactions.map((tx) => Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: kInBg,
                        child: Icon(Icons.arrow_upward_rounded, color: kNavyFg),
                      ),
                      title: Text(
                        tx.remarks.isNotEmpty ? tx.remarks : 'Transfer Out',
                        style: TextStyle(fontWeight: FontWeight.w600, color: kInk),
                      ),
                      subtitle: Text(
                        tx.trackingReference,
                        style: TextStyle(fontSize: 12, color: kMut),
                      ),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            tx.formattedAmount,
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                              color: AppTheme.errorCarmine,
                            ),
                          ),
                          Text(
                            tx.status,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppTheme.successGreen,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ],
                      ),
                      onTap: () {
                        setState(() {
                          _selectedTrackingRef = tx.trackingReference;
                          _currentIndex = 2;
                        });
                      },
                    ),
                  )),
          ],
        ),
      );
    },
    );
  }

  /// Remittance View backed by live M3 intake endpoint
  Widget _buildRemittanceForm() {
    return RemittanceForm(
      defaultSourceAccount: _latestDashboard?.primaryAccount.accountNumber ?? 'ACC_10000001',
      availableBalance: _latestDashboard?.primaryAccount.availableBalance,
      onSuccess: (trackingRef) {
        setState(() {
          _selectedTrackingRef = trackingRef;
          _currentIndex = 2;
        });
        _loadDashboard();
      },
    );
  }

  /// Interactive Visual Stepper backed by live M2 timeline adapter
  Widget _buildTimelineView() {
    final validTx = Demo.i.curTx.where((x) => x.ref.isNotEmpty && x.ref != 'TXN' && !x.ref.startsWith('Just now'));
    final latest = validTx.isNotEmpty ? validTx.first.ref : null;
    final ref = _selectedTrackingRef ?? widget.trackingReference ?? latest;
    return TrackingView(
      key: ValueKey(ref ?? 'default_timeline'),
      trackingReference: ref,
      apiClient: _apiClient,
      allowStaffMode: false,
    );
  }
}

