import 'package:flutter/material.dart';

import '../../core/theme/theme_controller.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import 'share_capital_screen.dart';

IconData _vaultIcon(int code) {
  switch (code) {
    case 0xe59c:
      return Icons.savings_rounded;
    case 0xe395:
      return Icons.school_rounded;
    case 0xe3ae:
      return Icons.groups_rounded;
    case 0xe06e:
      return Icons.account_balance_rounded;
    case 0xe5ce:
      return Icons.shield_rounded;
    case 0xe000:
    case 0xf0621:
      return Icons.shield_outlined;
    case 0xe040:
      return Icons.account_balance_wallet_rounded;
    case 0xe1db:
      return Icons.directions_car_rounded;
    case 0xe318:
      return Icons.home_rounded;
    case 0xe539:
      return Icons.flight_takeoff_rounded;
    case 0xe84f:
      return Icons.favorite_rounded;
    case 0xe227:
      return Icons.medical_services_rounded;
    default:
      return Icons.savings_rounded;
  }
}

/// Dedicated Co-op Ipon Vaults Page.
/// Features a round rectangular minimalist 2-column grid layout with
/// soft pastel tints, clean typography, and interactive sub-savings milestone tracking.
class IponVaultsPage extends StatefulWidget {
  final VoidCallback? onBack;
  final bool tab;

  const IponVaultsPage({
    super.key,
    this.onBack,
    this.tab = true,
  });

  @override
  State<IponVaultsPage> createState() => _IponVaultsPageState();
}

class _IponVaultsPageState extends State<IponVaultsPage> {
  String _selectedCategory = 'All';

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([ThemeController.i, Demo.i]),
      builder: (context, _) {
        final d = Demo.i;
        final allVaults = d.iponVaults;
        final filteredVaults = _selectedCategory == 'All'
            ? allVaults
            : allVaults.where((v) => v.category.toLowerCase() == _selectedCategory.toLowerCase()).toList();

        final totalEarmarked = d.totalEarmarkedInVaults;
        final savings = d.accts.length > 1 ? d.accts[1] : (d.accts.isNotEmpty ? d.accts[0] : null);
        final savingsBal = savings?.bal ?? 0.0;
        final totalTarget = allVaults.fold(0.0, (sum, v) => sum + v.targetAmount);
        final overallProgress = totalTarget > 0 ? (totalEarmarked / totalTarget).clamp(0.0, 1.0) : 0.0;
        final completedCount = allVaults.where((v) => v.isCompleted).length;

        return Pg(
          'Co-op Ipon Vaults',
          [
            // 1. Minimalist Hero Summary Banner
            _buildMinimalistHeroBanner(
              totalEarmarked: totalEarmarked,
              savingsBal: savingsBal,
              savingsType: savings?.type ?? 'Savings',
              overallProgress: overallProgress,
              totalVaults: allVaults.length,
              completedCount: completedCount,
            ),
            const SizedBox(height: 14),

            // 2. Filter Pills
            _buildFilterRow(allVaults),
            const SizedBox(height: 14),

            // 3. Round Rectangular Minimalist Boxes (2-Column Grid)
            if (filteredVaults.isEmpty)
              _buildEmptyState()
            else
              _buildVaultsGrid(filteredVaults),

            const SizedBox(height: 16),

            // 4. Minimalist Co-op Tip
            _buildMinimalistTipBanner(),
            const SizedBox(height: 14),

            // 5. Recent Activity Log
            _buildMinimalistActivityLog(d),
            const SizedBox(height: 80),
          ],
          onBack: widget.onBack,
          tab: widget.tab,
          actions: [
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: Center(
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ShareCapitalScreen())),
                    borderRadius: BorderRadius.circular(16),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(
                        color: const Color(0xFFD97706).withAlpha(kDark ? 45 : 22),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: const Color(0xFFD97706).withAlpha(kDark ? 90 : 60),
                          width: 1,
                        ),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.workspace_premium_rounded, size: 15, color: Color(0xFFD97706)),
                          SizedBox(width: 4),
                          Text(
                            'Certificate',
                            style: TextStyle(
                              fontFamily: 'Poppins',
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFFD97706),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(right: 14),
              child: Center(
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () => _showCreateVaultDialog(context),
                    borderRadius: BorderRadius.circular(16),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: kBlue.withAlpha(kDark ? 45 : 22),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: kBlue.withAlpha(kDark ? 90 : 60),
                          width: 1,
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.add_rounded, size: 15, color: kBlue),
                          const SizedBox(width: 3),
                          Text(
                            'New Vault',
                            style: TextStyle(
                              fontFamily: 'Poppins',
                              fontSize: 11.5,
                              fontWeight: FontWeight.w600,
                              color: kBlue,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  /// Minimalist hero banner.
  Widget _buildMinimalistHeroBanner({
    required double totalEarmarked,
    required double savingsBal,
    required String savingsType,
    required double overallProgress,
    required int totalVaults,
    required int completedCount,
  }) {
    final pct = (overallProgress * 100).round();

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: kDark
              ? [
                  const Color(0xFF000107),
                  const Color(0xFF003844),
                  const Color(0xFF005F73),
                ]
              : [
                  const Color(0xFF00222E),
                  const Color(0xFF005F73),
                  const Color(0xFF0091AE),
                ],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(4.5),
                    decoration: BoxDecoration(
                      color: Colors.white.withAlpha(35),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.savings_rounded, size: 13, color: Colors.white),
                  ),
                  const SizedBox(width: 6),
                  const Text(
                    'COOPERATIVE IPON PROGRAM',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 9.5,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                      letterSpacing: 0.6,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: Colors.black.withAlpha(45),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  '$completedCount of $totalVaults Met',
                  style: const TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 10,
                    fontWeight: FontWeight.w500,
                    color: Colors.white70,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Total Earmarked in Vaults',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 11,
                        color: Colors.white70,
                      ),
                    ),
                    Text(
                      peso(totalEarmarked),
                      style: const TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 22,
                        fontWeight: FontWeight.w500,
                        color: Colors.white,
                        letterSpacing: -0.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${peso(savingsBal)} available',
                    style: const TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 10.5,
                      color: Color(0xFF67E8F9),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                    decoration: BoxDecoration(
                      color: Colors.white.withAlpha(28),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      '$pct% Reached',
                      style: const TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: SizedBox(
              height: 4.5,
              child: Stack(
                children: [
                  Container(color: Colors.white.withAlpha(40)),
                  FractionallySizedBox(
                    widthFactor: overallProgress,
                    child: Container(
                      decoration: const BoxDecoration(
                        gradient: LinearGradient(
                          colors: [Color(0xFF2DD4BF), Color(0xFF67E8F9)],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Category filter pills.
  Widget _buildFilterRow(List<IponVault> allVaults) {
    final categories = ['All', 'Emergency', 'Community', 'Cooperative', 'Education'];

    return SizedBox(
      height: 32,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: categories.length,
        separatorBuilder: (context, index) => const SizedBox(width: 6),
        itemBuilder: (context, idx) {
          final cat = categories[idx];
          final isSelected = _selectedCategory.toLowerCase() == cat.toLowerCase();
          final count = cat == 'All'
              ? allVaults.length
              : allVaults.where((v) => v.category.toLowerCase() == cat.toLowerCase()).length;

          return InkWell(
            onTap: () => setState(() => _selectedCategory = cat),
            borderRadius: BorderRadius.circular(16),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
              decoration: BoxDecoration(
                color: isSelected
                    ? kBlue
                    : (kDark ? Colors.white.withAlpha(14) : const Color(0xFFF1F5F9)),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isSelected
                      ? kBlue
                      : (kDark ? Colors.white.withAlpha(20) : const Color(0xFFE2E8F0)),
                  width: 0.8,
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    cat,
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 11,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                      color: isSelected ? Colors.white : kInk,
                    ),
                  ),
                  const SizedBox(width: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? Colors.white.withAlpha(50)
                          : (kDark ? Colors.white.withAlpha(22) : Colors.black.withAlpha(12)),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      '$count',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 9,
                        fontWeight: FontWeight.w600,
                        color: isSelected ? Colors.white : kMut,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  /// 2-Column Grid of Round Rectangular Minimalist Boxes.
  Widget _buildVaultsGrid(List<IponVault> vaults) {
    final hasSlotForAdd = _selectedCategory == 'All';

    return LayoutBuilder(
      builder: (context, constraints) {
        final itemWidth = (constraints.maxWidth - 12) / 2;

        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            for (final v in vaults)
              SizedBox(
                width: itemWidth,
                height: 144,
                child: _buildMinimalistVaultBox(v),
              ),
            if (hasSlotForAdd)
              SizedBox(
                width: itemWidth,
                height: 144,
                child: _buildAddVaultCard(),
              ),
          ],
        );
      },
    );
  }

  /// ROUND RECTANGULAR MINIMALIST BOX (NO HEAVY CARD, SOFT PASTEL TINT, SQUIRCLE BORDER).
  Widget _buildMinimalistVaultBox(IponVault v) {
    final color = Color(v.colorValue);
    final pct = (v.progress * 100).round();
    final isShareCapital = v.name.toLowerCase().contains('share capital') || v.category.toLowerCase() == 'cooperative';

    return InkWell(
      onTap: () {
        if (isShareCapital) {
          Navigator.push(context, MaterialPageRoute(builder: (_) => const ShareCapitalScreen()));
        } else {
          _showManageVaultSheet(context, v);
        }
      },
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.all(13),
        decoration: BoxDecoration(
          color: color.withAlpha(kDark ? 28 : 14),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: color.withAlpha(kDark ? 65 : 35),
            width: 1.0,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            // Top row: Cute squircle icon + percentage chip
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: color.withAlpha(kDark ? 45 : 28),
                    borderRadius: BorderRadius.circular(11),
                  ),
                  child: Icon(
                    _vaultIcon(v.iconCode),
                    size: 17,
                    color: color,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6.5, vertical: 2.5),
                  decoration: BoxDecoration(
                    color: (v.isCompleted ? const Color(0xFF10B981) : color).withAlpha(kDark ? 40 : 25),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    v.isCompleted ? '🎉 Met' : '$pct%',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: v.isCompleted ? const Color(0xFF10B981) : color,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),

            // Middle: Name & due date
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  v.name,
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: kInk,
                    height: 1.2,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 1.5),
                Text(
                  'Due ${v.targetDate}',
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 9.5,
                    color: kMut,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),

            // Bottom: Balance & minimal progress bar
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Flexible(
                      child: Text(
                        peso(v.currentAmount),
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: kNavyFg,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 3),
                    Text(
                      'of ${peso(v.targetAmount)}',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 9.5,
                        color: kMut,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 5),
                ClipRRect(
                  borderRadius: BorderRadius.circular(2),
                  child: SizedBox(
                    height: 3.5,
                    child: Stack(
                      children: [
                        Container(color: color.withAlpha(kDark ? 35 : 22)),
                        FractionallySizedBox(
                          widthFactor: v.progress,
                          child: Container(
                            decoration: BoxDecoration(
                              color: color,
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// Minimalist slot to add a new goal in the 2-column grid.
  Widget _buildAddVaultCard() {
    return InkWell(
      onTap: () => _showCreateVaultDialog(context),
      borderRadius: BorderRadius.circular(18),
      child: Container(
        padding: const EdgeInsets.all(13),
        decoration: BoxDecoration(
          color: kDark ? Colors.white.withAlpha(8) : const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: kDark ? Colors.white.withAlpha(22) : const Color(0xFFCBD5E1),
            width: 1.0,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: kBlue.withAlpha(kDark ? 40 : 20),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.add_rounded, size: 20, color: kBlue),
            ),
            const SizedBox(height: 8),
            Text(
              'New Goal',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
                color: kInk,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              'Tap to create',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 10,
                color: kMut,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
      decoration: BoxDecoration(
        color: kBlue.withAlpha(kDark ? 20 : 10),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: kBlue.withAlpha(kDark ? 50 : 30),
        ),
      ),
      child: Column(
        children: [
          Icon(Icons.savings_outlined, size: 30, color: kBlue),
          const SizedBox(height: 8),
          Text(
            'No vaults in this category',
            style: TextStyle(
              fontFamily: 'Poppins',
              fontSize: 13.5,
              fontWeight: FontWeight.w600,
              color: kInk,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Set a milestone goal to earmark funds for your next purchase.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Poppins',
              fontSize: 11,
              color: kMut,
            ),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: () => _showCreateVaultDialog(context),
            style: ElevatedButton.styleFrom(
              backgroundColor: kBlue,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            ),
            child: const Text('Create Goal', style: TextStyle(fontSize: 12)),
          ),
        ],
      ),
    );
  }

  Widget _buildMinimalistTipBanner() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: kBlue.withAlpha(kDark ? 24 : 12),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: kBlue.withAlpha(kDark ? 50 : 30),
          width: 0.8,
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.lightbulb_outline_rounded, size: 16, color: kBlue),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Co-op Tip: Earmarked funds stay inside your deposit balance, earning interest and annual dividends while ring-fenced from daily spending.',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 10.5,
                color: kMut,
                height: 1.35,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMinimalistActivityLog(Demo d) {
    final vaultTransactions = <Tx>[];
    for (final acc in d.accts) {
      for (final tx in acc.tx) {
        if (tx.label.toLowerCase().contains('earmarked') ||
            tx.label.toLowerCase().contains('released from') ||
            tx.ref.startsWith('VAULT-')) {
          vaultTransactions.add(tx);
        }
      }
    }

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: kDark ? Colors.white.withAlpha(10) : const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: kDark ? Colors.white.withAlpha(16) : const Color(0xFFE2E8F0),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Recent Vault Activity',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: kInk,
                ),
              ),
              Text(
                '${vaultTransactions.length} records',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 10.5,
                  color: kMut,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (vaultTransactions.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 10),
              child: Center(
                child: Text(
                  'No recent vault allocations or releases.',
                  style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut),
                ),
              ),
            )
          else
            for (var idx = 0; idx < vaultTransactions.take(3).length; idx++) ...[
              if (idx > 0) const Divider(height: 12),
              _buildTxItem(vaultTransactions[idx]),
            ],
        ],
      ),
    );
  }

  Widget _buildTxItem(Tx tx) {
    final isDeposit = tx.amt < 0;
    final absAmount = tx.amt.abs();

    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(5),
          decoration: BoxDecoration(
            color: isDeposit
                ? const Color(0xFF0D9488).withAlpha(kDark ? 40 : 20)
                : const Color(0xFF0091AE).withAlpha(kDark ? 40 : 20),
            shape: BoxShape.circle,
          ),
          child: Icon(
            isDeposit ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded,
            size: 13,
            color: isDeposit ? const Color(0xFF0D9488) : const Color(0xFF0091AE),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                tx.label,
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 11.5,
                  fontWeight: FontWeight.w500,
                  color: kInk,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              Text(
                '${tx.ref} · ${tx.date.month}/${tx.date.day}/${tx.date.year}',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 9.5,
                  color: kMut,
                ),
              ),
            ],
          ),
        ),
        Text(
          '${isDeposit ? "+" : "-"} ${peso(absAmount)}',
          style: TextStyle(
            fontFamily: 'Poppins',
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: isDeposit ? const Color(0xFF0D9488) : kNavyFg,
          ),
        ),
      ],
    );
  }

  void _showManageVaultSheet(BuildContext context, IponVault v, {bool isDepositDefault = true}) {
    bool isDeposit = isDepositDefault;
    final controller = TextEditingController(text: '1000');
    final d = Demo.i;
    final savings = d.accts.length > 1 ? d.accts[1] : (d.accts.isNotEmpty ? d.accts[0] : null);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            final color = Color(v.colorValue);
            return Container(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 18,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
              ),
              decoration: BoxDecoration(
                color: kDark ? const Color(0xFF0C141F) : Colors.white,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: kLine,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: color.withAlpha(25),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Icon(
                              _vaultIcon(v.iconCode),
                              size: 18,
                              color: color,
                            ),
                          ),
                          const SizedBox(width: 10),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                v.name,
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 15,
                                  fontWeight: FontWeight.w600,
                                  color: kInk,
                                ),
                              ),
                              Text(
                                '${v.category} · Due ${v.targetDate}',
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 11.5,
                                  color: kMut,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: (v.isCompleted ? const Color(0xFF10B981) : color).withAlpha(25),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          v.isCompleted ? 'Goal Met!' : '${(v.progress * 100).round()}%',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: v.isCompleted ? const Color(0xFF10B981) : color,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: kInBg,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Vault Balance', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut)),
                            Text(peso(v.currentAmount), style: TextStyle(fontFamily: 'Poppins', fontSize: 15, fontWeight: FontWeight.w600, color: kNavyFg)),
                          ],
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text('Target Goal', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut)),
                            Text(peso(v.targetAmount), style: TextStyle(fontFamily: 'Poppins', fontSize: 15, fontWeight: FontWeight.w600, color: kInk)),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Toggle Segment
                  Row(
                    children: [
                      Expanded(
                        child: InkWell(
                          onTap: () => setSheetState(() => isDeposit = true),
                          borderRadius: BorderRadius.circular(10),
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            decoration: BoxDecoration(
                              color: isDeposit ? kNavyFg : kInBg,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Center(
                              child: Text(
                                'Save More (Deposit)',
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: isDeposit ? Colors.white : kMut,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: InkWell(
                          onTap: () => setSheetState(() => isDeposit = false),
                          borderRadius: BorderRadius.circular(10),
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            decoration: BoxDecoration(
                              color: !isDeposit ? kNavyFg : kInBg,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Center(
                              child: Text(
                                'Release to Savings',
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: !isDeposit ? Colors.white : kMut,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  Text(
                    'Allocation Amount',
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w600, color: kInk),
                  ),
                  const SizedBox(height: 6),
                  TextField(
                    controller: controller,
                    keyboardType: TextInputType.number,
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 16, fontWeight: FontWeight.w600, color: kInk),
                    decoration: InputDecoration(
                      prefixText: '₱ ',
                      prefixStyle: TextStyle(fontFamily: 'Poppins', fontSize: 16, fontWeight: FontWeight.w600, color: kNavyFg),
                      helperText: isDeposit
                          ? 'Available in ${savings?.type ?? "Savings"}: ${peso(savings?.bal ?? 0)}'
                          : 'Available in ${v.name}: ${peso(v.currentAmount)}',
                      helperStyle: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut),
                      filled: true,
                      fillColor: kInBg,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide(color: kLine)),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide(color: kLine)),
                      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide(color: kBlue, width: 1.5)),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: [
                      for (final add in [500, 1000, 2500, 5000])
                        InkWell(
                          onTap: () => setSheetState(() => controller.text = '$add'),
                          borderRadius: BorderRadius.circular(8),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: kDark ? const Color(0xFF131F2E) : const Color(0xFFF1F5F9),
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(color: kLine),
                            ),
                            child: Text(
                              '+₱$add',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: kNavyFg,
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 18),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: () async {
                        final amt = double.tryParse(controller.text) ?? 0.0;
                        if (amt <= 0) return;
                        final err = isDeposit ? await d.allocateToVault(v.id, amt) : await d.releaseFromVault(v.id, amt);
                        if (!context.mounted) return;
                        if (err != null) {
                          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(err), backgroundColor: Colors.red));
                        } else {
                          Navigator.pop(ctx);
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(isDeposit
                                  ? '₱${amt.toStringAsFixed(2)} earmarked for ${v.name}'
                                  : '₱${amt.toStringAsFixed(2)} released back to ${savings?.type ?? "Savings"}'),
                              backgroundColor: const Color(0xFF059669),
                            ),
                          );
                        }
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isDeposit ? const Color(0xFF0D9488) : kNavyFg,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        padding: const EdgeInsets.symmetric(vertical: 13),
                      ),
                      child: Text(
                        isDeposit ? 'Confirm Allocation' : 'Release to Savings Account',
                        style: const TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600, fontSize: 14),
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  void _showCreateVaultDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final targetCtrl = TextEditingController(text: '15000');
    final dateCtrl = TextEditingController(text: 'Dec 2026');
    String selectedCategory = 'Emergency';
    final d = Demo.i;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            return Container(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 18,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
              ),
              decoration: BoxDecoration(
                color: kDark ? const Color(0xFF0C141F) : Colors.white,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(color: kLine, borderRadius: BorderRadius.circular(2)),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Create New Ipon Vault',
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 17, fontWeight: FontWeight.w600, color: kInk),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Set a goal to earmark funds for your cooperative milestone.',
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: kMut),
                  ),
                  const SizedBox(height: 18),

                  // 1. Goal Name
                  Text(
                    'Goal Name',
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w600, color: kInk),
                  ),
                  const SizedBox(height: 6),
                  TextField(
                    controller: nameCtrl,
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 13.5, fontWeight: FontWeight.w500, color: kInk),
                    decoration: InputDecoration(
                      hintText: 'e.g. Tuition, Emergency, Paluwagan',
                      hintStyle: TextStyle(fontFamily: 'Poppins', fontSize: 13, color: kMut),
                      prefixIcon: Icon(Icons.bookmark_outline_rounded, size: 18, color: kBlue),
                      filled: true,
                      fillColor: kInBg,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide(color: kLine),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide(color: kLine),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide(color: kBlue, width: 1.5),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),

                  // 2. Target Amount & Target Date
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Target Amount',
                              style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w600, color: kInk),
                            ),
                            const SizedBox(height: 6),
                            TextField(
                              controller: targetCtrl,
                              keyboardType: TextInputType.number,
                              style: TextStyle(fontFamily: 'Poppins', fontSize: 13.5, fontWeight: FontWeight.w500, color: kInk),
                              decoration: InputDecoration(
                                prefixText: '₱ ',
                                prefixStyle: TextStyle(fontFamily: 'Poppins', fontSize: 13.5, fontWeight: FontWeight.w600, color: kInk),
                                hintText: '15,000',
                                hintStyle: TextStyle(fontFamily: 'Poppins', fontSize: 13, color: kMut),
                                filled: true,
                                fillColor: kInBg,
                                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: kLine),
                                ),
                                enabledBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: kLine),
                                ),
                                focusedBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: kBlue, width: 1.5),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Target Date',
                              style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w600, color: kInk),
                            ),
                            const SizedBox(height: 6),
                            TextField(
                              controller: dateCtrl,
                              style: TextStyle(fontFamily: 'Poppins', fontSize: 13.5, fontWeight: FontWeight.w500, color: kInk),
                              decoration: InputDecoration(
                                prefixIcon: Icon(Icons.calendar_today_outlined, size: 16, color: kBlue),
                                hintText: 'Dec 2026',
                                hintStyle: TextStyle(fontFamily: 'Poppins', fontSize: 13, color: kMut),
                                filled: true,
                                fillColor: kInBg,
                                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: kLine),
                                ),
                                enabledBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: kLine),
                                ),
                                focusedBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: kBlue, width: 1.5),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // 3. Category Pills
                  Text(
                    'Category',
                    style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w600, color: kInk),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final cat in ['Emergency', 'Community', 'Cooperative', 'Education'])
                        Builder(
                          builder: (context) {
                            final isSelected = selectedCategory == cat;
                            IconData icon;
                            switch (cat) {
                              case 'Emergency':
                                icon = Icons.shield_outlined;
                                break;
                              case 'Community':
                                icon = Icons.groups_outlined;
                                break;
                              case 'Cooperative':
                                icon = Icons.account_balance_outlined;
                                break;
                              case 'Education':
                              default:
                                icon = Icons.school_outlined;
                                break;
                            }
                            return InkWell(
                              onTap: () => setSheetState(() => selectedCategory = cat),
                              borderRadius: BorderRadius.circular(10),
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 180),
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                                decoration: BoxDecoration(
                                  color: isSelected ? kBlue : (kDark ? const Color(0xFF131F2E) : const Color(0xFFF1F5F9)),
                                  borderRadius: BorderRadius.circular(10),
                                  border: Border.all(
                                    color: isSelected ? kBlue : kLine,
                                    width: 1,
                                  ),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(
                                      icon,
                                      size: 14,
                                      color: isSelected ? Colors.white : kMut,
                                    ),
                                    const SizedBox(width: 6),
                                    Text(
                                      cat,
                                      style: TextStyle(
                                        fontFamily: 'Poppins',
                                        fontSize: 11.5,
                                        fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                                        color: isSelected ? Colors.white : kInk,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                    ],
                  ),
                  const SizedBox(height: 22),

                  // 4. Submit Button
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: () async {
                        final name = nameCtrl.text.trim();
                        final target = double.tryParse(targetCtrl.text) ?? 10000.0;
                        if (name.isEmpty) return;
                        await d.createVault(
                          name: name,
                          targetAmount: target,
                          targetDate: dateCtrl.text.trim(),
                          category: selectedCategory,
                        );
                        if (!context.mounted) return;
                        Navigator.pop(ctx);
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('Created Ipon Goal: $name'),
                            backgroundColor: const Color(0xFF059669),
                          ),
                        );
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: kBlue,
                        foregroundColor: Colors.white,
                        elevation: 0,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.add_circle_outline_rounded, size: 18),
                          SizedBox(width: 8),
                          Text(
                            'Create Goal',
                            style: TextStyle(
                              fontFamily: 'Poppins',
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}
