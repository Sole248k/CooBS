# Team Status: what exists, what changed, what each person does next

**As of:** 4 October 2026, from a read of the `dev` branch.

This is a snapshot, not the plan. Dates and ownership are in [`ZERO_CONFLICT_8_DEV_EXECUTION_PLAN.md`](ZERO_CONFLICT_8_DEV_EXECUTION_PLAN.md); file-level work is in [`PER_DEV_TECHNICAL_WORK_PACKAGES.md`](PER_DEV_TECHNICAL_WORK_PACKAGES.md).

---

## 1. Where we are

The foundations are in: all eleven services build, every database has its schema and seed, the contract is frozen, and the runtime names are published. No new feature code exists yet. The five new services are empty skeletons, and three things other people are waiting on have not landed (section 4).

## 2. What is ready to use

| Area | What exists | Where |
| --- | --- | --- |
| API contract | Frozen at tag `contracts-v1`. Every endpoint, payload, event, enumeration and error code | [`contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md`](../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md) |
| Contract examples | 61 JSON files, one per example, named by endpoint ID. Use them as fixtures, Postman bodies and test data | [`contracts/examples/`](../contracts/examples/) |
| Runtime settings | Ports, service URL variables, database names and connection variables, with the `application.properties` lines to copy | [`contracts/runtime/RUNTIME_SETTINGS.md`](../contracts/runtime/RUNTIME_SETTINGS.md) |
| Seed data | 50 members, 3 staff, 54 accounts, 5 GL accounts, the same IDs in every store. Password for every login is in the reference | [`database/seed/SEED_REFERENCE.md`](../database/seed/SEED_REFERENCE.md) |
| Cloud databases | `identity_db`, `customer_db`, `transaction_db` (schemas `txn`, `saga`), `notification_db`, with tables and seed | `database/postgres-cloud/` |
| Rebuilt on-premises schema | Oracle without `USER` and `ROLE`, with `HOLD` and `OUTBOX`; audit store plus the `batch` schema | `database/next/`, switched on with `docker-compose.target.yml` |
| Service skeletons | `customer-service`, `transaction-service`, `orchestration-service`, `risk-engine`, `batch-service`: security classes, health check, Dockerfile. No endpoints yet | `backend/<service>/` |
| Compose | All eleven services and five data stores. The new services already receive their database, Redis, Kafka and service-URL variables | `docker-compose.yml` |
| Kafka topics | The three new topics and a `.DLT` twin for every topic are created at start-up | `docker-compose.yml`, `kafka-init-topics` |
| Gateway routes | Transactions, inter-bank and batch routes are in place | `backend/api-gateway/src/main/resources/application.yml` |
| Pipeline | Validates Compose, loads the seeds, tests and builds all eleven services | `azure-pipelines.yml` |

## 3. What changed today that affects everyone

1. **The contract is frozen.** Build against `contracts-v1`. Field names, enumeration values and stage names are copied from the contract, not retyped.
2. **Handshake changes are announced, not voted on.** A change is written into `contracts/`, updates the consumers it affects, and is posted in the handshake thread. It does not wait for approval. A silent change is what is not allowed.
3. **Use the published variable names exactly.** They are already set in Compose. Read them in your service's `application.properties`; do not invent your own.
4. **No migration files.** No Flyway, no `db/migration/` folders. Tables come from the init scripts under `database/`. Write entities to match the script for your database and set `spring.jpa.hibernate.ddl-auto=validate`. If you need a column, ask April.
5. **Local first, one Docker network.** Splitting the cloud and on-premises networks is deferred to a later phase.
6. **Stage events:** `transaction-service` publishes stages 1 to 3; `orchestration-service` publishes 4 to 11. `risk-engine` and `ledger-service` publish no stage event.
7. **Risk band:** a score from 0.60 to 0.85 is `DECLINE` with `REVIEW_REQUIRED`.

## 4. The critical path: three things others are waiting on

| Not yet in the repository | Owner | Who is blocked |
| --- | --- | --- |
| `PostingEngine` extraction in `ledger-service` (handshake H4). `AccountBalanceServiceImpl` is still one 642-line class | Wayne | Ysa cannot start reversals. Holds cannot be added cleanly |
| Flutter core in `app/`. The folder does not exist | Neo | Gabo cannot build screens |
| `core-mock` | Ysa | Carl's saga has nothing to run against until the real risk and ledger endpoints exist |

## 5. What each person does next

In the order given. The first item for each person is the one that unblocks someone else.

### Carl: Dev 1 (lead, contracts, orchestration), and Dev 8 support

1. Scaffold the saga in `orchestration-service`: state machine, saga entity on `saga.saga_instance`, the risk, ledger and customer clients with timeouts, the stage event publisher, endpoints O1 to O4.
2. Update the five existing Postman collections: they still call registration and the old `/api/v1/ledger/transfers` path for customer transfers.
3. Create `postman/e2e/` for the Day 5 checkpoint. The pipeline has a placeholder step waiting for it.
4. Review pull requests that touch `contracts/` or cross a service boundary.
5. Later: OpenAPI and AsyncAPI files.

### Hans: Dev 2 (identity, customer, account)

1. Move `auth-service` onto `identity_db`. It still connects to Oracle. Keep the meaning of the `userId` claim (H1); remove registration.
2. `customer-service`: the risk-profile endpoint C7 (H6) first, because the saga calls it; then C1 to C3.
3. `account-service`: internal lookups K6 to K9 (H8), which Wayne needs for the ownership check.
4. Remove the fallback JWT secret from `account-service` (`application.properties`) and the fallback customer ID of `1` in `AccountController.java:63`.

### Wayne: Dev 3 (transactions, ledger, notifications)

1. The `PostingEngine` extraction (H4), alone, with no feature mixed in. Tell Ysa the moment it merges.
2. Ledger internal API (H3): open and read balance (L1, L2) for Hans, then hold, capture, release (L3 to L6) for Carl.
3. `transaction-service`: transfer ingress T1 with the Redis idempotency key, timeline T3, history T8 and T9.
4. `notification-service`: repoint from the audit PostgreSQL to `notification_db`, set `ddl-auto=validate` (it is `update` today), remove the H2 default and the fallback JWT secret.
5. Turn off `spring.jpa.show-sql` and remove `PostgresTestRunner.java` in `ledger-service`.

### Ysa: Dev 4 (risk, core mock, reversals, inter-bank)

1. `core-mock`: return the contract examples for hold, capture, release and reverse.
2. `risk-engine`: `POST /internal/v1/risk/evaluate` (H5) with the thresholds in section 9 of the contract and Redis velocity counters. No database.
3. Inter-bank DTOs and the auth filter inside `transaction-service/**/interbank/`.
4. Reversals inside `ledger-service/**/reversal/`, only after H4 merges.

### April: Dev 5 (database, batch, audit)

1. Agree with Wayne whether `batch.eod.step.completed.v1` goes through the ledger outbox or is published by `batch-service` (open decision 6). Until then `batch-service` has no Kafka variable.
2. `batch-service`: entities on the `batch` schema, the trigger and run-status endpoints (H11). Note that the `batch` schema exists only under `database/next/`, so the service needs the stack started with `docker-compose.target.yml`.
3. `audit-service`: the daily seal endpoint U4; remove the fallback JWT secret.
4. Answer column requests from service owners by changing the init scripts and the seed generator.

### Neo: Dev 6 (Flutter foundation)

1. Push the Flutter core to `app/`: routing, theme, API client with fixture mode, models built from `contracts/examples/`.
2. Tell Jared the fixed Flutter web port, so the origin can be added to gateway CORS (H14).
3. The 11-stage stepper and 3-stage customer view, from `T3-timeline-staff.response.json` and `T3-timeline-customer.response.json`.

### Gabo: Dev 7 (Flutter features)

1. Curate `contracts/examples/` into `app/assets/fixtures/` (H15). This can start now, before the core lands.
2. Customer 360, accounts, transfer, EOD deck and KYC screens on fixtures, once Neo's core is in.
3. Report any example that does not fit a screen to Carl, so the contract is corrected rather than the fixture.

### Jared: Dev 8 (DevOps and cloud)

1. Add the Flutter web origin to gateway CORS and to `CORS_ALLOWED_ORIGINS`, once Neo gives the port.
2. Update `scripts/demo_golden_path.ps1` and the `test_*.ps1` scripts: they still register a customer and fall back to `customer1`, which is not seeded.
3. Check whether the organisation has a hosted parallel job; then apply the branch policies in [`BRANCH_POLICY_PLAN.md`](BRANCH_POLICY_PLAN.md).
4. Move the pull request template to `.azuredevops/pull_request_template.md`.
5. When auth, account and ledger all run on the rebuilt schema, make `database/next/` the default and delete `docker-compose.target.yml`.
6. Settle the open decisions that are his: Kafka on AKS or Event Hubs, the Azure cost estimate, which imported branches to delete.

## 6. Starting the stack

```bash
docker compose up -d
docker compose --profile app --profile expansion up -d --build
```

The first command starts the data stores; wait for them to be healthy before the second. To run on the rebuilt Oracle and audit schema:

```bash
docker compose -f docker-compose.yml -f docker-compose.target.yml --profile app --profile expansion up -d --build
```

A reset wipes every volume and rebuilds from the seed:

```bash
docker compose --profile app --profile expansion --profile full down -v
```

## 7. Still open

Eight decisions are listed with owners in section 7 of [`ZERO_CONFLICT_8_DEV_EXECUTION_PLAN.md`](ZERO_CONFLICT_8_DEV_EXECUTION_PLAN.md).
