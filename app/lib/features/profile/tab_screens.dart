import 'package:flutter/material.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../auth/auth_screens.dart';

Widget _title(String t) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 8, 20, 10),
    child: Text(t, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)));

/// Dummy Pay QR screen (no camera yet).
class PayQrScreen extends StatelessWidget {
  const PayQrScreen({super.key});
  @override
  Widget build(BuildContext context) => ListView(children: [
        _title('Pay QR'),
        Padding(
          padding: const EdgeInsets.all(20),
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 20),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)),
            child: Column(children: [
              Container(
                  width: 200, height: 200,
                  decoration: BoxDecoration(border: Border.all(width: 3), borderRadius: BorderRadius.circular(14)),
                  child: const Icon(Icons.qr_code_scanner, size: 80)),
              const SizedBox(height: 20),
              const Text('Scan a QR code to pay from the selected account.', textAlign: TextAlign.center, style: TextStyle(color: kMut)),
              const SizedBox(height: 6),
              const Text('Placeholder screen', style: TextStyle(color: kMut, fontSize: 12)),
            ]),
          ),
        ),
      ]);
}

class AlertsScreen extends StatelessWidget {
  const AlertsScreen({super.key});
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: Demo.i,
        builder: (context, _) => ListView(padding: const EdgeInsets.only(bottom: 16), children: [
          _title('Notifications'),
          for (final a in Demo.i.alerts)
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
              child: Card(margin: EdgeInsets.zero, child: ListTile(title: Text(a[0], style: const TextStyle(fontSize: 13)), subtitle: Text(a[1]))),
            ),
        ]),
      );
}

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});
  void go(BuildContext c, Widget w) => Navigator.push(c, MaterialPageRoute(builder: (_) => w));

  @override
  Widget build(BuildContext context) => ListView(children: [
        _title('Profile & settings'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Card(
            child: Column(children: [
              const ListTile(
                  leading: CircleAvatar(backgroundColor: Color(0xFFE4EDFF), child: Icon(Icons.person_outline, color: kNavy)),
                  title: Text('john_doe', style: TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('Customer')),
              const Divider(height: 1),
              ListTile(title: const Text('KYC details'), subtitle: const Text('Name, address, occupation'), trailing: const Icon(Icons.chevron_right), onTap: () => go(context, const KycDetailsPage())),
              ListTile(title: const Text('Verify identity'), subtitle: const Text('Upload ID and selfie'), trailing: miniTag('Pending'), onTap: () => go(context, const KycVerifyPage(fromProfile: true))),
              ListTile(title: const Text('Security & tokens'), subtitle: const Text('Password and active sessions'), trailing: const Icon(Icons.chevron_right), onTap: () => go(context, const SecurityPage())),
              ListTile(
                  title: const Text('Log out'),
                  onTap: () => Navigator.pushAndRemoveUntil(context, MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false)),
            ]),
          ),
        ),
      ]);
}

class KycDetailsPage extends StatefulWidget {
  const KycDetailsPage({super.key});
  @override
  State<KycDetailsPage> createState() => _KycDetailsPageState();
}

class _KycDetailsPageState extends State<KycDetailsPage> {
  final c = {
    'First name': TextEditingController(text: 'John'),
    'M.I.': TextEditingController(text: 'D'),
    'Last name': TextEditingController(text: 'Doe'),
    'Address': TextEditingController(text: 'Quezon City, Metro Manila'),
    'Occupation': TextEditingController(text: 'Software engineer'),
    'Mobile number': TextEditingController(text: '+63 917 000 0000'),
  };
  String? civil = 'Single';

  @override
  Widget build(BuildContext context) => Pg('KYC details', [
        Row(children: [miniTag('Verified'), const SizedBox(width: 8), const Flexible(child: Text('Changes need teller approval.', style: TextStyle(color: kMut, fontSize: 12)))]),
        const SizedBox(height: 14),
        for (final e in c.entries.take(4)) tf(e.value, e.key),
        dd('Civil status', const ['Single', 'Married'], civil, (x) => civil = x),
        for (final e in c.entries.skip(4)) tf(e.value, e.key),
        bigBtn('Request update', () => snack(context, 'Update request sent for approval.')),
      ]);
}

class SecurityPage extends StatefulWidget {
  const SecurityPage({super.key});
  @override
  State<SecurityPage> createState() => _SecurityPageState();
}

class _SecurityPageState extends State<SecurityPage> {
  final cur = TextEditingController(), nw = TextEditingController();

  @override
  Widget build(BuildContext context) => Pg('Security & tokens', [
        const Padding(padding: EdgeInsets.only(bottom: 12), child: Text('Manage your password and signed-in devices.', style: TextStyle(color: kMut))),
        Card(
          child: Column(children: [
            ListTile(title: const Text('Access token'), subtitle: const Text('Expires in 14 min · auto-refreshes'), trailing: miniTag('Active')),
            ListTile(title: const Text('This phone'), subtitle: const Text('Quezon City · now'), trailing: miniTag('Current')),
            ListTile(
                title: const Text('Web dashboard'),
                subtitle: const Text('Chrome · 2 hours ago'),
                trailing: TextButton(onPressed: () => snack(context, 'Session revoked.'), child: const Text('Revoke'))),
          ]),
        ),
        const SizedBox(height: 16),
        tf(cur, 'Current password', obscure: true),
        tf(nw, 'New password', obscure: true),
        bigBtn('Change password', () => snack(context, 'Password updated.')),
        const SizedBox(height: 10),
        OutlinedButton(onPressed: () => snack(context, 'Signed out of all devices.'), child: const Text('Sign out all devices')),
      ]);
}
