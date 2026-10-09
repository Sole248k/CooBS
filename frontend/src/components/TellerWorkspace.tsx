import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Building2,
  Banknote,
  ArrowRightLeft,
  FileCheck2,
  ShieldCheck,
  CheckCircle2,
  XCircle,
  Search,
  RefreshCw,
  RotateCcw,
  Flag,
  PlusCircle,
  ArrowUpRight,
  ArrowDownLeft,
  ShieldAlert,
  ChevronRight,
  Database
} from 'lucide-react';
import type {
  Account,
  AccountFlag,
  BatchRunDetails,
  ClosureRequestResponse,
  Customer360Response,
  KycRequestResponse,
  PendingTransferItem,
  ReversalResponse,
  TimelineResponse,
  TransferResponse
} from '../types';
import { apiClient } from '../services/api';
import { AuditView } from './AuditView';

interface TellerWorkspaceProps {
  onRefreshGlobalData: () => void;
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export const TellerWorkspace: React.FC<TellerWorkspaceProps> = ({
  onRefreshGlobalData,
  showToast
}) => {
  // Navigation: 6 Terminal Tabs
  const [activeTab, setActiveTab] = useState<
    'customer360' | 'counter' | 'queues' | 'accounts' | 'saga' | 'audit'
  >('customer360');

  // Global Refresh & Auto-Poll Toggle (15s)
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [lastRefreshedAt, setLastRefreshedAt] = useState<Date>(new Date());
  const [isRefreshingAll, setIsRefreshingAll] = useState(false);

  // Accounts & Liquidity State
  const [allAccounts, setAllAccounts] = useState<Account[]>([]);
  const [allFlags, setAllFlags] = useState<(AccountFlag & { accountNumber?: string })[]>([]);

  // ==========================================================================
  // TAB 1: Customer 360 State
  // ==========================================================================
  const [searchCustomerId, setSearchCustomerId] = useState<string>('1');
  const [customer360, setCustomer360] = useState<Customer360Response | null>(null);
  const [c360Loading, setC360Loading] = useState(false);

  // ==========================================================================
  // TAB 2: Counter Desk (Cash & Assisted Saga Transfers)
  // ==========================================================================
  const [counterMode, setCounterMode] = useState<'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'SAGA_TRANSFER'>('CASH_DEPOSIT');
  const [cashAccountId, setCashAccountId] = useState<string>('1');
  const [cashAmount, setCashAmount] = useState<string>('5000.00');
  const [cashRemarks, setCashRemarks] = useState<string>('Branch counter teller settlement');
  const [cashLoading, setCashLoading] = useState(false);

  // Assisted Counter Saga Transfer
  const [sagaSourceAccId, setSagaSourceAccId] = useState<string>('1');
  const [sagaDestAccNumber, setSagaDestAccNumber] = useState<string>('ACC-2001-4455');
  const [sagaTransferAmount, setSagaTransferAmount] = useState<string>('2500.00');
  const [sagaTransferRef, setSagaTransferRef] = useState<string>(() => `OTC-SAGA-${Math.floor(100000 + Math.random() * 900000)}`);
  const [sagaTransferDesc, setSagaTransferDesc] = useState<string>('Counter assisted fund transfer');
  const [sagaTransferLoading, setSagaTransferLoading] = useState(false);
  const [lastSagaResult, setLastSagaResult] = useState<TransferResponse | null>(null);

  // ==========================================================================
  // TAB 3: Approval Queues State
  // ==========================================================================
  const [queueSubTab, setQueueSubTab] = useState<'kyc' | 'closures' | 'transfers'>('kyc');
  const [pendingKyc, setPendingKyc] = useState<KycRequestResponse[]>([]);
  const [rejectKycModal, setRejectKycModal] = useState<{ id: number; reason: string } | null>(null);

  const [pendingClosures, setPendingClosures] = useState<ClosureRequestResponse[]>([]);

  const [pendingTransfers, setPendingTransfers] = useState<PendingTransferItem[]>([]);
  const [rejectTransferModal, setRejectTransferModal] = useState<{ id: number; reason: string } | null>(null);

  // ==========================================================================
  // TAB 4: Accounts Directory & Compliance Holds State
  // ==========================================================================
  const [newAccCustomerId, setNewAccCustomerId] = useState<string>('1');
  const [newAccType, setNewAccType] = useState<string>('SAVINGS');
  const [newAccInitialDeposit, setNewAccInitialDeposit] = useState<string>('1000.00');
  const [newAccLoading, setNewAccLoading] = useState(false);

  // Flag Account Modal
  const [flagModalAccount, setFlagModalAccount] = useState<Account | null>(null);
  const [flagReasonPreset, setFlagReasonPreset] = useState<string>('AML KYC Document Verification Required');
  const [flagReasonCustom, setFlagReasonCustom] = useState<string>('');
  const [flagSubmitting, setFlagSubmitting] = useState(false);

  // ==========================================================================
  // TAB 5: Saga Tracker & Reversal State
  // ==========================================================================
  const [sagaTrackingRef, setSagaTrackingRef] = useState<string>('SAGA-772910');
  const [timelineData, setTimelineData] = useState<TimelineResponse | null>(null);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [timelinePolling, setTimelinePolling] = useState(false);
  const timelinePollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Reversal Modal
  const [isReversalModalOpen, setIsReversalModalOpen] = useState(false);
  const [reversalRefTarget, setReversalRefTarget] = useState<string>('');
  const [reversalReason, setReversalReason] = useState<
    'CUSTOMER_REQUEST' | 'SYSTEM_ERROR' | 'SUSPECTED_FRAUD' | 'DUPLICATE_PROCESSING'
  >('CUSTOMER_REQUEST');
  const [reversalRemarks, setReversalRemarks] = useState<string>('Customer walk-in duplicate request cancellation');
  const [reversalLoading, setReversalLoading] = useState(false);
  const [reversalResult, setReversalResult] = useState<ReversalResponse | null>(null);

  // ==========================================================================
  // TAB 6: Audit & EOD Batch Monitor State
  // ==========================================================================
  const [batchRuns, setBatchRuns] = useState<BatchRunDetails[]>([]);
  const [batchLoading, setBatchLoading] = useState(false);

  // ==========================================================================
  // Data Loaders
  // ==========================================================================
  const loadAccountsAndLiquidity = useCallback(async () => {
    try {
      const list = await apiClient.getAllAccounts();
      setAllAccounts(list);
      const flags = await apiClient.getAllFlags();
      setAllFlags(flags);
    } catch {
      // fallback
    }
  }, []);

  const loadCustomer360 = useCallback(async (id: number) => {
    setC360Loading(true);
    try {
      const data = await apiClient.getCustomer360(id);
      setCustomer360(data);
    } catch (err: any) {
      showToast('error', `Failed to load Customer 360 profile: ${err.message || 'Unknown error'}`);
    } finally {
      setC360Loading(false);
    }
  }, [showToast]);

  const loadQueues = useCallback(async () => {
    try {
      const kycList = await apiClient.getPendingKycUpdateRequests();
      setPendingKyc(kycList);
      const closureList = await apiClient.getPendingClosureRequests();
      setPendingClosures(closureList);
      const transferList = await apiClient.getPendingTransfers();
      setPendingTransfers(transferList);
    } catch {
      // fallback
    }
  }, []);

  const loadTimeline = useCallback(async (ref: string) => {
    if (!ref.trim()) return;
    setTimelineLoading(true);
    try {
      const res = await apiClient.getStaffTimeline(ref.trim());
      setTimelineData(res);
    } catch (err: any) {
      showToast('error', `Could not fetch saga timeline for ${ref}: ${err.message || 'Error'}`);
    } finally {
      setTimelineLoading(false);
    }
  }, [showToast]);

  const loadBatch = useCallback(async () => {
    setBatchLoading(true);
    try {
      const runs = await apiClient.getBatchRuns();
      setBatchRuns(runs);
    } catch {
      // fallback
    } finally {
      setBatchLoading(false);
    }
  }, []);

  const refreshAllData = useCallback(async () => {
    setIsRefreshingAll(true);
    await Promise.all([
      loadAccountsAndLiquidity(),
      loadQueues(),
      loadBatch()
    ]);
    if (customer360) {
      await loadCustomer360(customer360.customerId);
    }
    setLastRefreshedAt(new Date());
    setIsRefreshingAll(false);
  }, [loadAccountsAndLiquidity, loadQueues, loadBatch, customer360, loadCustomer360]);

  // Initial mount
  useEffect(() => {
    refreshAllData();
    loadCustomer360(1);
    loadTimeline('SAGA-772910');
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Auto-refresh interval (15s)
  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(() => {
      refreshAllData();
    }, 15000);
    return () => clearInterval(interval);
  }, [autoRefresh, refreshAllData]);

  // Live timeline polling (3s)
  useEffect(() => {
    if (timelinePolling && sagaTrackingRef) {
      timelinePollingRef.current = setInterval(() => {
        loadTimeline(sagaTrackingRef);
      }, 3000);
    } else if (timelinePollingRef.current) {
      clearInterval(timelinePollingRef.current);
      timelinePollingRef.current = null;
    }
    return () => {
      if (timelinePollingRef.current) {
        clearInterval(timelinePollingRef.current);
      }
    };
  }, [timelinePolling, sagaTrackingRef, loadTimeline]);

  // ==========================================================================
  // Calculated Branch Metrics
  // ==========================================================================
  const totalBranchLiquidity = allAccounts
    .filter(a => a.status === 'ACTIVE')
    .reduce((sum, a) => sum + (Number(a.balance) || 0), 0);

  // ==========================================================================
  // Actions: Customer 360
  // ==========================================================================
  const handleSearchCustomerSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const id = parseInt(searchCustomerId, 10);
    if (isNaN(id) || id <= 0) {
      showToast('error', 'Please enter a valid positive numeric Customer ID.');
      return;
    }
    loadCustomer360(id);
  };

  // ==========================================================================
  // Actions: Counter Desk (Cash & Saga Transfers)
  // ==========================================================================
  const handleCashSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const accId = parseInt(cashAccountId, 10);
    const amt = parseFloat(cashAmount);

    if (isNaN(accId) || accId <= 0) {
      showToast('error', 'Please enter a valid Account ID.');
      return;
    }
    if (isNaN(amt) || amt <= 0) {
      showToast('error', 'Amount must be greater than zero.');
      return;
    }

    setCashLoading(true);
    try {
      const ref = `OTC-${counterMode === 'CASH_DEPOSIT' ? 'DEP' : 'WTH'}-${Math.floor(100000 + Math.random() * 900000)}`;
      if (counterMode === 'CASH_DEPOSIT') {
        await apiClient.depositCash(accId, amt, ref, cashRemarks);
        showToast('success', `Cash deposit of ₱${amt.toLocaleString('en-US', { minimumFractionDigits: 2 })} completed! Ref: ${ref}`);
      } else {
        await apiClient.withdrawCash(accId, amt, ref, cashRemarks);
        showToast('success', `Cash withdrawal of ₱${amt.toLocaleString('en-US', { minimumFractionDigits: 2 })} processed! Ref: ${ref}`);
      }
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Counter cash operation failed.');
    } finally {
      setCashLoading(false);
    }
  };

  const handleSagaTransferSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const srcId = parseInt(sagaSourceAccId, 10);
    const amt = parseFloat(sagaTransferAmount);

    if (isNaN(srcId) || srcId <= 0) {
      showToast('error', 'Please select a valid source account.');
      return;
    }
    if (!sagaDestAccNumber.trim()) {
      showToast('error', 'Destination account number is required.');
      return;
    }
    if (isNaN(amt) || amt <= 0) {
      showToast('error', 'Transfer amount must be greater than zero.');
      return;
    }

    setSagaTransferLoading(true);
    try {
      const key = `saga-key-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
      const res = await apiClient.executeSagaTransfer(
        {
          sourceAccountId: srcId,
          destinationAccountNumber: sagaDestAccNumber.trim(),
          amount: amt,
          currency: 'PHP',
          description: sagaTransferDesc
        },
        key
      );
      setLastSagaResult(res);
      showToast('success', `Saga Transfer authorized! Ref: ${res.referenceNo}`);
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Assisted counter transfer failed.');
    } finally {
      setSagaTransferLoading(false);
    }
  };

  // ==========================================================================
  // Actions: Approval Queues
  // ==========================================================================
  const handleApproveKyc = async (id: number) => {
    try {
      await apiClient.approveKycUpdateRequest(id);
      showToast('success', `KYC update request #${id} approved successfully.`);
      await loadQueues();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to approve KYC request.');
    }
  };

  const handleRejectKyc = async () => {
    if (!rejectKycModal) return;
    if (!rejectKycModal.reason.trim()) {
      showToast('error', 'Rejection reason is required for compliance audit trails.');
      return;
    }
    try {
      await apiClient.rejectKycUpdateRequest(rejectKycModal.id, rejectKycModal.reason);
      showToast('info', `KYC request #${rejectKycModal.id} rejected.`);
      setRejectKycModal(null);
      await loadQueues();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to reject KYC request.');
    }
  };

  const handleApproveTransfer = async (id: number) => {
    try {
      await apiClient.approvePendingTransfer(id);
      showToast('success', `High-value transfer #${id} authorized and executed.`);
      await loadQueues();
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to authorize high-value transfer.');
    }
  };

  const handleRejectTransfer = async () => {
    if (!rejectTransferModal) return;
    try {
      await apiClient.rejectPendingTransfer(rejectTransferModal.id, rejectTransferModal.reason || 'Declined by branch teller');
      showToast('info', `Transfer request declined.`);
      setRejectTransferModal(null);
      await loadQueues();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to decline transfer.');
    }
  };

  // ==========================================================================
  // Actions: Accounts & Compliance Flags
  // ==========================================================================
  const handleOpenAccountSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const custId = parseInt(newAccCustomerId, 10);
    const deposit = parseFloat(newAccInitialDeposit) || 0;

    if (isNaN(custId) || custId <= 0) {
      showToast('error', 'Customer ID must be a positive integer.');
      return;
    }

    setNewAccLoading(true);
    try {
      const newAcc = await apiClient.openAccount(custId, newAccType, deposit);
      showToast('success', `Opened new ${newAccType} account ${newAcc.accountNumber} for Customer #${custId}.`);
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to create account.');
    } finally {
      setNewAccLoading(false);
    }
  };

  const handleStatusChangeSubmit = async (accountId: number, status: 'ACTIVE' | 'FROZEN' | 'DORMANT' | 'CLOSED') => {
    try {
      await apiClient.updateAccountStatus(accountId, status);
      showToast('info', `Account #${accountId} status updated to ${status}.`);
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to update account status.');
    }
  };

  const handleApplyAccountFlag = async () => {
    if (!flagModalAccount) return;
    const finalReason = flagReasonPreset === 'OTHER' ? flagReasonCustom.trim() : flagReasonPreset;
    if (!finalReason) {
      showToast('error', 'Please specify a compliance hold reason.');
      return;
    }

    setFlagSubmitting(true);
    try {
      await apiClient.addAccountFlag(flagModalAccount.accountId, finalReason);
      showToast('success', `Compliance risk hold applied to Account #${flagModalAccount.accountNumber}. Status set to FROZEN.`);
      setFlagModalAccount(null);
      setFlagReasonCustom('');
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to apply risk flag.');
    } finally {
      setFlagSubmitting(false);
    }
  };

  // ==========================================================================
  // Actions: Saga Tracker & Reversals
  // ==========================================================================
  const handleReversalSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reversalRefTarget.trim()) {
      showToast('error', 'Target transaction tracking reference is required.');
      return;
    }

    setReversalLoading(true);
    try {
      const res = await apiClient.reverseTransaction({
        originalTrackingRef: reversalRefTarget.trim(),
        reason: reversalReason,
        remarks: reversalRemarks
      });
      setReversalResult(res);
      showToast('success', `Transaction reversal recorded: ${res.reversalRef}`);
      await loadAccountsAndLiquidity();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to process transaction reversal.');
    } finally {
      setReversalLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '1360px', margin: '0 auto', padding: '24px 20px', display: 'flex', flexDirection: 'column', gap: '24px' }}>

      {/* ===================================================================== */}
      {/* 1. TOP KPI SUMMARY RIBBON                                              */}
      {/* ===================================================================== */}
      <div style={{
        background: '#ffffff',
        border: '1px solid #e2e8f0',
        borderRadius: '16px',
        padding: '20px 24px',
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr)) auto',
        alignItems: 'center',
        gap: '20px',
        boxShadow: 'var(--shadow-card)'
      }}>
        {/* Metric 1: Branch Liquidity */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '11px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            <Banknote size={15} color="#059669" />
            <span>Branch Liquidity (PHP)</span>
          </div>
          <div style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a', marginTop: '4px', fontFamily: 'var(--font-mono)' }}>
            ₱{totalBranchLiquidity.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div style={{ fontSize: '11px', color: '#059669', marginTop: '2px', fontWeight: '600' }}>
            Active customer deposits balance
          </div>
        </div>

        {/* Metric 2: Pending KYC */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '11px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            <FileCheck2 size={15} color="#2563eb" />
            <span>Pending KYC Verifications</span>
          </div>
          <div style={{ fontSize: '22px', fontWeight: '800', color: pendingKyc.length > 0 ? '#2563eb' : '#0f172a', marginTop: '4px' }}>
            {pendingKyc.length} {pendingKyc.length === 1 ? 'Request' : 'Requests'}
          </div>
          <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
            {pendingKyc.length > 0 ? 'Action required in Queues' : 'Queue cleared'}
          </div>
        </div>

        {/* Metric 3: Pending Closures */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '11px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            <ShieldAlert size={15} color="#d97706" />
            <span>Pending Closures</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginTop: '4px' }}>
            <div style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a' }}>
              {pendingClosures.length}
            </div>
            <span style={{ fontSize: '10px', fontWeight: '700', padding: '2px 6px', borderRadius: '4px', background: '#fffbeb', color: '#b45309', border: '1px solid #fde68a' }}>
              TELLER REVIEW
            </span>
          </div>
          <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
            Branch Manager approval required
          </div>
        </div>

        {/* Metric 4: System Accounts */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#64748b', fontSize: '11px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            <Building2 size={15} color="#475569" />
            <span>Total Accounts</span>
          </div>
          <div style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a', marginTop: '4px' }}>
            {allAccounts.length}
          </div>
          <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
            {allAccounts.filter(a => a.status === 'FROZEN').length} under compliance freeze
          </div>
        </div>

        {/* Controls: Auto-Refresh & Manual Trigger */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-end',
          gap: '8px',
          borderLeft: '1px solid #e2e8f0',
          paddingLeft: '20px'
        }}>
          <button
            type="button"
            onClick={refreshAllData}
            disabled={isRefreshingAll}
            className="banking-btn-secondary"
            style={{ fontSize: '12px', padding: '8px 14px', display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            <RefreshCw size={13} className={isRefreshingAll ? 'spin-icon' : ''} />
            <span>{isRefreshingAll ? 'Refreshing...' : 'Refresh All'}</span>
          </button>

          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', color: '#64748b', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              style={{ width: '14px', height: '14px', cursor: 'pointer' }}
            />
            <span>Auto-refresh (15s)</span>
          </label>
          <span style={{ fontSize: '10px', color: '#94a3b8' }}>
            Updated: {lastRefreshedAt.toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* ===================================================================== */}
      {/* 2. 6-TAB TERMINAL NAVIGATION                                           */}
      {/* ===================================================================== */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        borderBottom: '1px solid #e2e8f0',
        paddingBottom: '4px',
        overflowX: 'auto'
      }}>
        <button
          type="button"
          onClick={() => setActiveTab('customer360')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            whiteSpace: 'nowrap',
            background: activeTab === 'customer360' ? '#eff6ff' : 'transparent',
            color: activeTab === 'customer360' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'customer360' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <Search size={16} />
          <span>Customer 360 View</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('counter')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            whiteSpace: 'nowrap',
            background: activeTab === 'counter' ? '#eff6ff' : 'transparent',
            color: activeTab === 'counter' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'counter' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <Banknote size={16} />
          <span>Counter Desk (Cash & Transfers)</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('queues')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            whiteSpace: 'nowrap',
            background: activeTab === 'queues' ? '#eff6ff' : 'transparent',
            color: activeTab === 'queues' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'queues' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <FileCheck2 size={16} />
          <span>Approval Queues ({pendingKyc.length + pendingTransfers.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('accounts')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            whiteSpace: 'nowrap',
            background: activeTab === 'accounts' ? '#eff6ff' : 'transparent',
            color: activeTab === 'accounts' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'accounts' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <Building2 size={16} />
          <span>Accounts Directory & Holds</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('saga')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            whiteSpace: 'nowrap',
            background: activeTab === 'saga' ? '#eff6ff' : 'transparent',
            color: activeTab === 'saga' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'saga' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <ArrowRightLeft size={16} />
          <span>Saga Tracker & Reversals</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('audit')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            whiteSpace: 'nowrap',
            background: activeTab === 'audit' ? '#eff6ff' : 'transparent',
            color: activeTab === 'audit' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'audit' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <ShieldCheck size={16} />
          <span>Audit & Batch Monitor</span>
        </button>
      </div>

      {/* ===================================================================== */}
      {/* TAB 1: CUSTOMER 360 DEGREE PROFILE & LINKED ACCOUNTS                  */}
      {/* ===================================================================== */}
      {activeTab === 'customer360' && (
        <div className="animate-fade" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Customer Search Bar */}
          <div className="banking-card" style={{ padding: '20px 24px' }}>
            <form onSubmit={handleSearchCustomerSubmit} style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: '1 1 320px' }}>
                <Search size={18} color="#2563eb" />
                <div style={{ flex: 1, display: 'flex', gap: '8px' }}>
                  <input
                    type="number"
                    min="1"
                    value={searchCustomerId}
                    onChange={(e) => setSearchCustomerId(e.target.value)}
                    placeholder="Enter Customer ID (e.g. 1 or 4)"
                    style={{ flex: 1, fontSize: '14px', padding: '10px 14px' }}
                  />
                  <button type="submit" disabled={c360Loading} className="banking-btn-primary" style={{ padding: '10px 18px', fontSize: '13px' }}>
                    {c360Loading ? 'Searching...' : 'Search 360'}
                  </button>
                </div>
              </div>

              {/* Quick Jump Buttons */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '12px', color: '#64748b' }}>Quick Profiles:</span>
                <button
                  type="button"
                  onClick={() => { setSearchCustomerId('1'); loadCustomer360(1); }}
                  className="banking-btn-secondary"
                  style={{ fontSize: '12px', padding: '6px 12px' }}
                >
                  #1 John Doe
                </button>
                <button
                  type="button"
                  onClick={() => { setSearchCustomerId('4'); loadCustomer360(4); }}
                  className="banking-btn-secondary"
                  style={{ fontSize: '12px', padding: '6px 12px' }}
                >
                  #4 Juan Dela Cruz
                </button>
              </div>
            </form>
          </div>

          {/* Customer Profile View */}
          {customer360 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Profile Card Header */}
              <div className="banking-card">
                <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'flex-start', gap: '16px', borderBottom: '1px solid #e2e8f0', paddingBottom: '18px' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <h2 style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a' }}>
                        {customer360.fullName}
                      </h2>
                      <span className="badge badge-active" style={{ fontSize: '11px', textTransform: 'uppercase' }}>
                        KYC: {customer360.kycStatus}
                      </span>
                      <span style={{
                        fontSize: '11px',
                        fontWeight: '700',
                        padding: '3px 10px',
                        borderRadius: '9999px',
                        background: customer360.riskTier === 'LOW' ? '#ecfdf5' : (customer360.riskTier === 'MEDIUM' ? '#fffbeb' : '#fef2f2'),
                        color: customer360.riskTier === 'LOW' ? '#059669' : (customer360.riskTier === 'MEDIUM' ? '#d97706' : '#dc2626'),
                        border: `1px solid ${customer360.riskTier === 'LOW' ? '#a7f3d0' : (customer360.riskTier === 'MEDIUM' ? '#fde68a' : '#fecaca')}`
                      }}>
                        Risk Tier: {customer360.riskTier}
                      </span>
                    </div>
                    <div style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
                      Customer ID: #{customer360.customerId} &bull; Email: {customer360.email} &bull; Mobile: {customer360.phoneNumber || 'N/A'}
                    </div>
                  </div>

                  {/* Financial Snapshot */}
                  <div style={{ display: 'flex', gap: '20px', textAlign: 'right' }}>
                    <div>
                      <div style={{ fontSize: '11px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase' }}>Total Deposits</div>
                      <div style={{ fontSize: '20px', fontWeight: '800', color: '#059669', fontFamily: 'var(--font-mono)', marginTop: '2px' }}>
                        ₱{customer360.totalDeposits.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '11px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase' }}>Total Loans</div>
                      <div style={{ fontSize: '20px', fontWeight: '800', color: '#475569', fontFamily: 'var(--font-mono)', marginTop: '2px' }}>
                        ₱{customer360.totalLoans.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Profile Details Metadata */}
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                  gap: '14px',
                  paddingTop: '18px',
                  fontSize: '13px'
                }}>
                  <div>
                    <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>Civil Status:</span>
                    <div style={{ fontWeight: '600', color: '#0f172a' }}>{customer360.civilStatus || 'SINGLE'}</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>Registered Address:</span>
                    <div style={{ fontWeight: '600', color: '#0f172a' }}>{customer360.address || 'Metro Manila, Philippines'}</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>Total Accounts Linked:</span>
                    <div style={{ fontWeight: '600', color: '#0f172a' }}>{customer360.accounts.length} Accounts</div>
                  </div>
                </div>
              </div>

              {/* Linked Accounts Directory */}
              <div className="banking-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>
                    Member Deposit Accounts ({customer360.accounts.length})
                  </h3>
                  <button
                    type="button"
                    onClick={() => {
                      setNewAccCustomerId(String(customer360.customerId));
                      setActiveTab('accounts');
                    }}
                    className="banking-btn-secondary"
                    style={{ fontSize: '12px', padding: '6px 12px' }}
                  >
                    <PlusCircle size={14} />
                    <span>Open Account for Member</span>
                  </button>
                </div>

                <div className="banking-table-container">
                  <table className="banking-table">
                    <thead>
                      <tr>
                        <th>Account ID</th>
                        <th>Account Number</th>
                        <th>Type</th>
                        <th>Available Balance</th>
                        <th>Status</th>
                        <th>Counter Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {customer360.accounts.map((acc) => (
                        <tr key={acc.accountId}>
                          <td style={{ fontWeight: '600' }}>#{acc.accountId}</td>
                          <td style={{ fontWeight: '700', fontFamily: 'var(--font-mono)' }}>{acc.accountNumber}</td>
                          <td><span className="badge badge-neutral">{acc.accountType}</span></td>
                          <td style={{ fontWeight: '700', fontFamily: 'var(--font-mono)' }}>
                            ₱{Number(acc.availableBalance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                          </td>
                          <td>
                            <span className={`badge badge-${acc.status.toLowerCase()}`}>{acc.status}</span>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button
                                type="button"
                                onClick={() => {
                                  setCashAccountId(String(acc.accountId));
                                  setSagaSourceAccId(String(acc.accountId));
                                  setActiveTab('counter');
                                }}
                                className="banking-btn-secondary"
                                style={{ padding: '4px 10px', fontSize: '11px', color: '#2563eb' }}
                              >
                                Transact at Counter
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 2: COUNTER DESK (CASH & ASSISTED SAGA TRANSFERS)                   */}
      {/* ===================================================================== */}
      {activeTab === 'counter' && (
        <div className="animate-fade" style={{ display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '840px', margin: '0 auto', width: '100%' }}>
          {/* Submode Switcher */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '10px' }}>
            <button
              type="button"
              onClick={() => setCounterMode('CASH_DEPOSIT')}
              style={{
                padding: '12px',
                borderRadius: '10px',
                fontWeight: '700',
                fontSize: '13px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                background: counterMode === 'CASH_DEPOSIT' ? '#ecfdf5' : '#ffffff',
                color: counterMode === 'CASH_DEPOSIT' ? '#059669' : '#64748b',
                border: counterMode === 'CASH_DEPOSIT' ? '2px solid #059669' : '1px solid #e2e8f0',
                boxShadow: counterMode === 'CASH_DEPOSIT' ? 'var(--shadow-sm)' : 'none'
              }}
            >
              <ArrowDownLeft size={16} />
              <span>Cash Deposit (Credit)</span>
            </button>

            <button
              type="button"
              onClick={() => setCounterMode('CASH_WITHDRAWAL')}
              style={{
                padding: '12px',
                borderRadius: '10px',
                fontWeight: '700',
                fontSize: '13px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                background: counterMode === 'CASH_WITHDRAWAL' ? '#fef2f2' : '#ffffff',
                color: counterMode === 'CASH_WITHDRAWAL' ? '#dc2626' : '#64748b',
                border: counterMode === 'CASH_WITHDRAWAL' ? '2px solid #dc2626' : '1px solid #e2e8f0',
                boxShadow: counterMode === 'CASH_WITHDRAWAL' ? 'var(--shadow-sm)' : 'none'
              }}
            >
              <ArrowUpRight size={16} />
              <span>Cash Withdrawal (Debit)</span>
            </button>

            <button
              type="button"
              onClick={() => setCounterMode('SAGA_TRANSFER')}
              style={{
                padding: '12px',
                borderRadius: '10px',
                fontWeight: '700',
                fontSize: '13px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                background: counterMode === 'SAGA_TRANSFER' ? '#eff6ff' : '#ffffff',
                color: counterMode === 'SAGA_TRANSFER' ? '#2563eb' : '#64748b',
                border: counterMode === 'SAGA_TRANSFER' ? '2px solid #2563eb' : '1px solid #e2e8f0',
                boxShadow: counterMode === 'SAGA_TRANSFER' ? 'var(--shadow-sm)' : 'none'
              }}
            >
              <ArrowRightLeft size={16} />
              <span>Assisted Saga Transfer</span>
            </button>
          </div>

          {/* Form: Cash Deposit or Withdrawal */}
          {(counterMode === 'CASH_DEPOSIT' || counterMode === 'CASH_WITHDRAWAL') && (
            <div className="banking-card">
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
                <div style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '10px',
                  background: counterMode === 'CASH_DEPOSIT' ? '#ecfdf5' : '#fef2f2',
                  color: counterMode === 'CASH_DEPOSIT' ? '#059669' : '#dc2626',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <Banknote size={22} />
                </div>
                <div>
                  <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#0f172a' }}>
                    Over-The-Counter {counterMode === 'CASH_DEPOSIT' ? 'Cash Deposit' : 'Cash Withdrawal'}
                  </h3>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>
                    Direct ledger debit/credit posting at teller station.
                  </p>
                </div>
              </div>

              <form onSubmit={handleCashSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Target Account
                  </label>
                  <select
                    value={cashAccountId}
                    onChange={(e) => setCashAccountId(e.target.value)}
                    style={{ width: '100%' }}
                  >
                    {allAccounts.map(acc => (
                      <option key={acc.accountId} value={acc.accountId}>
                        #{acc.accountId} &bull; {acc.accountNumber} ({acc.accountType}) &mdash; Balance: ₱{acc.balance.toFixed(2)} [{acc.customerName || `Customer #${acc.customerId}`}]
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Amount (PHP ₱)
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      type="number"
                      step="0.01"
                      min="1"
                      value={cashAmount}
                      onChange={(e) => setCashAmount(e.target.value)}
                      required
                      style={{ paddingLeft: '32px', fontSize: '16px', fontWeight: '600' }}
                    />
                    <span style={{ position: 'absolute', left: '12px', top: '10px', fontWeight: '700', color: '#64748b' }}>₱</span>
                  </div>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Teller Remarks / Audit Description
                  </label>
                  <input
                    type="text"
                    value={cashRemarks}
                    onChange={(e) => setCashRemarks(e.target.value)}
                    placeholder="e.g. Branch counter teller settlement"
                  />
                </div>

                <button
                  type="submit"
                  disabled={cashLoading}
                  className="banking-btn-primary"
                  style={{
                    marginTop: '8px',
                    padding: '14px',
                    fontSize: '14px',
                    background: counterMode === 'CASH_DEPOSIT' ? '#059669' : '#dc2626'
                  }}
                >
                  {cashLoading ? 'Processing...' : `Confirm Cash ${counterMode === 'CASH_DEPOSIT' ? 'Deposit' : 'Withdrawal'}`}
                </button>
              </form>
            </div>
          )}

          {/* Form: Assisted Counter Saga Transfer */}
          {counterMode === 'SAGA_TRANSFER' && (
            <div className="banking-card">
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
                <div style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '10px',
                  background: '#eff6ff',
                  color: '#2563eb',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <ArrowRightLeft size={22} />
                </div>
                <div>
                  <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#0f172a' }}>
                    Assisted Counter Saga Transfer
                  </h3>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>
                    Dispatch an orchestrated multi-service saga transfer through the Transaction Gateway.
                  </p>
                </div>
              </div>

              <form onSubmit={handleSagaTransferSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Source Debit Account
                  </label>
                  <select
                    value={sagaSourceAccId}
                    onChange={(e) => setSagaSourceAccId(e.target.value)}
                    style={{ width: '100%' }}
                  >
                    {allAccounts.map(acc => (
                      <option key={acc.accountId} value={acc.accountId}>
                        #{acc.accountId} &bull; {acc.accountNumber} ({acc.accountType}) &mdash; Balance: ₱{acc.balance.toFixed(2)} [{acc.customerName}]
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Destination Account Number
                  </label>
                  <input
                    type="text"
                    value={sagaDestAccNumber}
                    onChange={(e) => setSagaDestAccNumber(e.target.value)}
                    placeholder="e.g. ACC-2001-4455 or ACC-4001-1122"
                    required
                    style={{ fontFamily: 'var(--font-mono)' }}
                  />
                  <div style={{ fontSize: '11px', color: '#64748b', marginTop: '4px' }}>
                    Target destination account number string required by Saga Orchestrator DTO.
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                      Transfer Amount (PHP ₱)
                    </label>
                    <div style={{ position: 'relative' }}>
                      <input
                        type="number"
                        step="0.01"
                        min="1"
                        value={sagaTransferAmount}
                        onChange={(e) => setSagaTransferAmount(e.target.value)}
                        required
                        style={{ paddingLeft: '32px', fontSize: '16px', fontWeight: '600' }}
                      />
                      <span style={{ position: 'absolute', left: '12px', top: '10px', fontWeight: '700', color: '#64748b' }}>₱</span>
                    </div>
                  </div>

                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                      <label style={{ fontSize: '13px', fontWeight: '600', color: '#334155' }}>
                        Tracking Reference
                      </label>
                      <button
                        type="button"
                        onClick={() => setSagaTransferRef(`OTC-SAGA-${Math.floor(100000 + Math.random() * 900000)}`)}
                        style={{ fontSize: '11px', color: '#2563eb', fontWeight: '600' }}
                      >
                        Regenerate
                      </button>
                    </div>
                    <input
                      type="text"
                      value={sagaTransferRef}
                      readOnly
                      style={{ background: '#f8fafc', fontFamily: 'var(--font-mono)', fontWeight: '600' }}
                    />
                  </div>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Remarks / Purpose
                  </label>
                  <input
                    type="text"
                    value={sagaTransferDesc}
                    onChange={(e) => setSagaTransferDesc(e.target.value)}
                    placeholder="e.g. Counter assisted fund transfer"
                  />
                </div>

                <button
                  type="submit"
                  disabled={sagaTransferLoading}
                  className="banking-btn-primary"
                  style={{ marginTop: '8px', padding: '14px', fontSize: '14px' }}
                >
                  {sagaTransferLoading ? 'Orchestrating Saga Transfer...' : 'Execute Assisted Saga Transfer'}
                </button>
              </form>

              {/* Success Result Link to Stepper */}
              {lastSagaResult && (
                <div style={{
                  marginTop: '20px',
                  padding: '16px',
                  background: '#ecfdf5',
                  border: '1px solid #a7f3d0',
                  borderRadius: '12px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#065f46', fontWeight: '700', fontSize: '14px' }}>
                      <CheckCircle2 size={18} />
                      <span>Saga Transfer Successfully Completed</span>
                    </div>
                    <div style={{ fontSize: '12px', color: '#047857', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
                      Ref: {lastSagaResult.referenceNo} &bull; Amount: ₱{lastSagaResult.amount.toFixed(2)}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setSagaTrackingRef(lastSagaResult.referenceNo);
                      loadTimeline(lastSagaResult.referenceNo);
                      setActiveTab('saga');
                    }}
                    className="banking-btn-primary"
                    style={{ fontSize: '12px', padding: '8px 14px', background: '#059669' }}
                  >
                    <span>View in 11-Stage Stepper</span>
                    <ChevronRight size={14} />
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 3: APPROVAL QUEUES (KYC, CLOSURES, HIGH TRANSFERS)                 */}
      {/* ===================================================================== */}
      {activeTab === 'queues' && (
        <div className="animate-fade" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Sub-Queue Selector */}
          <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid #e2e8f0', paddingBottom: '10px' }}>
            <button
              type="button"
              onClick={() => setQueueSubTab('kyc')}
              style={{
                padding: '8px 14px',
                borderRadius: '8px',
                fontWeight: '600',
                fontSize: '13px',
                background: queueSubTab === 'kyc' ? '#eff6ff' : 'transparent',
                color: queueSubTab === 'kyc' ? '#1d4ed8' : '#64748b',
                border: queueSubTab === 'kyc' ? '1px solid #bfdbfe' : '1px solid transparent'
              }}
            >
              KYC Verifications ({pendingKyc.length})
            </button>

            <button
              type="button"
              onClick={() => setQueueSubTab('closures')}
              style={{
                padding: '8px 14px',
                borderRadius: '8px',
                fontWeight: '600',
                fontSize: '13px',
                background: queueSubTab === 'closures' ? '#eff6ff' : 'transparent',
                color: queueSubTab === 'closures' ? '#1d4ed8' : '#64748b',
                border: queueSubTab === 'closures' ? '1px solid #bfdbfe' : '1px solid transparent'
              }}
            >
              Account Closures ({pendingClosures.length}) [Read-Only]
            </button>

            <button
              type="button"
              onClick={() => setQueueSubTab('transfers')}
              style={{
                padding: '8px 14px',
                borderRadius: '8px',
                fontWeight: '600',
                fontSize: '13px',
                background: queueSubTab === 'transfers' ? '#eff6ff' : 'transparent',
                color: queueSubTab === 'transfers' ? '#1d4ed8' : '#64748b',
                border: queueSubTab === 'transfers' ? '1px solid #bfdbfe' : '1px solid transparent'
              }}
            >
              High-Value Transfers ({pendingTransfers.length})
            </button>
          </div>

          {/* Sub-Queue 1: KYC Verification */}
          {queueSubTab === 'kyc' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div>
                  <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#0f172a' }}>Customer KYC Verification Queue</h3>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>Review and verify requested customer identification updates.</p>
                </div>
                <button type="button" onClick={loadQueues} className="banking-btn-secondary" style={{ fontSize: '12px' }}>
                  Refresh Queue
                </button>
              </div>

              {pendingKyc.length === 0 ? (
                <div className="banking-card" style={{ textAlign: 'center', padding: '48px 20px' }}>
                  <CheckCircle2 size={36} color="#059669" style={{ margin: '0 auto 10px' }} />
                  <h4 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a' }}>KYC Queue is Clear</h4>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>No customer KYC updates are currently awaiting verification.</p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  {pendingKyc.map((req) => (
                    <div key={req.kycRequestId} className="banking-card">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a' }}>
                              {req.customerName || `Customer #${req.customerId}`}
                            </span>
                            <span className="badge badge-pending">PENDING VERIFICATION</span>
                          </div>
                          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                            Request ID #{req.kycRequestId} &bull; Customer #{req.customerId} &bull; Submitted {new Date(req.requestedAt).toLocaleString()}
                          </div>
                        </div>

                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button
                            type="button"
                            onClick={() => handleApproveKyc(req.kycRequestId)}
                            className="banking-btn-primary"
                            style={{ padding: '8px 14px', fontSize: '13px', background: '#059669' }}
                          >
                            <CheckCircle2 size={15} />
                            <span>Approve KYC</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => setRejectKycModal({ id: req.kycRequestId, reason: '' })}
                            className="banking-btn-danger"
                            style={{ padding: '8px 14px', fontSize: '13px' }}
                          >
                            <XCircle size={15} />
                            <span>Reject</span>
                          </button>
                        </div>
                      </div>

                      {/* Field Diffs */}
                      <div style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                        gap: '12px',
                        padding: '14px',
                        background: '#f8fafc',
                        borderRadius: '8px',
                        border: '1px solid #e2e8f0',
                        fontSize: '13px'
                      }}>
                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>Requested Name:</span>
                          <div style={{ fontWeight: '600', color: '#0f172a' }}>
                            {req.newFirstName} {req.newMiddleInitial ? req.newMiddleInitial + '. ' : ''}{req.newLastName}
                          </div>
                        </div>

                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>New Mobile:</span>
                          <div style={{ fontWeight: '600', color: '#0f172a' }}>{req.newMobileNumber || 'N/A'}</div>
                        </div>

                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>New Civil Status:</span>
                          <div style={{ fontWeight: '600', color: '#0f172a' }}>{req.newCivilStatus || 'N/A'}</div>
                        </div>

                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>New Occupation:</span>
                          <div style={{ fontWeight: '600', color: '#0f172a' }}>{req.newOccupation || 'N/A'}</div>
                        </div>

                        <div style={{ gridColumn: '1 / -1' }}>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase', fontWeight: '600' }}>New Address:</span>
                          <div style={{ fontWeight: '600', color: '#0f172a' }}>{req.newAddress || 'N/A'}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Sub-Queue 2: Account Closures (Teller Read-Only) */}
          {queueSubTab === 'closures' && (
            <div>
              <div style={{
                background: '#fffbeb',
                border: '1px solid #fde68a',
                borderRadius: '12px',
                padding: '14px 18px',
                marginBottom: '16px',
                display: 'flex',
                alignItems: 'center',
                gap: '12px'
              }}>
                <ShieldAlert size={20} color="#b45309" />
                <div style={{ fontSize: '13px', color: '#92400e' }}>
                  <strong>Teller Review Policy:</strong> Tellers can inspect zero-balance closure requests. Final closure authorization is restricted to Branch Managers (<code style={{ fontFamily: 'var(--font-mono)' }}>ROLE_ADMIN</code>) as per bank governance rules.
                </div>
              </div>

              {pendingClosures.length === 0 ? (
                <div className="banking-card" style={{ textAlign: 'center', padding: '48px 20px' }}>
                  <CheckCircle2 size={36} color="#059669" style={{ margin: '0 auto 10px' }} />
                  <h4 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a' }}>No Pending Closures</h4>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>Zero accounts are currently awaiting closure authorization.</p>
                </div>
              ) : (
                <div className="banking-card">
                  <div className="banking-table-container">
                    <table className="banking-table">
                      <thead>
                        <tr>
                          <th>Request ID</th>
                          <th>Account Number</th>
                          <th>Customer</th>
                          <th>Available Balance</th>
                          <th>Reason</th>
                          <th>Requested At</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pendingClosures.map((item) => (
                          <tr key={item.closureRequestId}>
                            <td style={{ fontWeight: '600' }}>#{item.closureRequestId}</td>
                            <td style={{ fontWeight: '700', fontFamily: 'var(--font-mono)' }}>
                              {item.accountNumber || `ACC-${item.accountId}`}
                            </td>
                            <td>{item.customerName || `Customer #${item.customerId}`}</td>
                            <td style={{ fontWeight: '700', color: '#059669', fontFamily: 'var(--font-mono)' }}>
                              ₱{Number(item.availableBalance || 0).toFixed(2)}
                            </td>
                            <td style={{ maxWidth: '240px', fontSize: '12px', color: '#475569' }}>
                              {item.reason || 'Customer requested closure'}
                            </td>
                            <td style={{ fontSize: '12px', color: '#64748b' }}>
                              {new Date(item.requestedAt).toLocaleString()}
                            </td>
                            <td>
                              <span className="badge badge-pending">PENDING MANAGER</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Sub-Queue 3: High-Value Transfers (Dual Control) */}
          {queueSubTab === 'transfers' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div>
                  <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#0f172a' }}>High-Value Transfer Dual-Control Queue</h3>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>Dual-control authorization required for transactions exceeding risk limits.</p>
                </div>
                <button type="button" onClick={loadQueues} className="banking-btn-secondary" style={{ fontSize: '12px' }}>
                  Refresh Queue
                </button>
              </div>

              {pendingTransfers.length === 0 ? (
                <div className="banking-card" style={{ textAlign: 'center', padding: '48px 20px' }}>
                  <CheckCircle2 size={36} color="#059669" style={{ margin: '0 auto 10px' }} />
                  <h4 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a' }}>Transfer Queue is Clear</h4>
                  <p style={{ fontSize: '13px', color: '#64748b' }}>All customer transfers are currently settled within standard thresholds.</p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  {pendingTransfers.map((item) => (
                    <div key={item.transferRequestId} className="banking-card">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a', fontFamily: 'var(--font-mono)' }}>
                              ₱{item.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                            </span>
                            <span className="badge badge-pending">NEEDS TELLER APPROVAL</span>
                          </div>
                          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                            Ref: {item.referenceNo} &bull; Requested {new Date(item.requestedAt).toLocaleString()}
                          </div>
                        </div>

                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button
                            type="button"
                            onClick={() => handleApproveTransfer(item.transferRequestId)}
                            className="banking-btn-primary"
                            style={{ padding: '8px 14px', fontSize: '13px', background: '#059669' }}
                          >
                            <CheckCircle2 size={15} />
                            <span>Authorize</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => setRejectTransferModal({ id: item.transferRequestId, reason: '' })}
                            className="banking-btn-danger"
                            style={{ padding: '8px 14px', fontSize: '13px' }}
                          >
                            <XCircle size={15} />
                            <span>Decline</span>
                          </button>
                        </div>
                      </div>

                      <div style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr 1fr',
                        gap: '12px',
                        padding: '14px',
                        background: '#f8fafc',
                        borderRadius: '8px',
                        border: '1px solid #e2e8f0',
                        fontSize: '13px'
                      }}>
                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase' }}>Debit Source:</span>
                          <div style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                            {item.sourceAccountNumber || `Account #${item.sourceAccountId}`}
                          </div>
                        </div>

                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase' }}>Credit Destination:</span>
                          <div style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                            {item.destinationAccountNumber || `Account #${item.destinationAccountId}`}
                          </div>
                        </div>

                        <div>
                          <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase' }}>Remarks:</span>
                          <div style={{ color: '#334155' }}>{item.remarks || 'Standard Transfer'}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* KYC Rejection Reason Modal */}
          {rejectKycModal && (
            <div style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(15, 23, 42, 0.6)',
              backdropFilter: 'blur(4px)',
              zIndex: 100,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '20px'
            }}>
              <div style={{ background: '#ffffff', borderRadius: '16px', maxWidth: '440px', width: '100%', padding: '24px', boxShadow: 'var(--shadow-modal)' }}>
                <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', marginBottom: '8px' }}>
                  Reject KYC Request #{rejectKycModal.id}
                </h3>
                <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '16px' }}>
                  Please supply the official compliance reason for rejecting this customer update.
                </p>
                <textarea
                  rows={3}
                  value={rejectKycModal.reason}
                  onChange={(e) => setRejectKycModal({ ...rejectKycModal, reason: e.target.value })}
                  placeholder="e.g. Identity document unreadable or mismatched address proof"
                  required
                  style={{ width: '100%', padding: '10px' }}
                />
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
                  <button type="button" onClick={() => setRejectKycModal(null)} className="banking-btn-secondary">
                    Cancel
                  </button>
                  <button type="button" onClick={handleRejectKyc} className="banking-btn-danger">
                    Confirm Rejection
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Transfer Rejection Modal */}
          {rejectTransferModal && (
            <div style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(15, 23, 42, 0.6)',
              backdropFilter: 'blur(4px)',
              zIndex: 100,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '20px'
            }}>
              <div style={{ background: '#ffffff', borderRadius: '16px', maxWidth: '440px', width: '100%', padding: '24px', boxShadow: 'var(--shadow-modal)' }}>
                <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', marginBottom: '8px' }}>
                  Decline Transfer Authorization #{rejectTransferModal.id}
                </h3>
                <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '16px' }}>
                  Specify the teller compliance reason for declining this high-value transfer.
                </p>
                <textarea
                  rows={3}
                  value={rejectTransferModal.reason}
                  onChange={(e) => setRejectTransferModal({ ...rejectTransferModal, reason: e.target.value })}
                  placeholder="e.g. Unverified beneficiary details or risk threshold exceeded"
                  required
                  style={{ width: '100%', padding: '10px' }}
                />
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
                  <button type="button" onClick={() => setRejectTransferModal(null)} className="banking-btn-secondary">
                    Cancel
                  </button>
                  <button type="button" onClick={handleRejectTransfer} className="banking-btn-danger">
                    Decline Transfer
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 4: ACCOUNTS DIRECTORY & COMPLIANCE HOLDS                           */}
      {/* ===================================================================== */}
      {activeTab === 'accounts' && (
        <div className="animate-fade" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Create Account Form */}
          <div className="banking-card">
            <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', marginBottom: '16px' }}>
              Open New Customer Deposit Account
            </h3>

            <form onSubmit={handleOpenAccountSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: '14px', alignItems: 'flex-end' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                  Customer ID
                </label>
                <input
                  type="number"
                  min="1"
                  value={newAccCustomerId}
                  onChange={(e) => setNewAccCustomerId(e.target.value)}
                  placeholder="e.g. 1"
                  required
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                  Account Type
                </label>
                <select value={newAccType} onChange={(e) => setNewAccType(e.target.value)}>
                  <option value="SAVINGS">SAVINGS</option>
                  <option value="CHECKING">CHECKING</option>
                  <option value="PAYROLL">PAYROLL</option>
                  <option value="TIME_DEPOSIT">TIME_DEPOSIT</option>
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                  Initial Deposit (₱)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={newAccInitialDeposit}
                  onChange={(e) => setNewAccInitialDeposit(e.target.value)}
                  placeholder="0.00"
                />
              </div>

              <button
                type="submit"
                disabled={newAccLoading}
                className="banking-btn-primary"
                style={{ padding: '10px 18px' }}
              >
                <PlusCircle size={16} />
                <span>Open Account</span>
              </button>
            </form>
          </div>

          {/* All Accounts Listing & Actions */}
          <div className="banking-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>
                  System Accounts Directory & Compliance Holds
                </h3>
                <p style={{ fontSize: '13px', color: '#64748b' }}>
                  Maintain account status toggles and impose risk compliance holds.
                </p>
              </div>
              <button type="button" onClick={loadAccountsAndLiquidity} className="banking-btn-secondary" style={{ fontSize: '12px' }}>
                Refresh Accounts
              </button>
            </div>

            <div className="banking-table-container">
              <table className="banking-table">
                <thead>
                  <tr>
                    <th>Account ID</th>
                    <th>Account Number</th>
                    <th>Customer</th>
                    <th>Type</th>
                    <th>Balance</th>
                    <th>Status</th>
                    <th>Compliance Flags</th>
                    <th>Maintenance Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {allAccounts.map(acc => {
                    const accFlags = allFlags.filter(f => f.accountId === acc.accountId && f.status === 'ACTIVE');
                    return (
                      <tr key={acc.accountId}>
                        <td style={{ fontWeight: '600' }}>#{acc.accountId}</td>
                        <td style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>{acc.accountNumber}</td>
                        <td>{acc.customerName || `Customer #${acc.customerId}`}</td>
                        <td><span className="badge badge-neutral">{acc.accountType}</span></td>
                        <td style={{ fontWeight: '700', fontFamily: 'var(--font-mono)' }}>
                          ₱{acc.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                        </td>
                        <td>
                          <span className={`badge badge-${acc.status.toLowerCase()}`}>{acc.status}</span>
                        </td>
                        <td>
                          {accFlags.length > 0 ? (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              fontSize: '11px',
                              fontWeight: '700',
                              padding: '2px 8px',
                              borderRadius: '6px',
                              background: '#fef2f2',
                              color: '#dc2626',
                              border: '1px solid #fecaca'
                            }}>
                              <Flag size={11} />
                              {accFlags.length} Flag{accFlags.length > 1 ? 's' : ''} Active
                            </span>
                          ) : (
                            <span style={{ fontSize: '11px', color: '#94a3b8' }}>None</span>
                          )}
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: '6px' }}>
                            {acc.status === 'ACTIVE' ? (
                              <button
                                type="button"
                                onClick={() => handleStatusChangeSubmit(acc.accountId, 'FROZEN')}
                                className="banking-btn-secondary"
                                style={{ padding: '4px 8px', fontSize: '11px', color: '#dc2626' }}
                                title="Freeze Account"
                              >
                                Freeze
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => handleStatusChangeSubmit(acc.accountId, 'ACTIVE')}
                                className="banking-btn-secondary"
                                style={{ padding: '4px 8px', fontSize: '11px', color: '#059669' }}
                                title="Activate Account"
                              >
                                Activate
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() => {
                                setFlagModalAccount(acc);
                                setFlagReasonPreset('AML KYC Document Verification Required');
                                setFlagReasonCustom('');
                              }}
                              className="banking-btn-secondary"
                              style={{ padding: '4px 8px', fontSize: '11px', color: '#d97706' }}
                              title="Apply Risk Hold Flag"
                            >
                              <Flag size={12} />
                              <span>Flag</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Add Account Flag Modal */}
          {flagModalAccount && (
            <div style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(15, 23, 42, 0.6)',
              backdropFilter: 'blur(4px)',
              zIndex: 100,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '20px'
            }}>
              <div style={{ background: '#ffffff', borderRadius: '16px', maxWidth: '460px', width: '100%', padding: '24px', boxShadow: 'var(--shadow-modal)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
                  <Flag size={20} color="#dc2626" />
                  <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>
                    Impose Compliance Risk Hold
                  </h3>
                </div>
                <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '16px' }}>
                  Account #{flagModalAccount.accountNumber} ({flagModalAccount.accountType}) &bull; Current Status: <strong>{flagModalAccount.status}</strong>
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                      Standard Risk Hold Reason Preset
                    </label>
                    <select
                      value={flagReasonPreset}
                      onChange={(e) => setFlagReasonPreset(e.target.value)}
                      style={{ width: '100%' }}
                    >
                      <option value="AML KYC Document Verification Required">AML KYC Document Verification Required</option>
                      <option value="Suspicious Transaction Velocity Detected">Suspicious Transaction Velocity Detected</option>
                      <option value="Court Order / Legal Freeze Imposed">Court Order / Legal Freeze Imposed</option>
                      <option value="Suspected Account Compromise / Unauthorized Access">Suspected Account Compromise / Unauthorized Access</option>
                      <option value="OTHER">Other Custom Compliance Reason</option>
                    </select>
                  </div>

                  {flagReasonPreset === 'OTHER' && (
                    <div>
                      <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                        Custom Hold Reason
                      </label>
                      <textarea
                        rows={2}
                        value={flagReasonCustom}
                        onChange={(e) => setFlagReasonCustom(e.target.value)}
                        placeholder="Specify exact compliance audit reason..."
                        required
                        style={{ width: '100%', padding: '8px' }}
                      />
                    </div>
                  )}

                  <div style={{ fontSize: '11px', color: '#dc2626', background: '#fef2f2', padding: '10px', borderRadius: '8px', border: '1px solid #fecaca' }}>
                    <strong>Note:</strong> Imposing a compliance flag will automatically set this account status to <code style={{ fontFamily: 'var(--font-mono)' }}>FROZEN</code>. Only authorized branch managers may remove compliance holds.
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                  <button type="button" onClick={() => setFlagModalAccount(null)} className="banking-btn-secondary">
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleApplyAccountFlag}
                    disabled={flagSubmitting}
                    className="banking-btn-primary"
                    style={{ background: '#dc2626' }}
                  >
                    {flagSubmitting ? 'Applying Hold...' : 'Apply Risk Hold'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 5: SAGA TRACKER & REVERSALS                                       */}
      {/* ===================================================================== */}
      {activeTab === 'saga' && (
        <div className="animate-fade" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Tracking Bar & Actions */}
          <div className="banking-card" style={{ padding: '20px 24px' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: '1 1 360px' }}>
                <span style={{ fontSize: '13px', fontWeight: '700', color: '#0f172a' }}>Tracking Ref:</span>
                <input
                  type="text"
                  value={sagaTrackingRef}
                  onChange={(e) => setSagaTrackingRef(e.target.value)}
                  placeholder="e.g. SAGA-772910"
                  style={{ flex: 1, fontFamily: 'var(--font-mono)', fontWeight: '600' }}
                />
                <button
                  type="button"
                  onClick={() => loadTimeline(sagaTrackingRef)}
                  disabled={timelineLoading}
                  className="banking-btn-primary"
                  style={{ fontSize: '13px', padding: '9px 16px' }}
                >
                  <Search size={14} />
                  <span>Inspect</span>
                </button>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: '#64748b', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={timelinePolling}
                    onChange={(e) => setTimelinePolling(e.target.checked)}
                    style={{ width: '14px', height: '14px', cursor: 'pointer' }}
                  />
                  <span>Live Polling (3s)</span>
                </label>

                <button
                  type="button"
                  onClick={() => {
                    setReversalRefTarget(sagaTrackingRef);
                    setReversalResult(null);
                    setIsReversalModalOpen(true);
                  }}
                  className="banking-btn-secondary"
                  style={{ fontSize: '13px', padding: '9px 14px', color: '#dc2626', borderColor: '#fecaca', display: 'flex', alignItems: 'center', gap: '6px' }}
                >
                  <RotateCcw size={14} />
                  <span>Initiate Reversal</span>
                </button>
              </div>
            </div>
          </div>

          {/* Stepper Display */}
          {timelineData && (
            <div className="banking-card">
              {/* Stepper Header Summary */}
              <div style={{
                display: 'flex',
                flexWrap: 'wrap',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: '16px',
                borderBottom: '1px solid #e2e8f0',
                paddingBottom: '18px',
                marginBottom: '20px'
              }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span style={{ fontSize: '18px', fontWeight: '800', color: '#0f172a', fontFamily: 'var(--font-mono)' }}>
                      {timelineData.trackingRef}
                    </span>
                    <span className="badge badge-active" style={{ fontSize: '11px' }}>
                      {timelineData.currentStatus}
                    </span>
                    <span style={{ fontSize: '11px', color: '#64748b' }}>
                      Staff View (<code style={{ fontFamily: 'var(--font-mono)' }}>?view=staff</code>)
                    </span>
                  </div>
                  <div style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
                    Source: Account #{timelineData.sourceAccountId || 1} &bull; Dest: {timelineData.destinationAccountNumber || 'ACC-2001-4455'} &bull; Amount: ₱{Number(timelineData.amount || 2500).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </div>
                </div>

                <div style={{ fontSize: '12px', color: '#64748b' }}>
                  Last Updated: {timelineData.lastUpdated ? new Date(timelineData.lastUpdated).toLocaleTimeString() : 'Just now'}
                </div>
              </div>

              {/* 11-Stage Technical Pipeline */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {timelineData.stages.map((stage, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '14px',
                      padding: '14px 16px',
                      borderRadius: '10px',
                      background: stage.status === 'COMPLETED' ? '#f8fafc' : (stage.status === 'FAILED' ? '#fef2f2' : '#ffffff'),
                      border: `1px solid ${stage.status === 'COMPLETED' ? '#e2e8f0' : (stage.status === 'FAILED' ? '#fecaca' : '#bfdbfe')}`
                    }}
                  >
                    {/* Step Icon */}
                    <div style={{
                      width: '28px',
                      height: '28px',
                      borderRadius: '9999px',
                      background: stage.status === 'COMPLETED' ? '#ecfdf5' : (stage.status === 'FAILED' ? '#fef2f2' : '#eff6ff'),
                      color: stage.status === 'COMPLETED' ? '#059669' : (stage.status === 'FAILED' ? '#dc2626' : '#2563eb'),
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      fontWeight: '700',
                      fontSize: '12px'
                    }}>
                      {stage.status === 'COMPLETED' ? <CheckCircle2 size={16} /> : (stage.status === 'FAILED' ? <XCircle size={16} /> : `${idx + 1}`)}
                    </div>

                    {/* Step Content */}
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: '8px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '13px', fontWeight: '800', color: '#0f172a', fontFamily: 'var(--font-mono)' }}>
                            STAGE {idx + 1}: {stage.stage}
                          </span>
                          <span style={{
                            fontSize: '11px',
                            fontWeight: '600',
                            padding: '2px 8px',
                            borderRadius: '4px',
                            background: '#eff6ff',
                            color: '#1d4ed8'
                          }}>
                            {stage.service}
                          </span>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '12px' }}>
                          {stage.latencyMs !== undefined && (
                            <span style={{ color: '#059669', fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                              {stage.latencyMs}ms
                            </span>
                          )}
                          <span style={{ color: '#64748b' }}>
                            {stage.timestamp ? new Date(stage.timestamp).toLocaleTimeString() : 'N/A'}
                          </span>
                        </div>
                      </div>

                      <div style={{ fontSize: '12px', color: '#475569', marginTop: '4px' }}>
                        {stage.details || 'Lifecycle event verified by orchestrator.'}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Reversal Modal */}
          {isReversalModalOpen && (
            <div style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(15, 23, 42, 0.6)',
              backdropFilter: 'blur(4px)',
              zIndex: 100,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '20px'
            }}>
              <div style={{ background: '#ffffff', borderRadius: '16px', maxWidth: '480px', width: '100%', padding: '24px', boxShadow: 'var(--shadow-modal)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                  <RotateCcw size={20} color="#dc2626" />
                  <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>
                    Initiate Transaction Reversal
                  </h3>
                </div>
                <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '16px' }}>
                  Post a compensating ledger adjustment to reverse a completed transaction.
                </p>

                <form onSubmit={handleReversalSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                      Target Transaction Tracking Ref
                    </label>
                    <input
                      type="text"
                      value={reversalRefTarget}
                      onChange={(e) => setReversalRefTarget(e.target.value)}
                      required
                      style={{ width: '100%', fontFamily: 'var(--font-mono)', fontWeight: '600' }}
                    />
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                      Compliance Reversal Reason
                    </label>
                    <select
                      value={reversalReason}
                      onChange={(e) => setReversalReason(e.target.value as any)}
                      style={{ width: '100%' }}
                    >
                      <option value="CUSTOMER_REQUEST">CUSTOMER_REQUEST &mdash; Customer walk-in duplicate request</option>
                      <option value="SYSTEM_ERROR">SYSTEM_ERROR &mdash; Communication timeout or glitch</option>
                      <option value="SUSPECTED_FRAUD">SUSPECTED_FRAUD &mdash; Unauthorized actor or account fraud</option>
                      <option value="DUPLICATE_PROCESSING">DUPLICATE_PROCESSING &mdash; Double debit counter occurrence</option>
                    </select>
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                      Teller Audit Remarks
                    </label>
                    <textarea
                      rows={2}
                      value={reversalRemarks}
                      onChange={(e) => setReversalRemarks(e.target.value)}
                      placeholder="e.g. Branch counter teller reversal verification"
                      style={{ width: '100%', padding: '8px' }}
                    />
                  </div>

                  {reversalResult ? (
                    <div style={{ background: '#ecfdf5', padding: '12px', borderRadius: '8px', border: '1px solid #a7f3d0', fontSize: '12px', color: '#065f46' }}>
                      <strong>Reversal Requested:</strong> {reversalResult.message}
                      <div style={{ fontFamily: 'var(--font-mono)', marginTop: '4px' }}>
                        Reference: {reversalResult.reversalRef}
                      </div>
                    </div>
                  ) : null}

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                    <button type="button" onClick={() => setIsReversalModalOpen(false)} className="banking-btn-secondary">
                      {reversalResult ? 'Close' : 'Cancel'}
                    </button>
                    {!reversalResult && (
                      <button
                        type="submit"
                        disabled={reversalLoading}
                        className="banking-btn-primary"
                        style={{ background: '#dc2626' }}
                      >
                        {reversalLoading ? 'Processing Reversal...' : 'Confirm Reversal'}
                      </button>
                    )}
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 6: AUDIT INTEGRITY & BATCH MONITOR                                */}
      {/* ===================================================================== */}
      {activeTab === 'audit' && (
        <div className="animate-fade" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {/* EOD Batch Monitor Status Card */}
          <div className="banking-card" style={{ padding: '20px 24px' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: '16px', marginBottom: '16px' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Database size={18} color="#2563eb" />
                  <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#0f172a' }}>
                    End-Of-Day (EOD) Settlement & Batch Processing Monitor
                  </h3>
                </div>
                <p style={{ fontSize: '13px', color: '#64748b', marginTop: '2px' }}>
                  Read-only audit view for branch reconciliation. EOD batch jobs are scheduled and executed by Branch Managers.
                </p>
              </div>

              <button
                type="button"
                onClick={loadBatch}
                disabled={batchLoading}
                className="banking-btn-secondary"
                style={{ fontSize: '12px', padding: '6px 12px' }}
              >
                <RefreshCw size={12} className={batchLoading ? 'spin-icon' : ''} />
                <span>Refresh Runs</span>
              </button>
            </div>

            {batchRuns.length > 0 && (
              <div>
                {/* Latest Batch Run Ribbon */}
                <div style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '12px 16px',
                  background: '#f8fafc',
                  borderRadius: '10px',
                  border: '1px solid #e2e8f0',
                  marginBottom: '16px'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span style={{ fontSize: '14px', fontWeight: '700', color: '#0f172a' }}>
                      Run #{batchRuns[0].runId}: {batchRuns[0].batchJobName}
                    </span>
                    <span className="badge badge-active">
                      {batchRuns[0].status}
                    </span>
                  </div>

                  <div style={{ display: 'flex', gap: '16px', fontSize: '12px', color: '#64748b' }}>
                    <span>Total Records: <strong>{batchRuns[0].totalRecordsProcessed}</strong></span>
                    <span>Started: {new Date(batchRuns[0].startTime).toLocaleTimeString()}</span>
                    <span>Ended: {batchRuns[0].endTime ? new Date(batchRuns[0].endTime).toLocaleTimeString() : 'Running'}</span>
                  </div>
                </div>

                {/* 5 EOD Steps Table */}
                <div className="banking-table-container">
                  <table className="banking-table">
                    <thead>
                      <tr>
                        <th>Step ID</th>
                        <th>Settlement Step Description</th>
                        <th>Status</th>
                        <th>Records Processed</th>
                        <th>Step Duration</th>
                      </tr>
                    </thead>
                    <tbody>
                      {batchRuns[0].steps.map((s) => (
                        <tr key={s.stepId}>
                          <td style={{ fontWeight: '600' }}>#{s.stepId}</td>
                          <td style={{ fontWeight: '600', color: '#0f172a' }}>{s.stepName}</td>
                          <td>
                            <span className="badge badge-active">{s.status}</span>
                          </td>
                          <td style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                            {s.recordsProcessed.toLocaleString()} records
                          </td>
                          <td style={{ color: '#059669', fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                            {s.durationMs ? `${s.durationMs}ms` : 'N/A'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>

          {/* Cryptographic SHA-256 Audit Integrity Engine */}
          <div className="banking-card">
            <AuditView accounts={allAccounts} />
          </div>
        </div>
      )}

    </div>
  );
};
