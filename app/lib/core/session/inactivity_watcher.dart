import 'dart:async';
import 'package:flutter/material.dart';

import '../network/api_client.dart';
import '../storage/secure_storage_service.dart';

/// Banking Security Standard: Automatic Session Inactivity Timeout (PCI-DSS 4.0 / BSP).
/// Resets a timer on every user touch/pointer interaction.
/// If user remains inactive for [timeoutDuration] while authenticated,
/// session tokens are revoked on the backend and storage is purged,
/// triggering [ApiClient.onSessionExpired] to return to LoginScreen.
class InactivityWatcher extends StatefulWidget {
  final Widget child;
  final Duration timeoutDuration;

  const InactivityWatcher({
    super.key,
    required this.child,
    this.timeoutDuration = const Duration(minutes: 15),
  });

  @override
  State<InactivityWatcher> createState() => _InactivityWatcherState();
}

class _InactivityWatcherState extends State<InactivityWatcher> {
  Timer? _timer;
  final SecureStorageService _storage = SecureStorageService();

  @override
  void initState() {
    super.initState();
    _resetTimer();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _resetTimer() {
    _timer?.cancel();
    _timer = Timer(widget.timeoutDuration, _handleInactivityTimeout);
  }

  Future<void> _handleInactivityTimeout() async {
    final hasSession = await _storage.hasValidSession();
    if (!hasSession) return;

    try {
      await ApiClient().post('/auth/token/revoke');
    } catch (_) {}

    await _storage.clearSession();
    ApiClient.onSessionExpired?.call();
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _resetTimer(),
      onPointerMove: (_) => _resetTimer(),
      child: widget.child,
    );
  }
}
