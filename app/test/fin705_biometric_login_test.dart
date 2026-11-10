import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:app/core/config/app_config.dart';
import 'package:app/core/network/api_client.dart';
import 'package:app/core/session/biometric_authenticator.dart';
import 'package:app/core/storage/secure_storage_service.dart';
import 'package:app/features/accounts/home_screen.dart';
import 'package:app/features/auth/auth_screens.dart';
import 'package:app/features/data/mobile_repo.dart';
import 'package:app/features/profile/tab_screens.dart';

/// Stands in for the device sensor: [available] mimics an enrolled fingerprint, [accept] the user's touch.
class FakeBiometrics implements BiometricAuthenticator {
  bool available;
  bool accept;
  final List<String> prompts = [];
  FakeBiometrics({this.available = true, this.accept = true});

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<bool> authenticate(String reason) async {
    prompts.add(reason);
    return accept;
  }
}

class ScriptedGateway extends http.BaseClient {
  final http.Response Function(http.BaseRequest request) handler;
  final List<http.BaseRequest> requests = [];
  ScriptedGateway(this.handler);

  List<http.BaseRequest> to(String path) => requests.where((r) => r.url.path == path).toList();

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    final response = handler(request);
    return http.StreamedResponse(Stream.value(response.bodyBytes), response.statusCode,
        headers: response.headers, request: request);
  }
}

http.Response jsonResponse(Object body, int status) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

const refreshPath = '/api/v1/auth/token/refresh';

Future<void> pumpFor(WidgetTester tester, {int frames = 20}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late bool previousUseFixtures;
  late SecureStorageService storage;
  setUp(() {
    previousUseFixtures = AppConfig.useFixtures;
    rootBundle.clear();
    storage = SecureStorageService();
  });
  tearDown(() {
    AppConfig.useFixtures = previousUseFixtures;
    MobileRepo.apiForTesting = ApiClient();
  });

  group('Enabling fingerprint login after a password login', () {
    testWidgets('Offers it once and stores the session refresh token when the member accepts', (tester) async {
      AppConfig.useFixtures = true;
      final bio = FakeBiometrics();
      await tester.pumpWidget(MaterialApp(home: LoginScreen(storage: storage, biometrics: bio)));
      await tester.pump();

      await tester.enterText(find.byType(TextField).first, 'john_doe');
      await tester.enterText(find.byType(TextField).last, 'Password123!');
      await tester.tap(find.text('Log in'));
      await pumpFor(tester, frames: 5);

      expect(find.text('Enable fingerprint login?'), findsOneWidget);
      await tester.tap(find.text('Enable'));
      await pumpFor(tester);

      expect(find.byType(MobileShell), findsOneWidget);
      expect(await storage.getBiometricUsername(), 'john_doe');
      expect(await storage.getBiometricRefreshToken(), await storage.getRefreshToken());
      expect(await storage.wasBiometricOffered('john_doe'), isTrue);
    });

    testWidgets('"Not now" leaves it off and is remembered', (tester) async {
      AppConfig.useFixtures = true;
      await tester.pumpWidget(MaterialApp(home: LoginScreen(storage: storage, biometrics: FakeBiometrics())));
      await tester.pump();

      await tester.enterText(find.byType(TextField).first, 'john_doe');
      await tester.enterText(find.byType(TextField).last, 'Password123!');
      await tester.tap(find.text('Log in'));
      await pumpFor(tester, frames: 5);
      await tester.tap(find.text('Not now'));
      await pumpFor(tester);

      expect(find.byType(MobileShell), findsOneWidget);
      expect(await storage.getBiometricUsername(), isNull);
      expect(await storage.wasBiometricOffered('john_doe'), isTrue);
    });

    testWidgets('Phones without an enrolled fingerprint are never asked', (tester) async {
      AppConfig.useFixtures = true;
      await tester.pumpWidget(MaterialApp(home: LoginScreen(storage: storage, biometrics: FakeBiometrics(available: false))));
      await tester.pump();

      await tester.enterText(find.byType(TextField).first, 'john_doe');
      await tester.enterText(find.byType(TextField).last, 'Password123!');
      await tester.tap(find.text('Log in'));
      await pumpFor(tester);

      expect(find.text('Enable fingerprint login?'), findsNothing);
      expect(find.byType(MobileShell), findsOneWidget);
    });
  });

  group('Logging in with a fingerprint', () {
    setUp(() async {
      // State after an earlier session that enabled fingerprint login, then logged out.
      await storage.enableBiometricLogin(username: 'john_doe', refreshToken: 'bio-refresh-1');
    });

    Future<ScriptedGateway> openLogin(WidgetTester tester, FakeBiometrics bio, http.Response Function() refreshReply) async {
      AppConfig.useFixtures = false;
      final gateway = ScriptedGateway((req) => req.url.path == refreshPath ? refreshReply() : http.Response('', 404));
      MobileRepo.apiForTesting = ApiClient(httpClient: gateway);
      await tester.pumpWidget(MaterialApp(
        home: LoginScreen(apiClient: ApiClient(httpClient: gateway, storage: storage), storage: storage, biometrics: bio),
      ));
      return gateway;
    }

    testWidgets('Prompts on open, exchanges the stored refresh token and opens the app', (tester) async {
      final bio = FakeBiometrics();
      final gateway = await openLogin(tester, bio,
          () => jsonResponse({'accessToken': 'access-2', 'refreshToken': 'bio-refresh-2', 'userId': 7, 'username': 'john_doe'}, 200));
      await tester.runAsync(() => Future.delayed(const Duration(milliseconds: 300)));
      await pumpFor(tester);

      expect(bio.prompts, hasLength(1), reason: 'prompt appears without a tap');
      final refresh = gateway.to(refreshPath).single;
      expect(jsonDecode((refresh as http.Request).body), {'refreshToken': 'bio-refresh-1'});
      expect(find.byType(MobileShell), findsOneWidget);
      expect(await storage.getAccessToken(), 'access-2');
      expect(await storage.getUsername(), 'john_doe');
      expect(await storage.getBiometricRefreshToken(), 'bio-refresh-2', reason: 'rotated token replaces the used one');
    });

    testWidgets('Cancelling the prompt keeps the biometric screen; switching account shows the password form', (tester) async {
      final bio = FakeBiometrics(accept: false);
      final gateway = await openLogin(tester, bio, () => jsonResponse({}, 500));
      await pumpFor(tester);

      expect(bio.prompts, hasLength(1));
      expect(gateway.requests, isEmpty);
      expect(find.text('john_doe'), findsOneWidget);
      expect(find.text('Log in with fingerprint'), findsOneWidget);
      expect(find.text('Use another account'), findsOneWidget);
      expect(find.text('Log in with password'), findsOneWidget);
      expect(find.byType(TextField), findsNothing, reason: 'Second login screen asks for biometrics, not credentials');

      // Trying again from the button shows the prompt again.
      bio.accept = false;
      await tester.tap(find.text('Log in with fingerprint'));
      await pumpFor(tester);
      expect(bio.prompts, hasLength(2));
      expect(await storage.getBiometricUsername(), 'john_doe');

      // Asking to use another account opens the standard login form with fields
      await tester.tap(find.text('Use another account'));
      await pumpFor(tester);
      expect(find.byType(TextField), findsNWidgets(2));
      expect(find.text('Log in'), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField).first).controller!.text, isEmpty);
    });

    testWidgets('Asking to log in with password switches to password form prefilled with username', (tester) async {
      final bio = FakeBiometrics(accept: false);
      await openLogin(tester, bio, () => jsonResponse({}, 500));
      await pumpFor(tester);

      await tester.tap(find.text('Log in with password'));
      await pumpFor(tester);

      expect(find.byType(TextField), findsNWidgets(2));
      expect(find.text('Log in'), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField).first).controller!.text, 'john_doe');
    });

    testWidgets('Standalone BiometricLoginScreen prompts on mount and supports switching account', (tester) async {
      final bio = FakeBiometrics(accept: false);
      bool switched = false;
      await tester.pumpWidget(MaterialApp(
        home: BiometricLoginScreen(
          storage: storage,
          biometrics: bio,
          bioUser: 'john_doe',
          onUseAnotherAccount: () => switched = true,
        ),
      ));
      await pumpFor(tester);

      expect(bio.prompts, hasLength(1));
      expect(find.text('john_doe'), findsOneWidget);
      expect(find.byType(TextField), findsNothing);

      await tester.tap(find.text('Use another account'));
      await pumpFor(tester);
      expect(switched, isTrue);
    });

    testWidgets('An expired or revoked refresh token turns fingerprint login off and asks for the password', (tester) async {
      await openLogin(tester, FakeBiometrics(), () => jsonResponse({'detail': 'Invalid or expired refresh token'}, 401));
      await pumpFor(tester);

      expect(find.textContaining('Fingerprint login has expired'), findsOneWidget);
      expect(find.text('Log in with fingerprint'), findsNothing);
      expect(find.text('Welcome back'), findsOneWidget);
      expect(await storage.getBiometricUsername(), isNull);
      expect(await storage.getBiometricRefreshToken(), isNull);
    });

    testWidgets('A server outage keeps fingerprint login on for the next try', (tester) async {
      await openLogin(tester, FakeBiometrics(), () => jsonResponse({'title': 'Service Unavailable', 'status': 503}, 503));
      await pumpFor(tester);

      expect(find.textContaining('Could not reach CooBS'), findsOneWidget);
      expect(find.text('Log in with fingerprint'), findsOneWidget);
      expect(await storage.getBiometricRefreshToken(), 'bio-refresh-1');
    });
  });

  group('Fingerprint token storage', () {
    test('Follows refresh-token rotation and survives logout', () async {
      await storage.saveTokens(accessToken: 'a1', refreshToken: 'r1', userId: 7, username: 'john_doe');
      await storage.enableBiometricLogin(username: 'john_doe', refreshToken: 'r1');

      await storage.updateTokens(accessToken: 'a2', refreshToken: 'r2');
      expect(await storage.getBiometricRefreshToken(), 'r2');

      await storage.clearSession();
      expect(await storage.getRefreshToken(), isNull);
      expect(await storage.getBiometricUsername(), 'john_doe');
      expect(await storage.getBiometricRefreshToken(), 'r2');
    });

    test('A different member logging in with a password removes the previous fingerprint login', () async {
      await storage.enableBiometricLogin(username: 'john_doe', refreshToken: 'r1');
      await storage.saveTokens(accessToken: 'b1', refreshToken: 'jane-r1', userId: 8, username: 'jane_smith');

      expect(await storage.getBiometricUsername(), isNull);
      expect(await storage.getBiometricRefreshToken(), isNull);
    });
  });

  group('Profile toggle', () {
    Future<void> openProfile(WidgetTester tester, FakeBiometrics bio) async {
      await tester.pumpWidget(MaterialApp(home: Scaffold(body: ProfileScreen(storage: storage, biometrics: bio))));
      await pumpFor(tester, frames: 4);
    }

    testWidgets('Turning it on asks for the fingerprint, turning it off wipes the stored token', (tester) async {
      await storage.saveTokens(accessToken: 'a1', refreshToken: 'r1', userId: 7, username: 'john_doe');
      final bio = FakeBiometrics();
      await openProfile(tester, bio);

      await tester.tap(find.text('Fingerprint login'));
      await pumpFor(tester, frames: 4);
      expect(bio.prompts, hasLength(1));
      expect(await storage.getBiometricUsername(), 'john_doe');
      expect(await storage.getBiometricRefreshToken(), 'r1');

      await tester.tap(find.text('Fingerprint login'));
      await pumpFor(tester, frames: 4);
      expect(await storage.getBiometricUsername(), isNull);
      expect(await storage.getBiometricRefreshToken(), isNull);
    });

    testWidgets('Hidden on phones without an enrolled fingerprint', (tester) async {
      await openProfile(tester, FakeBiometrics(available: false));
      expect(find.text('Fingerprint login'), findsNothing);
    });
  });
}
