// ==============================================================================
// Staff BFF TypeScript Data Contracts
// Matches Spring Boot DTOs in com.group2.fse.staff_bff.dto & FUTURE_INTEGRATED_API_CONTRACT.md
// ==============================================================================

export type StaffRole = 'ROLE_TELLER' | 'ROLE_ADMIN';

export interface AuthData {
  accessToken: string;
  refreshToken: string;
  userId: string;
  username: string;
  roles: StaffRole[];
  branchCode: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export type AccountType = 'SAVINGS' | 'CHECKING' | 'CURRENT' | 'LOAN';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface StaffAccountSummary {
  accountId: number;
  accountNumber: string;
  customerName?: string;
  accountType: AccountType;
  currency: string;
  status: AccountStatus;
  availableBalance: string;
  ledgerBalance: string;
  heldAmount: string;
  outstandingPrincipal?: string | null;
  nextDueDate?: string | null;
  nextDueAmount?: string | null;
  activeFlags: string[];
}

export interface StaffCustomer360Response {
  customerId: number;
  displayName: string;
  username: string;
  email: string;
  phone?: string;
  membershipNumber: string;
  kycStatus: 'VERIFIED' | 'PENDING' | 'REJECTED' | 'UNVERIFIED';
  kycTier: string;
  creditScore: number;
  riskScore: string | number;
  accounts: StaffAccountSummary[];
  totals: {
    totalDeposits: string;
    totalLoans: string;
  };
  recentTransactions: Array<{
    transactionId?: string;
    referenceNo?: string;
    amount?: number | string;
    category?: string;
    status?: string;
    timestamp?: string;
    createdAt?: string;
    currency?: string;
    direction?: 'IN' | 'OUT';
    counterpartyName?: string;
    counterpartyAccountNumber?: string;
  }>;
  spendingBreakdown: Record<string, string> | Array<{ category: string; amount: string }>;
  savedBeneficiaries: Array<{
    beneficiaryId?: number;
    nickname?: string;
    accountNumber?: string;
    bankCode?: string;
  }>;
  asOf: string;
  partialFailures?: string[];
}

export interface StaffQueuesSummaryResponse {
  totalPendingKyc: number;
  totalPendingClosures: number;
  pendingKycRequests: Array<{
    requestId?: number;
    kycRequestId?: number;
    kycId?: number;
    customerId: number;
    customerName?: string;
    requestedFields?: Record<string, string>;
    status: string;
    submittedAt?: string;
    requestedAt?: string;
    newFirstName?: string;
    newMiddleInitial?: string;
    newLastName?: string;
    newAddress?: string;
    newMobileNumber?: string;
    newCivilStatus?: string;
    newOccupation?: string;
  }>;
  pendingClosureRequests: Array<{
    closureId: number;
    accountId: number;
    accountNumber: string;
    customerId: number;
    customerName?: string;
    availableBalance: number | string;
    reason: string;
    status: string;
    requestedAt: string;
  }>;
  partialFailures?: string[];
}

export interface BatchStepItem {
  stepNo: number;
  stepName: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
  itemsProcessed?: number;
  totalAmount?: string;
  durationMs?: number;
  message?: string;
}

export type BatchRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'HALTED' | 'FAILED';

export interface BatchRunDetail {
  batchRunId: number;
  businessDate: string;
  status: BatchRunStatus;
  triggeredBy?: number | string;
  startedAt: string;
  finishedAt?: string;
  steps?: BatchStepItem[];
}

export interface DeadLetterRecord {
  recordId: string;
  originalTopic: string;
  deadLetterTopic: string;
  recordKey?: string;
  errorMessage: string;
  attempts: number;
  maxAttempts?: number;
  failedAt: string;
  replayedAt?: string;
  payloadJson?: string;
}

export interface DailyAuditSealResponse {
  businessDate: string;
  lastAuditId?: number | string;
  sealHash: string;
  previousDaySealHash?: string;
  totalTransactions?: number;
  sealedAt: string;
  batchRunId?: number | string;
}

export interface StaffAdminOverviewResponse {
  latestBatchRun: {
    batchRunId?: number;
    businessDate?: string;
    status?: BatchRunStatus;
    triggeredBy?: number;
    startedAt?: string;
    finishedAt?: string;
    steps?: BatchStepItem[];
  };
  deadLetterQueueCount: number;
  deadLetterRecords: DeadLetterRecord[];
  latestAuditSeal: {
    businessDate?: string;
    lastAuditId?: number;
    sealHash?: string;
    sealedAt?: string;
    batchRunId?: number;
  };
  partialFailures?: string[];
}

export interface CounterCashPayload {
  accountId: number;
  amount: number | string;
  referenceNo?: string;
  remarks?: string;
}

export interface CounterTransferPayload {
  sourceAccountId: number;
  destinationAccountNumber: string;
  amount: number | string;
  destinationBankCode?: string;
  currency?: string;
  category?: string;
  remarks?: string;
}

export interface AccountOpenPayload {
  customerId: number;
  accountType: AccountType;
  currency?: string;
}

export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  errorCode: string;
  detail: string;
  path?: string;
  timestamp?: string;
  invalidParams?: Record<string, string>;
}

// ─── Transaction Timeline & Reversal ──────────────────────────────

export type TransactionStatus =
  | 'INITIATED'
  | 'PROCESSING'
  | 'POSTED'
  | 'FAILED'
  | 'REVERSED';

export type StageOutcome = 'PASSED' | 'FAILED' | 'IN_PROGRESS' | 'PENDING';

export interface TimelineStage {
  stageNumber: number;
  stageName: string;
  service: string;
  outcome: StageOutcome;
  timestamp?: string;
  detail?: string;
}

export interface TransactionSummary {
  transactionId: string;
  referenceNo: string;
  status: TransactionStatus;
  channel: string;
  sourceAccount: string;
  destinationAccount: string;
  amount: string;
  currency: string;
  category?: string;
  failureCode?: string;
  failureReason?: string;
  reversedByTransactionId?: string;
  createdAt: string;
  completedAt?: string;
}

export interface TimelineResponse {
  transaction?: TransactionSummary;
  trackingRef?: string;
  currentStatus?: string;
  sourceAccountId?: number;
  destinationAccountId?: number;
  destinationAccountNumber?: string;
  amount?: number | string;
  currency?: string;
  stages?: TimelineStage[];
  lastUpdated?: string;
}

// ─── Audit Statement ──────────────────────────────────────────────

export type AuditEntryType = 'DEBIT' | 'CREDIT' | 'REVERSAL_DEBIT' | 'REVERSAL_CREDIT';

export interface AuditRecord {
  auditId: number | string;
  referenceNo?: string;
  entryType?: AuditEntryType | string;
  transactionType?: string;
  amount: string | number;
  oldBalance: string | number;
  newBalance: string | number;
  previousHash?: string;
  currentHash?: string;
  actorId?: string | number;
  clientIp?: string;
  timestamp?: string;
  eventTimestamp?: string;
}

export interface ChainVerificationResult {
  intact?: boolean;
  isChainIntact?: boolean;
  recordsVerified?: number;
  totalRecordsVerified?: number;
  latestHash?: string;
  verifiedAt?: string;
  message?: string;
}
