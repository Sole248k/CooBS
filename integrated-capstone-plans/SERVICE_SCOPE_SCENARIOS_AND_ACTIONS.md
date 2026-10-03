# Service Scope, Scenarios and Actions

**Version:** 1.0 draft
**Purpose:** for each service, say what it is responsible for, what it must not do, which actions each role can perform, and what happens in each scenario, including the failure cases.
**Paths and payloads:** [`../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md`](../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md). Endpoint numbers such as T1 or L3 refer to that file. If this document and the contract disagree, the contract wins.

---

## 1. Who can do what, at a glance

| Action                                         | Customer     | Teller         | Admin | Partner bank |
| ---------------------------------------------- | ------------ | -------------- | ----- | ------------ |
| Sign in, refresh, sign out                     | Yes          | Yes            | Yes   | —           |
| View own profile and Customer 360              | Yes          | —             | —    | —           |
| View any customer's profile and Customer 360   | —           | Yes            | Yes   | —           |
| Manage own beneficiaries                       | Yes          | —             | —    | —           |
| Submit a KYC update request                    | Yes          | —             | —    | —           |
| Approve or reject a KYC update                 | —           | Yes            | Yes   | —           |
| Open an account for a customer                 | —           | Yes            | Yes   | —           |
| View own accounts and balances                 | Yes          | —             | —    | —           |
| View all accounts                              | —           | Yes            | Yes   | —           |
| Freeze, unfreeze or change account status      | —           | Yes            | Yes   | —           |
| Flag an account                                | —           | Yes            | Yes   | —           |
| Remove a flag                                  | —           | —             | Yes   | —           |
| Request account closure                        | Yes          | —             | —    | —           |
| Approve or reject closure                      | —           | —             | Yes   | —           |
| Send a transfer (own bank or another bank)     | Yes          | Yes, on behalf | —    | —           |
| Track a transfer: 3 stages                     | Yes          | —             | —    | —           |
| Track a transfer: 11 stages                    | —           | Yes            | Yes   | —           |
| Cash deposit or withdrawal at the counter      | —           | Yes            | Yes   | —           |
| Reverse a posted transfer                      | —           | Yes            | Yes   | —           |
| Trigger end-of-day batch                       | —           | —             | Yes   | —           |
| View batch runs                                | —           | Yes            | Yes   | —           |
| View and replay dead-letter records            | —           | —             | Yes   | —           |
| View own notifications                         | Yes          | —             | —    | —           |
| View account statement and verify audit chain  | Own accounts | Yes            | Yes   | —           |
| Send us an inter-bank transfer, ask its status | —           | —             | —    | Yes          |

Registration is not available to anyone: members are seeded.

---

## 2. api-gateway (8080, cloud)

**Scope.** The only public entry. Routes each path to its service, applies rate limits and CORS, and adds a correlation ID.

**Not in scope.** No business rules, no token issuing, no data.

| Action                               | Result                                         |
| ------------------------------------ | ---------------------------------------------- |
| Route a request by path              | Forwarded to the owning service                |
| Rate-limit by user or IP             | HTTP 429 when the limit is passed              |
| Add`X-Correlation-ID` when missing | Same ID appears in every service log and event |

| Scenario                                     | What happens                                                                       |
| -------------------------------------------- | ---------------------------------------------------------------------------------- |
| Request to`/api/v1/customers/kyc/**`       | Goes to`account-service`; this rule is matched before the general customers rule |
| Request to any other`/api/v1/customers/**` | Goes to`customer-service`                                                        |
| Request to`/internal/**` from outside      | Not routed; HTTP 404                                                               |
| Too many login attempts from one IP          | HTTP 429 from the auth limit (10 per second, burst 20)                             |
| A target service is down                     | HTTP 503 in the standard error shape                                               |

---

## 3. auth-service (8081, cloud)

**Scope.** Proves who the caller is. Checks username and password, runs MFA, issues and refreshes tokens, revokes tokens.

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
| Correct credentials, MFA off                   | Tokens returned;`userId` is the customer ID for a customer, the staff ID for staff                  |
| Correct credentials, MFA on                    | No tokens yet; an`mfaToken` is returned and a code is sent                                          |
| Wrong MFA code                                 | Rejected; the`mfaToken` stays valid until it expires                                                |
| Wrong password                                 | HTTP 401; nothing reveals whether the username exists                                                 |
| Inactive login                                 | HTTP 403                                                                                              |
| Access token expired                           | Client calls refresh and gets a new access token                                                      |
| Sign out                                       | Token ID is stored in Redis until the token would have expired; every service rejects it from then on |
| Customer tries the staff login, or the reverse | Rejected; each endpoint accepts only its own user type                                                |
| Tunnel to on-premises is down                  | Sign-in still works                                                                                   |

---

## 4. customer-service (8089, cloud)

**Scope.** The customer as seen through the app: display name, membership number, KYC tier, risk and credit scores, beneficiaries, and the Customer 360 view that combines data from other services.

**Not in scope.** Does not store the KYC master (legal name, address, mobile number). Does not own accounts or balances. Does not approve KYC changes.

**Data it owns.** `customer_db` (channel profile, beneficiaries).

| Action                          | Who                        | Endpoint   |
| ------------------------------- | -------------------------- | ---------- |
| View own profile                | Customer                   | C1         |
| View a customer's profile       | Teller, Admin              | C2         |
| View Customer 360               | Owner, Teller, Admin       | C3         |
| List, add, remove beneficiaries | Customer                   | C4, C5, C6 |
| Read risk profile               | Orchestration service only | C7         |

| Scenario                                    | What happens                                                                                                                                              |
| ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Customer opens their profile                | Channel profile from`customer_db`, KYC details fetched live from `account-service`, merged into one response                                          |
| Customer opens Customer 360                 | Accounts and balances from`account-service`, recent transactions and spending from `transaction-service`, scores and beneficiaries from its own store |
| Customer asks for another customer's 360    | HTTP 403                                                                                                                                                  |
| Staff opens a customer's 360                | Same view, plus the risk score                                                                                                                            |
| On-premises is unreachable                  | Profile and beneficiaries still load; the accounts and KYC sections are marked unavailable; the page does not fail whole                                  |
| Customer adds a beneficiary at our bank     | Account number is checked with`account-service`; unknown number is rejected                                                                             |
| Customer adds a beneficiary at another bank | Stored with that bank's code; checked with the partner only if they offer the account check                                                               |
| Saga asks for a risk profile                | Score, tier and daily limit returned from its own store, with no call to another service                                                                  |

---

## 5. account-service (8082, on-premises)

**Scope.** The account as a record: opening, type, status, flags, closure requests. Also holds the KYC master and the KYC update workflow.

**Not in scope.** Never changes a balance. Does not store credentials. Does not decide whether a transfer may proceed.

**Data it owns.** Oracle: `ACCOUNT`, `CUSTOMER` (ID and KYC status), `KYC`, `KYC_UPDATE_REQUEST`, `ACCOUNT_FLAG`, `ACCOUNT_CLOSURE_REQUEST`.

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
| Submit a KYC update request            | Customer                      | K2       |
| List, approve, reject KYC updates      | Teller, Admin                 | K2       |
| Look up an account by ID or number     | Services only                 | K6, K7   |
| Read the KYC master                    | `customer-service` only     | K9       |

| Scenario                                                  | What happens                                                                                        |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Teller opens a savings account                            | Account row created; the ledger is asked to open a zero balance;`account.created.v1` is published |
| Open an account for a customer whose KYC is not verified  | Rejected                                                                                            |
| Open a loan account                                       | Created with type`LOAN`; its schedule is seeded for the demo                                      |
| Ledger is down while opening an account                   | The opening fails as a whole; no account without a balance is left behind                           |
| Customer lists accounts                                   | Only their own; balances are read from the ledger                                                   |
| Teller freezes an account                                 | Status becomes`FROZEN`; new transfers from or to it are rejected at validation                    |
| Teller flags an account                                   | Flag is active; closure is blocked while any flag is active                                         |
| Customer requests closure with money still in the account | Rejected with`ACCOUNT_NON_ZERO_BALANCE`; the balance is checked with the ledger                   |
| Customer requests closure with an active flag             | Rejected with`ACCOUNT_ACTIVE_FLAG_EXISTS`                                                         |
| Admin approves closure                                    | Status becomes`CLOSED`; `account.closure.completed.v1` is published                             |
| Customer submits a KYC change                             | Stored as pending;`kyc.request.submitted.v1` is published                                         |
| Teller approves the change                                | The KYC master is updated;`kyc.request.evaluated.v1` is published                                 |

---

## 6. transaction-service (8090, cloud)

**Scope.** The front door for moving money. Accepts a transfer, checks the request, records it, hands it to the saga, and keeps the timeline that the app shows. Also hosts the inter-bank endpoints and the dead-letter replay.

**Not in scope.** Does not decide risk. Does not touch balances. Does not call the ledger.

**Data it owns.** `transaction_db`, schema `txn` (transaction records, stage events); Redis (idempotency keys).

| Action                                                       | Who                                            | Endpoint   |
| ------------------------------------------------------------ | ---------------------------------------------- | ---------- |
| Send a transfer                                              | Customer, Teller                               | T1         |
| View a transaction                                           | Owner, Teller, Admin                           | T2         |
| Track a transaction                                          | Owner (3 stages), Teller and Admin (11 stages) | T3         |
| List transactions                                            | Customer (own), Teller, Admin                  | T4         |
| Reverse a posted transfer                                    | Teller, Admin                                  | T5         |
| List dead-letter records                                     | Admin                                          | T6         |
| Replay a dead-letter record                                  | Admin                                          | T7         |
| Receive an inter-bank transfer, report status, account check | Partner bank                                   | I1, I2, I3 |

| Scenario                                                           | What happens                                                                               |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| Customer sends a valid transfer                                    | Stored as`INITIATED`; stages 1 to 3 pass; saga started; HTTP 202 with the transaction ID |
| Same request sent twice (double tap)                               | The second call returns the first result; only one transfer exists                         |
| No`Idempotency-Key` header                                       | HTTP 400                                                                                   |
| Source account is not the caller's                                 | Fails at stage 3 with`ACCOUNT_NOT_OWNED`; staff may act for any customer                 |
| Destination account number does not exist                          | Fails at stage 2 with`ACCOUNT_NOT_FOUND`                                                 |
| Either account is frozen or closed                                 | Fails at stage 2 with`ACCOUNT_NOT_ACTIVE`                                                |
| Amount is zero, negative or malformed                              | HTTP 400 with the field named                                                              |
| Source and destination are the same                                | HTTP 400                                                                                   |
| Customer opens the tracker                                         | Three stages: Initiated, Processing, Posted or Failed, with a plain message on failure     |
| Teller opens the tracker                                           | All eleven stages with times and reasons                                                   |
| Customer asks about someone else's transaction                     | HTTP 403                                                                                   |
| Teller reverses a posted transfer                                  | A new reversal transaction is created and sent to the saga                                 |
| Teller reverses a transfer that is not posted, or already reversed | Rejected                                                                                   |
| Admin replays a dead-letter record                                 | The record is sent back to its original topic and processed again                          |
| Stage events arrive out of order or twice                          | The timeline keeps one row per stage; a repeat changes nothing                             |

---

## 7. orchestration-service (8091, cloud)

**Scope.** Runs each transfer step by step and decides what to do when a step fails. Publishes one event per stage from 4 to 11.

**Not in scope.** No public endpoints. Does not compute risk itself. Does not compute balances.

**Data it owns.** `transaction_db`, schema `saga` (one row per saga).

| Action                | Who                              | Endpoint |
| --------------------- | -------------------------------- | -------- |
| Start a transfer saga | `transaction-service`          | O1       |
| Start a reversal saga | `transaction-service`          | O2       |
| Read saga state       | `transaction-service`, support | O3       |

| Scenario                                         | What happens                                                                                                                    | Final status                       |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| Everything passes                                | Risk approves, hold placed, hold captured, events for stages 4 to 11                                                            | `POSTED`                         |
| Risk declines                                    | Stops before any hold                                                                                                           | `FAILED`, `RISK_DECLINED`      |
| Over the daily limit                             | Stops before any hold                                                                                                           | `FAILED`, `LIMIT_EXCEEDED`     |
| Risk engine does not answer in 150 ms            | Treated as a decline                                                                                                            | `FAILED`, `RISK_UNAVAILABLE`   |
| Not enough money                                 | The hold is refused at stage 6                                                                                                  | `FAILED`, `INSUFFICIENT_FUNDS` |
| Hold placed, capture fails                       | The hold is released                                                                                                            | `FAILED`; balance unchanged      |
| Ledger unreachable before the hold               | Nothing to undo                                                                                                                 | `FAILED`, `LEDGER_UNAVAILABLE` |
| Ledger unreachable after the hold                | Retries; the transaction stays at its stage and is visible to staff                                                             | `PROCESSING` until resolved      |
| Posted, but the notification event never arrives | Stage 10 stays pending; the money has moved and the customer sees Posted                                                        | `POSTED`                         |
| Service restarts mid-saga                        | The saga row is read and the saga continues from its last state; ledger calls are safe to repeat because of the idempotency key | As above                           |
| Outbound inter-bank: partner accepts             | Hold captured against the partner's settlement account                                                                          | `POSTED`                         |
| Outbound inter-bank: partner rejects             | Hold released                                                                                                                   | `FAILED`, `INTERBANK_REJECTED` |
| Outbound inter-bank: partner silent              | Status asked up to three times; the hold is kept and staff are alerted                                                          | `PROCESSING`                     |
| Reversal requested                               | Ledger posts the compensating entries                                                                                           | Original becomes`REVERSED`       |

---

## 8. risk-engine (8092, cloud)

**Scope.** Answers one question: may this transfer proceed? Checks a fraud score and the daily limit.

**Not in scope.** No database. No calls to other services. Never sees account balances. Does not block or flag accounts.

**Data it owns.** Redis counters only (amount and count per customer per day).

| Action              | Who                       | Endpoint |
| ------------------- | ------------------------- | -------- |
| Evaluate a transfer | `orchestration-service` | R1       |

| Scenario                                               | Decision                                                                         |
| ------------------------------------------------------ | -------------------------------------------------------------------------------- |
| Score below 0.60 and within the limit                  | `APPROVE`                                                                      |
| Score from 0.60 to 0.85                                | `DECLINE`, `REVIEW_REQUIRED`                                                 |
| Score above 0.85                                       | `DECLINE`, `HIGH_RISK`                                                       |
| Today's total plus this amount exceeds the daily limit | `DECLINE`, `LIMIT_EXCEEDED`                                                  |
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

| Action                            | Who                       | Endpoint   |
| --------------------------------- | ------------------------- | ---------- |
| Read a balance                    | Teller, Admin; services   | L-S1, L2   |
| Cash deposit, cash withdrawal     | Teller, Admin             | L-S2, L-S3 |
| Counter transfer                  | Teller, Admin             | L-S4       |
| Open a balance                    | `account-service`       | L1         |
| Place, capture, release a hold    | `orchestration-service` | L3, L4, L5 |
| Reverse a posted transfer         | `orchestration-service` | L7         |
| Post batch entries, trial balance | `batch-service`         | L8, L9     |

| Scenario                                          | What happens                                                                                                                                          |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Place a hold with enough money                    | `HOLD` row `RESERVED`; available balance drops; ledger balance unchanged                                                                          |
| Place a hold without enough money                 | Refused,`INSUFFICIENT_FUNDS`; nothing written                                                                                                       |
| Capture a hold                                    | Debit and credit rows written, both balances updated, audit rows chained, hold becomes`POSTED`, event written to the outbox, all in one transaction |
| Release a hold                                    | Hold becomes`CANCELLED`; available balance restored                                                                                                 |
| Capture or release a hold twice                   | The second call returns the first result                                                                                                              |
| Capture a hold that was released                  | Refused,`HOLD_NOT_RESERVED`                                                                                                                         |
| Two transfers from one account at the same moment | Row locks make them run one after the other; the second sees the reduced balance                                                                      |
| A to B and B to A at the same moment              | Locks are always taken lowest account ID first, so there is no deadlock                                                                               |
| Audit store is down                               | The posting is rolled back; no balance changes without an audit row                                                                                   |
| Kafka or the tunnel is down                       | The posting still commits; the event waits in the outbox and is sent later                                                                            |
| Teller takes a cash deposit                       | Credit to the customer, debit to the cash GL account                                                                                                  |
| Teller takes a withdrawal larger than the balance | Refused,`INSUFFICIENT_FUNDS`                                                                                                                        |
| Reverse a posted transfer                         | Two new rows,`REVERSAL_CREDIT` and `REVERSAL_DEBIT`; originals untouched                                                                          |
| Reverse the same transfer again                   | Refused,`ALREADY_REVERSED`                                                                                                                          |
| Reverse when the recipient has spent the money    | Refused,`INSUFFICIENT_FUNDS`; staff decide what to do next                                                                                          |
| A customer token calls a ledger endpoint          | HTTP 403                                                                                                                                              |

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

| Scenario                                       | What happens                                                                                                               |
| ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Admin triggers; all is well                    | Six steps complete in 5 to 8 seconds; run is`COMPLETED`                                                                  |
| Trigger while a run is in progress             | Refused,`BATCH_ALREADY_RUNNING`                                                                                          |
| Trigger twice for the same business date       | The second run posts nothing new, because each posting reference is unique per date                                        |
| An account lacks money for a fee or instalment | That item is recorded as failed; the step continues with the others                                                        |
| Debits do not equal credits at step 5          | Step 5`FAILED`, step 6 `SKIPPED`, run `HALTED`, admin alerted. Earlier postings stay; they are corrected by reversal |
| Ledger unreachable                             | The current step fails; the run is`FAILED`; safe to trigger again                                                        |
| A teller tries to trigger                      | HTTP 403                                                                                                                   |

---

## 11. notification-service (8084, cloud)

**Scope.** Listens to events and turns them into in-app notifications. Serves the customer's inbox.

**Not in scope.** Does not send transfers or change any business data. Does not call other services.

**Data it owns.** `notification_db`.

| Action                 | Who      | Endpoint |
| ---------------------- | -------- | -------- |
| View own notifications | Customer | N1       |
| Mark one as read       | Customer | N2       |
| Unread count           | Customer | N3       |

| Scenario                                         | What happens                                                                               |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| Transfer posted                                  | One notification for the sender, one for the recipient; then`notification.dispatched.v1` |
| Transfer failed                                  | One notification for the sender                                                            |
| Deposit or withdrawal                            | One notification for the account owner                                                     |
| Account closed, KYC request submitted or decided | One notification for the customer                                                          |
| The same event arrives twice                     | The second is ignored; the event ID is unique                                              |
| An event cannot be processed                     | Retried after 1 s, 2 s, 4 s; then sent to the`.DLT` topic                                |
| Customer marks another customer's notification   | HTTP 404                                                                                   |

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
| Verify an untouched chain              | `isChainIntact: true` with the count checked                                                          |
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

| #  | Scenario                                     | Services touched                                       | What the audience sees                                                                     |
| -- | -------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| 1  | Customer signs in and opens Customer 360     | auth, customer, account, ledger, transaction           | Accounts, balances, spending chart, score gauges                                           |
| 2  | Customer sends a transfer to another member  | transaction, orchestration, risk, ledger, notification | Tracker moves Initiated, Processing, Posted; both balances change; both get a notification |
| 3  | Teller watches the same transfer             | transaction                                            | Eleven stages with times                                                                   |
| 4  | Transfer declined by risk                    | transaction, orchestration, risk                       | Fails at Fraud Check; balance unchanged                                                    |
| 5  | Transfer with not enough money               | transaction, orchestration, ledger                     | Fails at Funds Check; balance unchanged                                                    |
| 6  | Double tap on Send                           | transaction                                            | One transfer only                                                                          |
| 7  | Teller reverses a transfer                   | transaction, orchestration, ledger, audit              | Balances return; statement shows four entries; chain still verifies                        |
| 8  | Notification consumer breaks, then is fixed  | notification, transaction                              | Record appears in the dead-letter list; admin replays it; notification arrives             |
| 9  | Admin runs end of day                        | batch, ledger, audit                                   | Six steps tick through; balances change; debits equal credits; seal shown                  |
| 10 | Transfer to another group's bank             | transaction, orchestration, ledger, partner            | Posted on our side; partner shows the credit                                               |
| 11 | Transfer from another group's bank           | transaction, ledger, notification                      | Our customer's balance rises and they get a notification                                   |
| 12 | Concurrent transfers on one account (JMeter) | ledger                                                 | No overdraft; no double spend                                                              |
