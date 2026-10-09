# Service Scope, Scenarios and Actions

**Version:** 1.0 draft
**Purpose:** for each service, say what it is responsible for, what it must not do, which actions each role can perform, and what happens in each scenario, including the failure cases.
**Paths and payloads:** [`../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md`](../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md). Endpoint numbers such as T1 or L3 refer to that file. If this document and the contract disagree, the contract wins.

---

## 0. Scope boundaries: what is real, seeded, simulated or out

Read this first. It says, for every capability, whether we actually build it. The sections after it describe behaviour only for what is in scope.

| Label         | Meaning                                                                                                    |
| ------------- | ---------------------------------------------------------------------------------------------------------- |
| **BUILT**     | Real code, real data changes, shown in the demo                                                            |
| **EXISTING**  | Already in the code today. Kept working, not extended, not a demo focus                                    |
| **SEEDED**    | The data is loaded by the seed scripts. There is no screen or process that creates it                      |
| **SIMULATED** | Looks real to the user but the behaviour is fixed or stubbed. Must be labelled as such in the presentation |
| **OPTIONAL**  | Only if time allows, after Checkpoint 1. The system works without it                                       |
| **OUT**       | Not built. Do not design for it, do not let an AI assistant add it                                         |

### 0.1 Across the whole system

| Capability                                        | Status                                   | What that means in practice                                                                                                                |
| ------------------------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Customer onboarding and self-registration         | **OUT**                                  | The register endpoint is removed. Every member exists because the seed created them                                                        |
| KYC verification process                          | **SEEDED**                               | Every member is seeded as`VERIFIED`, `TIER_3_FULL`. Nobody is ever "pending". There is no document upload, ID check, OCR or reviewer queue |
| KYC screen in the Flutter app                     | **SIMULATED**                            | A presentation-only screen showing the verified status and a mock upload. It calls no backend process                                      |
| KYC update requests (change of address and so on) | **EXISTING**                             | The React console can still submit and approve them. Not extended, not in the Flutter app, not in the demo script                          |
| MFA at sign-in                                    | **OUT** by default                       | `MFA_ENABLED=false`. The code path exists, but today the "delivery" only writes the code to the log                                        |
| MFA by email through Resend                       | **OPTIONAL**                             | See 0.3                                                                                                                                    |
| MFA by SMS                                        | **OUT**                                  | Would need a paid SMS provider and the mobile number, which stays on-premises                                                              |
| Password reset, password change, profile editing  | **OUT**                                  | Credentials are seeded                                                                                                                     |
| Risk score and credit score                       | **SEEDED**                               | Fixed per member, for example 0.15 and 750. Nothing calculates or updates them                                                             |
| Fraud and limit checks                            | **BUILT**                                | Simple, deterministic rules. No machine learning, no watch-lists, no real AML screening                                                    |
| Manual review of a flagged transfer               | **OUT**                                  | A score in the review band is declined. There is no review queue                                                                           |
| Transfers between our own accounts                | **BUILT**                                | The full saga with the eleven stages                                                                                                       |
| Holds: place, capture, release                    | **BUILT**                                |                                                                                                                                            |
| Automatic expiry of an old hold                   | **OPTIONAL**                             |                                                                                                                                            |
| Reversal of a posted transfer                     | **BUILT**                                | Full amount, by staff. No partial reversal and no dispute workflow                                                                         |
| Cash deposit and withdrawal at the counter        | **EXISTING**                             | React staff console only                                                                                                                   |
| Account opening, status change, flags, closure    | **EXISTING**                             | React staff console only. Opening gains loan and current types                                                                             |
| Loan accounts and repayment schedules             | **SEEDED**                               | No loan application, approval or disbursement                                                                                              |
| Standing instructions                             | **SEEDED**                               | No screen to create or edit one                                                                                                            |
| Interest and fees                                 | **BUILT**                                | One fixed rate and one fixed fee from configuration                                                                                        |
| End-of-day batch                                  | **BUILT**                                | On demand from a button. No scheduler, no calendar, no holidays                                                                            |
| GL accounts and chart of accounts                 | **SEEDED**                               | Five internal accounts. Reconciliation is debits equal credits only                                                                        |
| Inter-bank transfer, inbound and outbound         | **BUILT**                                | Against our own loopback. A real partner is**OPTIONAL** and depends on that group                                                          |
| Clearing house, real settlement of funds          | **OUT**                                  | Each bank only keeps a settlement total per partner                                                                                        |
| In-app notifications                              | **BUILT**                                |                                                                                                                                            |
| Email, SMS or push notifications                  | **OUT**                                  |                                                                                                                                            |
| Beneficiaries: list, add, remove                  | **BUILT**                                |                                                                                                                                            |
| Spending breakdown                                | **BUILT**                                | From the category the sender picks. No automatic categorisation                                                                            |
| Dead-letter queue and admin replay                | **BUILT**                                |                                                                                                                                            |
| Ledger outbox                                     | **BUILT**                                | First item to cut if Day 6 runs late                                                                                                       |
| Audit chain, verification, daily seal             | **BUILT**                                | Chain and verification are**EXISTING**; the seal is new                                                                                    |
| Currencies other than PHP                         | **OUT**                                  |                                                                                                                                            |
| Cards, ATM, QR payments, bill payment             | **OUT**                                  |                                                                                                                                            |
| Flutter on iOS                                    | **OUT**                                  | Web and Android only                                                                                                                       |
| Azure dev environment                             | **BUILT** if a subscription is confirmed | Otherwise the local stack is the demo                                                                                                      |
| VPN Gateway, Front Door, API Management           | **OUT**                                  | Shown in the target diagram only                                                                                                           |

### 0.2 Per service

| Service               | BUILT or changed                                                                               | EXISTING, kept as is                                        | SEEDED or SIMULATED                                                   | OUT                                                                  |
| --------------------- | ---------------------------------------------------------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------- |
| auth-service          | Move to`identity_db`; same login, refresh, revoke                                              | Token issue and blacklist                                   | Logins and roles seeded                                               | Registration, MFA by default, password reset                         |
| customer-service      | Profile, Customer 360, beneficiaries, KYC update submission & review, risk profile lookup      | —                                                           | Scores, tier, membership number, KYC seeded                           | External ID document scanning/OCR                                    |
| account-service       | Balance through the ledger; loan and current types; internal lookups                           | Open account, status, flags, closure requests               | Accounts and balances seeded                                          | Loan origination, KYC verification (handled in `customer-service`)    |
| transaction-service   | Transfer ingress, timeline, history, reversal request, inter-bank endpoints                    | —                                                           | —                                                                     | Scheduled or recurring transfers created by the customer             |
| orchestration-service | Transfer and reversal sagas, stage events, compensation                                        | —                                                           | —                                                                     | Manual intervention screens; sagas for anything other than transfers |
| risk-engine           | Score thresholds, daily limit, velocity counter                                                | —                                                           | Deterministic rules stand in for real fraud detection (**SIMULATED**) | Machine learning, AML lists, case management                         |
| ledger-service        | Holds, capture, release, reversal, batch posting, outbox, GL legs                              | Debit, credit, transfer, balance, locking, audit dual-write | GL accounts seeded                                                    | Multi-currency; interest calculation inside the ledger               |
| batch-service         | Six-step pipeline on demand; run and step log                                                  | —                                                           | Standing instructions and loan schedules seeded                       | Scheduler, re-run of a single step, report files                     |
| notification-service  | Move to`notification_db`; retry, dead-letter list and replay; dispatched event                 | In-app inbox, read flag, unread count                       | —                                                                     | Email, SMS, push, user preferences                                   |
| audit-service         | Daily seal read                                                                                | Statement, forensic record, chain verification              | —                                                                     | Any write; export to file                                            |
| api-gateway           | New routes; ledger route for staff only; Flutter origin                                        | Rate limits, correlation ID, CORS                           | —                                                                     | Token validation at the gateway; API Management                      |
| Flutter app           | Sign-in, Customer 360, accounts, transfer, tracker, beneficiaries, notifications, batch screen | —                                                           | KYC screen (**SIMULATED**)                                            | Registration, staff back-office screens, iOS                         |
| React console         | Nothing new                                                                                    | All staff screens                                           | —                                                                     | Frozen: no new features                                              |

### 0.3 Optional: MFA by email through Resend

Worth doing only after Checkpoint 1, and only if the team agrees on Day 1. Sign-in must keep working with it switched off.

| Item                  | Detail                                                                                                                                                                         |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| What exists           | The MFA flow, the`mfaToken`, the verify endpoints (A3, A5) and a delivery class. `OtpDeliveryServiceImpl.sendEmail` only logs the code today                                   |
| What to add           | A real send in that one method, calling Resend's email API with an API key from the environment                                                                                |
| Why email, not SMS    | The login email is in`identity_db` in the cloud. The mobile number is in `customer-service`, which `auth-service` does not read                                      |
| Configuration         | `MFA_ENABLED=true`, `MFA_DEFAULT_CHANNEL=EMAIL`, `EMAIL_PROVIDER=RESEND`, `RESEND_API_KEY`, a sender address. The key goes in `.env`, later Key Vault, never in the repository |
| Frontend              | Removed from Flutter mobile scope; sign-in flow proceeds directly with Bearer tokens |
| Owners                | Dev 2 (send), Dev 6 (screen), Dev 8 (secret and variables)                                                                                                                     |
| To confirm first      | Resend's free plan limits, and whether it needs a verified domain to send to addresses other than your own. Seeded members need inboxes the team can open                      |
| Fallback for the demo | If the flag is off, sign-in proceeds without MFA. Keep at least one seeded user with MFA off                                                                                   |
| Rule                  | A failed email must not lock anyone out during the presentation                                                                                                                |

---

## 1. Who can do what, at a glance

| Action                                         | Customer     | Teller         | Admin | Partner bank |
| ---------------------------------------------- | ------------ | -------------- | ----- | ------------ |
| Sign in, refresh, sign out                     | Yes          | Yes            | Yes   | —            |
| View own profile and Customer 360              | Yes          | —              | —     | —            |
| View any customer's profile and Customer 360   | —            | Yes            | Yes   | —            |
| Manage own beneficiaries                       | Yes          | —              | —     | —            |
| Submit a KYC update request                    | Yes          | —              | —     | —            |
| Approve or reject a KYC update                 | —            | Yes            | Yes   | —            |
| Open an account for a customer                 | —            | Yes            | Yes   | —            |
| View own accounts and balances                 | Yes          | —              | —     | —            |
| View all accounts                              | —            | Yes            | Yes   | —            |
| Freeze, unfreeze or change account status      | —            | Yes            | Yes   | —            |
| Flag an account                                | —            | Yes            | Yes   | —            |
| Remove a flag                                  | —            | —              | Yes   | —            |
| Request account closure                        | Yes          | —              | —     | —            |
| Approve or reject closure                      | —            | —              | Yes   | —            |
| Send a transfer (own bank or another bank)     | Yes          | Yes, on behalf | —     | —            |
| Track a transfer: 3 stages                     | Yes          | —              | —     | —            |
| Track a transfer: 11 stages                    | —            | Yes            | Yes   | —            |
| Cash deposit or withdrawal at the counter      | —            | Yes            | Yes   | —            |
| Reverse a posted transfer                      | —            | Yes            | Yes   | —            |
| Trigger end-of-day batch                       | —            | —              | Yes   | —            |
| View batch runs                                | —            | Yes            | Yes   | —            |
| View and replay dead-letter records            | —            | —              | Yes   | —            |
| View own notifications                         | Yes          | —              | —     | —            |
| View account statement and verify audit chain  | Own accounts | Yes            | Yes   | —            |
| Send us an inter-bank transfer, ask its status | —            | —              | —     | Yes          |

Registration is not available to anyone: members are seeded.

---

## 2. api-gateway (8080, cloud)

**Scope.** The only public entry. Routes each path to its service, applies rate limits and CORS, and adds a correlation ID.

**Not in scope.** No business rules, no token issuing, no data.

| Action                             | Result                                         |
| ---------------------------------- | ---------------------------------------------- |
| Route a request by path            | Forwarded to the owning service                |
| Rate-limit by user or IP           | HTTP 429 when the limit is passed              |
| Add`X-Correlation-ID` when missing | Same ID appears in every service log and event |

| Scenario                                   | What happens                                                                     |
| ------------------------------------------ | -------------------------------------------------------------------------------- |
| Request to`/api/v1/customers/**`           | Goes to`customer-service` (:8089)                                                |
| Request to`/internal/**` from outside      | Not routed; HTTP 404                                                             |
| Too many login attempts from one IP        | HTTP 429 from the auth limit (10 per second, burst 20)                           |
| A target service is down                   | HTTP 503 in the standard error shape                                             |

---

## 3. auth-service (8081, cloud)

**Scope.** Proves who the caller is. Checks username and password, issues and refreshes tokens, revokes tokens.

**Scope note.** MFA is **out of scope by default** (`MFA_ENABLED=false`); the MFA rows below apply only if the optional email MFA in section 0.3 is switched on. Registration is removed.

**Not in scope.** No customer profile, no KYC, no registration, no account data. Does not connect to Oracle.

**Data it owns.** `identity_db` (logins, roles); Redis (refresh tokens, revoked tokens).

| Action                 | Who           | Endpoint |
| ---------------------- | ------------- | -------- |
| Sign in                | Customer      | A2       |
| Sign in                | Teller, Admin | A4       |
| Complete MFA           | All           | A3, A5   |
| Refresh access token   | All           | A6       |
| Sign out (revoke)      | All           | A7       |
| Read own login profile | All           | A8       |

| Scenario                                       | What happens                                                                                          |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Correct credentials, MFA off                   | Tokens returned;`userId` is the customer ID for a customer, the staff ID for staff                    |
| Correct credentials, MFA on                    | No tokens yet; an`mfaToken` is returned and a code is sent                                            |
| Wrong MFA code                                 | Rejected; the`mfaToken` stays valid until it expires                                                  |
| Wrong password                                 | HTTP 401; nothing reveals whether the username exists                                                 |
| Inactive login                                 | HTTP 403                                                                                              |
| Access token expired                           | Client calls refresh and gets a new access token                                                      |
| Sign out                                       | Token ID is stored in Redis until the token would have expired; every service rejects it from then on |
| Customer tries the staff login, or the reverse | Rejected; each endpoint accepts only its own user type                                                |
| Tunnel to on-premises is down                  | Sign-in still works                                                                                   |

---

## 4. customer-service (8089, cloud)

**Scope.** The customer member identity, personal records, demographic profile, KYC submission, verification, and update approval workflows, beneficiaries, and the Customer 360 aggregator.

**Not in scope.** Does not own accounts or balances. Does not execute financial ledger postings.

**Data it owns.** `CUSTOMER`, `KYC`, `KYC_UPDATE_REQUEST`, `CHANNEL_PROFILE`, `BENEFICIARY`.

| Action                          | Who                        | Endpoint   |
| ------------------------------- | -------------------------- | ---------- |
| View own profile                | Customer                   | C1         |
| View a customer's profile       | Teller, Admin              | C2         |
| View Customer 360               | Owner, Teller, Admin       | C3         |
| List, add, remove beneficiaries | Customer                   | C4, C5, C6 |
| Read risk profile               | Orchestration service only | C7         |
| Submit initial KYC              | Customer                   | C8         |
| Request KYC update              | Customer                   | C9         |
| List pending KYC updates        | Teller, Admin              | C10        |
| Approve KYC update              | Teller, Admin              | C11        |
| Reject KYC update              | Teller, Admin              | C12        |

| Scenario                                    | What happens                                                                                                                                          |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Customer opens their profile                | Full member profile and KYC details loaded directly from `customer-service`                                                                            |
| Customer opens Customer 360                 | Accounts and balances from `account-service`, recent transactions and spending from `transaction-service`, scores and beneficiaries from its own store |
| Customer asks for another customer's 360    | HTTP 403                                                                                                                                              |
| Staff opens a customer's 360                | Same view, plus the risk score                                                                                                                        |
| Customer submits KYC update                 | Stored as `PENDING`; `kyc.request.submitted.v1` event emitted to Kafka                                                                                |
| Staff approves KYC update                   | Master `KYC` record updated; `kyc.request.evaluated.v1` event emitted to Kafka                                                                       |
| Customer adds a beneficiary at our bank     | Account number is checked with `account-service`; unknown number is rejected                                                                           |
| Customer adds a beneficiary at another bank | Stored with that bank's code; checked with the partner only if they offer the account check                                                           |
| Saga asks for a risk profile                | Score, tier and daily limit returned from its own store, with no call to another service                                                              |

---

## 5. account-service (8082, on-premises)

**Scope.** The account as a financial record: opening, type (`SAVINGS`, `CURRENT`, `LOAN`), status (`ACTIVE`, `FROZEN`, `CLOSED`), flags/holds, and closure workflows.

**Not in scope.** Never changes a balance (owned by `ledger-service`). Does not store credentials (owned by `auth-service`). Does not own customer demographic identity or KYC workflows (owned by `customer-service`).

**Data it owns.** Oracle: `ACCOUNT`, `ACCOUNT_FLAG`, `ACCOUNT_CLOSURE_REQUEST`.

| Action                                 | Who                           | Endpoint |
| -------------------------------------- | ----------------------------- | -------- |
| Open an account                        | Teller, Admin                 | K3       |
| List all accounts                      | Teller, Admin                 | K4       |
| List own accounts, view one account    | Customer (own), Teller, Admin | K4       |
| Change account status                  | Teller, Admin                 | K5       |
| Add a flag                             | Teller, Admin                 | K5       |
| Remove a flag                          | Admin                         | K5       |
| Request closure, view own request      | Customer                      | K5       |
| List, approve, reject closure requests | Admin (list also Teller)      | K5       |
| Look up an account by ID or number     | Services only                 | K6, K7   |

| Scenario                                                  | What happens                                                                                      |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Teller opens a savings account                            | Account row created; the ledger is asked to open a zero balance;`account.created.v1` is published |
| Open an account for a customer whose KYC is not verified  | Rejected                                                                                          |
| Open a loan account                                       | Created with type`LOAN`; its schedule is seeded for the demo                                      |
| Ledger is down while opening an account                   | The opening fails as a whole; no account without a balance is left behind                         |
| Customer lists accounts                                   | Only their own; balances are read from the ledger                                                 |
| Teller freezes an account                                 | Status becomes`FROZEN`; new transfers from or to it are rejected at validation                    |
| Teller flags an account                                   | Flag is active; closure is blocked while any flag is active                                       |
| Customer requests closure with money still in the account | Rejected with`ACCOUNT_NON_ZERO_BALANCE`; the balance is checked with the ledger                   |
| Customer requests closure with an active flag             | Rejected with`ACCOUNT_ACTIVE_FLAG_EXISTS`                                                         |
| Admin approves closure                                    | Status becomes`CLOSED`; `account.closure.completed.v1` is published                               |

---

## 6. transaction-service (8090, cloud)

**Scope.** The front door for moving money. Accepts a transfer, checks the request, records it, hands it to the saga, and keeps the timeline that the app shows. Also hosts the inter-bank endpoints.

**Not in scope.** Does not decide risk. Does not touch balances. Does not call the ledger.

**Data it owns.** `transaction_db`, schema `txn` (transaction records, stage events); Redis (idempotency keys).

| Action                                                       | Who                                            | Endpoint   |
| ------------------------------------------------------------ | ---------------------------------------------- | ---------- |
| Send a transfer                                              | Customer, Teller                               | T1         |
| View a transaction                                           | Owner, Teller, Admin                           | T2         |
| Track a transaction                                          | Owner (3 stages), Teller and Admin (11 stages) | T3         |
| List transactions                                            | Customer (own), Teller, Admin                  | T4         |
| Reverse a posted transfer                                    | Teller, Admin                                  | T5         |
| Receive an inter-bank transfer, report status, account check | Partner bank                                   | I1, I2, I3 |

| Scenario                                                           | What happens                                                                             |
| ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| Customer sends a valid transfer                                    | Stored as`INITIATED`; stages 1 to 3 pass; saga started; HTTP 202 with the transaction ID |
| Same request sent twice (double tap)                               | The second call returns the first result; only one transfer exists                       |
| No`Idempotency-Key` header                                         | HTTP 400                                                                                 |
| Source account is not the caller's                                 | Fails at stage 3 with`ACCOUNT_NOT_OWNED`; staff may act for any customer                 |
| Destination account number does not exist                          | Fails at stage 2 with`ACCOUNT_NOT_FOUND`                                                 |
| Either account is frozen or closed                                 | Fails at stage 2 with`ACCOUNT_NOT_ACTIVE`                                                |
| Amount is zero, negative or malformed                              | HTTP 400 with the field named                                                            |
| Source and destination are the same                                | HTTP 400                                                                                 |
| Customer opens the tracker                                         | Three stages: Initiated, Processing, Posted or Failed, with a plain message on failure   |
| Teller opens the tracker                                           | All eleven stages with times and reasons                                                 |
| Customer asks about someone else's transaction                     | HTTP 403                                                                                 |
| Teller reverses a posted transfer                                  | A new reversal transaction is created and sent to the saga                               |
| Teller reverses a transfer that is not posted, or already reversed | Rejected                                                                                 |
| Stage events arrive out of order or twice                          | The timeline keeps one row per stage; a repeat changes nothing                           |

---

## 7. orchestration-service (8091, cloud)

**Scope.** Runs each transfer step by step and decides what to do when a step fails. Publishes one event per stage from 4 to 11.

**Not in scope.** No public endpoints. Does not compute risk itself. Does not compute balances.

**Data it owns.** `transaction_db`, schema `saga` (one row per saga).

| Action                | Who                            | Endpoint |
| --------------------- | ------------------------------ | -------- |
| Start a transfer saga | `transaction-service`          | O1       |
| Start a reversal saga | `transaction-service`          | O2       |
| Read saga state       | `transaction-service`, support | O3       |

| Scenario                                         | What happens                                                                                                                    | Final status                      |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| Everything passes                                | Risk approves, hold placed, hold captured, events for stages 4 to 11                                                            | `POSTED`                          |
| Risk declines                                    | Stops before any hold                                                                                                           | `FAILED`, `RISK_DECLINED`         |
| Over the daily limit                             | Stops before any hold                                                                                                           | `FAILED`, `LIMIT_EXCEEDED`        |
| Risk engine does not answer in 150 ms            | Treated as a decline                                                                                                            | `FAILED`, `RISK_UNAVAILABLE`      |
| Not enough money                                 | The hold is refused at stage 6                                                                                                  | `FAILED`, `INSUFFICIENT_FUNDS`    |
| Hold placed, capture fails                       | The hold is released                                                                                                            | <br />`FAILED`; balance unchanged |
| Ledger unreachable before the hold               | Nothing to undo                                                                                                                 | `FAILED`, `LEDGER_UNAVAILABLE`    |
| Ledger unreachable after the hold                | Retries; the transaction stays at its stage and is visible to staff                                                             | `PROCESSING` until resolved       |
| Posted, but the notification event never arrives | Stage 10 stays pending; the money has moved and the customer sees Posted                                                        | `POSTED`                          |
| Service restarts mid-saga                        | The saga row is read and the saga continues from its last state; ledger calls are safe to repeat because of the idempotency key | As above                          |
| Outbound inter-bank: partner accepts             | Hold captured against the partner's settlement account                                                                          | `POSTED`                          |
| Outbound inter-bank: partner rejects             | Hold released                                                                                                                   | `FAILED`, `INTERBANK_REJECTED`    |
| Outbound inter-bank: partner silent              | Status asked up to three times; the hold is kept and staff are alerted                                                          | `PROCESSING`                      |
| Reversal requested                               | Ledger posts the compensating entries                                                                                           | Original becomes`REVERSED`        |

---

## 8. risk-engine (8092, cloud)

**Scope.** Answers one question: may this transfer proceed? Checks a fraud score and the daily limit.

**Not in scope.** No database. No calls to other services. Never sees account balances. Does not block or flag accounts.

**Data it owns.** Redis counters only (amount and count per customer per day).

| Action              | Who                     | Endpoint |
| ------------------- | ----------------------- | -------- |
| Evaluate a transfer | `orchestration-service` | R1       |

| Scenario                                               | Decision                                                                         |
| ------------------------------------------------------ | -------------------------------------------------------------------------------- |
| Score below 0.60 and within the limit                  | `APPROVE`                                                                        |
| Score from 0.60 to 0.85                                | `DECLINE`, `REVIEW_REQUIRED`                                                     |
| Score above 0.85                                       | `DECLINE`, `HIGH_RISK`                                                           |
| Today's total plus this amount exceeds the daily limit | `DECLINE`, `LIMIT_EXCEEDED`                                                      |
| Many transfers in a short time                         | Score rises with velocity; may cross a threshold                                 |
| First transfer to a new beneficiary                    | Score rises by a fixed amount                                                    |
| Redis unavailable                                      | Evaluates without velocity and says so in the reasons; never fails open silently |
| Transfer is later declined or fails                    | The day's counters are not increased                                             |

Target: under 40 ms at the 95th percentile.

---

## 9. ledger-service (8083, on-premises)

**Scope.** The books. The only service that changes a balance. Places, captures and releases holds; posts double entries; reverses; writes the audit row in the same transaction; writes events to the outbox.

**Not in scope.** Does not decide who may transfer or whether a transfer is risky. Does not know about customers beyond the ID on an account. Is not called by customers.

**Data it owns.** Oracle: `BALANCE`, `HOLD`, `TRANSACTION`, `TRANSFER_REQUEST`, `OUTBOX`; writes to the audit store.

| Action                            | Who                     | Endpoint   |
| --------------------------------- | ----------------------- | ---------- |
| Read a balance                    | Teller, Admin; services | L-S1, L2   |
| Cash deposit, cash withdrawal     | Teller, Admin           | L-S2, L-S3 |
| Counter transfer                  | Teller, Admin           | L-S4       |
| Open a balance                    | `account-service`       | L1         |
| Place, capture, release a hold    | `orchestration-service` | L3, L4, L5 |
| Reverse a posted transfer         | `orchestration-service` | L7         |
| Post batch entries, trial balance | `batch-service`         | L8, L9     |

| Scenario                                          | What happens                                                                                                                                           |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Place a hold with enough money                    | `HOLD` row `RESERVED`; available balance drops; ledger balance unchanged                                                                               |
| Place a hold without enough money                 | Refused,`INSUFFICIENT_FUNDS`; nothing written                                                                                                          |
| Capture a hold                                    | Debit and credit rows written, both balances updated, audit rows chained, hold becomes`POSTED`, event written to the outbox, all in one transaction    |
| Release a hold                                    | Hold becomes`CANCELLED`; available balance restored                                                                                                    |
| Capture or release a hold twice                   | The second call returns the first result                                                                                                               |
| Capture a hold that was released                  | Refused,`HOLD_NOT_RESERVED`                                                                                                                            |
| Two transfers from one account at the same moment | Row locks make them run one after the other; the second sees the reduced balance                                                                       |
| A to B and B to A at the same moment              | Locks are always taken lowest account ID first, so there is no deadlock                                                                                |
| Audit store is down                               | The posting is rolled back; no balance changes without an audit row                                                                                    |
| Kafka or the tunnel is down                       | The posting still commits; the event waits in the outbox and is sent later                                                                             |
| Outbox relay                                      | A scheduled job inside this service, not a separate one. It reads unsent`OUTBOX` rows, publishes them to Kafka through the bridge, and marks them sent |
| Teller takes a cash deposit                       | Credit to the customer, debit to the cash GL account                                                                                                   |
| Teller takes a withdrawal larger than the balance | Refused,`INSUFFICIENT_FUNDS`                                                                                                                           |
| Reverse a posted transfer                         | Two new rows,`REVERSAL_CREDIT` and `REVERSAL_DEBIT`; originals untouched                                                                               |
| Reverse the same transfer again                   | Refused,`ALREADY_REVERSED`                                                                                                                             |
| Reverse when the recipient has spent the money    | Refused,`INSUFFICIENT_FUNDS`; staff decide what to do next                                                                                             |
| A customer token calls a ledger endpoint          | HTTP 403                                                                                                                                               |

---

## 10. batch-service (8093, on-premises)

**Scope.** Runs the end-of-day pipeline on demand: six steps, in order, each reported.

**Not in scope.** Does not write balances itself; every posting goes through the ledger. No scheduler.

**Data it owns.** On-premises PostgreSQL, schema `batch` (runs, steps, standing instructions, loan schedules).

| Action                      | Who           | Endpoint |
| --------------------------- | ------------- | -------- |
| Trigger end of day          | Admin         | B1       |
| View one run with its steps | Admin, Teller | B2       |
| List runs                   | Admin, Teller | B3       |

| Step                    | What it does                                                                    |
| ----------------------- | ------------------------------------------------------------------------------- |
| 1 Interest accrual      | Credits daily interest to savings accounts from the interest expense GL account |
| 2 Service fees          | Debits fees from qualifying accounts to the fee income GL account               |
| 3 Standing instructions | Executes transfers that are due today                                           |
| 4 Loan amortization     | Collects instalments that are due from the linked deposit account               |
| 5 GL reconciliation     | Compares total debits with total credits for the day                            |
| 6 Audit seal            | Writes one closing hash for the day's audit chain                               |

| Scenario                                       | What happens                                                                                                         |
| ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Admin triggers; all is well                    | Six steps complete in 5 to 8 seconds; run is`COMPLETED`                                                              |
| Trigger while a run is in progress             | Refused,`BATCH_ALREADY_RUNNING`                                                                                      |
| Trigger twice for the same business date       | The second run posts nothing new, because each posting reference is unique per date                                  |
| An account lacks money for a fee or instalment | That item is recorded as failed; the step continues with the others                                                  |
| Debits do not equal credits at step 5          | Step 5`FAILED`, step 6 `SKIPPED`, run `HALTED`, admin alerted. Earlier postings stay; they are corrected by reversal |
| Ledger unreachable                             | The current step fails; the run is`FAILED`; safe to trigger again                                                    |
| A teller tries to trigger                      | HTTP 403                                                                                                             |

---

## 11. notification-service (8084, cloud)

**Scope.** Listens to events and turns them into in-app notifications. Serves the customer's inbox, and the dead-letter list and replay for the events it could not process.

**Not in scope.** Does not send transfers or change any business data. Does not call other services.

**Data it owns.** `notification_db`.

| Action                 | Who      | Endpoint |
| ---------------------- | -------- | -------- |
| View own notifications | Customer | N1       |
| Mark one as read       | Customer | N2       |
| Unread count           | Customer | N3       |
| List dead-letter records | Admin  | N4       |
| Replay a dead-letter record, or all of them | Admin | N5, N6 |

| Scenario                                         | What happens                                                                             |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| Transfer posted                                  | One notification for the sender, one for the recipient; then`notification.dispatched.v1` |
| Transfer failed                                  | One notification for the sender                                                          |
| Deposit or withdrawal                            | One notification for the account owner                                                   |
| Account closed, KYC request submitted or decided | One notification for the customer                                                        |
| The same event arrives twice                     | The second is ignored; the event ID is unique                                            |
| An event cannot be processed                     | Retried after 1 s, 2 s, 4 s; then parked on`notification.events.DLT`                     |
| Admin replays a dead-letter record               | The record is sent back to its original topic and processed again                        |
| Customer marks another customer's notification   | HTTP 404                                                                                 |

---

## 12. audit-service (8085, on-premises)

**Scope.** Read-only window onto the audit chain: statements, one transaction's record, chain verification, daily seals.

**Not in scope.** Writes nothing. The ledger writes audit rows; the batch writes the seal.

**Data it reads.** On-premises PostgreSQL, schema `audit_store`.

| Action                         | Who                           | Endpoint |
| ------------------------------ | ----------------------------- | -------- |
| Account statement              | Customer (own), Teller, Admin | U1       |
| One transaction's audit record | Teller, Admin                 | U2       |
| Verify an account's chain      | Customer (own), Teller, Admin | U3       |
| Read a day's seal              | Teller, Admin                 | U4       |

| Scenario                               | What happens                                                                                            |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Verify an untouched chain              | `isChainIntact: true` with the count checked                                                            |
| A row was altered                      | The database trigger blocks the change; if it happened anyway, verification reports the chain as broken |
| Statement after a reversal             | Shows the original entries and the two reversal entries; nothing is removed                             |
| Seal for a day with no completed batch | HTTP 404                                                                                                |

---

## 13. Flutter app and React staff console

| Client                        | Used by                         | Can do                                                                                                                                                                          |
| ----------------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Flutter app (web and Android) | Customers; staff for monitoring | Sign in, Customer 360, accounts, send a transfer, track it, beneficiaries, notifications, mock KYC screen; staff see the 11-stage tracker; admin triggers and watches the batch |
| React staff console (frozen)  | Tellers, Admins                 | Open accounts, cash deposit and withdrawal, counter transfer, freeze and flag accounts, approve KYC updates and closures, audit statements and chain verification               |

Neither client holds business rules. A button hidden in the app is not security; every rule above is enforced by the service.

---

## 14. End-to-end scenarios for the demo

| #   | Scenario                                     | Services touched                                       | What the audience sees                                                                     |
| --- | -------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| 1   | Customer signs in and opens Customer 360     | auth, customer, account, ledger, transaction           | Accounts, balances, spending chart, score gauges                                           |
| 2   | Customer sends a transfer to another member  | transaction, orchestration, risk, ledger, notification | Tracker moves Initiated, Processing, Posted; both balances change; both get a notification |
| 3   | Teller watches the same transfer             | transaction                                            | Eleven stages with times                                                                   |
| 4   | Transfer declined by risk                    | transaction, orchestration, risk                       | Fails at Fraud Check; balance unchanged                                                    |
| 5   | Transfer with not enough money               | transaction, orchestration, ledger                     | Fails at Funds Check; balance unchanged                                                    |
| 6   | Double tap on Send                           | transaction                                            | One transfer only                                                                          |
| 7   | Teller reverses a transfer                   | transaction, orchestration, ledger, audit              | Balances return; statement shows four entries; chain still verifies                        |
| 8   | Notification consumer breaks, then is fixed  | notification                                           | Record appears in the dead-letter list; admin replays it; notification arrives             |
| 9   | Admin runs end of day                        | batch, ledger, audit                                   | Six steps tick through; balances change; debits equal credits; seal shown                  |
| 10  | Transfer to another group's bank             | transaction, orchestration, ledger, partner            | Posted on our side; partner shows the credit                                               |
| 11  | Transfer from another group's bank           | transaction, ledger, notification                      | Our customer's balance rises and they get a notification                                   |
| 12  | Concurrent transfers on one account (JMeter) | ledger                                                 | No overdraft; no double spend                                                              |
