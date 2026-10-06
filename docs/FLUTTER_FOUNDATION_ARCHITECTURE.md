# Flutter Foundation Architecture & API Strategy Specification

**Project:** Capstone 2: Omnichannel Real-Time Remittance & Fraud Screening  
**Component:** Mobile Applications Layer (Flutter Retail Member App strictly targeting Native Android & iOS)  
**Role / Assignee:** Neo: Dev 6 (Flutter Foundation)  
**Track:** Mobile Applications Track (Integrated with React Web Lead & DevSecOps Lead)  
**Reference Document:** `int.pdf` (Sections 1.B, 2, 3), `docs/CONTRACTS.md`, & `docs/API_ENDPOINT_CATALOG.md`  

---

## 1. Executive Summary & Omnichannel Scope

The **Mobile Applications Layer (`app/`)** provides the cross-platform omnichannel interface for retail banking members to perform real-time peer-to-peer (P2P) remittances, query account balances, and view transaction status progress. 

In the CooBS Omnichannel Architecture:
* **React Web Frontend (`http://localhost:3000` / `:5173`):** The **sole web frontend** for the entire platform, dedicated to Branch Staff, Tellers, and Administrators for operational account management and 11-stage deep transaction audit. Interfaces via Spring Cloud Gateway CORS.
* **Flutter Mobile App (`app/` — Android `10.0.2.2:8080`):** Dedicated **strictly to native mobile devices** (Android and iOS) for fast, seamless, and resilient mobile banking. **Flutter Web is not used or deployed** since React is the platform's exclusive web portal. The native Flutter mobile client interfaces directly via native OS network sockets, completely bypassing browser Same-Origin Policy (zero CORS enforcement). Routes `/api/v1/mobile/**` through **`mobile-bff` (:8094)** for multi-service composite screen aggregation and 3-stage customer milestones.

### Omnichannel Client Separation Matrix

| Dimension | 📱 Customer Side (Mobile App) | 🖥️ Teller Side (Web Frontend) |
| :--- | :--- | :--- |
| **Technology** | **Flutter Native App** (`app/`) | **React Portal** (`frontend/`) |
| **Target Audience** | **Retail Bank Members** (Customers on Android/iOS phones) | **Branch Tellers, Operations, Admins, Compliance** (Desktop web browsers) |
| **Primary Goal** | Self-service banking: check balances, P2P transfers, view 3-stage progress receipt. | Back-office ops: account lifecycle, KYC approval, 11-stage transaction audit. |
| **Transaction View** | **3-Stage Customer Milestones** (`M2`): Submitted &rarr; In Review &rarr; Completed | **11-Stage Technical Saga Stepper**: Idempotency &rarr; Ledger Holds &rarr; Risk Scores &rarr; Settlement |
| **Network Path** | Mobile App &rarr; Gateway (`/api/v1/mobile/**`) &rarr; **`mobile-bff` (:8094)** &rarr; Domain services | Web App &rarr; Gateway (`/api/v1/{accounts,audit,ledger,transactions}/**`) &rarr; Direct Domain Services (bypasses BFF) |

To satisfy **Neo's Deliverables** (`docs/TEAM_STATUS.md`):
1. **Push Flutter Core to `app/`:** Routing (`app_router.dart`), Theme (`app_theme.dart`), API client with Fixture Mode (`fixture_api_client.dart`), and Dart models built from `contracts/examples/`.
2. **Native Mobile Ingress Alignment:** Direct native OS network socket communication targeting Android Emulator loopback (`10.0.2.2:8080`) or physical device LAN IP (`adb reverse tcp:8080 tcp:8080`), with zero browser CORS overhead.
3. **Mobile BFF Middleware Integration:** Orchestrates `mobile-bff` (`:8094`) to collapse 11 technical backend stages into the **3-Stage Customer View** (`T3-timeline-customer.response.json` / `M2`) and provide composite dashboard aggregation (`M1`).
4. **Stepper Visual Experience:** The **3-Stage Customer View** in the mobile remittance receipt view, complemented by the **11-Stage Staff Stepper** in the teller portal.
5. **Air-Gapped Fixture Loading (H15):** Curating `contracts/examples/` into `app/assets/fixtures/` for zero-backend offline UI testing.

---

## 2. Flutter Directory Structure (`app/`)

```
app/
├── pubspec.yaml                       # Core dependencies (http, uuid, flutter_secure_storage, intl)
├── README.md                          # Run instructions, freeze contract & test commands
├── assets/
│   └── fixtures/                      # Local JSON fixtures (H15) curated from contracts/examples/
│       ├── A2-A4-login.response.json          # Customer auth session & tokens
│       ├── K3-K4-account.response.json        # Member deposit account summary
│       ├── M1-mobile-dashboard.response.json  # Aggregated composite mobile dashboard
│       ├── T1-transfer.response.json          # Remittance submission intake receipt (T1/M3)
│       ├── T2-transaction.response.json       # Remittance tracking detail status (T2)
│       ├── T3-timeline-customer.response.json # 3-stage retail member stepper view (T3/M2)
│       ├── T3-timeline-staff.response.json    # 11-stage granular audit stepper view
│       └── N1-notifications.response.json     # In-app member notification alerts
├── lib/
│   ├── main.dart                      # Application entry point & environment initialization
│   ├── core/                          # === CORE FOUNDATION (FROZEN BY AGREEMENT - NEO) ===
│   │   ├── config/
│   │   │   └── app_config.dart        # Environment toggle (Fixture / Live), Gateway URLs & timeouts
│   │   ├── network/
│   │   │   ├── api_client.dart        # Abstract ApiClient interface & ApiResponse wrapper
│   │   │   ├── http_api_client.dart   # Live HTTP implementation injecting headers & parsing RFC-7807
│   │   │   ├── fixture_api_client.dart# Air-gapped fixture client reading from assets/fixtures/
│   │   │   ├── circuit_breaker.dart   # 3-state fail-fast client resilience breaker (CLOSED/OPEN/HALF-OPEN)
│   │   │   └── api_exceptions.dart    # RFC-7807 ProblemDetailsException, RateLimit, 503 exceptions
│   │   ├── storage/
│   │   │   └── secure_storage_service.dart # Android KeyStore wrapper with web memory fallback
│   │   ├── theme/
│   │   │   └── app_theme.dart         # CooBS Banking Design System (Navy, Slate, Emerald)
│   │   ├── router/
│   │   │   └── app_router.dart        # Declarative named routing & auth route guards
│   │   └── mocks/
│   │       └── mock_api_client.dart   # Offline in-memory ledger engine & chaos injection hooks
│   ├── shell/
│   │   └── app_shell.dart             # Responsive shell (Desktop NavRail vs Mobile BottomNav)
│   └── features/                      # === FEATURE VERTICALS (UNFROZEN / ITERATIVE - GABO) ===
│       ├── auth/                      # Login screen & session management
│       ├── accounts/                  # Account balance card & list
│       ├── remittance/                # P2P transfer form with 3-stage customer tracking
│       ├── kyc/                       # Member KYC profile & update requests
│       └── resilience/
│           └── service_unavailable_view.dart # Active fallback screen when Circuit Breaker trips
└── test/
    └── core_foundation_test.dart      # Runnable validation test verifying frozen core contracts
```

---

## 3. End-to-End System Architecture

### 3.1 Two-Track Omnichannel Routing Topology

The architecture enforces a strict two-track model at the API Gateway:
* **Track A (Mobile Retail Flow):** Native Flutter client incorporates a client-side Circuit Breaker, routing exclusively to `/api/v1/mobile/**` on `mobile-bff:8094`.
* **Track B (Web Staff Flow):** React portal on desktop browsers connects directly to domain microservices via standard gateway paths, **completely bypassing the Mobile BFF**.

```mermaid
flowchart TB
    classDef mobilePath fill:#e0f2fe,stroke:#0284c7,stroke-width:2px,color:#0369a1;
    classDef webPath fill:#fef3c7,stroke:#d97706,stroke-width:2px,color:#b45309;
    classDef gateway fill:#fee2e2,stroke:#dc2626,stroke-width:2px,color:#b91c1c;
    classDef bff fill:#fef9c3,stroke:#ca8a04,stroke-width:2px,color:#854d0e;
    classDef backend fill:#ede9fe,stroke:#7c3aed,stroke-width:2px,color:#6d28d9;
    classDef resilience fill:#fef08a,stroke:#ca8a04,stroke-width:2px,color:#854d0e;

    %% Client Tier
    subgraph TIER1_CLIENTS ["1. CLIENT TIER"]
        subgraph FLUTTER_APP ["📱 Retail Mobile App (app/)"]
            UI_MOBILE["Mobile Screens<br/>(Dashboard, Remittance, Stepper)"]:::mobilePath
            CB["⚡ CircuitBreaker<br/>(CLOSED / OPEN / HALF-OPEN)"]:::resilience
            FAIL_SCREEN["ServiceUnavailableView<br/>(30s Cooldown Screen)"]:::resilience
        end

        subgraph REACT_PORTAL ["🖥️ Teller Web Portal (frontend/)"]
            UI_WEB["Staff Console<br/>(Accounts, KYC, 11-Stage Audit)"]:::webPath
        end
    end

    %% Edge Ingress
    subgraph TIER2_INGRESS ["2. EDGE GATEWAY (api-gateway :8080)"]
        GW_MOBILE["Mobile Ingress Route<br/>Path: /api/v1/mobile/**<br/>Rate Limit: 50 req/s"]:::gateway
        GW_WEB["Domain & Ops Routes<br/>Path: /api/v1/{accounts, audit, ledger, transactions}/**"]:::gateway
    end

    %% Mobile Middleware
    subgraph TIER3_BFF ["3. MOBILE MIDDLEWARE (:8094)"]
        BFF["mobile-bff<br/>• M1: Dashboard Aggregation (&lt;150ms)<br/>• M2: 3-Stage Stepper Adapter<br/>• M3: Remittance Ingress Relay (202 Accepted)"]:::bff
    end

    %% Domain Services
    subgraph TIER4_SERVICES ["4. CORE BACKEND MICROSERVICES"]
        CUST["customer-service (:8089)"]:::backend
        ACCT["account-service (:8082)"]:::backend
        TX["transaction-service (:8090)"]:::backend
        AUDIT["audit-service (:8085)"]:::backend
        NOTIF["notification-service (:8084)"]:::backend
        RISK["risk-engine (:8092)"]:::backend
    end

    %% Internal Mobile Resilience Flow
    UI_MOBILE <-->|"Normal Operations"| CB
    CB -.->|"Trips on 3 Failures"| FAIL_SCREEN

    %% Outgoing Mobile Network Call
    CB ==>|"Native OS Sockets (Zero CORS)"| GW_MOBILE
    GW_MOBILE ==>|"/api/v1/mobile/**"| BFF

    %% BFF Fanout
    BFF -->|"Aggregate Member Info"| CUST
    BFF -->|"Aggregate Balances"| ACCT
    BFF -->|"M2 Stepper / M3 Transfers"| TX
    BFF -->|"Aggregate Alerts"| NOTIF

    %% Web Teller Flow (Direct)
    UI_WEB ==>|"Direct Domain APIs (CORS Enabled)"| GW_WEB
    GW_WEB -->|"/api/v1/accounts/**"| ACCT
    GW_WEB -->|"/api/v1/audit/**"| AUDIT
    GW_WEB -->|"/api/v1/transactions/** (11-Stage View)"| TX
    GW_WEB -->|"/api/v1/customers/**"| CUST

    TX <-->|"Risk Screening (&lt;=200ms)"| RISK
```

### 3.2 Transaction Timeline Adapter (11-to-3 Translation)

The mobile client shields retail customers from backend distributed saga mechanics by delegating milestone translation to `mobile-bff` (Endpoint `M2`):

```mermaid
flowchart LR
    classDef staff fill:#fee2e2,stroke:#dc2626,stroke-width:1.5px,color:#991b1b;
    classDef bff fill:#fef9c3,stroke:#ca8a04,stroke-width:2px,color:#854d0e;
    classDef customer fill:#dcfce7,stroke:#16a34a,stroke-width:1.5px,color:#166534;

    subgraph STAFF_VIEW ["🖥️ TELLER WEB VIEW (11 Technical Saga Stages)"]
        direction TB
        S1["1. VALIDATE_IDEMPOTENCY"]:::staff
        S2["2. CHECK_BALANCE"]:::staff
        S3["3. RISK_EVALUATION_SCORE"]:::staff
        S4["4. PLACE_LEDGER_HOLD"]:::staff
        S5["5. DISPATCH_CLEARING"]:::staff
        S6["6. AWAIT_SETTLEMENT"]:::staff
        S7["7. POST_JOURNAL_ENTRIES"]:::staff
        S8["8. RELEASE_HOLD"]:::staff
        S9["9. RECORD_AUDIT_LOG"]:::staff
        S10["10. SEND_NOTIFICATION"]:::staff
        S11["11. SAGA_COMPLETED"]:::staff
        S1 --> S2 --> S3 --> S4 --> S5 --> S6 --> S7 --> S8 --> S9 --> S10 --> S11
    end

    ADAPTER["mobile-bff (:8094)<br/>Timeline Adapter (M2)"]:::bff

    subgraph CUSTOMER_VIEW ["📱 CUSTOMER MOBILE VIEW (3 Friendly Milestones)"]
        direction TB
        C1["🟢 1. Transfer Submitted<br/>(Request received & verified)"]:::customer
        C2["🟡 2. Security & Clearing<br/>(Screening & network clearing)"]:::customer
        C3["🟢 3. Transfer Completed<br/>(Funds deposited to recipient)"]:::customer
        C1 --> C2 --> C3
    end

    S1 & S2 -.-> ADAPTER
    S3 & S4 & S5 & S6 -.-> ADAPTER
    S7 & S8 & S9 & S10 & S11 -.-> ADAPTER

    ADAPTER ==> C1
    ADAPTER ==> C2
    ADAPTER ==> C3
```

---

## 4. Client-Side Resilience & Circuit Breaker Logic

As mandated in `int.pdf` §1.B, the client application must protect user experience when the gateway or server cluster becomes unreachable.

```
State: CLOSED (Normal Operation)
  │
  ├── Request fails (Timeout, 500, 502, 503, Connection Refused)
  │   └── Increment _consecutiveFailures
  │
  └── If _consecutiveFailures >= 3
      │
      ▼
State: OPEN (Fail-Fast Active)
  │
  ├── Immediately throws CircuitBreakerOpenException
  ├── App displays ServiceUnavailableView ("Service Temporarily Unavailable")
  ├── UI does not hang or spin indefinitely
  │
  └── 30-Second Cooldown Expires
      │
      ▼
State: HALF-OPEN (Recovery Probe)
  │
  ├── Allows 1 probe call to /actuator/health
  ├── IF Success: _consecutiveFailures = 0, Transition to CLOSED
  └── IF Failure: Reset timer, Transition back to OPEN
```

---

## 5. Core Freeze Agreement

By completing Task 2, the `app/lib/core` directory is **FROZEN**:
1. **Network Contracts:** Decoupled behind `ApiClient` and `FixtureApiClient` so screens never import raw HTTP libraries.
2. **Security:** Hardened with `SecureStorageService` targeting Android KeyStore.
3. **Resilience:** Guarded by `CircuitBreaker`.
4. **Theme:** CooBS high-contrast banking design tokens locked in `AppTheme`.
5. **Zero Backend Mutations:** No backend code changes or Git pushes required.

---

## 6. Remittance Stepper & Offline Fixtures Specification (`H14` & `H15`)

### 6.1 3-Stage Customer Stepper Widget (`CustomerProgressStepper`)
The retail member remittance receipt displays a clean 3-step visual indicator backed by `T3-timeline-customer.response.json`:
* **Stage 1 (Submitted):** Icon: Checkmark / Pending; Title: *"Transfer Submitted"*; Subtitle: *"Your transfer request has been received and verified."*
* **Stage 2 (In Review):** Icon: Shield / Radar; Title: *"Security & Clearing"*; Subtitle: *"Automated security screening and bank network clearing."*
* **Stage 3 (Completed):** Icon: Bank / Wallet; Title: *"Transfer Completed"*; Subtitle: *"Funds have been successfully deposited into the recipient's account."*

### 6.2 Fixture Mode (`H15`)
When `AppConfig.environment == Environment.fixture`:
* `FixtureApiClient` intercepts requests to `GET /api/v1/mobile/dashboard` and loads `app/assets/fixtures/M1-mobile-dashboard.response.json`.
* Intercepts requests to `GET /api/v1/transactions/{id}/timeline?view=customer` (or `/api/v1/mobile/transactions/{ref}/timeline`) and loads `app/assets/fixtures/T3-timeline-customer.response.json`.
* Intercepts `POST /api/v1/transactions/remittance` (or `/api/v1/mobile/transactions/remittance`) and returns `T1-transfer.response.json`.
* Intercepts `GET /api/v1/transactions/{id}` and returns `T2-transaction.response.json`.
* Intercepts `POST /api/v1/auth/customers/login` and returns `A2-A4-login.response.json`.
* Enables Gabo (Dev 7) and QA to test all screens air-gapped without backend dependencies running.
