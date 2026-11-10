import 'package:flutter/material.dart';

import '../../core/theme/theme_controller.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';

/// Dedicated Co-op Share Capital & Equity Ledger Screen.
/// Displays member digital share certificate, voting rights status,
/// capital build-up top-up engine, and annual patronage dividend distribution history.
class ShareCapitalScreen extends StatefulWidget {
  final VoidCallback? onBack;

  const ShareCapitalScreen({super.key, this.onBack});

  @override
  State<ShareCapitalScreen> createState() => _ShareCapitalScreenState();
}

class _ShareCapitalScreenState extends State<ShareCapitalScreen> {
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([ThemeController.i, Demo.i]),
      builder: (context, _) {
        final d = Demo.i;
        final sc = d.shareCapital;
        final divs = d.dividendHistory;
        final savings = d.accts.length > 1 ? d.accts[1] : (d.accts.isNotEmpty ? d.accts[0] : null);

        return Scaffold(
          backgroundColor: kBg,
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            elevation: 0,
            leading: IconButton(
              icon: Icon(Icons.arrow_back_ios_new_rounded, color: kInk, size: 20),
              onPressed: widget.onBack ?? () => Navigator.of(context).maybePop(),
            ),
            title: Text(
              'Co-op Share Capital',
              style: TextStyle(
                fontFamily: 'Poppins',
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: kInk,
              ),
            ),
            centerTitle: true,
          ),
          body: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
            children: [
              // 1. Digital Member Share Certificate Card
              _buildDigitalCertificate(sc, d.memberName),
              const SizedBox(height: 16),

              // 2. Capital Build-Up (Top-Up) Action Banner
              _buildTopUpBanner(context, sc, savings),
              const SizedBox(height: 18),

              // 3. Dividend & Patronage Performance Summary
              _buildDividendPerformanceCard(sc, divs),
              const SizedBox(height: 18),

              // 4. Historical Annual Dividend Declarations
              _buildHistoricalDividendsSection(divs),
              const SizedBox(height: 40),
            ],
          ),
        );
      },
    );
  }

  /// Digital Certificate with formal cooperative seal and equity specifications.
  Widget _buildDigitalCertificate(ShareCapitalModel sc, String memberName) {
    final pct = (sc.completionRatio * 100).round();

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: kDark
              ? [
                  const Color(0xFF1A150A),
                  const Color(0xFF2E220D),
                  const Color(0xFF1E1A0F),
                ]
              : [
                  const Color(0xFF785918),
                  const Color(0xFF9E7728),
                  const Color(0xFF5E430F),
                ],
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFFD97706).withAlpha(kDark ? 30 : 50),
            blurRadius: 18,
            offset: const Offset(0, 6),
          ),
        ],
        border: Border.all(
          color: const Color(0xFFFBBF24).withAlpha(kDark ? 70 : 120),
          width: 1.4,
        ),
      ),
      child: Stack(
        children: [
          // Background watermark seal
          Positioned(
            right: -20,
            bottom: -20,
            child: Opacity(
              opacity: 0.08,
              child: const Icon(
                Icons.verified_rounded,
                size: 180,
                color: Colors.white,
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header row: Co-op crest + Certificate No
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(7),
                          decoration: BoxDecoration(
                            color: Colors.white.withAlpha(35),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.account_balance_rounded,
                            size: 18,
                            color: Color(0xFFFDE68A),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'COOBS COOPERATIVE',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 10,
                                fontWeight: FontWeight.w800,
                                color: Color(0xFFFDE68A),
                                letterSpacing: 1.2,
                              ),
                            ),
                            Text(
                              'Member Share Certificate',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: Colors.white.withAlpha(235),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.black.withAlpha(70),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: Colors.white.withAlpha(40)),
                      ),
                      child: Text(
                        sc.certificateNo,
                        style: const TextStyle(
                          fontFamily: 'Courier',
                          fontSize: 10.5,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFFFDE68A),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),

                // Member Name
                Text(
                  memberName.toUpperCase(),
                  style: const TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                    letterSpacing: 0.5,
                  ),
                ),
                Text(
                  sc.hasVotingRights
                      ? 'Regular Member with Active Voting Rights'
                      : 'Associate Member (Pending Minimum Shares)',
                  style: TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 11,
                    color: sc.hasVotingRights ? const Color(0xFF86EFAC) : const Color(0xFFFDE68A),
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 16),

                // Subscribed vs Paid shares breakdown
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.black.withAlpha(50),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Paid-Up Capital',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 10,
                                color: Colors.white.withAlpha(180),
                              ),
                            ),
                            Text(
                              peso(sc.totalPaidAmount),
                              style: const TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: Colors.white,
                              ),
                            ),
                            Text(
                              '${sc.paidUpShares} Shares @ ₱${sc.parValue.toStringAsFixed(0)}',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 9.5,
                                color: const Color(0xFFFDE68A).withAlpha(220),
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(width: 1, height: 38, color: Colors.white.withAlpha(30)),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Subscribed Pledge',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 10,
                                color: Colors.white.withAlpha(180),
                              ),
                            ),
                            Text(
                              peso(sc.subscribedAmount),
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: Colors.white.withAlpha(220),
                              ),
                            ),
                            Text(
                              '${sc.subscribedShares} Subscribed Shares',
                              style: TextStyle(
                                fontFamily: 'Poppins',
                                fontSize: 9.5,
                                color: Colors.white.withAlpha(160),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),

                // Progress towards full subscribed pledge
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Pledge Fulfilled: $pct%',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 10.5,
                        fontWeight: FontWeight.w600,
                        color: Colors.white.withAlpha(220),
                      ),
                    ),
                    Text(
                      'Min ${sc.minimumRequiredShares} shares for voting',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 9.5,
                        color: const Color(0xFFFDE68A).withAlpha(200),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: sc.completionRatio,
                    backgroundColor: Colors.white.withAlpha(35),
                    valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFFFBBF24)),
                    minHeight: 6,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Capital Build-Up / Top-Up Action Banner.
  Widget _buildTopUpBanner(BuildContext context, ShareCapitalModel sc, Acct? savings) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: kDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: kLine),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: const Color(0xFFD97706).withAlpha(kDark ? 40 : 20),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(
                  Icons.add_chart_rounded,
                  size: 22,
                  color: Color(0xFFD97706),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Capital Build-Up (CBU)',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: kInk,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Top up shares to earn higher annual dividends.',
                      style: TextStyle(
                        fontFamily: 'Poppins',
                        fontSize: 11.5,
                        color: kMut,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            height: 44,
            child: ElevatedButton.icon(
              onPressed: () => _showTopUpModal(context, sc, savings),
              icon: const Icon(Icons.add_circle_outline_rounded, size: 18),
              label: const Text(
                'Top Up',
                style: TextStyle(
                  fontFamily: 'Poppins',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFD97706),
                foregroundColor: Colors.white,
                elevation: 0,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Dividend & Patronage Performance Card.
  Widget _buildDividendPerformanceCard(ShareCapitalModel sc, List<DividendPayoutModel> divs) {
    final lastDiv = divs.isNotEmpty ? divs.first : null;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: kDark ? const Color(0xFF0B132B) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: kLine),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.trending_up_rounded, size: 18, color: Color(0xFF10B981)),
                  const SizedBox(width: 6),
                  Text(
                    'Dividend & Patronage Yield',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: kInk,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFF10B981).withAlpha(kDark ? 40 : 20),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  '${sc.dividendRate}% p.a.',
                  style: const TextStyle(
                    fontFamily: 'Poppins',
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF10B981),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // 2 Metric boxes: Share Capital Dividend vs Patronage Refund
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: kDark ? const Color(0xFF111C36) : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Interest on Capital',
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 10, color: kMut),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        lastDiv != null ? peso(lastDiv.shareCapitalDividend) : '₱0.00',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: kInk,
                        ),
                      ),
                      Text(
                        'FY ${lastDiv?.fiscalYear ?? 2025} Payout',
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 9.5, color: kMut),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: kDark ? const Color(0xFF111C36) : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Patronage Refund',
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 10, color: kMut),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        lastDiv != null ? peso(lastDiv.patronageRefund) : '₱0.00',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: kInk,
                        ),
                      ),
                      Text(
                        'Based on transactions',
                        style: TextStyle(fontFamily: 'Poppins', fontSize: 9.5, color: kMut),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// Historical Annual Dividend Declarations.
  Widget _buildHistoricalDividendsSection(List<DividendPayoutModel> divs) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Annual Dividend Distribution History',
          style: TextStyle(
            fontFamily: 'Poppins',
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: kInk,
          ),
        ),
        const SizedBox(height: 10),
        if (divs.isEmpty)
          Container(
            padding: const EdgeInsets.all(20),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: kDark ? const Color(0xFF0F172A) : Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: kLine),
            ),
            child: Text(
              'No previous dividend records available.',
              style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: kMut),
            ),
          )
        else
          for (final d in divs) ...[
            Container(
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: kDark ? const Color(0xFF0F172A) : Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: kLine),
              ),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: const Color(0xFF10B981).withAlpha(kDark ? 35 : 18),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.payments_rounded,
                      size: 20,
                      color: Color(0xFF10B981),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Fiscal Year ${d.fiscalYear} Distribution',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: kInk,
                          ),
                        ),
                        Text(
                          '${d.referenceNo} · ${d.disposition.replaceAll('_', ' ')}',
                          style: TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 10.5,
                            color: kMut,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '+${peso(d.totalPayout)}',
                        style: const TextStyle(
                          fontFamily: 'Poppins',
                          fontSize: 13.5,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF10B981),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: const Color(0xFF10B981).withAlpha(kDark ? 40 : 20),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          d.status,
                          style: const TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 9,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF10B981),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
      ],
    );
  }

  /// Bottom sheet to top-up share capital from savings account.
  void _showTopUpModal(BuildContext context, ShareCapitalModel sc, Acct? savings) {
    final controller = TextEditingController(text: '1000');
    final available = savings?.bal ?? 0.0;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            final enteredAmt = double.tryParse(controller.text) ?? 0.0;
            final sharesToAdd = (enteredAmt / sc.parValue).floor();

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
                  Text(
                    'Top-Up Co-op Share Capital',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: kInk,
                    ),
                  ),
                  Text(
                    'Debits your ${savings?.type ?? "Savings"} account to purchase equity shares @ ₱${sc.parValue.toStringAsFixed(0)} each.',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontSize: 12,
                      color: kMut,
                    ),
                  ),
                  const SizedBox(height: 14),

                  // Available balance pill
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: kDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('Available Balance:', style: TextStyle(fontFamily: 'Poppins', fontSize: 11, color: kMut)),
                        Text(
                          peso(available),
                          style: TextStyle(fontFamily: 'Poppins', fontSize: 11, fontWeight: FontWeight.w600, color: kInk),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),

                  // Amount TextField
                  TextField(
                    controller: controller,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    onChanged: (_) => setSheetState(() {}),
                    decoration: InputDecoration(
                      prefixText: '₱ ',
                      labelText: 'Amount (PHP)',
                      filled: true,
                      fillColor: kInBg,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 10),

                  // Quick amount chips
                  Wrap(
                    spacing: 8,
                    children: [
                      for (final add in [500, 1000, 2500, 5000])
                        InkWell(
                          onTap: () {
                            controller.text = add.toString();
                            setSheetState(() {});
                          },
                          child: Chip(
                            label: Text('+₱$add', style: const TextStyle(fontSize: 11)),
                            backgroundColor: kDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Computed shares display
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    decoration: BoxDecoration(
                      color: const Color(0xFFD97706).withAlpha(kDark ? 35 : 18),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Shares Added:',
                          style: TextStyle(fontFamily: 'Poppins', fontSize: 12, color: kInk),
                        ),
                        Text(
                          '+$sharesToAdd Shares',
                          style: const TextStyle(
                            fontFamily: 'Poppins',
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFFD97706),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 18),

                  // Submit Button
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: () async {
                        final amt = double.tryParse(controller.text) ?? 0.0;
                        if (amt <= 0) return;
                        final err = await Demo.i.topUpShareCapital(amt);
                        if (!context.mounted) return;
                        if (err != null) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(err), backgroundColor: Colors.red),
                          );
                        } else {
                          Navigator.pop(ctx);
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                'Successfully subscribed to +$sharesToAdd shares (₱${amt.toStringAsFixed(2)})',
                              ),
                              backgroundColor: const Color(0xFF059669),
                            ),
                          );
                        }
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFD97706),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      child: const Text(
                        'Confirm Subscription',
                        style: TextStyle(
                          fontFamily: 'Poppins',
                          fontWeight: FontWeight.w600,
                          fontSize: 14,
                        ),
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
