// ============================================================================
// Staff BFF Service — All /api/v1/staff/** endpoints
// Backed by backend/staff-bff on Port 8095 via API Gateway
// ============================================================================
import type {
  AccountOpenPayload,
  BatchRunDetail,
  ChainVerificationResult,
  CounterCashPayload,
  CounterTransferPayload,
  DailyAuditSealResponse,
  DeadLetterRecord,
  StaffAccountSummary,
  StaffAdminOverviewResponse,
  StaffCustomer360Response,
  StaffQueuesSummaryResponse,
  TimelineResponse,
} from '../../types';
import { coreClient } from '../client';

// --------------------------------------------------------------------------
// Customer 360 (Staff BFF view)
// --------------------------------------------------------------------------
export async function getStaffCustomer360(customerId: number): Promise<StaffCustomer360Response> {
  const res = await fetch(`/api/v1/staff/customers/${customerId}/360`, {
    headers: coreClient.getAuthHeaders()
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || `Failed to fetch customer 360: HTTP ${res.status}`);
  }
  return await res.json();
}

// --------------------------------------------------------------------------
// Counter Operations
// --------------------------------------------------------------------------
export async function executeCounterDeposit(payload: CounterCashPayload, idempotencyKey?: string): Promise<any> {
  const res = await fetch('/api/v1/staff/counter/deposit', {
    method: 'POST',
    headers: { ...coreClient.getAuthHeaders(), 'Idempotency-Key': idempotencyKey || coreClient.generateUuid() },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || `Deposit failed: HTTP ${res.status}`);
  }
  return await res.json();
}

export async function executeCounterWithdrawal(payload: CounterCashPayload, idempotencyKey?: string): Promise<any> {
  const res = await fetch('/api/v1/staff/counter/withdraw', {
    method: 'POST',
    headers: { ...coreClient.getAuthHeaders(), 'Idempotency-Key': idempotencyKey || coreClient.generateUuid() },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || `Withdrawal failed: HTTP ${res.status}`);
  }
  return await res.json();
}

export async function executeCounterTransfer(payload: CounterTransferPayload, idempotencyKey?: string): Promise<any> {
  const res = await fetch('/api/v1/staff/counter/transfer', {
    method: 'POST',
    headers: { ...coreClient.getAuthHeaders(), 'Idempotency-Key': idempotencyKey || coreClient.generateUuid() },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || `Assisted transfer failed: HTTP ${res.status}`);
  }
  return await res.json();
}

// --------------------------------------------------------------------------
// Queues
// --------------------------------------------------------------------------
export async function getStaffQueuesSummary(): Promise<StaffQueuesSummaryResponse> {
  const res = await fetch('/api/v1/staff/queues/summary', { headers: coreClient.getAuthHeaders() });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || `Failed to fetch queues: HTTP ${res.status}`);
  }
  return await res.json();
}

export async function approveStaffKyc(requestId: number): Promise<any> {
  const res = await fetch(`/api/v1/staff/queues/kyc/${requestId}/approve`, { method: 'POST', headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to approve KYC: HTTP ${res.status}`); }
  return await res.json();
}

export async function rejectStaffKyc(requestId: number, reason: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/queues/kyc/${requestId}/reject`, {
    method: 'POST', headers: coreClient.getAuthHeaders(), body: JSON.stringify({ reason })
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to reject KYC: HTTP ${res.status}`); }
  return await res.json();
}

export async function approveStaffClosure(closureId: number): Promise<any> {
  const res = await fetch(`/api/v1/staff/queues/closures/${closureId}/approve`, { method: 'POST', headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to approve closure: HTTP ${res.status}`); }
  return await res.json();
}

export async function rejectStaffClosure(closureId: number, reason: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/queues/closures/${closureId}/reject`, {
    method: 'POST', headers: coreClient.getAuthHeaders(), body: JSON.stringify({ reason })
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to reject closure: HTTP ${res.status}`); }
  return await res.json();
}

// --------------------------------------------------------------------------
// Accounts (Staff BFF)
// --------------------------------------------------------------------------
export async function getStaffAccounts(): Promise<StaffAccountSummary[]> {
  const res = await fetch('/api/v1/staff/accounts', { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch accounts: HTTP ${res.status}`); }
  return await res.json();
}

export async function openStaffAccount(payload: AccountOpenPayload): Promise<any> {
  const res = await fetch('/api/v1/staff/accounts', { method: 'POST', headers: coreClient.getAuthHeaders(), body: JSON.stringify(payload) });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to open account: HTTP ${res.status}`); }
  return await res.json();
}

export async function updateStaffAccountStatus(accountId: number, status: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/accounts/${accountId}/status`, {
    method: 'PATCH', headers: coreClient.getAuthHeaders(), body: JSON.stringify({ status })
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to update status: HTTP ${res.status}`); }
  return await res.json();
}

export async function requestStaffAccountClosure(accountId: number, reason: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/accounts/${accountId}/closure-request`, {
    method: 'POST',
    headers: coreClient.getAuthHeaders(),
    body: JSON.stringify({ reason })
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || err.message || `Failed to submit closure request: HTTP ${res.status}`);
  }
  return await res.json();
}

export async function addStaffAccountFlag(accountId: number, reason: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/accounts/${accountId}/flags`, {
    method: 'POST', headers: coreClient.getAuthHeaders(), body: JSON.stringify({ reason })
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to add flag: HTTP ${res.status}`); }
  return await res.json();
}

export async function removeStaffAccountFlag(accountId: number, flagId: number): Promise<any> {
  const res = await fetch(`/api/v1/staff/accounts/${accountId}/flags/${flagId}`, {
    method: 'DELETE', headers: coreClient.getAuthHeaders()
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to remove flag: HTTP ${res.status}`); }
  return await res.json();
}

// --------------------------------------------------------------------------
// Transactions (Staff BFF)
// --------------------------------------------------------------------------
export async function getStaffBffTimeline(transactionId: string): Promise<TimelineResponse> {
  const res = await fetch(`/api/v1/staff/transactions/${transactionId}/timeline`, { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch timeline: HTTP ${res.status}`); }
  const data = await res.json();

  if (!data.transaction && !data.amount) {
    try {
      const txRes = await fetch(`/api/v1/transactions/${transactionId}`, { headers: coreClient.getAuthHeaders() });
      if (txRes.ok) {
        const txData = await txRes.json();
        data.transaction = txData;
        data.amount = txData.amount;
        data.currency = txData.currency;
        data.channel = txData.channel;
        data.sourceAccountId = txData.sourceAccountId;
        data.sourceAccountNumber = txData.sourceAccountNumber;
        data.destinationAccountId = txData.destinationAccountId;
        data.destinationAccountNumber = txData.destinationAccountNumber;
        data.createdAt = txData.createdAt;
        data.completedAt = txData.completedAt;
        data.reversedByTransactionId = txData.reversedByTransactionId;
      }
    } catch {
      // Non-critical enrichment failure
    }
  }

  return data;
}

export async function requestStaffReversal(transactionId: string, reason: string, idempotencyKey?: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/transactions/${transactionId}/reversal`, {
    method: 'POST',
    headers: { ...coreClient.getAuthHeaders(), 'Idempotency-Key': idempotencyKey || coreClient.generateUuid() },
    body: JSON.stringify({ reason })
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Reversal failed: HTTP ${res.status}`); }
  return await res.json();
}

// --------------------------------------------------------------------------
// Audit (Staff BFF)
// --------------------------------------------------------------------------
export async function getStaffAccountStatement(accountId: number, page = 0, size = 20): Promise<any> {
  const res = await fetch(`/api/v1/staff/audit/accounts/${accountId}/statement?page=${page}&size=${size}`, { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch statement: HTTP ${res.status}`); }
  return await res.json();
}

export async function verifyStaffAccountChain(accountId: number): Promise<ChainVerificationResult> {
  const res = await fetch(`/api/v1/staff/audit/verify-chain/${accountId}`, { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to verify chain: HTTP ${res.status}`); }
  return await res.json();
}

export async function getStaffDailySeal(businessDate: string): Promise<DailyAuditSealResponse | null> {
  const res = await fetch(`/api/v1/staff/audit/seals/${businessDate}`, { headers: coreClient.getAuthHeaders() });
  if (!res.ok) {
    if (res.status === 404) return null;
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || `Failed to fetch daily seal: HTTP ${res.status}`);
  }
  return await res.json();
}

// --------------------------------------------------------------------------
// Admin (Staff BFF)
// --------------------------------------------------------------------------
export async function getStaffAdminOverview(businessDate?: string): Promise<StaffAdminOverviewResponse> {
  const url = businessDate ? `/api/v1/staff/admin/overview?businessDate=${businessDate}` : '/api/v1/staff/admin/overview';
  const res = await fetch(url, { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch admin overview: HTTP ${res.status}`); }
  return await res.json();
}

export async function triggerStaffEod(payload?: any): Promise<any> {
  const res = await fetch('/api/v1/staff/admin/batch/eod/trigger', {
    method: 'POST', headers: coreClient.getAuthHeaders(), body: JSON.stringify(payload || {})
  });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to trigger EOD: HTTP ${res.status}`); }
  return await res.json();
}

export async function getStaffBatchRuns(): Promise<BatchRunDetail[]> {
  const res = await fetch('/api/v1/staff/batch/eod/runs', { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch batch runs: HTTP ${res.status}`); }
  return await res.json();
}

export async function getStaffBatchRun(batchRunId: number): Promise<BatchRunDetail> {
  const res = await fetch(`/api/v1/staff/batch/eod/runs/${batchRunId}`, { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch batch run: HTTP ${res.status}`); }
  return await res.json();
}

export async function getStaffDlqMessages(): Promise<DeadLetterRecord[]> {
  const res = await fetch('/api/v1/staff/admin/dlq', { headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to fetch DLQ messages: HTTP ${res.status}`); }
  return await res.json();
}

export async function replayStaffDlq(recordId: string): Promise<any> {
  const res = await fetch(`/api/v1/staff/admin/dlq/${recordId}/replay`, { method: 'POST', headers: coreClient.getAuthHeaders() });
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.detail || `Failed to replay DLQ record: HTTP ${res.status}`); }
  return await res.json();
}
