import 'dart:async';
import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';
import '../accounts/ui.dart';

/// Active fallback screen rendered when client-side Circuit Breaker trips
/// or infrastructure encounters a 503 gateway drop.
class ServiceUnavailableView extends StatefulWidget {
  final Duration? cooldown;
  final VoidCallback? onRetry;
  final bool isEmbedded;

  const ServiceUnavailableView({
    super.key,
    this.cooldown,
    this.onRetry,
    this.isEmbedded = false,
  });

  @override
  State<ServiceUnavailableView> createState() => _ServiceUnavailableViewState();
}

class _ServiceUnavailableViewState extends State<ServiceUnavailableView> {
  late int _remainingSeconds;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    if (widget.cooldown != null) {
      _remainingSeconds = widget.cooldown!.inSeconds;
    } else {
      _remainingSeconds = 30;
    }
    if (_remainingSeconds > 0) {
      _startTimer();
    }
  }

  @override
  void didUpdateWidget(ServiceUnavailableView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.cooldown != oldWidget.cooldown) {
      _timer?.cancel();
      if (widget.cooldown != null) {
        _remainingSeconds = widget.cooldown!.inSeconds;
      } else {
        _remainingSeconds = 30;
      }
      if (_remainingSeconds > 0) {
        _startTimer();
      }
    }
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_remainingSeconds <= 1) {
        timer.cancel();
        setState(() => _remainingSeconds = 0);
      } else {
        setState(() => _remainingSeconds--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final canRetry = _remainingSeconds == 0;

    final content = Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      color: AppTheme.errorCarmine.withAlpha(25),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.cloud_off_rounded,
                      size: 36,
                      color: AppTheme.errorCarmine,
                    ),
                  ),
                  const SizedBox(height: 24),
                  Text(
                    'Service Temporarily Unavailable',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w700,
                      color: kInk,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Our core banking mesh is currently undergoing automated recovery. '
                    'To protect transaction integrity, incoming requests are temporarily paused.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 14,
                      height: 1.5,
                      color: kMut,
                    ),
                  ),
                  const SizedBox(height: 32),
                  if (!canRetry) ...[
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: kNavyFg,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Text(
                          'Auto-reconnect in ${_remainingSeconds}s',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            color: kInk,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),
                  ],
                  ElevatedButton(
                    onPressed: canRetry
                        ? () {
                            if (widget.onRetry != null) {
                              widget.onRetry!();
                            } else if (Navigator.canPop(context)) {
                              Navigator.of(context).pop();
                            } else {
                              Navigator.of(context).pushReplacementNamed('/');
                            }
                          }
                        : null,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: canRetry ? AppTheme.navyPrimary : AppTheme.borderSubtle,
                      foregroundColor: canRetry ? Colors.white : AppTheme.textMuted,
                    ),
                    child: const Text('Retry Connection'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    if (widget.isEmbedded) {
      return content;
    }

    return Scaffold(
      backgroundColor: AppTheme.backgroundLight,
      body: SafeArea(child: content),
    );
  }
}
