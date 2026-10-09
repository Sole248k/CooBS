// ============================================================================
// Accounts Service — Account directory, open/close, flags, status updates
// ============================================================================
import type { Account, AccountFlag, ClosureRequestResponse } from '../../types';
import { coreClient } from '../client';
import {
  SEED_ACCOUNTS,
  SEED_FLAGS,
  SEED_CLOSURE_REQUESTS,
  SEED_NOTIFICATIONS,
} from '../seeds';

// Module-level mutable state (mirrors the old ApiClient private fields)
let localAccounts: Account[] = [...SEED_ACCOUNTS];
let localFlags: (AccountFlag & { accountNumber?: string })[] = [...SEED_FLAGS];
let localClosureRequests: ClosureRequestResponse[] = [...SEED_CLOSURE_REQUESTS];
let localNotifications = [...SEED_NOTIFICATIONS];

// Allow other modules to share the notifications array reference
export function getLocalNotifications() { return localNotifications; }
export function getLocalAccounts() { return localAccounts; }

// --------------------------------------------------------------------------
// Account Queries
// --------------------------------------------------------------------------
export async function getAccounts(): Promise<Account[]> {
  try {
    const res = await fetch('/api/v1/accounts/my-accounts', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      let data = await res.json();
      if (data && typeof data === 'object' && !Array.isArray(data)) {
        if (Array.isArray(data.data)) data = data.data;
        else if (Array.isArray(data.items)) data = data.items;
        else if (Array.isArray(data.content)) data = data.content;
        else if (data.accountId || data.accountNumber) data = [data];
      }
      if (Array.isArray(data) && data.length > 0) {
        const session = coreClient.getSession();
        localAccounts = data.map((acc: any) => ({
          accountId: Number(acc.accountId) || 1,
          accountNumber: acc.accountNumber || '',
          accountType: acc.accountType || 'SAVINGS',
          balance: acc.balance !== undefined ? Number(acc.balance) : Number(acc.availableBalance || 0),
          currency: acc.currency || 'PHP',
          status: acc.status || 'ACTIVE',
          customerId: acc.customerId || session?.userId || 1,
          customerName: acc.customerName,
          createdAt: acc.createdAt
        }));
        return localAccounts;
      }
    }
  } catch {
    // fallback
  }

  const session = coreClient.getSession();
  if (session?.userType === 'CUSTOMER') {
    const targetCid = session.userId || 1;
    const filtered = localAccounts.filter(a => a.customerId === targetCid);
    if (filtered.length > 0) return filtered;
  }
  return [...localAccounts];
}

export async function getAllAccounts(): Promise<Account[]> {
  try {
    const res = await fetch('/api/v1/accounts', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data)) {
        const mapped = data.map((acc: any) => ({
          accountId: Number(acc.accountId),
          accountNumber: acc.accountNumber || `ACC-${acc.accountId}`,
          accountType: acc.accountType || 'SAVINGS',
          balance: acc.balance !== undefined && acc.balance !== null
            ? Number(acc.balance)
            : (acc.availableBalance !== undefined && acc.availableBalance !== null ? Number(acc.availableBalance) : 0),
          currency: acc.currency || 'PHP',
          status: acc.status || 'ACTIVE',
          customerId: acc.customerId ? Number(acc.customerId) : 1,
          customerName: acc.customerName || `Customer #${acc.customerId || 1}`,
          createdAt: acc.createdAt
        }));
        localAccounts = mapped;
        return mapped;
      }
    }
  } catch {
    // fallback
  }
  return [...localAccounts];
}

// --------------------------------------------------------------------------
// Account Flags
// --------------------------------------------------------------------------
export async function getAllFlags(): Promise<(AccountFlag & { accountNumber?: string })[]> {
  try {
    const flags: (AccountFlag & { accountNumber?: string })[] = [];
    for (const acc of localAccounts) {
      const res = await fetch(`/api/v1/accounts/${acc.accountId}/flags`, {
        headers: coreClient.getAuthHeaders()
      });
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data)) {
          data.forEach((f: any) => {
            flags.push({
              flagId: Number(f.flagId),
              accountId: Number(f.accountId || acc.accountId),
              accountNumber: acc.accountNumber,
              reason: f.reason || 'Compliance Hold',
              status: f.status || 'ACTIVE',
              flaggedBy: f.flaggedBy ? Number(f.flaggedBy) : 1,
              flaggedAt: f.flaggedAt || new Date().toISOString(),
              removedBy: f.removedBy ? Number(f.removedBy) : undefined,
              removedAt: f.removedAt || undefined
            });
          });
        }
      }
    }
    if (flags.length > 0) {
      localFlags = flags;
      return flags.filter(f => f.status === 'ACTIVE');
    }
  } catch {
    // fallback
  }
  return localFlags.filter(f => f.status === 'ACTIVE');
}

export async function getAccountFlags(accountId: number): Promise<AccountFlag[]> {
  try {
    const res = await fetch(`/api/v1/accounts/${accountId}/flags`, {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data)) return data;
    }
  } catch { /* fallback */ }
  return localFlags.filter(f => f.accountId === accountId);
}

export async function addAccountFlag(accountId: number, reason: string): Promise<AccountFlag> {
  try {
    const res = await fetch(`/api/v1/accounts/${accountId}/flags`, {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ reason })
    });
    if (res.ok) {
      await getAllAccounts();
      return await res.json();
    } else {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Failed to add flag: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }

  const target = localAccounts.find(a => a.accountId === accountId);
  if (target) target.status = 'FROZEN';

  const newFlag: AccountFlag & { accountNumber?: string } = {
    flagId: Date.now(),
    accountId,
    accountNumber: target?.accountNumber || `ACC-${accountId}`,
    reason,
    status: 'ACTIVE',
    flaggedBy: coreClient.getSession()?.userId || 1,
    flaggedAt: new Date().toISOString()
  };
  localFlags.unshift(newFlag);

  localNotifications.unshift({
    id: Date.now(),
    type: 'SECURITY_HOLD',
    message: `Risk hold imposed on Account #${target?.accountNumber || accountId}: ${reason}`,
    isRead: false,
    createdAt: new Date().toISOString()
  });

  return newFlag;
}

export async function removeAccountFlag(accountId: number, flagId: number): Promise<void> {
  try {
    const res = await fetch(`/api/v1/accounts/${accountId}/flags/${flagId}`, {
      method: 'DELETE',
      headers: coreClient.getAuthHeaders()
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Failed to remove flag: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }

  const f = localFlags.find(item => item.flagId === flagId);
  if (f) {
    f.status = 'REMOVED';
    f.removedBy = coreClient.getSession()?.userId || 1;
    f.removedAt = new Date().toISOString();
  }

  const remainingActive = localFlags.filter(item => item.accountId === accountId && item.status === 'ACTIVE');
  if (remainingActive.length === 0) {
    const acc = localAccounts.find(a => a.accountId === accountId);
    if (acc && acc.status === 'FROZEN') acc.status = 'ACTIVE';
  }

  localNotifications.unshift({
    id: Date.now(),
    type: 'SECURITY_HOLD',
    message: `Compliance hold lifted from Account #${accountId} by administrator.`,
    isRead: false,
    createdAt: new Date().toISOString()
  });
}

// --------------------------------------------------------------------------
// Account Lifecycle
// --------------------------------------------------------------------------
export async function openAccount(customerId: number, accountType: string, initialDeposit = 0): Promise<Account> {
  try {
    const res = await fetch('/api/v1/accounts', {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ customerId, accountType, currency: 'PHP' })
    });
    if (res.ok) {
      await getAccounts();
      return await res.json();
    }
  } catch {
    // fallback
  }

  const newId = localAccounts.length + 1;
  const newAcc: Account = {
    accountId: newId,
    accountNumber: `ACC-100${newId}-${Math.floor(1000 + Math.random() * 9000)}`,
    accountType: accountType as any,
    balance: initialDeposit,
    currency: 'PHP',
    status: 'ACTIVE',
    customerId,
    customerName: customerId === 1 ? 'John Doe' : `Customer #${customerId}`,
    createdAt: new Date().toISOString()
  };
  localAccounts.push(newAcc);
  return newAcc;
}

export async function updateAccountStatus(
  accountId: number,
  status: 'ACTIVE' | 'FROZEN' | 'DORMANT' | 'CLOSED'
): Promise<void> {
  try {
    const res = await fetch(`/api/v1/accounts/${accountId}/status`, {
      method: 'PATCH',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ status })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Failed to update status: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }
  const acc = localAccounts.find(a => a.accountId === accountId);
  if (acc) acc.status = status;
}

// --------------------------------------------------------------------------
// Closure Requests
// --------------------------------------------------------------------------
export async function submitAccountClosureRequest(accountId: number, reason?: string): Promise<ClosureRequestResponse> {
  const target = localAccounts.find(a => a.accountId === accountId);
  if (target && target.balance > 0) {
    throw new Error(`Cannot close account: Remaining balance of ₱${target.balance.toFixed(2)} must be ₱0.00.`);
  }

  try {
    const res = await fetch(`/api/v1/accounts/${accountId}/closure-request`, {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ reason: reason || 'Customer requested closure' })
    });
    if (res.ok) {
      return await res.json();
    } else {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Closure request failed: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }

  const newReq: ClosureRequestResponse = {
    closureRequestId: Date.now(),
    accountId,
    accountNumber: target?.accountNumber || `ACC-${accountId}`,
    accountType: target?.accountType || 'SAVINGS',
    customerId: target?.customerId || 1,
    customerName: target?.customerName || 'John Doe',
    availableBalance: target?.balance || 0,
    reason: reason || 'Customer requested closure',
    status: 'PENDING',
    requestedAt: new Date().toISOString()
  };
  localClosureRequests.unshift(newReq);

  localNotifications.unshift({
    id: Date.now(),
    type: 'CLOSURE_REVIEW_REQUIRED',
    message: `Account closure requested for ${newReq.accountNumber} with ₱0.00 balance. Awaiting admin review.`,
    isRead: false,
    createdAt: new Date().toISOString()
  });

  return newReq;
}

export async function getPendingClosureRequests(): Promise<ClosureRequestResponse[]> {
  try {
    const res = await fetch('/api/v1/accounts/closure-requests?status=PENDING', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      return data.content || data;
    }
  } catch {
    // fallback
  }
  return localClosureRequests.filter(r => r.status === 'PENDING');
}

export async function getMyClosureRequests(): Promise<ClosureRequestResponse[]> {
  return [...localClosureRequests];
}

export async function approveClosureRequest(id: number): Promise<void> {
  try {
    const res = await fetch(`/api/v1/accounts/closure-requests/${id}/approve`, {
      method: 'POST',
      headers: coreClient.getAuthHeaders()
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || 'Approval failed');
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }

  const req = localClosureRequests.find(r => r.closureRequestId === id);
  if (req) {
    req.status = 'APPROVED';
    const acc = localAccounts.find(a => a.accountId === req.accountId);
    if (acc) acc.status = 'CLOSED';

    localNotifications.unshift({
      id: Date.now(),
      type: 'CLOSURE_STATUS',
      message: `Your request to close Account #${req.accountNumber} has been APPROVED. The account is now closed.`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

export async function rejectClosureRequest(id: number, reason: string): Promise<void> {
  try {
    const res = await fetch(`/api/v1/accounts/closure-requests/${id}/reject`, {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify({ rejectionReason: reason })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || 'Rejection failed');
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }

  const req = localClosureRequests.find(r => r.closureRequestId === id);
  if (req) {
    req.status = 'REJECTED';
    req.rejectionReason = reason;

    localNotifications.unshift({
      id: Date.now(),
      type: 'CLOSURE_STATUS',
      message: `Your request to close Account #${req.accountNumber} was rejected: ${reason}`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}
