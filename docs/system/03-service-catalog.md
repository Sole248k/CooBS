# 3. Service catalogue

All services live under `backend/<service>/`, package `com.group2.fse.<service_name>`, built by the Maven aggregator `backend/pom.xml`. Each has a `Dockerfile`, exposes `/actuator/health` (with liveness/readiness probes), `/actuator/info`, `/actuator/metrics` and `/actuator/prometheus`, tags metrics with `application=<name>`, validates JWTs itself, and returns errors as RFC 7807 problem details.

Common layout inside a service: `controller/`, `service/` + `service/impl/`, `repository/`, `entity/`, `dto/`, `exception/` (with a `GlobalExceptionHandler`), `security/` (JWT filter, provider, handlers, `SecurityConfig`), `config/`, and `client/` for outbound HTTP.

## Summary

| Service | Port | Zone | Framework | Data | Publishes | Consumes |
| --- | --- | --- | --- | --- | --- | --- |
| `api-gateway` | 8080 | Cloud | Spring Cloud Gateway 3.3.4 (WebFlux) | Redis (rate limits) | — | — |
| `auth-service` | 8081 | Cloud | Spring Boot 4.1.1 | `identity_db`; Redis | — | — |
| `account-service` | 8082 | On-prem | Spring Boot 4.1.1 | Oracle; Redis (revocation list) | `account.created.v1`, `account.closure.completed.v1` | — |
| `ledger-service` | 8083 | On-prem | Spring Boot 4.1.1 | Oracle; PostgreSQL audit store (write); Redis | `ledger.mutation.completed.v1`, `ledger.transfer.completed.v1`, `ledger.transfer.failed.v1` | — |
| `notification-service` | 8084 | Cloud | Spring Boot 4.1.1 | `notification_db` | `notification.dispatched.v1` | ledger, account and KYC topics |
| `audit-service` | 8085 | On-prem | Spring Boot 4.1.1 | PostgreSQL audit store (read) | — | — |
| `customer-service` | 8089 | Cloud | Spring Boot 4.1.1 | `customer_db`; Redis | — | — |
| `transaction-service` | 8090 | Cloud | Spring Boot 4.1.1 | `transaction_db.txn`; Redis | `transaction.stage.changed.v1` (stages 1–3) | `transaction.stage.changed.v1` |
| `orchestration-service` | 8091 | Cloud, internal | Spring Boot 4.1.1, virtual threads | `transaction_db.saga` | `transaction.stage.changed.v1` (stages 4–11) | `ledger.transfer.completed.v1`, `notification.dispatched.v1` |
| `risk-engine` | 8092 | Cloud, internal | Spring Boot 4.1.1 | Redis (counters) | — | — |
| `batch-service` | 8093 | On-prem | Spring Boot 4.1.1 | PostgreSQL `batch` + `audit_store`; reads Oracle | `batch.eod.step.completed.v1`, `ledger.mutation.completed.v1` (month-end interest) | — |
| `mobile-bff` | 8094 | Cloud | Spring Boot 4.1.1 | None | — | — |
| `staff-bff` | 8095 | Cloud | Spring Boot 4.1.1 | None | — | — |

---

## 3.1 api-gateway

**Purpose.** The only public entry for API calls.

**Behaviour.**
- Routes by path prefix to ten upstreams (table in [section 4.1](04-api-reference.md#41-gateway-routes-and-rate-limits)); also routes `/swagger-ui/**`, `/v3/api-docs/**`, `/openapi.yaml` to the Swagger UI container.
- Redis token-bucket rate limiting per route. `ipKeyResolver` for auth; `userOrIpKeyResolver` (JWT subject, else client IP) for the rest (`config/RateLimiterConfig.java`).
- `CorrelationIdFilter`: reuses or generates `X-Correlation-ID` and returns it.
- `InternalPerimeterRejectionFilter`: refuses any `/internal/**` path, including URL-encoded variants.
- `RequestLoggingFilter`: method, path, status, latency per request.
- `GlobalErrorWebExceptionHandler`: 503 when an upstream cannot be reached, 504 on response timeout, in problem-details form.
- Global CORS (allowed origins from `CORS_ALLOWED_ORIGINS` plus any `localhost`/`127.0.0.1` port); strips `Origin` before forwarding.
- Optional TLS (`SSL_ENABLED`, keystore in `resources/keystore/`); off by default because TLS ends at Caddy in Azure.

**No route exists** for `orchestration-service`, `risk-engine` or any `/internal/**` path.

## 3.2 auth-service

**Purpose.** Sign-in for members and staff; access and refresh tokens; revocation; "who am I".

**Endpoints.** A2 `POST /api/v1/auth/customers/login`, A4 `POST /api/v1/auth/staff/login`, A6 `POST /api/v1/auth/token/refresh`, A7 `POST /api/v1/auth/token/revoke`, A8 `GET /api/v1/auth/token/me`.

**Behaviour.**
- Passwords checked against bcrypt hashes in `identity_db.app_user`; `INACTIVE` users refused (`AccountInactiveException`).
- Access token: HMAC-signed JWT with `sub`, `userId`, `roles`, `userType` (`CUSTOMER`/`STAFF`) and a unique `jti`. Lifetime from `JWT_EXPIRATION_MS` (default 15 min in this service's properties; 24 h in Compose; 2 h in Azure).
- Refresh token: opaque value stored in Redis for 7 days, rotated on every use; refused for a user no longer `ACTIVE`.
- **Single session for members** (`SingleSessionGuard`): a new sign-in replaces the stored token pair, deletes the old refresh token and blacklists the old access token. Staff are not limited.
- Revocation writes `jti` to a Redis blacklist with the token's remaining lifetime. The blacklist is checked by `auth-service`, `account-service` and `ledger-service` (other services rely on token expiry).
- `mfaRequired` is always `false`; there is no registration endpoint.

## 3.3 customer-service

**Purpose.** Member profile, Customer 360, beneficiaries, KYC update requests, and the risk profile the saga needs.

**Endpoints.** C1 `GET /me`, C2 `GET /{customerId}` (staff), C3 `GET /me/360` and `GET /{customerId}/360`, C4–C6 beneficiaries (`GET`/`POST /me/beneficiaries`, `DELETE /me/beneficiaries/{id}`), KYC `POST /kyc/submit`, `POST /kyc/update-request`, `GET /kyc/update-requests` (staff), approve/reject (staff, both `/update-requests/` and `/update-request/` spellings), C7 internal `GET /internal/v1/customers/{id}/risk-profile`. All public paths are under `/api/v1/customers`.

**Behaviour.**
- **Customer 360** aggregates: profile (`displayName`, `membershipNumber`, `kycStatus`, `kycTier`, `creditScore`, `riskScore` — risk score shown to staff only), accounts with `availableBalance`/`ledgerBalance`/`heldAmount` and totals of deposits and loans (from `account-service` internal API), recent transactions (T8) and spending by category (T9) from `transaction-service`.
- A customer may read only their own 360; staff may read any.
- **Risk profile** returns `riskScore` (0.00–1.00), `kycTier` (default `TIER_1_BASIC`) and `dailyLimit` (default 100,000.00) for the orchestrator.
- Owns `customer`, `channel_profile`, `kyc`, `kyc_update_request`, `beneficiary` in `customer_db`.

## 3.4 account-service

**Purpose.** Account lifecycle, status, flags and closures; Ipon Vaults; share capital. On-premises, reads and writes Oracle.

**Endpoints** (all under `/api/v1/accounts` unless noted).

| Area | Endpoints | Roles |
| --- | --- | --- |
| Lifecycle | `POST /` open (K3); `GET /` all accounts (`?customerId=` filter); `GET /my-accounts`; `GET /{id}`; `PATCH|POST /{id}/status` | Open/list/status: TELLER, ADMIN. Read own: CUSTOMER |
| Closure | `POST /{id}/closure-request`; `GET /{id}/closure-request` (customer); `GET /closure-requests`; `POST /closure-requests/{id}/approve|reject` | Request: any; queue: staff; decide: ADMIN |
| Flags | `POST /{id}/flags`; `GET /{id}/flags`; `DELETE /{id}/flags/{flagId}` | Add/list: staff; remove: ADMIN |
| Vaults | `/vaults` `GET`, `POST`; `/vaults/{id}` `GET`, `DELETE`; `/{id}/allocate`, `/{id}/release`, `/{id}/transactions` | CUSTOMER, TELLER, ADMIN |
| Share capital | `/share-capital` `GET`; `/top-up` `POST`; `/dividends`, `/transactions` `GET` | CUSTOMER, TELLER, ADMIN |
| Internal | `GET /internal/v1/customers/{id}/accounts`, `/internal/v1/accounts/{id}`, `/internal/v1/accounts/by-number/{no}` | SERVICE |

**Business rules.**
- New accounts get a generated number (`ACC_XXXXXXXX`), status `ACTIVE` and a zero `BALANCE` row; `account.created.v1` is published. There is no opening deposit on this endpoint; money arrives through counter deposit or transfer.
- Status values: `ACTIVE`, `FROZEN`, `DORMANT`, `CLOSED`. The ledger refuses postings to any non-`ACTIVE` account (`ACCOUNT_NOT_ACTIVE`).
- Closure request is refused when the account is already `CLOSED`, a `PENDING` request exists, an `ACTIVE` flag exists (`ACCOUNT_ACTIVE_FLAG_EXISTS`), or the balance is not zero (`ACCOUNT_NON_ZERO_BALANCE`). Approval re-checks the balance, sets the account `CLOSED`, and publishes `account.closure.completed.v1`.
- A customer may only see and act on accounts they own (`UnauthorizedAccountAccessException`).
- **Ipon Vaults** earmark money inside a deposit account: allocation lowers the account's `available_balance` and raises `vault.current_amount`; release and close reverse it. A vault reaching its target becomes `COMPLETED`. References: `VTX-…`.
- **Share capital**: one certificate per member (`SC-2024-NNNNNN`), par value ₱100, minimum 20 shares for voting rights (`REGULAR` vs `ASSOCIATE`), dividend rate 7.25%. Top-up debits the chosen account's available balance and adds `floor(amount / par)` shares. A member without a record is provisioned on first read with demonstration values (250 paid-up shares, two years of dividend history).
- Vault and share-capital movements write `BALANCE` directly rather than through the ledger; see [section 14](14-known-gaps-and-limitations.md).

## 3.5 ledger-service

**Purpose.** The only writer of balances and the journal. Double-entry postings, holds, capture, release, reversals, batch postings, transactional outbox, audit rows at commit, balance cache.

**Endpoints.**

| ID | Endpoint | Caller | Purpose |
| --- | --- | --- | --- |
| L-S1 | `GET /api/v1/ledger/accounts/{id}/balance` (alias `/balance/{id}`) | Staff | Balance, cached 10 s in Redis (`ledger:balance:{id}`) |
| — | `POST /api/v1/ledger/credits` (`/credit`), `/debits` (`/debit`) | Staff (counter cash via `staff-bff`) | Single-leg posting on one account (active status and funds checked); `Idempotency-Key` required |
| — | `POST /api/v1/ledger/transfers` (`/transfer`) | Staff | Direct two-account transfer (legacy path; members use T1) |
| L3 | `POST /internal/v1/ledger/holds` | Orchestrator | Place hold: `available_balance -= amount` |
| L4 | `POST /internal/v1/ledger/holds/{id}/capture` | Orchestrator | Post: source `ledger_balance -= amount`, destination both balances `+= amount`, two journal rows, audit rows, outbox row |
| L5 | `POST /internal/v1/ledger/holds/{id}/release` | Orchestrator | `available_balance += amount`, hold `CANCELLED` |
| — | `GET /internal/v1/ledger/holds/{id}` | Orchestrator | Hold detail |
| L7 | `POST /internal/v1/ledger/reversals` | Orchestrator | Compensating `REVERSAL_CREDIT`/`REVERSAL_DEBIT` legs |
| L8 | `POST /internal/v1/ledger/postings` | Orchestrator (inbound inter-bank), batch | A list of debit/credit pairs, each committed separately, each idempotent by reference |

`/api/v1/ledger/**` requires TELLER or ADMIN; `/internal/v1/**` requires SERVICE. Members never call the ledger.

**Internals.** `PostingEngine`/`PostingEngineImpl` is the single place balances are written (`post(balance, availableDelta, ledgerDelta)`); locks are `SELECT … FOR UPDATE` taken in ascending account-ID order (`lockAccountsInOrder`) so two postings can never deadlock. `DualWriteLedgerAuditService` collects audit rows and writes them to PostgreSQL just before the Oracle commit (details in [section 7](07-ledger-audit-and-eod.md)). `OutboxRelay` polls `OUTBOX` every 500 ms and marks a row published only after Kafka acknowledges. `IdempotencyInterceptor` guards public mutations with a Redis `SETNX` lock (30 s) and caches successful responses for 24 h (replays carry `X-Cache-Replay`). `TokenBlacklistFilter` checks revoked tokens.

## 3.6 audit-service

**Purpose.** Read-only window onto the audit store.

**Endpoints** (`/api/v1/audit`): U1 `GET /accounts/{id}/statement` (paged, staff), U2 `GET /transactions/{id}` (forensic record, staff), U3 `GET /verify-chain/{accountId}` (TELLER, ADMIN, AUDITOR), U4 `GET /seals/{businessDate}`.

**Behaviour.** Verification re-computes every row's SHA-256 with the same formula as the PostgreSQL trigger (`AuditQueryServiceImpl.computeSha256`) and reports `isChainIntact`, `totalRecordsVerified`, the `latestHash` and a message naming where the chain broke, if it did.

## 3.7 transaction-service

**Purpose.** Transfer intake, history, timeline, reversal requests, and the inter-bank gateway in both directions.

**Endpoints.**

| ID | Endpoint | Roles | Purpose |
| --- | --- | --- | --- |
| T1 | `POST /api/v1/transactions/transfers` | Authenticated (member or staff) | Accept a transfer; 202 with `transactionId`, `referenceNo` (`TX-YYYYMMDD-XXXXXXXX`), `timelineUrl` |
| T2 | `GET /api/v1/transactions/{id}` | Owner or staff | Detail by UUID or reference |
| T3 | `GET /api/v1/transactions/{id}/timeline?view=` | Owner or staff | 11-stage staff view or 3-stage customer view (view follows the caller's role) |
| — | `GET /api/v1/transactions/{ref}/status` | Owner or staff | Four-phase execution status: Ingress → Screened → Committed → Notified |
| T5 | `POST /api/v1/transactions/{id}/reversal` | TELLER, ADMIN | Request a reversal (`RV-…`) |
| T8/T9 | `GET /internal/v1/transactions`, `/spending` | SERVICE | History and spending for Customer 360 |
| I1 | `POST /api/v1/interbank/transfers` (also `/inbound`, `/inward`) | Partner bank (signed) | Inbound credit |
| I2 | `GET /api/v1/interbank/transfers/{messageId}` | Partner bank | Status of an inbound message |
| I3 | `GET /api/v1/interbank/accounts/{accountNumber}` | Partner bank | Account confirmation with masked name |
| I4 | `POST /internal/v1/interbank/outbound` | SERVICE (orchestrator) | Send one transfer to a partner bank |

**Behaviour.** Intake validation, ownership, two-level idempotency, stage 1–3 recording and saga start are described in [section 5](05-transfer-lifecycle.md). A `StageEventListener` consumes `transaction.stage.changed.v1` and writes `txn_stage_event` rows and the transaction's status (`PROCESSING`, `POSTED`, `FAILED`, `REVERSED`). `SagaStartRedispatcher` resends saga starts that never arrived. Partners are configured under `app.interbank.partners.*`; a partner without a URL is served by `LoopbackPartnerAdapter` (accepts everything except account numbers starting `0000`); `AURABANK` has its own adapter (`X-API-Key`), others use `ProposalPartnerAdapter` (HMAC).

## 3.8 orchestration-service

**Purpose.** Runs the transfer saga (O1), reversal saga (O2) and inbound inter-bank saga (O4); publishes stages 4–11; recovers sagas left in flight. No public route.

**Endpoints** (`/internal/v1/sagas`, all SERVICE): `POST /transfers`, `POST /reversals`, `GET /{transactionId}` (O3), `POST /inbound-interbank`.

**Behaviour.** State machine `STARTED → RISK_APPROVED → FUNDS_RESERVED → POSTED → COMPLETED` with `FAILED`, `COMPENSATING`, `COMPENSATED`; optimistic locking on the saga row (`version`); `SagaRecoveryRunner` resumes in-flight sagas at start-up and every 30 s for rows untouched for 60 s; `DownstreamWarmUp` keeps the risk path warm; capture retried 3 times with 500 ms back-off; hold retries given up after 5 minutes. Full detail in [section 5](05-transfer-lifecycle.md).

## 3.9 risk-engine

**Purpose.** Decides APPROVE/DECLINE for a transfer and holds the daily-limit reservation.

**Endpoints** (`/internal/v1/risk`, SERVICE): R1 `POST /evaluate`, R2 `POST /outcomes`.

**Behaviour.** Weighted fraud score with additive penalties, customer risk floor, bands at 0.60 and 0.85, tier daily limits, atomic Redis reservation, velocity and reversal counters, device and geo checks. Throws `RISK_UNAVAILABLE` rather than guess when Redis cannot be read. Full model in [section 6](06-risk-engine.md).

## 3.10 notification-service

**Purpose.** In-app inbox fed by Kafka; dead-letter inspection and replay.

**Endpoints** (`/api/v1/notifications`): N1 `GET ""` or `/my-notifications` (`unreadOnly`, paging), N2 `PATCH /{id}/read`, N3 `GET /unread-count` (all CUSTOMER); N4 `GET /admin/dlq`, N5 `POST /admin/dlq/{recordId}/replay`, `POST /admin/dlq/replay` (all, ADMIN).

**Behaviour.** Listeners for ledger mutation, transfer completed, transfer failed, account closure, KYC submitted and KYC evaluated. Each event is stored once (unique `event_id`, idempotent consumer). Titles produced: "Account Debited", "Account Credited", "Monthly Interest Credited", "Funds Transfer Sent", "Funds Transfer Received", "Funds Transfer Failed", "Account Closed", "KYC Update Submitted/Approved/Rejected". After storing a transfer notification it publishes `notification.dispatched.v1`, which completes the saga. Failed records are retried with exponential back-off (3 attempts, 1 s × 2) and then sent to `notification.events.DLT` with headers `x-original-topic`, `x-exception-message`, `x-attempts`, `x-failed-at`; replay republishes to the original topic. SMS and e-mail channels are out of scope.

## 3.11 batch-service

**Purpose.** On-demand six-step end-of-day pipeline.

**Endpoints** (`/api/v1/batch`): B1 `POST /eod/trigger` (ADMIN), B2 `GET /eod/runs/{id}`, B3 `GET /eod/runs` (ADMIN, TELLER).

**Behaviour.** Steps `INTEREST_ACCRUAL`, `SERVICE_FEES`, `STANDING_INSTRUCTIONS`, `LOAN_AMORTIZATION`, `GL_RECONCILIATION`, `AUDIT_SEAL`, run asynchronously; every posting goes through ledger L8; a step that fails halts the run and skips later steps; a run left `RUNNING` by a restart is marked halted at the next start. Detail in [section 7.6](07-ledger-audit-and-eod.md#76-end-of-day-batch).

## 3.12 mobile-bff

**Purpose.** Screen-shaped API for the Flutter app.

**Endpoints** (`/api/v1/mobile`, CUSTOMER): M1 `GET /dashboard` (profile, primary account and balance, unread count, recent transactions, fetched concurrently; target < 150 ms), M2 `GET /transactions/{trackingReference}/timeline` (the 3-milestone customer view), R1 `GET /transactions/remittance/options` (amount bounds ₱0.01–₱500,000.00 and source accounts), M3 `POST /transactions/remittance` (validates, resolves accounts, forwards to T1, returns 202).

`/actuator/prometheus` requires an ADMIN token here, unlike other services.

## 3.13 staff-bff

**Purpose.** Screen-shaped API for the staff console.

**Endpoints** (`/api/v1/staff`): Customer 360; counter deposit, withdraw (TELLER, ADMIN) and assisted transfer (TELLER only); queue summary (`totalPendingKyc`, `totalPendingClosures`) and KYC/closure decisions; account list, open, status, closure request, flags; transaction timeline and reversal; audit statement, chain verification, seal; admin overview (EOD runs, DLQ count), EOD trigger and run list; DLQ list and replay. Full list in [section 4](04-api-reference.md#413-staff-bff-apiv1staff).

**Behaviour.** Forwards the staff member's token; counter cash calls ledger `/credits` and `/debits` with the console's own `Idempotency-Key` and reference (a key invented in the BFF would make a retried click pay twice). Aggregates fail softly: partial failures are listed in `partialFailures` instead of failing the whole screen.
