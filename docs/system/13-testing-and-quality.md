# 13. Testing and quality

## 13.1 Test levels

| Level | Tooling | Where | Runs in CI |
| --- | --- | --- | --- |
| Unit and slice tests | JUnit 5, Spring Boot Test, MockMvc, Mockito | `backend/<service>/src/test` | Yes (Azure Pipelines "Test") |
| Integration tests with real stores | Spring Boot Test against Oracle, PostgreSQL, Redis, Kafka containers started by the pipeline | Same | Yes, per selected service |
| Database integrity | SQL checks on the audit store and seed | `azure-pipelines.yml` "Infra", `.github/workflows/ci.yml` | Yes |
| End-to-end API | Postman / Newman, 12 folders, 143 checks | `postman/e2e/CooBS_End_To_End.postman_collection.json` | No (run by script, locally and against Azure) |
| Scenario scripts | PowerShell (some also Bash) | `scripts/test_*.ps1` | No |
| Release checks | `scripts/run_release_checks.ps1` | — | No |
| Load | Apache JMeter | `test/jmeter-load-test.jmx` | No |
| Clients | `npm run lint` + `npm run build`; `flutter test` | `frontend/`, `app/test/` | Yes (Azure Pipelines "Test → clients") |
| Planned test cases | CSV test plans | `integrated-capstone-plans/test-plans/` | — |

## 13.2 Backend unit and integration tests

Test classes per service (`*Test*.java` under `src/test`):

| Service | Classes | Service | Classes |
| --- | --- | --- | --- |
| ledger-service | 36 | customer-service | 7 |
| transaction-service | 17 | audit-service | 4 |
| account-service | 13 | batch-service | 6 |
| auth-service | 10 | notification-service | 6 |
| risk-engine | 6 | staff-bff | 4 |
| api-gateway | 5 | mobile-bff | 3 |
| orchestration-service | 3 | **Total** | **120** |

The ledger's suite is the deepest: posting engine, ordered locks, holds, capture/release, reversals, L8 idempotency, dual-write audit, outbox relay, idempotency interceptor, controllers and security. Run a service's tests with `cd backend/<service> && ./mvnw clean test`.

## 13.3 End-to-end suite

`postman/e2e/CooBS_End_To_End.postman_collection.json` with `postman/CooBS_Local.postman_environment.json` (variable `baseUrl`):

| # | Folder |
| --- | --- |
| 01 | System health and gateway ingress |
| 02 | Authentication and token harvest (member, teller, admin) |
| 03 | Customer 360 and beneficiary management |
| 04 | Account discovery and initial balances |
| 05 | Distributed transfer saga (transaction + orchestration) |
| 06 | Atomic double-entry ledger verification |
| 07 | Reversal saga and compensation |
| 08 | Real-time in-app notifications |
| 09 | Cryptographic audit chain and forensic integrity |
| 10 | Edge cases, risk guardrails and negative paths (incl. `/internal` refused) |
| 11 | End-of-day batch pipeline |
| 12 | Member risk rating and inter-bank outbound |

```bash
npx --yes newman run postman/e2e/CooBS_End_To_End.postman_collection.json \
  -e postman/CooBS_Local.postman_environment.json --delay-request 150
# against Azure: add --env-var baseUrl=https://coobs.indonesiacentral.cloudapp.azure.com
#                    --env-var staffPassword=<…> --env-var tellerPassword=<…>
```

Result: 143 of 143 on the local stack and on the Azure HTTPS address.

Older collections in `postman/` (`CooBS_Core_Banking`, `User_Lifecycle_Flow`, `CooBS_Edge_Cases_And_Compliance`, `CooBS_Kafka_Event_Driven_Verification`, `CooBS_Advanced_Validation`) predate the service split; some still call registration or customer transfers through `/api/v1/ledger/transfers` and are superseded by the E2E suite.

## 13.4 Scenario scripts (`scripts/`)

| Script | Proves |
| --- | --- |
| `demo_golden_path.ps1` / `.py` | Walk-through of the main flow for a demonstration |
| `demo_dev4_showcase.ps1` | Risk, reversal and inter-bank showcase |
| `test_daily_limit_reservation.ps1` | The daily limit is given back when a transfer does not post |
| `test_risk_scenarios.ps1 -Scenario Burst|EngineDown` | 5th transfer in 10 min declined; risk engine stopped → fail closed and recovery |
| `test_advanced_fraud_scenarios.ps1`, `test_high_risk_members.ps1` | Scoring rules, high-risk members |
| `test_outbound_interbank.ps1` | Outbound accepted, refused (account `0000…`), non-partner refused |
| `test_inbound_interbank.ps1` | Signed inbound credit posted once, duplicates answered from record |
| `test_idempotency.ps1` | Replays return the first answer; different body with same key refused |
| `test_dead_letter_replay.ps1` | Notification DB stopped → DLT → replay |
| `test_month_end_interest_notification.ps1` | Month-end EOD credits interest and notifies |
| `test_batch_report_and_recovery.ps1`, `test_kafka_eod.ps1` | EOD reporting, halted-run recovery, step events |
| `verify_audit_hash_chain.ps1` | Chain intact for every account |
| `test_correlation_propagation.ps1|.sh` | One `X-Correlation-ID` across services and events |
| `test_rate_limiting.ps1`, `test_gateway_health.ps1` | 429 behaviour, gateway health |
| `test_redis_velocity.ps1|.sh` | Velocity counters |
| `test_timeline.ps1` | Staff and customer timelines |
| `test_hybrid_360_and_blacklist.ps1` | Customer 360 across zones; revoked token refused |
| `test_observability_and_alerts.ps1|.sh` | Metrics exposed, alerts loaded |
| `test_swagger.ps1|.sh` | Swagger UI and OpenAPI reachable |
| `test_api_endpoints.ps1`, `test_user_lifecycle_flow.ps1`, `test_edge_cases_and_compliance.ps1`, `test_kafka_events.ps1` | Earlier suites (pre-split); see note above |

## 13.5 Release checks

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run_release_checks.ps1          # full
powershell -ExecutionPolicy Bypass -File scripts\run_release_checks.ps1 -Quick   # skips container-stopping checks
powershell -ExecutionPolicy Bypass -File scripts\run_release_checks.ps1 -List    # show plan only
```

Order: reset demo data → E2E suite → daily-limit reservation → outbound inter-bank → inbound inter-bank → risk burst → month-end interest notification → risk engine down (stops a container) → dead-letter replay (stops a database). It then prints the twelve demonstration scenarios of the service-scope document with the evidence for each and exits non-zero on any failure. Scenario 12 (concurrent transfers) is the JMeter plan.

## 13.6 Load test

`test/jmeter-load-test.jmx`, driven by `scripts/run_jmeter_test.ps1`:

- Virtual users sign in (`/api/v1/auth/customers/login`) and extract tokens.
- Traffic mix: 40% account inquiries (`/accounts/my-accounts`), 30% balance queries (`/ledger/accounts/1/balance`), 20% transfers (`/ledger/transfers`), 10% notification feeds.
- Transfers "ping-pong" ₱1.00 between accounts 1 and 2 so balances never run out.
- Gaussian think time 200–400 ms to stay inside the gateway rate limits.
- Assertions: 200/201/202 and response time < 3000 ms.

```powershell
.\scripts\run_jmeter_test.ps1                          # 5 users, 60 s
.\scripts\run_jmeter_test.ps1 -Threads 10 -Duration 120 -Report
jmeter -n -t test/jmeter-load-test.jmx -l test/results/results.jtl -e -o test/results/html-report -JTHREADS=5 -JDURATION=60 -JSLA_MS=3000
```

Not yet run against Azure (the node has 2 vCPU). **The plan predates the service split:** it signs in as a member and then calls `/api/v1/ledger/accounts/1/balance` and `/api/v1/ledger/transfers`, which now accept staff roles only, so those requests (50% of the mix) are refused with 403. Before using its numbers, move transfers to T1 (`/api/v1/transactions/transfers` or `/api/v1/mobile/transactions/remittance`) and balances to `/api/v1/mobile/dashboard` or `/api/v1/accounts/my-accounts`, or sign in as a teller for the ledger calls.

## 13.7 Continuous integration

### Azure Pipelines (`azure-pipelines.yml`, pipeline `CooBS-CI`) — primary

| Stage | When | Content |
| --- | --- | --- |
| 1 Validate | Always | `scripts/ci_scope.py` decides what the change touches; Compose validation; shell and PowerShell syntax; service layout; SQL files present; `az bicep build`; render both Kubernetes manifest sets |
| 2 Infra | Branch builds; PRs touching database, scripts or Compose | Audit store scripts apply, hash chain present, UPDATE/DELETE refused; seed generator output equals the committed files and loads; Redis lock and Kafka round trip |
| 3 Test | Always | `backend`: `mvnw clean test` per selected service with the stores it needs. `clients`: console `npm ci`, lint, build (Node 22); `flutter test` (Flutter 3.44.8) |
| 4 Package | `main`, `release/*`, `v*` tags | Build the 13 backend images; push when queued with "push images" (`coobs-acr`) |
| 5 Deploy | When queued with "run deployment stages" | Placeholders (`Deploy_Dev`, `Deploy_Prod`); deployment is done by the scripts in section 12 |

Triggers: pushes to `main`, `dev`, `release/*`, `v*` tags (batched); documentation-only or client-only pushes start no run. A `dev` run takes about 19 minutes. Build validation is required on `main`, present but disabled on `dev`.

### GitHub Actions (`.github/workflows/ci.yml`) — secondary

Jobs: (1) validate Compose, scripts and environment; (2) test the PostgreSQL cryptographic audit store; (3) test Redis idempotency and Kafka KRaft; (4) validate Oracle DDL; (5) build and test the ledger engine (Java 21); (6) quality gate. Runs on pushes to `main`, `master`, `dev`, `develop`, `feat/**`, `feature/**`, `fix/**`, `phase/**` and on PRs to `main`, `master`, `dev`.

### Branching and pull requests

`main` (presented), `dev` (integration), `feature/*`, `bugfix/*`, `release/<version>`, `hotfix/*`. PR templates in `.azuredevops/` and `.github/`. Plan: into `dev` one reviewer and build validation, squash; into `main` two reviewers including the lead. See `integrated-capstone-plans/BRANCH_POLICY_PLAN.md`.

## 13.8 Planned test cases

`integrated-capstone-plans/test-plans/` (generated by `generate.mjs`):

| Plan | Cases |
| --- | --- |
| 01 Functional | 244 |
| 02 Integration | 89 |
| 03 System / E2E | 47 |
| 04 Security | 61 |
| 05 Performance and resilience | 54 |
| 06 UAT (clients) | 40 |
| 07 Deployment smoke | 19 |
| **Total** | **554** |

Manual client testing: `TESTING_GUIDE.md`; checklist: `integrated-capstone-plans/E2E_CHECKLIST.md`.
