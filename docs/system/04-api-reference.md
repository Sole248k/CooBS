# 4. API reference

This is the complete endpoint list read from the controllers on `dev`. Request and response bodies for each endpoint ID are in `contracts/examples/<ID>-*.json` and in `contracts/api/openapi.yaml` (served by Swagger UI at `http://localhost:8080/swagger-ui` through the gateway, or `http://localhost:8086` directly).

## 4.1 Gateway routes and rate limits

Base URL: `http://localhost:8080` locally; `https://coobs.indonesiacentral.cloudapp.azure.com` in Azure.

| Route ID | Path | Upstream | Rate (req/s) | Burst | Key |
| --- | --- | --- | --- | --- | --- |
| `auth-service-route` | `/api/v1/auth/**` | auth-service :8081 | 10 | 20 | Client IP |
| `account-service-accounts-route` | `/api/v1/accounts/**` | account-service :8082 | 50 | 100 | User, else IP |
| `customer-service-route` | `/api/v1/customers/**` | customer-service :8089 | 50 | 100 | User, else IP |
| `ledger-service-route` | `/api/v1/ledger/**` | ledger-service :8083 | 20 | 40 | User, else IP |
| `notification-service-route` | `/api/v1/notifications/**` | notification-service :8084 | 50 | 100 | User, else IP |
| `audit-service-route` | `/api/v1/audit/**` | audit-service :8085 | 30 | 60 | User, else IP |
| `transaction-service-route` | `/api/v1/transactions/**` | transaction-service :8090 | 20 | 40 | User, else IP |
| `transaction-service-interbank-route` | `/api/v1/interbank/**` | transaction-service :8090 | 20 | 40 | User, else IP |
| `batch-service-route` | `/api/v1/batch/**` | batch-service :8093 | 10 | 20 | User, else IP |
| `mobile-bff-route` | `/api/v1/mobile/**` | mobile-bff :8094 | 50 | 100 | User, else IP |
| `staff-bff-route` | `/api/v1/staff/**` | staff-bff :8095 | 50 | 100 | User, else IP |
| `swagger-ui-route` | `/swagger-ui/**`, `/v3/api-docs/**`, `/openapi.yaml` | swagger-ui :8086 | — | — | — |

Not routed (403 from the gateway): `/internal/**`. No route exists for `orchestration-service` or `risk-engine`. Exceeding a limit returns `429` with `X-RateLimit-*` headers.

Gateway operational endpoints: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`.

## 4.2 Conventions

### Headers

| Header | Required on | Rule |
| --- | --- | --- |
| `Authorization: Bearer <jwt>` | Everything except login, token refresh and inter-bank | Validated by each service |
| `X-Correlation-ID` | Every request (gateway adds one) | Passed on to every call, log line and event |
| `Idempotency-Key` | Every `POST` that moves money | Client UUID on public calls (max 40 chars on T1). Internal ledger calls use `<transactionId>:<step>` (`:hold`, `:capture`, `:release`, `:reverse`) |
| `Content-Type: application/json` | Every body | UTF-8 |
| `X-Bank-Code`, `X-Timestamp`, `X-Signature` or `X-Api-Key` | Inter-bank inbound | See [section 10.6](10-security.md#106-inter-bank-authentication) |

### Data formats

| Item | Format |
| --- | --- |
| Money | String with 2–4 decimals (`"2500.00"`) on new endpoints and all events; JSON number on older account/ledger endpoints. Stored as `NUMBER(18,4)` / `NUMERIC(18,4)` |
| Currency | `"PHP"` |
| Time | ISO-8601 UTC with `Z` |
| `transactionId` | UUID created by `transaction-service` |
| `referenceNo` | `TX-YYYYMMDD-XXXXXXXX` (transfer), `RV-…` (reversal), max 36 chars |
| Account number | `ACC_10000001`; GL accounts `GL_CASH_VAULT` etc. |
| Paging | `?page=0&size=20` (max 100) → `{content, pageNumber, pageSize, totalElements, totalPages, isLast}` |

### Errors (RFC 7807)

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

Clients switch on `errorCode`:

| `errorCode` | HTTP | Meaning |
| --- | --- | --- |
| `INVALID_REQUEST_PARAMETERS` | 400 | Validation failed; see `invalidParams` |
| `AUTH_ACCESS_DENIED` | 403 | Role not allowed |
| `AUTH_TOKEN_REVOKED` | 401 | Token on the blacklist |
| `ACCOUNT_NOT_FOUND` | 404 | No such account |
| `ACCOUNT_NOT_ACTIVE` | 422 | Account frozen, dormant or closed |
| `ACCOUNT_NOT_OWNED` | 403 | Member tried to send from someone else's account |
| `ACCOUNT_UNAVAILABLE` | 503 | Account could not be checked (on-premises unreachable) |
| `ACCOUNT_NON_ZERO_BALANCE` | 400 | Closure refused: balance not zero |
| `ACCOUNT_ACTIVE_FLAG_EXISTS` | 422 | Closure refused: active flag |
| `TRANSACTION_NOT_FOUND` | 404 | Unknown transaction ID or reference |
| `INSUFFICIENT_FUNDS` | 422 | Available balance below the amount |
| `DUPLICATE_IDEMPOTENCY_KEY` | 409 | Same key still in flight, or reused for a different request |
| `CONCURRENT_TRANSACTION_IN_FLIGHT` | 409 | Lock held by another request |
| `INVALID_TRANSACTION` | 400 | e.g. self-transfer, non-positive amount |
| `LEDGER_AUDIT_DUAL_WRITE_FAILED` | 500 | Audit store refused; posting rolled back |
| `RISK_DECLINED` | 422 (saga outcome) | Fraud score in a decline band |
| `LIMIT_EXCEEDED` | 422 (saga outcome) | Daily limit would be exceeded |
| `RISK_UNAVAILABLE` | 503 (saga outcome) | Risk engine or its counters unreachable |
| `HOLD_NOT_FOUND`, `HOLD_NOT_RESERVED` | 404, 409 | Hold missing or already resolved |
| `ALREADY_REVERSED` | 409 | Transaction reversed before |
| `REVERSAL_NOT_ALLOWED` | 422 | e.g. outbound inter-bank transfer |
| `LEDGER_UNAVAILABLE` | 503 | Ledger silent beyond the retry window |
| `BATCH_ALREADY_RUNNING` | 409 | An EOD run is in progress |
| `INTERBANK_REJECTED`, `INTERBANK_TIMEOUT` | 422, 504 | Partner bank refused / did not answer |
| `INTERNAL_SERVER_ERROR` | 500 | Anything else |

Saga outcomes (`RISK_DECLINED`, `LIMIT_EXCEEDED`, `INSUFFICIENT_FUNDS`, …) are not returned by T1, which always answers 202; they appear as the transaction's `failureCode` and on the failed stage of its timeline.

## 4.3 auth-service (`/api/v1/auth`)

| ID | Method and path | Auth | Body → response |
| --- | --- | --- | --- |
| A2 | `POST /customers/login` | None | `{username, password}` → `{mfaRequired:false, authResponse:{accessToken, refreshToken, tokenType, expiresIn, customerId/userId, username, roles, userType}}` |
| A4 | `POST /staff/login` | None | Same shape; roles `ROLE_TELLER` or `ROLE_ADMIN` |
| A6 | `POST /token/refresh` | None | `{refreshToken}` → new pair (refresh token rotated) |
| A7 | `POST /token/revoke` | Bearer | → 204; access token blacklisted |
| A8 | `GET /token/me` | Bearer | → `{userId, username, roles, userType, …}` |

## 4.4 customer-service (`/api/v1/customers`)

| ID | Method and path | Roles |
| --- | --- | --- |
| C1 | `GET /me` | CUSTOMER |
| C2 | `GET /{customerId}` | TELLER, ADMIN |
| C3 | `GET /me/360` | CUSTOMER |
| C3 | `GET /{customerId}/360` | CUSTOMER (own only), TELLER, ADMIN |
| C4 | `GET /me/beneficiaries` | CUSTOMER |
| C5 | `POST /me/beneficiaries` | CUSTOMER |
| C6 | `DELETE /me/beneficiaries/{beneficiaryId}` | CUSTOMER |
| — | `POST /kyc/submit` | CUSTOMER |
| — | `POST /kyc/update-request` | CUSTOMER |
| — | `GET /kyc/update-requests?status=PENDING` | TELLER, ADMIN |
| — | `POST /kyc/update-requests/{id}/approve` (also `/kyc/update-request/{id}/approve`) | TELLER, ADMIN |
| — | `POST /kyc/update-requests/{id}/reject` (also singular) — body `{rejectionReason}` | TELLER, ADMIN |
| C7 | `GET /internal/v1/customers/{customerId}/risk-profile` | SERVICE |

## 4.5 account-service (`/api/v1/accounts`)

| ID | Method and path | Roles |
| --- | --- | --- |
| K3 | `POST /` — `{customerId, accountType, currency}` | TELLER, ADMIN |
| — | `GET /` (optional `?customerId=`) | TELLER, ADMIN |
| K4 | `GET /my-accounts` (staff receive all) | CUSTOMER, TELLER, ADMIN |
| — | `GET /{accountId}` (owner or staff) | CUSTOMER, TELLER, ADMIN |
| — | `PATCH` or `POST /{accountId}/status` — `{status}` | TELLER, ADMIN |
| — | `POST /{accountId}/closure-request` — `{reason}` | CUSTOMER, TELLER, ADMIN |
| — | `GET /{accountId}/closure-request` | CUSTOMER |
| — | `GET /closure-requests?status=PENDING` | TELLER, ADMIN |
| — | `POST /closure-requests/{id}/approve` | ADMIN |
| — | `POST /closure-requests/{id}/reject` — `{rejectionReason}` | ADMIN |
| — | `POST /{accountId}/flags` — `{flagType, reason}` | TELLER, ADMIN |
| — | `GET /{accountId}/flags` | TELLER, ADMIN |
| — | `DELETE /{accountId}/flags/{flagId}` | ADMIN |
| — | `GET /vaults` · `POST /vaults` · `GET /vaults/{id}` · `DELETE /vaults/{id}` | CUSTOMER, TELLER, ADMIN |
| — | `POST /vaults/{id}/allocate` · `POST /vaults/{id}/release` — `{amount, notes}` · `GET /vaults/{id}/transactions` | CUSTOMER, TELLER, ADMIN |
| — | `GET /share-capital` · `POST /share-capital/top-up` · `GET /share-capital/dividends` · `GET /share-capital/transactions` | CUSTOMER, TELLER, ADMIN |
| K6 | `GET /internal/v1/accounts/{accountId}` | SERVICE |
| K7 | `GET /internal/v1/accounts/by-number/{accountNumber}` | SERVICE |
| — | `GET /internal/v1/customers/{customerId}/accounts` | SERVICE |

## 4.6 ledger-service

| ID | Method and path | Roles | Notes |
| --- | --- | --- | --- |
| L-S1 | `GET /api/v1/ledger/accounts/{id}/balance` (alias `/balance/{id}`) | TELLER, ADMIN | `{accountId, currency, availableBalance, ledgerBalance, heldAmount, asOfTimestamp, isCached}` |
| — | `POST /api/v1/ledger/credits` (alias `/credit`) | TELLER, ADMIN | `Idempotency-Key`; `{accountId, amount, referenceNo, remarks, channel}` |
| — | `POST /api/v1/ledger/debits` (alias `/debit`) | TELLER, ADMIN | As above |
| — | `POST /api/v1/ledger/transfers` (alias `/transfer`) | TELLER, ADMIN | Direct posting; `{sourceAccountId, destinationAccountId, amount, referenceNo, remarks}` |
| L3 | `POST /internal/v1/ledger/holds` | SERVICE | `{transactionId, referenceNo, accountId, amount, currency, actorId}` → `{holdId, status: RESERVED}` |
| L4 | `POST /internal/v1/ledger/holds/{holdId}/capture` | SERVICE | `{destinationAccountId, remarks, actorId, clientIp}` → journal entries |
| L5 | `POST /internal/v1/ledger/holds/{holdId}/release` | SERVICE | `{reason}` (`CAPTURE_FAILED`, `HOLD_ABANDONED`, `INTERBANK_REJECTED`, …) → `CANCELLED` |
| — | `GET /internal/v1/ledger/holds/{holdId}` | SERVICE | Hold detail |
| L7 | `POST /internal/v1/ledger/reversals` | SERVICE | `{originalReferenceNo, reversalReferenceNo, reason, actorId}` |
| L8 | `POST /internal/v1/ledger/postings` | SERVICE | `{batchRunId?, stepName?, transactionId?, actorId?, postings:[{referenceNo, debitAccountId, creditAccountId, amount, remarks}]}` → `{posted, failed, results[]}` |

## 4.7 transaction-service

| ID | Method and path | Roles | Notes |
| --- | --- | --- | --- |
| T1 | `POST /api/v1/transactions/transfers` | Any authenticated | `Idempotency-Key`; `{sourceAccountId, destinationAccountNumber, destinationBankCode?, beneficiaryName?, amount, currency, category?, remarks?}` → 202 `{transactionId, referenceNo, status: INITIATED, amount, currency, createdAt, timelineUrl, replayed?}` |
| T2 | `GET /api/v1/transactions/{transactionId or referenceNo}` | Owner, staff, service | Detail |
| T3 | `GET /api/v1/transactions/{id}/timeline?view=staff|customer` | Owner, staff | Staff view: 11 stages with details. Customer view: 3 milestones. A customer always gets the customer view |
| — | `GET /api/v1/transactions/{trackingRef}/status` | Owner, staff | Ingress / Screened / Committed / Notified |
| T5 | `POST /api/v1/transactions/{id}/reversal` | TELLER, ADMIN | `{reason}`; `Idempotency-Key` → 202 with reversal `transactionId` |
| T8 | `GET /internal/v1/transactions?customerId=&limit=` | SERVICE | Recent history |
| T9 | `GET /internal/v1/transactions/spending?customerId=&days=` | SERVICE | Spending by category |
| I1 | `POST /api/v1/interbank/transfers` (aliases `/transfers/inbound`, `/transfers/inward`) | Partner (signed) | Inbound credit; repeat `messageId` returns the first answer |
| I2 | `GET /api/v1/interbank/transfers/{messageId}` | Partner | `ACCEPTED`, `REJECTED`, `PROCESSING` or `NOT_FOUND` |
| I3 | `GET /api/v1/interbank/accounts/{accountNumber}` | Partner | Exists, active, masked name |
| I4 | `POST /internal/v1/interbank/outbound` | SERVICE | `{transactionId, referenceNo, destinationBankCode, amount, currency}` → `{partnerStatus: ACCEPTED|REJECTED|PENDING, partnerReference, reasonCode}` |

## 4.8 orchestration-service (internal only, `/internal/v1/sagas`)

| ID | Method and path | Notes |
| --- | --- | --- |
| O1 | `POST /transfers` | Start transfer saga; idempotent on `transactionId`; runs asynchronously |
| O2 | `POST /reversals` | Start reversal saga; `reversesTransactionId` |
| O3 | `GET /{transactionId}` | `{transactionId, sagaState, holdId, riskDecision, failureCode, updatedAt}` |
| O4 | `POST /inbound-interbank` | Synchronous single posting from settlement account to beneficiary |

## 4.9 risk-engine (internal only, `/internal/v1/risk`)

| ID | Method and path | Notes |
| --- | --- | --- |
| R1 | `POST /evaluate` | Request and response in [section 6](06-risk-engine.md) |
| R2 | `POST /outcomes` | `{transactionId, outcome: FAILED|REVERSED, customerId?}` |

## 4.10 notification-service (`/api/v1/notifications`)

| ID | Method and path | Roles |
| --- | --- | --- |
| N1 | `GET ""` or `/my-notifications?unreadOnly=&page=&size=` | CUSTOMER |
| N2 | `PATCH /{id}/read` | CUSTOMER |
| N3 | `GET /unread-count` | CUSTOMER |
| N4 | `GET /admin/dlq` | ADMIN |
| N5 | `POST /admin/dlq/{recordId}/replay` | ADMIN |
| — | `POST /admin/dlq/replay` (all) | ADMIN |

## 4.11 audit-service (`/api/v1/audit`) and batch-service (`/api/v1/batch`)

| ID | Method and path | Roles |
| --- | --- | --- |
| U1 | `GET /audit/accounts/{accountId}/statement?page=&size=&sort=` | TELLER, ADMIN |
| U2 | `GET /audit/transactions/{transactionId}` | TELLER, ADMIN |
| U3 | `GET /audit/verify-chain/{accountId}` → `{accountId, totalRecordsVerified, isChainIntact, latestHash, verifiedAt, message}` | TELLER, ADMIN, AUDITOR |
| U4 | `GET /audit/seals/{businessDate}` → `{businessDate, lastAuditId, sealHash, sealedAt, batchRunId}` | CUSTOMER, TELLER, ADMIN, AUDITOR |
| B1 | `POST /batch/eod/trigger` — `{businessDate?}` → 202 `{batchRunId, status}` | ADMIN |
| B2 | `GET /batch/eod/runs/{batchRunId}` → run with six steps | ADMIN, TELLER |
| B3 | `GET /batch/eod/runs` | ADMIN, TELLER |

## 4.12 mobile-bff (`/api/v1/mobile`)

| ID | Method and path | Roles |
| --- | --- | --- |
| M1 | `GET /dashboard` | CUSTOMER |
| M2 | `GET /transactions/{trackingReference}/timeline` | CUSTOMER |
| R1 | `GET /transactions/remittance/options` | CUSTOMER |
| M3 | `POST /transactions/remittance` (`Idempotency-Key`; amount ₱0.01–₱500,000.00) | CUSTOMER |

## 4.13 staff-bff (`/api/v1/staff`)

| Method and path | Roles | Forwards to |
| --- | --- | --- |
| `GET /customers/{customerId}/360` | TELLER, ADMIN | customer C3 |
| `POST /counter/deposit` | TELLER, ADMIN | ledger `/credits` |
| `POST /counter/withdraw` | TELLER, ADMIN | ledger `/debits` |
| `POST /counter/transfer` | **TELLER only** | transaction T1 |
| `GET /queues/summary` | TELLER, ADMIN | customer + account queues |
| `POST /queues/kyc/{requestId}/approve` · `/reject` | TELLER, ADMIN | customer KYC |
| `POST /queues/closures/{closureId}/approve` · `/reject` | ADMIN | account closure |
| `GET /accounts` · `POST /accounts` | TELLER, ADMIN | account |
| `PATCH|POST /accounts/{accountId}/status` | TELLER, ADMIN | account |
| `POST /accounts/{accountId}/closure-request` | TELLER, ADMIN | account |
| `POST /accounts/{accountId}/flags` | TELLER, ADMIN | account |
| `DELETE /accounts/{accountId}/flags/{flagId}` | ADMIN | account |
| `GET /transactions/{transactionId}/timeline` | TELLER, ADMIN | transaction T3 (staff view) |
| `POST /transactions/{transactionId}/reversal` | TELLER, ADMIN | transaction T5 |
| `GET /audit/accounts/{accountId}/statement` · `/audit/verify-chain/{accountId}` · `/audit/seals/{businessDate}` | TELLER, ADMIN | audit |
| `GET /admin/overview?businessDate=` | ADMIN | batch + notification DLQ |
| `POST /admin/batch/eod/trigger` | ADMIN | batch B1 |
| `GET /batch/eod/runs` · `/batch/eod/runs/{batchRunId}` | TELLER, ADMIN | batch B2/B3 |
| `GET /admin/dlq` · `POST /admin/dlq/{recordId}/replay` | ADMIN | notification N4/N5 |
