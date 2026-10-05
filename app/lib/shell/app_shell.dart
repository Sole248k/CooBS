import 'package:flutter/material.dart';

import '../core/config/app_config.dart';
import '../core/models/models.dart';
import '../core/network/api_client.dart';
import '../core/theme/app_theme.dart';

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

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
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
    );
  }

  Widget _buildCurrentView() {
    switch (_currentIndex) {
      case 0:
        return _buildDashboardView();
      case 1:
        return _buildRemittancePlaceholder();
      case 2:
        return _buildTimelineView();
      default:
        return _buildDashboardView();
    }
  }

  /// Dashboard view backed by M1 composite fixture
  Widget _buildDashboardView() {
    return FutureBuilder<Map<String, dynamic>>(
      future: _apiClient.get('/mobile/dashboard'),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator(color: AppTheme.navyPrimary));
        }

        if (snapshot.hasError) {
          return Center(child: Text('Error: ${snapshot.error}'));
        }

        final data = snapshot.data ?? {};
        final dashboard = MobileDashboardResponse.fromJson(data);

        return ListView(
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
                      style: TextStyle(fontSize: 14, color: AppTheme.textSecondary),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      dashboard.member.displayName,
                      style: const TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.navyPrimary,
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
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(
                  child: Text(
                    'No recent transactions found.',
                    style: TextStyle(color: AppTheme.textMuted),
                  ),
                ),
              )
            else
              ...dashboard.recentTransactions.map((tx) => Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppTheme.navyPrimary.withAlpha(20),
                        child: const Icon(Icons.arrow_upward_rounded, color: AppTheme.navyPrimary),
                      ),
                      title: Text(
                        tx.remarks.isNotEmpty ? tx.remarks : 'Transfer Out',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text(
                        tx.trackingReference,
                        style: const TextStyle(fontSize: 12, color: AppTheme.textMuted),
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
                        setState(() => _currentIndex = 2);
                      },
                    ),
                  )),
          ],
        );
      },
    );
  }

  /// Remittance View Placeholder
  Widget _buildRemittancePlaceholder() {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.send_rounded, size: 48, color: AppTheme.navyPrimary),
                const SizedBox(height: 16),
                const Text(
                  'Send Remittance',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Feature vertical unblocked for Gabo (Dev 7). Form models and API client ready.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppTheme.textSecondary),
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: () => setState(() => _currentIndex = 2),
                  child: const Text('View Sample Timeline'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 3-Stage Customer Timeline View backed by T3 fixture
  Widget _buildTimelineView() {
    return FutureBuilder<Map<String, dynamic>>(
      future: _apiClient.get('/mobile/transactions/TX-20261003-8F3A21C0/timeline'),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator(color: AppTheme.navyPrimary));
        }

        final data = snapshot.data ?? {};
        final timeline = TransactionTimelineResponse.fromJson(data);

        return ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'TRACKING: ${timeline.trackingReference}',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 0.8,
                            color: AppTheme.textSecondary,
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTheme.infoBlue.withAlpha(25),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            timeline.overallStatus,
                            style: const TextStyle(
                              color: AppTheme.infoBlue,
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const Divider(height: 32),
                    ...timeline.stages.map((stage) {
                      final isLast = stage == timeline.stages.last;
                      return Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Column(
                            children: [
                              CircleAvatar(
                                radius: 14,
                                backgroundColor: stage.isCompleted
                                    ? AppTheme.successGreen
                                    : (stage.isInProgress ? AppTheme.warningAmber : AppTheme.borderSubtle),
                                child: Icon(
                                  stage.isCompleted
                                      ? Icons.check
                                      : (stage.isInProgress ? Icons.hourglass_top : Icons.circle),
                                  size: 14,
                                  color: Colors.white,
                                ),
                              ),
                              if (!isLast)
                                Container(
                                  width: 2,
                                  height: 48,
                                  color: stage.isCompleted ? AppTheme.successGreen : AppTheme.borderSubtle,
                                ),
                            ],
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  stage.title,
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 15,
                                    color: stage.isPending ? AppTheme.textMuted : AppTheme.textPrimary,
                                  ),
                                ),
                                if (stage.subtitle != null) ...[
                                  const SizedBox(height: 4),
                                  Text(
                                    stage.subtitle!,
                                    style: const TextStyle(fontSize: 13, color: AppTheme.textSecondary),
                                  ),
                                ],
                                const SizedBox(height: 24),
                              ],
                            ),
                          ),
                        ],
                      );
                    }),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
