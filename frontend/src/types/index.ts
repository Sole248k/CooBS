export interface User {
  userId: number;
  username: string;
  roles: string[];
  userType: 'CUSTOMER' | 'TELLER' | 'ADMIN';
}

export interface AuthSession {
  accessToken: string;
  refreshToken?: string;
  userId: number;
  username: string;
  roles: string[];
  userType: 'CUSTOMER' | 'TELLER' | 'ADMIN';
}

export interface Account {
  accountId: number;
  accountNumber: string;
  accountType: 'SAVINGS' | 'CHECKING' | 'PAYROLL' | 'TIME_DEPOSIT';
  balance: number;
  currency: string;
  status: 'ACTIVE' | 'FROZEN' | 'DORMANT' | 'CLOSED';
  customerId?: number;
  customerName?: string;
  createdAt?: string;
}

export interface TransferRequest {
  sourceAccountId: number;
  destinationAccountId: number;
  amount: number;
  referenceNo: string;
  remarks?: string;
}

export interface TransferResponse {
  transactionId: number | string;
  referenceNo: string;
  sourceAccountId: number;
  destinationAccountId: number;
  amount: number;
  status: 'COMPLETED' | 'PENDING' | 'FAILED';
  timestamp?: string;
  isCachedReplay?: boolean;
}

export interface NotificationItem {
  id: number;
  type: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  timestamp?: string;
}

export interface AuditRecord {
  auditId: number;
  transactionId: number;
  accountId: number;
  referenceNo?: string;
  transactionType: string;
  amount: number;
  oldBalance: number;
  newBalance: number;
  previousHash: string;
  currentHash: string;
  actorId?: number;
  clientIp?: string;
  eventTimestamp: string;
}

export interface ChainVerificationResult {
  accountId: number;
  totalRecordsVerified: number;
  isChainIntact: boolean;
  latestHash?: string;
  verifiedAt: string;
  message: string;
}

// KYC Info & Requests
export interface KycRecord {
  kycId?: number;
  customerId?: number;
  firstName: string;
  middleInitial?: string;
  lastName: string;
  address: string;
  mobileNumber: string;
  civilStatus: string;
  occupation: string;
  status: 'VERIFIED' | 'PENDING' | 'REJECTED';
  createdAt?: string;
}

export interface CustomerProfile {
  customerId: number;
  username: string;
  email: string;
  kyc?: KycRecord;
  kycStatus: string;
  createdAt?: string;
}

export interface KycUpdateRequestDto {
  newFirstName?: string;
  newMiddleInitial?: string;
  newLastName?: string;
  newAddress?: string;
  newMobileNumber?: string;
  newCivilStatus?: string;
  newOccupation?: string;
}

export interface KycRequestResponse {
  kycRequestId: number;
  kycId?: number;
  customerId: number;
  customerName?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  approvedBy?: number;
  requestedAt: string;
  approvedAt?: string;
  reviewedAt?: string;
  rejectionReason?: string;
  message?: string;
  newFirstName?: string;
  newMiddleInitial?: string;
  newLastName?: string;
  newAddress?: string;
  newMobileNumber?: string;
  newCivilStatus?: string;
  newOccupation?: string;
}

// Account Closure
export interface AccountClosureRequestDto {
  reason?: string;
}

export interface ClosureRequestResponse {
  closureRequestId: number;
  accountId: number;
  accountNumber?: string;
  accountType?: string;
  customerId?: number;
  customerName?: string;
  availableBalance?: number;
  reason?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
  reviewedAt?: string;
  approvedBy?: number;
  rejectionReason?: string;
  accountStatus?: string;
  message?: string;
}

// Account Flags / Holds
export interface AccountFlag {
  flagId: number;
  accountId: number;
  reason: string;
  status: 'ACTIVE' | 'REMOVED';
  flaggedBy: number;
  removedBy?: number;
  flaggedAt: string;
  removedAt?: string;
}

// High Transaction Transfer (Dual-Control Approval)
export interface PendingTransferItem {
  transferRequestId: number;
  sourceAccountId: number;
  sourceAccountNumber?: string;
  destinationAccountId: number;
  destinationAccountNumber?: string;
  amount: number;
  referenceNo: string;
  remarks?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
}

// Customer 360 View DTOs
export interface Customer360Account {
  accountId: number;
  accountNumber: string;
  accountType: 'SAVINGS' | 'CHECKING' | 'PAYROLL' | 'TIME_DEPOSIT';
  availableBalance: number;
  currency: string;
  status: 'ACTIVE' | 'FROZEN' | 'DORMANT' | 'CLOSED';
}

export interface Customer360Response {
  customerId: number;
  fullName: string;
  email: string;
  phoneNumber?: string;
  civilStatus?: string;
  address?: string;
  kycStatus: string;
  riskTier: 'LOW' | 'MEDIUM' | 'HIGH';
  totalDeposits: number;
  totalLoans: number;
  accounts: Customer360Account[];
}

// Transaction Saga Timeline & Technical Stepper DTOs
export interface TimelineStage {
  stage: string;
  status: 'COMPLETED' | 'IN_PROGRESS' | 'PENDING' | 'FAILED';
  service: string;
  timestamp?: string;
  latencyMs?: number;
  details?: string;
}

export interface TimelineResponse {
  trackingRef: string;
  currentStatus: string;
  sourceAccountId?: number;
  destinationAccountId?: number;
  destinationAccountNumber?: string;
  amount?: number;
  currency?: string;
  stages: TimelineStage[];
  lastUpdated?: string;
}

// Transaction Reversal DTOs
export interface ReversalRequest {
  originalTrackingRef: string;
  reason: 'CUSTOMER_REQUEST' | 'SYSTEM_ERROR' | 'SUSPECTED_FRAUD' | 'DUPLICATE_PROCESSING';
  remarks?: string;
}

export interface ReversalResponse {
  reversalRef: string;
  originalTrackingRef: string;
  status: 'REVERSED' | 'PENDING' | 'FAILED';
  reversalTimestamp: string;
  message: string;
}

// Assisted Counter Saga Transfer DTO
export interface SagaTransferRequest {
  sourceAccountId: number;
  destinationAccountNumber: string;
  amount: number;
  currency?: string;
  description?: string;
}

// EOD Batch Monitor DTOs
export interface BatchStep {
  stepId: number;
  stepName: string;
  status: 'COMPLETED' | 'RUNNING' | 'FAILED' | 'PENDING';
  recordsProcessed: number;
  durationMs?: number;
}

export interface BatchRunDetails {
  runId: number;
  batchJobName: string;
  status: 'COMPLETED' | 'RUNNING' | 'FAILED';
  startTime: string;
  endTime?: string;
  totalRecordsProcessed: number;
  steps: BatchStep[];
}

// ============================================================================
// End-of-Day (EOD) Batch Pipeline & Daily Audit Seal Contracts
// ============================================================================

export type BatchStepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
export type BatchRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'HALTED';

export interface BatchStepItem {
  stepNo: number;
  stepName: string;
  status: BatchStepStatus;
  itemsProcessed: number;
  totalAmount?: string;
  durationMs?: number;
  message?: string;
}

export interface BatchRunDetail {
  batchRunId: number;
  businessDate: string; // YYYY-MM-DD
  status: BatchRunStatus;
  triggeredBy?: number;
  startedAt: string;
  finishedAt?: string;
  steps: BatchStepItem[];
}

export interface BatchTriggerResponse {
  batchRunId: number;
  businessDate: string;
  status: BatchRunStatus;
  startedAt: string;
}

export interface DailyAuditSealResponse {
  businessDate: string;
  lastAuditId: number;
  sealHash: string;
  sealedAt: string;
  batchRunId?: number;
}

export * from './staff-bff';


