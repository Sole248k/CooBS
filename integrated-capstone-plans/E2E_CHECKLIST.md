# CooBS Platform — End-to-End (E2E) Verification Checklist & Test Guide

**Document Version:** 1.0 (Day 2 Wrap-up & Day 3 Readiness)  
**Editor & Owner:** Dev 1 (Carl — Lead, Contracts, Orchestration, E2E Checklist)  
**Target Environments:** Local Docker Compose / Azure Pipelines CI  
**Companion Artifacts:**
- Master E2E Postman Collection: [`postman/e2e/CooBS_End_To_End.postman_collection.json`](file:///d:/Fullstack/Capstone-dev/postman/e2e/CooBS_End_To_End.postman_collection.json)
- Environment Variables: [`postman/CooBS_Local.postman_environment.json`](file:///d:/Fullstack/Capstone-dev/postman/CooBS_Local.postman_environment.json)
- API Contract Source of Truth: [`contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md`](file:///d:/Fullstack/Capstone-dev/contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md)
- Runtime Settings: [`contracts/runtime/RUNTIME_SETTINGS.md`](file:///d:/Fullstack/Capstone-dev/contracts/runtime/RUNTIME_SETTINGS.md)

---

## 1. Executive Summary & Architecture Topology

The **CooBS (Cooperative Core Banking System)** comprises **11 microservices** and **5 shared datastores** operating across hybrid Cloud and On-Premises trust boundaries.

Before tagging `contracts-v1` or declaring release readiness, every service owner and the Lead must verify this checklist sequentially.

### 1.1 Microservice Port & Zone Matrix

| Service Name | Container Name | Host Port | Internal Port | Zone | Primary Responsibilities |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `api-gateway` | `api-gateway` | **8080** | 8080 | Cloud | Ingress routing, JWT gateway validation, rate limiting, correlation ID injection |
| `auth-service` | `auth-service` | **8081** | 8081 | Cloud | Customer & staff authentication, token issuance, refresh, revocation |
| `account-service` | `account-service` | **8082** | 8082 | On-Prem | Account catalog, KYC verification master, account status & closure flags |
| `ledger-service` | `ledger-service` | **8083** | 8083 | On-Prem | Double-entry system of record, balance holds (L3), hold captures (L4), releases (L5), reversals (L7) |
| `notification-service` | `notification-service` | **8084** | 8084 | Cloud | Kafka consumer for stage/balance events, in-app notifications, unread counters |
| `audit-service` | `audit-service` | **8085** | 8085 | On-Prem | Forensic SHA-256 cryptographic hash chain, immutable audit trail |
| `customer-service` | `customer-service` | **8089** | 8089 | Cloud | Customer 360 aggregation, beneficiary address book, channel risk profiles |
| `transaction-service` | `transaction-service` | **8090** | 8090 | Cloud | Customer transfer inception (T1), idempotency mutex, stages 1–3, DLQ replay |
| `orchestration-service`| `orchestration-service`| **8091** | 8091 | Cloud | 11-stage transfer saga engine (O1), reversal & compensation coordinator (O2), interbank saga (O4) |
| `risk-engine` | `risk-engine` | **8092** | 8092 | Cloud | Real-time rule evaluation, fraud check, daily limit checks (< 150ms SLA) |
| `batch-service` | `batch-service` | **8093** | 8093 | On-Prem | End-of-Day (EOD) interest accrual, statement generation, reconciliation postings |

### 1.2 Datastore & Broker Matrix

| Datastore | Container Name | Host Port | Database / Schema | Auth / User |
| :--- | :--- | :---: | :--- | :--- |
| **Oracle 23ai** | `oracle-core-db` | `1522` | Multi-tenant pluggable DB (`FREEPDB1`) | `system / password` |
| **PostgreSQL (Audit)** | `postgres-audit-db` | `5434` | `audit_db`, `batch_db` | `audit_user / audit_pass` |
| **PostgreSQL (Cloud)** | `postgres-cloud-db` | `5435` | `customer_db`, `transaction_db`, `saga` | `postgres / postgres` |
| **Redis** | `redis-cache` | `6379` | Token blacklists, idempotency locks | Default |
| **Kafka + KRaft** | `kafka-broker` | `9092` | Topics: `transaction.stage.changed.v1`, `ledger.postings.v1`, etc. | Plaintext |

---

## 2. Pre-Flight Infrastructure Readiness Checks

Execute these checks before running any automated or manual test runs.

- [ ] **Docker Containers Running**:
  ```bash
  docker compose ps
  # Ensure all 11 application containers and 5 datastores are healthy / Up
  ```
- [ ] **Oracle Core DB Initialized**:
  ```bash
  docker exec -it oracle-core-db healthcheck.sh
  # Verify tables: CUSTOMER, ACCOUNT, LEDGER_ENTRY, BALANCE, AUDIT_LOG
  ```
- [ ] **PostgreSQL Cloud & Audit Databases Populated**:
  ```bash
  # Check cloud DB (customer, transaction, saga tables)
  docker exec -it postgres-cloud-db psql -U postgres -d transaction_db -c "\dt saga.*"
  # Check audit DB
  docker exec -it postgres-audit-db psql -U audit_user -d audit_db -c "\dt"
  ```
- [ ] **Kafka Topics Created**:
  ```bash
  docker exec -it kafka-broker kafka-topics --bootstrap-server localhost:9092 --list
  # Required:
  # - transaction.stage.changed.v1
  # - transaction.dlq.v1
  # - ledger.postings.v1
  # - customer.events.v1
  ```
- [ ] **Redis Ping**:
  ```bash
  docker exec -it redis-cache redis-cli ping
  # Expected: PONG
  ```

---

## 3. Service Actuator & Health Verification Matrix

Run a health check across all 11 services through the Ingress Gateway and individual service ports.

| Target | Health Command | Expected Status | Checked |
| :--- | :--- | :---: | :---: |
| **API Gateway** | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}` | [ ] |
| **Auth Service** | `curl -s http://localhost:8081/actuator/health` | `{"status":"UP"}` | [ ] |
| **Account Service** | `curl -s http://localhost:8082/actuator/health` | `{"status":"UP"}` | [ ] |
| **Ledger Service** | `curl -s http://localhost:8083/actuator/health` | `{"status":"UP"}` | [ ] |
| **Notification Service** | `curl -s http://localhost:8084/actuator/health` | `{"status":"UP"}` | [ ] |
| **Audit Service** | `curl -s http://localhost:8085/actuator/health` | `{"status":"UP"}` | [ ] |
| **Customer Service** | `curl -s http://localhost:8089/actuator/health` | `{"status":"UP"}` | [ ] |
| **Transaction Service** | `curl -s http://localhost:8090/actuator/health` | `{"status":"UP"}` | [ ] |
| **Orchestration Service** | `curl -s http://localhost:8091/actuator/health` | `{"status":"UP"}` | [ ] |
| **Risk Engine** | `curl -s http://localhost:8092/actuator/health` | `{"status":"UP"}` | [ ] |
| **Batch Service** | `curl -s http://localhost:8093/actuator/health` | `{"status":"UP"}` | [ ] |

---

## 4. The 19 Handshakes (H1–H19) Alignment Matrix

| Handshake | Scope / Interfaces | Owners Involved | Status | Verified In E2E |
| :--- | :--- | :---: | :---: | :---: |
| **H1** | Shared Security Config & JWT validation | All Devs | **VERIFIED** | [ ] |
| **H2** | Deprecate customer registration in test suites | Dev 1 & Dev 2 | **VERIFIED** | [ ] |
| **H3** | Risk Profile Endpoint (`/internal/v1/customers/{id}/risk-profile`) | Dev 1 & Dev 3 | **VERIFIED** | [ ] |
| **H4** | Customer 360 Aggregation (Accounts & Transactions) | Dev 3, 2, 4 | **IN PROGRESS** | [ ] |
| **H5** | Risk Engine Evaluation Interface (`/internal/v1/risk/evaluate`) | Dev 1 & Dev 4 | **VERIFIED** | [ ] |
| **H6** | Ledger Hold & Capture Endpoints (L3/L4/L5/L7) | Dev 1 & Dev 5 | **VERIFIED** | [ ] |
| **H7** | 11-Stage Transaction Pipeline Enumeration | Dev 1, 3, 4, 6 | **VERIFIED** | [ ] |
| **H8** | Account Balance Read Delegation to Ledger | Dev 2 & Dev 5 | **IN PROGRESS** | [ ] |
| **H9** | Internal Account Lookup by Number (`/internal/v1/accounts/...`) | Dev 2 & Dev 4 | **IN PROGRESS** | [ ] |
| **H10** | Inbound/Outbound Inter-bank Wire Schema | Dev 1 & Dev 4 | **VERIFIED** | [ ] |
| **H11** | Dead-Letter Queue (DLQ) Notification Consumer | Dev 4 & Dev 6 | **IN PROGRESS** | [ ] |
| **H12** | Local Docker Compose Port Allocations | Dev 1 & Dev 8 | **VERIFIED** | [ ] |
| **H13** | Service-to-Service Base URL Environment Variables | All Devs | **VERIFIED** | [ ] |
| **H14** | Database Seed Population (`generate_seed.py`) | Dev 1 & Dev 5 | **VERIFIED** | [ ] |
| **H15** | Daily End-of-Day Ledger Postings Contract | Dev 5 & Dev 7 | **IN PROGRESS** | [ ] |
| **H16** | Notification Dispatch on Saga Completion | Dev 1 & Dev 6 | **VERIFIED** | [ ] |
| **H17** | SHA-256 Audit Dual-Write Consistency | Dev 5 & Dev 7 | **IN PROGRESS** | [ ] |
| **H18** | Multi-Branch Git Branching Policy & CI Gates | Dev 1 & Dev 8 | **VERIFIED** | [ ] |
| **H19** | Zero-Conflict Directory Ownership Protocol | All 8 Devs | **VERIFIED** | [ ] |

---

## 5. Automated E2E Execution Guide

### 5.1 Running the Master Suite via Newman CLI

The automated test suite can be run from the root of the repository:

```bash
# 1. Install Newman (if not already installed)
npm install -g newman

# 2. Run the End-to-End Master Test Suite
newman run postman/e2e/CooBS_End_To_End.postman_collection.json \
  -e postman/CooBS_Local.postman_environment.json \
  --delay-request 150 \
  --reporters cli
```

### 5.2 Running the Existing Legacy Collections (Regression Check)

To ensure no backward-incompatible breaks occurred:

```bash
# Core Banking Regression
newman run postman/CooBS_Core_Banking.postman_collection.json \
  -e postman/CooBS_Local.postman_environment.json \
  --delay-request 100

# Advanced Validation
newman run postman/CooBS_Advanced_Validation.postman_collection.json \
  -e postman/CooBS_Local.postman_environment.json

# Edge Cases & Compliance
newman run postman/CooBS_Edge_Cases_And_Compliance.postman_collection.json \
  -e postman/CooBS_Local.postman_environment.json

# Kafka Verification
newman run postman/CooBS_Kafka_Event_Driven_Verification.postman_collection.json \
  -e postman/CooBS_Local.postman_environment.json
```

---

## 6. End-to-End Functional Scenario Checklist

### Scenario 1: Identity, RBAC & Customer 360
- [ ] **1.1 Customer Login**: John Doe (`john_doe`) authenticates via `/api/v1/auth/customers/login` and receives Bearer JWT (`ROLE_CUSTOMER`).
- [ ] **1.2 Counterparty Login**: Maria Santos (`maria_santos`) authenticates and receives Bearer JWT.
- [ ] **1.3 Staff Login**: Teller Alice (`teller_alice`) receives Bearer JWT (`ROLE_TELLER`).
- [ ] **1.4 Customer 360 Profile**: Teller queries `/api/v1/customers/1/360` and verifies aggregated data: customer details, linked accounts (`ACC_10000001`), KYC verification status (`VERIFIED`), and risk tier (`TIER_2_STANDARD`).
- [ ] **1.5 Beneficiary Management**: John Doe adds `ACC_10000002` (Maria Santos) as a saved beneficiary via `/api/v1/customers/me/beneficiaries`.

### Scenario 2: Double-Entry Intra-Bank Transfer Saga (O1)
- [ ] **2.1 Pre-Transfer Balance Snapshot**:
  - Sender `ACC_10000001`: recorded as $B_{initial, sender}$.
  - Receiver `ACC_10000002`: recorded as $B_{initial, receiver}$.
- [ ] **2.2 Inception**: Client sends `POST /api/v1/transactions/transfers` with amount `2500.00` and unique `Idempotency-Key`.
- [ ] **2.3 Immediate Response**: Gateway returns HTTP `202 Accepted` with `status: "INITIATED"` and a UUID `transactionId`.
- [ ] **2.4 Saga Stage Traversal (Stages 1–8)**:
  - Stage 1: `INITIATED` (transaction-service)
  - Stage 2: `VALIDATED` (account-service check)
  - Stage 3: `AUTHENTICATED` (token claims validated)
  - Stage 4: `FRAUD_CHECK` (risk-engine check approved)
  - Stage 5: `LIMIT_CHECK` (daily limit not exceeded)
  - Stage 6: `FUNDS_CHECK` (hold L3 placed on ledger: holdId 9001)
  - Stage 7: `AUTHORIZED` (funds reserved)
  - Stage 8: `POSTED` (hold captured on ledger, status marked POSTED)
- [ ] **2.5 Post-Transfer Atomic Balance Verification**:
  - Sender `ACC_10000001` available balance == $B_{initial, sender} - 2500.00$.
  - Receiver `ACC_10000002` available balance == $B_{initial, receiver} + 2500.00$.
  - No imbalance permitted; ledger zero-sum debit/credit balance enforced.
- [ ] **2.6 Idempotency Replay**: Client resubmits the exact same payload with identical `Idempotency-Key`; returns HTTP `200 OK` or `202 Accepted` with same `transactionId` without creating a duplicate debit.

### Scenario 3: Transfer Reversal & Hold Compensation (O2 / FIN-104)
- [ ] **3.1 Reversal Initiation**: Staff sends `POST /api/v1/transactions/{id}/reversal` with reason and staff `actorId`.
- [ ] **3.2 Posted Reversal Execution**:
  - `orchestration-service` identifies the original transaction was in `POSTED`/`COMPLETED`.
  - Invokes `LedgerClient.reverse(...)` (L7 `/internal/v1/ledger/reversals`) with idempotency key `<transactionId>:reversal`.
  - Original transaction transitions to `REVERSED`.
  - Reversal saga transitions to `COMPLETED`.
  - Stages `POSTED`, `LEDGER_UPDATE`, `NOTIFICATION`, and `RECONCILIATION` are published.
- [ ] **3.3 Balance Compensation Verification**:
  - Sender `ACC_10000001` balance restored (credited +2500.00).
  - Receiver `ACC_10000002` balance debited (-2500.00).
- [ ] **3.4 Pre-Capture Compensation Drill**:
  - Transfer is interrupted while in `FUNDS_RESERVED`.
  - `ReversalSaga` invokes hold release L5 (`/internal/v1/ledger/holds/{id}/release`).
  - Original saga transitions to `COMPENSATED`, funds are unlocked, no ledger posting occurs.

### Scenario 4: Inbound & Outbound Inter-Bank Transfer (O4)
- [ ] **4.1 Outbound Dispatch**: Customer transfers funds with `destinationBankCode != 'COOBS'`; transaction marked `PROCESSING`, pending settlement webhook.
- [ ] **4.2 Inbound Webhook (O4)**:
  - Partner bank calls `/api/v1/interbank/transfers` with signature and reference number.
  - `orchestration-service` executes `InboundInterbankSaga.run(...)`.
  - Validates recipient account exists and is `ACTIVE`.
  - Credits recipient account via ledger postings API.
  - Returns `COMPLETED` and emits notification event.

### Scenario 5: Real-Time Event Dispatch & In-App Notifications
- [ ] **5.1 Notification Event Consumption**:
  - `notification-service` consumes `transaction.stage.changed.v1` events from Kafka.
  - Upon stage 8 `POSTED`, writes in-app notification to `notification_db`.
- [ ] **5.2 Customer Inbox Check**:
  - John Doe queries `GET /api/v1/notifications`; sees transfer confirmation notification.
  - Maria Santos queries `GET /api/v1/notifications`; sees incoming funds notification.
- [ ] **5.3 Read Receipt**:
  - John Doe marks notification read via `PATCH /api/v1/notifications/{id}/read`.
  - `GET /api/v1/notifications/unread-count` decrements by 1.

### Scenario 6: Cryptographic SHA-256 Audit Trail Verification
- [ ] **6.1 Immutable Audit Log**:
  - Ledger write produces dual-write audit log entry with `oldBalance`, `newBalance`, `actorId`, and `clientIp`.
- [ ] **6.2 Cryptographic Hash Chain Validation**:
  - Admin queries `GET /api/v1/audit/chain/verify`.
  - Audit service traverses all historical blocks, recalculates SHA-256 hashes ($H_n = \text{SHA256}(H_{n-1} + \text{data})$).
  - Asserts `isChainIntact: true` and verifies zero broken blocks.

### Scenario 7: Negative Testing, Risk Guardrails & Idempotency
- [ ] **7.1 Insufficient Funds**:
  - Customer attempts to transfer `999,999,999.00`.
  - Ledger hold check fails with `INSUFFICIENT_FUNDS`.
  - Returns HTTP `422 Unprocessable Entity`; saga marked `FAILED`; no money moved.
- [ ] **7.2 Risk Engine Decline**:
  - Transfer flagged as `DECLINE` with score `> 0.85` or rule violation.
  - Saga halts immediately at stage 4 (`FRAUD_CHECK` `FAILED`).
  - No hold placed; returns `RISK_DECLINED`.
- [ ] **7.3 Daily Limit Exceeded**:
  - Cumulative daily volume exceeds KYC tier limit.
  - Saga halts at stage 5 (`LIMIT_CHECK` `FAILED`); returns `LIMIT_EXCEEDED`.
- [ ] **7.4 Unauthorized Access (RBAC)**:
  - John Doe attempts to inspect Maria Santos's account (`GET /api/v1/accounts/2`).
  - Account service returns HTTP `403 Forbidden` (`AUTH_ACCESS_DENIED`).
- [ ] **7.5 Internal Endpoint Gateway Rejection**:
  - Client attempts to call `http://localhost:8080/internal/v1/sagas/transfers`.
  - API Gateway rejects with HTTP `404 Not Found` or `403 Forbidden` (internal routes never exposed publicly).

### Scenario 8: End-of-Day (EOD) Batch Settlement & Reconciliation
- [ ] **8.1 Batch Trigger**: Admin invokes `POST /api/v1/batch/jobs/eod-settlement`.
- [ ] **8.2 Multi-Step Execution**:
  - Step 1: Interest calculation.
  - Step 2: Fee assessment.
  - Step 3: GL batch posting to `ledger-service`.
  - Step 4: Statement generation.
- [ ] **8.3 Reconciliation Check**:
  - Reconciliation service verifies ledger balance matches aggregate transaction deltas.

---

## 7. Disaster Recovery & Failure Mode Drills

- [ ] **Drill A: Risk Engine Timeout (Fail-Closed)**:
  - Risk engine delayed > 150ms.
  - `orchestration-service` client times out, does NOT proceed to hold, marks transaction `FAILED` (`RISK_UNAVAILABLE`).
- [ ] **Drill B: Ledger Capture Failure (Compensating Hold Release)**:
  - Ledger hold placed (holdId 9001), but capture endpoint fails or times out.
  - `TransferSaga` retries 3 times, then executes compensating hold release L5.
  - Hold unlocked, customer funds freed, saga marked `FAILED`.
- [ ] **Drill C: Dead-Letter Queue (DLQ) Recovery**:
  - Kafka consumer fails repeatedly on poisoned message.
  - Message routed to `transaction.dlq.v1`.
  - Admin inspects DLQ via `/api/v1/notifications/admin/dlq` (N4) and triggers `/{recordId}/replay` (N5). `scripts/test_dead_letter_replay.ps1` runs this end to end.

---

## 8. Day 3 Formal Contract Lock Sign-Off

When all checks above are satisfied:

- [ ] All 11 microservice unit test suites green (`mvn test` across all services).
- [ ] Master E2E Newman test suite passes 100% (`0` failures).
- [ ] All 4 legacy Postman suites pass with zero regressions.
- [ ] Handshakes H1 through H19 verified.
- [ ] Git commit tagged:
  ```bash
  git tag -a contracts-v1 -m "Formal contract lock v1 — 11 services and E2E master suite verified"
  git push origin contracts-v1
  ```
