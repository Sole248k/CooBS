import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/models/models.dart';
import '../../core/network/api_client.dart';
import '../../core/session/biometric_authenticator.dart';
import '../../core/storage/secure_storage_service.dart';
import '../../core/theme/theme_controller.dart';
import '../../shell/app_shell.dart';
import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../auth/auth_screens.dart';
import '../kyc/kyc_intake_screen.dart';

Widget _title(String t) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 12, 20, 10),
    child: Text(
      t,
      style: TextStyle(
        fontFamily: 'Poppins',
        fontSize: 18,
        fontWeight: FontWeight.w500,
        color: kInk,
        letterSpacing: -0.3,
      ),
    ));

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

/// Dummy Pay QR screen (no camera yet). Currently not in the mobile navbar; kept for a later phase.
class PayQrScreen extends StatelessWidget {
  const PayQrScreen({super.key});
  @override
  Widget build(BuildContext context) => ListView(children: [
        _title('Pay QR'),
        Padding(
          padding: const EdgeInsets.all(20),
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 20),
            decoration: BoxDecoration(color: kSurface, borderRadius: BorderRadius.circular(16)),
            child: Column(children: [
              Container(
                  width: 200, height: 200,
                  decoration: BoxDecoration(border: Border.all(color: kLine, width: 3), borderRadius: BorderRadius.circular(14)),
                  child: Icon(Icons.qr_code_scanner, size: 80, color: kInk)),
              const SizedBox(height: 20),
              Text('Scan a QR code to pay from the selected account.', textAlign: TextAlign.center, style: TextStyle(color: kMut)),
              const SizedBox(height: 6),
              Text('Placeholder screen', style: TextStyle(color: kMut, fontSize: 12)),
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
    if (!_isLoading && mounted) {
      setState(() => _isLoading = true);
    }
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
    return ListenableBuilder(
      listenable: ThemeController.i,
      builder: (context, _) => RefreshIndicator(
        onRefresh: _loadNotifications,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(0, 10, 0, 24),
          children: [
            if (_isLoading)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 40),
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
                      title: Text(a[0], style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: kInk)),
                      subtitle: Text(a[1], style: TextStyle(color: kMut, fontSize: 12)),
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
                      Text('No notifications yet', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: kInk)),
                      const SizedBox(height: 4),
                      Text(
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
                                color: kInk,
                              ),
                            ),
                          ),
                          if (!item.isRead)
                            Container(
                              width: 8,
                              height: 8,
                              margin: const EdgeInsets.only(left: 6),
                              decoration: BoxDecoration(
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
                          color: item.isRead ? kMut : (kDark ? const Color(0xFFCBD5E1) : const Color(0xFF334155)),
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

class ProfileScreen extends StatefulWidget {
  /// Injected in tests; the screen builds its own otherwise.
  final SecureStorageService? storage;
  final BiometricAuthenticator? biometrics;
  const ProfileScreen({super.key, this.storage, this.biometrics});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late final SecureStorageService _storage = widget.storage ?? SecureStorageService();
  late final BiometricAuthenticator _biometrics = widget.biometrics ?? LocalBiometricAuthenticator();
  late final Future<String?> _usernameFuture;
  bool _bioAvailable = false, _bioOn = false;

  @override
  void initState() {
    super.initState();
    _usernameFuture = _storage.getUsername();
    _loadBiometric();
  }

  Future<void> _loadBiometric() async {
    final available = await _biometrics.isAvailable();
    final bioUser = await _storage.getBiometricUsername();
    final username = await _storage.getUsername();
    if (!mounted) return;
    setState(() {
      _bioAvailable = available;
      _bioOn = bioUser != null && bioUser == username;
    });
  }

  Future<void> _setBiometric(bool on) async {
    if (!on) {
      await _storage.disableBiometricLogin();
      if (mounted) setState(() => _bioOn = false);
      return;
    }
    final username = await _storage.getUsername();
    final refreshToken = await _storage.getRefreshToken();
    if (username == null || refreshToken == null || refreshToken.isEmpty) {
      if (mounted) snack(context, 'Log in again with your password to turn on fingerprint login.');
      return;
    }
    if (!await _biometrics.authenticate('Confirm your fingerprint to turn on fingerprint login')) return;
    await _storage.enableBiometricLogin(username: username, refreshToken: refreshToken);
    if (mounted) setState(() => _bioOn = true);
  }

  void go(BuildContext c, Widget w) => Navigator.push(c, MaterialPageRoute(builder: (_) => w));

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: ThemeController.i,
        builder: (context, _) => ListView(children: [
          _title('Profile & settings'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Card(
              child: Column(children: [
                FutureBuilder<String?>(
                  future: _usernameFuture,
                  builder: (context, snapshot) {
                    final username = snapshot.data ?? 'john_doe';
                    return ListTile(
                      leading: CircleAvatar(backgroundColor: kInBg, child: Icon(Icons.person_outline, color: kNavyFg)),
                      title: Text(username, style: TextStyle(fontWeight: FontWeight.w600, color: kInk)),
                      subtitle: Text('Customer', style: TextStyle(color: kMut)),
                    );
                  },
                ),
                const Divider(height: 1),
                ListTile(title: Text('KYC details', style: TextStyle(color: kInk)), subtitle: Text('Name, address, occupation', style: TextStyle(color: kMut)), trailing: Icon(Icons.chevron_right, color: kMut), onTap: () => go(context, const KycDetailsPage())),
                ListTile(
                    title: Text('Verify identity', style: TextStyle(color: kInk)),
                    subtitle: Text('Submit your details for review', style: TextStyle(color: kMut)),
                    trailing: AnimatedBuilder(animation: Demo.i, builder: (context, _) => kycChip(Demo.i.kyc)),
                    onTap: () => go(context, const KycIntakeScreen())),
                ListTile(title: Text('Security & tokens', style: TextStyle(color: kInk)), subtitle: Text('Password and active sessions', style: TextStyle(color: kMut)), trailing: Icon(Icons.chevron_right, color: kMut), onTap: () => go(context, const SecurityPage())),
                ListTile(
                  title: Text('Live Saga & Audit Timeline', style: TextStyle(color: kInk)),
                  subtitle: Text('Distributed transfers, timeline & resilience', style: TextStyle(color: kMut)),
                  trailing: Icon(Icons.chevron_right, color: kMut),
                  onTap: () => go(context, const AppShell()),
                ),
                SwitchListTile(
                  secondary: const Icon(Icons.dark_mode_outlined),
                  title: Text('Dark mode', style: TextStyle(color: kInk)),
                  subtitle: Text(ThemeController.i.dark ? 'On' : 'Off', style: TextStyle(color: kMut)),
                  value: ThemeController.i.dark,
                  activeThumbColor: Colors.white,
                  activeTrackColor: kBlue,
                  inactiveThumbColor: Colors.white,
                  inactiveTrackColor: const Color(0xFFC7CDD3),
                  trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
                  onChanged: ThemeController.i.setDark,
                ),
                if (_bioAvailable)
                  SwitchListTile(
                    secondary: const Icon(Icons.fingerprint),
                    title: Text('Fingerprint login', style: TextStyle(color: kInk)),
                    subtitle: Text(_bioOn ? 'On' : 'Off', style: TextStyle(color: kMut)),
                    value: _bioOn,
                    activeThumbColor: Colors.white,
                    activeTrackColor: kBlue,
                    inactiveThumbColor: Colors.white,
                    inactiveTrackColor: const Color(0xFFC7CDD3),
                    trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
                    onChanged: _setBiometric,
                  ),
                ListTile(
                    title: Text('Log out', style: TextStyle(color: kRed)),
                    onTap: () => _handleLogout(context)),
              ]),
            ),
          ),
          Padding(padding: const EdgeInsets.fromLTRB(20, 20, 20, 6), child: Text('Tester tools', style: TextStyle(fontSize: 12, color: kMut))),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
            child: Card(
              child: Column(children: [
                ListTile(title: Text('Reload data', style: TextStyle(color: kInk)), onTap: () => Demo.i.simulate(null)),
                ListTile(title: Text('Simulate partial failure', style: TextStyle(color: kInk)), subtitle: Text('Recent transactions unavailable', style: TextStyle(color: kMut)), onTap: () => Demo.i.simulate('partial')),
                ListTile(title: Text('Simulate service unavailable', style: TextStyle(color: kInk)), onTap: () => Demo.i.simulate('offline')),
                ListTile(title: Text('Reset KYC to Unverified', style: TextStyle(color: kInk)), onTap: () => Demo.i.setKyc('UNVERIFIED')),
              ]),
            ),
          ),
        ]),
      );
}

class KycDetailsPage extends StatefulWidget {
  const KycDetailsPage({super.key});
  @override
  State<KycDetailsPage> createState() => _KycDetailsPageState();
}

class _KycDetailsPageState extends State<KycDetailsPage> {
  final _apiClient = ApiClient();
  bool _isLoading = true;
  bool _isSubmitting = false;
  String _kycStatus = 'UNVERIFIED';

  final _firstCtrl = TextEditingController();
  final _midCtrl = TextEditingController();
  final _lastCtrl = TextEditingController();
  final _addrCtrl = TextEditingController();
  final _occCtrl = TextEditingController();
  final _mobCtrl = TextEditingController();
  String? civil = 'Single';

  @override
  void initState() {
    super.initState();
    _loadProfile();
  }

  @override
  void dispose() {
    _firstCtrl.dispose();
    _midCtrl.dispose();
    _lastCtrl.dispose();
    _addrCtrl.dispose();
    _occCtrl.dispose();
    _mobCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadProfile() async {
    if (!_isLoading && mounted) {
      setState(() => _isLoading = true);
    }
    try {
      final json = await _apiClient.get('/customers/me');
      final profile = CustomerProfileData.fromJson(json);
      if (mounted) {
        setState(() {
          _kycStatus = profile.kycStatus;
          if (profile.kyc != null) {
            _firstCtrl.text = profile.kyc!.firstName;
            _midCtrl.text = profile.kyc!.middleInitial;
            _lastCtrl.text = profile.kyc!.lastName;
            _addrCtrl.text = profile.kyc!.address;
            _occCtrl.text = profile.kyc!.occupation;
            _mobCtrl.text = profile.kyc!.mobileNumber;
            final rawCivil = profile.kyc!.civilStatus.toUpperCase();
            if (rawCivil == 'SINGLE') {
              civil = 'Single';
            } else if (rawCivil == 'MARRIED') {
              civil = 'Married';
            } else if (rawCivil == 'WIDOWED') {
              civil = 'Widowed';
            } else if (rawCivil == 'DIVORCED') {
              civil = 'Divorced';
            } else {
              civil = 'Single';
            }
          } else {
            final parts = profile.displayName.split(' ');
            if (parts.isNotEmpty) _firstCtrl.text = parts.first;
            if (parts.length > 1) _lastCtrl.text = parts.last;
            civil = 'Single';
          }
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _submitUpdateRequest() async {
    setState(() => _isSubmitting = true);
    try {
      final payload = {
        'newFirstName': _firstCtrl.text.trim(),
        'newMiddleInitial': _midCtrl.text.trim(),
        'newLastName': _lastCtrl.text.trim(),
        'newAddress': _addrCtrl.text.trim(),
        'newOccupation': _occCtrl.text.trim(),
        'newMobileNumber': _mobCtrl.text.trim(),
        'newCivilStatus': civil ?? 'Single',
      };

      await _apiClient.post(
        '/customers/kyc/update-request',
        body: payload,
      );

      if (mounted) {
        snack(context, 'KYC update request submitted for teller approval');
        setState(() => _isSubmitting = false);
      }
    } catch (e) {
      if (mounted) {
        snack(context, 'Failed to submit update: ${e.toString().replaceAll("Exception: ", "")}');
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Pg('KYC details', [
        if (_isLoading)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 40),
            child: Center(child: CircularProgressIndicator(color: kNavy)),
          )
        else ...[
          Row(children: [
            miniTag(_kycStatus),
            const SizedBox(width: 8),
            Flexible(child: Text('Changes need teller approval.', style: TextStyle(color: kMut, fontSize: 12))),
          ]),
          const SizedBox(height: 14),
          tf(_firstCtrl, 'First name'),
          tf(_midCtrl, 'M.I.'),
          tf(_lastCtrl, 'Last name'),
          tf(_addrCtrl, 'Address'),
          dd('Civil status', const ['Single', 'Married', 'Widowed', 'Divorced'], civil, (x) => civil = x),
          tf(_occCtrl, 'Occupation'),
          tf(_mobCtrl, 'Mobile number', k: TextInputType.phone),
          _isSubmitting
              ? Padding(
                  padding: const EdgeInsets.only(top: 20),
                  child: Center(child: CircularProgressIndicator(color: kNavy)),
                )
              : bigBtn('Request update', _submitUpdateRequest),
        ],
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
        Padding(padding: EdgeInsets.only(bottom: 12), child: Text('Manage your password and signed-in devices.', style: TextStyle(color: kMut))),
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

  @override
  void dispose() {
    cur.dispose();
    nw.dispose();
    super.dispose();
  }
}
