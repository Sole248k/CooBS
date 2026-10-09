import 'package:flutter/material.dart';

import '../../core/config/app_config.dart';
import '../../core/models/auth_models.dart';
import '../../core/network/api_client.dart';
import '../../core/network/api_exceptions.dart';
import '../../core/storage/secure_storage_service.dart';
import '../accounts/demo_store.dart';
import '../accounts/home_screen.dart';
import '../accounts/ui.dart';

/// Go to the main app (clears the login/register stack).
void toApp(BuildContext c, [String? msg]) {
  final m = ScaffoldMessenger.of(c);
  Navigator.pushAndRemoveUntil(c, MaterialPageRoute(builder: (_) => const MobileShell()), (_) => false);
  if (msg != null) m.showSnackBar(SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating));
}

void _push(BuildContext c, Widget w) => Navigator.push(c, MaterialPageRoute(builder: (_) => w));

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final u = TextEditingController(), p = TextEditingController();
  final ApiClient _apiClient = ApiClient();
  final SecureStorageService _storage = SecureStorageService();
  bool _isLoading = false;
  int _failedAttempts = 0;
  DateTime? _lockoutUntil;

  Future<void> _handleLogin() async {
    if (_lockoutUntil != null && DateTime.now().isBefore(_lockoutUntil!)) {
      final remaining = _lockoutUntil!.difference(DateTime.now()).inSeconds;
      snack(context, 'Security lock active: Please wait $remaining seconds before trying again.');
      return;
    }

    final username = u.text.trim();
    final password = p.text;
    if (username.isEmpty || password.isEmpty) {
      snack(context, 'Enter your username and password');
      return;
    }

    setState(() => _isLoading = true);
    try {
      final response = await _apiClient.post(
        '/auth/customers/login',
        body: {'username': username, 'password': password},
      );
      final token = TokenResponse.fromJson(response);
      if (token.accessToken.isEmpty) {
        throw UnauthorizedException('Invalid login credentials received');
      }
      _failedAttempts = 0;
      _lockoutUntil = null;
      await _storage.saveTokens(
        accessToken: token.accessToken,
        refreshToken: token.refreshToken,
        userId: token.userId > 0 ? token.userId : 1,
        username: username,
      );
      // Immediately load live Customer 360 data from Docker for this newly authenticated user
      await Demo.i.load();
      if (mounted) {
        toApp(context, 'Welcome back, $username');
      }
    } catch (e) {
      _failedAttempts++;
      if (_failedAttempts >= 5) {
        _lockoutUntil = DateTime.now().add(const Duration(seconds: 30));
        _failedAttempts = 0;
        if (mounted) {
          snack(context, 'Account security shield: 5 failed attempts. Please wait 30 seconds.');
        }
      } else if (mounted) {
        final message = e is ApiException ? e.message : 'Authentication failed. Please verify your credentials.';
        snack(context, message);
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showServerSettings() {
    final c = TextEditingController(text: AppConfig.gatewayBaseUrl);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + MediaQuery.of(ctx).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Backend Gateway URL', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            const Text('Configure the Spring Cloud Gateway address running in Docker.', style: TextStyle(color: kMut, fontSize: 13)),
            const SizedBox(height: 14),
            tf(c, 'Gateway Base URL (e.g. http://192.168.254.126:8080)'),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => c.text = 'http://10.0.2.2:8080',
                    child: const Text('Emulator (10.0.2.2)'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => c.text = 'http://localhost:8080',
                    child: const Text('Localhost:8080'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            bigBtn('Save & Apply', () async {
              final newUrl = c.text.trim();
              if (newUrl.isNotEmpty) {
                AppConfig.customRuntimeGatewayUrl = newUrl;
                await _storage.saveGatewayUrl(newUrl);
                if (ctx.mounted) Navigator.pop(ctx);
                if (mounted) snack(context, 'Gateway updated to $newUrl');
              }
            }),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: ListView(padding: const EdgeInsets.fromLTRB(22, 30, 22, 30), children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    logoBox(),
                    IconButton(
                      tooltip: 'Server Connection',
                      icon: const Icon(Icons.dns_outlined, color: kNavy),
                      onPressed: _showServerSettings,
                    ),
                  ],
                ),
                const SizedBox(height: 30),
                const Text('Welcome back', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
                const Padding(padding: EdgeInsets.only(top: 4, bottom: 20), child: Text('Log in to see your accounts.', style: TextStyle(color: kMut))),
                tf(u, 'Username'),
                tf(p, 'Password', obscure: true),
                Align(alignment: Alignment.centerLeft, child: TextButton(onPressed: () => _push(context, const ForgotPage()), child: const Text('Forgot password?'))),
                _isLoading
                    ? const Center(child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator()))
                    : bigBtn('Log in', _handleLogin),
                const SizedBox(height: 10),
                OutlinedButton(onPressed: () => _push(context, const RegisterPage()), child: const Text('Create account')),
              ]),
            ),
          ),
        ),
      );
}

class ForgotPage extends StatelessWidget {
  const ForgotPage({super.key});
  @override
  Widget build(BuildContext context) {
    final e = TextEditingController();
    return Pg('Forgot password', [
      const Padding(padding: EdgeInsets.only(bottom: 16), child: Text("Enter your email and we'll send a reset link.", style: TextStyle(color: kMut))),
      tf(e, 'Email', k: TextInputType.emailAddress),
      bigBtn('Send reset link', () {
        final m = ScaffoldMessenger.of(context);
        Navigator.pop(context);
        m.showSnackBar(const SnackBar(content: Text('Reset link sent. Check your email.'), behavior: SnackBarBehavior.floating));
      }),
    ]);
  }
}

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key});
  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final c = <String, TextEditingController>{};
  bool terms = false;
  TextEditingController t(String k) => c.putIfAbsent(k, TextEditingController.new);
  String s(String k) => t(k).text.trim();

  void submit() {
    if (s('first').isEmpty || s('last').isEmpty || s('user').isEmpty || t('pw').text.isEmpty) return snack(context, 'Fill in your name, username and password');
    if (t('pw').text != t('pw2').text) return snack(context, 'Passwords do not match');
    if (!terms) return snack(context, 'Please accept the Terms & Conditions');
    _push(context, const KycVerifyPage());
  }

  @override
  Widget build(BuildContext context) => Pg('Create account', [
        const Padding(padding: EdgeInsets.only(bottom: 14), child: Text('Step 1 of 2: your basic details.', style: TextStyle(color: kMut))),
        tf(t('first'), 'First name'),
        tf(t('mid'), 'Middle name'),
        tf(t('last'), 'Last name'),
        tf(t('bday'), 'Birthday (dd/mm/yyyy)', k: TextInputType.datetime),
        tf(t('user'), 'Username'),
        tf(t('mail'), 'Email', k: TextInputType.emailAddress),
        tf(t('mob'), 'Mobile number', k: TextInputType.phone),
        tf(t('pw'), 'Password', obscure: true),
        tf(t('pw2'), 'Confirm password', obscure: true),
        CheckboxListTile(
            contentPadding: EdgeInsets.zero, controlAffinity: ListTileControlAffinity.leading, value: terms,
            onChanged: (x) => setState(() => terms = x ?? false), title: const Text('I accept the Terms & Conditions')),
        bigBtn('Continue to identity check', submit),
      ]);
}

/// KYC identity check. Used after registering and from Profile.
class KycVerifyPage extends StatefulWidget {
  final bool fromProfile;
  const KycVerifyPage({super.key, this.fromProfile = false});
  @override
  State<KycVerifyPage> createState() => _KycVerifyPageState();
}

class _KycVerifyPageState extends State<KycVerifyPage> {
  String? idType = 'Passport';

  Widget upload(String label, String hint) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 12, color: kMut)),
          const SizedBox(height: 6),
          OutlinedButton(
              onPressed: () => snack(context, 'Upload is a placeholder'),
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(84)),
              child: Text(hint)),
        ]),
      );

  void done() {
    const msg = 'Submitted. KYC status: Pending review.';
    if (widget.fromProfile) {
      final m = ScaffoldMessenger.of(context);
      Navigator.pop(context);
      m.showSnackBar(const SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating));
    } else {
      toApp(context, msg);
    }
  }

  @override
  Widget build(BuildContext context) => Pg('Verify your identity', [
        const Padding(padding: EdgeInsets.only(bottom: 14), child: Text('A teller reviews this before your accounts open.', style: TextStyle(color: kMut))),
        dd('ID type', const ['Passport', "Driver's license", 'UMID', 'National ID'], idType, (x) => idType = x),
        upload('ID front', 'Tap to upload'),
        upload('ID back', 'Tap to upload'),
        upload('Selfie holding your ID', 'Open camera'),
        bigBtn('Submit for review', done),
        if (!widget.fromProfile) TextButton(onPressed: () => toApp(context), child: const Text('Do this later')),
      ], chip: widget.fromProfile ? null : const Text('2 / 2', style: TextStyle(color: kBlue, fontWeight: FontWeight.w700)));
}
