# 1. System overview

## 1.1 What CooBS is

CooBS (Cooperative Banking System) is a core retail banking platform built for a member-owned cooperative in the Philippines. It keeps member accounts and balances, moves money between them and to partner banks, screens every transfer for fraud and daily-limit breaches, keeps a tamper-evident audit trail of every balance change, and closes each business day with an end-of-day batch. All amounts are in Philippine pesos (PHP, shown as ₱).

It was built by an eight-person team (Group 2) as the final capstone of the FSE programme, against a frozen API contract (`contracts-v1`), and is deployed both locally (Docker Compose) and in Azure.

## 1.2 Who uses it

| User | Role in the system | Client | How they sign in |
| --- | --- | --- | --- |
| Member (customer) | `ROLE_CUSTOMER` | Flutter mobile app (Android; iOS and web targets exist in code) | `POST /api/v1/auth/customers/login`, or fingerprint after a first sign-in |
| Teller | `ROLE_TELLER` | React staff console | `POST /api/v1/auth/staff/login` |
| Administrator / branch manager | `ROLE_ADMIN` | React staff console | `POST /api/v1/auth/staff/login` |
| Another service | `ROLE_SERVICE` | Internal calls on `/internal/v1/**` | Short-lived service token signed with the internal secret |
| Partner bank | No user role; authenticated per partner | Inter-bank API `/api/v1/interbank/**` | HMAC-SHA256 signature or API key |

There is no self-service registration: members are created by the seed (and, in a real deployment, by the cooperative's onboarding process). Multi-factor authentication is not implemented; every sign-in returns `mfaRequired: false`.

## 1.3 What it does

### For members (mobile app)

- Sign in with password or fingerprint; one active session per member (a new sign-in ends the old one).
- Dashboard: profile, primary account, balance, unread notifications, recent transactions — one call.
- Send money (remittance) to another CooBS account or to a partner bank; see the transfer as a three-step timeline (Submitted → Security & Clearing → Completed).
- Beneficiaries: list, add, remove.
- In-app notifications for debits, credits, sent/received/failed transfers, account closure and month-end interest.
- **Ipon Vaults**: goal-based savings envelopes earmarked inside a deposit account.
- **Share capital**: view the cooperative membership certificate, top up shares, see dividend and patronage-refund history.
- KYC update request, QR payment codes, scores/deals, a demo shop and loan screens (several of these are client-side demonstrations; see section 11).

### For tellers and administrators (staff console)

- **Customer 360**: profile, KYC tier, risk and credit scores, every account with balances, recent transfers and spending by category.
- **Counter desk**: cash deposit, cash withdrawal, and (tellers only) assisted transfer on a member's behalf.
- **Queues**: approve or reject KYC update requests (teller and admin) and account closure requests (admin only).
- **Accounts**: open accounts (savings, checking, current, loan), freeze/activate, add flags (admin removes flags).
- **Transaction tracker**: the full 11-stage timeline of any transfer; reverse a posted transfer.
- **Audit**: per-account audit statement with hashes, hash-chain verification, daily seal.
- **End of day** (admin triggers, teller views): the six-step EOD pipeline and its run history.
- **Dead-letter queue** (admin only): inspect failed Kafka messages and replay them.

### For partner banks

- Send an inbound credit to a CooBS account; query its status; confirm an account and get a masked name.
- Receive outbound transfers from CooBS (to `BANKB` through a built-in loopback, or to `AURABANK` when its address is configured).

## 1.4 Key qualities the design guarantees

| Quality | How |
| --- | --- |
| Money is never created or lost | Double-entry postings; Oracle `CHECK` constraints (`available_balance >= 0`, `ledger_balance >= available_balance`); every posting through one engine with ordered row locks |
| A request sent twice takes effect once | `Idempotency-Key` on every money movement, checked in Redis and enforced by unique keys in the database; ledger calls keyed `<transactionId>:<step>` |
| Fails closed on doubt | Risk engine unreachable → transfer declined, no hold placed. Ledger silent → saga stays in flight and is retried, never guessed |
| Tamper evidence | Append-only audit table; PostgreSQL trigger chains each row with SHA-256; UPDATE/DELETE refused (`SQLSTATE 55000`); daily seal |
| Events survive outages | Ledger writes events to an `OUTBOX` table in the same Oracle transaction and relays them to Kafka |
| Sign-in survives a link outage | Auth, its database and Redis all live in the cloud zone |
| Traceability | `X-Correlation-ID` on every request, log line and event; stage events for every transfer |

## 1.5 Scope boundaries

In scope and built: everything in 1.3. Out of scope or not built: customer self-registration, MFA, SMS/e-mail notifications (in-app only), card processing, foreign currency, loan origination on the server (loan screens in the app are client-side), Azure Front Door/WAF, a production environment. Section 14 lists every gap.

## 1.6 Glossary

| Term | Meaning |
| --- | --- |
| Cloud zone | Services that run in Azure on AKS (locally: same Docker network) |
| On-premises zone | Services that sit with the system of record in the bank's datacenter; in Azure, a VM in a separate, firewalled network |
| System of record (SoR) | Oracle database holding accounts, balances, holds and the journal |
| Gateway | `api-gateway`, the only public entry for API calls |
| BFF | Backend-for-frontend: `mobile-bff` and `staff-bff` shape data for one client's screens |
| Saga | The multi-step run of one transfer across services with a defined undo for each step (`orchestration-service`) |
| Hold / capture / release | Reserve money for a transfer (available balance down) / post it (ledger balance down, recipient credited) / give it back |
| Outbox | Table the ledger writes events into inside the posting's own transaction, relayed to Kafka afterwards |
| Idempotency key | Client-generated value so a repeated request has the effect of one |
| Stage | One of the 11 named steps a transfer passes (`INITIATED` … `RECONCILIATION`) |
| DLT / DLQ | Dead-letter topic/queue: where a Kafka message goes after repeated failure |
| GL account | Internal general-ledger account (IDs 9001–9005) used as the other leg of cash, fee, interest, loan and settlement postings |
| KYC tier | `TIER_1_BASIC`, `TIER_2_STANDARD`, `TIER_3_FULL`; sets the default daily transfer limit |
| Seal | SHA-256 digest over the last audit row of a business day, written by the EOD batch |
| Ipon Vault | Savings-goal envelope inside a deposit account ("ipon" is Filipino for savings) |
| Share capital | A member's paid-up shares in the cooperative; determines voting rights and dividends |

## 1.7 Team and ownership

| Developer | Area |
| --- | --- |
| Carl (Dev 1, lead) | Contracts, `orchestration-service`, E2E suite, Azure deployment (Cloud and Overall Testing) |
| Hans (Dev 2) | Identity, `customer-service`, `account-service` (Backend, Authentication and Customer & Account Service) |
| Wayne (Dev 3) | `transaction-service`, `ledger-service` (PostingEngine), `notification-service` (Web Frontend and Transaction) |
| Ysa (Dev 4) | `risk-engine`, reversals, inter-bank (Risk Services) |
| April (Dev 5) | Databases and seed, `batch-service`, `audit-service` (Backend and Batch Services) |
| Neo (Dev 6) | Flutter foundation (`app/lib/core`) (Mobile Backend) |
| Gabo (Dev 7) | Flutter features (Mobile Frontend) |
| Jared (Dev 8) | DevOps, CI, cloud (Cloud and CI/CD Pipeline) |

## 1.8 Technology summary

| Layer | Technology |
| --- | --- |
| Backend | Java 21; Spring Boot 4.1.1 (Spring MVC) for 12 services; Spring Boot 3.3.4 + Spring Cloud Gateway (WebFlux) for `api-gateway`; Maven multi-module (`backend/pom.xml`) |
| Staff console | React 19, TypeScript 6, Vite 8, Tailwind CSS 3, React Router 6, lucide-react; served by nginx |
| Mobile app | Flutter 3.44, Dart 3.12; `http`, `flutter_secure_storage`, `local_auth`, `qr_flutter`, `mobile_scanner`, `geolocator` |
| System of record | Oracle Database Free 23ai (`gvenzl/oracle-free`) |
| Audit store and batch schema | PostgreSQL 16 with `pgcrypto` |
| Cloud databases | PostgreSQL 16 locally; Azure SQL Database (4 × S0) in Azure |
| Cache, locks, counters | Redis 7.4 locally; Azure Managed Redis in Azure |
| Messaging | Apache Kafka (KRaft, single broker) |
| Infrastructure | Docker Compose; AKS + Ubuntu VM; Bicep; kustomize; Caddy (TLS) |
| Observability | Prometheus, Grafana, Loki, Promtail, Jaeger locally; Datadog in Azure |
| Testing | JUnit/Spring Boot Test, Postman/Newman, PowerShell scenario scripts, JMeter, `flutter test` |
| CI | Azure Pipelines (`azure-pipelines.yml`, primary) and GitHub Actions (`.github/workflows/ci.yml`) |
