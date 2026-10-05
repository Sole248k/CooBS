# CooBS Core Remittance Platform — REST API Design Specification
## Version: 2.1.0 (Decomposed Remittance & Cooperative Banking Architecture)
### Base Architecture: `docs/update/CooBS_Core_Remittance_Architecture.md` & `docs/update/CooBS_Services_Architecture_Breakdown.md`

---

## 1. Executive Summary & Architectural Evolution

This document specifies the revised REST API contracts for the **CooBS Core Remittance Architecture** (Capstone 2). Under this updated architecture:

1. **The legacy Account Service is decomposed into two autonomous microservices:**
   * **`customer-service` (Port 8089):** Dedicated to Member Identity, Personal Demographic Information (PII), and KYC Demographic Verification & Staged Update Management.
   * **`account-service` (Port 8082):** Dedicated to Deposit Account Open/Close Lifecycles, Read-Optimized Balance Projections (Redis-backed), Account Statuses, and Legal/Administrative Flags.

2. **The legacy Ledger Service ingress is decomposed to decouple client intake from double-entry posting:**
   * **`transaction-service` (Port 8090):** Introduced as the **Remittance Ingress Gateway & Shock Absorber**. Enforces edge idempotency (Redis `SETNX`), performs pre-flight limit and KYC verification checks, assigns tracking references (`TX-XXXXXX`), writes `PENDING` states to `transaction_db`, and triggers the saga.
   * **`orchestration-service` (Port 8091):** Manages the durable multi-service Saga workflow ($\text{PENDING\_RISK} \to \text{RISK\_APPROVED} \to \text{CORE\_PROCESSING} \to \text{COMPLETED / REJECTED}$) and coordinates automated compensating reversals.
   * **`risk-engine` (Port 8092):** Performs sub-40ms algorithmic AML, velocity, and sanction list screening.
   * **`ledger-service` (Port 8083 - SoR):** Shielded from direct client HTTP ingress. Serves strictly as the internal double-entry system of record enforcing deadlock-free ordered pessimistic row locking, zero-overdraft invariants, atomic double-entry ledger journal auditing, and post-commit Kafka event publishing.

---

## 2. Global Standards, Headers & Error Conventions

### 2.1 Service Ingress Topology & Ports

| Service | Port | Base Path | Ingress Type | Primary Domain Responsibility |
| :--- | :---: | :--- | :--- | :--- |
| **API Gateway** | `8080` | `/api/v1/**` | Public Edge | SSL Termination, Rate Limiting, Route Forwarding |
| **`auth-service`** | `8081` | `/api/v1/auth/**` | Client / Staff | JWT Authentication, Key Distribution, Session Revocation |
| **`customer-service`** | `8089` | `/api/v1/customers/**` | Client / Staff | Member Identity, KYC Profile Verification & Updates, PII Protection |
| **`account-service`** | `8082` | `/api/v1/accounts/**` | Client / Staff | Account Lifecycle, Flags, Cached Balance Lookups |
| **`transaction-service`** | `8090` | `/api/v1/transactions/**` | Client / Staff | Remittance Intake, Ingress Idempotency, Limit Checks |
| **`mobile-bff`** | `8093` | `/api/v1/mobile/**` | Client / Mobile | Mobile BFF Application Middleware, Multi-Service Composite Aggregation, 3-Stage Stepper Adaptation |
| **`orchestration-service`**| `8091` | `/api/v1/internal/saga/**`| Internal | Saga Coordination, Risk/Ledger Hand-off, Compensations |
| **`risk-engine`** | `8092` | `/api/v1/internal/risk/**`| Internal | Sub-40ms AML & Fraud Velocity Screening |
| **`ledger-service`** | `8083` | `/api/v1/internal/ledger/**`| Internal | Double-Entry SoR, Ordered Row Locks, Immutable Journal |
| **`notification-service`** | `8084` | `/api/v1/notifications/**`| Client / Internal| In-App Notifications, SMS/Push Alerts via Kafka |
| **`audit-service`** | `8085` | `/api/v1/audit/**` | Auditor / Staff | Forensic Audit Verification & Ledger Journal Query |

---

### 2.2 Standard HTTP Request Headers

| Header Name | Type | Requirement | Usage & Semantics |
| :--- | :--- | :--- | :--- |
| `Authorization` | String | Mandatory on protected routes | Format: `Bearer <RSA256_JWT_TOKEN>` |
| `Idempotency-Key` | UUIDv4 | Mandatory on mutating requests (`POST`, `PUT`) | Prevents duplicate charges during rapid mobile re-taps. Intercepted by `transaction-service` via Redis. |
| `X-Correlation-ID` | UUIDv4 | Optional (Gateway generates if missing) | End-to-end distributed trace identifier passed across HTTP and Kafka hops. |
| `Content-Type` | String | Requests with body | Must be `application/json; charset=UTF-8` |
| `Accept` | String | All requests | Must be `application/json` or `application/problem+json` |

---

### 2.3 Precision & Data Formats

* **Monetary Precision:** Fixed-point numeric string or `BigDecimal` strictly constrained to `scale: 4` and `RoundingMode.HALF_UP` (e.g., `5000.0000 PHP`). Binary floating points (`float`, `double`) are strictly prohibited.
* **Currency:** Philippine Peso (`PHP`), ISO-4217 standard.
* **Timestamps:** ISO-8601 UTC string format: `YYYY-MM-DDTHH:mm:ss.sssZ` (e.g., `2026-10-02T14:30:00.000Z`).
* **Account Numbers:** Format `ACC_` followed by an 8-digit sequence (e.g., `ACC_10000001`).
* **Transaction Tracking Reference:** Format `TX-` followed by an 8-digit alphanumeric sequence (e.g., `TX-88392101`).

---

### 2.4 Standard RFC-7807 Error Envelope

All service endpoints return `application/problem+json` error responses:

```json
{
  "type": "https://api.coobs.local/errors/TRANSACTION_LIMIT_EXCEEDED",
  "title": "Daily Remittance Limit Exceeded",
  "status": 422,
  "detail": "Requested amount ₱55,000.0000 exceeds daily remittance limit of ₱50,000.0000.",
  "instance": "/api/v1/transactions/remittance",
  "errorCode": "TRANSACTION_LIMIT_EXCEEDED",
  "timestamp": "2026-10-02T14:30:00.000Z",
  "invalidParams": [
    {
      "name": "amount",
      "reason": "Maximum daily remittance allowance is 50000.0000 PHP"
    }
  ]
}
```

---

## 3. Customer Service API (`/api/v1/customers/**` — Port 8089)

The **Customer Service** is the authoritative master for member identity, personal demographic information, and KYC verification records. It protects Personally Identifiable Information (PII) under strict data privacy regulations.

### 3.1 Customer Profile & Identification

#### `GET /api/v1/customers/me`
* **Access:** `ROLE_CUSTOMER`
* **Description:** Retrieves the authenticated member's personal profile and current demographic KYC record.

##### Response Body (`200 OK`):
```json
{
  "customerId": 101,
  "username": "juan_delacruz",
  "email": "juan.delacruz@coop.local",
  "kycStatus": "VERIFIED",
  "kyc": {
    "kycId": 201,
    "firstName": "Juan",
    "middleInitial": "D",
    "lastName": "Dela Cruz",
    "address": "Barangay San Isidro, Nueva Ecija",
    "mobileNumber": "+639171234567",
    "civilStatus": "MARRIED",
    "occupation": "Rice Farmer / Agricultural Producer",
    "status": "VERIFIED",
    "createdAt": "2026-09-15T08:00:00.000Z"
  },
  "createdAt": "2026-09-15T08:00:00.000Z"
}
```

---

#### `GET /api/v1/customers/{customerId}`
* **Access:** `ROLE_TELLER`, `ROLE_ADMIN`
* **Description:** Internal staff endpoint to query customer demographic profile and KYC verification record by customer ID.

##### Response Body (`200 OK`):
```json
{
  "customerId": 101,
  "username": "juan_delacruz",
  "email": "juan.delacruz@coop.local",
  "kycStatus": "VERIFIED",
  "kyc": {
    "kycId": 201,
    "firstName": "Juan",
    "middleInitial": "D",
    "lastName": "Dela Cruz",
    "address": "Barangay San Isidro, Nueva Ecija",
    "mobileNumber": "+639171234567",
    "civilStatus": "MARRIED",
    "occupation": "Rice Farmer / Agricultural Producer",
    "status": "VERIFIED",
    "createdAt": "2026-09-15T08:00:00.000Z"
  },
  "createdAt": "2026-09-15T08:00:00.000Z"
}
```

---

### 3.2 Demographic KYC Verification & Staged Updates

KYC verification in CooBS is managed through demographic information submission, staged modification requests, and branch staff evaluation.

#### `POST /api/v1/customers/kyc/submit`
* **Access:** `ROLE_CUSTOMER`
* **Description:** Customer submits initial demographic KYC verification details.

##### Request Body (`KycSubmitRequest`):
```json
{
  "firstName": "Juan",
  "middleInitial": "D",
  "lastName": "Dela Cruz",
  "address": "Barangay San Isidro, Nueva Ecija",
  "civilStatus": "MARRIED",
  "occupation": "Rice Farmer / Agricultural Producer",
  "mobileNumber": "+639171234567"
}
```

##### Response Body (`201 Created` — `KycRequestResponse`):
```json
{
  "kycId": 201,
  "customerId": 101,
  "status": "VERIFIED",
  "message": "Initial KYC demographic details submitted successfully."
}
```

---

#### `POST /api/v1/customers/kyc/update-request`
* **Access:** `ROLE_CUSTOMER`
* **Description:** Submits a staged modification to customer demographic details requiring teller or branch manager review.

##### Request Body (`KycUpdateRequestDto`):
```json
{
  "newFirstName": "Juan",
  "newMiddleInitial": "D",
  "newLastName": "Dela Cruz",
  "newAddress": "Poblacion, Cabanatuan City, Nueva Ecija",
  "newMobileNumber": "+639189998888",
  "newCivilStatus": "MARRIED",
  "newOccupation": "Agricultural Cooperative Manager"
}
```

##### Response Body (`202 Accepted` — `KycRequestResponse`):
```json
{
  "kycRequestId": 302,
  "customerId": 101,
  "status": "PENDING",
  "requestedAt": "2026-10-02T14:35:00.000Z",
  "message": "KYC update request submitted for staff review."
}
```

---

#### `GET /api/v1/customers/kyc/update-requests`
* **Access:** `ROLE_TELLER`, `ROLE_ADMIN`
* **Query Parameters:** `status=PENDING` (optional), `page=0`, `size=20`
* **Description:** Retrieves paginated list of pending KYC update requests for branch staff review.

##### Response Body (`200 OK` — `PageResponse<KycRequestResponse>`):
```json
{
  "content": [
    {
      "kycRequestId": 302,
      "customerId": 101,
      "status": "PENDING",
      "requestedAt": "2026-10-02T14:35:00.000Z",
      "newAddress": "Poblacion, Cabanatuan City, Nueva Ecija",
      "newMobileNumber": "+639189998888",
      "newCivilStatus": "MARRIED",
      "newOccupation": "Agricultural Cooperative Manager"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

---

#### `POST /api/v1/customers/kyc/update-requests/{id}/approve`
* **Access:** `ROLE_TELLER`, `ROLE_ADMIN`
* **Description:** Staff approves the pending request. Merges changes into the customer's master `KYC` record and transitions request status to `APPROVED`. Emits `kyc.request.evaluated.v1` event to Kafka.

##### Response Body (`200 OK` — `KycRequestResponse`):
```json
{
  "kycRequestId": 302,
  "customerId": 101,
  "status": "APPROVED",
  "approvedBy": 2,
  "reviewedAt": "2026-10-02T14:45:00.000Z",
  "message": "Customer KYC demographic update approved and merged successfully."
}
```

---

#### `POST /api/v1/customers/kyc/update-requests/{id}/reject`
* **Access:** `ROLE_TELLER`, `ROLE_ADMIN`
* **Description:** Staff rejects the pending KYC update request with a recorded justification.

##### Request Body (`KycRejectRequest`):
```json
{
  "rejectionReason": "Address provided does not match municipal residency records."
}
```

##### Response Body (`200 OK` — `KycRequestResponse`):
```json
{
  "kycRequestId": 302,
  "customerId": 101,
  "status": "REJECTED",
  "reviewedAt": "2026-10-02T14:45:00.000Z",
  "rejectionReason": "Address provided does not match municipal residency records.",
  "message": "KYC update request rejected."
}
```

---

## 4. Account Service API (`/api/v1/accounts/**` — Port 8082)

The **Account Service** manages deposit account lifecycles (`ACTIVE`, `FROZEN`, `CLOSED`), high-speed cached balance lookups (via Redis), account closure workflows, and administrative/AML flags.

> [!NOTE]
> By decoupling balance reads from transactional ledger writes, tens of thousands of members checking balances read from this cached service without competing for database row locks against branch tellers making physical cash deposits.

### 4.1 Account Opening & Inquiries

#### `POST /api/v1/accounts`
* **Access:** `ROLE_TELLER`, `ROLE_ADMIN`
* **Headers:** `Idempotency-Key: <UUID>`
* **Description:** Opens a new deposit account under an existing verified customer. Calls `customer-service` internally to confirm KYC status before opening.

##### Request Body:
```json
{
  "customerId": 101,
  "accountType": "SAVINGS",
  "currency": "PHP"
}
```

##### Response Body (`201 Created`):
```json
{
  "accountId": 501,
  "customerId": 101,
  "accountNumber": "ACC_10000005",
  "accountType": "SAVINGS",
  "currency": "PHP",
  "status": "ACTIVE",
  "availableBalance": 0.0000,
  "createdAt": "2026-10-02T14:50:00.000Z"
}
```

---

#### `GET /api/v1/accounts/my-accounts`
* **Access:** `ROLE_CUSTOMER`
* **Description:** Retrieves all accounts owned by the authenticated customer.

##### Response Body (`200 OK`):
```json
[
  {
    "accountId": 501,
    "accountNumber": "ACC_10000005",
    "accountType": "SAVINGS",
    "currency": "PHP",
    "status": "ACTIVE",
    "availableBalance": 50000.0000,
    "hasActiveFlags": false
  }
]
```

---

#### `GET /api/v1/accounts/{accountId}/balance`
* **Access:** `ROLE_CUSTOMER`, `ROLE_TELLER`
* **Description:** Read-optimized balance lookup served from Redis cache (`account:balance:{id}`, TTL=10s). Cache misses fall back to Oracle `BALANCE` read-only projections.

##### Response Body (`200 OK`):
```json
{
  "accountId": 501,
  "accountNumber": "ACC_10000005",
  "currency": "PHP",
  "availableBalance": 50000.0000,
  "asOfTimestamp": "2026-10-02T14:55:00.000Z",
  "isCached": true
}
```

---

### 4.2 Account Closure Lifecycle

#### `POST /api/v1/accounts/{accountId}/closure-request`
* **Access:** `ROLE_CUSTOMER`
* **Headers:** `Idempotency-Key: <UUID>`
* **Description:** Member submits a request to close an account.
  * Backend verifies account ownership.
  * Backend enforces zero-balance invariant: rejects with `400 Bad Request` (`ACCOUNT_NON_ZERO_BALANCE`) if `availableBalance > 0.0000`.
  * Verifies that no active legal holds or AML flags exist.

##### Request Body:
```json
{
  "reason": "Transferring all savings to cooperative share capital account."
}
```

##### Response Body (`202 Accepted`):
```json
{
  "closureRequestId": 12,
  "accountId": 501,
  "accountNumber": "ACC_10000005",
  "status": "PENDING_APPROVAL",
  "requestedAt": "2026-10-02T15:00:00.000Z",
  "message": "Account closure request submitted for branch manager review."
}
```

---

#### `POST /api/v1/accounts/closure-requests/{id}/approve`
* **Access:** `ROLE_ADMIN`
* **Description:** Manager approves account closure.
  * Updates `ACCOUNT.status = 'CLOSED'`.
  * **Customer Integrity Guarantee:** The `CUSTOMER` record remains active. Only the specific `ACCOUNT` is closed.
  * Emits `account.closure.completed.v1` event to Kafka.

##### Response Body (`200 OK`):
```json
{
  "closureRequestId": 12,
  "accountId": 501,
  "accountNumber": "ACC_10000005",
  "accountStatus": "CLOSED",
  "customerStatus": "ACTIVE",
  "approvedBy": 1,
  "reviewedAt": "2026-10-02T15:05:00.000Z",
  "message": "Account ACC_10000005 has been closed. Member profile remains active."
}
```

---

### 4.3 Administrative Holds & Legal Flags

#### `POST /api/v1/accounts/{accountId}/flags`
* **Access:** `ROLE_TELLER`, `ROLE_ADMIN`
* **Description:** Applies an administrative, court-ordered, or AML hold (`ACCOUNT_FLAG`), preventing debits and withdrawals.

##### Request Body:
```json
{
  "reason": "Court Order Freeze / Subpoena Reference RTC-2026-101",
  "flagType": "LEGAL_FREEZE"
}
```

##### Response Body (`201 Created`):
```json
{
  "flagId": 44,
  "accountId": 501,
  "status": "ACTIVE",
  "reason": "Court Order Freeze / Subpoena Reference RTC-2026-101",
  "flaggedBy": 1,
  "flaggedAt": "2026-10-02T15:10:00.000Z"
}
```

---

#### `DELETE /api/v1/accounts/{accountId}/flags/{flagId}`
* **Access:** `ROLE_ADMIN`
* **Description:** Formally lifts an administrative hold.
##### Response Body (`200 OK`):
```json
{
  "flagId": 44,
  "accountId": 501,
  "status": "LIFTED",
  "removedBy": 1,
  "removedAt": "2026-10-02T15:15:00.000Z",
  "message": "Administrative hold lifted successfully."
}
```

---

## 5. Transaction Service API (`/api/v1/transactions/**` — Port 8090)

The **Transaction Service** is the **Remittance Ingress Gateway & Shock Absorber**. It handles incoming customer transfers, enforces edge idempotency, executes pre-flight tier-limit checks, assigns tracking references, writes `PENDING` states to `transaction_db`, and triggers the saga orchestrator.

### 5.1 Remittance Intake Endpoint

#### `POST /api/v1/transactions/remittance`
* **Access:** `ROLE_CUSTOMER`, `ROLE_TELLER`
* **Headers:**
  * `Authorization: Bearer <JWT>`
  * `Idempotency-Key: <UUID>` (Mandatory)
* **Lifecycle & Shock Absorber Rules:**
  1. **Idempotency Protection (Redis `SETNX`):** Acquires lock with key `idempotency:remittance:{key}` (60s TTL). Duplicate requests within 60s return `409 Conflict` (or cached response if completed).
  2. **Pre-flight KYC & Limit Verification:** Queries `customer-service` cache to verify that the customer's KYC status is `VERIFIED` and the transfer amount does not exceed the standard daily remittance limit (e.g. ₱50,000/day).
  3. **Initiation Receipt:** Writes an intent record to `transaction_db` with status `PENDING`, assigns public tracking reference (`TX-88392101`).
  4. **Saga Dispatch:** Hands off the transaction to `orchestration-service` (Port 8091).
  5. **Immediate Client Acknowledgement:** Returns `202 Accepted` immediately so the mobile app does not freeze waiting on downstream services.

##### Request Body:
```json
{
  "sourceAccountId": 1,
  "destinationAccountNumber": "ACC_10000002",
  "destinationBankCode": "COOBS",
  "amount": "2500.00",
  "currency": "PHP",
  "category": "TRANSFER",
  "remarks": "Payment for fertilizer supply"
}
```
*Note: In omnichannel and mobile requests, `destinationAccountNumber` is primary. For internal backwards-compatibility, `destinationAccountId: 2` is also accepted.*

##### Success Response (`202 Accepted`):
```json
{
  "trackingReference": "TX-88392101",
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "currency": "PHP",
  "status": "PENDING",
  "submittedAt": "2026-10-02T15:20:00.000Z",
  "message": "Remittance request accepted for processing. Tracking reference assigned."
}
```

##### Error Response (`422 Unprocessable Entity` — Transaction Limit Exceeded):
```json
{
  "type": "https://api.coobs.local/errors/TRANSACTION_LIMIT_EXCEEDED",
  "title": "Daily Remittance Limit Exceeded",
  "status": 422,
  "detail": "Requested amount 55000.0000 PHP exceeds standard daily allowance of 50000.0000 PHP.",
  "errorCode": "TRANSACTION_LIMIT_EXCEEDED",
  "timestamp": "2026-10-02T15:20:00.000Z"
}
```

##### Error Response (`409 Conflict` — In-Flight Duplicate):
```json
{
  "type": "https://api.coobs.local/errors/CONCURRENT_TRANSACTION_IN_FLIGHT",
  "title": "Transaction Already In Progress",
  "status": 409,
  "detail": "A transaction with this Idempotency-Key is currently being processed. Duplicate submission blocked.",
  "errorCode": "CONCURRENT_TRANSACTION_IN_FLIGHT",
  "timestamp": "2026-10-02T15:20:01.000Z"
}
```

---

### 5.2 Remittance Status Tracking & History

#### `GET /api/v1/transactions/{trackingReference}`
* **Access:** `ROLE_CUSTOMER`, `ROLE_TELLER`
* **Description:** Allows mobile applications and tellers to poll the state machine status of a submitted remittance.

##### Response Body (`200 OK` — In Progress):
```json
{
  "trackingReference": "TX-88392101",
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "currency": "PHP",
  "status": "IN_ORCHESTRATION",
  "currentStep": "RISK_EVALUATION",
  "submittedAt": "2026-10-02T15:20:00.000Z",
  "completedAt": null
}
```

##### Response Body (`200 OK` — Completed):
```json
{
  "trackingReference": "TX-88392101",
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "currency": "PHP",
  "status": "COMPLETED",
  "currentStep": "POSTED_TO_LEDGER",
  "ledgerJournalReference": "TRF-TX88392101",
  "submittedAt": "2026-10-02T15:20:00.000Z",
  "completedAt": "2026-10-02T15:20:01.250Z"
}
```

---

#### `GET /api/v1/transactions/{trackingReference}/timeline` (Endpoint T3)
* **Access:** `ROLE_CUSTOMER`, `ROLE_TELLER`, `ROLE_ADMIN`
* **Query Parameters:** `view=staff` (default for staff, returns all 11 saga stages) or `view=customer` (consolidated 3-stage customer view).
* **Description:** Exposes real-time timeline stepper status tracking the transaction progression through the orchestration saga.

##### Query Parameters:
| Parameter | Type | Required | Values | Default | Description |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `view` | String | No | `staff`, `customer` | `staff` (if staff role), `customer` (if member role) | Controls stage granularity returned. |

##### Response Body (`200 OK` — `view=staff` Full 11-Stage Saga):
```json
{
  "trackingReference": "TX-88392101",
  "view": "staff",
  "overallStatus": "IN_PROGRESS",
  "stages": [
    {"stage": "INTAKE_VALIDATED", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.000Z", "order": 1},
    {"stage": "IDEMPOTENCY_LOCKED", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.020Z", "order": 2},
    {"stage": "SAGA_INITIALIZED", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.050Z", "order": 3},
    {"stage": "CUSTOMER_VERIFIED", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.080Z", "order": 4},
    {"stage": "ACCOUNTS_VALIDATED", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.100Z", "order": 5},
    {"stage": "SOURCE_FUNDS_HELD", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.140Z", "order": 6},
    {"stage": "RISK_SCREENING_EVALUATED", "status": "COMPLETED", "timestamp": "2026-10-02T15:20:00.180Z", "order": 7},
    {"stage": "DESTINATION_CREDITED", "status": "IN_PROGRESS", "timestamp": "2026-10-02T15:20:00.220Z", "order": 8},
    {"stage": "SOURCE_FUNDS_CAPTURED", "status": "PENDING", "timestamp": null, "order": 9},
    {"stage": "LEDGER_COMMITTED", "status": "PENDING", "timestamp": null, "order": 10},
    {"stage": "COMPLETED", "status": "PENDING", "timestamp": null, "order": 11}
  ]
}
```

##### Response Body (`200 OK` — `view=customer` 3-Stage Consolidated):
```json
{
  "trackingReference": "TX-88392101",
  "view": "customer",
  "overallStatus": "IN_PROGRESS",
  "stages": [
    {
      "stage": "SUBMITTED",
      "status": "COMPLETED",
      "timestamp": "2026-10-02T15:20:00.000Z",
      "title": "Remittance Submitted",
      "description": "Transfer request received and accepted for processing."
    },
    {
      "stage": "PROCESSING",
      "status": "IN_PROGRESS",
      "timestamp": "2026-10-02T15:20:00.100Z",
      "title": "Security & Ledger Processing",
      "description": "Validating accounts, running fraud screening, and posting to ledger."
    },
    {
      "stage": "COMPLETED",
      "status": "PENDING",
      "timestamp": null,
      "title": "Transfer Completed",
      "description": "Funds credited to destination account."
    }
  ]
}
```

---

#### `GET /api/v1/transactions/my-transactions`
* **Access:** `ROLE_CUSTOMER`
* **Query Parameters:** `page=0`, `size=20`, `status=COMPLETED`
* **Description:** Retrieves paginated transaction records belonging to the authenticated customer from `transaction_db`.

---

### 5.3 Mobile BFF Application Middleware Endpoints (`/api/v1/mobile/**` — Port 8094)

The **Mobile BFF** (`mobile-bff`) is an application-level orchestration middleware deployed in the cloud behind Spring Cloud Gateway. It provides multi-service aggregation, response trimming, and UI-specific adapters for the Flutter mobile application (`app/`).

#### `GET /api/v1/mobile/dashboard` (Endpoint M1)
* **Access:** `ROLE_CUSTOMER`
* **Description:** Composite dashboard aggregation. Collates member profile (`customer-service:8089`), deposit accounts & balances (`account-service:8082`), recent remittances (`transaction-service:8090`), and unread notifications (`notification-service:8084`) into a single mobile response.
* **Network Advantage:** Reduces 4 sequential mobile HTTP roundtrips over cellular networks to 1 single composite call.

##### Response Body (`200 OK`):
```json
{
  "member": {
    "customerId": 101,
    "displayName": "Juan Dela Cruz",
    "kycStatus": "VERIFIED",
    "unreadNotifications": 2
  },
  "accounts": [
    {
      "accountId": 501,
      "accountNumber": "ACC_10000005",
      "accountType": "SAVINGS",
      "currency": "PHP",
      "balance": 50000.0000,
      "status": "ACTIVE"
    }
  ],
  "recentTransactions": [
    {
      "trackingReference": "TX-88392101",
      "amount": 5000.0000,
      "currency": "PHP",
      "status": "COMPLETED",
      "destinationAccountNumber": "ACC_10000006",
      "timestamp": "2026-10-02T15:20:01.250Z"
    }
  ]
}
```

---

#### `GET /api/v1/mobile/transactions/{trackingReference}/timeline` (Endpoint M2)
* **Access:** `ROLE_CUSTOMER`
* **Description:** Mobile 3-Stage Stepper Adapter. Proxies to `transaction-service:8090/api/v1/transactions/{trackingReference}/timeline?view=customer` to render Flutter's `CustomerProgressStepper`.

---

#### `POST /api/v1/mobile/transactions/remittance` (Endpoint M3)
* **Access:** `ROLE_CUSTOMER`
* **Headers:** `Idempotency-Key: <UUID>` (Mandatory)
* **Description:** Remittance intake pass-through. Validates payload before forwarding to `transaction-service:8090/api/v1/transactions/remittance`.

---

## 6. Internal Orchestration & Risk Engine Contracts (Private Mesh)

These endpoints are strictly internal and accessible only across the internal service mesh (`core-banking-net`).

### 6.1 Orchestration Service (`/api/v1/internal/saga/**` — Port 8091)

#### `POST /api/v1/internal/saga/remittance`
* **Caller:** `transaction-service`
* **Description:** Starts the durable Saga state machine:
  $$\text{PENDING\_RISK} \longrightarrow \text{RISK\_APPROVED} \longrightarrow \text{CORE\_PROCESSING} \longrightarrow \text{COMPLETED / REJECTED}$$

##### Request Body:
```json
{
  "trackingReference": "TX-88392101",
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "currency": "PHP",
  "sourceCustomerId": 101,
  "clientIp": "192.168.1.100",
  "deviceId": "dev-and-99214-a"
}
```

##### Response Body (`200 OK`):
```json
{
  "sagaId": "saga_49204-99124-ab",
  "trackingReference": "TX-88392101",
  "sagaStatus": "COMPLETED",
  "riskEvaluation": "APPROVED",
  "ledgerPosting": "COMMITTED"
}
```

---

### 6.2 Risk Engine (`/api/v1/internal/risk/**` — Port 8092)

#### `POST /api/v1/internal/risk/evaluate`
* **Caller:** `orchestration-service`
* **Description:** Evaluates transaction velocity, geo-IP anomalies, and AML sanction lists in $< 40\text{ms}$.

##### Request Body:
```json
{
  "trackingReference": "TX-88392101",
  "customerId": 101,
  "sourceAccountId": 501,
  "amount": 5000.0000,
  "currency": "PHP",
  "clientIp": "192.168.1.100",
  "timestamp": "2026-10-02T15:20:00.100Z"
}
```

##### Response Body (`200 OK`):
```json
{
  "riskScore": 0.12,
  "decision": "APPROVED",
  "reason": "Normal velocity; domestic IP; no watchlist hits.",
  "latencyMs": 28
}
```

---

## 7. Ledger Service Internal API (`/api/v1/internal/ledger/**` — Port 8083)

The **Ledger Service** is the internal double-entry transactional heart. It is invoked exclusively by `orchestration-service` after risk validation passes.

> [!IMPORTANT]
> **Ledger Core Invariants Enforced:**
> 1. **Ordered Pessimistic Locking:** Always locks accounts in ascending order $\min(\text{src}, \text{dst})$ then $\max(\text{src}, \text{dst})$ with a 5000ms database lock timeout.
> 2. **Zero-Overdraft Invariant ($B \ge 0.0000$):** Rejects any debit that would result in a negative balance.
> 3. **Atomic Double-Entry Ledger Journal:** Writes balanced debit and credit entries ($\sum \text{debits} = \sum \text{credits}$) directly to the immutable Oracle DB ledger journal table within the same ACID transaction boundary.
> 4. **Post-Commit Event Dispatch:** Publishes events to Kafka strictly on `TransactionPhase.AFTER_COMMIT`.

### 7.1 Internal Double-Entry Fund Transfer

#### `POST /api/v1/internal/ledger/post-transfer`
* **Caller:** `orchestration-service`
* **Headers:** `X-Saga-ID: <UUID>`

##### Request Body:
```json
{
  "transferReference": "TRF-TX88392101",
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "remarks": "Remittance TX-88392101"
}
```

##### Response Body (`200 OK`):
```json
{
  "transferReference": "TRF-TX88392101",
  "sourceAccountId": 501,
  "destinationAccountId": 502,
  "amount": 5000.0000,
  "sourcePreviousBalance": 50000.0000,
  "sourceNewBalance": 45000.0000,
  "destinationPreviousBalance": 10000.0000,
  "destinationNewBalance": 15000.0000,
  "status": "COMPLETED",
  "postedAt": "2026-10-02T15:20:00.800Z"
}
```

---

### 7.2 Single-Account Internal Mutations (Debits & Credits)

* **`POST /api/v1/internal/ledger/debit`** (For internal cash withdrawals, loan disbursements, or fee deductions).
* **`POST /api/v1/internal/ledger/credit`** (For internal cash deposits, dividend payments, or refunds).

---

## 8. Decomposition & Changes Matrix

This section documents the specific changes transitioning from the legacy monolithic architecture (`backend/account-service` and `backend/ledger-service`) to the modernized CooBS services (`customer-service`, `account-service`, `transaction-service`, and `ledger-service`).

### 8.1 Account Service Decomposition Matrix

| Legacy Endpoint / Behavior | Former Location | New Location & Route | Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Customer Registration & Profile**<br/>`POST /api/v1/auth/customer/register`<br/>`GET /api/v1/customers/me` | `account-service` (`CustomerController.java`) | **`customer-service` (Port 8089)**<br/>`GET /api/v1/customers/me`<br/>`GET /api/v1/customers/{id}` | Separates Personally Identifiable Information (PII) from financial account records; isolates data privacy compliance (GDPR / Data Privacy Act). |
| **KYC Submission & Staged Updates**<br/>`POST /api/v1/customers/kyc/submit`<br/>`POST /api/v1/customers/kyc/update-request`<br/>`GET /api/v1/customers/kyc/update-requests`<br/>`POST .../approve`, `POST .../reject` | `account-service` (`CustomerController.java`) | **`customer-service` (Port 8089)**<br/>`POST /api/v1/customers/kyc/submit`<br/>`POST /api/v1/customers/kyc/update-request`<br/>`GET /api/v1/customers/kyc/update-requests`<br/>`POST .../approve`, `POST .../reject` | Manages demographic identity verification and staged modifications reviewed by branch staff without handling financial posting. |
| **Deposit Account Opening**<br/>`POST /api/v1/accounts` | `account-service` (`AccountController.java`) | **`account-service` (Port 8082)**<br/>`POST /api/v1/accounts` | Account service manages account lifecycles independently of customer personal demographic updates. |
| **Customer Account Listing**<br/>`GET /api/v1/accounts/my-accounts` | `account-service` (`AccountController.java`) | **`account-service` (Port 8082)**<br/>`GET /api/v1/accounts/my-accounts` | Returns user's accounts, states (`ACTIVE`, `FROZEN`), and balances. |
| **Balance Inquiries & Caching**<br/>`GET /api/v1/ledger/accounts/{id}/balance` | Previously in `ledger-service` (`BalanceController.java`) | **`account-service` (Port 8082)**<br/>`GET /api/v1/accounts/{id}/balance` | **Moved from Ledger to Account Service:** High-volume read queries (e.g. 30,000 members checking balances on payday) hit Redis/Account Service, offloading Oracle row locks from the core ledger. |
| **Account Closure Workflow**<br/>`POST /api/v1/accounts/{id}/closure-request`<br/>`POST .../approve`, `.../reject` | `account-service` (`AccountController.java`) | **`account-service` (Port 8082)**<br/>`POST /api/v1/accounts/{id}/closure-request`<br/>`POST .../approve`, `.../reject` | Enforces zero-balance requirement and closure of the specific `ACCOUNT` without closing or deactivating the `CUSTOMER`. |
| **Account Flags & Legal Holds**<br/>`POST /api/v1/accounts/{id}/flags`<br/>`DELETE /api/v1/accounts/{id}/flags/{flagId}` | `account-service` (`AccountController.java`) | **`account-service` (Port 8082)**<br/>`POST /api/v1/accounts/{id}/flags`<br/>`DELETE /api/v1/accounts/{id}/flags/{flagId}` | Administrative and judicial freezes remain managed under account governance. |

---

### 8.2 Ledger Service Decomposition Matrix

| Legacy Endpoint / Behavior | Former Location | New Location & Route | Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Client Remittance / Transfer Ingress**<br/>`POST /api/v1/ledger/transfers` | `ledger-service` (`LedgerTransferController.java`) | **`transaction-service` (Port 8090)**<br/>`POST /api/v1/transactions/remittance` | **Ingress Decoupling:** Moves client-facing HTTP intake out of the ledger engine. Shields the database from direct mobile traffic. |
| **Edge Idempotency Interception**<br/>`IdempotencyInterceptor.java` | `ledger-service` | **`transaction-service` (Port 8090)**<br/>`IdempotencyInterceptor.java` | **Shock Absorber:** Intercepts repeated client button taps in Redis within 60s at the edge, before any saga is started or database row lock is requested. |
| **Pre-Flight Balance & KYC Checks**<br/>Not present in legacy ledger service | *New Behavior Added* | **`transaction-service` (Port 8090)** | Validates that KYC is in `VERIFIED` status, available balance covers amount, and account formats are valid before downstream calls. |
| **Initiation Tracking & Status**<br/>Direct synchronous DB commit | Legacy generated `TRF-XXXX` directly in `TRANSACTION` | **`transaction-service` (Port 8090)**<br/>`GET /api/v1/transactions/{trackingRef}` | Writes initial intent with status `PENDING` to `transaction_db` and allows asynchronous polling/webhooks. |
| **Saga Coordination & Fraud Screening**<br/>Legacy ledger executed immediately without risk checks | *New Behavior Added* | **`orchestration-service` (Port 8091)** & **`risk-engine` (Port 8092)** | Enforces AML screening ($< 40\text{ms}$) and coordinates compensating refunds if downstream steps fail. |
| **Core Double-Entry Balance Posting**<br/>`executeTransfer()` in `AccountBalanceServiceImpl` | `ledger-service` | **`ledger-service` (Port 8083)**<br/>`POST /api/v1/internal/ledger/post-transfer` | **Internal SoR Only:** Retains deadlock-free ordered locking ($\min \to \max$), zero-overdraft checks, and Oracle XE journal posting. Internal network only. |
| **Core Ledger Journal Auditing**<br/>`JournalEntryService` | `ledger-service` | **`ledger-service` (Port 8083)** | Writes directly to Oracle DB ledger journal tables within the same ACID transaction, eliminating external PostgreSQL dual-write overhead. |
| **Kafka Domain Event Streaming**<br/>`LedgerEventPublisher.java` | `ledger-service` | **`ledger-service` (Port 8083)** | Unchanged. Emits `ledger.mutation.completed.v1` and `ledger.transfer.completed.v1` strictly on `TransactionPhase.AFTER_COMMIT`. |

---

## 9. Summary Architecture Topology Diagram

```mermaid
flowchart TD
    subgraph Omnichannel Clients
        ReactWeb["React Web Portal (Port 3000 / 5173)<br/>• Staff, Teller & Admin Operations<br/>• 11-Stage Saga Stepper (`view=staff`)"]
        FlutterApp["Flutter Mobile App (`app/`)<br/>• Retail Cooperative Member Application<br/>• 3-Stage Member Stepper (`view=customer`)"]
    end

    Gateway["Spring Cloud API Gateway (Port 8080)<br/>• Edge Ingress, SSL Termination, Token-Bucket Rate Limiter"]

    ReactWeb -->|HTTPS /api/v1/**<br/>(CORS Enforced)| Gateway
    FlutterApp -->|HTTPS /api/v1/**<br/>(Native OS Socket / Port 8088 Web)| Gateway

    subgraph Edge Application Middleware
        MobileBFF["Mobile BFF (Port 8094)<br/>• Multi-Service Composite Aggregator<br/>• Dashboard Aggregation (`/api/v1/mobile/dashboard`)<br/>• 3-Stage Stepper Adapter (`/api/v1/mobile/**`)"]
    end

    Gateway -->|/api/v1/mobile/**| MobileBFF
    Gateway -->|/api/v1/customers/**| CustomerService["Customer Service (Port 8089)<br/>• Member Profile & PII<br/>• KYC Demographic Verification"]
    Gateway -->|/api/v1/accounts/**| AccountService["Account Service (Port 8082)<br/>• Account Open/Close<br/>• Cached Balance Lookups<br/>• Legal Holds & Flags"]
    Gateway -->|/api/v1/transactions/**| TxnService["Transaction Service (Port 8090)<br/>• Remittance Intake & Shock Absorber<br/>• Redis Idempotency (60s lock)<br/>• 11-Stage & 3-Stage Timeline Engine (T3)<br/>• Pre-flight KYC Status & Balance Checks"]

    MobileBFF -.->|Internal Cloud Mesh| CustomerService
    MobileBFF -.->|Internal Cloud Mesh| AccountService
    MobileBFF -.->|Internal Cloud Mesh| TxnService
    MobileBFF -.->|Internal Cloud Mesh| NotificationService["Notification Service (Port 8084)<br/>• Member In-App & SMS Alerts"]

    TxnService -->|1. Dispatch Saga| Orchestrator["Orchestration Service (Port 8091)<br/>• Saga State Machine Coordinator"]

    Orchestrator -->|2. Fraud/AML Screening| RiskEngine["Risk Engine (Port 8092)<br/>• Sub-40ms Velocity/AML Checks"]
    Orchestrator -->|3. Post When Approved| LedgerService["Ledger Service (Port 8083 - SoR)<br/>• Ordered Locks: min(src,dst) then max(src,dst)<br/>• Zero-Overdraft Invariant<br/>• Oracle XE System of Record"]

    LedgerService -->|Commit SoR & Journal| OracleDB[("Oracle DB Core Vault SoR<br/>Double-Entry General Ledger")]
    LedgerService -->|AFTER_COMMIT| KafkaBroker["Apache Kafka Broker"]

    KafkaBroker --> NotificationService
    KafkaBroker --> AuditService["Audit Service (Port 8085)<br/>• Tamper Detection & Verification"]
```
