import 'package:flutter/material.dart';

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

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: kBg,
        body: SafeArea(
          child: ListView(padding: const EdgeInsets.fromLTRB(22, 30, 22, 30), children: [
            Align(alignment: Alignment.centerLeft, child: logoBox()),
            const SizedBox(height: 30),
            const Text('Welcome back', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
            const Padding(padding: EdgeInsets.only(top: 4, bottom: 20), child: Text('Log in to see your accounts.', style: TextStyle(color: kMut))),
            tf(u, 'Username'),
            tf(p, 'Password', obscure: true),
            Align(alignment: Alignment.centerLeft, child: TextButton(onPressed: () => _push(context, const ForgotPage()), child: const Text('Forgot password?'))),
            bigBtn('Log in', () => u.text.trim().isEmpty || p.text.isEmpty ? snack(context, 'Enter your username and password') : toApp(context)),
            const SizedBox(height: 10),
            OutlinedButton(onPressed: () => _push(context, const RegisterPage()), child: const Text('Create account')),
          ]),
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
