# Per-Developer Technical Work Packages

## CooBS Expansion: what each developer adds, changes and removes

**Version:** 1.0
**Payloads and paths:** [`contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md`](../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md)
**Basis:** a read of the `dev` branch: controllers, service classes, entities, configuration, Compose, CI, SQL and scripts. Line numbers are from that read and will drift.

Every item is tagged **ADD** (new file), **CHANGE** (edit an existing file) or **REMOVE**. Java paths are relative to `backend/<service>/src/main/java/com/group2/fse/<service_package>/`.

---

## 1. Code review findings that shape the work

These come from the existing code, not from the plan. Each one changes somebody's task list.

| #   | Finding                                                                                                                                                         | Where                                                                                  | Consequence                                                                                                                                            | Owner               |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------- |
| R1  | The token's`userId` claim means **customer ID** for customers and **staff user ID** for staff. `account-service` passes it straight in as the customer ID.      | `AccountController.java:62-108`, `JwtTokenProvider.java:71`                            | The new`identity_db` must keep this meaning, or four services break. See handshake H1.                                                                 | Dev 2               |
| R2  | The React staff console calls`/api/v1/ledger/transfers`, `/debits` and `/credits` directly.                                                                     | `frontend/src/services/api.ts`                                                         | The gateway's ledger route**cannot be retired** while React is the staff console. It stays, restricted to staff roles. This corrects the earlier plan. | Dev 8, Dev 3        |
| R3  | The ledger publishes events after commit, from a listener, straight to Kafka. A crash or tunnel drop between commit and send loses the event.                   | `LedgerEventPublisher.java:46`                                                         | This is the exact gap the outbox closes.                                                                                                               | Dev 3               |
| R4  | `AccountBalanceServiceImpl` is 642 lines and holds locking, posting, audit and events for debit, credit and transfer. Holds and reversals both need that logic. | `AccountBalanceServiceImpl.java`                                                       | Two developers would edit one file. Dev 3 extracts a`PostingEngine` first. See H4.                                                                     | Dev 3               |
| R5  | The ledger builds a`User` object just to store `approved_by`, and `Account` holds a `Customer` object just to read `customer_id`.                               | `AccountBalanceServiceImpl.java:105,178,290`, `entity/Account.java:25`                 | Both become plain`Long` fields; ten unused entity classes can go.                                                                                      | Dev 3               |
| R6  | `account-service` reads and writes `BALANCE` directly, including creating the balance row when an account is opened.                                            | `AccountServiceImpl.java:69,98-143`, `AccountClosureServiceImpl.java:51-61`            | Balance belongs to the ledger. Account opening and closure checks must call the ledger. See H3.                                                        | Dev 2, Dev 3        |
| R7  | `auth-service` login reads `CUSTOMER` and `KYC`; registration writes both.                                                                                      | `CustomerAuthServiceImpl.java:42-80,86,174`                                            | Login moves to`identity_db`. Registration is removed. The KYC name fields leave the login response; React does not use them.                           | Dev 2               |
| R8  | Three services carry a hard-coded fallback JWT secret, and the values differ.                                                                                   | `application.properties` in `account-service`, `notification-service`, `audit-service` | Remove the fallbacks so a missing secret fails at startup.                                                                                             | Dev 2, Dev 3, Dev 5 |
| R9  | `notification-service` defaults to an in-memory H2 database with `ddl-auto=update`; Compose points it at the audit PostgreSQL.                                  | `application.properties`, `docker-compose.yml:305`                                     | Repoint to `notification_db`; tables come from the init scripts, with `ddl-auto=validate`.                                                                                                     | Dev 3, Dev 5, Dev 8 |
| R10 | The Kafka error handler retries three times at one second, then logs and drops.                                                                                 | `KafkaConsumerConfig.java:72-80`                                                       | Replaced by exponential backoff and a dead-letter publisher.                                                                                           | Dev 3               |
| R11 | Event classes are copied by hand between`ledger-service` and `notification-service`.                                                                            | `event/LedgerTransferEvent.java` in both                                               | The AsyncAPI file becomes the single source; a change to one copy needs the other owner told. Both are Dev 3.                                          | Dev 3, Dev 1        |
| R12 | A fallback customer ID of`1` is used when no principal is present.                                                                                              | `AccountController.java:63`                                                            | Remove; an unauthenticated call must fail.                                                                                                             | Dev 2               |
| R13 | `ledger-service` has SQL logging on and a startup test runner in main sources.                                                                                  | `application.properties` (`show-sql=true`), `PostgresTestRunner.java`                  | Turn off; remove.                                                                                                                                      | Dev 3               |
| R14 | One Docker network holds everything; both SQL folders are mounted whole.                                                                                        | `docker-compose.yml:19,44`                                                             | The network split is deferred to a later phase; development is local on one network. The cloud PostgreSQL needs its own init folder, or audit scripts run in it. See H13.                                          | Dev 8, Dev 5        |
| R15 | CI names the six services in two places and builds each in its own step.                                                                                        | `ci.yml:47`, `ci.yml:384-435`                                                          | Both lists need the five new services.                                                                                                                 | Dev 8               |
| R16 | The demo script registers a customer, then falls back to a user called`customer1` that is not seeded. Test scripts log in as `john_doe`.                        | `scripts/demo_golden_path.ps1:123-126`                                                 | Registration is going away; the script and the seeded user names must agree.                                                                           | Dev 8, Dev 5        |

---

## 2. Handshakes: who must agree with whom

A handshake is one agreed interface between developers. The **provider** writes it into `contracts/` and posts in the team channel when it merges; **consumers** build against it. Nobody changes a handshake without telling every consumer listed. Telling is required; waiting for agreement is not.

| ID  | What is agreed                                                                                                                                                                                                  | Provider                            | Consumers                                                                             | Agreed by              |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------- | ---------------------- |
| H1  | **Token claims.** `userId` = customer ID for customer tokens, staff user ID for staff tokens. `roles`, `userType`, signing key and algorithm unchanged. No service edits its JWT classes.                       | Dev 2                               | Dev 1, 3, 4, 5, 6                                                                     | Day 1                  |
| H2  | **Seed IDs.** One table of customer IDs, staff IDs, account IDs and numbers, GL account IDs, user names and passwords. `john_doe` stays customer 1.                                                             | Dev 5                               | Everyone                                                                              | Day 2                  |
| H3  | **Ledger internal API.** Open balance, place hold, capture, release, reverse, batch posting, read balance. Each call carries an idempotency key built as `<transactionId>:<step>`.                              | Dev 3                               | Dev 1 (saga), Dev 2 (account open and closure), Dev 4 (mock, reversal), Dev 5 (batch) | Day 2                  |
| H4  | **`PostingEngine` extraction.** One pull request moves lock, post, audit and event logic out of `AccountBalanceServiceImpl`. Until it merges, nobody else touches the ledger's `service/` or `entity/` folders. | Dev 3                               | Dev 4                                                                                 | Merged by end of Day 3 |
| H5  | **Risk API.** Request: customer ID, amount, risk score, KYC tier, channel. Response: decision, score, reasons. Thresholds and the 0.60–0.85 band.                                                               | Dev 4                               | Dev 1                                                                                 | Day 2                  |
| H6  | **Risk profile lookup.** Internal endpoint in `customer-service` returning risk score and KYC tier for a customer.                                                                                              | Dev 2                               | Dev 1                                                                                 | Day 2                  |
| H7  | **Stage event and timeline.** Fields of `transaction.stage.changed.v1` and the timeline response. Stage names spelled once.                                                                                     | Dev 1 (event), Dev 3 (timeline API) | Dev 3, Dev 6                                                                          | Day 2                  |
| H8  | **Account lookup.** Internal endpoints in `account-service` returning owner customer ID, status and type for an account, by ID and by account number.                                                           | Dev 2                               | Dev 3 (ownership check), Dev 1                                                        | Day 2                  |
| H9  | **Customer 360 payload.**                                                                                                                                                                                       | Dev 2                               | Dev 7                                                                                 | Day 2                  |
| H10 | **Transaction history and category.** Recent transactions per customer; the `category` values.                                                                                                                  | Dev 3                               | Dev 2 (360), Dev 7 (chart)                                                            | Day 2                  |
| H11 | **Batch API.** Trigger, run status, six step names, `batch.eod.step.completed.v1`.                                                                                                                              | Dev 5                               | Dev 7 (EOD deck), Dev 8 (route)                                                       | Day 3                  |
| H12 | **Database settings.** Database names, users, environment variable names, init folder layout. Written in `contracts/runtime/RUNTIME_SETTINGS.md`.                                                                                                                   | Dev 5                               | Dev 8, all backend owners                                                             | Day 2                  |
| H13 | **Runtime settings.** Ports, service URL variable names, network names, Compose profiles. Written in `contracts/runtime/RUNTIME_SETTINGS.md`.                                                                                                                       | Dev 8                               | Everyone                                                                              | Day 2                  |
| H14 | **Gateway routes and CORS.** New paths, the Flutter web origin, the staff-only ledger route.                                                                                                                    | Dev 8                               | Dev 1, 2, 3, 5, 6                                                                     | Day 2                  |
| H15 | **Fixtures.** Dev 1 exports contract examples; Dev 7 curates them.                                                                                                                                              | Dev 1 → Dev 7                       | Dev 6, Dev 7                                                                          | Day 2                  |
| H16 | **Inter-bank payload and settlement account.**                                                                                                                                                                  | Dev 4                               | Dev 3 (host service), Dev 5 (GL account), Dev 1                                       | Day 5                  |
| H17 | **Topics.** Names of new topics and `.DLT` twins.                                                                                                                                                               | Dev 1, Dev 3                        | Dev 8 (creates them)                                                                  | Day 3                  |
| H18 | **Flutter core change requests.** After the Day 2 freeze, Dev 7 asks Dev 6 for any route, package or shared widget.                                                                                             | Dev 6                               | Dev 7                                                                                 | Ongoing                |
| H19 | **Error format.** The existing problem shape with `errorCode`.                                                                                                                                                  | Dev 1                               | Everyone                                                                              | Day 1                  |

**How to communicate a handshake**

1. One channel thread per handshake, titled with its ID.
2. The provider posts the contract file path when it merges.
3. A change notice states: field, old value, new value, reason, whether it is compatible, and every known consumer. It does not need approval before merge. The change itself must update `contracts/` and, where it can, the consumer-side code, tests and fixtures it affects, so no known consumer is left broken.
4. Daily 15-minute sync: each developer names the handshakes they are waiting on.

---

## 3. Merge order that prevents collisions

| Order | Pull request                                                                                       | Owner | Why it goes first                                    |
| ----- | -------------------------------------------------------------------------------------------------- | ----- | ---------------------------------------------------- |
| 1     | Scaffold five services, add cloud PostgreSQL, CI lists, gateway route placeholders, runtime variables | Dev 8 | Everyone else builds inside the folders this creates |
| 2     | Rebuilt Oracle schema, cloud databases, seed source                                                | Dev 5 | Entities are written against it                      |
| 3     | `contracts-v1` tag                                                                                 | Dev 1 | All DTOs follow it                                   |
| 4     | Flutter core                                                                                       | Dev 6 | Dev 7 builds on it                                   |
| 5     | `PostingEngine` extraction in the ledger (H4)                                                      | Dev 3 | Unblocks holds and reversals without a shared file   |
| 6     | Everything else, inside each owner's folders                                                       | All   | No shared files remain                               |

Pull requests 1 and 2 land on Day 2 before noon. Everyone rebases once after each.

### Files more than one developer needs

| File                                                                   | Who needs it                         | Rule                                                                                               |
| ---------------------------------------------------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------- |
| `ledger-service/.../AccountBalanceServiceImpl.java`                    | Dev 3, Dev 4                         | Only Dev 3 edits it. Dev 4 calls`PostingEngine`.                                                   |
| `ledger-service/.../entity/Transaction.java`                           | Dev 3, Dev 4                         | Dev 3 adds`reversesTransactionId` in the H4 pull request, so Dev 4 never opens the file.           |
| `transaction-service/.../security/config/SecurityConfig.java`          | Dev 3, Dev 4                         | Dev 4 requests one rule for`/api/v1/interbank/**`; Dev 3 adds it.                                  |
| `database/oracle/02_create_tables.sql`                                 | Dev 5, and requests from Dev 2, 3, 4 | Only Dev 5 edits. Requests list table, column, type.                                               |
| `backend/<service>/src/main/resources/application.properties`          | Service owner, Dev 8                 | The owner edits. Dev 8 supplies variable names (H13) and never edits the file.                     |
| `docker-compose.yml`, gateway `application.yml`, `azure-pipelines.yml` | Dev 8, requests from all             | Only Dev 8 edits.                                                                                  |
| `app/pubspec.yaml`, `app/lib/core/**`                                  | Dev 6, requests from Dev 7           | Only Dev 6 edits.                                                                                  |
| `postman/*.json` (five existing collections)                           | Dev 1, all owners                    | Dev 1 owns the existing five and`postman/e2e/`. Owners add new folders under `postman/<service>/`. |
| `scripts/*.ps1`, `scripts/*.sh`                                        | Dev 8, Dev 1                         | Dev 8 edits. Dev 1 says what the demo script must do.                                              |

---

## 4. Work packages

### Dev 1: Lead, contracts, `orchestration-service`

**ADD**

- `contracts/openapi/<service>.yaml` for all eleven services; `contracts/asyncapi/events.yaml`; `contracts/examples/*.json`.
- `backend/orchestration-service/`:
  - `saga/TransferSaga.java`, `saga/SagaState.java`, `saga/SagaInstance.java` (entity, schema `saga`), `saga/SagaRepository.java`
  - `client/RiskClient.java`, `client/LedgerClient.java`, `client/CustomerClient.java`, each with a timeout
  - `event/StageEventPublisher.java`, `event/StageChangedEvent.java`
  - `controller/SagaInternalController.java` (`POST /internal/v1/sagas/transfers`)
  - `security/` copied unchanged from `account-service` (H1)
- `postman/e2e/` collection; E2E checklist in `docs/`.

**CHANGE**

- The five existing Postman collections: new transfer path, removal of registration, new seeded names (H2).
- `README.md`: architecture, ports, run instructions.

**Tests:** state machine transitions; compensation on failed capture; risk timeout fails closed.

**Waits on:** H3 (Dev 3), H5 (Dev 4), H6 (Dev 2). Until they are live, runs against Dev 4's mocks.
**Must tell:** Dev 3 and Dev 6 of any change to stage names (H7); everyone of any contract change.
**Reviews:** every pull request that touches `contracts/` or crosses a service boundary.

---

### Dev 2: Identity, customer, account

**`auth-service`**

- **CHANGE** `pom.xml`: PostgreSQL driver in place of the Oracle driver. No Flyway: tables come from `database/postgres-cloud/`.
- **CHANGE** `application.properties`: datasource to `identity_db`; PostgreSQL dialect.
- **CHANGE** `entity/User.java` → `AppUser` on table `app_user` with `userType`, `customerId`; `entity/Role.java` on table `role`.
- **CHANGE** `service/impl/CustomerAuthServiceImpl.java`: login through `AppUserRepository` filtered by `userType = CUSTOMER`; put `customerId` in the `userId` claim (H1); drop the KYC lookup at line 174.
- **CHANGE** `service/impl/StaffAuthServiceImpl.java`: filter by `userType = STAFF`.
- **CHANGE** `dto/UserProfileDto.java`: remove `firstName`, `lastName`, `mobileNumber`, `kycStatus`.
- **REMOVE** `register` in `CustomerAuthServiceImpl` and its mapping in `CustomerAuthController`; `dto/CustomerRegistrationDto.java`.
- **REMOVE** `entity/Customer.java`, `entity/Kyc.java`, `repository/CustomerRepository.java`, `repository/KycRepository.java`.
- **Tests to update:** `AuthRepositoryIntegrationTest`, `AuthControllerIntegrationTest`, `AuthBusinessServiceIntegrationTest`. `CrossServiceTokenCompatibilityTest` must pass **unchanged**; it is the proof of H1.

**`customer-service` (new)**

- **ADD** `entity/ChannelProfile.java`, `entity/Beneficiary.java` and repositories.
- **ADD** `controller/CustomerController.java`: `/api/v1/customers/me`, `/{id}`, `/{id}/360`, beneficiaries.
- **ADD** `controller/CustomerInternalController.java`: `/internal/v1/customers/{id}/risk-profile` (H6).
- **ADD** `client/AccountClient.java` (accounts and KYC master), `client/TransactionClient.java` (history, H10).
- **ADD** `service/Customer360Service.java`. It never stores KYC fields; a Redis cache of a few minutes at most.

**`account-service`**

- **ADD** `controller/InternalAccountController.java`: `/internal/v1/accounts/{id}` (H8), `/internal/v1/customers/{id}/kyc`.
- **ADD** `client/LedgerClient.java`: read balance, open balance (H3).
- **CHANGE** `service/impl/AccountServiceImpl.java`: line 69 calls the ledger to open the balance; lines 98–143 read balances through the client.
- **CHANGE** `service/impl/AccountClosureServiceImpl.java`: the zero-balance check at lines 51–61 calls the ledger.
- **CHANGE** `entity/AccountFlag.java`, `AccountClosureRequest.java`, `KycUpdateRequest.java`: `flaggedBy`, `removedBy`, `approvedBy` become `Long`.
- **CHANGE** `entity/Customer.java`: drop `username`, `passwordHash`, `email`.
- **CHANGE** `entity/Account.java`: accept `CURRENT`, `LOAN`, `GL` types.
- **CHANGE** `controller/AccountController.java:63`: remove the fallback customer ID (R12).
- **CHANGE** `controller/CustomerController.java`: remove `GET /me` and `GET /{customerId}` (now in `customer-service`). The five `/api/v1/customers/kyc/**` endpoints stay as they are; the gateway routes that more specific path here.
- **CHANGE** `application.properties`: remove the fallback JWT secret (R8).
- **REMOVE** `entity/Balance.java`, `BalanceRepository`, `entity/User.java`, `entity/Role.java`, `UserRepository`, `RoleRepository`.
- **Tests to update:** `AccountServiceTest`, `AccountClosureServiceTest`, `AccountFlagServiceTest`, `CustomerServiceTest`, both controller tests.

**Waits on:** H2 and the `identity_db` init script (Dev 5); H3 (Dev 3); H10 (Dev 3).
**Must tell:** Dev 1, 3, 4, 5, 6 if any token claim changes (H1); Dev 7 of the 360 payload (H9); Dev 8 when the gateway customer route can flip.
**Order inside this package:** `auth-service` first, since nobody can log in until it works.

---

### Dev 3: Transactions, ledger, events

**`ledger-service`, pull request H4 first**

- **ADD** `posting/PostingEngine.java`: ordered lock, double-entry write, audit dual-write, event. Moved out of `AccountBalanceServiceImpl`, behaviour unchanged.
- **CHANGE** `service/impl/AccountBalanceServiceImpl.java`: delegates to `PostingEngine`; lines 105, 178, 290 pass a `Long` actor ID.
- **CHANGE** `entity/Account.java`: `Customer customer` → `Long customerId`.
- **CHANGE** `entity/Transaction.java`, `entity/TransferRequest.java`: `User approvedBy` → `Long approvedBy`; `Transaction` gains `reversesTransactionId`.
- **CHANGE** `entity/Balance.java`: add `ledgerBalance`.
- **REMOVE** `entity/Customer.java`, `Kyc.java`, `KycUpdateRequest.java`, `User.java`, `Role.java`, `AccountClosureRequest.java`, `AccountFlag.java`, `SystemLog.java`, `TransactionFlag.java`; check `TransactionAudit.java` for use before removing.
- **REMOVE** `PostgresTestRunner.java`; set `spring.jpa.show-sql=false` (R13).
- **Tests:** the 25 existing ledger tests must pass after the extraction, before anything new is added.

**`ledger-service`, after H4**

- **ADD** `hold/Hold.java`, `HoldRepository.java`, `HoldService.java`, `HoldController.java` (`/internal/v1/ledger/holds`, `/{id}/capture`, `/{id}/release`).
- **ADD** `controller/LedgerInternalController.java`: open balance, batch posting (H3).
- **ADD** `outbox/OutboxEvent.java`, `OutboxRepository.java`, `OutboxRelay.java` (Day 6).
- **CHANGE** `event/publisher/LedgerEventPublisher.java`: write an outbox row inside the transaction in place of the after-commit send at line 46.
- **CHANGE** `security/config/SecurityConfig.java`: `/api/v1/ledger/**` staff roles only (R2); `/internal/**` for service callers.
- **KEEP** `LedgerTransferController`, `LedgerMutationController`: the React staff console uses them.

**`transaction-service` (new)**

- **ADD** `controller/TransferController.java` (`POST /api/v1/transactions/transfers`), `TimelineController.java`, `HistoryController.java` (H10), `DlqReplayController.java` (admin).
- **ADD** `service/TransferIngressService.java`: validation, ownership through `AccountClient` (H8), idempotency key in Redis, hand-off to the saga.
- **ADD** `entity/TxnTransaction.java`, `TxnStageEvent.java` and repositories.
- **ADD** `listener/StageEventListener.java`: consumes `transaction.stage.changed.v1`.
- **ADD** `client/OrchestrationClient.java`, `client/AccountClient.java`.
- **ADD** one line in `security/config/SecurityConfig.java` for Dev 4's inter-bank path, on request.

**`notification-service`**

- **CHANGE** `application.properties`: datasource to `notification_db`; `ddl-auto=validate`; remove the H2 default and the fallback JWT secret.
- **CHANGE** `config/KafkaConsumerConfig.java:72-80`: `DeadLetterPublishingRecoverer` with exponential backoff, three attempts.
- **ADD** producer configuration and `event/NotificationDispatchedEvent.java`; publish after a notification is stored.

**Waits on:** schema init scripts (Dev 5); H8 (Dev 2); H7 event shape (Dev 1); topics created (Dev 8).
**Must tell:** Dev 4 the moment H4 merges; Dev 1, 2, 4, 5 of any change to H3; Dev 8 the list of `.DLT` topics (H17).
**Heaviest file risk in the project:** the H4 extraction. Do it alone, early, with no feature mixed in.

---

### Dev 4: Risk, core mock, reversals, inter-bank

**`risk-engine` (new)**

- **ADD** `controller/RiskController.java` (`POST /internal/v1/risk/evaluate`), `service/RiskEvaluator.java`, `service/LimitPolicy.java`, `service/VelocityCounter.java` (Redis), DTOs per H5.
- No database. No call to another service on the request path.
- **Tests:** each threshold boundary; the 0.60–0.85 band; a recorded p95 latency.

**`core-mock` (new)**

- **ADD** a small service or stub mappings returning the H3 contract examples for hold, capture, release, reverse. Switched on by configuration in the orchestrator.

**Inside `ledger-service`, package `reversal/` only, after H4**

- **ADD** `reversal/ReversalController.java` (`POST /internal/v1/ledger/reversals`), `ReversalService.java`, DTOs.
- Calls `PostingEngine`. Does not open `AccountBalanceServiceImpl` or any entity file.

**Inside `transaction-service`, package `interbank/` only**

- **ADD** `interbank/InterbankController.java` (`POST /api/v1/interbank/transfers`), `InterbankService.java`, `InterbankClient.java`, `InterbankAuthFilter.java` (shared secret or signature).
- Inbound transfers enter the same ingress path with `channel = INTERBANK`.

**Waits on:** H4 merged (Dev 3) before any reversal code; the settlement GL account (Dev 5, H16); the security rule in `transaction-service` (Dev 3).
**Must tell:** Dev 1 of any change to the risk response (H5); Dev 3 when a pull request touches their services, since they review it.
**Until H4 merges:** build `risk-engine` and `core-mock`, which touch nobody else's code.

---

### Dev 5: Database, batch, audit

**`database/`**

- **CHANGE** `oracle/02_create_tables.sql`: rebuilt. No `ROLE`, no `USER`; `CUSTOMER` without credentials; `approved_by`, `flagged_by`, `removed_by` as plain numbers; `BALANCE.ledger_balance`; `TRANSACTION.reverses_transaction_id`; new `HOLD`, `OUTBOX`.
- **CHANGE** `oracle/03_seed_data.sql`: generated from the seed source.
- **ADD** `seed/seed.yaml` and a generator that writes every seed file and the H2 ID table.
- **ADD** `postgres-cloud/01_create_databases.sql`: `identity_db`, `customer_db`, `transaction_db`, `notification_db`, one user each. A **separate folder**, because the audit container mounts all of `postgres/` (R14).
- **ADD** `postgres/06_batch_schema.sql`: `batch_run`, `batch_step`, `standing_instruction`, `loan_schedule`; `postgres/07_daily_audit_seal.sql`.
- **REMOVE** `postgres/05_create_notifications.sql`.

**No migration files in services**

- Tables for every service are created by the init scripts under `database/`. Services carry no `db/migration/` folder and run with `ddl-auto=validate`. The service owner reviews the script for their database and writes entities to match.

**`batch-service` (new, on-premises)**

- **ADD** `controller/BatchController.java` (`POST /api/v1/batch/eod/trigger`, `GET /api/v1/batch/eod/runs/{id}`), `service/EodPipeline.java`, one class per step under `step/`, `client/LedgerClient.java`, entities for the `batch` schema.
- Publishes `batch.eod.step.completed.v1` through the ledger outbox table or its own producer; agree which with Dev 3.

**`audit-service`**

- **ADD** `GET /api/v1/audit/seals/{date}` to read the daily seal.
- **CHANGE** `application.properties`: remove the fallback JWT secret.

**Waits on:** H3 batch posting endpoint (Dev 3); loan account type (Dev 2); the entity field lists from each owner.
**Must tell:** everyone when a schema change needs a reset; Dev 8 of every database name, user and folder (H12); Dev 8 that CI jobs 2 and 4 reference the SQL files being changed.
**First deliverable:** the H2 ID table. Until it exists, nobody can write a test or a fixture.

---

### Dev 6: Flutter foundation

**ADD** (all under `app/`)

- `pubspec.yaml`, platform folders.
- `lib/core/api/api_client.dart` (base URL from a build setting, bearer token, problem-format mapping), `fixture_client.dart`.
- `lib/core/auth/` (login, token storage, refresh), `lib/core/routing/`, `lib/core/theme/`, `lib/core/models/` from the contract examples.
- `lib/features/monitoring/`: 11-stage stepper, 3-stage customer view, polling controller.
- Widget tests for core and monitoring.

**Reference, do not edit:** `frontend/src/services/api.ts` and `frontend/src/types/index.ts` show today's request and response shapes.

**Waits on:** H1 (login response), H7 (timeline), H15 (fixtures), H14 (CORS origin).
**Must tell:** Dev 8 the fixed Flutter web port on Day 1; Dev 7 when the core freezes at the end of Day 2.

---

### Dev 7: Flutter features

**ADD**

- `app/lib/features/customer360/`, `accounts/`, `transfer/`, `batch/`, `kyc/`: each with its own screens, controller and widget tests.
- `app/assets/fixtures/*.json`, curated from Dev 1's export.

**Never edits:** `app/lib/core/**`, `app/pubspec.yaml`. Requests go to Dev 6 (H18).

**Waits on:** Flutter core (Dev 6); H9 (Dev 2); H10 (Dev 3); H11 (Dev 5).
**Must tell:** Dev 6 of any shared widget needed; Dev 2, 3 or 5 when a payload does not match its fixture.

---

### Dev 8: DevOps and cloud

**CHANGE**

- `docker-compose.yml`: one network for now (the split into `cloud-net` and `onprem-net` is deferred to a later phase); `postgres-cloud-db` container mounting `database/postgres-cloud/`; five new service blocks; `auth-service` and `notification-service` moved to the cloud network with new datasource variables; new topics and `.DLT` twins in `kafka-init-topics`; profiles per slice with heap caps.
- `.env.example`: new database, URL and port variables (H13). The names are published in `contracts/runtime/RUNTIME_SETTINGS.md`.
- `backend/pom.xml`: five module lines.
- **ADD** `azure-pipelines.yml`: the six jobs of `.github/workflows/ci.yml` ported to Azure Pipelines, with the five new services in the service list (line 47 there) and the build steps (lines 384–435 there); Postman shape tests as a gate. The GitHub workflow stops running once the repository lives in Azure DevOps.
- `backend/api-gateway/src/main/resources/application.yml`: routes for customers, transactions, interbank, batch; ledger route kept and marked staff-only; Flutter origin in CORS.
- `monitoring/prometheus/prometheus.yml`: five scrape targets.
- `scripts/demo_golden_path.ps1` and the `test_*.ps1` scripts: no registration, new transfer path, seeded names from H2.

**ADD**

- Five `Dockerfile`s copied from `ledger-service/Dockerfile`.
- `infra/`: deployment files for the shared environment.
- Branch policies on `dev` and `main` in Azure Repos: build validation, at least one reviewer, and **automatically included reviewers by path**, one rule per row of the ownership matrix. Azure Repos does not read a `CODEOWNERS` file.
- `.azuredevops/pull_request_template.md`, moved from `.github/pull_request_template.md`.

**Never edits:** any service's `application.properties` or Java source.

**Waits on:** H12 (Dev 5); route and topic requests from owners; the web port (Dev 6).
**Must tell:** everyone the variable names and ports (H13) before noon on Day 2, and each time the shared environment is redeployed.

---

## 5. Corrections to the earlier plan documents

| Earlier statement                                    | Correction                                                    | Reason                                            |
| ---------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------- |
| The public ledger transfer route is retired on Day 7 | It stays, restricted to staff roles                           | The React staff console calls it (R2)             |
| `identity_db` gives each login its own user ID       | The`userId` claim keeps its current meaning                   | Four services read it as the customer ID (R1)     |
| `account-service` only stops reading balances        | It must also stop creating the balance row on account opening | The ledger owns`BALANCE` (R6)                     |
| The cloud PostgreSQL reuses`database/postgres/`      | It needs`database/postgres-cloud/`                            | The audit container mounts the whole folder (R14) |
