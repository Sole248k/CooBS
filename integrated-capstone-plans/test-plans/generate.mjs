// Writes one CSV per test suite for Azure Test Plans ("Import test cases from CSV/XLSX").
// Source for the cases: SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md and postman/e2e.
// Run:  node integrated-capstone-plans/test-plans/generate.mjs
// Edit AREA / ASSIGNED here, not in the CSVs. ASSIGNED must be a user in the organisation, or empty.
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert';

const AREA = 'CooBS';
const ASSIGNED = '';
const STATE = 'Design';

const SEED = ['Precondition: the stack is up and the data is reset to the seed (scripts/reset_demo_data.ps1, or -ResetData on the deploy scripts)', 'GET /actuator/health on the gateway answers UP'];
const CUST = ['Sign in as customer john_doe with the seeded demo password (POST /api/v1/auth/customers/login)', 'HTTP 200 with an access token and a refresh token'];
const MARIA = ['Sign in as customer maria_santos (POST /api/v1/auth/customers/login)', 'HTTP 200 with tokens'];
const TELLER = ['Sign in as teller_alice (POST /api/v1/auth/staff/login)', 'HTTP 200 with tokens'];
const ADMIN = ['Sign in as admin (POST /api/v1/auth/staff/login)', 'HTTP 200 with tokens'];
const SEND = ['As john_doe, POST /api/v1/transactions/transfers with a new Idempotency-Key: sourceAccountId 1, destinationAccountNumber ACC_10000002, destinationBankCode COOBS, amount "2500.00", currency PHP', 'HTTP 202 with a transactionId and status INITIATED'];
const POSTED = ['Poll GET /api/v1/transactions/{transactionId} until the status is final', 'Status is POSTED'];

const suites = {
  '01_functional': ['FUN', 'functional', [
    // api-gateway
    ['gateway: a customer path is routed to customer-service', 2, 'gateway', [CUST, ['GET /api/v1/customers/me through port 8080', 'HTTP 200 with the profile served by customer-service']]],
    ['gateway: adds X-Correlation-ID when the caller sends none', 2, 'gateway', [['Call any public endpoint without an X-Correlation-ID header', 'The request succeeds and an ID is generated'], ['Search the gateway and service logs for that request', 'The same correlation ID appears in every service that handled it (scripts/test_correlation_propagation.ps1)'], ['Repeat with your own X-Correlation-ID value', 'Your value is kept, not replaced']]],
    // auth-service
    ['auth: customer signs in with correct credentials (A2)', 1, 'auth', [CUST, ['GET /api/v1/auth/token/me with the access token', 'userId is the customer ID and the role is the customer role']]],
    ['auth: teller and admin sign in on the staff endpoint (A4)', 1, 'auth', [TELLER, ADMIN, ['GET /api/v1/auth/token/me with each token', 'userId is the staff ID and the role matches the user']]],
    ['auth: wrong password is refused', 1, 'auth', [['POST /api/v1/auth/customers/login as john_doe with a wrong password', 'HTTP 401 in the standard error shape'], ['Repeat with a username that does not exist', 'HTTP 401 with the same body, so nothing reveals whether the username exists']]],
    ['auth: an inactive login cannot sign in', 2, 'auth', [['Precondition: one login in identity_db is inactive', 'The login row shows the inactive status'], ['Sign in with that login and its correct password', 'HTTP 403']]],
    ['auth: refresh returns a new access token (A6)', 1, 'auth', [CUST, ['Call the refresh endpoint with the refresh token', 'HTTP 200 with a new access token'], ['GET /api/v1/auth/token/me with the new token', 'HTTP 200']]],
    ['auth: sign out revokes the token (A7)', 1, 'auth', [CUST, ['POST /api/v1/auth/token/revoke with the access token', 'HTTP 204'], ['GET /api/v1/auth/token/me with the same token', 'HTTP 401']]],
    // customer-service
    ['customer: customer views own profile (C1)', 1, 'customer', [CUST, ['GET /api/v1/customers/me', 'HTTP 200 with the member profile and KYC details of john_doe']]],
    ['customer: staff views a customer profile (C2)', 2, 'customer', [TELLER, ['Request the profile of customer 1 (C2)', 'HTTP 200 with that customer profile']]],
    ['customer: customer opens own Customer 360 (C3)', 1, 'customer', [CUST, ['GET /api/v1/customers/{own id}/360', 'HTTP 200 with accounts and balances, recent transactions and spending, scores and beneficiaries']]],
    ['customer: staff Customer 360 includes the risk score', 2, 'customer', [TELLER, ['GET /api/v1/customers/1/360', 'HTTP 200 with the same view as the customer plus the risk score']]],
    ['customer: add, list and remove a beneficiary at our bank (C4, C5, C6)', 1, 'customer', [CUST, ['POST /api/v1/customers/me/beneficiaries with accountNumber ACC_10000002 and bankCode COOBS', 'HTTP 201; the beneficiary is returned with an ID'], ['GET /api/v1/customers/me/beneficiaries', 'The new beneficiary is in the list'], ['Remove it (C6) and list again', 'It is no longer in the list']]],
    ['customer: a beneficiary with an unknown account number is rejected', 2, 'customer', [CUST, ['POST /api/v1/customers/me/beneficiaries with bankCode COOBS and an account number that does not exist', 'Rejected with an error code; nothing is stored']]],
    ['customer: a beneficiary at another bank is stored with its bank code', 2, 'customer', [CUST, ['POST /api/v1/customers/me/beneficiaries with bankCode BANKB and accountNumber 1000-2000-3000', 'Stored with bank code BANKB']]],
    ['customer: KYC update request is stored as PENDING and staff approve it', 1, 'customer;kyc', [CUST, ['POST /api/v1/customers/kyc/update-request with a new address and occupation', 'HTTP 202; the request is PENDING'], TELLER, ['GET /api/v1/customers/kyc/update-requests?status=PENDING', 'The request is listed'], ['POST /api/v1/customers/kyc/update-requests/{id}/approve', 'HTTP 200; the request is approved'], ['As the customer, GET /api/v1/customers/me', 'The profile shows the new address and occupation']]],
    ['customer: staff reject a KYC update request (C12)', 2, 'customer;kyc', [CUST, ['Submit a KYC update request', 'The request is PENDING'], TELLER, ['Reject the request (C12)', 'The request is rejected'], ['As the customer, GET /api/v1/customers/me', 'The profile is unchanged']]],
    // account-service
    ['account: teller opens a savings account (K3)', 1, 'account', [TELLER, ['POST /api/v1/accounts for a customer with verified KYC, accountType SAVINGS', 'The account is created and is ACTIVE'], ['Read the balance of the new account', 'Balance is 0.00']]],
    ['account: opening is refused when the customer KYC is not verified', 2, 'account', [TELLER, ['POST /api/v1/accounts for a customer whose KYC is not verified', 'Rejected with an error code; no account is created']]],
    ['account: teller opens a loan account', 3, 'account', [TELLER, ['POST /api/v1/accounts with accountType LOAN', 'The account is created with type LOAN']]],
    ['account: customer lists only own accounts', 1, 'account', [CUST, ['GET /api/v1/accounts/my-accounts', 'Only accounts of john_doe, each with a balance read from the ledger']]],
    ['account: teller lists all accounts (K4)', 2, 'account', [TELLER, ['List all accounts (K4)', 'HTTP 200 with accounts of more than one customer']]],
    ['account: a frozen account cannot send or receive a transfer', 1, 'account;transaction', [TELLER, ['Change the status of account 2 to FROZEN (K5)', 'Status is FROZEN'], SEND, ['Poll the transaction and open its timeline', 'FAILED with ACCOUNT_NOT_ACTIVE at validation; no balance changed'], ['Set account 2 back to ACTIVE', 'Status is ACTIVE']]],
    ['account: a flag blocks closure until an admin removes it', 2, 'account', [TELLER, ['Add a flag to a customer account (K5)', 'The flag is active'], ['As the customer, request closure of that account', 'Rejected with ACCOUNT_ACTIVE_FLAG_EXISTS'], ADMIN, ['Remove the flag', 'The flag is no longer active']]],
    ['account: closure is refused while money is in the account', 1, 'account', [CUST, ['Request closure of account 1, which has a balance', 'Rejected with ACCOUNT_NON_ZERO_BALANCE']]],
    ['account: admin approves a closure request', 2, 'account', [['Precondition: a customer has an ACTIVE account with a 0.00 balance and no flag (open one as the teller)', 'The account exists with balance 0.00'], ['As that customer, request closure', 'A closure request is created'], ADMIN, ['Approve the closure request', 'The account status is CLOSED']]],
    ['account: admin rejects a closure request', 3, 'account', [['Precondition: a pending closure request on a zero-balance account', 'The request is listed for the admin'], ADMIN, ['Reject the request with a reason', 'The request is rejected and the account stays ACTIVE']]],
    // transaction-service
    ['transaction: a valid transfer is accepted and posts (T1, T2)', 1, 'transaction', [SEED, CUST, SEND, POSTED, ['GET /api/v1/accounts/1 and, as maria_santos, her account', 'Sender is lower by exactly 2500.00 and receiver higher by exactly 2500.00']]],
    ['transaction: a transfer without Idempotency-Key is refused', 1, 'transaction', [CUST, ['POST /api/v1/transactions/transfers with a valid body and no Idempotency-Key header', 'HTTP 400 in the standard error shape; no transfer is created']]],
    ['transaction: the same request sent twice creates one transfer', 1, 'transaction', [CUST, SEND, ['Send the identical request again with the same Idempotency-Key', 'HTTP 200 with the same transactionId as the first call'], ['List the transactions of john_doe (T4)', 'Exactly one transfer for that key; the balance moved once']]],
    ['transaction: a source account the caller does not own is refused', 1, 'transaction', [CUST, ['POST a transfer with sourceAccountId 2, which belongs to maria_santos', 'The transfer ends FAILED with ACCOUNT_NOT_OWNED at stage 3; no balance changed']]],
    ['transaction: an unknown destination account number fails', 2, 'transaction', [CUST, ['POST a transfer to a destinationAccountNumber that does not exist at COOBS', 'The transfer ends FAILED with ACCOUNT_NOT_FOUND at stage 2']]],
    ['transaction: zero, negative and malformed amounts are refused', 1, 'transaction', [CUST, ['POST a transfer with amount "0.00"', 'HTTP 400 naming the amount field'], ['POST a transfer with amount "-10.00"', 'HTTP 400 naming the amount field'], ['POST a transfer with amount "abc"', 'HTTP 400 naming the amount field']]],
    ['transaction: source and destination cannot be the same account', 2, 'transaction', [CUST, ['POST a transfer from account 1 to ACC_10000001', 'HTTP 400']]],
    ['transaction: a transfer to a bank that is not a partner is refused', 2, 'transaction;interbank', [['Sign in as david_kim', 'HTTP 200 with tokens'], ['POST a transfer from account 3 with destinationBankCode BPI', 'HTTP 422']]],
    ['transaction: the customer tracker shows three stages (T3)', 1, 'transaction', [CUST, SEND, POSTED, ['GET /api/v1/transactions/{transactionId}/timeline as the customer', 'Three stages: Initiated, Processing, Posted']]],
    ['transaction: the staff tracker shows eleven stages (T3)', 1, 'transaction', [['Precondition: a POSTED transfer exists', 'Its transactionId is known'], TELLER, ['GET /api/v1/transactions/{transactionId}/timeline as the teller', 'Eleven stages, each with a time, in order']]],
    ['transaction: customer lists own transactions only (T4)', 2, 'transaction', [CUST, ['List transactions (T4)', 'Only transactions where john_doe is the sender or the receiver']]],
    ['transaction: an unknown transaction ID answers 404', 3, 'transaction', [CUST, ['GET /api/v1/transactions/00000000-0000-0000-0000-000000000000', 'HTTP 404 in the standard error shape']]],
    ['transaction: teller reverses a posted transfer (T5)', 1, 'transaction;reversal', [['Precondition: a POSTED transfer of john_doe exists', 'Its transactionId and both balances are noted'], TELLER, ['POST /api/v1/transactions/{transactionId}/reversal with a reason', 'Accepted; a new reversal transaction is created'], ['Poll the original transaction', 'Status is REVERSED'], ['Read both balances', 'Both are back to the values before the transfer']]],
    ['transaction: a transfer that is not posted, or already reversed, cannot be reversed', 2, 'transaction;reversal', [TELLER, ['Request a reversal of a FAILED transfer', 'Rejected'], ['Request a reversal of a transfer that is already REVERSED', 'Rejected']]],
    // risk-engine, seen through transfers
    ['risk: a transfer by a high-risk member is declined', 1, 'risk', [['Sign in as andrea_reyes and note the balance of account 7', 'Balance noted'], ['POST a transfer from account 7', 'HTTP 202'], ['Poll the transaction and open the staff timeline', 'FAILED at FRAUD_CHECK with HIGH_RISK'], ['Read the balance of account 7', 'Unchanged']]],
    ['risk: a transfer by a review-band member is declined', 2, 'risk', [['Sign in as jose_bautista', 'HTTP 200 with tokens'], ['POST a transfer from his account', 'HTTP 202'], ['Poll the transaction and open the staff timeline', 'FAILED at FRAUD_CHECK with REVIEW_REQUIRED']]],
    ['risk: a transfer over the daily limit is declined', 1, 'risk', [MARIA, ['Note the balance of account 2, then POST a transfer above her remaining daily limit', 'HTTP 202'], ['Poll the transaction and open the staff timeline', 'FAILED at LIMIT_CHECK with LIMIT_EXCEEDED'], ['Read the balance of account 2', 'Unchanged']]],
    // ledger-service
    ['ledger: staff read a balance with ledger and held amounts', 2, 'ledger', [TELLER, ['GET /api/v1/ledger/accounts/1/balance', 'HTTP 200 with the available balance, ledgerBalance and heldAmount as money strings']]],
    ['ledger: teller takes a cash deposit', 1, 'ledger', [TELLER, ['Note the balance of account 1, then POST /api/v1/ledger/credits for "1000.00"', 'Accepted'], ['Read the balance again', 'Higher by exactly 1000.00'], ['Open the audit statement of account 1', 'One credit to the customer against the cash GL account']]],
    ['ledger: teller takes a cash withdrawal', 1, 'ledger', [TELLER, ['Note the balance of account 1, then POST /api/v1/ledger/debits for "500.00"', 'Accepted'], ['Read the balance again', 'Lower by exactly 500.00']]],
    ['ledger: a withdrawal larger than the balance is refused', 1, 'ledger', [TELLER, ['POST /api/v1/ledger/debits on account 9 for more than its balance', 'Refused with INSUFFICIENT_FUNDS'], ['Read the balance', 'Unchanged']]],
    ['ledger: teller makes a counter transfer', 2, 'ledger', [TELLER, ['POST /api/v1/ledger/transfers from account 1 to account 2 for "100.00" with a new referenceNo', 'Accepted'], ['Read both balances', 'Account 1 lower and account 2 higher by exactly 100.00']]],
    ['ledger: a transfer without enough money fails at the funds check', 1, 'ledger;transaction', [['Sign in as liza_mercado and note the balance of account 9', 'Balance noted'], ['POST a transfer from account 9 for more than the balance', 'HTTP 202'], ['Poll the transaction and open the staff timeline', 'FAILED at FUNDS_CHECK with INSUFFICIENT_FUNDS'], ['Read the balance of account 9', 'Unchanged']]],
    // batch-service
    ['batch: admin triggers end of day and six steps complete (B1, B2)', 1, 'batch', [ADMIN, ['POST /api/v1/batch/eod/trigger', 'Accepted with a run ID'], ['Poll GET /api/v1/batch/eod/runs/{runId}', 'Run is COMPLETED with six steps in order, each reported']]],
    ['batch: teller views batch runs (B3)', 3, 'batch', [TELLER, ['GET /api/v1/batch/eod/runs', 'HTTP 200 with the list of runs']]],
    ['batch: a second run for the same business date posts nothing new', 1, 'batch', [['Precondition: a COMPLETED run exists for today; note the balance of one savings account', 'Balance noted'], ADMIN, ['Trigger end of day again and wait for it to finish', 'The run finishes'], ['Read the same balance', 'Unchanged: no second interest or fee posting']]],
    ['batch: a trigger while a run is in progress is refused', 2, 'batch', [ADMIN, ['Trigger end of day, then trigger again at once', 'The second call is refused with BATCH_ALREADY_RUNNING']]],
    // notification-service
    ['notification: customer reads the inbox, the unread count and marks one read (N1, N2, N3)', 2, 'notification', [CUST, ['GET /api/v1/notifications/my-notifications', 'HTTP 200 with own notifications only'], ['GET /api/v1/notifications/unread-count', 'A number'], ['Mark one notification as read (N2), then read the count again', 'The count is lower by one']]],
    ['notification: a posted transfer notifies the sender and the receiver', 1, 'notification', [CUST, SEND, POSTED, ['Read the inbox of john_doe and of maria_santos', 'Each has exactly one new notification for this transfer']]],
    ['notification: a failed transfer notifies the sender only', 2, 'notification', [['As liza_mercado, send a transfer that fails for insufficient funds', 'The transfer is FAILED'], ['Read her inbox and the inbox of the intended receiver', 'One new notification for the sender; none for the receiver']]],
    // audit-service
    ['audit: an untouched chain verifies (U3)', 1, 'audit', [CUST, ['GET /api/v1/audit/verify-chain/1', 'isChainIntact is true, with the number of rows checked']]],
    ['audit: account statement lists the postings (U1)', 2, 'audit', [CUST, ['GET /api/v1/audit/accounts/1/statement', 'HTTP 200 with every posting on the account, in order']]],
    ['audit: staff read one transaction audit record (U2)', 3, 'audit', [TELLER, ['GET /api/v1/audit/transactions/{transactionId} for a POSTED transfer', 'HTTP 200 with the debit and credit rows']]],
    ['audit: the statement after a reversal shows four entries', 1, 'audit;reversal', [['Precondition: a transfer was posted and then reversed', 'The original is REVERSED'], ['GET /api/v1/audit/accounts/1/statement', 'The two original entries and the two reversal entries; nothing removed'], ['GET /api/v1/audit/verify-chain/1', 'isChainIntact is true']]],
    ['audit: the daily seal exists after a completed batch and not before (U4)', 2, 'audit;batch', [TELLER, ['GET /api/v1/audit/seals/{date} for a date with no completed batch', 'HTTP 404'], ['GET /api/v1/audit/seals/{date} for the date of a COMPLETED run', 'HTTP 200 with the closing hash']]],
  ]],

  '02_integration': ['INT', 'integration', [
    ['transfer saga: transaction, orchestration, risk and ledger complete eleven stages', 1, 'saga', [SEED, CUST, SEND, POSTED, ['As the teller, open the timeline', 'Stages 1 to 3 from transaction-service and 4 to 11 from the orchestrator, one row each, in order'], ['Read both balances and the audit statement', 'One debit and one credit of 2500.00; the hold is POSTED']]],
    ['account to ledger: opening an account opens a zero balance (K3 to L1)', 1, 'account;ledger', [TELLER, ['Open a savings account', 'The account is created'], ['GET /api/v1/ledger/accounts/{new id}/balance', 'A balance row exists with 0.00']]],
    ['account to ledger: opening fails as a whole when the ledger is down', 2, 'account;ledger', [['Stop ledger-service', 'The container is stopped'], ['As the teller, open a savings account', 'The call fails'], ['Start ledger-service and list the customer accounts', 'No new account exists; nothing is left without a balance']]],
    ['account to Kafka: account.created.v1 and account.closure.completed.v1 are published', 2, 'account;kafka', [['Consume the account topics (scripts/test_kafka_events.ps1)', 'The consumer is listening'], ['Open an account, then close a zero-balance account', 'One account.created.v1 and one account.closure.completed.v1, each with the correlation ID'], ['Read the customer inbox', 'One notification for the closure']]],
    ['Customer 360 aggregates customer, account, ledger and transaction data', 1, 'customer;hybrid', [CUST, ['GET the Customer 360', 'Balances equal those from /api/v1/accounts/my-accounts; recent transactions equal those from T4'], ['Send a transfer, wait for POSTED, and read the 360 again', 'The new balance and the new transaction are both shown']]],
    ['customer to account: a beneficiary number is checked with account-service', 2, 'customer;account', [CUST, ['Add a beneficiary with ACC_10000002 at COOBS', 'Accepted'], ['Add one with a number that does not exist', 'Rejected']]],
    ['KYC events reach the notification service', 2, 'customer;kafka;notification', [CUST, ['Submit a KYC update request', 'kyc.request.submitted.v1 is on Kafka; one notification for the customer'], ['As the teller, approve it', 'kyc.request.evaluated.v1 is on Kafka; one more notification for the customer']]],
    ['orchestration reads the risk profile and asks the risk engine', 1, 'saga;risk', [['As andrea_reyes, send a transfer', 'HTTP 202'], ['Open the staff timeline and the orchestrator log for the correlation ID', 'The risk profile was read from customer-service, the risk engine answered DECLINE with HIGH_RISK, and no hold was placed']]],
    ['compensation: a hold whose capture fails is released', 1, 'saga;ledger', [['Precondition: make the capture fail after the hold (stop the ledger between the two calls, or use the orchestrator unit test for this path if the fault cannot be produced on the stack)', 'The fault is in place'], ['Send a transfer and wait for the final status', 'FAILED'], ['Read the sender balance and held amount', 'Balance unchanged and heldAmount back to its earlier value']]],
    ['risk outcome: the daily limit is given back when an approved transfer does not post (R2)', 2, 'saga;risk', [['Run scripts/test_daily_limit_reservation.ps1, or by hand: send a transfer that risk approves but the ledger refuses for insufficient funds', 'The transfer is FAILED'], ['Send a transfer that fits only if the first reservation was returned', 'It is approved by risk']]],
    ['ledger outbox: a posting is published to Kafka by the relay', 1, 'ledger;kafka', [['Consume the ledger topic, then make a cash deposit', 'The deposit is accepted'], ['Watch the topic and the OUTBOX table', 'One event for the posting; its OUTBOX row is marked sent']]],
    ['ledger to audit store: every posting writes chained audit rows', 1, 'ledger;audit', [['Make a deposit, a withdrawal and a transfer on account 1', 'All accepted'], ['GET the statement and verify-chain for account 1', 'One audit entry per posting leg; isChainIntact is true']]],
    ['notification: the same event delivered twice gives one notification', 2, 'notification;kafka', [['Publish one stage event twice with the same event ID', 'Both are consumed'], ['Read the inbox', 'One notification only']]],
    ['notification: an event that cannot be processed is parked and replayed (N4, N5)', 1, 'notification;kafka', [['Run scripts/test_dead_letter_replay.ps1, or publish an event the consumer cannot process', 'It is retried after 1 s, 2 s and 4 s, then parked on notification.events.DLT'], ['As admin, GET /api/v1/notifications/admin/dlq', 'The record is listed'], ['Fix the cause, then POST /api/v1/notifications/admin/dlq/{recordId}/replay', 'The record is processed and the notification arrives']]],
    ['transaction: stage events that arrive twice or out of order keep one row per stage', 2, 'transaction;kafka', [['Publish a stage event for an existing transaction twice, then an earlier stage after a later one', 'All are consumed'], ['Open the staff timeline', 'One row per stage; the repeat changed nothing']]],
    ['correlation ID travels through every service and event', 2, 'gateway;kafka', [['Send a transfer with a chosen X-Correlation-ID (scripts/test_correlation_propagation.ps1)', 'HTTP 202'], ['Search the logs of gateway, transaction, orchestration, risk and ledger, and the events on Kafka', 'The same ID is in every log line and every event for that transfer']]],
    ['a revoked token is refused by every service', 1, 'auth;redis', [CUST, ['Revoke the token', 'Accepted'], ['Call customer, account, transaction, notification and audit endpoints with it', 'HTTP 401 from each']]],
    ['batch to ledger to audit: end of day posts, reconciles and seals', 1, 'batch;ledger;audit', [['Note the balance of one savings account', 'Balance noted'], ['As admin, trigger end of day and wait for COMPLETED', 'Six steps complete'], ['Read the balance, the reconciliation step and the seal for the business date', 'Interest is credited, total debits equal total credits, and the seal is returned']]],
    ['batch interest reaches the customer as a notification through Kafka', 3, 'batch;notification', [['Run scripts/test_month_end_interest_notification.ps1', 'The script passes: the interest notification is in the customer inbox']]],
    ['outbound inter-bank: the partner accepts and the transfer posts', 1, 'interbank;saga', [['As david_kim, note the balance of account 3 and POST a transfer of "1.00" to 1000-2000-3000 at BANKB', 'HTTP 202'], ['Poll the transaction and open the staff timeline', 'POSTED; the partner answer is stage 7 and the money moves after it'], ['Read the balance of account 3', 'Lower by exactly 1.00']]],
    ['outbound inter-bank: the partner refuses and the hold is released', 1, 'interbank;saga', [['As david_kim, POST a transfer of "1.00" to 0000-1111-2222 at BANKB', 'HTTP 202'], ['Poll the transaction and open the staff timeline', 'FAILED with INTERBANK_REJECTED at stage 7; the hold is released'], ['Read the balance of account 3', 'Unchanged']]],
    ['inbound inter-bank: a signed transfer credits our customer once (I1, I2)', 1, 'interbank;ledger', [['Run scripts/test_inbound_interbank.ps1, which signs one inbound transfer as BANKB', 'The beneficiary is credited once and gets a notification'], ['The script sends the same messageId again', 'No second credit'], ['GET /api/v1/interbank/transfers/{messageId}', 'The status of that transfer is returned']]],
    ['reversal saga: transaction, orchestration, ledger and audit', 1, 'reversal;saga', [['Post a transfer, then as the teller request its reversal', 'The reversal is accepted'], ['Poll the original, read both balances and the statement', 'REVERSED; balances restored; REVERSAL_CREDIT and REVERSAL_DEBIT rows added and the originals untouched']]],
    ['a transfer sent to a partner bank cannot be reversed', 2, 'reversal;interbank', [['As the teller, request a reversal of a POSTED outbound transfer to BANKB', 'The reversal ends FAILED with REVERSAL_NOT_ALLOWED; no balance changes']]],
    ['hybrid link: cloud services read on-premises data', 1, 'hybrid', [['Run scripts/test_hybrid_360_and_blacklist.ps1 against the deployed address', 'Customer 360 shows balances held on-premises; a revoked token is refused on both sides of the link']]],
  ]],

  '03_system_e2e': ['E2E', 'e2e', [
    ['automated suite: the Postman end-to-end collection passes', 1, 'newman', [SEED, ['Run postman/e2e/CooBS_End_To_End.postman_collection.json with Newman and the CooBS_Local environment', 'Every assertion passes (108 of 108 at the last recorded run)'], ['Run it a second time without a reset', 'It passes again']]],
    ['scenario 1: customer signs in and opens Customer 360', 1, 'demo', [CUST, ['Open Customer 360 in the app', 'Accounts, balances, the spending chart and the score gauges are shown']]],
    ['scenario 2: customer sends a transfer to another member', 1, 'demo', [CUST, ['In the app, send 2500.00 from account 1 to ACC_10000002', 'The tracker moves Initiated, Processing, Posted'], ['Check both accounts', 'Sender lower and receiver higher by 2500.00'], ['Check both inboxes', 'One notification each']]],
    ['scenario 3: teller watches the same transfer', 1, 'demo', [TELLER, ['Open the transfer from scenario 2 in the staff view', 'Eleven stages, each with a time']]],
    ['scenario 4: transfer declined by risk', 1, 'demo', [['As andrea_reyes, send a transfer', 'The tracker shows Failed with a plain message'], ['As the teller, open the tracker', 'Failed at Fraud Check'], ['Check the balance', 'Unchanged']]],
    ['scenario 5: transfer with not enough money', 1, 'demo', [['As liza_mercado, send more than the balance of account 9', 'The tracker shows Failed'], ['As the teller, open the tracker', 'Failed at Funds Check'], ['Check the balance', 'Unchanged']]],
    ['scenario 6: double tap on Send', 1, 'demo', [CUST, ['Tap Send twice quickly on one transfer', 'One transfer only in the history; the balance moved once']]],
    ['scenario 7: teller reverses a transfer', 1, 'demo', [TELLER, ['Reverse the transfer from scenario 2', 'Both balances return to their earlier values'], ['Open the statement of account 1', 'Four entries for that transfer'], ['Verify the chain', 'Still intact']]],
    ['scenario 8: notification consumer breaks, then is fixed', 2, 'demo', [['Break the notification consumer and post a transfer', 'No notification arrives; the record appears in the dead-letter list'], ['Fix the consumer; as admin, replay the record', 'The notification arrives']]],
    ['scenario 9: admin runs end of day', 1, 'demo', [ADMIN, ['Trigger end of day and watch the run', 'Six steps tick through; balances change; debits equal credits'], ['Open the seal for the business date', 'The seal is shown']]],
    ['scenario 10: transfer to another group bank', 2, 'demo;interbank', [['As david_kim, send 1.00 to 1000-2000-3000 at BANKB', 'Posted on our side'], ['Ask the partner group to check the beneficiary', 'The partner shows the credit']]],
    ['scenario 11: transfer from another group bank', 2, 'demo;interbank', [['The partner group sends a transfer to one of our accounts', 'Accepted'], ['Check our customer', 'The balance is higher and a notification arrived']]],
    ['scenario 12: concurrent transfers on one account', 1, 'demo;jmeter', [['Run scripts/run_jmeter_test.ps1 (test/jmeter-load-test.jmx)', 'The run completes'], ['Read the account balance and the audit statement', 'No overdraft and no double spend: the balance equals the opening balance minus the transfers that posted']]],
  ]],

  '04_security': ['SEC', 'security', [
    ['a protected endpoint without a token answers 401', 1, 'authn', [['GET /api/v1/customers/me with no Authorization header', 'HTTP 401 in the standard error shape']]],
    ['an expired or malformed token answers 401', 1, 'authn', [['Call a protected endpoint with an expired access token, then with a token whose signature is altered', 'HTTP 401 both times']]],
    ['a revoked token is rejected', 1, 'authn', [CUST, ['POST /api/v1/auth/token/revoke, then GET /api/v1/auth/token/me with the same token', 'HTTP 401']]],
    ['each login endpoint accepts only its own user type', 1, 'authn', [['Sign in as john_doe on /api/v1/auth/staff/login', 'Rejected'], ['Sign in as teller_alice on /api/v1/auth/customers/login', 'Rejected']]],
    ['a failed sign-in does not reveal whether the username exists', 2, 'authn', [['Sign in with a real username and a wrong password, then with an unknown username', 'Same status and same body for both']]],
    ['too many sign-in attempts from one address are limited', 2, 'gateway;rate-limit', [['Run scripts/test_rate_limiting.ps1, or send sign-in requests above 10 per second with a burst over 20', 'HTTP 429 once the limit is passed'], ['Wait and sign in normally', 'HTTP 200']]],
    ['a customer cannot read another customer 360', 1, 'authz', [CUST, ['GET /api/v1/customers/{another customer id}/360', 'HTTP 403']]],
    ['a customer cannot read another customer account', 1, 'authz', [CUST, ['GET /api/v1/accounts/2', 'HTTP 403']]],
    ['a customer cannot read another customer transaction or timeline', 1, 'authz', [['As a customer who is neither sender nor receiver, GET /api/v1/transactions/{id} and its /timeline', 'HTTP 403 for both']]],
    ['a customer asking for the staff timeline gets three stages', 1, 'authz', [CUST, ['GET /api/v1/transactions/{own id}/timeline?view=STAFF', 'Three stages only; no internal stage detail']]],
    ['a customer token cannot call a ledger endpoint', 1, 'authz', [CUST, ['GET /api/v1/ledger/accounts/1/balance', 'HTTP 403']]],
    ['only an admin can trigger end of day', 1, 'authz', [['POST /api/v1/batch/eod/trigger as a customer', 'HTTP 403'], ['POST /api/v1/batch/eod/trigger as teller_alice', 'HTTP 403']]],
    ['only an admin can remove a flag or decide a closure', 2, 'authz', [TELLER, ['Try to remove an account flag', 'HTTP 403'], ['Try to approve a closure request', 'HTTP 403']]],
    ['only staff can open accounts, change status and reverse transfers', 1, 'authz', [CUST, ['Try K3 open account, K5 status change and T5 reversal with the customer token', 'HTTP 403 for each']]],
    ['only an admin can list and replay dead-letter records', 2, 'authz', [['GET /api/v1/notifications/admin/dlq as a customer and as a teller', 'HTTP 403 for both']]],
    ['a customer cannot mark another customer notification', 2, 'authz', [CUST, ['Mark as read a notification ID that belongs to another customer', 'HTTP 404']]],
    ['internal paths are not reachable through the gateway', 1, 'perimeter', [['GET /internal/v1/sagas/00000000-0000-0000-0000-000000000000 on port 8080, with and without a valid user token', 'Refused by the gateway (403 from the perimeter filter); the request never reaches a service']]],
    ['internal endpoints accept only a service token', 1, 'perimeter', [['On the local stack, call an /internal/v1 endpoint on a service port directly with a customer token, then a staff token', 'HTTP 403 for both']]],
    ['gateway route listing is not exposed', 2, 'perimeter', [['GET /actuator/gateway/routes on port 8080', 'HTTP 404']]],
    ['on-premises services are not reachable from the internet', 1, 'perimeter;hybrid', [['From outside Azure, try ports 8082, 8083, 8085 and 8093 of the on-premises VM', 'No route: the VM has no public address and the NSG admits only the AKS subnet']]],
    ['an inbound inter-bank message with a bad signature is refused', 1, 'interbank', [['Send an I1 message with an altered body or a wrong HMAC', 'Rejected; no account is credited']]],
    ['errors use the standard shape and leak nothing', 2, 'errors', [['Trigger a 400, a 403, a 404 and a 5xx', 'Each body has the problem shape with an errorCode; none has a stack trace, SQL or a class name']]],
    ['a service does not start when a secret is missing', 2, 'secrets', [['Start each service with JWT_SECRET (and its other secrets) unset', 'Startup fails with a clear message; no built-in default is used']]],
    ['CORS admits only the configured origins', 3, 'gateway', [['Send a preflight to the gateway with an Origin that is not configured', 'No Access-Control-Allow-Origin for that origin'], ['Repeat with the staff console origin', 'Allowed']]],
  ]],

  '05_performance_resilience': ['NFR', 'non-functional', [
    ['risk engine answers under 40 ms at the 95th percentile', 1, 'performance;risk', [['Run scripts/benchmark_risk_eval.ps1 on a warm stack', 'p95 is under 40 ms']]],
    ['concurrent transfers from one account never overdraw it', 1, 'performance;ledger', [['Run scripts/run_jmeter_test.ps1', 'The run completes with no 5xx from the ledger'], ['Read the balance and the statement', 'The balance is never negative and equals the opening balance minus the posted transfers']]],
    ['A to B and B to A at the same moment do not deadlock', 2, 'performance;ledger', [['Send transfers 1 to 2 and 2 to 1 in parallel, repeated 50 times', 'All finish; none hangs or fails on a lock timeout'], ['Read both balances', 'Each equals its opening balance plus the net of the posted transfers']]],
    ['two transfers near the daily limit at the same moment cannot both pass', 2, 'performance;risk', [['Send two transfers in parallel that fit the remaining limit one at a time but not together', 'One is POSTED; the other is FAILED with LIMIT_EXCEEDED']]],
    ['end of day completes in 5 to 8 seconds', 3, 'performance;batch', [['As admin, trigger end of day on seed data and time it', 'COMPLETED in 5 to 8 seconds']]],
    ['the first transfer on an idle stack still posts', 1, 'resilience;risk', [['Leave the stack idle for 30 minutes, then send one transfer', 'POSTED, not FAILED with RISK_UNAVAILABLE']]],
    ['risk engine too slow or down: the transfer is declined before any hold', 1, 'resilience;risk', [['Stop risk-engine (or delay it past 150 ms) and send a transfer', 'FAILED with RISK_UNAVAILABLE'], ['Read the sender balance and held amount', 'Unchanged; no hold was placed']]],
    ['Redis down: the risk engine refuses to decide', 2, 'resilience;risk', [['Stop Redis and send a transfer with a token issued earlier', 'The risk engine answers 503 RISK_UNAVAILABLE and the transfer is FAILED before any hold'], ['Start Redis and send a transfer', 'POSTED']]],
    ['ledger unreachable before the hold: the transfer fails cleanly', 1, 'resilience;ledger', [['Stop ledger-service and send a transfer', 'FAILED with LEDGER_UNAVAILABLE'], ['Start the ledger and read the balance', 'Unchanged']]],
    ['ledger unreachable after the hold: the transfer waits and then finishes', 2, 'resilience;saga', [['Stop ledger-service after the hold is placed', 'The transaction stays PROCESSING at its stage and is visible to staff'], ['Start the ledger and wait for the sweep', 'The transfer reaches a final status and the money moved exactly once or not at all']]],
    ['orchestration restarts in the middle of a saga', 1, 'resilience;saga', [['Send a transfer and restart orchestration-service before it finishes', 'The service comes back'], ['Poll the transaction', 'It continues from its last state to a final status; the money moved exactly once']]],
    ['Kafka down: postings still commit and events are sent later', 1, 'resilience;kafka', [['Stop Kafka and make a cash deposit', 'The deposit is accepted and the balance changes; the event waits in OUTBOX'], ['Start Kafka', 'The relay publishes the event and the notification arrives']]],
    ['audit store down: no balance changes without an audit row', 1, 'resilience;audit', [['Stop the audit PostgreSQL and make a cash deposit', 'The deposit fails'], ['Start it and read the balance', 'Unchanged']]],
    ['a service that is down gives 503 in the standard shape', 2, 'resilience;gateway', [['Stop customer-service and GET /api/v1/customers/me through the gateway', 'HTTP 503 with the problem shape and an errorCode']]],
    ['link between cloud and on-premises is cut', 1, 'resilience;hybrid', [['Block the peering link with a firewall rule', 'The rule is in place'], ['Sign in', 'Sign-in still works'], ['Send a transfer', 'It fails cleanly or waits in PROCESSING; no balance is half-changed'], ['Remove the rule and send a transfer', 'POSTED; anything left in PROCESSING reaches a final status']]],
    ['batch survives a ledger outage and can be run again', 2, 'resilience;batch', [['Stop ledger-service and trigger end of day (scripts/test_batch_report_and_recovery.ps1)', 'The current step fails and the run is FAILED'], ['Start the ledger and trigger again', 'COMPLETED; nothing is posted twice']]],
    ['batch halts when debits do not equal credits', 2, 'resilience;batch', [['Precondition: on a throwaway stack, make the day debits differ from the credits', 'The imbalance exists'], ['Trigger end of day', 'Step 5 FAILED, step 6 SKIPPED, run HALTED, and the admin is alerted']]],
    ['an account short of money for a fee or instalment does not stop the batch', 3, 'resilience;batch', [['Precondition: one qualifying account has less than the fee', 'Balance noted'], ['Trigger end of day', 'That item is recorded as failed; the step continues and the run is COMPLETED']]],
    ['monitoring raises an alert when a service is down', 3, 'observability', [['Run scripts/test_observability_and_alerts.ps1, or stop one service', 'The dashboard shows the service down and the alert fires'], ['Start the service', 'The alert clears']]],
  ]],

  '06_uat_clients': ['UAT', 'uat', [
    ['mobile: customer signs in and signs out', 1, 'flutter', [['Open the Flutter app against the gateway and sign in as john_doe', 'The dashboard opens'], ['Sign out', 'The login screen returns and the old session cannot be reused']]],
    ['mobile: Customer 360 dashboard', 1, 'flutter', [['Open the dashboard', 'Accounts, balances, spending chart and score gauges match the seed']]],
    ['mobile: send a transfer and follow the three-stage tracker', 1, 'flutter', [['Send 100.00 to ACC_10000002', 'The tracker moves Initiated, Processing, Posted'], ['Return to the dashboard', 'The balance is lower by 100.00']]],
    ['mobile: a failed transfer shows a plain message', 2, 'flutter', [['As liza_mercado, send more than the balance', 'The tracker shows Failed with a message a customer understands, with no error code or stack trace']]],
    ['mobile: manage beneficiaries', 2, 'flutter', [['Add ACC_10000002 as a beneficiary, then remove it', 'It appears in the list and then disappears']]],
    ['mobile: notifications inbox and unread badge', 2, 'flutter', [['After a posted transfer, open notifications', 'The new notification is listed and the unread badge counts it'], ['Open the notification', 'It is marked read and the badge drops']]],
    ['mobile: the session ends after inactivity', 3, 'flutter', [['Leave the app idle for 15 minutes', 'The app returns to the login screen and the token is revoked']]],
    ['mobile: staff see the eleven-stage tracker and admin runs the batch', 2, 'flutter', [['Sign in as teller_alice and open a transfer', 'Eleven stages with times'], ['Sign in as admin and trigger end of day', 'Six steps tick through to completed']]],
    ['console: teller signs in and signs out', 1, 'react', [['Open the staff console and sign in as teller_alice', 'The console opens'], ['Sign out', 'The token is revoked and session storage is cleared']]],
    ['console: teller opens an account', 1, 'react', [['Open a savings account for a customer with verified KYC', 'The account appears in the customer account list with a 0.00 balance']]],
    ['console: cash deposit and withdrawal at the counter', 1, 'react', [['Deposit 1000.00 into account 1', 'The balance rises by 1000.00'], ['Withdraw 500.00', 'The balance drops by 500.00'], ['Withdraw more than the balance', 'Refused with a clear message']]],
    ['console: counter transfer', 2, 'react', [['Transfer 100.00 from account 1 to account 2', 'Both balances change by 100.00']]],
    ['console: freeze and flag an account', 2, 'react', [['Freeze account 2, then unfreeze it', 'The status changes each time'], ['Flag an account', 'The flag is shown as active']]],
    ['console: approve a KYC update and a closure', 2, 'react', [['As the teller, approve a pending KYC update', 'It leaves the pending list'], ['As admin, approve a pending closure request', 'The account is CLOSED']]],
    ['console: audit statement and chain verification', 1, 'react', [['Open the statement of account 1', 'Every posting is listed'], ['Verify the chain', 'The chain is reported intact']]],
  ]],

  '07_deployment_smoke': ['SMK', 'smoke', [
    ['smoke: every workload is running', 1, 'deploy', [['kubectl get pods in the CooBS namespace', 'The eight cloud services are Running and Ready, with no restart loop'], ['docker compose ps on the on-premises VM', 'ledger, account, batch, audit, Oracle and the audit store are healthy']]],
    ['smoke: the gateway is healthy at the public address', 1, 'deploy', [['GET /actuator/health at the public address', 'UP']]],
    ['smoke: a customer and a teller can sign in', 1, 'deploy', [CUST, TELLER]],
    ['smoke: Customer 360 shows on-premises balances', 1, 'deploy;hybrid', [CUST, ['Open Customer 360', 'Balances are shown, which proves the cloud reaches the on-premises zone']]],
    ['smoke: one small transfer posts with eleven stages', 1, 'deploy', [['As john_doe, send 1.00 to ACC_10000002', 'POSTED'], ['As the teller, open the timeline', 'Eleven stages']]],
    ['smoke: the notification arrives and the chain verifies', 1, 'deploy', [['Read the inbox of john_doe', 'A notification for the smoke transfer'], ['GET /api/v1/audit/verify-chain/1', 'isChainIntact is true']]],
    ['smoke: the end-to-end collection passes against the deployed address', 1, 'deploy;newman', [['Run the Postman end-to-end collection with baseUrl set to the public address, on seed data', 'Every assertion passes']]],
  ]],
};

const HEADER = ['ID', 'Work Item Type', 'Title', 'Test Step', 'Step Action', 'Step Expected', 'Area Path', 'Assigned To', 'State', 'Priority', 'Tags'];
const cell = (v) => `"${String(v).replace(/"/g, '""')}"`;
const out = dirname(fileURLToPath(import.meta.url));
const seen = new Set();
let total = 0;

for (const [file, [prefix, tag, cases]] of Object.entries(suites)) {
  const rows = [HEADER];
  cases.forEach(([name, priority, tags, steps], i) => {
    const title = `${prefix}-${String(i + 1).padStart(2, '0')} ${name}`;
    assert(!seen.has(name), `duplicate title: ${name}`);
    assert(title.length <= 255 && steps.length > 0, `bad case: ${title}`);
    assert(steps.every((s) => s.length === 2 && s[0] && s[1]), `bad step in: ${title}`);
    assert(!/[^\x20-\x7e]/.test(JSON.stringify(steps) + title), `non-ASCII text in: ${title}`);
    seen.add(name);
    // One title row with no step, then step rows with no title. A row that has a title starts a
    // new test case and its step cells are dropped (seen on import, 11 October 2026).
    rows.push(['', 'Test Case', title, '', '', '', AREA, ASSIGNED, STATE, priority, `${tag}; ${tags.replace(/;/g, '; ')}`]);
    steps.forEach(([action, expected], n) => rows.push(['', '', '', n + 1, action, expected, '', '', '', '', '']));
  });
  writeFileSync(join(out, `${file}.csv`), rows.map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n', 'utf8');
  console.log(`${file}.csv  ${cases.length} test cases, ${rows.length - 1 - cases.length} steps`);
  total += cases.length;
}
console.log(`total ${total} test cases`);
