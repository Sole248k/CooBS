// ============================================================================
// Seed / Offline Fallback Data
// Extracted from the monolithic api.ts to keep service modules clean.
// All exports are pure constants — no runtime side effects.
// ============================================================================
import type {
  Account,
  AccountFlag,
  AuditRecord,
  BatchRunDetail,
  BatchRunDetails,
  BatchStepItem,
  ClosureRequestResponse,
  Customer360Response,
  CustomerProfile,
  DailyAuditSealResponse,
  KycRequestResponse,
  NotificationItem,
  PendingTransferItem,
} from '../types';

export const SEED_BATCH_STEPS: BatchStepItem[] = [
  { stepNo: 1, stepName: 'INTEREST_ACCRUAL', status: 'COMPLETED', itemsProcessed: 3, totalAmount: '12.24', durationMs: 810, message: 'Processed daily accrued interest across eligible accounts' },
  { stepNo: 2, stepName: 'SERVICE_FEES', status: 'COMPLETED', itemsProcessed: 1, totalAmount: '50.00', durationMs: 640, message: 'Applied maintenance and dormancy service fee charges' },
  { stepNo: 3, stepName: 'STANDING_INSTRUCTIONS', status: 'COMPLETED', itemsProcessed: 1, totalAmount: '1000.00', durationMs: 900, message: 'Executed recurring standing instructions' },
  { stepNo: 4, stepName: 'LOAN_AMORTIZATION', status: 'COMPLETED', itemsProcessed: 0, totalAmount: '5450.00', durationMs: 300, message: 'Processed amortization deductions for active loan schedules' },
  { stepNo: 5, stepName: 'GL_RECONCILIATION', status: 'COMPLETED', itemsProcessed: 0, totalAmount: '0.00', durationMs: 1200, message: 'Debits equal credits (trial balance validated)' },
  { stepNo: 6, stepName: 'AUDIT_SEAL', status: 'COMPLETED', itemsProcessed: 1, durationMs: 700, message: 'SHA-256 closing cryptographic seal recorded and anchored' }
];

export const SEED_EOD_BATCH_RUNS: BatchRunDetail[] = [
  {
    batchRunId: 1,
    businessDate: '2026-10-05',
    status: 'COMPLETED',
    triggeredBy: 1,
    startedAt: '2026-10-05T15:59:00.000Z',
    finishedAt: '2026-10-05T15:59:06.500Z',
    steps: SEED_BATCH_STEPS
  }
];

export const SEED_DAILY_SEAL: DailyAuditSealResponse = {
  businessDate: '2026-10-05',
  lastAuditId: 48,
  sealHash: '0787e54c7904c2988a544af97d8b75ee18011ced8a2772d0d12490c81cf8d7ff',
  sealedAt: '2026-10-05T15:59:06.400Z',
  batchRunId: 1
};

export const SEED_ACCOUNTS: Account[] = [
  {
    accountId: 1,
    accountNumber: 'ACC-1001-8842',
    accountType: 'CHECKING',
    balance: 14750.50,
    currency: 'PHP',
    status: 'ACTIVE',
    customerId: 1,
    customerName: 'John Doe',
    createdAt: '2026-01-15T08:30:00Z',
  },
  {
    accountId: 2,
    accountNumber: 'ACC-1002-9915',
    accountType: 'SAVINGS',
    balance: 85200.00,
    currency: 'PHP',
    status: 'ACTIVE',
    customerId: 1,
    customerName: 'John Doe',
    createdAt: '2026-02-01T10:15:00Z',
  },
  {
    accountId: 3,
    accountNumber: 'ACC-1003-3321',
    accountType: 'PAYROLL',
    balance: 0.00, // Eligible for account closure demonstration
    currency: 'PHP',
    status: 'ACTIVE',
    customerId: 1,
    customerName: 'John Doe',
    createdAt: '2026-03-10T14:00:00Z',
  },
  {
    accountId: 4,
    accountNumber: 'ACC-2001-4455',
    accountType: 'SAVINGS',
    balance: 120500.00,
    currency: 'PHP',
    status: 'ACTIVE',
    customerId: 2,
    customerName: 'Jane Smith',
    createdAt: '2026-02-14T09:00:00Z',
  },
  {
    accountId: 5,
    accountNumber: 'ACC-4001-1122',
    accountType: 'SAVINGS',
    balance: 150000.00,
    currency: 'PHP',
    status: 'ACTIVE',
    customerId: 4,
    customerName: 'Juan Dela Cruz',
    createdAt: '2026-02-20T11:00:00Z',
  },
  {
    accountId: 6,
    accountNumber: 'ACC-4002-3344',
    accountType: 'CHECKING',
    balance: 100000.00,
    currency: 'PHP',
    status: 'ACTIVE',
    customerId: 4,
    customerName: 'Juan Dela Cruz',
    createdAt: '2026-03-01T15:30:00Z',
  }
];

export const SEED_PROFILE: CustomerProfile = {
  customerId: 1,
  username: 'john_doe',
  email: 'john.doe@example.com',
  kycStatus: 'VERIFIED',
  createdAt: '2026-01-15T08:00:00Z',
  kyc: {
    kycId: 101,
    customerId: 1,
    firstName: 'John',
    middleInitial: 'A',
    lastName: 'Doe',
    address: '123 Ayala Avenue, Makati City, Metro Manila',
    mobileNumber: '09171234567',
    civilStatus: 'SINGLE',
    occupation: 'Senior Software Engineer',
    status: 'VERIFIED',
    createdAt: '2026-01-15T08:20:00Z'
  }
};

export const SEED_KYC_REQUESTS: KycRequestResponse[] = [
  {
    kycRequestId: 501,
    kycId: 101,
    customerId: 1,
    customerName: 'John Doe',
    status: 'PENDING',
    requestedAt: new Date(Date.now() - 3600000 * 4).toISOString(),
    newFirstName: 'Jonathan',
    newMiddleInitial: 'A',
    newLastName: 'Doe',
    newAddress: '456 Bonifacio Global City, Taguig, Metro Manila',
    newMobileNumber: '09179998888',
    newCivilStatus: 'MARRIED',
    newOccupation: 'Engineering Director'
  }
];

export const SEED_CLOSURE_REQUESTS: ClosureRequestResponse[] = [
  {
    closureRequestId: 301,
    accountId: 3,
    accountNumber: 'ACC-1003-3321',
    accountType: 'PAYROLL',
    customerId: 1,
    customerName: 'John Doe',
    availableBalance: 0.00,
    reason: 'Job change, company transitioned to another bank payroll provider.',
    status: 'PENDING',
    requestedAt: new Date(Date.now() - 3600000 * 2).toISOString()
  }
];

export const SEED_PENDING_TRANSFERS: PendingTransferItem[] = [
  {
    transferRequestId: 801,
    sourceAccountId: 1,
    sourceAccountNumber: 'ACC-1001-8842',
    destinationAccountId: 4,
    destinationAccountNumber: 'ACC-2001-4455',
    amount: 150000.00,
    referenceNo: 'REF-TXN-HIGH-9941',
    remarks: 'High value commercial equipment procurement',
    status: 'PENDING',
    requestedAt: new Date(Date.now() - 3600000 * 1.5).toISOString()
  }
];

export const SEED_NOTIFICATIONS: NotificationItem[] = [
  {
    id: 101,
    type: 'TRANSACTION_CREDIT',
    message: 'Deposit credited: ₱10,000.00 received in Account ACC-1001-8842.',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
  },
  {
    id: 102,
    type: 'TRANSACTION_TRANSFER',
    message: 'Fund transfer executed: ₱250.00 to Account ACC-1002-9915.',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 90).toISOString(),
  },
  {
    id: 103,
    type: 'KYC_STATUS',
    message: 'Your profile KYC verification is currently VERIFIED.',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 300).toISOString(),
  }
];

export const SEED_AUDIT_RECORDS: AuditRecord[] = [
  {
    auditId: 1001,
    transactionId: 501,
    accountId: 1,
    referenceNo: 'REF-DEP-001',
    transactionType: 'DEPOSIT',
    amount: 10000.00,
    oldBalance: 0.00,
    newBalance: 10000.00,
    previousHash: '0000000000000000000000000000000000000000000000000000000000000000',
    currentHash: 'c7d23e59a85b9e0f6b4d32e1850f2495b41cf131e50682a39281e59c049b72a4',
    actorId: 1,
    clientIp: '127.0.0.1',
    eventTimestamp: '2026-09-27T08:15:22.000Z'
  },
  {
    auditId: 1002,
    transactionId: 502,
    accountId: 1,
    referenceNo: 'REF-PAY-002',
    transactionType: 'CREDIT',
    amount: 5000.00,
    oldBalance: 10000.00,
    newBalance: 15000.00,
    previousHash: 'c7d23e59a85b9e0f6b4d32e1850f2495b41cf131e50682a39281e59c049b72a4',
    currentHash: '4a6b98e1f02c4819d45a901e7492c318bf920518dc9319e7a2b904128f49c018',
    actorId: 1,
    clientIp: '127.0.0.1',
    eventTimestamp: '2026-09-27T10:30:10.000Z'
  },
  {
    auditId: 1003,
    transactionId: 503,
    accountId: 1,
    referenceNo: 'REF-TRF-003',
    transactionType: 'TRANSFER_DEBIT',
    amount: 249.50,
    oldBalance: 15000.00,
    newBalance: 14750.50,
    previousHash: '4a6b98e1f02c4819d45a901e7492c318bf920518dc9319e7a2b904128f49c018',
    currentHash: '8e219fb041c9a48b5209c148209e51c8901b27e8a93149e0c8192a472918e932',
    actorId: 1,
    clientIp: '127.0.0.1',
    eventTimestamp: '2026-09-28T01:45:00.000Z'
  }
];

export const SEED_FLAGS: (AccountFlag & { accountNumber?: string })[] = [
  {
    flagId: 901,
    accountId: 4,
    accountNumber: 'ACC-2001-4455',
    reason: 'AML KYC Document Verification Required',
    status: 'ACTIVE',
    flaggedBy: 1,
    flaggedAt: new Date(Date.now() - 3600000 * 5).toISOString()
  }
];

export const SEED_CUSTOMER_360: Record<number, Customer360Response> = {
  1: {
    customerId: 1,
    fullName: 'John Doe',
    email: 'john.doe@example.com',
    phoneNumber: '09171234567',
    civilStatus: 'SINGLE',
    address: '123 Ayala Avenue, Makati City, Metro Manila',
    kycStatus: 'VERIFIED',
    riskTier: 'LOW',
    totalDeposits: 99950.50,
    totalLoans: 0.00,
    accounts: [
      { accountId: 1, accountNumber: 'ACC-1001-8842', accountType: 'CHECKING', availableBalance: 14750.50, currency: 'PHP', status: 'ACTIVE' },
      { accountId: 2, accountNumber: 'ACC-1002-9915', accountType: 'SAVINGS', availableBalance: 85200.00, currency: 'PHP', status: 'ACTIVE' },
      { accountId: 3, accountNumber: 'ACC-1003-3321', accountType: 'PAYROLL', availableBalance: 0.00, currency: 'PHP', status: 'ACTIVE' }
    ]
  },
  2: {
    customerId: 2,
    fullName: 'Jane Smith',
    email: 'jane.smith@example.com',
    phoneNumber: '09187654321',
    civilStatus: 'MARRIED',
    address: '88 Roxas Boulevard, Pasay City, Metro Manila',
    kycStatus: 'VERIFIED',
    riskTier: 'MEDIUM',
    totalDeposits: 120500.00,
    totalLoans: 0.00,
    accounts: [
      { accountId: 4, accountNumber: 'ACC-2001-4455', accountType: 'SAVINGS', availableBalance: 120500.00, currency: 'PHP', status: 'ACTIVE' }
    ]
  },
  4: {
    customerId: 4,
    fullName: 'Juan Dela Cruz',
    email: 'juan.delacruz@coobs.ph',
    phoneNumber: '09181234567',
    civilStatus: 'MARRIED',
    address: '789 Rizal St., Barangay San Jose, Pasig City',
    kycStatus: 'VERIFIED',
    riskTier: 'LOW',
    totalDeposits: 250000.00,
    totalLoans: 0.00,
    accounts: [
      { accountId: 5, accountNumber: 'ACC-4001-1122', accountType: 'SAVINGS', availableBalance: 150000.00, currency: 'PHP', status: 'ACTIVE' },
      { accountId: 6, accountNumber: 'ACC-4002-3344', accountType: 'CHECKING', availableBalance: 100000.00, currency: 'PHP', status: 'ACTIVE' }
    ]
  }
};

export const SEED_BATCH_RUNS: BatchRunDetails[] = [
  {
    runId: 1042,
    batchJobName: 'EOD_SETTLEMENT_AND_INTEREST_ACCRUAL',
    status: 'COMPLETED',
    startTime: new Date(Date.now() - 3600000 * 14).toISOString(),
    endTime: new Date(Date.now() - 3600000 * 13.8).toISOString(),
    totalRecordsProcessed: 1420,
    steps: [
      { stepId: 1, stepName: 'STEP 1: DAILY SAVINGS INTEREST ACCRUAL', status: 'COMPLETED', recordsProcessed: 450, durationMs: 1200 },
      { stepId: 2, stepName: 'STEP 2: DORMANT ACCOUNT INACTIVITY SWEEP', status: 'COMPLETED', recordsProcessed: 120, durationMs: 850 },
      { stepId: 3, stepName: 'STEP 3: BELOW-MINIMUM BALANCE FEE CALCULATION', status: 'COMPLETED', recordsProcessed: 310, durationMs: 940 },
      { stepId: 4, stepName: 'STEP 4: MONTHLY ACCOUNT STATEMENT SNAPSHOT', status: 'COMPLETED', recordsProcessed: 450, durationMs: 2400 },
      { stepId: 5, stepName: 'STEP 5: GENERAL LEDGER DAY-END HASH SEAL', status: 'COMPLETED', recordsProcessed: 90, durationMs: 650 }
    ]
  },
  {
    runId: 1041,
    batchJobName: 'EOD_SETTLEMENT_AND_INTEREST_ACCRUAL',
    status: 'COMPLETED',
    startTime: new Date(Date.now() - 3600000 * 38).toISOString(),
    endTime: new Date(Date.now() - 3600000 * 37.8).toISOString(),
    totalRecordsProcessed: 1395,
    steps: [
      { stepId: 1, stepName: 'STEP 1: DAILY SAVINGS INTEREST ACCRUAL', status: 'COMPLETED', recordsProcessed: 445, durationMs: 1150 },
      { stepId: 2, stepName: 'STEP 2: DORMANT ACCOUNT INACTIVITY SWEEP', status: 'COMPLETED', recordsProcessed: 118, durationMs: 820 },
      { stepId: 3, stepName: 'STEP 3: BELOW-MINIMUM BALANCE FEE CALCULATION', status: 'COMPLETED', recordsProcessed: 302, durationMs: 910 },
      { stepId: 4, stepName: 'STEP 4: MONTHLY ACCOUNT STATEMENT SNAPSHOT', status: 'COMPLETED', recordsProcessed: 445, durationMs: 2350 },
      { stepId: 5, stepName: 'STEP 5: GENERAL LEDGER DAY-END HASH SEAL', status: 'COMPLETED', recordsProcessed: 85, durationMs: 630 }
    ]
  }
];
