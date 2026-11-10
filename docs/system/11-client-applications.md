# 11. Client applications

CooBS has two clients with deliberately different audiences and looks:

| | Staff console (`frontend/`) | Member app (`app/`) |
| --- | --- | --- |
| Users | Tellers, administrators | Members |
| Technology | React 19, TypeScript 6, Vite 8, Tailwind 3, React Router 6, lucide-react | Flutter 3.44 / Dart 3.12 |
| Palette | Corporate navy `#1E3A5F` and blue `#2563EB` on `#F0F4F8`; light only | Teal `#0D9488` / `#14B8A6`; light and dark themes |
| Backend entry | `/api/v1/staff/**` (`staff-bff`) plus some direct service routes | `/api/v1/mobile/**` (`mobile-bff`) plus direct customer/account/notification routes |
| Transfer view | 11-stage staff timeline | 3-milestone customer timeline |
| Delivery | nginx container (port 3000) | Android APK installed by hand; iOS and web targets in code |

## 11.1 Staff console (`frontend/`)

### Structure

```
frontend/src/
  App.tsx                 routes + ProtectedRoute / AdminRoute guards
  main.tsx                React root, BrowserRouter, AuthProvider
  contexts/AuthContext.tsx  staff sign-in, token in sessionStorage, isAdmin/isTeller
  services/client.ts      fetch wrapper: base URL, Bearer, X-Correlation-ID, problem-details errors, revoke on logout
  services/api/*.ts       accounts, audit, auth, batch, customers, notifications, staff, transactions
  types/staff-bff.ts      TypeScript contracts for staff-bff responses
  lib/utils.ts            formatCurrency (₱, 2 dp), formatTimestamp, truncateHash, generateUUID, getStatusClass, cn
  components/shell/       AppLayout, AppHeader, Sidebar, RoleBadge, StaffSignIn
  components/<screen>/    one folder per screen (below)
```

Files at the top of `components/` (`AdminWorkspace.tsx`, `TellerWorkspace.tsx`, `CustomerWorkspace.tsx`, `DashboardView.tsx`, `TransferView.tsx`, `TopologyView.tsx`, `EodBatchView.tsx`, `AuditView.tsx`, `NotificationView.tsx`, `LoginPage.tsx`, `AuthModal.tsx`, `Header.tsx`, `NavigationTabs.tsx`, `NotificationDrawer.tsx`) belong to an earlier single-page dashboard and are not routed.

### Screens and routes

| ID | Route | Component | What it does | Backend |
| --- | --- | --- | --- | --- |
| WEB-01 | `/login` | `shell/StaffSignIn` | Username and password; no registration or reset | A4 |
| WEB-02 | (shell) | `shell/AppLayout`, `AppHeader`, `Sidebar` | 56 px header (brand, branch pill, gateway latency, role badge, user, sign-out); 64 px navy sidebar | `/actuator/health` |
| — | `/dashboard` | `dashboard/StaffDashboard` → `TellerDashboard` or `AdminDashboard` | Role-specific landing: queues summary, recent activity; admin overview with EOD and DLQ | `/staff/queues/summary`, `/staff/admin/overview` |
| WEB-03 | `/customer-360` | `customer/Customer360` | Search a member; profile with KYC, tier, risk and credit score; totals; accounts with copyable IDs; recent transfers; spending by category | `/staff/customers/{id}/360` |
| WEB-04 | `/counter` | `counter/CounterDesk` | Tabs: Cash Deposit, Cash Withdrawal, Assisted Transfer (teller only). Amount chips (+₱1,000, +₱5,000, +₱10,000, Max), remarks, confirmation modal and receipt | `/staff/counter/*` |
| WEB-05 | `/queues` | `queues/QueuesManager`, `KYCQueue`, `ClosureQueue` | KYC updates (approve/reject with mandatory reason) and closures (admin decides; teller read-only) | `/staff/queues/*` |
| WEB-06 | `/accounts` | `accounts/AccountDirectory`, `OpenAccountDrawer` | Directory with status pills and flag chips; open account (SAVINGS, CHECKING, CURRENT, LOAN; starts at ₱0.00); freeze/activate; add flag; remove flag (admin) | `/staff/accounts*` |
| WEB-07 | `/tracker` | `tracker/TransactionTracker` | Search by reference or UUID; 11-stage vertical pipeline; 3 s live polling toggle; "Initiate Reversal" (reason required, enabled only for POSTED) | `/staff/transactions/{id}/timeline`, `/reversal` |
| WEB-08 | `/audit` | `audit/AuditStatement` | Chain-integrity banner, daily seal card, statement with previous/current hash per row and copy actions | `/staff/audit/*` |
| WEB-09 | `/admin/eod` | `admin/EODBatchPipeline` | Run list with duration and status; 6-step pipeline view; "Trigger EOD Run" | `/staff/batch/eod/runs*`, `/staff/admin/batch/eod/trigger` |
| WEB-10 | `/admin/dlq` | `admin/DeadLetterQueue` | Failed Kafka messages: original topic, failure, payload preview; "Replay to Kafka" (replayed IDs remembered in `localStorage`) | `/staff/admin/dlq*` |

`/admin/*` routes are wrapped in `AdminRoute` (tellers are redirected to `/dashboard`); the server enforces the same rules.

### Formatting conventions

Currency `₱1,420,500.00` from string amounts parsed exactly; account numbers `ACC_10000001`; references `TX-20261003-8F3A21C0`, reversals `RV-…`; hashes truncated (`7f8a12c0…3d9f`) in JetBrains Mono with copy; tabular numbers; status colours — emerald (active/intact/passed), amber (pending), crimson (frozen/failed/DLQ), slate (closed).

### Running

```bash
cd frontend
npm install
npm run dev        # http://localhost:3000; proxies /api/v1/auth → :8081 and /api/v1/staff → :8095 directly
                   # (bypassing the gateway), all other /api and /actuator → gateway :8080
npm run build      # tsc -b && vite build
npm run lint       # oxlint
```

In Docker the image is built from `frontend/Dockerfile` and served by nginx (`frontend/nginx.conf`): SPA fallback to `index.html`, `/internal` → 403, `/api` and `/actuator` proxied to `api-gateway:8080`. Staff sign in with `teller_alice` or `admin` (`Password123!` locally).

## 11.2 Member app (`app/`)

### Structure

```
app/lib/
  main.dart               entry; USE_FIXTURES off by default at runtime
  main_fixtures.dart      entry for fixture (offline) mode
  core/config/app_config.dart   gateway URL (Azure default, --dart-define GATEWAY_URL, or in-app setting), timeouts
  core/network/api_client.dart  HTTP client: Bearer, X-Correlation-ID, Idempotency-Key, token refresh on 401, RFC 7807 parsing
  core/network/circuit_breaker.dart  CLOSED → OPEN (3 failures) → HALF-OPEN after 30 s
  core/storage/secure_storage_service.dart  tokens in Keystore/Keychain
  core/session/            biometric_authenticator.dart, inactivity_watcher.dart (15 min)
  core/models/             Dart DTOs matching the contract
  core/router/app_router.dart  named routes
  core/theme/              light/dark theme, ThemeController
  shell/app_shell.dart     bottom navigation: Dashboard · Transfer · Timeline
  features/                one folder per feature (below)
  features/data/mobile_repo.dart  all backend calls
  features/accounts/demo_store.dart  app-wide state store (wraps MobileRepo)
```

### Features

| Feature | Folder | Backend | Notes |
| --- | --- | --- | --- |
| Splash, sign-in | `auth/` | A2, A6 | Password; server address selectable on the sign-in screen |
| Fingerprint sign-in | `auth/biometric_login_screen.dart` | A6 (stored refresh token) | `local_auth` |
| Dashboard / home | `accounts/home_screen.dart` | M1, K4 | Balance cards, recent activity, unread badge |
| Send money (remittance) | `remittance/` | R1, M3 | Own bank or partner bank; same `Idempotency-Key` on retap |
| Transfer timeline | `tracking/` | M2 / T3 | 3 milestones: Submitted → Security & Clearing → Completed (or Failed with reason) |
| Beneficiaries | `beneficiaries/` | C4–C6 | |
| Notifications | `profile/tab_screens.dart` | N1–N3 | |
| KYC update | `kyc/kyc_intake_screen.dart` | `POST /customers/kyc/update-request` | |
| Ipon Vaults | `vaults/ipon_vaults_screen.dart` | `/accounts/vaults*` | Goal envelopes; allocate, release, close |
| Share capital | `vaults/share_capital_screen.dart` | `/accounts/share-capital*` | Certificate, top-up, dividends |
| QR pay / receive | `qr/` | — (feeds the remittance form) | Payload = account number + optional amount, plain JSON; `mobile_scanner`, `qr_flutter`, save/share |
| Scores and deals | `scores/` | — | Client-side personalisation and offers |
| Loans | `loans/` | — | Apply flow with a flat 12% p.a. demo estimate; no server endpoint |
| Shop | `shop/` | — | Client-side demo catalogue |
| Ads | `ads/` | — | Probability-based promotional dialog once per session |
| Service unavailable | `resilience/` | — | Shown when the circuit breaker is open, with a 30 s countdown |

`DemoStore` calls the API and, if a call fails, keeps working on local values (several `catch (_) {}` blocks). That keeps the demonstration smooth but can show a vault or share-capital change that the server did not accept; see section 14.

### Resilience

- Every call has a 10 s time limit (2.5 s for token refresh).
- On-device circuit breaker: 3 consecutive timeouts or 5xx open it; while open, calls fail at once and the app shows `ServiceUnavailableView`; after 30 s one probe call decides whether to close it. 4xx responses do not trip it.
- A 401 triggers one refresh with the stored refresh token, then the original call is retried.

### Running

```bash
cd app
flutter pub get
flutter run                                                    # against Azure (default)
flutter run --dart-define=GATEWAY_URL=http://10.0.2.2:8080     # Android emulator → local stack
flutter run --dart-define=USE_FIXTURES=true                    # offline, assets/fixtures/*.json
flutter build apk --release --dart-define=GATEWAY_URL=https://coobs.indonesiacentral.cloudapp.azure.com
flutter test
flutter analyze
```

On a physical phone against a local stack use the PC's LAN address or `adb reverse tcp:8080 tcp:8080`; see `TESTING_GUIDE.md` for troubleshooting (`SocketException`, ADB port conflicts). Members sign in with seeded usernames such as `juan_delacruz` / `Password123!`.

### Tests (`app/test/`)

18 test files: core foundation, fixtures-vs-contract, token-refresh interceptor, auth and shell flow, biometric login, retap idempotency, remittance live flow, tracking, DLQ and resilience, QR payload, share capital, Ipon Vaults and envelopes, scores and reversals, personalisation and deals, shop, ads, theme contrast, widget smoke test.
