# Frontend Screens and Requirements

**Version:** 1.0 final, 8 October 2026. Code read at `dev` after PR 67.
**Covers:** the Flutter mobile banking app in `app/` (customers) and the React staff console in `frontend/` (tellers and admins).
**Purpose:** for every screen, say what the user must see, what they can do, which role may do it, and which endpoint backs it.

Paths, payloads, field names, enumerations and error codes come from [`FUTURE_INTEGRATED_API_CONTRACT.md`](../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md); endpoint IDs such as T1 or C3 refer to it. What is in and out of scope comes from section 0 of [`SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md`](../integrated-capstone-plans/SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md). If this file disagrees with either, they win and this file is corrected.

This file replaces sections 3 and 6 of [`TELLER_FRONTEND_SPECIFICATION_AND_PLAN.md`](../integrated-capstone-plans/TELLER_FRONTEND_SPECIFICATION_AND_PLAN.md), whose reversal blockers were closed by PRs 64 to 67.

Each screen carries a **Today** line with one of these words:

| Word | Meaning |
| --- | --- |
| **Live** | The screen exists and calls the real endpoint |
| **Local** | The screen exists but works on data held in the client; nothing reaches the backend |
| **Mock** | A placeholder with no behaviour |
| **Missing** | No screen exists |
| **Remove** | The screen exists and is out of scope |

---

## 1. Clients and roles

| Client | Path | Users | Role in the token | Sign-in endpoint |
| --- | --- | --- | --- | --- |
| Mobile banking app (Flutter, Android) | `app/` | Members | `ROLE_CUSTOMER` | A2 `POST /api/v1/auth/customers/login` |
| Staff console (React, browser) | `frontend/` | Tellers | `ROLE_TELLER` | A4 `POST /api/v1/auth/staff/login` |
| Staff console (React, browser) | `frontend/` | Admins | `ROLE_ADMIN` | A4 `POST /api/v1/auth/staff/login` |

- Every member and staff login is seeded. Nobody registers. Logins are listed in `database/seed/SEED_REFERENCE.md`: members such as `john_doe` and `juan_delacruz`, tellers `teller_alice` and `teller_bob`, and `admin`.
- Each sign-in endpoint accepts only its own user type. A member cannot sign in to the staff console with A4, and staff cannot sign in to the mobile app with A2.
- The console is frozen: it takes defect fixes only. Section 6 lists where this rule collides with the role matrix.
- Neither client holds a business rule. Hiding a button is a courtesy to the user, not security; the service enforces every rule and the client must handle the refusal.

---

## 2. Requirements for every screen

### 2.1 Data and formatting

| Item | Rule |
| --- | --- |
| Money | Shown as `₱49,625.00`. New endpoints send money as a string (`"2500.00"`); parse it as a decimal, never as a float. The amount a client sends to T1 is a string with two decimals |
| Time | The API sends ISO-8601 UTC. Show local time; keep the date visible on anything older than today |
| Account numbers | Shown exactly as returned, for example `ACC_10000001` and `LN_10000007` |
| Enumerations | Display labels may be friendly, but the client compares against the values in contract section 2.7 |
| References | Show `referenceNo` (`TX-20261003-8F3A21C0`) to the user. `transactionId` is the UUID used in paths |

### 2.2 Headers the client sends

| Header | When |
| --- | --- |
| `Authorization: Bearer <accessToken>` | Every call except sign-in and token refresh |
| `X-Correlation-ID` | Every call. One new UUID per user action |
| `Idempotency-Key` | T1 (transfer) and T5 (reversal). One UUID per press of the confirm button, reused on a retry of the same press and never reused for a new transfer |

### 2.3 States every screen handles

| State | What the user sees |
| --- | --- |
| Loading | A progress indicator in place of the data. Buttons that submit are disabled while a call is in flight |
| Empty | A plain sentence, for example "No transactions yet". Never sample rows |
| Validation error (400) | The message beside the field named in `invalidParams` |
| Access token expired (401) | The client calls A6 once and repeats the request. If the refresh fails, it returns to sign-in |
| Token revoked (401 `AUTH_TOKEN_REVOKED`) | Return to sign-in |
| Not allowed (403) | "You do not have access to this." The screen does not retry |
| Rate limited (429) | "Too many attempts. Wait a moment and try again." |
| Service down (503, 504, no network) | Mobile: the Service Unavailable screen with its 30-second cooldown (MOB-11). Console: a banner naming the failed action; the screen keeps the last real data and shows no invented data |
| Any other error | The `detail` text from the problem body. The client switches on `errorCode`, never on the text |

### 2.4 Simulated and sample data

Anything the client shows that did not come from the backend carries a visible label on that screen ("Simulated" or "Sample data"). This applies to the mobile fixture mode, the mobile KYC screen, and the console's offline fallback.

---

## 3. Mobile banking app (customers)

One role uses this app: the customer. No teller or admin screen belongs in it.

### 3.1 Navigation

```
Sign in (MOB-01)
  └─ Bottom bar
       ├─ Home (MOB-02)
       │    ├─ Account detail (MOB-03)
       │    ├─ Transfer history (MOB-06)    ── Transfer tracker (MOB-05)
       │    ├─ Scores and habits (MOB-08)
       │    └─ Beneficiaries (MOB-07)
       ├─ Transfer (MOB-04) ── Transfer tracker (MOB-05)
       ├─ Alerts (MOB-09)
       └─ Profile (MOB-10) ── KYC status (simulated) ── Log out
Service unavailable (MOB-11) can replace any screen
```

The header on every signed-in screen shows the CooBS logo and `@username`.

### MOB-01 Sign in

| | |
| --- | --- |
| **Sees** | Logo, "Welcome back", Username field, Password field (hidden text), Log in button |
| **Can do** | Enter credentials and sign in |
| **Endpoint** | A2. On success store `accessToken`, `refreshToken`, `userId` and `username` in secure storage and open Home |
| **Errors** | 401: "Incorrect username or password" (the same sentence whichever was wrong). 403: "This login is inactive. Visit a branch." 429: rate-limit message. A staff username is refused by A2 and gets the same sentence as a wrong password |
| **Must not show** | Create account, Forgot password, an MFA code step |
| **Today** | Sign-in is **Live**. Create account, the two-step registration and Forgot password are present and are **Remove** |

### MOB-02 Home

| | |
| --- | --- |
| **Sees** | A swipeable card per account the member owns: type, account number, status, available balance. A loan card shows outstanding principal, next due date and next due amount instead of a balance. Below the cards: the five most recent transactions (description, reference, signed amount, status) with See all; shortcuts to Scores and habits and to Beneficiaries; an unread count on the Alerts tab |
| **Can do** | Swipe between accounts. Hide or show balances. Tap an account to open MOB-03. Tap Transfer on a deposit account to open MOB-04 with that account selected. Tap a transaction to open MOB-05. Pull down to refresh |
| **Endpoints** | M1 `GET /api/v1/mobile/dashboard` for the greeting, primary account, recent transactions and unread count. K4 `GET /api/v1/accounts/my-accounts` (or the `accounts` array of C3) for the full list of accounts |
| **Rules** | Only accounts the server returns are shown. A `FROZEN` or `CLOSED` account shows its status and has no Transfer button. A loan card is read-only |
| **Today** | **Local**, partly Live. The first card takes its number and balance from M1; the second card, every transaction and the loan card are hard-coded. Deposit and Transfer change numbers on the device only. Manage Cards and "Reset loan (tester only)" are **Remove** |

### MOB-03 Account detail

| | |
| --- | --- |
| **Sees** | Deposit account: type, number, status, available balance, ledger balance, held amount. Loan account: outstanding principal, next due date, next due amount. The account's recent transfers |
| **Can do** | Open the full history filtered to this account (MOB-06). Start a transfer from this account |
| **Endpoints** | K4 `GET /api/v1/accounts/{accountId}`; C3 for `ledgerBalance`, `heldAmount` and the loan fields; T4 with `accountId` for the transfers |
| **Rules** | Another member's `accountId` returns 403 and the screen says the account is not available |
| **Must not show** | The audit statement or chain verification. The API allows them for the owner, but they are not in the Flutter scope list and stay in the console |
| **Today** | **Missing** |

### MOB-04 Transfer

| | |
| --- | --- |
| **Sees** | From: a picker of the member's own `ACTIVE` deposit accounts with available balance. To: an account number field, with a button to pick a saved beneficiary. Amount. Category picker (`TRANSFER`, `BILLS`, `LOAN_REPAYMENT`, `FEES`, `SAVINGS`, `OTHER`; default `TRANSFER`). Remarks (optional). A review step repeating From, To, Amount and Category before Send |
| **Can do** | Send a transfer to another CooBS account, including the member's own other account. After Send, go straight to the tracker (MOB-05) |
| **Endpoint** | T1 `POST /api/v1/transactions/transfers` with `Idempotency-Key`; M3 is the same intake through the BFF. The response is 202 with `transactionId`, `referenceNo` and `status: INITIATED` |
| **Client checks** | Amount greater than zero with at most two decimals; destination not empty and not the same as the source. These save a round trip; the server still decides |
| **Double tap** | Send is disabled after the first press. A retry after a timeout reuses the same `Idempotency-Key`, and the server returns the first result |
| **Errors at submit** | `INVALID_REQUEST_PARAMETERS` 400: field message. `ACCOUNT_NOT_OWNED` 403: "You can only send from your own account." `ACCOUNT_NOT_FOUND` 404: "We could not find that account number." `ACCOUNT_UNAVAILABLE` 503: "We could not check the account. Try again." `DUPLICATE_IDEMPOTENCY_KEY` 409: "This transfer is already being processed." |
| **Errors after submit** | A declined or failed transfer is not an error at T1. It arrives through the tracker as a `FAILED` status with a `failureMessage`: risk decline, daily limit, not enough money, ledger unavailable. A frozen or closed account (`ACCOUNT_NOT_ACTIVE`) can be refused at submit or fail in the tracker; handle both |
| **Other banks** | Hidden unless outbound inter-bank is switched on (see section 6). When it is: a Bank picker fed by configuration, `destinationBankCode` other than `COOBS`, and a required Beneficiary name |
| **Must not show** | A fixed list of commercial banks, a "Deposit" action (cash deposit is a counter transaction), QR payment |
| **Today** | **Local**. The sheet edits the on-device balance, offers BDO, BPI, UnionBank, Metrobank and Landbank, and never calls T1 |

### MOB-05 Transfer tracker

| | |
| --- | --- |
| **Sees** | Reference number, amount and overall status. Three steps, each with a title, a subtitle, a state (`COMPLETED`, `IN_PROGRESS`, `PENDING`, `FAILED`) and a time once reached: **Transfer Submitted**, **Security & Clearing**, **Transfer Completed**. On failure, the server's `failureMessage` in plain words |
| **Can do** | Watch the transfer finish. Go back to Home. Send another transfer |
| **Endpoint** | T3 `GET /api/v1/transactions/{transactionId}/timeline` (customer view), or M2 through the BFF by `trackingReference` |
| **Rules** | Poll every one to two seconds until the status is `POSTED`, `FAILED` or `REVERSED`, then stop. When the status becomes `POSTED`, refresh balances on Home. A `REVERSED` transfer says "This transfer was reversed" |
| **Must not show** | The eleven internal stages, stage names such as `FRAUD_CHECK`, risk scores, `failureCode`, or a switch to a staff view |
| **Today** | **Live** against M2 in the old three-tab shell, which sign-in no longer opens; unreachable from Home. The view has a "Customer (4)" and "Staff Audit (11)" switch: the customer view must have three steps, and the staff switch is **Remove** |

### MOB-06 Transfer history

| | |
| --- | --- |
| **Sees** | The member's transfers, newest first, grouped by date: direction, counterparty name and account number, reference, signed amount, category, status. A filter by account and by status |
| **Can do** | Scroll and load more (20 per page). Filter. Tap a row to open the tracker |
| **Endpoint** | T4 `GET /api/v1/transactions` with `accountId`, `status`, `from`, `to`, `page`, `size` |
| **Source** | `transaction_db` through `transaction-service`, and nothing else. The app never reads the ledger or the audit store for history |
| **What it covers** | Transfers only: sent and received, inter-bank, and reversals. Title the screen "Transfers", not "Statement". Cash deposits and withdrawals at the counter and end-of-day postings (interest, fees, loan instalments) go straight to the ledger and are not in this list; the member sees them in the balance and, for cash, as an alert. The full statement is the audit statement in the console (WEB-08) |
| **Today** | **Local**. The list is hard-coded. T4 is in the contract but has no controller in `transaction-service` yet (section 6) |

### MOB-07 Beneficiaries

| | |
| --- | --- |
| **Sees** | Saved beneficiaries: nickname, account number, bank code. An empty sentence when there are none |
| **Can do** | Add a beneficiary (nickname and account number; bank code defaults to `COOBS`). Remove one, after a confirmation. Tap one to start a transfer to it |
| **Endpoints** | C4 list, C5 add, C6 remove |
| **Errors** | An account number that does not exist at CooBS is refused by C5: "We could not find that account number." |
| **Must not show** | Relationship, nationality, address, date of birth or document upload. Those belong to a legal-heir record, which is not what a transfer beneficiary is |
| **Today** | **Local**, and the wrong shape: a three-step personal-details form that never calls C4 to C6 |

### MOB-08 Scores and habits

| | |
| --- | --- |
| **Sees** | Credit score as a number and gauge. Spending for the last 30 days by category, as amounts and a chart. Totals: deposits and loans |
| **Can do** | Read only |
| **Endpoint** | C3 `GET /api/v1/customers/{customerId}/360` with the member's own ID: `creditScore`, `spendingBreakdown`, `totals` |
| **Rules** | The score is seeded and fixed; the screen does not suggest it updates. `riskScore` is not sent to customers and is never shown. Another member's ID returns 403 |
| **Today** | **Local**. Score 832 and the monthly chart are hard-coded |

### MOB-09 Alerts

| | |
| --- | --- |
| **Sees** | In-app notifications, newest first: title, message, time, and a marker on unread ones. The tab shows the unread count |
| **Can do** | Open one, which marks it read. Pull to refresh. Load more |
| **Endpoints** | N1 list, N2 mark as read, N3 unread count |
| **Rules** | A transfer produces one alert for the sender and one for the recipient once it posts. There is no email, SMS or push |
| **Today** | **Local**. Three hard-coded rows; N1 to N3 are not called |

### MOB-10 Profile

| | |
| --- | --- |
| **Sees** | Display name, username, membership number, email. KYC status `VERIFIED` and tier. Personal details, read-only: name, address, mobile number, civil status, occupation |
| **Can do** | Open the KYC status screen. Log out |
| **Endpoints** | C1 `GET /api/v1/customers/me`. Log out calls A7, then clears secure storage and returns to sign-in whether or not A7 succeeded |
| **KYC status screen** | **Simulated** and labelled so. Shows the verified status and a mock upload control. Calls nothing. It must not tell a verified member that they are "Pending" |
| **Must not show** | Editing of personal details, a KYC update request, change password, a list of signed-in devices, session revocation per device |
| **Today** | Log out is **Live**. Personal details are hard-coded and editable with a Request update button that does nothing; Verify identity shows "Pending"; Security & tokens shows invented sessions and a Change password form. All three are **Remove** or replace with the read-only view |

### MOB-11 Service unavailable

| | |
| --- | --- |
| **Sees** | A plain message that the service cannot be reached, and a countdown until the next attempt |
| **Can do** | Wait, or retry when the countdown ends |
| **Rules** | Shown when the on-device circuit breaker is open (three consecutive timeouts or 5xx responses). Validation errors never open it |
| **Today** | **Live** |

### 3.2 Screens that must not exist in the mobile app

| Screen today | Why it goes |
| --- | --- |
| Create account, registration step 1 and 2 | Registration is OUT; members are seeded |
| Forgot password, Change password | Password reset and change are OUT |
| Loan application, loan approval preview, Pay in advance, My bill | Loans are SEEDED; there is no application, approval, disbursement or customer repayment endpoint |
| Pay QR tab | QR payment is OUT |
| Manage cards | Cards are OUT |
| Staff Audit (11) view in the tracker | Staff screens are OUT of the mobile app |
| KYC update request, account closure request | EXISTING in the console only; not extended to the app |

---

## 4. Staff console (tellers and admins)

### 4.1 Layout

One console, signed in through A4. The tabs shown depend on the role in the token.

| Tab | Teller | Admin |
| --- | --- | --- |
| Customer 360 (WEB-03) | Yes | Yes |
| Counter desk (WEB-04) | Yes | Cash only |
| Queues (WEB-05) | KYC: decide. Closures: view | KYC: decide. Closures: decide |
| Accounts (WEB-06) | Open, freeze, activate, add flag | The same, plus remove flag |
| Tracker and reversal (WEB-07) | Yes | Yes |
| Audit (WEB-08) | Yes | Yes |
| End of day (WEB-09) | View runs | Trigger and view runs |
| Dead letters (WEB-10) | — | Yes |

### WEB-01 Sign in

| | |
| --- | --- |
| **Sees** | Staff sign-in: username, password, Sign in |
| **Can do** | Sign in as a teller or an admin |
| **Endpoint** | A4. The role comes from `authData.roles`, never from the username |
| **Errors** | 401: one sentence for a wrong username or password. 403: inactive login. 429: rate limit. Gateway unreachable: "The gateway cannot be reached." and no session is created |
| **Today** | **Live**, with one defect: when the gateway is unreachable or answers 5xx, the console creates a session on its own and picks Admin if the username contains "admin" |

### WEB-02 Header

| | |
| --- | --- |
| **Sees** | CooBS brand, signed-in username, role badge (Branch Teller or System Admin), gateway status with latency, Log out |
| **Can do** | Log out (A7, then clear the session) |
| **Rules** | No notification bell for staff: N1 to N3 serve customers only, and a staff token is refused. The Switch Portal shortcut signs in with seeded credentials and is a demo aid only |
| **Today** | **Live**. The bell is shown to staff and fills with built-in sample rows because N1 refuses them |

### WEB-03 Customer 360

| | |
| --- | --- |
| **Sees** | After entering a customer ID: display name, membership number, KYC status and tier, credit score, **risk score**. Accounts table: ID, number, type, status, available balance, ledger balance, held amount; loans show outstanding principal and next due date and amount. Totals for deposits and loans. Recent transactions. Spending by category for 30 days. Saved beneficiaries. The "as of" time |
| **Endpoint** | C3 `GET /api/v1/customers/{customerId}/360`; C2 for the full profile |

| Action | Teller | Admin |
| --- | --- | --- |
| Look up any customer by ID | Yes | Yes |
| Jump to Counter desk with one of the customer's accounts selected | Yes | Yes |
| Jump to Accounts filtered to this customer | Yes | Yes |
| Edit any customer detail | — | — |

**Errors.** Unknown customer: "No customer with that ID." If the accounts list comes back empty for a member who has accounts, the panel says accounts are unavailable; it does not show a zero total.
**Today.** Teller: **Live**. Admin: **Missing**.

### WEB-04 Counter desk

| | |
| --- | --- |
| **Sees** | A mode switch: Cash deposit, Cash withdrawal, Assisted transfer. Cash modes: target account, amount, reference, remarks. Assisted transfer: source account, destination account number, amount, remarks. After a posting: the new balance and the reference |
| **Endpoints** | Cash deposit L-S3 `POST /api/v1/ledger/credits`; cash withdrawal L-S2 `POST /api/v1/ledger/debits`; assisted transfer T1 with `Idempotency-Key` |

| Action | Teller | Admin |
| --- | --- | --- |
| Take a cash deposit | Yes | Yes |
| Pay a cash withdrawal | Yes | Yes |
| Send a transfer from any customer's account | Yes | — |
| Open the tracker for the transfer just sent | Yes | — |

**Rules.** An assisted transfer goes through T1, not the direct ledger transfer, so that it has a timeline. Staff may send from any account; the server records the teller as the actor.
**Errors.** `INSUFFICIENT_FUNDS` 422 on a withdrawal larger than the balance. `ACCOUNT_NOT_ACTIVE` 422 on a frozen or closed account. `ACCOUNT_NOT_FOUND` 404. `DUPLICATE_IDEMPOTENCY_KEY` and `CONCURRENT_TRANSACTION_IN_FLIGHT` 409: "This posting is already being processed."
**Today.** Teller: **Live**. Admin: **Missing**.

### WEB-05 Queues

**KYC update requests.** Sees pending requests: request ID, customer, the requested changes, submitted time. Endpoint C10.
**Closure requests.** Sees pending requests: request ID, account number, customer, available balance, reason, requested time, status. Endpoint `GET /api/v1/accounts/closure-requests?status=PENDING` (K5).

| Action | Endpoint | Teller | Admin |
| --- | --- | --- | --- |
| View pending KYC update requests | C10 | Yes | Yes |
| Approve a KYC update | C11 | Yes | Yes |
| Reject a KYC update, with a required reason | C12 | Yes | Yes |
| View pending closure requests | K5 | Yes | Yes |
| Approve a closure | K5 | — | Yes |
| Reject a closure, with a required reason | K5 | — | Yes |

**Rules.** A teller sees the closure list without Approve or Reject buttons. A decided request leaves the list. Approving a closure sets the account to `CLOSED`.
**Errors.** `ACCOUNT_NON_ZERO_BALANCE` 400 and `ACCOUNT_ACTIVE_FLAG_EXISTS` 422 when a closure can no longer be approved.
**Must not show.** A "High-Value Transfer Dual-Control Queue". Manual review of a transfer is OUT and no endpoint backs it.
**Today.** KYC queue: Teller **Live**, Admin **Missing**. Closure list for tellers: **Live**. Closure decisions for admins: **Live**. The dual-control queue exists on sample data and is **Remove**.

### WEB-06 Accounts

| | |
| --- | --- |
| **Sees** | An Open account form: customer ID, account type (`SAVINGS`, `CHECKING`, `CURRENT`, `LOAN`), currency fixed at `PHP`. A directory of all accounts: ID, number, customer name, type, available balance, status, active flags. An active-flags list: flag ID, account, reason, flagged date |
| **Endpoints** | K3 open; K4 `GET /api/v1/accounts` list; K5 `PATCH /api/v1/accounts/{accountId}/status`, and `POST`, `GET`, `DELETE` on `/api/v1/accounts/{accountId}/flags` |

| Action | Teller | Admin |
| --- | --- | --- |
| Open an account for a customer | Yes | Yes |
| View all accounts and their flags | Yes | Yes |
| Freeze an `ACTIVE` account | Yes | Yes |
| Activate a `FROZEN` account | Yes | Yes |
| Add a flag, with a required reason | Yes | Yes |
| Remove a flag | — | Yes |

**Rules.** A new account opens with a zero balance; money arrives through a cash deposit. A frozen account refuses new transfers in either direction. A flagged account cannot be closed. A teller sees no Remove button.
**Errors.** Opening for a customer whose KYC is not verified is refused. If the ledger is down, opening fails as a whole and the screen says no account was created.
**Today.** Teller: **Live**, with one defect: the type list offers `PAYROLL` and `TIME_DEPOSIT`, which are not account types, omits `CURRENT` and `LOAN`, and has an initial-deposit field that K3 does not take. Admin: add flag, remove flag, activate and the directory are **Live**; open account and freeze are **Missing**.

### WEB-07 Tracker and reversal

| | |
| --- | --- |
| **Sees** | A search by reference or transaction ID. A summary: reference, status, channel, source and destination accounts, amount, created and completed times, and `failureCode` with `failureReason` when failed. Eleven rows, always all eleven: number, stage name, emitting service, outcome (`PASSED`, `FAILED`, `PENDING`), time, detail. A stage not reached is `PENDING` with no time |
| **Endpoints** | T2 for the summary; T3 for the timeline (staff view); T5 `POST /api/v1/transactions/{transactionId}/reversal` with `Idempotency-Key` and `{ "reason": "…" }` |

The eleven stages, in order: `INITIATED`, `VALIDATED`, `AUTHENTICATED`, `FRAUD_CHECK`, `LIMIT_CHECK`, `FUNDS_CHECK`, `AUTHORIZED`, `POSTED`, `LEDGER_UPDATE`, `NOTIFICATION`, `RECONCILIATION`.

| Action | Teller | Admin |
| --- | --- | --- |
| Look up any transaction and see all eleven stages | Yes | Yes |
| Turn live polling on or off (every three seconds, stopping at a final status) | Yes | Yes |
| Reverse a `POSTED` transfer, with a required reason | Yes | Yes |
| Reverse part of a transfer | — | — |
| Push a stuck transfer forward by hand | — | — |

**Rules.** The Reverse button is enabled only when the status is `POSTED` and `reversedByTransactionId` is empty. A reversal is a new transaction with an `RV-` reference; after submitting, the tracker switches to it. When it posts, the original shows `REVERSED`. A transfer that stays `PROCESSING` (ledger unreachable after the hold, or a silent partner bank) remains visible here with its last stage; staff cannot change it.
**Errors.** `ALREADY_REVERSED` 409: "This transfer has already been reversed." `INSUFFICIENT_FUNDS` 422: "The recipient no longer has the funds; the reversal was not posted." `TRANSACTION_NOT_FOUND` 404. A transfer that is not posted is refused.
**Today.** Teller: **Live**. Admin: **Missing**.

### WEB-08 Audit

| | |
| --- | --- |
| **Sees** | After choosing an account: the statement, oldest first: audit ID, reference, type (`DEBIT`, `CREDIT`, `REVERSAL_DEBIT`, `REVERSAL_CREDIT`), amount, old and new balance, previous and current hash, actor ID, client IP, time. A chain result: intact or broken, records verified, latest hash, verified time, message. The daily seal for a business date: seal hash, last audit ID, sealed time, batch run |
| **Endpoints** | U1 statement; U2 one transaction's record; U3 verify chain; U4 `GET /api/v1/audit/seals/{businessDate}` |

| Action | Teller | Admin |
| --- | --- | --- |
| View any account's statement | Yes | Yes |
| View one transaction's audit record | Yes | Yes |
| Verify an account's chain | Yes | Yes |
| Read a day's seal | Yes | Yes |
| Change or delete an audit row | — | — |

**Rules.** After a reversal the statement shows the two original rows and the two reversal rows; nothing is removed. A seal for a day with no completed batch returns 404 and the screen says "No seal for this date".
**Today.** Statement and chain verification: **Live** for both roles. Daily seal: **Missing**. The teller's audit view has "simulate tampering" and "restore chain" buttons that edit rows in the browser only; they need a "Simulated" label or removal.

### WEB-09 End of day

| | |
| --- | --- |
| **Sees** | A list of runs: run ID, business date, status (`RUNNING`, `COMPLETED`, `HALTED`, `FAILED`), triggered by, started, finished. For the selected run, six rows: step number, step name, status (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`), items processed, total amount, duration, message |
| **Endpoints** | B1 `POST /api/v1/batch/eod/trigger`; B2 one run; B3 list |

The six steps, in order: `INTEREST_ACCRUAL`, `SERVICE_FEES`, `STANDING_INSTRUCTIONS`, `LOAN_AMORTIZATION`, `GL_RECONCILIATION`, `AUDIT_SEAL`.

| Action | Teller | Admin |
| --- | --- | --- |
| View runs and their steps | Yes | Yes |
| Trigger end of day, for today or a chosen business date, after a confirmation | — | Yes |
| Re-run a single step, or schedule a run | — | — |

**Rules.** After a trigger the screen polls B2 until the run leaves `RUNNING`. A `HALTED` run shows step 5 `FAILED` and step 6 `SKIPPED` with the variance message. The screen does not say runs are scheduled; there is no scheduler.
**Errors.** `BATCH_ALREADY_RUNNING` 409: "A run is already in progress." `LEDGER_UNAVAILABLE` 503: the run is `FAILED` and can be triggered again. A teller token on B1 is refused with 403.
**Today.** Teller read-only monitor: **Live**, with wording that says jobs are "scheduled". Admin view and trigger: **Missing**.

### WEB-10 Dead letters

| | |
| --- | --- |
| **Sees** | Records that failed three times: record ID, dead-letter topic, original topic, key, error message, attempts, failed time |
| **Endpoints** | T6 list, T7 replay |

| Action | Teller | Admin |
| --- | --- | --- |
| View dead-letter records | — | Yes |
| Replay one record to its original topic | — | Yes |

**Rules.** After a successful replay the row shows "Replayed" with the time and the list refreshes.
**Today.** **Missing**.

### 4.2 Customer workspace in the console (existing, not extended)

The console also signs members in through A2 and shows them a workspace. It is kept because it is the only place a member can submit a KYC update or a closure request; it is not a demo focus and gains nothing new.

| Tab | Sees | Can do | Endpoint | Today |
| --- | --- | --- | --- | --- |
| Accounts | Own accounts and balances | Refresh | K4 `my-accounts` | **Live** |
| Transfer | A transfer form | Send a transfer | Must be T1 | **Defect**: it posts to L-S4 `/api/v1/ledger/transfers`, which is staff-only, so a member gets 403 |
| Profile | Own profile and KYC details | Submit a KYC update request | C1, C9 | **Live** |
| Closure | Own accounts and past closure requests | Request closure of an account, with a reason | K5 | **Live** |

---

## 5. Who can do what, by screen

| Action | Screen | Customer | Teller | Admin |
| --- | --- | --- | --- | --- |
| Sign in, stay signed in, log out | MOB-01, WEB-01 | Yes | Yes | Yes |
| View own accounts, balances and loan summary | MOB-02, MOB-03 | Yes | — | — |
| View own profile, credit score and spending | MOB-08, MOB-10 | Yes | — | — |
| Send a transfer from own account | MOB-04 | Yes | — | — |
| Track own transfer in three steps | MOB-05 | Yes | — | — |
| View own transfer history | MOB-06 | Yes | — | — |
| List, add and remove own beneficiaries | MOB-07 | Yes | — | — |
| Read own notifications | MOB-09 | Yes | — | — |
| Submit a KYC update request; request account closure | Console, 4.2 | Yes | — | — |
| View any customer's 360, including risk score | WEB-03 | — | Yes | Yes |
| Cash deposit and withdrawal | WEB-04 | — | Yes | Yes |
| Send a transfer on a customer's behalf | WEB-04 | — | Yes | — |
| Approve or reject a KYC update | WEB-05 | — | Yes | Yes |
| View closure requests | WEB-05 | — | Yes | Yes |
| Approve or reject a closure | WEB-05 | — | — | Yes |
| Open an account; freeze or activate; add a flag | WEB-06 | — | Yes | Yes |
| Remove a flag | WEB-06 | — | — | Yes |
| Track any transfer in eleven stages | WEB-07 | — | Yes | Yes |
| Reverse a posted transfer | WEB-07 | — | Yes | Yes |
| View any statement, verify a chain, read a seal | WEB-08 | — | Yes | Yes |
| View end-of-day runs | WEB-09 | — | Yes | Yes |
| Trigger end of day | WEB-09 | — | — | Yes |
| View and replay dead-letter records | WEB-10 | — | — | Yes |

Nobody, in either client: register, reset or change a password, complete MFA, review a flagged transfer by hand, reverse part of a transfer, apply for a loan, schedule a batch, or pay by card or QR.

---

## 6. Gaps and decisions

### 6.1 What stands between the code and this specification

| # | Gap | Client | Size |
| --- | --- | --- | --- |
| 1 | Home, Transfer, Transfer history, Beneficiaries, Scores, Alerts and Profile run on on-device data. Each needs wiring to its endpoint (plan task 2.3) | Mobile | Large |
| 2 | Out-of-scope screens are reachable: registration, forgot and change password, loan application and repayment, Pay QR, Manage cards | Mobile | Small, deletions |
| 3 | The tracker is not reachable from the shell that sign-in opens, labels the customer view as four stages, and offers a staff view | Mobile | Small |
| 4 | The app defaults to `USE_FIXTURES=true`; the demo build needs `false` | Mobile | Trivial |
| 5 | Admins have three tabs. Customer 360, KYC decisions, cash desk, open account, freeze, the tracker, reversal, the batch view and trigger, the seal and dead letters have no admin screen | Console | Medium |
| 6 | The console invents a session and data when the gateway is down, with no label | Console | Small |
| 7 | Open-account type list and initial-deposit field do not match K3 | Console | Trivial |
| 8 | The dual-control queue and the tamper buttons are simulations without a label | Console | Trivial |
| 9 | The member's transfer tab in the console calls the staff-only ledger path | Console | Small |
| 10 | The notification bell is shown to staff | Console | Trivial |

### 6.2 Needed from a backend owner

| Need | Service | Why |
| --- | --- | --- |
| T4 `GET /api/v1/transactions` | transaction-service | MOB-06 has nothing to call; only the internal T8 list exists |
| Received transfers in T4, T8 and the M1 recent list | transaction-service | History is looked up by the sender's customer ID and every row is marked `OUT`, so a member never sees a transfer they received |
| End-of-day steps that post through the ledger (plan task 3.1) | batch-service, ledger-service | Until then WEB-09 shows steps that change no balance and must be labelled Simulated |
| Outbound inter-bank (plan task 3.5) | transaction-service, orchestration-service | T1 rejects any bank code other than `COOBS` today, so MOB-04 hides the bank picker |

### 6.3 Decisions

Each item was checked against section 0 of the scope document. None of the capabilities is OUT; what was out of scope is noted per item.

| # | Question | Scope check | Decision | What it requires |
| --- | --- | --- | --- | --- |
| 1 | Where do the admin screens live? | The end-of-day trigger "from a button", dead-letter replay, reversal, the eleven-stage tracker and the daily seal are all BUILT. Staff back-office screens are OUT for Flutter (section 0.2), which contradicts the "batch screen" in the same row and section 13. Manual intervention screens are OUT everywhere | Every staff screen is in the console. Nothing for staff is built in Flutter. The console freeze gets one exception: what the role matrix already grants an admin | Console: show admins the teller tabs (WEB-03 to WEB-08, without the assisted transfer); add the trigger button to WEB-09, the seal to WEB-08, and WEB-10. Scope document: remove "batch screen" from the Flutter row, correct section 13, and record the exception in the React row |
| 2 | Which dead-letter path? | Dead-letter queue and admin replay are BUILT | The code stays. The contract moves T6 and T7 to `/api/v1/notifications/admin/dlq` (plan task 3.2) | A contract edit with its example files, then WEB-10 against that path. WEB-10 uses the single-record replay only; the bulk `/replay` in the code is not in the contract and gets no button |
| 3 | May an admin send a transfer? | Section 1 and T1: customers and tellers only | No. The console shows the assisted transfer to tellers only | No client work beyond hiding it. `transaction-service` should refuse an admin token at T1; low priority, since the console never offers it |
| 4 | Customers on U2 and U4? | Section 12: tellers and admins only. Customers have U1 and U3 for their own accounts | No mobile screen uses any audit endpoint; audit is not in the Flutter scope list | `audit-service` should drop `CUSTOMER` from U2 and U4, and the `AUDITOR` role, which does not exist. It belongs with the audit ownership hardening in plan task 3.3 |
| 5 | Bottom bar: Transfer or History? | QR payment is OUT, so the Pay QR tab goes either way | Transfer. It is the action the demo is built around; Transfer history is one tap from Home | Replace the tab in the mobile shell |

**Minimum for the demo.** Scenarios 8 and 9 need only the trigger button, the seal and WEB-10. Showing admins the teller tabs is reuse and can follow.
