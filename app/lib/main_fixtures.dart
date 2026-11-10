import 'package:flutter/material.dart';

import 'core/theme/app_theme.dart';
import 'features/accounts/home_screen.dart';
import 'features/accounts/ui.dart';
import 'features/kyc/kyc_intake_screen.dart';
import 'features/remittance/remittance_screen.dart';

/// Isolated mobile screens, each running only on assets/fixtures/ (FIN-702).
/// Run with:  flutter run -t lib/main_fixtures.dart
void main() => runApp(MaterialApp(
      title: 'CooBS Fixture Screens',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: const FixtureMenu(),
    ));

class FixtureMenu extends StatelessWidget {
  const FixtureMenu({super.key});

  Widget item(BuildContext c, String t, String fixtures, Widget w) => Card(
        child: ListTile(
          title: Text(t, style: const TextStyle(fontWeight: FontWeight.w700)),
          subtitle: Text(fixtures, style: const TextStyle(fontSize: 12)),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.push(c, MaterialPageRoute(builder: (_) => w)),
        ),
      );

  @override
  Widget build(BuildContext context) => Pg('Fixture screens', [
        Padding(padding: EdgeInsets.only(bottom: 12), child: Text('Each screen runs on mock JSON only.', style: TextStyle(color: kMut))),
        item(context, 'Customer 360 (mobile home)', 'M1, K5, K6, N1', const MobileShell()),
        item(context, 'Remittance Transfer', 'K5, R1, M3, M2, E1, E2', const RemittanceScreen()),
        item(context, 'Simulated KYC Intake', 'K1, K2 (pending, verified, rejected)', const KycIntakeScreen()),
      ], onBack: () {});
}
