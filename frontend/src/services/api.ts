// ============================================================================
// api.ts — Backward-Compatible Facade
//
// All existing components import from this file:
//   import { apiClient } from '../services/api'
//   import { staffApi } from '../services/api'
//
// The implementations now live in dedicated domain modules under ./api/.
// This file composes them back into the same object shapes that components expect.
// New code should import directly from the domain modules for clarity:
//   import { getCustomer360 } from '@/services/api/customers'
// ============================================================================
import type {
  Account,
  AccountFlag,
  AccountOpenPayload,
  AuditRecord,
  AuthData,
  AuthSession,
  BatchRunDetail,
  BatchRunDetails,
  BatchTriggerResponse,
  ChainVerificationResult,
  ClosureRequestResponse,
  CounterCashPayload,
  CounterTransferPayload,
  Customer360Response,
  CustomerProfile,
  DailyAuditSealResponse,
  DeadLetterRecord,
  KycRequestResponse,
  KycUpdateRequestDto,
  NotificationItem,
  PendingTransferItem,
  ReversalRequest,
  ReversalResponse,
  SagaTransferRequest,
  StaffAccountSummary,
  StaffAdminOverviewResponse,
  StaffCustomer360Response,
  StaffQueuesSummaryResponse,
  TimelineResponse,
  TransferRequest,
  TransferResponse,
} from '../types';

import { coreClient } from './client';

// Domain modules
import { loginCustomer, loginStaff, logout } from './api/auth';
import * as accountsApi from './api/accounts';
import * as customersApi from './api/customers';
import * as transactionsApi from './api/transactions';
import * as auditApi from './api/audit';
import * as batchApi from './api/batch';
import * as notificationsApi from './api/notifications';
import * as staffBffApi from './api/staff';

// Re-export domain modules for consumers that want to import directly
export { loginCustomer, loginStaff, logout } from './api/auth';
export * from './api/accounts';
export * from './api/customers';
export * from './api/transactions';
export * from './api/audit';
export * from './api/batch';
export * from './api/notifications';
export * from './api/staff';
export { coreClient } from './client';

// ============================================================================
// apiClient — The original monolithic object interface preserved for
// backward compatibility with all existing component imports.
// ============================================================================
export const apiClient = {
  // Session
  getSession: () => coreClient.getSession(),
  setSession: (s: AuthSession | null) => coreClient.setSession(s),
  logout: () => coreClient.logout(),

  // Auth
  loginCustomer,
  loginStaff,

  // Gateway Health
  checkGatewayHealth: () => coreClient.checkGatewayHealth(),

  // Accounts
  getAccounts: () => accountsApi.getAccounts(),
  getAllAccounts: () => accountsApi.getAllAccounts(),
  getAllFlags: () => accountsApi.getAllFlags(),
  getAccountFlags: (accountId: number) => accountsApi.getAccountFlags(accountId),
  addAccountFlag: (accountId: number, reason: string) => accountsApi.addAccountFlag(accountId, reason),
  removeAccountFlag: (accountId: number, flagId: number) => accountsApi.removeAccountFlag(accountId, flagId),
  openAccount: (customerId: number, accountType: string, initialDeposit?: number) => accountsApi.openAccount(customerId, accountType, initialDeposit),
  updateAccountStatus: (accountId: number, status: 'ACTIVE' | 'FROZEN' | 'DORMANT' | 'CLOSED') => accountsApi.updateAccountStatus(accountId, status),
  submitAccountClosureRequest: (accountId: number, reason?: string) => accountsApi.submitAccountClosureRequest(accountId, reason),
  getPendingClosureRequests: () => accountsApi.getPendingClosureRequests(),
  getMyClosureRequests: () => accountsApi.getMyClosureRequests(),
  approveClosureRequest: (id: number) => accountsApi.approveClosureRequest(id),
  rejectClosureRequest: (id: number, reason: string) => accountsApi.rejectClosureRequest(id, reason),

  // Customers & KYC
  getCustomerProfile: () => customersApi.getCustomerProfile(),
  submitKycUpdateRequest: (payload: KycUpdateRequestDto) => customersApi.submitKycUpdateRequest(payload),
  getPendingKycUpdateRequests: () => customersApi.getPendingKycUpdateRequests(),
  approveKycUpdateRequest: (id: number) => customersApi.approveKycUpdateRequest(id),
  rejectKycUpdateRequest: (id: number, reason: string) => customersApi.rejectKycUpdateRequest(id, reason),
  getCustomer360: (customerId: number) => customersApi.getCustomer360(customerId),

  // Transactions
  executeTransfer: (payload: TransferRequest, idempotencyKey: string, correlationId: string) =>
    transactionsApi.executeTransfer(payload, idempotencyKey, correlationId),
  getPendingTransfers: () => transactionsApi.getPendingTransfers(),
  approvePendingTransfer: (id: number) => transactionsApi.approvePendingTransfer(id),
  rejectPendingTransfer: (id: number, reason: string) => transactionsApi.rejectPendingTransfer(id, reason),
  depositCash: (accountId: number, amount: number, referenceNo: string, remarks?: string) =>
    transactionsApi.depositCash(accountId, amount, referenceNo, remarks),
  withdrawCash: (accountId: number, amount: number, referenceNo: string, remarks?: string) =>
    transactionsApi.withdrawCash(accountId, amount, referenceNo, remarks),
  executeSagaTransfer: (payload: SagaTransferRequest, idempotencyKey: string) =>
    transactionsApi.executeSagaTransfer(payload, idempotencyKey),
  getStaffTimeline: (trackingRef: string) => transactionsApi.getStaffTimeline(trackingRef),
  createSimulatedTimeline: (trackingRef: string, srcAccId: number, dstAccNum: string, amount: number) =>
    transactionsApi.createSimulatedTimeline(trackingRef, srcAccId, dstAccNum, amount),
  reverseTransaction: (payload: ReversalRequest) => transactionsApi.reverseTransaction(payload),

  // Audit
  getAccountAuditStatements: (accountId: number) => auditApi.getAccountAuditStatements(accountId),
  verifyChainIntegrity: (accountId: number) => auditApi.verifyChainIntegrity(accountId),
  verifyAuditChain: (accountId: number) => auditApi.verifyChainIntegrity(accountId),
  getDailyAuditSeal: (businessDate: string) => auditApi.getDailyAuditSeal(businessDate),
  simulateTampering: (accountId: number) => auditApi.simulateTampering(accountId),
  restoreIntactChain: (accountId: number) => auditApi.restoreIntactChain(accountId),

  // Batch
  getBatchRuns: () => batchApi.getBatchRuns(),
  getAllBatchRuns: () => batchApi.getAllBatchRuns(),
  getBatchRun: (batchRunId: number) => batchApi.getBatchRun(batchRunId),
  triggerEodBatch: (businessDate?: string) => batchApi.triggerEodBatch(businessDate),

  // Notifications
  getNotifications: () => notificationsApi.getNotifications(),
  markNotificationAsRead: (id: number) => notificationsApi.markNotificationAsRead(id),

  // Staff BFF
  getStaffCustomer360: (customerId: number) => staffBffApi.getStaffCustomer360(customerId),
  executeCounterDeposit: (payload: CounterCashPayload, idempotencyKey?: string) => staffBffApi.executeCounterDeposit(payload, idempotencyKey),
  executeCounterWithdrawal: (payload: CounterCashPayload, idempotencyKey?: string) => staffBffApi.executeCounterWithdrawal(payload, idempotencyKey),
  executeCounterTransfer: (payload: CounterTransferPayload, idempotencyKey?: string) => staffBffApi.executeCounterTransfer(payload, idempotencyKey),
  getStaffQueuesSummary: () => staffBffApi.getStaffQueuesSummary(),
  approveStaffKyc: (requestId: number) => staffBffApi.approveStaffKyc(requestId),
  rejectStaffKyc: (requestId: number, reason: string) => staffBffApi.rejectStaffKyc(requestId, reason),
  approveStaffClosure: (closureId: number) => staffBffApi.approveStaffClosure(closureId),
  rejectStaffClosure: (closureId: number, reason: string) => staffBffApi.rejectStaffClosure(closureId, reason),
  getStaffAccounts: () => staffBffApi.getStaffAccounts(),
  openStaffAccount: (payload: AccountOpenPayload) => staffBffApi.openStaffAccount(payload),
  updateStaffAccountStatus: (accountId: number, status: string) => staffBffApi.updateStaffAccountStatus(accountId, status),
  addStaffAccountFlag: (accountId: number, reason: string) => staffBffApi.addStaffAccountFlag(accountId, reason),
  removeStaffAccountFlag: (accountId: number, flagId: number) => staffBffApi.removeStaffAccountFlag(accountId, flagId),
  getStaffBffTimeline: (transactionId: string) => staffBffApi.getStaffBffTimeline(transactionId),
  requestStaffReversal: (transactionId: string, reason: string, idempotencyKey?: string) =>
    staffBffApi.requestStaffReversal(transactionId, reason, idempotencyKey),
  getStaffAccountStatement: (accountId: number, page?: number, size?: number) => staffBffApi.getStaffAccountStatement(accountId, page, size),
  verifyStaffAccountChain: (accountId: number) => staffBffApi.verifyStaffAccountChain(accountId),
  getStaffDailySeal: (businessDate: string) => staffBffApi.getStaffDailySeal(businessDate),
  getStaffAdminOverview: (businessDate?: string) => staffBffApi.getStaffAdminOverview(businessDate),
  triggerStaffEod: (payload?: any) => staffBffApi.triggerStaffEod(payload),
  getStaffBatchRuns: () => staffBffApi.getStaffBatchRuns(),
  getStaffBatchRun: (batchRunId: number) => staffBffApi.getStaffBatchRun(batchRunId),
  getStaffDlqMessages: () => staffBffApi.getStaffDlqMessages(),
  replayStaffDlq: (recordId: string) => staffBffApi.replayStaffDlq(recordId),
};

// ============================================================================
// staffApi — The original facade used by staff-facing components
// ============================================================================
export const staffApi = {
  setAuthToken(token: string | null) {
    if (token) {
      coreClient.setSession({
        accessToken: token,
        userId: 1,
        username: 'staff',
        roles: ['ROLE_TELLER'],
        userType: 'TELLER',
      });
    } else {
      coreClient.setSession(null);
    }
  },
  async login(req: { username: string; password: string }): Promise<AuthData> {
    const session = await loginStaff(req.username, req.password);
    return {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken || '',
      userId: String(session.userId),
      username: session.username,
      roles: session.roles as any,
      branchCode: 'BR-0104 MAKATI',
    };
  },
  async logout(): Promise<void> {
    await coreClient.logout();
  },
  async getCustomer360(customerId: number): Promise<StaffCustomer360Response> {
    return staffBffApi.getStaffCustomer360(customerId);
  },
  async depositCash(payload: CounterCashPayload, idempotencyKey?: string) {
    return staffBffApi.executeCounterDeposit(payload, idempotencyKey);
  },
  async withdrawCash(payload: CounterCashPayload, idempotencyKey?: string) {
    return staffBffApi.executeCounterWithdrawal(payload, idempotencyKey);
  },
  async assistedTransfer(payload: CounterTransferPayload, idempotencyKey?: string) {
    return staffBffApi.executeCounterTransfer(payload, idempotencyKey);
  },
  async getQueuesSummary(): Promise<StaffQueuesSummaryResponse> {
    return staffBffApi.getStaffQueuesSummary();
  },
  async approveKyc(requestId: number) { return staffBffApi.approveStaffKyc(requestId); },
  async rejectKyc(requestId: number, reason: string) { return staffBffApi.rejectStaffKyc(requestId, reason); },
  async approveClosure(closureId: number) { return staffBffApi.approveStaffClosure(closureId); },
  async rejectClosure(closureId: number, reason: string) { return staffBffApi.rejectStaffClosure(closureId, reason); },
  async getAllAccounts(): Promise<StaffAccountSummary[]> { return staffBffApi.getStaffAccounts(); },
  async openAccount(payload: AccountOpenPayload) { return staffBffApi.openStaffAccount(payload); },
  async updateAccountStatus(accountId: number, status: string) { return staffBffApi.updateStaffAccountStatus(accountId, status); },
  async requestAccountClosure(accountId: number, reason: string) { return staffBffApi.requestStaffAccountClosure(accountId, reason); },
  async addAccountFlag(accountId: number, reason: string) { return staffBffApi.addStaffAccountFlag(accountId, reason); },
  async removeAccountFlag(accountId: number, flagId: number) { return staffBffApi.removeStaffAccountFlag(accountId, flagId); },
  async getTransactionTimeline(transactionId: string): Promise<TimelineResponse> { return staffBffApi.getStaffBffTimeline(transactionId); },
  async requestReversal(transactionId: string, reason: string, idempotencyKey?: string) { return staffBffApi.requestStaffReversal(transactionId, reason, idempotencyKey); },
  async getAccountStatement(accountId: number, page = 0, size = 20) { return staffBffApi.getStaffAccountStatement(accountId, page, size); },
  async verifyChain(accountId: number): Promise<ChainVerificationResult> { return staffBffApi.verifyStaffAccountChain(accountId); },
  async getDailySeal(businessDate: string): Promise<DailyAuditSealResponse> { return staffBffApi.getStaffDailySeal(businessDate) as Promise<DailyAuditSealResponse>; },
  async getAdminOverview(businessDate?: string): Promise<StaffAdminOverviewResponse> { return staffBffApi.getStaffAdminOverview(businessDate); },
  async triggerEodBatch(payload?: any) { return staffBffApi.triggerStaffEod(payload); },
  async getAllBatchRuns(): Promise<BatchRunDetail[]> { return staffBffApi.getStaffBatchRuns(); },
  async getBatchRun(batchRunId: number): Promise<BatchRunDetail> { return staffBffApi.getStaffBatchRun(batchRunId); },
  async getDlqMessages(): Promise<DeadLetterRecord[]> { return staffBffApi.getStaffDlqMessages(); },
  async replayDlqRecord(recordId: string) { return staffBffApi.replayStaffDlq(recordId); },
};
