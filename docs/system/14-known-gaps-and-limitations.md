# 14. Known gaps and limitations

This section lists what is incomplete, risky or out of date, so that nobody relies on behaviour the system does not have. Items marked **(code)** were found while reading the code for this documentation set; the others are from the team's own gap register (`integrated-capstone-plans/ENTERPRISE_ARCHITECTURE_AND_DEPLOYMENT.md` §8, IDs G1–G36).

Severity is the consequence if the system were used beyond a demonstration.

## 14.1 Correctness findings (code)

| # | Finding | Where | Consequence | Recommended fix | Severity |
| --- | --- | --- | --- | --- | --- |
| C1 | **Vaults and share capital change `BALANCE` outside the ledger** with an unlocked read-modify-write (no `SELECT … FOR UPDATE`, no `@Version` on `Balance`) | `account-service` `VaultServiceImpl.allocateInternal/releaseFunds/closeVault`, `ShareCapitalServiceImpl.topUpShareCapital` | A vault allocation running at the same moment as a ledger hold or posting on the same account can overwrite the ledger's update (lost update). These movements also leave no journal row, no audit row and no idempotency key, and are invisible to GL reconciliation and the hash chain | Route them through the ledger: vault allocate/release as an L3-style hold/release keyed by vault reference; share-capital top-up as an L8 posting to a share-capital GL account | High |
| C2 | Share-capital top-up lowers `available_balance` only; `ledger_balance` is unchanged | `ShareCapitalServiceImpl` | The money is permanently "held" rather than moved; balances no longer reconcile with the journal | Same as C1 | High |
| C3 | A member's share-capital record is created on first read with demonstration values (250 shares, ₱25,000 paid, two years of dividends) and no money moved | `ShareCapitalServiceImpl.provisionInitialShareCapital` | Displays capital and dividends the member never paid or received | Seed real records; return 404/empty for members without one | Medium |
| C4 | Daily interest references include the batch run ID (`EOD-YYYYMMDD-B{run}-INT-…`) | `batch-service` `InterestAccrualStep` | Two EOD runs on the same business date pay daily interest twice (month-end references are protected) | Drop the run ID from the daily reference | Medium |
| C5 | No service publishes `kyc.request.submitted.v1` / `kyc.request.evaluated.v1` since KYC moved to `customer-service` | `customer-service` (no Kafka producer) | "KYC Update Submitted/Approved/Rejected" notifications never occur | Add a producer in `customer-service` on submit and on approve/reject | Medium |
| C6 | Counter cash postings are single-leg (no `GL_CASH_VAULT` counter-entry) | `ledger-service` `/credits`, `/debits` | Teller cash is outside the double-entry check; EOD reconciliation reports it separately | Post counter cash as a two-leg transfer against 9001 | Medium |
| C7 | The mobile app's `DemoStore` swallows API errors (`catch (_) {}`) and continues with local values | `app/lib/features/accounts/demo_store.dart` | The app can show a vault, share-capital or beneficiary change the server rejected | Surface errors; reload from the server after each change | Medium |
| C8 | Audit rows commit before Oracle; a failure of the Oracle COMMIT itself leaves audit rows for a posting that did not happen | `DualWriteLedgerAuditService` (documented in code) | Rare false audit rows | Write audit rows after commit from an outbox | Low |
| C9 | Orchestrator recovery and batch run-halting assume one instance | `SagaRecoveryRunner`, `EodPipelineImpl` | Scaling to two replicas repeats steps (safe for the ledger, noisy) or halts a live EOD run | Row claims / leases before scaling | Low |

## 14.2 Functional gaps

| Gap | Notes |
| --- | --- |
| No self-registration, password reset or MFA | Endpoints and MFA settings in `.env` exist only as placeholders; `mfaRequired` is always `false` |
| No account lockout after failed sign-ins | Only the gateway's per-IP rate limit on `/api/v1/auth/**` |
| In-app notifications only | SMS and e-mail retired |
| Loans, scores/deals, shop and ads in the app are client-side | No loan origination or catalogue on the server; loan estimate uses a flat 12% p.a. |
| `account.created.v1`, `batch.eod.step.completed.v1` have no consumers | Published for future use |
| `AUDITOR` role accepted by audit endpoints but never issued | — |
| Opening an account cannot take an initial deposit | By design; fund it by counter deposit or transfer |
| One currency (PHP) | — |
| Partner banks | `BANKB` is served by the loopback; `AURABANK` adapter is configured but needs `INTERBANK_AURABANK_URL` |

## 14.3 Platform and deployment gaps (team register)

| ID | Gap | Severity |
| --- | --- | --- |
| G1 | No Front Door / WAF / DDoS edge; one Caddy pod in the path | High |
| G4, G19 | One Kafka broker, no replication, PLAINTEXT, no auth | High / Medium |
| G6, G11, G36 | One 2-vCPU node, one replica per service, ~80m CPU spare; any restart is an outage | High |
| G10 | One environment (`dev`) only | High |
| G12 | No backup of Oracle or the audit store on the VM | High |
| G16, G29 | Pipeline deploy stages are placeholders; images built and deployed from a workstation | High |
| G30 | Build validation on `dev` disabled | High |
| G2 | VNet peering instead of a VPN/ExpressRoute | Medium |
| G3 | Cloud services tested in CI on PostgreSQL, run on Azure SQL in Azure | Medium |
| G13 | Datadog on a trial; Application Insights created but unused | Medium |
| G15 | On-premises services not traced | Low |
| G31, G32 | No release branch or tag since `contracts-v1`; 126 remote branches | Medium / Low |
| G33 | Load test never run against Azure (and see 14.6) | Medium |

## 14.4 Security gaps (team register)

| ID | Gap | Severity |
| --- | --- | --- |
| G18 | No encryption between services or across the link (no mTLS) | High |
| G24 | One seeded password for every member, on a public address | High |
| G20 | No Kubernetes network policies | Medium |
| G21 | Token revocation checked only by auth, account and ledger | Medium |
| G22 | Refresh token survives sign-out (fingerprint sign-in depends on it) | Medium |
| G23 | One shared HMAC key signs user and service tokens | Medium |
| G25 | No dependency, image or secret scanning in CI | Medium |
| G26 | Secrets copied into a Kubernetes Secret by script, not mounted by CSI | Low |
| G27 | Android manifest allows cleartext HTTP (`usesCleartextTraffic="true"`) | Low |
| G28 | Console sign-in pre-fills `Password123!` | Low |

## 14.5 Security findings (code)

| # | Finding | Where | Fix |
| --- | --- | --- | --- |
| S1 | Committed default secret for partner `AURABANK`: `INTERBANK_AURABANK_API_KEY:AURABANK-SECURE-KEY-2026` | `transaction-service/src/main/resources/application.properties`, `.env`, `.env.example` | Remove the default; a partner without a secret should be refused |
| S2 | The inter-bank API-key profile accepts requests without `X-Timestamp`, so a captured request can be replayed (the `messageId` uniqueness stops a double credit, but not probing) | `InterbankAuthValidator` | Require the timestamp on every profile |
| S3 | Default internal secret `coobs-risk-internal-secret-2026` in code and properties; `X-Internal-Secret` grants `ROLE_SERVICE` on the risk engine | `risk-engine` `JwtAuthenticationFilter`, `application.properties` | Remove the default; prefer service JWTs only |
| S4 | Default Oracle password `CorePassword123!` for `batch-service` | `batch-service/src/main/resources/application.properties` | Remove the default, as the other services do |
| S5 | `.env` with development secrets (including `JWT_SECRET`) is committed | Repository root | Commit only `.env.example`; generate `.env` locally |

## 14.6 Documentation and test assets out of date

| Item | What is wrong |
| --- | --- |
| Root `README.md` | Describes 6 services (now 13), a customer registration endpoint, MFA, KYC Kafka notifications (C5), "`--profile app`" starting everything (the `expansion` profile is also needed), and customers calling `/api/v1/ledger/transfers` (now staff only) |
| `contracts/api/CURRENT_API_INVENTORY_AND_USAGE_GUIDE.md` | Pre-split inventory (customers under account-service, transfers in the ledger, `interbank-gateway`/`eod-batch-runner` service names) |
| `docs/TEAM_STATUS.md` | Snapshot of 4 October (services described as empty skeletons; 3 staff instead of 12) |
| `frontend/README.md` | Lists a `supervisor_bob` login that does not exist (seed has `teller_bob`, `admin`, managers) |
| `app/README.md` | Says the app targets iOS; the project's deployment scope is Android |
| `database/azure-sql/README.md` | Says no complete load has happened; Azure SQL was loaded and is in service |
| `CONTEXT_HANDOFF.md` | Refers to React 18 and a scaffold path on another machine |
| `test/jmeter-load-test.jmx` | Member tokens on staff-only ledger routes (see 13.6) |
| Older Postman collections and `scripts/test_api_endpoints.ps1` etc. | Pre-split paths; superseded by `postman/e2e` |
| `frontend/src/components/*.tsx` (top level) | Unrouted legacy single-page dashboard |
| `database/oracle/02_create_tables.sql` | Still creates copies of tables now owned by cloud services |

## 14.7 Recommended order of work

1. C1–C2 (vault and share-capital through the ledger) — the only finding that can corrupt balances.
2. S1–S5 (remove committed and defaulted secrets), G24, G30.
3. C4, C5, C6.
4. Update the root README to point at this documentation set; retire or update the outdated documents in 14.6; fix the JMeter plan.
5. Team roadmap: release branch and tag; pipeline delivery and scanning; backups; network policies and Kafka security; second environment; paid-tier platform (Front Door, more nodes, replicas, managed Kafka); mTLS and asymmetric keys.
