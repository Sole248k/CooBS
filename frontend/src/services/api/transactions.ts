// ============================================================================
// Transactions Service — Fund transfers, saga transfers, reversals, timeline
// ============================================================================
import type {
  PendingTransferItem,
  ReversalRequest,
  ReversalResponse,
  SagaTransferRequest,
  TimelineResponse,
  TimelineStage,
  TransferRequest,
  TransferResponse,
} from '../../types';
import { coreClient } from '../client';
import { SEED_PENDING_TRANSFERS, SEED_NOTIFICATIONS } from '../seeds';
import { getLocalAccounts, getAllAccounts } from './accounts';

let localPendingTransfers: PendingTransferItem[] = [...SEED_PENDING_TRANSFERS];
let localTimelines: Map<string, TimelineResponse> = new Map();
let idempotencyCache: Map<string, TransferResponse> = new Map();
let localNotifications = [...SEED_NOTIFICATIONS];

// --------------------------------------------------------------------------
// Standard Ledger Transfer (customer portal)
// --------------------------------------------------------------------------
export async function executeTransfer(
  payload: TransferRequest,
  idempotencyKey: string,
  correlationId: string
): Promise<{ response: TransferResponse; isCachedReplay: boolean; latencyMs: number }> {
  const start = performance.now();
  const localAccounts = getLocalAccounts();

  try {
    const res = await fetch('/api/v1/ledger/transfers', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${coreClient.getSession()?.accessToken || ''}`,
        'Idempotency-Key': idempotencyKey,
        'X-Correlation-ID': correlationId
      },
      body: JSON.stringify(payload)
    });

    const latencyMs = Math.round(performance.now() - start);

    if (res.ok) {
      const data = await res.json();
      const txnId = data.transferReference || data.transactionId || data.transferId || data.id;
      const result: TransferResponse = {
        transactionId: txnId,
        referenceNo: data.transferReference || payload.referenceNo,
        sourceAccountId: payload.sourceAccountId,
        destinationAccountId: payload.destinationAccountId,
        amount: payload.amount,
        status: 'COMPLETED',
        timestamp: data.timestamp || new Date().toISOString(),
        isCachedReplay: res.headers.get('X-Cache') === 'HIT'
      };
      await getAllAccounts();
      return { response: result, isCachedReplay: !!result.isCachedReplay, latencyMs };
    } else {
      const errorJson = await res.json().catch(() => ({}));
      throw new Error(errorJson.detail || `Transfer rejected by gateway: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch') && !err.message.includes('NetworkError')) {
      throw err;
    }
  }

  // Standalone simulation
  const latencyMs = Math.round(performance.now() - start);

  if (idempotencyCache.has(idempotencyKey)) {
    const cached = idempotencyCache.get(idempotencyKey)!;
    return { response: { ...cached, isCachedReplay: true }, isCachedReplay: true, latencyMs: 3 };
  }

  const src = localAccounts.find(a => a.accountId === payload.sourceAccountId);
  const dst = localAccounts.find(a => a.accountId === payload.destinationAccountId);

  if (src && src.balance < payload.amount) {
    throw new Error(`Insufficient funds: Available balance is ₱${src.balance.toFixed(2)}, required ₱${payload.amount.toFixed(2)}.`);
  }

  if (src) src.balance = Math.round((src.balance - payload.amount) * 100) / 100;
  if (dst) dst.balance = Math.round((dst.balance + payload.amount) * 100) / 100;

  const newTxnId = 5000 + Math.floor(Math.random() * 9000);
  const newResponse: TransferResponse = {
    transactionId: newTxnId,
    referenceNo: payload.referenceNo,
    sourceAccountId: payload.sourceAccountId,
    destinationAccountId: payload.destinationAccountId,
    amount: payload.amount,
    status: 'COMPLETED',
    timestamp: new Date().toISOString(),
    isCachedReplay: false
  };

  idempotencyCache.set(idempotencyKey, newResponse);

  localNotifications.unshift({
    id: Date.now(),
    type: 'TRANSACTION_TRANSFER',
    message: `Transferred ₱${payload.amount.toFixed(2)} from #${src?.accountNumber || payload.sourceAccountId} to #${dst?.accountNumber || payload.destinationAccountId}. Ref: ${payload.referenceNo}`,
    isRead: false,
    createdAt: new Date().toISOString()
  });

  return { response: newResponse, isCachedReplay: false, latencyMs: Math.max(10, latencyMs) };
}

// --------------------------------------------------------------------------
// Pending Transfers (Dual-Control)
// --------------------------------------------------------------------------
export async function getPendingTransfers(): Promise<PendingTransferItem[]> {
  return localPendingTransfers.filter(t => t.status === 'PENDING');
}

export async function approvePendingTransfer(transferRequestId: number): Promise<void> {
  const item = localPendingTransfers.find(t => t.transferRequestId === transferRequestId);
  if (item) {
    item.status = 'APPROVED';
    const localAccounts = getLocalAccounts();
    const src = localAccounts.find(a => a.accountId === item.sourceAccountId);
    const dst = localAccounts.find(a => a.accountId === item.destinationAccountId);
    if (src && src.balance >= item.amount) {
      src.balance -= item.amount;
      if (dst) dst.balance += item.amount;
    }
    localNotifications.unshift({
      id: Date.now(),
      type: 'TRANSFER_APPROVED',
      message: `High-value transfer #${item.referenceNo} for ₱${item.amount.toLocaleString()} was APPROVED by Teller.`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

export async function rejectPendingTransfer(transferRequestId: number, reason: string): Promise<void> {
  const item = localPendingTransfers.find(t => t.transferRequestId === transferRequestId);
  if (item) {
    item.status = 'REJECTED';
    localNotifications.unshift({
      id: Date.now(),
      type: 'TRANSFER_REJECTED',
      message: `High-value transfer #${item.referenceNo} was REJECTED: ${reason}`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

// --------------------------------------------------------------------------
// Teller Cash Operations
// --------------------------------------------------------------------------
export async function depositCash(accountId: number, amount: number, referenceNo: string, remarks?: string): Promise<void> {
  const localAccounts = getLocalAccounts();
  try {
    const res = await fetch('/api/v1/ledger/credits', {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ accountId, amount, referenceNo, remarks })
    });
    if (res.ok) {
      await getAllAccounts();
      return;
    }
  } catch { /* fallback */ }

  const acc = localAccounts.find(a => a.accountId === accountId);
  if (acc) {
    acc.balance += amount;
    localNotifications.unshift({
      id: Date.now(),
      type: 'TRANSACTION_CREDIT',
      message: `Cash deposit of ₱${amount.toFixed(2)} completed for ${acc.accountNumber}.`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

export async function withdrawCash(accountId: number, amount: number, referenceNo: string, remarks?: string): Promise<void> {
  const localAccounts = getLocalAccounts();
  const acc = localAccounts.find(a => a.accountId === accountId);
  if (acc && acc.balance < amount) {
    throw new Error(`Insufficient funds: Account has ₱${acc.balance.toFixed(2)}, attempted withdrawal of ₱${amount.toFixed(2)}.`);
  }

  try {
    const res = await fetch('/api/v1/ledger/debits', {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ accountId, amount, referenceNo, remarks })
    });
    if (res.ok) {
      await getAllAccounts();
      return;
    }
  } catch { /* fallback */ }

  if (acc) {
    acc.balance -= amount;
    localNotifications.unshift({
      id: Date.now(),
      type: 'TRANSACTION_DEBIT',
      message: `Cash withdrawal of ₱${amount.toFixed(2)} processed from ${acc.accountNumber}.`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

// --------------------------------------------------------------------------
// Saga Transfer (Assisted Counter)
// --------------------------------------------------------------------------
export async function executeSagaTransfer(payload: SagaTransferRequest, idempotencyKey: string): Promise<TransferResponse> {
  const correlationId = coreClient.generateUuid();
  try {
    const res = await fetch('/api/v1/transactions/transfers', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${coreClient.getSession()?.accessToken || ''}`,
        'Idempotency-Key': idempotencyKey,
        'X-Correlation-ID': correlationId
      },
      body: JSON.stringify({
        sourceAccountId: payload.sourceAccountId,
        destinationAccountNumber: payload.destinationAccountNumber,
        amount: payload.amount,
        currency: payload.currency || 'PHP',
        description: payload.description || 'Assisted Counter Transfer'
      })
    });

    if (res.ok) {
      const data = await res.json();
      const ref = data.trackingReference || data.referenceNo || `TXN-${Date.now()}`;
      const resp: TransferResponse = {
        transactionId: data.transactionId || ref,
        referenceNo: ref,
        sourceAccountId: payload.sourceAccountId,
        destinationAccountId: data.destinationAccountId || 0,
        amount: payload.amount,
        status: 'COMPLETED',
        timestamp: data.timestamp || new Date().toISOString()
      };
      await getAllAccounts();
      return resp;
    } else {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Saga transfer failed: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch') && !err.message.includes('NetworkError')) {
      throw err;
    }
  }

  const localAccounts = getLocalAccounts();
  const src = localAccounts.find(a => a.accountId === payload.sourceAccountId);
  if (src && src.balance < payload.amount) {
    throw new Error(`Insufficient funds: Account #${src.accountNumber} has ₱${src.balance.toFixed(2)}, required ₱${payload.amount.toFixed(2)}.`);
  }

  const dst = localAccounts.find(a => a.accountNumber === payload.destinationAccountNumber);
  if (src) src.balance = Math.round((src.balance - payload.amount) * 100) / 100;
  if (dst) dst.balance = Math.round((dst.balance + payload.amount) * 100) / 100;

  const ref = `SAGA-${Math.floor(100000 + Math.random() * 900000)}`;
  const resp: TransferResponse = {
    transactionId: Date.now(),
    referenceNo: ref,
    sourceAccountId: payload.sourceAccountId,
    destinationAccountId: dst ? dst.accountId : 99,
    amount: payload.amount,
    status: 'COMPLETED',
    timestamp: new Date().toISOString()
  };

  createSimulatedTimeline(ref, payload.sourceAccountId, payload.destinationAccountNumber, payload.amount);

  localNotifications.unshift({
    id: Date.now(),
    type: 'SAGA_TRANSFER_COMPLETED',
    message: `Assisted Counter Transfer of ₱${payload.amount.toFixed(2)} to ${payload.destinationAccountNumber} completed. Ref: ${ref}`,
    isRead: false,
    createdAt: new Date().toISOString()
  });

  return resp;
}

// --------------------------------------------------------------------------
// Staff Transaction Timeline
// --------------------------------------------------------------------------
export async function getStaffTimeline(trackingRef: string): Promise<TimelineResponse> {
  try {
    const res = await fetch(`/api/v1/transactions/${encodeURIComponent(trackingRef)}/timeline?view=staff`, {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      if (data && Array.isArray(data.stages) && data.stages.length > 0) {
        return data;
      }
    }
  } catch {
    // fallback
  }

  if (localTimelines.has(trackingRef)) {
    return localTimelines.get(trackingRef)!;
  }

  return createSimulatedTimeline(trackingRef, 1, 'ACC-2001-4455', 2500.00);
}

export function createSimulatedTimeline(trackingRef: string, srcAccId: number, dstAccNum: string, amount: number): TimelineResponse {
  const now = Date.now();
  const stages: TimelineStage[] = [
    { stage: 'INITIATED', status: 'COMPLETED', service: 'Transaction Gateway', timestamp: new Date(now - 3200).toISOString(), latencyMs: 12, details: 'HTTP POST /api/v1/transactions/transfers received and validated. Idempotency verified.' },
    { stage: 'FRAUD_CHECK_PENDING', status: 'COMPLETED', service: 'Fraud Detection Engine', timestamp: new Date(now - 2800).toISOString(), latencyMs: 45, details: 'Velocity evaluation and AML threshold risk score calculation in progress.' },
    { stage: 'FRAUD_CHECK_PASSED', status: 'COMPLETED', service: 'Fraud Detection Engine', timestamp: new Date(now - 2500).toISOString(), latencyMs: 18, details: 'Risk score: 0.04 (LOW). Zero blacklisted beneficiary anomalies detected.' },
    { stage: 'SOURCE_DEBIT_PENDING', status: 'COMPLETED', service: 'Account Ledger Service', timestamp: new Date(now - 2100).toISOString(), latencyMs: 32, details: `Checking available balance and applying pessimistic lock on Source Account #${srcAccId}.` },
    { stage: 'SOURCE_DEBITED', status: 'COMPLETED', service: 'Account Ledger Service', timestamp: new Date(now - 1700).toISOString(), latencyMs: 28, details: `Source account #${srcAccId} successfully debited for ₱${amount.toFixed(2)}.` },
    { stage: 'DESTINATION_CREDIT_PENDING', status: 'COMPLETED', service: 'Account Ledger Service', timestamp: new Date(now - 1400).toISOString(), latencyMs: 25, details: `Resolving destination account ${dstAccNum} and preparing credit posting.` },
    { stage: 'DESTINATION_CREDITED', status: 'COMPLETED', service: 'Account Ledger Service', timestamp: new Date(now - 1000).toISOString(), latencyMs: 30, details: `Destination account ${dstAccNum} credited for ₱${amount.toFixed(2)}.` },
    { stage: 'LEDGER_COMMITTED', status: 'COMPLETED', service: 'Core Ledger Service', timestamp: new Date(now - 700).toISOString(), latencyMs: 52, details: 'Double-entry cryptographic ledger transaction journals written and sealed.' },
    { stage: 'NOTIFICATION_QUEUED', status: 'COMPLETED', service: 'Kafka Broker (events.notifications)', timestamp: new Date(now - 450).toISOString(), latencyMs: 14, details: 'Transaction notification event published to Kafka broker topic.' },
    { stage: 'NOTIFICATION_SENT', status: 'COMPLETED', service: 'Notification Service', timestamp: new Date(now - 200).toISOString(), latencyMs: 40, details: 'SMS and In-App push alerts dispatched to sender and recipient.' },
    { stage: 'COMPLETED', status: 'COMPLETED', service: 'Saga Orchestrator', timestamp: new Date(now).toISOString(), latencyMs: 10, details: 'Saga orchestrated lifecycle finalized with terminal state COMPLETED.' }
  ];

  const timeline: TimelineResponse = {
    trackingRef,
    currentStatus: 'COMPLETED',
    sourceAccountId: srcAccId,
    destinationAccountNumber: dstAccNum,
    amount,
    currency: 'PHP',
    stages,
    lastUpdated: new Date().toISOString()
  };
  localTimelines.set(trackingRef, timeline);
  return timeline;
}

// --------------------------------------------------------------------------
// Reversals
// --------------------------------------------------------------------------
export async function reverseTransaction(payload: ReversalRequest): Promise<ReversalResponse> {
  const reason = payload.remarks?.trim()
    ? `${payload.reason}: ${payload.remarks.trim()}`
    : payload.reason;

  const res = await fetch(
    `/api/v1/transactions/${encodeURIComponent(payload.originalTrackingRef)}/reversal`,
    {
      method: 'POST',
      headers: { ...coreClient.getAuthHeaders(), 'Idempotency-Key': coreClient.generateUuid() },
      body: JSON.stringify({ reason })
    }
  );

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.detail || data.message || `Reversal was not accepted (HTTP ${res.status}).`);
  }

  await getAllAccounts();
  return {
    reversalRef: data.referenceNo,
    originalTrackingRef: payload.originalTrackingRef,
    status: 'PENDING',
    reversalTimestamp: data.createdAt || new Date().toISOString(),
    message: `Reversal ${data.referenceNo} accepted and is being processed.`
  };
}
