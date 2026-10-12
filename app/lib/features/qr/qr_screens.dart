import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:gal/gal.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';

import '../accounts/demo_store.dart';
import '../accounts/ui.dart';
import '../remittance/remittance_screen.dart';
import 'qr_payload.dart';

/// Entry point from the center nav button: receive (show my QR) or scan to pay.
class QrHomeScreen extends StatelessWidget {
  final int from;
  const QrHomeScreen({super.key, this.from = 0});

  @override
  Widget build(BuildContext context) {
    void go(Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => w));
    return Pg('QR Transactions', [
      Padding(
        padding: const EdgeInsets.only(top: 4, bottom: 16),
        child: Text('What would you like to do?', style: TextStyle(fontSize: 14, color: kMut)),
      ),
      _ChoiceCard(
        icon: Icons.qr_code_2_rounded,
        title: 'Receive money',
        body: 'Show your QR code. The other person scans it to pay you. You can set an amount or leave it to them.',
        filled: false,
        onTap: () => go(QrReceiveScreen(from: from)),
      ),
      const SizedBox(height: 16),
      _ChoiceCard(
        icon: Icons.qr_code_scanner_rounded,
        title: 'Scan to pay',
        body: "Scan someone's CooBS QR code. We fill in their account number, and the amount if they set one.",
        filled: true,
        onTap: () => go(QrScanScreen(from: from)),
      ),
      const SizedBox(height: 24),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(color: kOutBg, borderRadius: BorderRadius.circular(16)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.shield_outlined, size: 20, color: kMut),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'A CooBS QR holds only an account number and, if set, an amount. No money moves until you check the details and tap Send transfer.',
              style: TextStyle(fontSize: 12, height: 1.55, color: kMut),
            ),
          ),
        ]),
      ),
    ]);
  }
}

class _ChoiceCard extends StatelessWidget {
  final IconData icon;
  final String title, body;
  final bool filled;
  final VoidCallback onTap;
  const _ChoiceCard({required this.icon, required this.title, required this.body, required this.filled, required this.onTap});

  @override
  Widget build(BuildContext context) => Material(
        color: filled ? kNavy : kSurface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(22),
          side: filled ? BorderSide.none : BorderSide(color: kLine),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(22),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(color: filled ? kBlue : kInBg, borderRadius: BorderRadius.circular(16)),
                  child: Icon(icon, size: 28, color: filled ? Colors.white : kNavyFg),
                ),
                Icon(Icons.chevron_right_rounded, color: kFaint),
              ]),
              const SizedBox(height: 18),
              Text(title, style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: filled ? Colors.white : kInk)),
              const SizedBox(height: 4),
              Text(body, style: TextStyle(fontSize: 13, height: 1.5, color: filled ? const Color(0xFFCBD5E1) : kMut)),
            ]),
          ),
        ),
      );
}

/// Receive: a QR for one of the member's own accounts and an optional amount.
class QrReceiveScreen extends StatefulWidget {
  final int from;
  const QrReceiveScreen({super.key, this.from = 0});
  @override
  State<QrReceiveScreen> createState() => _QrReceiveScreenState();
}

class _QrReceiveScreenState extends State<QrReceiveScreen> {
  static const _maxCentavos = 50000000;
  late int acct = widget.from < Demo.i.accts.length ? widget.from : 0;
  final amt = TextEditingController();
  final _qrKey = GlobalKey();
  bool busy = false;

  @override
  void dispose() {
    amt.dispose();
    super.dispose();
  }

  int? get _cents {
    var t = amt.text.trim();
    if (t.endsWith('.')) t = t.substring(0, t.length - 1);
    final c = centavos(t);
    return c != null && c > 0 ? c : null;
  }

  bool get _tooBig => (_cents ?? 0) > _maxCentavos;

  String get _fileName {
    final c = _cents;
    return 'CooBS_QR_${Demo.i.accts[acct].no}${c == null ? '' : '_${two(c).replaceAll('.', '-')}'}';
  }

  Future<Uint8List?> _png() async {
    final b = _qrKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
    if (b == null) return null;
    final img = await b.toImage(pixelRatio: 3);
    final data = await img.toByteData(format: ui.ImageByteFormat.png);
    return data?.buffer.asUint8List();
  }

  Future<void> _save() async {
    if (busy) return;
    setState(() => busy = true);
    try {
      if (!await Gal.hasAccess() && !await Gal.requestAccess()) {
        if (mounted) snack(context, 'Allow photo access in Settings to save your QR');
        return;
      }
      final png = await _png();
      if (png == null) return;
      await Gal.putImageBytes(png, name: _fileName);
      if (mounted) snack(context, 'QR saved to your photos');
    } on GalException catch (e) {
      if (mounted) {
        snack(context, e.type == GalExceptionType.accessDenied
            ? 'Allow photo access in Settings to save your QR'
            : 'Could not save the QR. Please try again.');
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _share() async {
    if (busy) return;
    setState(() => busy = true);
    try {
      final png = await _png();
      if (png == null) return;
      await SharePlus.instance.share(ShareParams(
        files: [XFile.fromData(png, mimeType: 'image/png')],
        fileNameOverrides: ['$_fileName.png'],
        text: 'Scan this in the CooBS app to pay me.',
      ));
    } catch (_) {
      if (mounted) snack(context, 'Could not share the QR. Please try again.');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final accts = Demo.i.accts;
    if (accts.isEmpty) {
      return const Pg('Receive money', [Padding(padding: EdgeInsets.all(30), child: Text('No accounts available to receive into.'))]);
    }
    final a = accts[acct < accts.length ? acct : 0];
    final cents = _tooBig ? null : _cents;
    final payload = encodeQrPayment(QrPayment(a.no, cents));
    const ink = Color(0xFF000107);

    return Pg('Receive money', [
      ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: RepaintBoundary(
          key: _qrKey,
          child: Container(
            color: Colors.white,
            padding: const EdgeInsets.all(20),
            child: Column(children: [
              QrImageView(
                data: payload,
                size: 210,
                padding: const EdgeInsets.all(6),
                backgroundColor: Colors.white,
                errorCorrectionLevel: QrErrorCorrectLevel.M,
                eyeStyle: const QrEyeStyle(eyeShape: QrEyeShape.square, color: ink),
                dataModuleStyle: const QrDataModuleStyle(dataModuleShape: QrDataModuleShape.square, color: ink),
                semanticsLabel: 'QR code to pay ${a.no}${cents == null ? '' : ' ${money(cents)}'}',
              ),
              const SizedBox(height: 12),
              Text('${a.type} account', style: const TextStyle(fontSize: 12, color: Color(0xFF64748B))),
              Text(a.no, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, fontFamily: 'monospace', color: ink)),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                decoration: BoxDecoration(
                  color: cents == null ? const Color(0xFFF1F5F9) : const Color(0xFFE0F4F8),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text(
                  cents == null ? 'Any amount · payer enters it' : 'Requesting ${money(cents)}',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: cents == null ? const Color(0xFF475569) : const Color(0xFF006B82),
                  ),
                ),
              ),
            ]),
          ),
        ),
      ),
      const SizedBox(height: 18),
      Text('Receive into', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: kMut)),
      const SizedBox(height: 8),
      Row(children: [
        for (var i = 0; i < accts.length; i++) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(child: _AcctChip(accts[i], i == acct, () => setState(() => acct = i))),
        ],
      ]),
      const SizedBox(height: 18),
      TextField(
        controller: amt,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        inputFormatters: [
          TextInputFormatter.withFunction((o, n) => RegExp(r'^\d{0,9}(\.\d{0,2})?$').hasMatch(n.text) ? n : o),
        ],
        onChanged: (_) => setState(() {}),
        decoration: InputDecoration(
          labelText: 'Amount (optional)',
          prefixText: '₱ ',
          hintText: 'Leave blank for any amount',
          errorText: _tooBig ? 'Maximum per transfer is ${money(_maxCentavos)}' : null,
          suffixIcon: amt.text.isEmpty
              ? null
              : IconButton(
                  tooltip: 'Clear amount',
                  icon: const Icon(Icons.close_rounded),
                  onPressed: () => setState(amt.clear),
                ),
        ),
      ),
      const SizedBox(height: 18),
      Row(children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: busy || _tooBig ? null : _save,
            icon: const Icon(Icons.download_rounded, size: 18),
            label: const Text('Save'),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: ElevatedButton.icon(
            onPressed: busy || _tooBig ? null : _share,
            style: ElevatedButton.styleFrom(backgroundColor: kBlue, foregroundColor: Colors.white),
            icon: const Icon(Icons.share_rounded, size: 18),
            label: const Text('Share'),
          ),
        ),
      ]),
    ]);
  }
}

class _AcctChip extends StatelessWidget {
  final Acct a;
  final bool selected;
  final VoidCallback onTap;
  const _AcctChip(this.a, this.selected, this.onTap);

  @override
  Widget build(BuildContext context) => Semantics(
        selected: selected,
        button: true,
        child: Material(
          color: selected ? kInBg : kSurface,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: selected ? kBlue : kLine, width: 1.5),
          ),
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(a.type, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: kInk)),
                Text(a.no, style: TextStyle(fontSize: 11, fontFamily: 'monospace', color: kMut)),
              ]),
            ),
          ),
        ),
      );
}

/// Scan to pay: reads a CooBS QR from the camera or a saved picture and opens Transfer filled in.
class QrScanScreen extends StatefulWidget {
  final int from;
  const QrScanScreen({super.key, this.from = 0});
  @override
  State<QrScanScreen> createState() => _QrScanScreenState();
}

class _QrScanScreenState extends State<QrScanScreen> {
  final _cam = MobileScannerController(formats: const [BarcodeFormat.qrCode]);
  bool _handled = false;
  String? _lastBad;
  DateTime _lastBadAt = DateTime(0);

  @override
  void dispose() {
    _cam.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture c) {
    if (_handled || c.barcodes.isEmpty) return;
    final raw = c.barcodes.first.rawValue;
    if (raw == null) return;
    // The same unreadable code stays in view after the sheet closes; don't reopen it at once.
    if (raw == _lastBad && DateTime.now().difference(_lastBadAt) < const Duration(seconds: 3)) return;
    _handle(raw);
  }

  Future<void> _handle(String raw) async {
    _handled = true;
    final p = decodeQrPayment(raw);
    if (p == null) {
      await _showProblem(
        "This isn't a CooBS payment QR",
        "We couldn't find a CooBS account number in this code. Ask the receiver to open Receive money in their CooBS app and show you that QR.",
      );
      _lastBad = raw;
      _lastBadAt = DateTime.now();
      _handled = false;
      return;
    }
    await _cam.stop();
    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => RemittanceScreen(
          from: widget.from,
          initialDest: p.account,
          initialBank: 'COOBS',
          initialAmount: p.amountCentavos == null ? null : two(p.amountCentavos!),
          fromQr: true,
        ),
      ),
    );
  }

  Future<void> _pickFromGallery() async {
    if (_handled) return;
    final f = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (f == null || !mounted) return;
    String? raw;
    try {
      final cap = await _cam.analyzeImage(f.path, formats: const [BarcodeFormat.qrCode]);
      if (cap != null && cap.barcodes.isNotEmpty) raw = cap.barcodes.first.rawValue;
    } catch (_) {}
    if (!mounted) return;
    if (raw == null) {
      _handled = true;
      await _showProblem('No QR code found', "We couldn't find a QR code in that picture. Try another one, or scan with the camera.");
      _handled = false;
      return;
    }
    await _handle(raw);
  }

  Future<void> _showProblem(String title, String body) => showModalBottomSheet<void>(
        context: context,
        backgroundColor: kSurface,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
        builder: (ctx) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(
                  color: kDark ? const Color(0xFF3B1414) : const Color(0xFFFEE2E2),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Icon(Icons.error_outline_rounded, size: 28, color: kRed),
              ),
              const SizedBox(height: 14),
              Text(title, textAlign: TextAlign.center, style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: kInk)),
              const SizedBox(height: 8),
              Text(body, textAlign: TextAlign.center, style: TextStyle(fontSize: 13, height: 1.55, color: kMut)),
              bigBtn('Scan again', () => Navigator.pop(ctx)),
            ]),
          ),
        ),
      );

  Widget _cameraError(BuildContext context, MobileScannerException e) => ColoredBox(
        color: const Color(0xFF0B1118),
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(40),
            child: Text(
              e.errorCode == MobileScannerErrorCode.permissionDenied
                  ? 'Camera access is off. Allow it in Settings to scan, or pick a saved QR from your gallery.'
                  : 'The camera could not start. You can still pick a saved QR from your gallery.',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontSize: 14, height: 1.5),
            ),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: Colors.black,
        body: Stack(children: [
          Positioned.fill(
            child: MobileScanner(controller: _cam, onDetect: _onDetect, errorBuilder: _cameraError),
          ),
          SafeArea(
            child: Column(children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 9, 16, 9),
                child: Row(children: [
                  _GlassButton(icon: Icons.arrow_back_rounded, label: 'Back', size: 40, onTap: () => Navigator.maybePop(context)),
                  const SizedBox(width: 12),
                  const Text('Scan to pay', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w500, letterSpacing: -0.3)),
                ]),
              ),
              const Spacer(),
              const SizedBox(width: 260, height: 260, child: CustomPaint(painter: _FramePainter())),
              const SizedBox(height: 24),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 40),
                child: Column(children: [
                  Text('Point your camera at a CooBS QR', textAlign: TextAlign.center, style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w500)),
                  SizedBox(height: 6),
                  Text(
                    "It scans on its own. The recipient's account number and amount go straight into Transfer.",
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Color(0xFFCBD5E1), fontSize: 13, height: 1.5),
                  ),
                ]),
              ),
              const Spacer(),
              Padding(
                padding: const EdgeInsets.only(bottom: 32),
                child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  ValueListenableBuilder<MobileScannerState>(
                    valueListenable: _cam,
                    builder: (context, s, _) => _GlassButton(
                      icon: s.torchState == TorchState.on ? Icons.flashlight_off_rounded : Icons.flashlight_on_rounded,
                      label: 'Flashlight',
                      caption: true,
                      onTap: s.torchState == TorchState.unavailable ? null : _cam.toggleTorch,
                    ),
                  ),
                  const SizedBox(width: 40),
                  _GlassButton(icon: Icons.photo_library_outlined, label: 'From gallery', caption: true, onTap: _pickFromGallery),
                ]),
              ),
            ]),
          ),
        ]),
      );
}

class _GlassButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final double size;
  final bool caption;
  final VoidCallback? onTap;
  const _GlassButton({required this.icon, required this.label, this.size = 56, this.caption = false, this.onTap});

  @override
  Widget build(BuildContext context) {
    final round = size >= 56;
    final btn = Material(
      color: Colors.white.withAlpha(36),
      shape: round
          ? CircleBorder(side: BorderSide(color: Colors.white.withAlpha(56)))
          : RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: BorderSide(color: Colors.white.withAlpha(56))),
      child: InkWell(
        customBorder: round ? const CircleBorder() : RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        onTap: onTap,
        child: SizedBox(width: size, height: size, child: Icon(icon, size: round ? 22 : 20, color: Colors.white.withAlpha(onTap == null ? 110 : 255))),
      ),
    );
    if (!caption) return Tooltip(message: label, child: btn);
    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        btn,
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Colors.white, fontSize: 12)),
      ]),
    );
  }
}

/// Four rounded corner brackets and a scan line marking where to hold the QR.
class _FramePainter extends CustomPainter {
  const _FramePainter();

  @override
  void paint(Canvas canvas, Size s) {
    const accent = Color(0xFF00B4D8);
    const arm = 44.0, r = 18.0;
    final p = Paint()
      ..color = accent
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4
      ..strokeCap = StrokeCap.round;
    void corner(double x, double y, double dx, double dy) {
      canvas.drawPath(
        Path()
          ..moveTo(x, y + dy * arm)
          ..lineTo(x, y + dy * r)
          ..arcToPoint(Offset(x + dx * r, y), radius: const Radius.circular(r), clockwise: dx * dy > 0)
          ..lineTo(x + dx * arm, y),
        p,
      );
    }

    corner(0, 0, 1, 1);
    corner(s.width, 0, -1, 1);
    corner(0, s.height, 1, -1);
    corner(s.width, s.height, -1, -1);
    canvas.drawLine(
      Offset(18, s.height / 2),
      Offset(s.width - 18, s.height / 2),
      Paint()
        ..color = accent
        ..strokeWidth = 2
        ..maskFilter = const MaskFilter.blur(BlurStyle.solid, 4),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
