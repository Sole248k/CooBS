import 'package:flutter/material.dart';
import '../../core/theme/theme_controller.dart';
import '../tracking/tracking_view.dart';
import 'demo_store.dart';

/// Mobile palette. Getters (not consts) so every screen follows the dark mode toggle.
bool get kDark => ThemeController.i.dark;

// CooBS Brand Colors: Deep Obsidian Black (#000107) & Pacific Cyan-Teal (#0091AE)
Color get kBlue => kDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE);
Color get kNavy => kDark ? const Color(0xFF0D1722) : const Color(0xFF000107); // fills & headers
Color get kNavyFg => kDark ? const Color(0xFF00B4D8) : const Color(0xFF0091AE); // text & icons
Color get kInk => kDark ? const Color(0xFFF1F5F9) : const Color(0xFF000107);
Color get kMut => kDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B);
Color get kBg => kDark ? const Color(0xFF000107) : const Color(0xFFF8FAFC);
Color get kRed => kDark ? const Color(0xFFFF7A6B) : const Color(0xFFDC2626);
Color get kSurface => kDark ? const Color(0xFF0C141F) : Colors.white;
Color get kLine => kDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0);
Color get kInBg => kDark ? const Color(0xFF082F3B) : const Color(0xFFE0F4F8);
Color get kOutBg => kDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9);
Color get kFaint => kDark ? const Color(0xFF64748B) : const Color(0xFF94A3B8);
Color get kSkel => kDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0);
Color get kDot => kDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1);

const grads = [
  [Color(0xFF000107), Color(0xFF005F73), Color(0xFF0091AE)], // Checking (Obsidian to Cyan-Teal)
  [Color(0xFF004E64), Color(0xFF0091AE), Color(0xFF25A2BE)], // Savings (Vibrant Cyan-Teal)
  [Color(0xFF0A0F1A), Color(0xFF1E293B), Color(0xFF334155)], // Loan (Midnight Obsidian Slate)
];

void snack(BuildContext c, String m) => ScaffoldMessenger.of(c)
    .showSnackBar(SnackBar(content: Text(m), behavior: SnackBarBehavior.floating));

Widget tf(TextEditingController c, String l, {TextInputType? k, ValueChanged<String>? ch, bool obscure = false}) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextField(key: ValueKey(l), controller: c, keyboardType: k, onChanged: ch, obscureText: obscure, decoration: InputDecoration(labelText: l)));

Widget dd(String l, List<String> o, String? v, ValueChanged<String?> f) {
  final effectiveValue = (v != null && o.contains(v)) ? v : (o.isNotEmpty ? o.first : null);
  return Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: DropdownButtonFormField<String>(
        key: ValueKey('$l-$effectiveValue'),
        initialValue: effectiveValue,
        items: [for (final x in o) DropdownMenuItem(value: x, child: Text(x))],
        onChanged: f,
        decoration: InputDecoration(labelText: l)));
}

Widget bigBtn(String t, VoidCallback? f) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: ElevatedButton(
        onPressed: f,
        style: ElevatedButton.styleFrom(backgroundColor: kBlue, foregroundColor: Colors.white),
        child: Text(t)));

Widget tag(String t, int g) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
    decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        gradient: LinearGradient(colors: grads[g % grads.length])),
    child: Text(t, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)));

Widget statRow(String k, String v) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
      Text(k, style: TextStyle(color: kMut)),
      const SizedBox(width: 12),
      Flexible(child: Text(v, textAlign: TextAlign.right, style: TextStyle(fontWeight: FontWeight.w600, color: kInk))),
    ]));

Widget panel(List<Widget> kids) => Card(
    child: Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: kids)));

Widget logoBox({double height = 28, bool forceDark = false}) {
  final isDark = forceDark || kDark;
  return Image.asset(
    isDark ? 'assets/images/coobs_logo_dark.png' : 'assets/images/coobs_logo.png',
    height: height,
    fit: BoxFit.contain,
    errorBuilder: (context, error, stackTrace) => Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          'Coo',
          style: TextStyle(
            color: isDark ? Colors.white : const Color(0xFF000107),
            fontSize: height * 0.72,
            fontWeight: FontWeight.w900,
            letterSpacing: -0.5,
          ),
        ),
        Text(
          'BS',
          style: TextStyle(
            color: const Color(0xFF0091AE),
            fontSize: height * 0.72,
            fontWeight: FontWeight.w900,
            letterSpacing: -0.5,
          ),
        ),
      ],
    ),
  );
}

Widget miniTag(String t) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(color: kInBg, borderRadius: BorderRadius.circular(10)),
    child: Text(t, style: TextStyle(color: kNavyFg, fontSize: 11, fontWeight: FontWeight.w600)));

String _formatTxTitle(String raw, bool isOut) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty || trimmed.startsWith('TX-') || trimmed.startsWith('TXN-')) {
    return isOut ? 'Transfer Out' : 'Received Funds';
  }
  if (trimmed == trimmed.toUpperCase() && trimmed.contains(RegExp(r'[A-Z]'))) {
    final words = trimmed.replaceAll('_', ' ').split(' ');
    return words
        .map((w) => w.isEmpty ? '' : w[0].toUpperCase() + w.substring(1).toLowerCase())
        .join(' ');
  }
  return trimmed;
}

IconData _getTxIcon(String label, bool isOut) {
  final l = label.toLowerCase();
  if (l.contains('food') || l.contains('dining') || l.contains('restaurant') || l.contains('cafe') || l.contains('coffee') || l.contains('burger') || l.contains('lunch') || l.contains('dinner')) {
    return Icons.restaurant_rounded;
  }
  if (l.contains('shop') || l.contains('store') || l.contains('market') || l.contains('grocery') || l.contains('buy') || l.contains('purchase') || l.contains('online')) {
    return Icons.shopping_bag_outlined;
  }
  if (l.contains('bill') || l.contains('utility') || l.contains('electric') || l.contains('water') || l.contains('meralco') || l.contains('telco') || l.contains('wifi') || l.contains('internet')) {
    return Icons.receipt_long_rounded;
  }
  if (l.contains('payroll') || l.contains('salary') || l.contains('wage') || l.contains('stipend')) {
    return Icons.payments_outlined;
  }
  if (l.contains('interest') || l.contains('dividend') || l.contains('yield') || l.contains('growth')) {
    return Icons.trending_up_rounded;
  }
  if (l.contains('convert') || l.contains('exchange') || l.contains('swap') || l.contains('forex')) {
    return Icons.sync_alt_rounded;
  }
  if (l.contains('atm') || l.contains('card') || l.contains('withdraw')) {
    return Icons.credit_card_rounded;
  }
  if (l.contains('top-up') || l.contains('top up') || l.contains('cash in') || l.contains('load') || l.contains('deposit')) {
    return isOut ? Icons.arrow_outward_rounded : Icons.add_circle_outline_rounded;
  }
  return isOut ? Icons.arrow_outward_rounded : Icons.arrow_downward_rounded;
}

Widget txRow(Tx x) {
  final out = x.amt < 0;
  final now = DateTime.now();
  final isToday = x.date.year == now.year && x.date.month == now.month && x.date.day == now.day;
  final title = _formatTxTitle(x.label, out);
  final icon = _getTxIcon(x.label, out);
  final dateStr = isToday ? 'Today' : fmt(x.date);
  final cleanRef = x.ref.isNotEmpty && x.ref != 'TXN' && !x.ref.startsWith('Just now')
      ? (x.ref.contains('·') ? x.ref.split('·').last.trim() : x.ref)
      : '';
  final refDisplay = cleanRef.isNotEmpty
      ? ' · ${cleanRef.length > 14 ? '${cleanRef.substring(0, 13)}…' : cleanRef}'
      : '';

  return Builder(
    builder: (context) => Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: x.ref.isNotEmpty && x.ref != 'TXN' && !x.ref.startsWith('Just now')
            ? () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => Scaffold(
                      backgroundColor: kBg,
                      appBar: AppBar(
                        backgroundColor: kBg,
                        surfaceTintColor: Colors.transparent,
                        foregroundColor: kInk,
                        centerTitle: false,
                        automaticallyImplyLeading: false,
                        elevation: 0,
                        scrolledUnderElevation: 0,
                        toolbarHeight: 58,
                        leadingWidth: 64,
                        leading: const Padding(
                          padding: EdgeInsets.only(left: 16),
                          child: ModernBackButton(),
                        ),
                        titleSpacing: 12,
                        title: Text('Track ${x.ref}', style: TextStyle(fontFamily: 'Poppins', color: kInk, fontSize: 18, fontWeight: FontWeight.w500, letterSpacing: -0.3)),
                      ),
                      body: TrackingView(trackingReference: x.ref),
                    ),
                  ),
                )
            : null,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
          decoration: BoxDecoration(
            color: kDark ? const Color(0xFF0C141F).withAlpha(220) : Colors.white.withAlpha(225),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: kDark ? const Color(0xFF1E293B) : Colors.white.withAlpha(220), width: 0.9),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withAlpha(kDark ? 16 : 6),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: out ? kOutBg : kInBg,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(
                  icon,
                  size: 20,
                  color: out ? (kDark ? const Color(0xFF94A3B8) : const Color(0xFF475569)) : kNavyFg,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
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
                      '$dateStr$refDisplay',
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
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    '${out ? '-' : '+'}${peso(x.amt.abs())}',
                    style: TextStyle(
                      fontFamily: 'Poppins',
                      fontWeight: FontWeight.w500,
                      fontSize: 14,
                      color: out ? kInk : (kDark ? const Color(0xFF00B4D8) : const Color(0xFF059669)),
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
        ),
      ),
    ),
  );
}

Widget txCard(List<Tx> l) => Column(
      children: [
        for (final x in l)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: txRow(x),
          ),
      ],
    );

/// Modern tactile squircle back button for sub-pages and modal navigation.
class ModernBackButton extends StatelessWidget {
  final VoidCallback? onPressed;
  const ModernBackButton({super.key, this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onPressed ?? () => Navigator.maybePop(context),
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: kDark ? const Color(0xFF16202E) : Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: kLine.withAlpha(kDark ? 50 : 160),
                width: 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withAlpha(kDark ? 30 : 8),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            alignment: Alignment.center,
            child: Icon(
              Icons.arrow_back_rounded,
              size: 20,
              color: kInk,
            ),
          ),
        ),
      ),
    );
  }
}

/// Standard sub-page: modern tactile back button, Poppins title, optional chip and actions.
/// With [tab] true it is hosted as a bottom-nav tab: no back arrow.
class Pg extends StatelessWidget {
  final String title;
  final Widget? chip;
  final List<Widget> kids;
  final VoidCallback? onBack;
  final bool tab;
  final List<Widget>? actions;

  const Pg(
    this.title,
    this.kids, {
    super.key,
    this.chip,
    this.onBack,
    this.tab = false,
    this.actions,
  });

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        appBar: AppBar(
          backgroundColor: kBg,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 0,
          toolbarHeight: 58,
          centerTitle: false,
          automaticallyImplyLeading: false,
          leadingWidth: tab ? 0 : 64,
          leading: tab
              ? null
              : Padding(
                  padding: const EdgeInsets.only(left: 16),
                  child: ModernBackButton(
                    onPressed: onBack ?? () => Navigator.pop(context),
                  ),
                ),
          titleSpacing: tab ? 20 : 12,
          title: Row(
            children: [
              Text(
                title,
                style: TextStyle(
                  fontFamily: 'Poppins',
                  color: kInk,
                  fontSize: 18,
                  fontWeight: FontWeight.w500,
                  letterSpacing: -0.3,
                ),
              ),
              if (chip != null) ...[
                const SizedBox(width: 10),
                chip!,
              ],
            ],
          ),
          actions: actions != null
              ? [
                  ...actions!,
                  const SizedBox(width: 8),
                ]
              : null,
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          children: kids,
        ),
      );
}
