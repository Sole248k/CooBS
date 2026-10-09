# CooBS Integrated API Contract (Target State)

**Version:** 0.10 draft, for freeze as `contracts-v1` on Day 2  
**Editor:** Dev 1. Change requests follow the handshake rules in [`PER_DEV_TECHNICAL_WORK_PACKAGES.md`](../../integrated-capstone-plans/PER_DEV_TECHNICAL_WORK_PACKAGES.md).  
**Reads with:** [`CURRENT_API_INVENTORY_AND_USAGE_GUIDE.md`](CURRENT_API_INVENTORY_AND_USAGE_GUIDE.md) (what exists today) and [`INTERBANK_TRANSFER_PROPOSAL.md`](INTERBANK_TRANSFER_PROPOSAL.md) (what we send to other groups).

Every endpoint carries a status:

| Status | Meaning |
| --- | --- |
| **SAME** | Exists today; path and payload do not change |
| **CHANGED** | Exists today; something changes, stated in the row |
| **NEW** | Does not exist today |
| **REMOVED** | Exists today; goes away |

---

## 1. Review of the current inventory

The inventory lists the right 38 endpoints, but several payloads in it do not match the code. Build against the shapes in this document, which were taken from the DTO classes.

| # | Inventory says | Code says |
| --- | --- | --- |
| 1 | Login result has `authResponse` and `mfaSessionToken` | Fields are `authData`, `mfaToken`, `deliveryChannel`, `maskedDestination` |
| 2 | Token response has `customerId` | It has `userId` and `userType`. For customers `userId` is the customer ID |
| 3 | MFA verify takes `username`, `totpCode`, `mfaSessionToken` | It takes `mfaToken` and `code` |
| 4 | Users are stored in `USERS` and `STAFF_USER` | Tables are `CUSTOMER` and `"USER"` |
| 5 | Create account takes `initialDeposit` and calls the ledger | No such field; the service writes the balance row itself |
| 6 | Account response has `balance` | Field is `availableBalance`; there is also `customerName` and `message` |
| 7 | Account number looks like `ACC-1002-9915` | Seeded numbers look like `ACC_10000001` |
| 8 | Flag request has `flagType` | Only `reason` |
| 9 | Debit and credit take `channel`; response has `type`, `remainingBalance` | No `channel`; response has `transactionType`, `previousBalance`, `newBalance`, `status` |
| 10 | Idempotency lock is 60 s | 30 s lock, 24 h result cache; `X-Idempotency-Key` is also accepted |
| 11 | Notification page has `items`, each with `id` | `content`, each with `notificationId`; page fields as in section 2.6 |
| 12 | Audit rows have `entryType`, `balanceAfter` | `transactionType`, `oldBalance`, `newBalance`, plus `actorId`, `clientIp` |
| 13 | Chain check returns `verifiedEntries`, `brokenAtAuditId`, `computedHash`, `recordedHash` | Returns `totalRecordsVerified`, `isChainIntact`, `latestHash`, `verifiedAt`, `message` |
| 14 | Frontend runs on 5173 | The preview runs on 3000; both origins are allowed |
| 15 | Section 5 lists `interbank-gateway` and `eod-batch-runner` on 8093 | Superseded: see section 3 |

The JSON examples in this document are exported to [`../examples/`](../examples/), one file each; the marker above each block names its file.

---

## 2. Conventions every service follows

### 2.1 Paths

| Prefix | Reachable from | Use |
| --- | --- | --- |
| `/api/v1/**` | Clients, through the gateway on 8080 | Public API |
| `/internal/v1/**` | Other services only; **never routed by the gateway** | Service-to-service |
| `/actuator/**` | Operations | Health and metrics |

### 2.2 Headers

| Header | Required on | Rule |
| --- | --- | --- |
| `Authorization: Bearer <jwt>` | Everything except login, token refresh and the inter-bank webhook | Internal calls use a service token, see 2.3 |
| `X-Correlation-ID` | Every request | The gateway adds one if missing; every service passes it on and puts it in every event |
| `Idempotency-Key` | Every `POST` that moves money | UUID from the client on public calls. On internal ledger calls it is `<transactionId>:<step>`, for example `…:hold`, `…:capture`, `…:release`, `…:reverse` |
| `Content-Type: application/json` | Every body | UTF-8 |

### 2.3 Token claims (unchanged)

<!-- example: common-token-claims.customer.json -->
```json
{
  "sub": "john_doe",
  "jti": "378c0fb9-f2d1-45f4-8168-05335260e02b",
  "userId": 1,
  "roles": ["ROLE_CUSTOMER"],
  "userType": "CUSTOMER"
}
```

`userId` is the **customer ID** when `userType` is `CUSTOMER` and the **staff user ID** when it is `STAFF`. Roles: `ROLE_CUSTOMER`, `ROLE_TELLER`, `ROLE_ADMIN`.

**Service token for internal calls.** A caller's token cannot be forwarded everywhere: the saga continues after the client's request has returned, events have no user, the batch has no customer, and partner banks send no JWT. So every `/internal/v1/**` call carries a token the calling service signs itself with the shared `JWT_SECRET`:

<!-- example: common-token-claims.service.json -->
```json
{
  "sub": "orchestration-service",
  "userId": 0,
  "roles": ["ROLE_SERVICE"],
  "userType": "SERVICE",
  "exp": 1791000300
}
```

- Lifetime: five minutes; minted per call or cached until it expires.
- Every `/internal/v1/**` endpoint requires `ROLE_SERVICE` and nothing else. A customer or staff token is rejected with 403.
- The person who caused the action travels in the body as `actorId` and `actorType`, never in the token. That is why the saga and ledger payloads carry `actorId`.
- `X-Correlation-ID` is always passed on.
- Target state replaces the shared secret with per-service keys from Key Vault; out of scope for the nine days.

### 2.4 Money, time, IDs

| Item | Existing endpoints | New endpoints and all events |
| --- | --- | --- |
| Money | JSON number, as today | **String** with two to four decimals: `"2500.00"`. Never a float in Dart or JavaScript |
| Currency | `"PHP"` | `"PHP"` |
| Time | As today (some values have no zone) | ISO-8601 UTC with `Z`: `"2026-10-03T11:40:00Z"` |
| Customer, account, staff IDs | JSON number | JSON number |
| `transactionId` | — | UUID string, created by `transaction-service` |
| `referenceNo` | Client-supplied on ledger calls | Server-generated on new transfers: `TX-20261003-8F3A21C0`, at most 36 characters |

### 2.5 Errors

Every service returns the existing problem shape. `errorCode` is the field clients switch on.

<!-- example: common-error.json -->
```json
{
  "type": "https://api.corebank.local/errors/INSUFFICIENT_FUNDS",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Available balance is lower than the requested amount.",
  "instance": "/api/v1/transactions/transfers",
  "errorCode": "INSUFFICIENT_FUNDS",
  "timestamp": "2026-10-03T11:40:00Z",
  "invalidParams": [{ "field": "amount", "message": "amount must be greater than zero" }]
}
```

`invalidParams` appears only on validation errors.

| `errorCode` | HTTP | Raised by |
| --- | --- | --- |
| `INVALID_REQUEST_PARAMETERS` | 400 | Any |
| `AUTH_ACCESS_DENIED` | 403 | Any |
| `AUTH_TOKEN_REVOKED` | 401 | Any |
| `ACCOUNT_NOT_FOUND` | 404 | account, ledger, transaction |
| `ACCOUNT_NOT_ACTIVE` | 422 | account, ledger, transaction |
| `ACCOUNT_NON_ZERO_BALANCE` | 400 | account |
| `ACCOUNT_ACTIVE_FLAG_EXISTS` | 422 | account |
| `ACCOUNT_NOT_OWNED` (new) | 403 | transaction |
| `ACCOUNT_UNAVAILABLE` (new) | 503 | transaction |
| `TRANSACTION_NOT_FOUND` (new) | 404 | transaction, orchestration |
| `INSUFFICIENT_FUNDS` | 422 | ledger |
| `DUPLICATE_IDEMPOTENCY_KEY` | 409 | ledger, transaction |
| `CONCURRENT_TRANSACTION_IN_FLIGHT` | 409 | ledger, transaction |
| `INVALID_TRANSACTION` | 400 | ledger |
| `LEDGER_AUDIT_DUAL_WRITE_FAILED` | 500 | ledger |
| `RISK_DECLINED` (new) | 422 | orchestration |
| `LIMIT_EXCEEDED` (new) | 422 | orchestration |
| `RISK_UNAVAILABLE` (new) | 503 | orchestration |
| `HOLD_NOT_FOUND`, `HOLD_NOT_RESERVED` (new) | 404, 409 | ledger |
| `ALREADY_REVERSED` (new) | 409 | ledger |
| `LEDGER_UNAVAILABLE` (new) | 503 | orchestration, batch |
| `BATCH_ALREADY_RUNNING` (new) | 409 | batch |
| `INTERBANK_REJECTED`, `INTERBANK_TIMEOUT` (new) | 422, 504 | transaction, orchestration |
| `INTERNAL_SERVER_ERROR` | 500 | Any |

HTTP codes for the existing error codes were checked against the ledger's exception handler. Codes marked new are proposals.

### 2.6 Paging

New list endpoints use the shape `account-service` and `notification-service` already return:

<!-- example: common-page.json -->
```json
{ "content": [], "pageNumber": 0, "pageSize": 20, "totalElements": 0, "totalPages": 0, "isLast": true }
```

Query: `page` (from 0), `size` (default 20, max 100).

### 2.7 Enumerations (spelled once, here)

| Name | Values |
| --- | --- |
| Account type | `SAVINGS`, `CHECKING`, `CURRENT`, `LOAN`, `GL` |
| Account status | `ACTIVE`, `FROZEN`, `DORMANT`, `CLOSED` |
| Transaction status (customer view) | `INITIATED`, `PROCESSING`, `POSTED`, `FAILED`, `REVERSED` |
| Hold status | `RESERVED`, `POSTED`, `CANCELLED` |
| Ledger transaction type | `DEBIT`, `CREDIT`, `REVERSAL_DEBIT`, `REVERSAL_CREDIT` |
| Channel | `APP`, `TELLER`, `INTERBANK`, `BATCH` |
| Category | `TRANSFER`, `BILLS`, `LOAN_REPAYMENT`, `FEES`, `SAVINGS`, `OTHER` |
| Risk decision | `APPROVE`, `DECLINE` |
| Stage outcome | `PASSED`, `FAILED`, `PENDING` |
| Batch step status | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED` |
| KYC tier | `TIER_1_BASIC`, `TIER_2_STANDARD`, `TIER_3_FULL` |

**Stages**

| No | `stageName` | Emitted by |
| --- | --- | --- |
| 1 | `INITIATED` | transaction-service |
| 2 | `VALIDATED` | transaction-service |
| 3 | `AUTHENTICATED` | transaction-service |
| 4 | `FRAUD_CHECK` | orchestration-service |
| 5 | `LIMIT_CHECK` | orchestration-service |
| 6 | `FUNDS_CHECK` | orchestration-service |
| 7 | `AUTHORIZED` | orchestration-service |
| 8 | `POSTED` | orchestration-service |
| 9 | `LEDGER_UPDATE` | orchestration-service |
| 10 | `NOTIFICATION` | orchestration-service |
| 11 | `RECONCILIATION` | orchestration-service |

Customer view: stage 1 is `INITIATED`; stages 2 to 7 are `PROCESSING`; stage 8 onward is `POSTED`.

---

## 3. Route map

### 3.1 Public routes through the gateway

Order matters: the gateway must match the more specific KYC route before the general customers route.

| Path | Service | Zone | Status |
| --- | --- | --- | --- |
| `/api/v1/auth/**` | auth-service 8081 | Cloud | SAME path; service moves to the cloud |
| `/api/v1/customers/**` | customer-service 8089 | Cloud | Hosts profile, KYC & beneficiaries endpoints |
| `/api/v1/accounts/**` | account-service 8082 | On-premises | Account lifecycle, closure & holds |
| `/api/v1/transactions/**` | transaction-service 8090 | Cloud | NEW (transfer intake & timeline) |
| `/api/v1/mobile/**` | mobile-bff 8094 | Cloud | NEW (mobile screen aggregation & adapters) |
| `/api/v1/interbank/**` | transaction-service 8090 | Cloud | NEW |
| `/api/v1/batch/**` | batch-service 8093 | On-premises | NEW |
| `/api/v1/ledger/**` | ledger-service 8083 | On-premises | CHANGED: staff roles only |
| `/api/v1/notifications/**` | notification-service 8084 | Cloud | SAME path; service moves to the cloud |
| `/api/v1/audit/**` | audit-service 8085 | On-premises | SAME |

### 3.2 Internal calls

| Caller | Calls | For |
| --- | --- | --- |
| mobile-bff | customer, account, transaction, notification services | Screen aggregation & mobile composite responses |
| transaction-service | account-service `/internal/v1/accounts/**` | Ownership, status, lookup by number |
| transaction-service | orchestration-service `/internal/v1/sagas/**` | Start transfer or reversal saga |
| orchestration-service | customer-service `/internal/v1/customers/{id}/risk-profile` | Score and tier |
| orchestration-service | risk-engine `/internal/v1/risk/evaluate` | Decision |
| orchestration-service | risk-engine `/internal/v1/risk/outcomes` | Gives back the daily-limit reservation of a transfer that did not post; counts a reversal of one that did |
| orchestration-service | ledger-service `/internal/v1/ledger/**` | Hold, capture, release, reverse |
| customer-service | account-service `/internal/v1/**` | Accounts lookup for Customer 360 |
| customer-service | transaction-service `/internal/v1/transactions` | Recent transactions |
| account-service | ledger-service `/internal/v1/ledger/balances/**` | Open and read balances |
| batch-service | ledger-service `/internal/v1/ledger/postings` | EOD postings |

No other service-to-service call is allowed without Dev 1's approval.

---

## 4. auth-service (8081)

> **Scope.** Registration is removed and members are seeded as KYC-verified. MFA is off by default, so `mfaRequired` is always `false`; A3 and A5 matter only if the optional email MFA is enabled. See section 0 of [`SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md`](../../integrated-capstone-plans/SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md).

| # | Method and path | Status | Notes |
| --- | --- | --- | --- |
| A1 | `POST /api/v1/auth/customers/register` | **REMOVED** | Members are seeded |
| A2 | `POST /api/v1/auth/customers/login` | SAME | Reads `identity_db` |
| A3 | `POST /api/v1/auth/customers/mfa/verify` | SAME | |
| A4 | `POST /api/v1/auth/staff/login` | SAME | |
| A5 | `POST /api/v1/auth/staff/mfa/verify` | SAME | |
| A6 | `POST /api/v1/auth/token/refresh` | SAME | |
| A7 | `POST /api/v1/auth/token/revoke` | SAME | |
| A8 | `GET /api/v1/auth/token/me` | CHANGED | Name, mobile and KYC fields removed |

**A2 / A4 request**

<!-- example: A2-A4-login.request.json -->
```json
{ "username": "john_doe", "password": "<password>", "preferredChannel": "SMS" }
```

`preferredChannel` is optional.

**A2 / A4 response, 200**

<!-- example: A2-A4-login.response.json -->
```json
{
  "mfaRequired": false,
  "mfaToken": null,
  "deliveryChannel": null,
  "maskedDestination": null,
  "authData": {
    "accessToken": "<jwt>",
    "refreshToken": "<opaque>",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "userId": 1,
    "username": "john_doe",
    "roles": ["ROLE_CUSTOMER"],
    "userType": "CUSTOMER"
  }
}
```

When `mfaRequired` is `true`, `authData` is `null` and `mfaToken` is set.

**A3 / A5 request**

<!-- example: A3-A5-mfa-verify.request.json -->
```json
{
  "mfaToken": "<token>",
  "code": "482910"
}
```

Response: the `authData` object.

**A6 request**

<!-- example: A6-token-refresh.request.json -->
```json
{
  "refreshToken": "<opaque>"
}
```

Response: the `authData` object.

**A8 response, 200**

<!-- example: A8-token-me.response.json -->
```json
{
  "userId": 1,
  "username": "john_doe",
  "email": "john.doe@example.com",
  "roles": ["ROLE_CUSTOMER"],
  "userType": "CUSTOMER",
  "status": "ACTIVE",
  "createdAt": "2026-01-15T08:00:00Z"
}
```

---

## 5. customer-service (8089) — NEW

| # | Method and path | Roles | Status |
| --- | --- | --- | --- |
| C1 | `GET /api/v1/customers/me` | Customer | CHANGED: served here; shape extended, old fields kept |
| C2 | `GET /api/v1/customers/{customerId}` | Teller, Admin | CHANGED: same as C1 |
| C3 | `GET /api/v1/customers/{customerId}/360` | Owner, Teller, Admin | NEW |
| C4 | `GET /api/v1/customers/me/beneficiaries` | Customer | NEW |
| C5 | `POST /api/v1/customers/me/beneficiaries` | Customer | NEW |
| C6 | `DELETE /api/v1/customers/me/beneficiaries/{beneficiaryId}` | Customer | NEW |
| C7 | `GET /internal/v1/customers/{customerId}/risk-profile` | Service | NEW |
| C8 | `POST /api/v1/customers/kyc/submit` | Customer | Initial KYC document and identity verification submission |
| C9 | `POST /api/v1/customers/kyc/update-request` | Customer | Sensitive profile update request (legal name, address) |
| C10 | `GET /api/v1/customers/kyc/update-requests` | Teller, Admin | Queue listing pending KYC verification requests |
| C11 | `POST /api/v1/customers/kyc/update-requests/{id}/approve` | Teller, Admin | Staff approval advancing customer KYC level |
| C12 | `POST /api/v1/customers/kyc/update-requests/{id}/reject` | Teller, Admin | Staff rejection of KYC request with reason |

**C1 / C2 response, 200.** The first six keys are today's shape, so the React console keeps working. The `kyc` object is managed directly by `customer-service`.

`username` is taken from the token for C1 and from the seeded channel profile for C2. `email` is seeded into the channel profile as well as into `identity_db`; `customer-service` never reads `identity_db`.

<!-- example: C1-C2-customer-profile.response.json -->
```json
{
  "customerId": 1,
  "username": "john_doe",
  "email": "john.doe@example.com",
  "kycStatus": "VERIFIED",
  "createdAt": "2026-01-15T08:00:00",
  "kyc": {
    "kycId": 1,
    "firstName": "John",
    "middleInitial": "A",
    "lastName": "Doe",
    "address": "123 Ayala Avenue, Makati City",
    "mobileNumber": "09171234567",
    "civilStatus": "SINGLE",
    "occupation": "Software Engineer",
    "status": "VERIFIED",
    "createdAt": "2026-01-15T08:00:00"
  },
  "membershipNumber": "MBR-000001",
  "displayName": "John D.",
  "kycTier": "TIER_3_FULL"
}
```

**C3 response, 200**

<!-- example: C3-customer-360.response.json -->
```json
{
  "customerId": 1,
  "displayName": "John D.",
  "membershipNumber": "MBR-000001",
  "kycStatus": "VERIFIED",
  "kycTier": "TIER_3_FULL",
  "creditScore": 750,
  "riskScore": "0.15",
  "accounts": [
    {
      "accountId": 1,
      "accountNumber": "ACC_10000001",
      "accountType": "SAVINGS",
      "currency": "PHP",
      "status": "ACTIVE",
      "availableBalance": "49625.00",
      "ledgerBalance": "49625.00",
      "heldAmount": "0.00"
    },
    {
      "accountId": 7,
      "accountNumber": "LN_10000007",
      "accountType": "LOAN",
      "currency": "PHP",
      "status": "ACTIVE",
      "outstandingPrincipal": "120000.00",
      "nextDueDate": "2026-10-15",
      "nextDueAmount": "5450.00"
    }
  ],
  "totals": { "deposits": "49625.00", "loans": "120000.00" },
  "recentTransactions": [
    {
      "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
      "referenceNo": "TX-20261003-8F3A21C0",
      "direction": "OUT",
      "counterpartyName": "Maria S.",
      "counterpartyAccountNumber": "ACC_10000002",
      "amount": "2500.00",
      "category": "TRANSFER",
      "status": "POSTED",
      "createdAt": "2026-10-03T11:40:00Z"
    }
  ],
  "spendingBreakdown": {
    "periodDays": 30,
    "items": [
      { "category": "TRANSFER", "amount": "7500.00", "count": 3 },
      { "category": "LOAN_REPAYMENT", "amount": "5450.00", "count": 1 }
    ]
  },
  "beneficiaries": [
    { "beneficiaryId": 11, "nickname": "Maria", "accountNumber": "ACC_10000002", "bankCode": "COOBS", "active": true }
  ],
  "asOf": "2026-10-03T11:45:00Z"
}
```

`riskScore` is returned to staff only; for a customer caller the field is omitted.

**C5 request**

<!-- example: C5-add-beneficiary.request.json -->
```json
{ "nickname": "Maria", "accountNumber": "ACC_10000002", "bankCode": "COOBS" }
```

`bankCode` defaults to our own code. Response 201: the beneficiary object.

**C7 response, 200**

<!-- example: C7-risk-profile.response.json -->
```json
{ "customerId": 1, "riskScore": "0.15", "kycTier": "TIER_3_FULL", "kycStatus": "VERIFIED", "dailyLimit": "100000.00" }
```

---

## 6. account-service (8082)

All 20 existing endpoints keep their paths and payloads, with these exceptions.

| # | Method and path | Status | Notes |
| --- | --- | --- | --- |
| K1 | `GET /api/v1/customers/me`, `GET /api/v1/customers/{id}` | **REMOVED** here | Now C1, C2 in `customer-service` |
| K2 | `POST /api/v1/customers/kyc/submit` and four `kyc/update-request(s)` endpoints | **REMOVED** here | Moved to `customer-service` (C8–C12) |
| K3 | `POST /api/v1/accounts` | CHANGED | `accountType` accepts `CURRENT`, `LOAN`; the balance row is opened through the ledger (L1) |
| K4 | `GET /api/v1/accounts`, `/my-accounts`, `/{accountId}` | CHANGED | `availableBalance` is read from the ledger; shape unchanged |
| K5 | Closure requests (5 endpoints), flags (3 endpoints), `PATCH /{accountId}/status` | SAME | `approvedBy`, `flaggedBy`, `removedBy` remain numbers |
| K6 | `GET /internal/v1/accounts/{accountId}` | NEW | Internal account lookup by ID |
| K7 | `GET /internal/v1/accounts/by-number/{accountNumber}` | NEW | Internal account lookup by account number |
| K8 | `GET /internal/v1/customers/{customerId}/accounts` | NEW | Internal lookup of accounts by customer ID |

**K3 request**

<!-- example: K3-open-account.request.json -->
```json
{
  "customerId": 1,
  "accountType": "SAVINGS",
  "currency": "PHP"
}
```

**Account response (today's shape, unchanged)**

<!-- example: K3-K4-account.response.json -->
```json
{
  "accountId": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "accountNumber": "ACC_10000001",
  "accountType": "SAVINGS",
  "currency": "PHP",
  "status": "ACTIVE",
  "availableBalance": 49625.00,
  "createdAt": "2026-01-15T08:00:00",
  "message": null
}
```

**K6 / K7 response, 200**

<!-- example: K6-K7-account-lookup.response.json -->
```json
{
  "accountId": 2,
  "accountNumber": "ACC_10000002",
  "customerId": 2,
  "accountType": "CHECKING",
  "currency": "PHP",
  "status": "ACTIVE",
  "hasActiveFlag": false,
  "ownerDisplayName": "Maria S."
}
```

---


## 7. transaction-service (8090) — NEW

| # | Method and path | Roles | Notes |
| --- | --- | --- | --- |
| T1 | `POST /api/v1/transactions/transfers` | Customer, Teller | Needs `Idempotency-Key`. Returns 202. Alias: `/api/v1/transactions/remittance` |
| T2 | `GET /api/v1/transactions/{transactionId}` | Owner, Teller, Admin | Path param also accepts `trackingReference` |
| T3 | `GET /api/v1/transactions/{transactionId}/timeline` | Owner, Teller, Admin | Customers get three stages, staff eleven |
| T4 | `GET /api/v1/transactions` | Customer (own), Teller, Admin | Filters: `accountId`, `status`, `from`, `to`, paging. Alias: `/my-transactions` |
| T5 | `POST /api/v1/transactions/{transactionId}/reversal` | Teller, Admin | Needs `Idempotency-Key` |
| T6, T7 | Moved | | The dead-letter list and replay are served by notification-service: N4 and N5 in section 12 |
| T8 | `GET /internal/v1/transactions?customerId=&limit=` | Service | For Customer 360 |
| T9 | `GET /internal/v1/transactions/spending?customerId=&days=` | Service | For Customer 360 |
| T10 | `POST /api/v1/interbank/transfers` and related | Partner bank | See section 14 |

**T1 request**

<!-- example: T1-transfer.request.json -->
```json
{
  "sourceAccountId": 1,
  "destinationAccountNumber": "ACC_10000002",
  "destinationBankCode": "COOBS",
  "amount": "2500.00",
  "currency": "PHP",
  "category": "TRANSFER",
  "remarks": "Lunch money"
}
```

- `destinationBankCode` is optional and defaults to our own code. Any other value makes it an outbound inter-bank transfer.
- `category` is optional and defaults to `TRANSFER`.
- For an outbound inter-bank transfer, add `"beneficiaryName": "Juan Dela Cruz"`.

**T1 response, 202**

<!-- example: T1-transfer.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "referenceNo": "TX-20261003-8F3A21C0",
  "status": "INITIATED",
  "amount": "2500.00",
  "currency": "PHP",
  "createdAt": "2026-10-03T11:40:00Z",
  "timelineUrl": "/api/v1/transactions/6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11/timeline"
}
```

A repeat of the same `Idempotency-Key` returns the same body with 200.

**T2 response, 200**

<!-- example: T2-transaction.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "referenceNo": "TX-20261003-8F3A21C0",
  "channel": "APP",
  "sourceAccountId": 1,
  "sourceAccountNumber": "ACC_10000001",
  "destinationAccountId": 2,
  "destinationAccountNumber": "ACC_10000002",
  "destinationBankCode": "COOBS",
  "amount": "2500.00",
  "currency": "PHP",
  "category": "TRANSFER",
  "remarks": "Lunch money",
  "status": "POSTED",
  "failureCode": null,
  "failureReason": null,
  "reversedByTransactionId": null,
  "createdAt": "2026-10-03T11:40:00Z",
  "completedAt": "2026-10-03T11:40:01Z"
}
```

**T3 response, 200 (staff)**

<!-- example: T3-timeline-staff.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "trackingReference": "TX-20261003-8F3A21C0",
  "status": "POSTED",
  "view": "STAFF",
  "currentStage": 11,
  "totalStages": 11,
  "overallStatus": "COMPLETED",
  "stages": [
    { "stageNo": 1, "stageNumber": 1, "stageKey": "INITIATED", "stageName": "INITIATED", "title": "Initiated", "service": "transaction-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.010Z", "timestamp": "2026-10-03T11:40:00.010Z", "detail": null },
    { "stageNo": 2, "stageNumber": 2, "stageKey": "VALIDATED", "stageName": "VALIDATED", "title": "Validated", "service": "transaction-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.020Z", "timestamp": "2026-10-03T11:40:00.020Z", "detail": null },
    { "stageNo": 3, "stageNumber": 3, "stageKey": "AUTHENTICATED", "stageName": "AUTHENTICATED", "title": "Authenticated", "service": "transaction-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.030Z", "timestamp": "2026-10-03T11:40:00.030Z", "detail": null },
    { "stageNo": 4, "stageNumber": 4, "stageKey": "FRAUD_CHECK", "stageName": "FRAUD_CHECK", "title": "Fraud Check", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.040Z", "timestamp": "2026-10-03T11:40:00.040Z", "detail": "score 0.15" },
    { "stageNo": 5, "stageNumber": 5, "stageKey": "LIMIT_CHECK", "stageName": "LIMIT_CHECK", "title": "Limit Check", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.050Z", "timestamp": "2026-10-03T11:40:00.050Z", "detail": null },
    { "stageNo": 6, "stageNumber": 6, "stageKey": "FUNDS_CHECK", "stageName": "FUNDS_CHECK", "title": "Funds Check", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.060Z", "timestamp": "2026-10-03T11:40:00.060Z", "detail": null },
    { "stageNo": 7, "stageNumber": 7, "stageKey": "AUTHORIZED", "stageName": "AUTHORIZED", "title": "Authorized", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.120Z", "timestamp": "2026-10-03T11:40:00.120Z", "detail": null },
    { "stageNo": 8, "stageNumber": 8, "stageKey": "POSTED", "stageName": "POSTED", "title": "Posted", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.210Z", "timestamp": "2026-10-03T11:40:00.210Z", "detail": null },
    { "stageNo": 9, "stageNumber": 9, "stageKey": "LEDGER_UPDATE", "stageName": "LEDGER_UPDATE", "title": "Ledger Update", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:00.450Z", "timestamp": "2026-10-03T11:40:00.450Z", "detail": null },
    { "stageNo": 10, "stageNumber": 10, "stageKey": "NOTIFICATION", "stageName": "NOTIFICATION", "title": "Notification", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:01.100Z", "timestamp": "2026-10-03T11:40:01.100Z", "detail": null },
    { "stageNo": 11, "stageNumber": 11, "stageKey": "RECONCILIATION", "stageName": "RECONCILIATION", "title": "Reconciliation", "service": "orchestration-service", "status": "COMPLETED", "outcome": "PASSED", "occurredAt": "2026-10-03T11:40:01.400Z", "timestamp": "2026-10-03T11:40:01.400Z", "detail": null }
  ]
}
```

All eleven stages are always present; one not reached yet has `"outcome": "PENDING"` and `"occurredAt": null`. `stageKey` and `stageName` both carry the stage name from section 2.7; `title` is the same name in words.

**T3 response, 200 (customer)**

<!-- example: T3-timeline-customer.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "trackingReference": "TX-20261003-8F3A21C0",
  "status": "PROCESSING",
  "overallStatus": "PROCESSING",
  "view": "CUSTOMER",
  "currentStage": 1,
  "totalStages": 3,
  "stages": [
    {
      "stageNumber": 1,
      "stageKey": "SUBMITTED",
      "stageName": "INITIATED",
      "title": "Transfer Submitted",
      "subtitle": "Your transfer request has been received and verified.",
      "status": "COMPLETED",
      "outcome": "PASSED",
      "occurredAt": "2026-10-03T11:40:00.010Z",
      "timestamp": "2026-10-03T11:40:00.010Z"
    },
    {
      "stageNumber": 2,
      "stageKey": "IN_REVIEW",
      "stageName": "PROCESSING",
      "title": "Security & Clearing",
      "subtitle": "Automated security screening and cooperative network clearing.",
      "status": "IN_PROGRESS",
      "outcome": "PENDING",
      "occurredAt": null,
      "timestamp": null
    },
    {
      "stageNumber": 3,
      "stageKey": "COMPLETED",
      "stageName": "POSTED",
      "title": "Transfer Completed",
      "subtitle": "Funds have been successfully deposited into the recipient's account.",
      "status": "PENDING",
      "outcome": "PENDING",
      "occurredAt": null,
      "timestamp": null
    }
  ],
  "failureMessage": null
}
```

Customers never see internal technical details or raw risk scores; `failureMessage` is a plain, user-friendly sentence.

**T5 request**

<!-- example: T5-reversal.request.json -->
```json
{
  "reason": "Customer dispute approved"
}
```

**T5 response, 202.** The T1 response shape, for the new reversal transaction, plus `reversesTransactionId`.

<!-- example: T5-reversal.response.json -->
```json
{
  "transactionId": "c4b7e2d9-5a13-4f86-b0c2-7d9e1a3f5b64",
  "referenceNo": "RV-20261003-11AA22BB",
  "status": "INITIATED",
  "amount": "2500.00",
  "currency": "PHP",
  "createdAt": "2026-10-03T11:55:00Z",
  "timelineUrl": "/api/v1/transactions/c4b7e2d9-5a13-4f86-b0c2-7d9e1a3f5b64/timeline",
  "reversesTransactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"
}
```

**T8 response:** an array of the `recentTransactions` items shown in C3.  
**T9 response:** the `spendingBreakdown` object shown in C3.

---

## 7.1 mobile-bff (8094) — NEW, mobile middleware

The **Mobile Backend-for-Frontend (BFF)** is an application-level orchestration middleware deployed on the cloud network. It sits behind Spring Cloud Gateway (:8080) at `/api/v1/mobile/**` to eliminate high-latency cellular round-trips over 4G/5G mobile connections, minimize battery and bandwidth drain, and adapt complex microservice states for the Flutter mobile client.

| # | Method and path | Roles | Notes |
| --- | --- | --- | --- |
| M1 | `GET /api/v1/mobile/dashboard` | Customer | Concurrently aggregates member profile, primary account, balance, unread notifications count, and recent transactions in a single round-trip (< 150ms) |
| M2 | `GET /api/v1/mobile/transactions/{trackingReference}/timeline` | Customer | 3-stage customer progress adapter translating microservice saga states into member milestones (`SUBMITTED`, `IN_REVIEW`, `COMPLETED`) |
| M3 | `POST /api/v1/mobile/transactions/remittance` | Customer | Mobile remittance submission intake; validates client DTO and dispatches to `transaction-service`, returning `202 Accepted` |

**M1 response, 200**

<!-- example: M1-mobile-dashboard.response.json -->
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

## 8. orchestration-service (8091) — NEW, internal only

| # | Method and path | Caller |
| --- | --- | --- |
| O1 | `POST /internal/v1/sagas/transfers` | transaction-service |
| O2 | `POST /internal/v1/sagas/reversals` | transaction-service |
| O3 | `GET /internal/v1/sagas/{transactionId}` | transaction-service, support |
| O4 | `POST /internal/v1/sagas/inbound-interbank` | transaction-service |

**O1 request**

<!-- example: O1-start-transfer-saga.request.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "referenceNo": "TX-20261003-8F3A21C0",
  "channel": "APP",
  "customerId": 1,
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "destinationBankCode": "COOBS",
  "amount": "2500.00",
  "currency": "PHP",
  "actorId": 1,
  "actorType": "CUSTOMER",
  "isNewBeneficiary": false
}
```

`isNewBeneficiary` is optional; a request without it is treated as `false`. transaction-service sets it to `true` when the customer has no earlier transfer to that destination account that reached `POSTED`. The saga passes it to the risk engine unchanged (R1), where it raises the score by a fixed amount.

**O1 response, 202.** Progress comes back as stage events, not in this response.

<!-- example: O1-start-transfer-saga.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "sagaState": "STARTED"
}
```

**O2 request.** `transactionId` is the new reversal transaction; `reversesTransactionId` is the original.

<!-- example: O2-start-reversal-saga.request.json -->
```json
{
  "transactionId": "c4b7e2d9-5a13-4f86-b0c2-7d9e1a3f5b64",
  "reversesTransactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "referenceNo": "RV-20261003-11AA22BB",
  "reason": "Customer dispute approved",
  "actorId": 3
}
```

**O3 response**

<!-- example: O3-saga-state.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "sagaState": "COMPLETED",
  "holdId": 9001,
  "riskDecision": "APPROVE",
  "failureCode": null,
  "updatedAt": "2026-10-03T11:40:01Z"
}
```

Saga states: `STARTED`, `RISK_APPROVED`, `FUNDS_RESERVED`, `POSTED`, `COMPLETED`, `FAILED`, `COMPENSATING`, `COMPENSATED`.

**O4 request.** An inbound credit needs no risk check and no hold: the money is not leaving one of our customers. The saga posts once through L8 and publishes stages 1, 2, 8, 9, 10 and 11; stages 3 to 7 are published as `PASSED` with the detail `not applicable`.

<!-- example: O4-inbound-interbank-saga.request.json -->
```json
{
  "transactionId": "9a2d6c1e-1f0b-4a7e-8f55-3c1e2d4b5a66",
  "referenceNo": "IB-20261003-000045",
  "messageId": "0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21",
  "sourceBankCode": "BANKB",
  "settlementAccountId": 9005,
  "destinationAccountId": 2,
  "amount": "2500.00",
  "currency": "PHP"
}
```

**O4 response, 200.** This call is synchronous, because the partner bank is waiting for `ACCEPTED` or `REJECTED`.

<!-- example: O4-inbound-interbank-saga.response.json -->
```json
{
  "transactionId": "9a2d6c1e-1f0b-4a7e-8f55-3c1e2d4b5a66",
  "sagaState": "COMPLETED",
  "referenceNo": "IB-20261003-000045"
}
```

---

## 9. risk-engine (8092) — NEW, internal only

| # | Method and path | Caller |
| --- | --- | --- |
| R1 | `POST /internal/v1/risk/evaluate` | orchestration-service |
| R2 | `POST /internal/v1/risk/outcomes` | orchestration-service |

**R1 request**

<!-- example: R1-risk-evaluate.request.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "customerId": 1,
  "amount": "2500.00",
  "currency": "PHP",
  "channel": "APP",
  "customerRiskScore": "0.15",
  "kycTier": "TIER_3_FULL",
  "dailyLimit": "100000.00",
  "isNewBeneficiary": false
}
```

**R1 response, 200**

<!-- example: R1-risk-evaluate.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "decision": "APPROVE",
  "score": "0.18",
  "fraudCheck": { "outcome": "PASSED", "reasons": [] },
  "limitCheck": { "outcome": "PASSED", "usedToday": "5000.00", "dailyLimit": "100000.00" },
  "evaluatedInMs": 12
}
```

| Score | Decision | Reason code |
| --- | --- | --- |
| Below 0.60 | `APPROVE` | — |
| 0.60 to 0.85 | `DECLINE` | `REVIEW_REQUIRED` |
| Above 0.85 | `DECLINE` | `HIGH_RISK` |
| Over daily limit | `DECLINE` | `LIMIT_EXCEEDED` |

The caller times out at 150 ms and treats a timeout as `RISK_UNAVAILABLE`.

**The daily limit is a reservation.** An `APPROVE` reserves the amount against the customer's limit for the business date, which is the date in Manila (`Asia/Manila`), not in UTC. The amount is added and the new total compared with the limit in one step, so two transfers at the same moment cannot both take the last of the room: the one that no longer fits is declined with `LIMIT_EXCEEDED`. A transaction is reserved once, however often R1 is asked about it. `usedToday` is what is reserved for that business date.

**R2** tells the engine what became of a transfer. The saga sends it once the transfer or its reversal has ended, and each report is safe to repeat.

| `outcome` | When | What the engine does |
| --- | --- | --- |
| `FAILED` | The engine approved the transfer and it ended without posting: the hold was refused, the capture was refused and the hold released, or it was cancelled before posting | Gives back the amount it reserved. For a transfer that holds no reservation it changes nothing and answers `released: false` |
| `REVERSED` | The transfer posted and the ledger has since reversed it (L7) | Counts one reversal for the sending customer on the business date of the reversal. Nothing is given back: a transfer that posted keeps its reservation. `customerId` is required |

Two or more reversals on a business date add 0.25 to the fraud score of that customer's later transfers that day. Only the engine writes its counters; no other service touches them.

R2 is best effort. If it does not arrive, the customer has less of that day's limit left, or one reversal fewer is counted; no money is affected. Any other outcome, or `REVERSED` with no `customerId`, is `400 INVALID_REQUEST_PARAMETERS`, and `503 RISK_UNAVAILABLE` means the counter could not be reached.

**R2 request**

<!-- example: R2-risk-outcome.request.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "outcome": "FAILED"
}
```

**R2 response, 200**

<!-- example: R2-risk-outcome.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "outcome": "FAILED",
  "released": true,
  "releasedAmount": "2500.00"
}
```

**R2 request, a reversed transfer**

<!-- example: R2-risk-outcome-reversed.request.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "outcome": "REVERSED",
  "customerId": 1
}
```

**R2 response, 200.** `reversalsToday` is the customer's count for the business date, this one included.

<!-- example: R2-risk-outcome-reversed.response.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "outcome": "REVERSED",
  "released": false,
  "reversalsToday": 1
}
```

---

## 10. ledger-service (8083)

### 10.1 Staff endpoints (existing, staff roles only)

| # | Method and path | Status |
| --- | --- | --- |
| L-S1 | `GET /api/v1/ledger/accounts/{accountId}/balance` | CHANGED: response gains `ledgerBalance`, `heldAmount` |
| L-S2 | `POST /api/v1/ledger/debits` | SAME; the counter-leg is the cash GL account |
| L-S3 | `POST /api/v1/ledger/credits` | SAME; the counter-leg is the cash GL account |
| L-S4 | `POST /api/v1/ledger/transfers` | SAME for the staff console. Customer transfers use T1 |

Request and response shapes for L-S2 to L-S4 are today's: `{ accountId, amount, referenceNo, remarks }` and `{ sourceAccountId, destinationAccountId, amount, referenceNo, remarks }`.

**L-S1 response**

<!-- example: L-S1-balance.response.json -->
```json
{
  "accountId": 1,
  "currency": "PHP",
  "availableBalance": 49625.00,
  "ledgerBalance": 49625.00,
  "heldAmount": 0.00,
  "asOfTimestamp": "2026-10-03T11:30:00Z",
  "isCached": true
}
```

### 10.2 Internal endpoints (NEW)

**GL accounts.** Five internal accounts are seeded with type `GL`: cash, interest expense, fee income, loan receivable, and one inter-bank settlement account per partner. Their account IDs are published in the seed ID table (handshake H2), and services read them from configuration, never hard-coded. `BALANCE` has a check that the available balance cannot go below zero, so the GL accounts that get debited are seeded with a large opening balance. Reconciliation compares the day's total debits with total credits and is not affected by that opening figure.

| # | Method and path | Caller |
| --- | --- | --- |
| L1 | `POST /internal/v1/ledger/balances` | account-service |
| L2 | `GET /internal/v1/ledger/balances/{accountId}` | account-service, batch-service |
| L3 | `POST /internal/v1/ledger/holds` | orchestration-service |
| L4 | `POST /internal/v1/ledger/holds/{holdId}/capture` | orchestration-service |
| L5 | `POST /internal/v1/ledger/holds/{holdId}/release` | orchestration-service |
| L6 | `GET /internal/v1/ledger/holds/{holdId}` | orchestration-service |
| L7 | `POST /internal/v1/ledger/reversals` | orchestration-service |
| L8 | `POST /internal/v1/ledger/postings` | batch-service; orchestration-service for inbound inter-bank |
| L9 | `GET /internal/v1/ledger/gl/trial-balance?businessDate=` | batch-service |

Every `POST` needs `Idempotency-Key`. A repeat returns the first result.

**L1 request**

<!-- example: L1-open-balance.request.json -->
```json
{
  "accountId": 8,
  "currency": "PHP"
}
```

**L1 response, 201**

<!-- example: L1-open-balance.response.json -->
```json
{
  "accountId": 8,
  "availableBalance": "0.00",
  "ledgerBalance": "0.00"
}
```

**L3 request**

<!-- example: L3-place-hold.request.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "referenceNo": "TX-20261003-8F3A21C0",
  "accountId": 1,
  "amount": "2500.00",
  "currency": "PHP",
  "actorId": 1
}
```

**L3 response, 201**

<!-- example: L3-place-hold.response.json -->
```json
{
  "holdId": 9001,
  "referenceNo": "TX-20261003-8F3A21C0",
  "accountId": 1,
  "amount": "2500.00",
  "status": "RESERVED",
  "availableBalance": "47125.00",
  "ledgerBalance": "49625.00",
  "createdAt": "2026-10-03T11:40:00.200Z"
}
```

**L4 request**

<!-- example: L4-capture-hold.request.json -->
```json
{ "destinationAccountId": 2, "remarks": "Lunch money", "actorId": 1, "clientIp": "203.0.113.10" }
```

**L4 response, 200**

<!-- example: L4-capture-hold.response.json -->
```json
{
  "holdId": 9001,
  "status": "POSTED",
  "transferReference": "TX-20261003-8F3A21C0",
  "debitTransactionId": 5001,
  "creditTransactionId": 5002,
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": "2500.00",
  "sourceNewBalance": "47125.00",
  "destinationNewBalance": "27875.00",
  "auditHash": "a3f5b8e9c1d0…",
  "timestamp": "2026-10-03T11:40:00.900Z"
}
```

**L5 request**

<!-- example: L5-release-hold.request.json -->
```json
{
  "reason": "CAPTURE_FAILED"
}
```

**L5 response, 200**

<!-- example: L5-release-hold.response.json -->
```json
{
  "holdId": 9001,
  "status": "CANCELLED",
  "availableBalance": "49625.00"
}
```

**L7 request**

<!-- example: L7-reversal.request.json -->
```json
{
  "originalReferenceNo": "TX-20261003-8F3A21C0",
  "reversalReferenceNo": "RV-20261003-11AA22BB",
  "reason": "Customer dispute approved",
  "actorId": 3
}
```

**L7 response, 201**

<!-- example: L7-reversal.response.json -->
```json
{
  "reversalReferenceNo": "RV-20261003-11AA22BB",
  "originalReferenceNo": "TX-20261003-8F3A21C0",
  "status": "POSTED",
  "entries": [
    { "transactionId": 5003, "accountId": 1, "transactionType": "REVERSAL_CREDIT", "amount": "2500.00", "newBalance": "49625.00" },
    { "transactionId": 5004, "accountId": 2, "transactionType": "REVERSAL_DEBIT", "amount": "2500.00", "newBalance": "25375.00" }
  ],
  "timestamp": "2026-10-03T12:10:00Z"
}
```

`batchRunId` and `stepName` are required for batch calls. For an inbound inter-bank credit they are omitted and `transactionId` is sent instead, with one posting: debit the partner's settlement GL account, credit the beneficiary.

`actorId` is optional: the person who caused the postings, when there is one. `referenceNo` is at most 33 characters here, because the ledger stores each leg with a two-character suffix.

Each posting is one debit and one credit in its own transaction, and has its own outcome:

- A posting the ledger refuses is `FAILED` with an `errorCode` from section 2.5 (`INSUFFICIENT_FUNDS`, `ACCOUNT_NOT_FOUND`, `ACCOUNT_NOT_ACTIVE`, `INVALID_TRANSACTION`), and the others still post. The answer is 200 with `failed` above zero, so read `failed` and not the status code.
- A `referenceNo` that is already in the journal with the same accounts and amount is answered `POSTED` and is not posted again. That makes the whole request safe to repeat. With different accounts or a different amount it is `FAILED` with `DUPLICATE_IDEMPOTENCY_KEY`.
- When the ledger itself fails the answer is 5xx and no posting is reported as refused. Repeat the request.

`totalAmount` is the sum of the `POSTED` postings. An inter-bank call gets `transactionId` back in place of `batchRunId` and `stepName`.

**L8 request**

<!-- example: L8-postings.request.json -->
```json
{
  "batchRunId": 31,
  "stepName": "INTEREST_ACCRUAL",
  "postings": [
    {
      "referenceNo": "EOD-20261003-INT-000001",
      "debitAccountId": 9002,
      "creditAccountId": 1,
      "amount": "4.08",
      "remarks": "Daily savings interest"
    }
  ]
}
```

**L8 response, 200**

<!-- example: L8-postings.response.json -->
```json
{
  "batchRunId": 31,
  "stepName": "INTEREST_ACCRUAL",
  "posted": 1,
  "failed": 0,
  "totalAmount": "4.08",
  "results": [ { "referenceNo": "EOD-20261003-INT-000001", "status": "POSTED", "errorCode": null } ]
}
```

**L9 response, 200**

<!-- example: L9-trial-balance.response.json -->
```json
{ "businessDate": "2026-10-03", "totalDebits": "125430.08", "totalCredits": "125430.08", "balanced": true, "variance": "0.00" }
```

---

## 11. batch-service (8093) — NEW

| # | Method and path | Roles |
| --- | --- | --- |
| B1 | `POST /api/v1/batch/eod/trigger` | Admin |
| B2 | `GET /api/v1/batch/eod/runs/{batchRunId}` | Admin, Teller |
| B3 | `GET /api/v1/batch/eod/runs` | Admin, Teller |

**B1 request.** `businessDate` is optional and defaults to today.

<!-- example: B1-eod-trigger.request.json -->
```json
{
  "businessDate": "2026-10-03"
}
```

**B1 response, 202**

<!-- example: B1-eod-trigger.response.json -->
```json
{
  "batchRunId": 31,
  "businessDate": "2026-10-03",
  "status": "RUNNING",
  "startedAt": "2026-10-03T17:00:00Z"
}
```

**B2 response, 200**

<!-- example: B2-eod-run.response.json -->
```json
{
  "batchRunId": 31,
  "businessDate": "2026-10-03",
  "status": "COMPLETED",
  "triggeredBy": 3,
  "startedAt": "2026-10-03T17:00:00Z",
  "finishedAt": "2026-10-03T17:00:06Z",
  "steps": [
    { "stepNo": 1, "stepName": "INTEREST_ACCRUAL", "status": "COMPLETED", "itemsProcessed": 3, "totalAmount": "12.24", "durationMs": 820, "message": null },
    { "stepNo": 2, "stepName": "SERVICE_FEES", "status": "COMPLETED", "itemsProcessed": 1, "totalAmount": "50.00", "durationMs": 640, "message": null },
    { "stepNo": 3, "stepName": "STANDING_INSTRUCTIONS", "status": "COMPLETED", "itemsProcessed": 1, "totalAmount": "1000.00", "durationMs": 910, "message": null },
    { "stepNo": 4, "stepName": "LOAN_AMORTIZATION", "status": "COMPLETED", "itemsProcessed": 1, "totalAmount": "5450.00", "durationMs": 880, "message": null },
    { "stepNo": 5, "stepName": "GL_RECONCILIATION", "status": "COMPLETED", "itemsProcessed": 0, "totalAmount": "0.00", "durationMs": 1200, "message": "Debits equal credits" },
    { "stepNo": 6, "stepName": "AUDIT_SEAL", "status": "COMPLETED", "itemsProcessed": 1, "totalAmount": "0.00", "durationMs": 700, "message": "Seal a3f5b8…" }
  ]
}
```

If step 5 finds a variance, its status is `FAILED`, step 6 is `SKIPPED`, and the run status is `HALTED`. Run statuses: `RUNNING`, `COMPLETED`, `HALTED`, `FAILED`.

---

## 12. notification-service (8084) and audit-service (8085)

| # | Method and path | Status |
| --- | --- | --- |
| N1 | `GET /api/v1/notifications/my-notifications` | SAME |
| N2 | `PATCH /api/v1/notifications/{id}/read` | SAME |
| N3 | `GET /api/v1/notifications/unread-count` | SAME |
| N4 | `GET /api/v1/notifications/admin/dlq` | NEW. Admin. Dead-letter records |
| N5 | `POST /api/v1/notifications/admin/dlq/{recordId}/replay` | NEW. Admin. Replays one record |
| N6 | `POST /api/v1/notifications/admin/dlq/replay` | NEW. Admin. Replays every record in the list |
| U1 | `GET /api/v1/audit/accounts/{accountId}/statement` | SAME |
| U2 | `GET /api/v1/audit/transactions/{transactionId}` | SAME |
| U3 | `GET /api/v1/audit/verify-chain/{accountId}` | SAME |
| U4 | `GET /api/v1/audit/seals/{businessDate}` | NEW |

**N1 response, 200 (today's shape).** Items in the page shape of section 2.6.

<!-- example: N1-notifications.response.json -->
```json
{
  "content": [
    {
      "notificationId": 881,
      "title": "Funds Transfer Sent",
      "message": "PHP 2500.00 was transferred to account ACC_10000002. Reference: TX-20261003-8F3A21C0. New balance: PHP 47125.00.",
      "channel": "IN_APP",
      "isRead": false,
      "createdAt": "2026-10-03T11:40:01Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

**N2 response, 200**

<!-- example: N2-mark-read.response.json -->
```json
{
  "notificationId": 881,
  "isRead": true
}
```

**N3 response, 200**

<!-- example: N3-unread-count.response.json -->
```json
{
  "customerId": 1,
  "unreadCount": 3
}
```

**U1 row (today's shape)**

<!-- example: U1-statement-row.json -->
```json
{
  "auditId": 9012, "transactionId": 5001, "accountId": 1, "referenceNo": "TX-20261003-8F3A21C0",
  "transactionType": "DEBIT", "amount": 2500.00, "oldBalance": 49625.00, "newBalance": 47125.00,
  "previousHash": "e8d2a1…", "currentHash": "a3f5b8…", "actorId": 1, "clientIp": "203.0.113.10",
  "eventTimestamp": "2026-10-03T11:40:00"
}
```

**U3 response, 200 (today's shape)**

<!-- example: U3-verify-chain.response.json -->
```json
{
  "accountId": 1,
  "totalRecordsVerified": 42,
  "isChainIntact": true,
  "latestHash": "a3f5b8…",
  "verifiedAt": "2026-10-03T11:45:00",
  "message": "Audit chain integrity verified successfully."
}
```

**U4 response, 200**

<!-- example: U4-daily-seal.response.json -->
```json
{ "businessDate": "2026-10-03", "lastAuditId": 9040, "sealHash": "c7d1e2…", "sealedAt": "2026-10-03T17:00:06Z", "batchRunId": 31 }
```

**N4 response, 200:** a JSON array of the item below, not paged. `recordId` is `<topic>-<partition>-<offset>`, and `payload` is the parked record as text.

<!-- example: N4-dlq-item.json -->
```json
{
  "recordId": "notification.events.DLT-0-42",
  "topic": "notification.events.DLT",
  "originalTopic": "ledger.transfer.completed.v1",
  "key": "1",
  "errorMessage": "Listener failed",
  "attempts": 3,
  "failedAt": "2026-10-03T11:41:00Z",
  "payload": "{\"eventId\":\"evt_3c892f6a\",\"eventType\":\"LEDGER_TRANSFER_COMPLETED\"}"
}
```

**N5 response, 200.** The record is sent back to `originalTopic` with its original key and consumed again. N6 answers with an array of these.

<!-- example: N5-dlq-replay.response.json -->
```json
{
  "recordId": "notification.events.DLT-0-42",
  "replayed": true,
  "replayedAt": "2026-10-03T11:50:00Z"
}
```

A replayed record stays in the list, because the dead-letter topic is only ever added to. Replaying it again is safe: the consumer skips an event ID it has already stored.

---

## 13. Events (Kafka)

All events share one envelope, the one the ledger uses today:

<!-- example: event-envelope.json -->
```json
{
  "eventId": "evt_3b1c…",
  "eventType": "TRANSACTION_STAGE_CHANGED",
  "timestamp": "2026-10-03T11:40:00.060Z",
  "version": "1.0",
  "correlationId": "e7b8c1…",
  "payload": {}
}
```

`correlationId` is new and optional for existing consumers.

| Topic | Key | Producer | Consumers | Status |
| --- | --- | --- | --- | --- |
| `ledger.mutation.completed.v1` | accountId | ledger-service | notification-service | SAME |
| `ledger.transfer.completed.v1` | sourceAccountId | ledger-service | notification-service, orchestration-service | SAME, plus `transactionId` and `auditHash` in the payload |
| `ledger.transfer.failed.v1` | sourceAccountId | ledger-service | notification-service | SAME |
| `account.created.v1`, `account.closure.completed.v1` | accountId | account-service | notification-service | SAME |
| `kyc.request.submitted.v1`, `kyc.request.evaluated.v1` | customerId | customer-service | notification-service | CHANGED: produced by customer-service |
| `transaction.stage.changed.v1` | transactionId | transaction-service (1–3), orchestration-service (4–11) | transaction-service | NEW |
| `notification.dispatched.v1` | transactionId | notification-service | orchestration-service | NEW |
| `batch.eod.step.completed.v1` | batchRunId | batch-service | notification-service (optional) | NEW |
| `notification.events.DLT` | as original | notification-service, for every topic it consumes, after the retries | notification-service (replay, N4 to N6) | NEW |
| `<topic>.DLT` | as original | orchestration-service and transaction-service, after the retries | none: there is no replay endpoint | NEW |

**`transaction.stage.changed.v1` payload**

<!-- example: event-transaction-stage-changed.payload.json -->
```json
{
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "referenceNo": "TX-20261003-8F3A21C0",
  "stageNo": 4,
  "stageName": "FRAUD_CHECK",
  "outcome": "PASSED",
  "transactionStatus": "PROCESSING",
  "failureCode": null,
  "detail": "score 0.15",
  "customerMessage": null,
  "occurredAt": "2026-10-03T11:40:00.060Z"
}
```

**`ledger.transfer.completed.v1` payload (today's fields, two added)**

<!-- example: event-ledger-transfer-completed.payload.json -->
```json
{
  "transferReference": "TX-20261003-8F3A21C0",
  "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 2500.00,
  "sourcePreviousBalance": 49625.00,
  "sourceNewBalance": 47125.00,
  "destinationPreviousBalance": 25375.00,
  "destinationNewBalance": 27875.00,
  "sourceCustomerId": 1,
  "destinationCustomerId": 2,
  "auditHash": "a3f5b8e9c1d0…"
}
```

Amounts in the three existing ledger events stay JSON numbers so the current consumer keeps working.

**`notification.dispatched.v1` payload**

<!-- example: event-notification-dispatched.payload.json -->
```json
{ "transactionId": "6f1c0e0a-…", "referenceNo": "TX-20261003-8F3A21C0", "sourceEventId": "evt_9a…", "customerIds": [1, 2], "channel": "IN_APP", "dispatchedAt": "2026-10-03T11:40:01.200Z" }
```

**`batch.eod.step.completed.v1` payload.** One element of the `steps` array in B2, plus `batchRunId` and `businessDate`.

<!-- example: event-batch-eod-step-completed.payload.json -->
```json
{
  "batchRunId": 31,
  "businessDate": "2026-10-03",
  "stepNo": 1,
  "stepName": "INTEREST_ACCRUAL",
  "status": "COMPLETED",
  "itemsProcessed": 3,
  "totalAmount": "12.24",
  "durationMs": 820,
  "message": null
}
```

**Dead-letter rule:** retries with an exponential backoff of 1 s, 2 s, 4 s; when the last one fails the record is parked with its original key. notification-service parks every topic it consumes on one topic, `notification.events.DLT`, with headers `x-original-topic`, `x-exception-message`, `x-attempts` and `x-failed-at`, and parks a record it cannot read as JSON at once, without retries. orchestration-service and transaction-service park on `<topic>.DLT`.

---

## 14. Inter-bank transfers

The full proposal for partner groups is in [`INTERBANK_TRANSFER_PROPOSAL.md`](INTERBANK_TRANSFER_PROPOSAL.md). This section is what **we** expose and how it maps inside.

| # | Method and path | Direction | Purpose |
| --- | --- | --- | --- |
| I1 | `POST /api/v1/interbank/transfers` | Partner → us | Credit one of our accounts |
| I2 | `GET /api/v1/interbank/transfers/{messageId}` | Partner → us | Status of a transfer they sent |
| I3 | `GET /api/v1/interbank/accounts/{accountNumber}` | Partner → us | Optional name check before sending |

These three are not protected by our JWT. They require `X-Bank-Code`, `X-Timestamp` and `X-Signature` as defined in the proposal.

**Inbound mapping**

| Step | What happens |
| --- | --- |
| 1 | `InterbankAuthFilter` checks the bank code, timestamp window and signature |
| 2 | `messageId` is the idempotency key; a repeat returns the stored response |
| 3 | The beneficiary account is looked up by number (K7) and must be `ACTIVE` |
| 4 | A transaction is created with `channel = INTERBANK` |
| 5 | `transaction-service` calls O4; the saga posts through L8: debit our settlement GL account for that partner, credit the beneficiary |
| 6 | Response `ACCEPTED` with our `referenceNo`, or `REJECTED` with a reason code |

**Outbound mapping** (T1 with a foreign `destinationBankCode`)

| Step | What happens |
| --- | --- |
| 1 | Normal saga up to the hold on the sender's account |
| 2 | `InterbankClient` calls the partner's endpoint, translating our fields to theirs |
| 3 | `ACCEPTED`: capture the hold against the settlement GL account for that partner |
| 4 | `REJECTED`: release the hold; the transaction fails with `INTERBANK_REJECTED` |
| 5 | No answer: query the partner's status endpoint up to three times, then keep the hold and flag for staff. Never release on a timeout alone |

One adapter class per partner sits behind `InterbankClient`, so a partner whose payload differs from the proposal needs only its own mapping.
