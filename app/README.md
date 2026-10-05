# CooBS Mobile Application (`app/`)

Dedicated Flutter retail member **Mobile Application** targeting **Android** (and iOS).

> **Note on Client Architecture:**
> The web front end for CooBS is the **React** portal located in `frontend/` (serving web users and staff console).
> The Flutter application in `app/` is **strictly for mobile devices** and is **not** used as a web front end.

---

## 1. Architecture Overview

- **Role / Ownership:** Neo: Dev 6 (Flutter Foundation) & Gabo: Dev 7 (Flutter Features)
- **Target Platform:** Mobile devices / Android Emulator
- **Target Ingress:** 
  - Android Emulator: `http://10.0.2.2:8080` (Spring Cloud Gateway loopback)
  - Physical Device: `http://<LAN_IP>:8080` or `adb reverse tcp:8080 tcp:8080`
- **Mobile Middleware:** Interacts with `mobile-bff` (:8094) for composite dashboard (`M1`) and 3-stage timeline adapter (`M2`).
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

## 3. Running the App

### Android Device / Emulator
```bash
flutter run -d android
```

### Run Tests
```bash
flutter test
```
