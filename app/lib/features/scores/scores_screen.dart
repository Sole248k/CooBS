import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/theme_controller.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import 'deals_catalog.dart';

/// One debit transaction: activity, receiver, txn no., date, amount, account used.
class Debit {
  final String activity, receiver, ref;
  final DateTime date;
  final double amt; // positive number; shown as a negative
  final int acct;
  const Debit(this.activity, this.receiver, this.ref, this.date, this.amt, this.acct);
}

/// Live debits for the given account index from the store.
List<Debit> debitsFor(int a) {
  final out = <Debit>[];
  if (Demo.i.accts.isEmpty || a >= Demo.i.accts.length) return out;
  for (final x in Demo.i.accts[a].tx) {
    if (x.amt >= 0) continue;
    final p = x.label.split(' to ');
    final receiver = p.length > 1 ? p.last : (x.ref.isNotEmpty ? x.ref : 'Recipient');
    out.add(Debit(p.first, receiver, x.ref, x.date, -x.amt, a));
  }
  return out;
}

class ScoresPage extends StatefulWidget {
  /// Back action when shown as a navbar tab (null = pop the route).
  final VoidCallback? onBack;
  const ScoresPage({super.key, this.onBack});
  @override
  State<ScoresPage> createState() => _ScoresPageState();
}

class _ScoresPageState extends State<ScoresPage> {
  int acct = 0, page = 0;
  String selectedCity = 'Makati CBD';
  String selectedSubcategory = 'All';
  bool expandAllDeals = false;
  double? liveLat;
  double? liveLng;
  bool isLocating = false;

  Future<void> _fetchLiveLocation() async {
    if (isLocating) return;
    setState(() => isLocating = true);
    final coords = await getDeviceCoordinates();
    if (mounted) {
      setState(() {
        isLocating = false;
        if (coords != null) {
          liveLat = coords['lat'];
          liveLng = coords['lng'];
        }
      });
    }
  }
  static const per = 6;
  static const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  // Monthly spending in thousands of pesos (mock). Nov/Dec are still empty.
  static const chk = [32.0, 28, 41, 25, 30, 36, 29, 33, 38, 22];
  static const sav = [12.0, 10, 14, 8, 11, 9, 15, 12, 10, 6];

  Widget scoreCard() {
    final score = Demo.i.creditScore ?? 750;
    final rating = score >= 800 ? 'Exceptional' : (score >= 740 ? 'Very Good' : (score >= 670 ? 'Good' : 'Fair'));
    final progress = ((score - 300) / (850 - 300)).clamp(0.0, 1.0);

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: BorderSide(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 0.9,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: Column(
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Your Credit Score',
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: kInk,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: kDark ? const Color(0xFF0F291E) : const Color(0xFFDCFCE7),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: kDark ? const Color(0xFF166534) : const Color(0xFFBBF7D0),
                      width: 0.8,
                    ),
                  ),
                  child: Text(
                    rating,
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              '$score',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 60,
                fontWeight: FontWeight.w600,
                color: kNavyFg,
                letterSpacing: -1.0,
                height: 1.0,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Range: 300 – 850 · Seeded reference',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 12,
                fontWeight: FontWeight.w400,
                color: kMut,
              ),
            ),
            const SizedBox(height: 18),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: SizedBox(
                height: 8,
                child: Stack(
                  children: [
                    Container(color: kLine.withAlpha(kDark ? 40 : 120)),
                    FractionallySizedBox(
                      widthFactor: progress,
                      child: Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [
                              kBlue,
                              const Color(0xFF0284C7),
                              const Color(0xFF10B981),
                            ],
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
      ),
    );
  }

  Widget financialHealthNudgesCard() {
    final score = Demo.i.creditScore ?? 750;
    final breakdown = Demo.i.spendingBreakdown;

    String topCategory = 'FOOD';
    double topAmt = 0;
    if (breakdown.isNotEmpty) {
      for (final e in breakdown.entries) {
        if (e.value > topAmt) {
          topAmt = e.value;
          topCategory = e.key;
        }
      }
    }

    final isFood = topCategory.toUpperCase().contains('FOOD') || topCategory.toUpperCase().contains('GROCER');
    final isShop = topCategory.toUpperCase().contains('SHOP');

    final String habitNudgeTitle;
    final String habitNudgeDesc;
    final IconData habitIcon;
    final Color habitAccent;

    if (isFood) {
      habitNudgeTitle = 'Dining & Cafe Affinity';
      habitNudgeDesc = topAmt > 0
          ? 'You spent ${peso(topAmt)} on dining & cafes this month. Enjoy up to 20% off at our partner cafes below.'
          : 'Food & cafes are a frequent expense. Check out our partner discounts in Makati & Mandaluyong.';
      habitIcon = Icons.coffee_rounded;
      habitAccent = const Color(0xFFD97706);
    } else if (isShop) {
      habitNudgeTitle = 'Retail & Fashion Affinity';
      habitNudgeDesc = topAmt > 0
          ? 'Shopping makes up ${peso(topAmt)} of your expenses. Exclusive clothing vouchers are available below.'
          : 'Exclusive member discounts at partner apparel outlets in Greenbelt and Megamall.';
      habitIcon = Icons.shopping_bag_rounded;
      habitAccent = const Color(0xFF8B5CF6);
    } else {
      habitNudgeTitle = 'Spending Habit Insights';
      habitNudgeDesc = 'Your top outflow this month is $topCategory (${peso(topAmt)}). Keeping expenses balanced maintains your financial health.';
      habitIcon = Icons.trending_up_rounded;
      habitAccent = const Color(0xFF0D9488);
    }

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 0.9,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(7),
                  decoration: BoxDecoration(
                    color: kDark ? const Color(0xFF0F291E) : const Color(0xFFDCFCE7),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(
                    Icons.auto_awesome_rounded,
                    size: 16,
                    color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A),
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  'Personalized Health Nudges',
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: kInk,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: kInBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: kLine.withAlpha(kDark ? 40 : 100)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: habitAccent.withAlpha(25),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(habitIcon, size: 16, color: habitAccent),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          habitNudgeTitle,
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: kInk,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          habitNudgeDesc,
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 11.5,
                            fontWeight: FontWeight.w400,
                            color: kMut,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: kInBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: kLine.withAlpha(kDark ? 40 : 100)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: kBlue.withAlpha(25),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(Icons.verified_user_rounded, size: 16, color: kBlue),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          score >= 750 ? 'Tier 3 Prime Status' : 'Credit Growth Target',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: kInk,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          score >= 750
                              ? 'Your score of $score is prime. You qualify for discounted member loan rates & waived transfer fees.'
                              : 'Paying on time and keeping loan balances under 30% helps raise your rating toward prime status.',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 11.5,
                            fontWeight: FontWeight.w400,
                            color: kMut,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (Demo.i.loan != null) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: kInBg,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: kLine.withAlpha(kDark ? 40 : 100)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: const Color(0xFF10B981).withAlpha(25),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.shield_outlined, size: 16, color: Color(0xFF10B981)),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Active Loan #${Demo.i.loan!.ref}',
                            style: TextStyle(
                              fontFamily: 'Poppins',
                              fontSize: 12.5,
                              fontWeight: FontWeight.w600,
                              color: kInk,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${peso(Demo.i.loan!.balance)} remaining. Next monthly repayment is on track.',
                            style: TextStyle(
                              fontFamily: 'Poppins',
                              fontSize: 11.5,
                              fontWeight: FontWeight.w400,
                              color: kMut,
                              height: 1.35,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget totalsCard() {
    final deposits = Demo.i.totalDeposits > 0
        ? Demo.i.totalDeposits
        : Demo.i.accts.fold(0.0, (sum, a) => sum + a.bal);
    final loans = Demo.i.totalLoans;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 0.9,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Total Deposits',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 12,
                      fontWeight: FontWeight.w400,
                      color: kMut,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    peso(deposits),
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: kInk,
                    ),
                  ),
                ],
              ),
            ),
            Container(width: 1, height: 36, color: kLine.withAlpha(kDark ? 40 : 100)),
            const SizedBox(width: 18),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Total Loans',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 12,
                      fontWeight: FontWeight.w400,
                      color: kMut,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    peso(loans),
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: kInk,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget spendingBreakdownCard() {
    final breakdown = Demo.i.spendingBreakdown.isNotEmpty
        ? Demo.i.spendingBreakdown
        : {
            'Groceries & Food': 8450.0,
            'Utilities & Bills': 4200.0,
            'Shopping & Retail': 3150.0,
            'Transportation': 1850.0,
            'Entertainment': 950.0,
          };

    final totalSpending = breakdown.values.fold(0.0, (sum, val) => sum + val);

    final categoryColors = [
      kBlue,
      const Color(0xFF0284C7),
      const Color(0xFF0D9488),
      const Color(0xFFD97706),
      const Color(0xFF8B5CF6),
      const Color(0xFFEC4899),
    ];

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 0.9,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    '30-Day Spending',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                      color: kInk,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  peso(totalSpending),
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                    color: kNavyFg,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (totalSpending > 0)
              ClipRRect(
                borderRadius: BorderRadius.circular(5),
                child: SizedBox(
                  height: 10,
                  child: Row(
                    children: [
                      for (var i = 0; i < breakdown.entries.length; i++)
                        Expanded(
                          flex: (breakdown.entries.elementAt(i).value * 100 / totalSpending).round().clamp(1, 1000),
                          child: Container(
                            color: categoryColors[i % categoryColors.length],
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 16),
            for (var i = 0; i < breakdown.entries.length; i++) ...[
              if (i > 0) const SizedBox(height: 10),
              Row(
                children: [
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      color: categoryColors[i % categoryColors.length],
                      borderRadius: BorderRadius.circular(3),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      breakdown.entries.elementAt(i).key,
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 13,
                        fontWeight: FontWeight.w400,
                        color: kInk,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    peso(breakdown.entries.elementAt(i).value),
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: kInk,
                    ),
                  ),
                  const SizedBox(width: 6),
                  SizedBox(
                    width: 32,
                    child: Text(
                      totalSpending > 0
                          ? '${((breakdown.entries.elementAt(i).value / totalSpending) * 100).toStringAsFixed(0)}%'
                          : '0%',
                      textAlign: TextAlign.end,
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 11,
                        fontWeight: FontWeight.w400,
                        color: kMut,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _showEditBudgetDialog(BuildContext context, String category, double currentLimit) {
    final controller = TextEditingController(text: currentLimit.toStringAsFixed(0));
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 20,
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
                  Expanded(
                    child: Text(
                      'Adjust Envelope Cap',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: kInk,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: kInBg,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      category,
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                        color: kBlue,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                'Set your monthly spending ceiling for $category. We will alert you when you approach this limit.',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 12,
                  color: kMut,
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: controller,
                keyboardType: TextInputType.number,
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: kInk,
                ),
                decoration: InputDecoration(
                  prefixText: '₱ ',
                  prefixStyle: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: kNavyFg,
                  ),
                  filled: true,
                  fillColor: kInBg,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: kLine),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [
                  for (final add in [500, 1000, 2500, 5000])
                    ActionChip(
                      label: Text('+₱$add', style: const TextStyle(fontSize: 11)),
                      onPressed: () {
                        final cur = double.tryParse(controller.text) ?? 0.0;
                        controller.text = (cur + add).toStringAsFixed(0);
                      },
                    ),
                ],
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    final val = double.tryParse(controller.text) ?? currentLimit;
                    if (val > 0) {
                      Demo.i.updateCategoryBudget(category, val);
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Updated $category budget to ${peso(val)}'),
                          duration: const Duration(seconds: 2),
                        ),
                      );
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: kNavyFg,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: const Text(
                    'Save Envelope Budget',
                    style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget smartEnvelopesCard() {
    final breakdown = Demo.i.spendingBreakdown.isNotEmpty
        ? Demo.i.spendingBreakdown
        : {
            'Groceries & Food': 8450.0,
            'Utilities & Bills': 4200.0,
            'Shopping & Retail': 3150.0,
            'Transportation': 1850.0,
            'Entertainment': 950.0,
          };

    final now = DateTime.now();
    final daysInMonth = DateTime(now.year, now.month + 1, 0).day;
    final currentDay = now.day;
    final monthFraction = (currentDay / daysInMonth).clamp(0.0, 1.0);

    double totalBudget = 0;
    double totalSpent = 0;
    for (final e in breakdown.entries) {
      totalSpent += e.value;
      totalBudget += Demo.i.getBudgetForCategory(e.key);
    }
    if (totalBudget == 0) totalBudget = 25000.0;

    final overallSpentRatio = totalSpent / totalBudget;
    final isOverallPacingFast = overallSpentRatio > (monthFraction + 0.15) && overallSpentRatio <= 1.0;
    final isOverallOverBudget = overallSpentRatio > 1.0;

    final overallPacingColor = isOverallOverBudget
        ? const Color(0xFFEF4444)
        : (isOverallPacingFast ? const Color(0xFFF59E0B) : const Color(0xFF10B981));

    final overallPacingLabel = isOverallOverBudget
        ? 'Budget Exceeded'
        : (isOverallPacingFast ? 'Pacing Ahead' : 'Pacing Safe');

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 0.9,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(7),
                        decoration: BoxDecoration(
                          color: overallPacingColor.withAlpha(25),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(Icons.account_balance_wallet_rounded, size: 16, color: overallPacingColor),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Smart Envelopes',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontWeight: FontWeight.w600,
                                fontSize: 14,
                                color: kInk,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                            Text(
                              'Day $currentDay of $daysInMonth (${(monthFraction * 100).toStringAsFixed(0)}% of month)',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 11,
                                color: kMut,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: overallPacingColor.withAlpha(kDark ? 40 : 25),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: overallPacingColor.withAlpha(70), width: 0.8),
                  ),
                  child: Text(
                    overallPacingLabel,
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: overallPacingColor,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // Overview numbers bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: kInBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: kLine.withAlpha(kDark ? 40 : 100)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Total Budget', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut)),
                        const SizedBox(height: 2),
                        Text(peso(totalBudget), style: TextStyle(fontFamily: 'Poppins', fontSize: 13, fontWeight: FontWeight.w600, color: kInk)),
                      ],
                    ),
                  ),
                  Container(width: 1, height: 28, color: kLine),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Spent', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut)),
                        const SizedBox(height: 2),
                        Text(peso(totalSpent), style: TextStyle(fontFamily: 'Poppins', fontSize: 13, fontWeight: FontWeight.w600, color: kNavyFg)),
                      ],
                    ),
                  ),
                  Container(width: 1, height: 28, color: kLine),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Remaining', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut)),
                        const SizedBox(height: 2),
                        Text(
                          peso((totalBudget - totalSpent).clamp(0.0, double.infinity)),
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: isOverallOverBudget ? const Color(0xFFEF4444) : const Color(0xFF10B981),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Envelopes List
            for (final entry in breakdown.entries) ...[
              Builder(
                builder: (context) {
                  final cat = entry.key;
                  final spent = entry.value;
                  final limit = Demo.i.getBudgetForCategory(cat);
                  final ratio = limit > 0 ? (spent / limit) : 1.0;
                  final isExceeded = spent > limit;
                  final isPacingFast = ratio > (monthFraction + 0.15) && !isExceeded;
                  final envColor = isExceeded
                      ? const Color(0xFFEF4444)
                      : (isPacingFast ? const Color(0xFFF59E0B) : const Color(0xFF10B981));

                  return InkWell(
                    onTap: () => _showEditBudgetDialog(context, cat, limit),
                    borderRadius: BorderRadius.circular(12),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 2),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Row(
                                  children: [
                                    Flexible(
                                      child: Text(
                                        cat,
                                        style: TextStyle(
                                          fontFamily: 'Poppins',
                                          fontSize: 13,
                                          fontWeight: FontWeight.w500,
                                          color: kInk,
                                        ),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    Icon(Icons.edit_rounded, size: 12, color: kMut),
                                  ],
                                ),
                              ),
                              Row(
                                children: [
                                  Text(
                                    peso(spent),
                                    style: TextStyle(
                                      fontFamily: 'Poppins',
                                      fontSize: 12.5,
                                      fontWeight: FontWeight.w600,
                                      color: kInk,
                                    ),
                                  ),
                                  Text(
                                    ' / ${peso(limit)}',
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
                          const SizedBox(height: 6),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: SizedBox(
                              height: 6,
                              child: Stack(
                                children: [
                                  Container(color: kLine.withAlpha(kDark ? 40 : 100)),
                                  FractionallySizedBox(
                                    widthFactor: ratio.clamp(0.0, 1.0),
                                    child: Container(color: envColor),
                                  ),
                                ],
                              ),
                            ),
                          ),
                          const SizedBox(height: 4),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                isExceeded
                                    ? 'Over cap by ${peso(spent - limit)}'
                                    : '${peso(limit - spent)} left in envelope',
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w500,
                                  color: envColor,
                                ),
                              ),
                              Text(
                                '${(ratio * 100).toStringAsFixed(0)}% used',
                                style: TextStyle(
                                  fontFamily: 'Poppins',
                                  fontSize: 10.5,
                                  color: kMut,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _filterChip(String label, bool isSelected, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: isSelected ? kNavyFg : (kDark ? const Color(0xFF16202E) : const Color(0xFFF1F5F9)),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontFamily: 'Poppins',
            fontSize: 11,
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
            color: isSelected ? Colors.white : kMut,
          ),
        ),
      ),
    );
  }

  Widget _subChip(String label, bool isSelected, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: isSelected ? (kDark ? const Color(0xFF1E3A5F) : const Color(0xFFE2E8F0)) : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: kLine.withAlpha(kDark ? 40 : 100)),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontFamily: 'Poppins',
            fontSize: 11,
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
            color: isSelected ? kInk : kMut,
          ),
        ),
      ),
    );
  }

  Widget _buildDealItem(EnrichedDeal enriched) {
    final deal = enriched.deal;
    final isCafe = deal.subcategory == 'Cafe';

    return InkWell(
      onTap: () => _showDealModal(context, enriched),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: kInBg,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: enriched.isHabitMatch
                ? (kDark ? const Color(0xFF059669) : const Color(0xFF10B981)).withAlpha(90)
                : kLine.withAlpha(kDark ? 30 : 80),
            width: enriched.isHabitMatch ? 1.2 : 0.8,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: isCafe ? const Color(0xFFFEF3C7) : const Color(0xFFEDE9FE),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Center(
                child: Icon(
                  isCafe ? Icons.local_cafe_rounded : Icons.checkroom_rounded,
                  size: 20,
                  color: isCafe ? const Color(0xFFD97706) : const Color(0xFF7C3AED),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          deal.name,
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: kInk,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                        decoration: BoxDecoration(
                          color: kDark ? const Color(0xFF0F291E) : const Color(0xFFDCFCE7),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          deal.discount,
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 10.5,
                            fontWeight: FontWeight.w700,
                            color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${deal.area} · ${enriched.formattedDistance}',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 11,
                      color: kMut,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    deal.title,
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 11.5,
                      fontWeight: FontWeight.w400,
                      color: kInk,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (enriched.isHabitMatch) ...[
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Icon(Icons.auto_awesome, size: 11, color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A)),
                        const SizedBox(width: 4),
                        Text(
                          'Matches your 30-day spending habits',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 10,
                            fontWeight: FontWeight.w500,
                            color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A),
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showDealModal(BuildContext context, EnrichedDeal enriched) {
    final deal = enriched.deal;
    final isCafe = deal.subcategory == 'Cafe';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: kSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(ctx).viewInsets.bottom + 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: isCafe ? const Color(0xFFFEF3C7) : const Color(0xFFEDE9FE),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    isCafe ? Icons.local_cafe_rounded : Icons.checkroom_rounded,
                    size: 20,
                    color: isCafe ? const Color(0xFFD97706) : const Color(0xFF7C3AED),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        deal.name,
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 16, fontWeight: FontWeight.w600, color: kInk),
                      ),
                      Text(
                        '${deal.area}, ${deal.city} · ${enriched.formattedDistance}',
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: kMut),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close_rounded),
                  onPressed: () => Navigator.pop(ctx),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: kDark ? const Color(0xFF0F291E) : const Color(0xFFDCFCE7),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: kDark ? const Color(0xFF166534) : const Color(0xFFBBF7D0)),
              ),
              child: Row(
                children: [
                  Text(
                    deal.discount,
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      deal.title,
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        color: kDark ? const Color(0xFFE2E8F0) : const Color(0xFF14532D),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'PROMO CODE',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: kMut,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: kInBg,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: kLine),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    deal.promoCode,
                    style: TextStyle(
                      fontFamily: 'Courier',
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.2,
                      color: kInk,
                    ),
                  ),
                  TextButton.icon(
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      backgroundColor: kBlue,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    onPressed: () {
                      Clipboard.setData(ClipboardData(text: deal.promoCode));
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Promo code ${deal.promoCode} copied to clipboard!'),
                          duration: const Duration(seconds: 2),
                          behavior: SnackBarBehavior.floating,
                        ),
                      );
                    },
                    icon: const Icon(Icons.copy_rounded, size: 14),
                    label: const Text('Copy', style: TextStyle(fontFamily: 'Poppins', fontSize: 12, fontWeight: FontWeight.w600)),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            Text(
              'Terms & Conditions',
              style: TextStyle(fontFamily: 'Poppins', fontSize: 11, fontWeight: FontWeight.w600, color: kMut),
            ),
            const SizedBox(height: 4),
            Text(
              deal.terms,
              style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut, height: 1.4),
            ),
            const SizedBox(height: 8),
            Text(
              'Valid until: ${deal.validUntil}',
              style: TextStyle(fontFamily: 'Poppins', fontSize: 11, fontWeight: FontWeight.w500, color: kInk),
            ),
          ],
        ),
      ),
    );
  }

  Widget dealsSection() {
    String topCategory = 'FOOD';
    double topAmt = 0;
    for (final e in Demo.i.spendingBreakdown.entries) {
      if (e.value > topAmt) {
        topAmt = e.value;
        topCategory = e.key;
      }
    }

    final bool hasLiveGps = liveLat != null && liveLng != null;
    final double userLat = liveLat ?? (selectedCity == 'Mandaluyong' ? kMandaluyongLat : kDefaultLat);
    final double userLng = liveLng ?? (selectedCity == 'Mandaluyong' ? kMandaluyongLng : kDefaultLng);

    final allDeals = getRankedDeals(
      userLat: userLat,
      userLng: userLng,
      topCategory: topCategory,
      cityFilter: selectedCity,
      subcategoryFilter: selectedSubcategory,
    );

    final habitMatchCount = allDeals.where((d) => d.isHabitMatch).length;
    final displayDeals = expandAllDeals ? allDeals : allDeals.take(5).toList();

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(
          color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
          width: 0.9,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(6),
                            decoration: BoxDecoration(
                              color: kDark ? const Color(0xFF0F291E) : const Color(0xFFDCFCE7),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Icon(
                              Icons.local_offer_rounded,
                              size: 14,
                              color: kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Personalized Geo-Deals',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: kInk,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            hasLiveGps ? Icons.my_location_rounded : Icons.location_on_outlined,
                            size: 12,
                            color: hasLiveGps
                                ? (kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A))
                                : kMut,
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              hasLiveGps
                                  ? 'Measured from live device GPS'
                                  : (selectedCity == 'Mandaluyong'
                                      ? 'Measured from Mandaluyong Center'
                                      : 'Measured from Makati CBD Center'),
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 10.5,
                                fontWeight: hasLiveGps ? FontWeight.w500 : FontWeight.w400,
                                color: hasLiveGps
                                    ? (kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A))
                                    : kMut,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                InkWell(
                  onTap: _fetchLiveLocation,
                  borderRadius: BorderRadius.circular(10),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                    decoration: BoxDecoration(
                      color: hasLiveGps
                          ? (kDark ? const Color(0xFF0F291E) : const Color(0xFFDCFCE7))
                          : (kDark ? const Color(0xFF0C1F2E) : const Color(0xFFE0F2FE)),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: hasLiveGps
                            ? (kDark ? const Color(0xFF166534) : const Color(0xFFBBF7D0))
                            : (kDark ? const Color(0xFF0369A1) : const Color(0xFFBAE6FD)),
                        width: 0.8,
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          isLocating
                              ? Icons.sync_rounded
                              : (hasLiveGps ? Icons.gps_fixed_rounded : Icons.my_location_rounded),
                          size: 12,
                          color: hasLiveGps
                              ? (kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A))
                              : kBlue,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          isLocating
                              ? 'Locating...'
                              : (hasLiveGps ? 'GPS Active' : 'Use GPS'),
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 10.5,
                            fontWeight: FontWeight.w600,
                            color: hasLiveGps
                                ? (kDark ? const Color(0xFF4ADE80) : const Color(0xFF16A34A))
                                : kNavyFg,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // City location filter chips
            Row(
              children: [
                _filterChip('Makati CBD', selectedCity == 'Makati CBD', () {
                  setState(() => selectedCity = 'Makati CBD');
                }),
                const SizedBox(width: 6),
                _filterChip('Mandaluyong', selectedCity == 'Mandaluyong', () {
                  setState(() => selectedCity = 'Mandaluyong');
                }),
                const SizedBox(width: 6),
                _filterChip('All Cities', selectedCity == 'All', () {
                  setState(() => selectedCity = 'All');
                }),
              ],
            ),
            const SizedBox(height: 8),

            // Subcategory filter chips
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              clipBehavior: Clip.none,
              child: Row(
                children: [
                  _subChip('All (${allDeals.length})', selectedSubcategory == 'All', () {
                    setState(() => selectedSubcategory = 'All');
                  }),
                  const SizedBox(width: 6),
                  _subChip('☕ Cafes', selectedSubcategory == 'Cafe', () {
                    setState(() => selectedSubcategory = 'Cafe');
                  }),
                  const SizedBox(width: 6),
                  _subChip('👕 Clothes', selectedSubcategory == 'Clothes', () {
                    setState(() => selectedSubcategory = 'Clothes');
                  }),
                  if (habitMatchCount > 0) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFEF3C7),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '⭐ $habitMatchCount Habit Matches',
                        style: const TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFF92400E),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 14),

            // Deal cards list
            if (displayDeals.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 20),
                child: Center(
                  child: Text('No deals found for this filter', style: TextStyle(fontFamily: 'Poppins', color: kMut, fontSize: 13)),
                ),
              )
            else
              for (var i = 0; i < displayDeals.length; i++) ...[
                if (i > 0) const SizedBox(height: 10),
                _buildDealItem(displayDeals[i]),
              ],

            if (allDeals.length > 5) ...[
              const SizedBox(height: 12),
              Center(
                child: TextButton.icon(
                  onPressed: () => setState(() => expandAllDeals = !expandAllDeals),
                  icon: Icon(expandAllDeals ? Icons.keyboard_arrow_up_rounded : Icons.keyboard_arrow_down_rounded, size: 18),
                  label: Text(
                    expandAllDeals ? 'Show Less' : 'Show All ${allDeals.length} Partner Deals',
                    style: const TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w600, fontSize: 12.5),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget bar(int i, double h) => Column(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 14, height: sav[i] / 60 * h, color: const Color(0xFF0284C7)),
        Container(width: 14, height: chk[i] / 60 * h, color: kBlue),
      ]);

  Widget dot(Color c, String t) => Row(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 10, height: 10, color: c),
        const SizedBox(width: 6),
        Text(t, style: TextStyle(fontSize: 12, color: kMut)),
        const SizedBox(width: 14),
      ]);

  Widget chart() {
    const h = 170.0;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 16, 12, 14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Spending habits (₱ thousands per month)', style: TextStyle(fontFamily: 'Poppins', fontWeight: FontWeight.w500, fontSize: 14, color: kInk)),
          const SizedBox(height: 14),
          SizedBox(
            height: h + 22,
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SizedBox(
                  width: 28, height: h,
                  child: Column(mainAxisAlignment: MainAxisAlignment.spaceBetween, crossAxisAlignment: CrossAxisAlignment.start, children: [
                    for (final t in [60, 40, 20, 0]) Text('$t', style: TextStyle(fontSize: 11, color: kMut)),
                  ])),
              Expanded(
                child: Column(children: [
                  SizedBox(
                    height: h,
                    child: Stack(children: [
                      for (final f in [0.0, 1 / 3, 2 / 3, 1.0])
                        Positioned(left: 0, right: 0, top: f * (h - 1), child: Container(height: 1, color: kLine)),
                      Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                        for (var i = 0; i < 12; i++)
                          Expanded(child: Align(alignment: Alignment.bottomCenter, child: i < chk.length ? bar(i, h) : const SizedBox())),
                      ]),
                    ]),
                  ),
                  const SizedBox(height: 6),
                  Row(children: [
                    for (final m in months) Expanded(child: Text(m, textAlign: TextAlign.center, style: TextStyle(fontSize: 9, color: kMut))),
                  ]),
                ]),
              ),
            ]),
          ),
          const SizedBox(height: 8),
          Row(children: [dot(kBlue, 'Checking'), dot(const Color(0xFF0284C7), 'Savings')]),
        ]),
      ),
    );
  }

  Widget row(Debit d) {
    final typeName = (d.acct < Demo.i.accts.length) ? Demo.i.accts[d.acct].type : 'Account';
    final hasNamedReceiver = d.receiver.isNotEmpty &&
        d.receiver != d.ref &&
        !d.receiver.startsWith('TX-') &&
        d.receiver != 'Recipient';

    final cleanRef = d.ref.isNotEmpty && d.ref != 'TXN' && !d.ref.startsWith('Just now')
        ? (d.ref.contains('·') ? d.ref.split('·').last.trim() : d.ref)
        : '';
    final shortRef = cleanRef.isNotEmpty
        ? (cleanRef.length > 14 ? '${cleanRef.substring(0, 13)}…' : cleanRef)
        : '';

    final String subtitleText;
    if (hasNamedReceiver) {
      subtitleText = 'To ${d.receiver}${shortRef.isNotEmpty ? ' · $shortRef' : ''} · $typeName';
    } else if (shortRef.isNotEmpty) {
      subtitleText = '$shortRef · $typeName';
    } else {
      subtitleText = typeName;
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: kOutBg,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(
              Icons.arrow_outward_rounded,
              size: 20,
              color: kDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  d.activity,
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                    color: kInk,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 3),
                Text(
                  subtitleText,
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 12,
                    fontWeight: FontWeight.w400,
                    color: kMut,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '- ${peso(d.amt)}',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontWeight: FontWeight.w500,
                  fontSize: 14,
                  color: kInk,
                  letterSpacing: -0.2,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                'Success',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 11,
                  fontWeight: FontWeight.w400,
                  color: kDark ? const Color(0xFF00B4D8) : const Color(0xFF059669),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('Test');
    if (!isTest) {
      _fetchLiveLocation();
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        Demo.i.fetchScoresAndHabits();
        if (Demo.i.state == LoadState.loading) {
          Demo.i.load();
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([Demo.i, ThemeController.i]),
      builder: (context, _) {
        final safeAcct = Demo.i.accts.isEmpty ? 0 : (acct < Demo.i.accts.length ? acct : 0);
        final all = debitsFor(safeAcct);
        final pages = all.isEmpty ? 1 : (all.length / per).ceil();
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        final g = <DateTime, List<Debit>>{};
        for (final d in all.skip(page * per).take(per)) {
          g.putIfAbsent(d.date, () => []).add(d);
        }
        return Pg('Scores and Habits', [
          scoreCard(),
          const SizedBox(height: 8),
          financialHealthNudgesCard(),
          const SizedBox(height: 8),
          totalsCard(),
          const SizedBox(height: 8),
          spendingBreakdownCard(),
          const SizedBox(height: 8),
          smartEnvelopesCard(),
          const SizedBox(height: 8),
          chart(),
          Padding(
            padding: const EdgeInsets.only(top: 20, bottom: 4),
            child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Expanded(
                child: Text(
                  'Debit transaction history',
                  style: TextStyle(fontFamily: 'Poppins', fontSize: 15, fontWeight: FontWeight.w500, color: kInk),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              PopupMenuButton<int>(
                onSelected: (v) => setState(() { acct = v; page = 0; }),
                itemBuilder: (_) => <PopupMenuEntry<int>>[
                  for (var i = 0; i < Demo.i.accts.length; i++)
                    PopupMenuItem(value: i, child: Text(Demo.i.accts[i].type)),
                ],
                child: Container(
                  padding: const EdgeInsets.only(left: 14, right: 6, top: 6, bottom: 6),
                  decoration: BoxDecoration(color: safeAcct == 0 ? kBlue : const Color(0xFF0284C7), borderRadius: BorderRadius.circular(16)),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    Text(
                      safeAcct < Demo.i.accts.length ? Demo.i.accts[safeAcct].type.toUpperCase() : 'ACCOUNT',
                      style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    if (Demo.i.accts.length > 1) const Icon(Icons.arrow_drop_down, color: Colors.white),
                  ]),
                ),
              ),
            ]),
          ),
          if (all.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 36),
                child: Center(
                  child: Text('No debit transactions found', style: TextStyle(color: kMut, fontSize: 14)),
                ),
              ),
            )
          else
            for (final e in g.entries) ...[
              Padding(
                padding: const EdgeInsets.only(top: 14, bottom: 8),
                child: Center(
                  child: Text(
                    fmt(e.key),
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontWeight: FontWeight.w500,
                      fontSize: 12,
                      color: kMut,
                    ),
                  ),
                ),
              ),
              Card(
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                  side: BorderSide(
                    color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220),
                    width: 0.9,
                  ),
                ),
                child: Column(
                  children: [
                    for (var i = 0; i < e.value.length; i++) ...[
                      if (i > 0)
                        Divider(
                          height: 1,
                          thickness: 0.8,
                          indent: 72,
                          endIndent: 16,
                          color: kLine.withAlpha(kDark ? 30 : 100),
                        ),
                      row(e.value[i]),
                    ],
                  ],
                ),
              ),
            ],
          if (all.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                IconButton(icon: Icon(Icons.arrow_left, size: 38, color: kNavyFg), onPressed: page > 0 ? () => setState(() => page--) : null),
                Text('${page + 1} / $pages', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: kInk)),
                IconButton(icon: Icon(Icons.arrow_right, size: 38, color: kNavyFg), onPressed: page < pages - 1 ? () => setState(() => page++) : null),
              ]),
            ),
          const SizedBox(height: 16),
          dealsSection(),
        ], onBack: widget.onBack);
      },
    );
  }
}
