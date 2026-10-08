import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/models/models.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/secure_storage_service.dart';
import '../../shell/app_shell.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../auth/auth_screens.dart';

Widget _title(String t) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 8, 20, 10),
    child: Text(t, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)));

Future<void> _handleLogout(BuildContext context) async {
  try {
    await ApiClient().post('/auth/token/revoke');
  } catch (_) {}
  await SecureStorageService().clearSession();
  Demo.i.reset();
  if (context.mounted) {
    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }
}

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

class AlertsScreen extends StatefulWidget {
  const AlertsScreen({super.key});

  @override
  State<AlertsScreen> createState() => _AlertsScreenState();
}

class _AlertsScreenState extends State<AlertsScreen> {
  final ApiClient _apiClient = ApiClient();
  bool _isLoading = true;
  List<NotificationItem> _liveNotifications = [];

  @override
  void initState() {
    super.initState();
    _loadNotifications();
  }

  Future<void> _loadNotifications() async {
    setState(() => _isLoading = true);
    try {
      final json = await _apiClient.get('/notifications/my-notifications');
      final page = NotificationPageResponse.fromJson(json);
      if (mounted) {
        setState(() {
          _liveNotifications = page.content;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _markAsRead(NotificationItem item, int index) async {
    if (item.isRead) return;
    try {
      await _apiClient.patch('/notifications/${item.notificationId}/read');
      if (mounted) {
        setState(() {
          _liveNotifications[index] = item.copyWith(isRead: true);
        });
      }
    } catch (_) {}
  }

  String _formatTimestamp(DateTime? dt) {
    if (dt == null) return 'Recently';
    final diff = DateTime.now().toUtc().difference(dt.toUtc());
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return DateFormat('dd MMM, HH:mm').format(dt.toLocal());
  }

  IconData _iconFor(String title) {
    final t = title.toLowerCase();
    if (t.contains('sent') || t.contains('debited')) return Icons.arrow_upward_rounded;
    if (t.contains('received') || t.contains('credited')) return Icons.arrow_downward_rounded;
    if (t.contains('failed') || t.contains('rejected')) return Icons.error_outline_rounded;
    if (t.contains('kyc') || t.contains('security')) return Icons.shield_outlined;
    return Icons.notifications_none_rounded;
  }

  Color _iconColorFor(String title) {
    final t = title.toLowerCase();
    if (t.contains('sent') || t.contains('debited')) return const Color(0xFFE11D48);
    if (t.contains('received') || t.contains('credited')) return const Color(0xFF16A34A);
    if (t.contains('failed') || t.contains('rejected')) return const Color(0xFFDC2626);
    return kBlue;
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: _loadNotifications,
      child: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          _title('Notifications'),
          if (_isLoading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 40),
              child: Center(
                child: CircularProgressIndicator(color: kNavy),
              ),
            )
          else if (_liveNotifications.isNotEmpty) ...[
            for (var idx = 0; idx < _liveNotifications.length; idx++)
              _buildLiveNotificationCard(_liveNotifications[idx], idx),
          ] else if (Demo.i.alerts.isNotEmpty) ...[
            for (final a in Demo.i.alerts)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
                child: Card(
                  margin: EdgeInsets.zero,
                  child: ListTile(
                    title: Text(a[0], style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    subtitle: Text(a[1], style: const TextStyle(color: kMut, fontSize: 12)),
                  ),
                ),
              ),
          ] else
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.notifications_off_outlined, size: 48, color: kMut.withAlpha(120)),
                    const SizedBox(height: 12),
                    const Text('No notifications yet', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    const SizedBox(height: 4),
                    const Text(
                      'Transaction alerts and updates will appear here.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: kMut, fontSize: 13),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildLiveNotificationCard(NotificationItem item, int index) {
    final icon = _iconFor(item.title);
    final iconColor = _iconColorFor(item.title);

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
      child: Card(
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(
            color: item.isRead ? Colors.transparent : kBlue.withAlpha(50),
            width: 1,
          ),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: () => _markAsRead(item, index),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  radius: 18,
                  backgroundColor: iconColor.withAlpha(30),
                  child: Icon(icon, color: iconColor, size: 18),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              item.title,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: item.isRead ? FontWeight.w600 : FontWeight.bold,
                                color: const Color(0xFF1E293B),
                              ),
                            ),
                          ),
                          if (!item.isRead)
                            Container(
                              width: 8,
                              height: 8,
                              margin: const EdgeInsets.only(left: 6),
                              decoration: const BoxDecoration(
                                color: kBlue,
                                shape: BoxShape.circle,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        item.message,
                        style: TextStyle(
                          fontSize: 12,
                          color: item.isRead ? kMut : const Color(0xFF334155),
                          height: 1.3,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _formatTimestamp(item.createdAt),
                        style: TextStyle(
                          fontSize: 11,
                          color: kMut.withAlpha(180),
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
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
              FutureBuilder<String?>(
                future: SecureStorageService().getUsername(),
                builder: (context, snapshot) {
                  final username = snapshot.data ?? 'john_doe';
                  return ListTile(
                    leading: const CircleAvatar(backgroundColor: Color(0xFFE4EDFF), child: Icon(Icons.person_outline, color: kNavy)),
                    title: Text(username, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: const Text('Customer'),
                  );
                },
              ),
              const Divider(height: 1),
              ListTile(title: const Text('KYC details'), subtitle: const Text('Name, address, occupation'), trailing: const Icon(Icons.chevron_right), onTap: () => go(context, const KycDetailsPage())),
              ListTile(title: const Text('Verify identity'), subtitle: const Text('Upload ID and selfie'), trailing: miniTag('Pending'), onTap: () => go(context, const KycVerifyPage(fromProfile: true))),
              ListTile(title: const Text('Security & tokens'), subtitle: const Text('Password and active sessions'), trailing: const Icon(Icons.chevron_right), onTap: () => go(context, const SecurityPage())),
              ListTile(
                title: const Text('Live Saga & Audit Timeline'),
                subtitle: const Text('Distributed transfers, timeline & resilience'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => go(context, const AppShell()),
              ),
              ListTile(
                  title: const Text('Log out'),
                  onTap: () => _handleLogout(context)),
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
        OutlinedButton(onPressed: () => _handleLogout(context), child: const Text('Sign out all devices')),
      ]);
}
