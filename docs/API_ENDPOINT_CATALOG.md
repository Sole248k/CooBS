# CooBS Core Retail Banking Platform — Comprehensive API Endpoint Catalog

This document is the authoritative, production-grade inventory and specification of all **38 core business endpoints** and **5 infrastructure/observability endpoints** across the **CooBS Core Banking Platform**.

---

## 1. Global API Standards & Architecture

### 1.1 Ingress & Routing Architecture
All inbound external traffic is routed through **Spring Cloud Gateway (`api-gateway` on port `8080`)**. Direct client access to internal microservices on ports `8081`–`8094` is restricted in production.

```
React Web (Staff/Teller :3000/:5173)         Flutter Mobile (Members 10.0.2.2:8080)
           │ (Browser CORS)                                   │ (Native OS Socket)
           └──────────────────────────┬───────────────────────┘
                                      ▼
                      Spring Cloud API Gateway (:8080)
                                      │
       ├─► /api/v1/auth/**          ──► auth-service (:8081)
       ├─► /api/v1/mobile/**        ──► mobile-bff (:8094) [Orchestration Middleware]
       ├─► /api/v1/accounts/**      ──► account-service (:8082)
       ├─► /api/v1/customers/**     ──► customer-service (:8089)
       ├─► /api/v1/transactions/**  ──► transaction-service (:8090) [T1, T2, T3, T8, T9]
       ├─► /api/v1/ledger/**        ──► ledger-service (:8083)
       ├─► /api/v1/notifications/** ──► notification-service (:8084)
       └─► /api/v1/audit/**         ──► audit-service (:8085)
```

### 1.2 Mandatory & Standard HTTP Headers
| Header | Type | Scope | Description |
| :--- | :---: | :--- | :--- |
| `Authorization` | String | Secured Endpoints | Bearer JWT Token (`Bearer <token>`) containing `sub`, `userId`, `roles`. |
| `X-Correlation-ID` | UUIDv4 | All Requests | Distributed trace identifier passed across all microservice hops. |
| `Idempotency-Key` | UUIDv4 | Financial Mutations | Client-generated key preventing duplicate mutations (`/api/v1/transactions/**`, `/api/v1/ledger/**`). |
| `Content-Type` | String | Requests with Body | `application/json; charset=UTF-8` |
| `Accept` | String | All Requests | `application/json` or `application/problem+json` |

### 1.3 Redis Edge Rate Limiting Policies
| Route Group | Predicate | Replenish Rate | Burst Capacity | Resolver Type |
| :--- | :--- | :---: | :---: | :--- |
| **Auth** | `/api/v1/auth/**` | **10 req/s** | **20** | Client IP (`ipKeyResolver`) |
| **Mobile BFF** | `/api/v1/mobile/**` | **50 req/s** | **100** | User Principal / IP (`userOrIpKeyResolver`) |
| **Transactions** | `/api/v1/transactions/**` | **25 req/s** | **50** | User Principal / IP (`userOrIpKeyResolver`) |
| **Ledger** | `/api/v1/ledger/**` | **20 req/s** | **40** | User Principal / IP (`userOrIpKeyResolver`) |
| **Accounts** | `/api/v1/accounts/**` | **50 req/s** | **100** | User Principal / IP (`userOrIpKeyResolver`) |
| **Audit** | `/api/v1/audit/**` | **30 req/s** | **60** | User Principal / IP (`userOrIpKeyResolver`) |
| **Notifications**| `/api/v1/notifications/**`| **50 req/s** | **100** | User Principal / IP (`userOrIpKeyResolver`) |

### 1.4 Handshake H14: Dual-Client Ingress & Gateway CORS Specification
| Client Application | Platform / Target | Port / Base URL | Ingress & CORS Enforcement |
| :--- | :--- | :--- | :--- |
| **React Web Portal** | Browser (Staff & Teller) | `http://localhost:3000` / `:5173` | **Gateway CORS Whitelist**: `allowedOrigins: ["http://localhost:3000", "http://localhost:5173", "http://127.0.0.1:3000", "http://127.0.0.1:5173"]`. Responds to `OPTIONS` pre-flight with 200 OK. |
| **Flutter Mobile App** | Native Android (`app/`) | `http://10.0.2.2:8080` (Emulator) | **Routes to `mobile-bff:8094` via Gateway**. Zero CORS friction: Native OS sockets bypass browser Same-Origin Policy. No `Origin` header sent. |
| **Flutter Web Dev (Opt)**| Browser (Chrome Dev) | `http://localhost:8088` | **Gateway CORS Whitelist**: Fixed port `8088` (`flutter run -d chrome --web-port=8088`) avoids port 3000 collision with React. |

---

## 2. API Endpoint Master Inventory & Summary

```
Total Core Business Endpoints: 49
├── Auth Service:              6 endpoints
├── Mobile BFF Middleware:     3 endpoints  <-- [M1 Dashboard, M2 Stepper, M3 Remittance Dispatch]
├── Account Service:          13 endpoints
├── Customer Service:         10 endpoints
├── Transaction Service:       5 endpoints  <-- [T1, T2, T3, T8, T9 Remittance Shock Absorber]
├── Ledger Service:            4 endpoints
├── Notification Service:      3 endpoints
└── Audit Service:             3 endpoints
Total Infrastructure/Actuator:  5 endpoints
GRAND TOTAL:                   54 endpoints
```

---

## 3. Auth Service Endpoints (`auth-service` :8081)

Base Path via Gateway: `/api/v1/auth`

| # | Method | Path | Required Role | Description |
| :-: | :---: | :--- | :---: | :--- |
| 1 | `POST` | `/api/v1/auth/customers/register` | **REMOVED** | Registration is removed in contracts-v1. Retail members are pre-seeded in identity_db with verified KYC (database/seed/SEED_REFERENCE.md). |
| 2 | `POST` | `/api/v1/auth/customers/login` | Public | Authenticates customer with username/password and returns JWT access & refresh tokens. |
| 3 | `POST` | `/api/v1/auth/staff/login` | Public | Authenticates staff member (TELLER / ADMIN) with username/password. |
| 4 | `POST` | `/api/v1/auth/token/refresh` | Public | Rotates expired access token using valid refresh token cookie/payload. |
| 5 | `POST` | `/api/v1/auth/token/revoke` | Authenticated | Revokes current JWT token and blacklists token ID in Redis. |
| 6 | `GET` | `/api/v1/auth/token/me` | Authenticated | Returns identity, roles, and session metadata for active token holder. |

> **Note on Registration:** `POST /api/v1/auth/customers/register` is REMOVED in target architecture. All test and demo members (14 members, 18 accounts) are pre-seeded across all data stores (see `database/seed/SEED_REFERENCE.md`). Baseline customer credentials: `john_doe` / `Password123!`. Full showcase member: `juan_delacruz` / `Password123!`. Login directly via `POST /api/v1/auth/customers/login`.

---

## 4. Account Lifecycle Endpoints (`account-service` :8082)

Base Path via Gateway: `/api/v1/accounts`

| # | Method | Path | Required Role | Description |
| :-: | :---: | :--- | :---: | :--- |
| 9 | `POST` | `/api/v1/accounts` | `TELLER`, `ADMIN` | Opens a new bank account (`CHECKING`, `SAVINGS`, `LOAN`) for customer. |
| 10 | `GET` | `/api/v1/accounts` | `TELLER`, `ADMIN` | Staff query listing all bank accounts across the platform. |
| 11 | `GET` | `/api/v1/accounts/my-accounts` | `CUSTOMER`, `TELLER`, `ADMIN` | Retrieves all accounts owned by the authenticated customer. |
| 12 | `GET` | `/api/v1/accounts/{accountId}` | `CUSTOMER`, `TELLER`, `ADMIN` | Fetches details and operational metadata for a specific account ID. |
| 13 | `PATCH` | `/api/v1/accounts/{accountId}/status` | `TELLER`, `ADMIN` | Updates account operational status (`ACTIVE`, `FROZEN`, `DORMANT`, `CLOSED`). |
| 14 | `POST` | `/api/v1/accounts/{accountId}/closure-request` | `CUSTOMER`, `TELLER`, `ADMIN` | Submits formal customer request to terminate and close account. |
| 15 | `GET` | `/api/v1/accounts/{accountId}/closure-request` | `CUSTOMER`, `TELLER`, `ADMIN` | Retrieves active closure request for specific account. |
| 16 | `GET` | `/api/v1/accounts/closure-requests` | `TELLER`, `ADMIN` | Staff inbox listing all pending customer account closure requests. |
| 17 | `POST` | `/api/v1/accounts/closure-requests/{id}/approve` | `TELLER`, `ADMIN` | Staff decision approving account closure and setting status to `CLOSED`. |
| 18 | `POST` | `/api/v1/accounts/closure-requests/{id}/reject` | `TELLER`, `ADMIN` | Staff decision rejecting closure request with explanatory rationale. |
| 19 | `POST` | `/api/v1/accounts/{accountId}/flags` | `TELLER`, `ADMIN` | Places administrative flag on account (`SUSPICIOUS_ACTIVITY`, `LEGAL_HOLD`). |
| 20 | `DELETE`| `/api/v1/accounts/{accountId}/flags/{flagId}` | `TELLER`, `ADMIN` | Clears administrative restriction or compliance flag from account. |
| 21 | `GET` | `/api/v1/accounts/{accountId}/flags` | `TELLER`, `ADMIN` | Lists all active and historical compliance flags on an account. |

### Sample Payload: Open Account (`POST /api/v1/accounts`)
```json
{
  "customerId": 1,
  "accountType": "CHECKING",
  "currency": "PHP",
  "initialDeposit": 1000.0000
}
```

---

## 5. Customer & KYC Endpoints (`customer-service` :8089)

Base Path via Gateway: `/api/v1/customers`

| # | Method | Path | Required Role | Description |
| :-: | :---: | :--- | :---: | :--- |
| 22 | `GET` | `/api/v1/customers/me` | `CUSTOMER`, `TELLER`, `ADMIN` | Fetches profile, contact details, and KYC status of authenticated customer. |
| 23 | `GET` | `/api/v1/customers/{customerId}` | `TELLER`, `ADMIN` | Staff lookup to retrieve detailed profile of any registered customer. |
| 24 | `POST` | `/api/v1/customers/kyc/submit` | `CUSTOMER` | Initial customer KYC document and identity verification submission. |
| 25 | `POST` | `/api/v1/customers/kyc/update-request` | `CUSTOMER` | Customer request to update sensitive profile data (e.g. legal name, address). |
| 26 | `GET` | `/api/v1/customers/kyc/update-requests` | `TELLER`, `ADMIN` | Staff queue listing pending KYC verification requests. |
| 27 | `POST` | `/api/v1/customers/kyc/update-requests/{id}/approve` | `TELLER`, `ADMIN` | Staff approval updating customer profile and advancing KYC level. |
| 28 | `POST` | `/api/v1/customers/kyc/update-requests/{id}/reject` | `TELLER`, `ADMIN` | Staff rejection of KYC request with reason message. |
| 28a | `GET` | `/api/v1/customers/{customerId}/beneficiaries` | `CUSTOMER`, `TELLER` | Retrieves saved remittance beneficiaries for quick mobile P2P transfers. |
| 28b | `POST` | `/api/v1/customers/{customerId}/beneficiaries` | `CUSTOMER`, `TELLER` | Saves a new trusted remittance beneficiary with account number and name. |
| 28c | `DELETE`| `/api/v1/customers/{customerId}/beneficiaries/{id}` | `CUSTOMER`, `TELLER` | Deletes a saved remittance beneficiary. |

---

## 6. Remittance Intake & Shock Absorber Endpoints (`transaction-service` :8090)

Base Path via Gateway: `/api/v1/transactions`

| # | Code | Method | Path | Required Role | Idempotent | Description |
| :-: | :---: | :---: | :--- | :---: | :---: | :--- |
| 29 | **T1** | `POST` | `/api/v1/transactions/remittance` | `CUSTOMER`, `TELLER` | **Yes** (`Idempotency-Key`) | Intake shock absorber. Validates solvency pre-flight, locks mutex in Redis (60s), generates `TX-XXXXXX`, triggers Saga, and returns `202 Accepted` immediately. |
| 30 | **T2** | `GET` | `/api/v1/transactions/{trackingReference}` | `CUSTOMER`, `TELLER`, `ADMIN` | No | Real-time status lookup returning current orchestration step and outcome. |
| 31 | **T3** | `GET` | `/api/v1/transactions/{trackingReference}/timeline` | `CUSTOMER`, `TELLER`, `ADMIN` | No | Stepper progress endpoint. Supports `?view=customer` (3-stage member view for Flutter Mobile) and `?view=staff` (11-stage granular audit stepper for React Portal). |
| 32 | **T8** | `GET` | `/api/v1/transactions/my-transactions` | `CUSTOMER` | No | Paginated personal transaction history for authenticated member. |
| 33 | **T9** | `GET` | `/api/v1/transactions/staff/search` | `TELLER`, `ADMIN` | No | Cross-account administrative lookup for audit, reconciliation, and teller support. |

### Sample Payload: Remittance Submission (T1: `POST /api/v1/transactions/remittance`)
```json
{
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "currency": "PHP",
  "remarks": "Payment for agricultural tools"
}
```

### Sample Response: 3-Stage Customer View (T3: `GET /api/v1/transactions/{ref}/timeline?view=customer`)
```json
{
  "trackingReference": "TX-88392101",
  "view": "CUSTOMER",
  "currentStage": 3,
  "totalStages": 3,
  "overallStatus": "COMPLETED",
  "stages": [
    { "stageNumber": 1, "stageKey": "SUBMITTED", "title": "Transfer Submitted", "subtitle": "Your transfer request has been received and verified.", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.180Z" },
    { "stageNumber": 2, "stageKey": "IN_REVIEW", "title": "Security & Clearing", "subtitle": "Automated security screening and bank network clearing.", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.290Z" },
    { "stageNumber": 3, "stageKey": "COMPLETED", "title": "Transfer Completed", "subtitle": "Funds have been successfully deposited into the recipient's account.", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.750Z" }
  ]
}
```

### Sample Response: 11-Stage Staff Stepper (T3: `GET /api/v1/transactions/{ref}/timeline?view=staff`)
```json
{
  "trackingReference": "TX-88392101",
  "view": "STAFF",
  "currentStage": 11,
  "totalStages": 11,
  "overallStatus": "COMPLETED",
  "stages": [
    { "stageNumber": 1, "stageKey": "INGRESS_ACCEPTED", "title": "Ingress Accepted", "service": "transaction-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.050Z" },
    { "stageNumber": 2, "stageKey": "PREFLIGHT_VALIDATED", "title": "Pre-flight Solvency Checked", "service": "transaction-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.120Z" },
    { "stageNumber": 3, "stageKey": "DISPATCHED_TO_ORCHESTRATOR", "title": "Dispatched to Orchestrator", "service": "transaction-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.180Z" },
    { "stageNumber": 4, "stageKey": "SAGA_INITIALIZED", "title": "Saga State Machine Initialized", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.220Z" },
    { "stageNumber": 5, "stageKey": "RISK_EVALUATION_REQUESTED", "title": "Risk Evaluation Requested", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.250Z" },
    { "stageNumber": 6, "stageKey": "RISK_EVALUATION_COMPLETED", "title": "Risk Screening Passed", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.290Z", "details": "Score: 0.12 (Low Risk)" },
    { "stageNumber": 7, "stageKey": "LEDGER_HOLD_RESERVED", "title": "Ledger Balance Hold Reserved", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.340Z" },
    { "stageNumber": 8, "stageKey": "LEDGER_POSTED", "title": "Double-Entry SoR Committed", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.580Z", "details": "Journal Ref: TRF-TX88392101" },
    { "stageNumber": 9, "stageKey": "EVENT_EMITTED", "title": "Domain Event Emitted", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.620Z", "details": "Topic: ledger.transfer.completed.v1" },
    { "stageNumber": 10, "stageKey": "NOTIFICATIONS_DISPATCHED", "title": "Member Notifications Sent", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.700Z" },
    { "stageNumber": 11, "stageKey": "COMPLETED", "title": "Saga Fully Reconciled", "service": "orchestration-service", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.750Z" }
  ]
}
```

---

## 7. Mobile Backend-for-Frontend (BFF) Middleware Endpoints (`mobile-bff` :8094)

Base Path via Gateway: `/api/v1/mobile`

The **Mobile BFF** is an application-level orchestration middleware deployed in the cloud private network (`core-banking-net`). It terminates client requests forwarded by the Gateway, fans out calls in parallel to internal microservices, and aggregates responses into lightweight, screen-optimized JSON payloads for Flutter mobile devices.

| # | Code | Method | Path | Required Role | Idempotent | Description |
| :-: | :---: | :---: | :--- | :---: | :---: | :--- |
| 34 | **M1** | `GET` | `/api/v1/mobile/dashboard` | `CUSTOMER` | No | Screen aggregation endpoint. Concurrently queries customer, account, notification, and transaction services to assemble a complete mobile dashboard in a single round trip (< 150ms over 4G/5G). |
| 35 | **M2** | `GET` | `/api/v1/mobile/transactions/{trackingReference}/timeline` | `CUSTOMER` | No | Stepper adapter middleware. Intercepts downstream 11-stage technical saga from `transaction-service:8090` and translates it into the Flutter 3-stage user-friendly timeline. |
| 36 | **M3** | `POST` | `/api/v1/mobile/transactions/remittance` | `CUSTOMER` | **Yes** (`Idempotency-Key`) | Mobile remittance intake dispatch. Validates payload format and delegates directly to `transaction-service:8090`, propagating tracking headers and returning `202 Accepted`. |

### Sample Response: Mobile Composite Dashboard (M1: `GET /api/v1/mobile/dashboard`)
```json
{
  "member": {
    "customerId": 1,
    "displayName": "John D. Doe",
    "email": "john.doe@example.com",
    "kycStatus": "VERIFIED"
  },
  "primaryAccount": {
    "accountId": 1,
    "accountNumber": "ACC_10000001",
    "accountType": "SAVINGS",
    "currency": "PHP",
    "availableBalance": 50000.00,
    "formattedBalance": "₱50,000.00",
    "status": "ACTIVE"
  },
  "unreadNotifications": 2,
  "recentTransactions": [
    {
      "trackingReference": "TX-20261003-8F3A21C0",
      "amount": 2500.00,
      "formattedAmount": "-₱2,500.00",
      "currency": "PHP",
      "status": "POSTED",
      "remarks": "Lunch money",
      "submittedAt": "2026-10-03T11:40:00Z"
    }
  ]
}
```

---

## 8. Financial Ledger & Balance Mutation Endpoints (`ledger-service` :8083)

Base Path via Gateway: `/api/v1/ledger`

| # | Method | Path | Required Role | Idempotent | Description |
| :-: | :---: | :--- | :---: | :---: | :--- |
| 37 | `POST` | `/api/v1/ledger/transfers`<br>*(alias: `/transfer`)* | `CUSTOMER`, `TELLER`, `ADMIN` | **Yes** | Executes atomic double-entry transfer between source and destination accounts. Enforces pessimistic row locks and publishes Kafka event. |
| 38 | `POST` | `/api/v1/ledger/debits`<br>*(alias: `/debit`)* | `TELLER`, `ADMIN` | **Yes** | Direct ledger debit mutation (withdrawal / fee assessment). |
| 39 | `POST` | `/api/v1/ledger/credits`<br>*(alias: `/credit`)* | `TELLER`, `ADMIN` | **Yes** | Direct ledger credit mutation (deposit / disbursement). |
| 40 | `GET` | `/api/v1/ledger/accounts/{accountId}/balance`<br>*(alias: `/balance/{accountId}`)* | `CUSTOMER`, `TELLER`, `ADMIN` | No | Real-time balance inspection checking Oracle row with Redis caching. |

### Sample Payload: Atomic Transfer (`POST /api/v1/ledger/transfers`)
```json
{
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 125.5000,
  "referenceNo": "REF-TRF-20260929-001",
  "idempotencyKey": "c1ae3937-0048-4af9-b202-4f5603e97fcc"
}
```

---

## 9. In-App Notification Endpoints (`notification-service` :8084)

Base Path via Gateway: `/api/v1/notifications`

| # | Method | Path | Required Role | Description |
| :-: | :---: | :--- | :---: | :--- |
| 41 | `GET` | `/api/v1/notifications/my-notifications` | `CUSTOMER`, `TELLER`, `ADMIN` | Paginated in-app notification feed generated from consumed Kafka events. |
| 42 | `PATCH`| `/api/v1/notifications/{id}/read` | `CUSTOMER`, `TELLER`, `ADMIN` | Marks a specific in-app notification as read. |
| 43 | `GET` | `/api/v1/notifications/unread-count` | `CUSTOMER`, `TELLER`, `ADMIN` | Returns integer count of unread notifications for badge display. |

---

## 10. Forensic Audit & Verification Endpoints (`audit-service` :8085)

Base Path via Gateway: `/api/v1/audit`

| # | Method | Path | Required Role | Description |
| :-: | :---: | :--- | :---: | :--- |
| 44 | `GET` | `/api/v1/audit/accounts/{accountId}/statement` | `CUSTOMER`, `TELLER`, `ADMIN` | Paginated immutable historical statement retrieved from core ledger journal store. |
| 45 | `GET` | `/api/v1/audit/transactions/{transactionId}` | `CUSTOMER`, `TELLER`, `ADMIN` | Deep forensic audit record with actor ID, client IP, timestamp, and hash. |
| 46 | `GET` | `/api/v1/audit/verify-chain/{accountId}` | `CUSTOMER`, `TELLER`, `ADMIN`, `AUDITOR` | Traverses account audit records and recomputes SHA-256 hashes to verify chain integrity. |

---

## 11. Infrastructure & Observability Endpoints

Accessible via Gateway or direct container ports for automated health probes and scrapers:

| # | Method | Path | Target Container | Purpose |
| :-: | :---: | :--- | :---: | :--- |
| 47 | `GET` | `/actuator/health` | `api-gateway` (:8080) | Liveness & readiness probe for edge gateway and downstream routes. |
| 48 | `GET` | `/actuator/gateway/routes` | `api-gateway` (:8080) | Live dynamic route table showing mapped microservice targets. |
| 49 | `GET` | `/actuator/prometheus` | All Services (`:8080`–`:8094`) | Micrometer Prometheus scrape endpoint consumed every 5s by Prometheus. |
| 50 | `GET` | `/ready` | `loki` (:3100) | Loki cluster and ingester readiness probe. |
| 51 | `GET` | `/targets` | `promtail` (:9080) | Promtail Docker container discovery and scraper state. |

---

## 12. Role-Based Access Control (RBAC) Matrix

| Endpoint Group | `ROLE_CUSTOMER` | `ROLE_TELLER` | `ROLE_ADMIN` | `ROLE_AUDITOR` |
| :--- | :---: | :---: | :---: | :---: |
| **Auth - Self / Token** | Yes | Yes | Yes | Yes |
| **Auth - Staff Login** | No | Yes | Yes | Yes |
| **Mobile BFF - Dashboard (M1)** | Yes (Own) | No | No | No |
| **Mobile BFF - 3-Stage Stepper (M2)** | Yes (Own) | No | No | No |
| **Mobile BFF - Submit Remittance (M3)** | Yes (Own) | No | No | No |
| **Accounts - View Own** | Yes | Yes | Yes | Yes |
| **Accounts - Open / Manage All** | No | Yes | Yes | No |
| **Accounts - Closure Requests** | Submit Own | Approve/Reject | Approve/Reject | View |
| **Accounts - Flags & Restrictions**| No | Yes | Yes | View |
| **Customers - KYC Submit** | Yes | No | No | No |
| **Customers - KYC Approve/Reject**| No | Yes | Yes | No |
| **Transactions - Submit Remittance (T1)**| Yes | Yes | Yes | No |
| **Transactions - Status & 3-Stage Timeline (T2, T3)**| Yes (Own) | Yes | Yes | View |
| **Transactions - 11-Stage Staff Stepper (T3)**| No | Yes | Yes | View |
| **Transactions - My History (T8)**| Yes (Own) | Yes (Own) | Yes (Own) | View |
| **Transactions - Global Search (T9)**| No | Yes | Yes | Yes |
| **Ledger - Atomic Transfers** | Yes | Yes | Yes | No |
| **Ledger - Direct Debits/Credits** | No | Yes | Yes | No |
| **Notifications - Feed & Read** | Yes (Own) | Yes (Own) | Yes (Own) | Yes (Own) |
| **Audit - Statements & Forensics** | Yes (Own) | Yes | Yes | Yes |
| **Audit - Hash Chain Verification** | Yes (Own) | Yes | Yes | Yes |
