// ============================================================================
// Customers Service — Customer Profile, KYC update requests, Customer 360
// ============================================================================
import type {
  Customer360Account,
  Customer360Response,
  CustomerProfile,
  KycRequestResponse,
  KycUpdateRequestDto,
} from '../../types';
import { coreClient } from '../client';
import { SEED_PROFILE, SEED_KYC_REQUESTS, SEED_CUSTOMER_360, SEED_NOTIFICATIONS } from '../seeds';
import { getLocalAccounts } from './accounts';

let localProfile: CustomerProfile = { ...SEED_PROFILE };
let localKycRequests: KycRequestResponse[] = [...SEED_KYC_REQUESTS];
let localNotifications = [...SEED_NOTIFICATIONS];

export function getLocalKycRequests() { return localKycRequests; }

// --------------------------------------------------------------------------
// Profile
// --------------------------------------------------------------------------
export async function getCustomerProfile(): Promise<CustomerProfile> {
  try {
    const res = await fetch('/api/v1/customers/me', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      localProfile = data;
      return data;
    }
  } catch {
    // fallback
  }
  return localProfile;
}

// --------------------------------------------------------------------------
// KYC Update Requests
// --------------------------------------------------------------------------
export async function submitKycUpdateRequest(payload: KycUpdateRequestDto): Promise<KycRequestResponse> {
  try {
    const res = await fetch('/api/v1/customers/kyc/update-request', {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify(payload)
    });
    if (res.ok) {
      return await res.json();
    } else {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || `KYC update request failed: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch')) throw err;
  }

  const newReq: KycRequestResponse = {
    kycRequestId: Date.now(),
    kycId: localProfile.kyc?.kycId || 101,
    customerId: localProfile.customerId,
    customerName: `${payload.newFirstName || localProfile.kyc?.firstName} ${payload.newLastName || localProfile.kyc?.lastName}`,
    status: 'PENDING',
    requestedAt: new Date().toISOString(),
    ...payload
  };
  localKycRequests.unshift(newReq);

  localNotifications.unshift({
    id: Date.now(),
    type: 'KYC_REVIEW_REQUIRED',
    message: `KYC update submitted by ${newReq.customerName}. Verification required.`,
    isRead: false,
    createdAt: new Date().toISOString()
  });

  return newReq;
}

export async function getPendingKycUpdateRequests(): Promise<KycRequestResponse[]> {
  try {
    const res = await fetch('/api/v1/customers/kyc/update-requests?status=PENDING', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      return data.content || data;
    }
  } catch {
    // fallback
  }
  return localKycRequests.filter(r => r.status === 'PENDING');
}

export async function approveKycUpdateRequest(id: number): Promise<void> {
  try {
    const res = await fetch(`/api/v1/customers/kyc/update-requests/${id}/approve`, {
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

  const req = localKycRequests.find(r => r.kycRequestId === id);
  if (req) {
    req.status = 'APPROVED';
    req.approvedAt = new Date().toISOString();
    if (localProfile.kyc) {
      if (req.newFirstName) localProfile.kyc.firstName = req.newFirstName;
      if (req.newMiddleInitial) localProfile.kyc.middleInitial = req.newMiddleInitial;
      if (req.newLastName) localProfile.kyc.lastName = req.newLastName;
      if (req.newAddress) localProfile.kyc.address = req.newAddress;
      if (req.newMobileNumber) localProfile.kyc.mobileNumber = req.newMobileNumber;
      if (req.newCivilStatus) localProfile.kyc.civilStatus = req.newCivilStatus;
      if (req.newOccupation) localProfile.kyc.occupation = req.newOccupation;
    }
    localNotifications.unshift({
      id: Date.now(),
      type: 'KYC_STATUS',
      message: 'Your profile KYC update has been APPROVED by the branch.',
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

export async function rejectKycUpdateRequest(id: number, reason: string): Promise<void> {
  try {
    const res = await fetch(`/api/v1/customers/kyc/update-requests/${id}/reject`, {
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

  const req = localKycRequests.find(r => r.kycRequestId === id);
  if (req) {
    req.status = 'REJECTED';
    req.rejectionReason = reason;
    localNotifications.unshift({
      id: Date.now(),
      type: 'KYC_STATUS',
      message: `Your KYC update request was rejected: ${reason}`,
      isRead: false,
      createdAt: new Date().toISOString()
    });
  }
}

// --------------------------------------------------------------------------
// Customer 360 (self-service portal view)
// --------------------------------------------------------------------------
export async function getCustomer360(customerId: number): Promise<Customer360Response> {
  const localAccounts = getLocalAccounts();

  try {
    const res = await fetch(`/api/v1/customers/${customerId}/360`, {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      // Backend Blocker B3 Handling: customer-service's internal call to account-service may lack
      // authorization credentials, returning an empty accounts list. Gracefully supplement.
      let accounts: Customer360Account[] = Array.isArray(data.accounts) && data.accounts.length > 0
        ? data.accounts
        : localAccounts
            .filter(a => a.customerId === customerId)
            .map(a => ({
              accountId: a.accountId,
              accountNumber: a.accountNumber,
              accountType: a.accountType,
              availableBalance: a.balance,
              currency: a.currency,
              status: a.status
            }));

      if (accounts.length === 0 && SEED_CUSTOMER_360[customerId]) {
        accounts = SEED_CUSTOMER_360[customerId].accounts;
      }

      const totalDeposits = accounts.reduce((sum, a) => sum + (Number(a.availableBalance) || 0), 0);

      return {
        customerId: data.customerId || customerId,
        fullName: data.fullName || data.customerName || `Customer #${customerId}`,
        email: data.email || `customer${customerId}@coobs.ph`,
        phoneNumber: data.phoneNumber || data.mobileNumber || '09170000000',
        civilStatus: data.civilStatus || 'SINGLE',
        address: data.address || 'Metro Manila, Philippines',
        kycStatus: data.kycStatus || 'VERIFIED',
        riskTier: (data.riskTier as any) || (customerId === 2 ? 'MEDIUM' : 'LOW'),
        totalDeposits: data.totalDeposits && data.totalDeposits > 0 ? Number(data.totalDeposits) : totalDeposits,
        totalLoans: Number(data.totalLoans || 0),
        accounts
      };
    }
  } catch {
    // offline / standalone fallback
  }

  if (SEED_CUSTOMER_360[customerId]) {
    const seed = SEED_CUSTOMER_360[customerId];
    const syncedAccounts = seed.accounts.map(acc => {
      const found = localAccounts.find(a => a.accountId === acc.accountId);
      return found ? { ...acc, availableBalance: found.balance, status: found.status } : acc;
    });
    const deposits = syncedAccounts.reduce((sum, a) => sum + a.availableBalance, 0);
    return { ...seed, accounts: syncedAccounts, totalDeposits: deposits };
  }

  const linked = localAccounts.filter(a => a.customerId === customerId);
  const deposits = linked.reduce((sum, a) => sum + a.balance, 0);
  return {
    customerId,
    fullName: linked[0]?.customerName || `Customer #${customerId}`,
    email: `customer${customerId}@coobs.ph`,
    phoneNumber: '09170000000',
    civilStatus: 'SINGLE',
    address: 'Metro Manila, Philippines',
    kycStatus: 'VERIFIED',
    riskTier: 'LOW',
    totalDeposits: deposits,
    totalLoans: 0.00,
    accounts: linked.map(a => ({
      accountId: a.accountId,
      accountNumber: a.accountNumber,
      accountType: a.accountType,
      availableBalance: a.balance,
      currency: a.currency,
      status: a.status
    }))
  };
}
