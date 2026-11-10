import 'package:flutter/material.dart';

import 'core/config/app_config.dart';
import 'core/network/api_client.dart';
import 'core/router/app_router.dart';
import 'core/session/inactivity_watcher.dart';
import 'core/storage/secure_storage_service.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_controller.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Override fixture mode to false for live gateway execution unless explicitly enabled via --dart-define
  AppConfig.useFixtures = const bool.fromEnvironment('USE_FIXTURES', defaultValue: false);
  try {
    final savedGateway = await SecureStorageService().getGatewayUrl();
    if (savedGateway != null && savedGateway.isNotEmpty) {
      AppConfig.customRuntimeGatewayUrl = savedGateway;
    }
  } catch (_) {}
  ApiClient.onSessionExpired = () {
    AppRouter.navigatorKey.currentState?.pushNamedAndRemoveUntil(
      AppRouter.login,
      (_) => false,
    );
  };
  await ThemeController.i.load(); // restore saved dark mode before first frame
  runApp(const CooBSApp());
}

/// CooBS Mobile Application Root Widget.
class CooBSApp extends StatelessWidget {
  final String? initialRoute;
  const CooBSApp({super.key, this.initialRoute});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: ThemeController.i,
      builder: (context, _) => MaterialApp(
        navigatorKey: AppRouter.navigatorKey,
        title: 'CooBS Member Banking',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: ThemeController.i.mode,
        initialRoute: initialRoute ?? AppRouter.initial,
        onGenerateRoute: AppRouter.onGenerateRoute,
        builder: (context, child) =>
            InactivityWatcher(child: child ?? const SizedBox.shrink()),
      ),
    );
  }
}
