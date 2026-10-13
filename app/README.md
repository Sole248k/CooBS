# CooBS Mobile Application (`app/`)

Dedicated Flutter retail member **Mobile Application** targeting **Android** (and iOS).

> **Note on Client Architecture:**
> The web front end for CooBS is the **React** portal located in `frontend/` (serving web users and staff console).
> The Flutter application in `app/` is **strictly for mobile devices** and is **not** used as a web front end.

---

## 1. Architecture Overview

### Client Separation: Customer Mobile vs. Teller Web
CooBS strictly divides user access into two independent client channels:

| Dimension | 📱 Customer Side (Mobile App) | 🖥️ Teller Side (Web Frontend) |
| :--- | :--- | :--- |
| **Technology** | **Flutter Native App** (`app/`) | **React Portal** (`frontend/`) |
| **Target Audience** | **Retail Bank Members** (Android & iOS) | **Branch Tellers, Operations, Compliance** (Web Browser) |
| **Primary Goal** | Self-service banking: balance, remittances, 3-stage receipts | Back-office ops: account lifecycle, KYC approval, 11-stage audit |
| **Transaction View** | **3-Stage Customer Milestones** (`M2`): Submitted &rarr; In Review &rarr; Completed | **11-Stage Technical Saga Stepper**: Idempotency &rarr; Holds &rarr; Risk &rarr; Settlement |
| **Network Path** | Mobile &rarr; Gateway (`/api/v1/mobile/**`) &rarr; **`mobile-bff` (:8094)** | Web &rarr; Gateway (`/api/v1/{accounts,audit,ledger,transactions}/**`) &rarr; Direct Services |

### Why `mobile-bff` (:8094)?
The **Mobile Backend-for-Frontend** acts as an application-level aggregator to eliminate mobile cellular latency and battery drain:
- **`M1: GET /api/v1/mobile/dashboard`**: Returns a single composite payload (<150ms) bundling member profile, KYC status, primary balance, recent transactions, and unread alerts. Replaces 4 chatty microservice calls.
- **`M2: GET /api/v1/mobile/transactions/{ref}/timeline`**: Collapses the backend's internal **11-stage distributed saga** into a clean **3-stage customer milestone stepper**.
- **`M3: POST /api/v1/mobile/transactions/remittance`**: Immediate `202 Accepted` intake acknowledgement so the mobile UI never freezes.

### Client Resilience: On-Device Circuit Breaker
Wrapped around every request in `ApiClient`:
- **State Machine**: `CLOSED` &rarr; `OPEN` &rarr; `HALF-OPEN`.
- **Trip Condition**: 3 consecutive network timeouts or 5xx server drops trip the breaker to `OPEN` (4xx validation errors do not trip).
- **Fail-Fast**: In `OPEN` state, requests fail in 0ms and route to `ServiceUnavailableView` with a live 30-second cooldown timer.
- **Probing**: After 30 seconds, a probe request tests recovery; on success, resets to `CLOSED`.

- **Target Platform:** Mobile devices (iOS & Android) / Simulator / Emulator
- **Target Ingress:** 
  - Production Azure Gateway (Default): `https://coobs.indonesiacentral.cloudapp.azure.com`
  - Local Docker Gateway (Optional override): `http://localhost:8080` (or `http://10.0.2.2:8080` on Android Emulator)
- **Air-Gapped Fixture Mode:** Toggle `USE_FIXTURES=true` to run offline against `assets/fixtures/` without a running backend.

---

## 2. Directory Structure

```
app/
├── pubspec.yaml                       # Core dependencies (http, uuid, flutter_secure_storage, intl)
├── assets/
│   └── fixtures/                      # Curated JSON contract fixtures (H15)
│       ├── A2-A4-login.response.json
│       ├── K3-K4-account.response.json
│       ├── M1-mobile-dashboard.response.json
│       ├── T1-transfer.response.json
│       ├── T2-transaction.response.json
│       ├── T3-timeline-customer.response.json
│       ├── T3-timeline-staff.response.json
│       └── N1-notifications.response.json
├── lib/
│   ├── main.dart                      # App entry point & environment setup
│   ├── core/                          # === Core Foundation (Frozen) ===
│   │   ├── config/                    # Base URLs, timeouts, feature flags
│   │   ├── models/                    # Dart DTOs matching docs/CONTRACTS.md
│   │   ├── network/                   # ApiClient, CircuitBreaker, RFC-7807 error parsing
│   │   ├── storage/                   # Secure storage service (KeyStore / memory fallback)
│   │   ├── theme/                     # CooBS banking design system (Navy / Emerald / Slate)
│   │   ├── router/                    # Named routes & auth guards
│   │   └── mocks/                     # Mock client & chaos hooks
│   ├── shell/                         # Responsive app navigation shell
│   └── features/                      # === Feature Verticals ===
│       ├── auth/                      # Login & session
│       ├── accounts/                  # Account balances & detail
│       ├── remittance/                # P2P Remittance & 3-stage tracking stepper
│       ├── kyc/                       # KYC profile status view (Simulated)
│       └── resilience/                # ServiceUnavailableView (Circuit Breaker trip screen)
└── test/                              # Core foundation & widget tests
```

---

## 3. Running and Testing the App

For a complete step-by-step setup walkthrough including troubleshooting, consult [TESTING_GUIDE.md](../TESTING_GUIDE.md).

### Mode A: Standard Run (Defaults to Live Azure Gateway)
Runs against `https://coobs.indonesiacentral.cloudapp.azure.com`:
```bash
# iOS Simulator
flutter run -d "iPhone 17 Pro"

# Physical iOS / Android Device
flutter run -d <DEVICE_ID>
```

### Mode B: Air-Gapped Fixture Mode (Offline / Zero Backend)
```bash
# Works completely offline against local JSON fixtures:
flutter run --dart-define=USE_FIXTURES=true
```

### Mode C: Custom Gateway Override (CLI Flag)
```bash
# Override gateway to local development server:
flutter run --dart-define=GATEWAY_URL=http://localhost:8080
```
*(Or easily switch between Azure Cloud, Localhost, and Android Emulator via the in-app server settings popup).*

### Mode D: Physical iOS Device with Xcode
1. Open the workspace in Xcode:
   ```bash
   open ios/Runner.xcworkspace
   ```
2. Under **Runner > Signing & Capabilities**, verify your **Development Team**.
3. Select your connected iPhone and click **Run** (or `flutter run -d <DEVICE_ID>`).



### Run Tests & Static Analysis
```bash
flutter test
flutter analyze
```
