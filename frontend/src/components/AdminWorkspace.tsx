import { useState, useEffect } from 'react';
import {
  FileCheck,
  History,
  ShieldAlert,
  ShieldCheck,
  CheckCircle2,
  XCircle,
  Lock,
  Unlock,
  CalendarClock
} from 'lucide-react';
import type {
  Account,
  AccountFlag,
  AuditRecord,
  ChainVerificationResult,
  ClosureRequestResponse
} from '../types';
import { apiClient } from '../services/api';
import { EodBatchView } from './EodBatchView';

interface AdminWorkspaceProps {
  onRefreshGlobalData: () => void;
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export const AdminWorkspace: React.FC<AdminWorkspaceProps> = ({
  onRefreshGlobalData,
  showToast
}) => {
  const [activeTab, setActiveTab] = useState<'closures' | 'audit' | 'governance' | 'batch'>('batch');

  // Closures State
  const [pendingClosures, setPendingClosures] = useState<ClosureRequestResponse[]>([]);
  const [rejectModal, setRejectModal] = useState<{ id: number; reason: string } | null>(null);

  // Audit Ledger State
  const [searchAccountId, setSearchAccountId] = useState<number>(1);
  const [auditRecords, setAuditRecords] = useState<AuditRecord[]>([]);
  const [verificationResult, setVerificationResult] = useState<ChainVerificationResult | null>(null);
  const [verifyingChain, setVerifyingChain] = useState(false);

  // Accounts & Governance State
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [activeFlags, setActiveFlags] = useState<(AccountFlag & { accountNumber?: string })[]>([]);
  const [flagAccountId, setFlagAccountId] = useState<string>('1');
  const [flagReason, setFlagReason] = useState<string>('AML KYC Document Verification Required');

  const loadPendingClosures = async () => {
    try {
      const list = await apiClient.getPendingClosureRequests();
      setPendingClosures(list);
    } catch {
      // fallback
    }
  };

  const loadFlags = async () => {
    try {
      const list = await apiClient.getAllFlags();
      setActiveFlags(list);
    } catch {
      // fallback
    }
  };

  const loadAccounts = async () => {
    try {
      const list = await apiClient.getAllAccounts();
      setAccounts(list);
      if (list.length > 0) {
        setFlagAccountId(prev => (prev && list.some(a => String(a.accountId) === prev) ? prev : String(list[0].accountId)));
      }
    } catch {
      // fallback
    }
  };

  const handleSearchStatement = async (accId: number) => {
    setVerificationResult(null);
    try {
      const records = await apiClient.getAccountAuditStatements(accId);
      setAuditRecords(records);
    } catch {
      // fallback
    }
  };

  useEffect(() => {
    loadPendingClosures();
    loadAccounts();
    loadFlags();
    handleSearchStatement(1);
  }, []);

  // Closure Actions
  const handleApproveClosure = async (id: number) => {
    try {
      await apiClient.approveClosureRequest(id);
      showToast('success', `Account closure request #${id} approved. Account has been closed.`);
      loadPendingClosures();
      loadAccounts();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to approve closure.');
    }
  };

  const handleRejectClosure = async () => {
    if (!rejectModal) return;
    if (!rejectModal.reason.trim()) {
      showToast('error', 'Rejection reason is mandatory.');
      return;
    }
    try {
      await apiClient.rejectClosureRequest(rejectModal.id, rejectModal.reason);
      showToast('info', `Account closure request #${rejectModal.id} rejected.`);
      setRejectModal(null);
      loadPendingClosures();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to reject closure.');
    }
  };

  // Verify Hash Chain
  const handleVerifyChain = async () => {
    setVerifyingChain(true);
    try {
      const res = await apiClient.verifyChainIntegrity(searchAccountId);
      setVerificationResult(res);
      if (res.isChainIntact) {
        showToast('success', `SHA-256 Ledger Chain Intact (${res.totalRecordsVerified} records verified).`);
      } else {
        showToast('error', 'Hash chain discrepancy detected!');
      }
    } catch (err: any) {
      showToast('error', err.message || 'Verification failed.');
    } finally {
      setVerifyingChain(false);
    }
  };

  // Impose Account Flag / Hold
  const handleImposeFlag = async (e: React.FormEvent) => {
    e.preventDefault();
    const accId = parseInt(flagAccountId, 10);
    if (isNaN(accId)) {
      showToast('error', 'Please select a valid account.');
      return;
    }
    if (!flagReason.trim()) {
      showToast('error', 'Administrative reason is required.');
      return;
    }

    try {
      await apiClient.addAccountFlag(accId, flagReason.trim());
      showToast('info', `Compliance hold imposed: Account #${accId} set to FROZEN.`);
      setFlagReason('AML KYC Document Verification Required');
      loadFlags();
      loadAccounts();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to flag account.');
    }
  };

  // Lift Hold
  const handleLiftFlag = async (accountId: number, flagId: number) => {
    try {
      await apiClient.removeAccountFlag(accountId, flagId);
      showToast('success', `Compliance hold #${flagId} lifted from Account #${accountId}.`);
      loadFlags();
      loadAccounts();
      onRefreshGlobalData();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to lift hold.');
    }
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Navigation Sub-Tabs */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        borderBottom: '1px solid #e2e8f0',
        marginBottom: '28px',
        paddingBottom: '12px'
      }}>
        <button
          type="button"
          onClick={() => setActiveTab('closures')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'closures' ? '#eff6ff' : 'transparent',
            color: activeTab === 'closures' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'closures' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <FileCheck size={18} />
          <span>Account Closure Requests ({pendingClosures.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('audit')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'audit' ? '#eff6ff' : 'transparent',
            color: activeTab === 'audit' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'audit' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <History size={18} />
          <span>Transaction History & Cryptographic Audit</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('governance')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'governance' ? '#eff6ff' : 'transparent',
            color: activeTab === 'governance' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'governance' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <ShieldAlert size={18} />
          <span>Risk Holds & Governance</span>
        </button>

        <button
          type="button"
          id="tab-btn-batch"
          onClick={() => setActiveTab('batch')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'batch' ? '#eff6ff' : 'transparent',
            color: activeTab === 'batch' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'batch' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <CalendarClock size={18} />
          <span>End-of-Day Settlement (Batch)</span>
        </button>
      </div>

      {/* ========================================================================= */}
      {/* 1. ACCOUNT CLOSURE REQUESTS APPROVAL                                      */}
      {/* ========================================================================= */}
      {activeTab === 'closures' && (
        <div className="animate-fade">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <div>
              <h2 style={{ fontSize: '20px', fontWeight: '800', color: '#0f172a' }}>Account Closure Authorizations</h2>
              <p style={{ fontSize: '13px', color: '#64748b', marginTop: '2px' }}>
                Bank administration approval queue for zero-balance customer accounts requesting closure.
              </p>
            </div>
            <button type="button" onClick={loadPendingClosures} className="banking-btn-secondary" style={{ fontSize: '13px' }}>
              Refresh Queue
            </button>
          </div>

          {pendingClosures.length === 0 ? (
            <div className="banking-card" style={{ textAlign: 'center', padding: '50px 20px' }}>
              <CheckCircle2 size={40} style={{ color: '#059669', margin: '0 auto 12px' }} />
              <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a' }}>No Pending Closure Requests</h3>
              <p style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
                All customer closure submissions have been processed.
              </p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {pendingClosures.map((item) => (
                <div key={item.closureRequestId} className="banking-card">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', fontFamily: 'var(--font-mono)' }}>
                          {item.accountNumber || `Account #${item.accountId}`}
                        </span>
                        <span className="badge badge-pending">PENDING ADMIN SIGN-OFF</span>
                      </div>
                      <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                        Closure Request #{item.closureRequestId} &bull; Requested by {item.customerName || `Customer #${item.customerId}`} &bull; {new Date(item.requestedAt).toLocaleString()}
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        type="button"
                        onClick={() => handleApproveClosure(item.closureRequestId)}
                        className="banking-btn-primary"
                        style={{ padding: '8px 14px', fontSize: '13px', background: '#059669' }}
                      >
                        <CheckCircle2 size={15} />
                        <span>Approve Closure</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setRejectModal({ id: item.closureRequestId, reason: '' })}
                        className="banking-btn-danger"
                        style={{ padding: '8px 14px', fontSize: '13px' }}
                      >
                        <XCircle size={15} />
                        <span>Reject</span>
                      </button>
                    </div>
                  </div>

                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 2fr',
                    gap: '12px',
                    padding: '14px',
                    background: '#f8fafc',
                    borderRadius: '8px',
                    border: '1px solid #e2e8f0',
                    fontSize: '13px'
                  }}>
                    <div>
                      <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase' }}>Verified Balance:</span>
                      <div style={{ fontWeight: '700', color: '#059669' }}>
                        ₱{item.availableBalance !== undefined ? item.availableBalance.toFixed(2) : '0.00'} (Zero Balance)
                      </div>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase' }}>Account Type:</span>
                      <div style={{ fontWeight: '600', color: '#334155' }}>{item.accountType || 'SAVINGS'}</div>
                    </div>

                    <div>
                      <span style={{ color: '#64748b', fontSize: '11px', textTransform: 'uppercase' }}>Customer Reason:</span>
                      <div style={{ color: '#0f172a' }}>{item.reason || 'None provided'}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Rejection Modal */}
          {rejectModal && (
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
              <div style={{
                background: '#ffffff',
                borderRadius: '16px',
                maxWidth: '440px',
                width: '100%',
                padding: '24px',
                boxShadow: 'var(--shadow-modal)'
              }}>
                <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', marginBottom: '8px' }}>
                  Reject Account Closure #{rejectModal.id}
                </h3>
                <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '16px' }}>
                  Specify why this closure request is being denied.
                </p>

                <textarea
                  rows={3}
                  value={rejectModal.reason}
                  onChange={(e) => setRejectModal({ ...rejectModal, reason: e.target.value })}
                  placeholder="e.g. Active standing orders or pending clearing charges"
                  required
                />

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
                  <button type="button" onClick={() => setRejectModal(null)} className="banking-btn-secondary">
                    Cancel
                  </button>
                  <button type="button" onClick={handleRejectClosure} className="banking-btn-danger">
                    Confirm Rejection
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* 2. TRANSACTION HISTORY & CRYPTOGRAPHIC AUDIT                             */}
      {/* ========================================================================= */}
      {activeTab === 'audit' && (
        <div className="animate-fade">
          <div className="banking-card" style={{ marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '14px', marginBottom: '20px' }}>
              <div>
                <h2 style={{ fontSize: '20px', fontWeight: '800', color: '#0f172a' }}>Account Transaction History & Audit Ledger</h2>
                <p style={{ fontSize: '13px', color: '#64748b', marginTop: '2px' }}>
                  Cryptographically chained immutable ledger entries stored in PostgreSQL audit cluster.
                </p>
              </div>

              {/* Account Selector & Verify Button */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <select
                  value={searchAccountId}
                  onChange={(e) => {
                    const id = Number(e.target.value);
                    setSearchAccountId(id);
                    handleSearchStatement(id);
                  }}
                  style={{ width: 'auto', minWidth: '220px' }}
                >
                  {accounts.map(a => (
                    <option key={a.accountId} value={a.accountId}>
                      Account #{a.accountId} &bull; {a.accountNumber}
                    </option>
                  ))}
                </select>

                <button
                  type="button"
                  onClick={handleVerifyChain}
                  disabled={verifyingChain}
                  className="banking-btn-primary"
                  style={{ fontSize: '13px' }}
                >
                  <ShieldCheck size={16} />
                  <span>{verifyingChain ? 'Verifying Hashes...' : 'Verify Cryptographic Chain'}</span>
                </button>
              </div>
            </div>

            {/* Verification Result Banner */}
            {verificationResult && (
              <div style={{
                padding: '16px',
                borderRadius: '8px',
                marginBottom: '20px',
                background: verificationResult.isChainIntact ? '#ecfdf5' : '#fef2f2',
                border: `1px solid ${verificationResult.isChainIntact ? '#a7f3d0' : '#fecaca'}`,
                display: 'flex',
                alignItems: 'flex-start',
                gap: '12px'
              }}>
                <CheckCircle2 size={20} style={{ color: '#059669', flexShrink: 0, marginTop: '2px' }} />
                <div style={{ fontSize: '13px' }}>
                  <div style={{ fontWeight: '700', color: '#059669' }}>
                    SHA-256 Ledger Cryptographic Proof Verified
                  </div>
                  <div style={{ color: '#334155', marginTop: '2px' }}>
                    {verificationResult.message} ({verificationResult.totalRecordsVerified} blocks traversed).
                  </div>
                  {verificationResult.latestHash && (
                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#64748b', marginTop: '4px' }}>
                      Latest Root Hash: {verificationResult.latestHash}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Audit Table */}
            <div className="banking-table-container">
              <table className="banking-table">
                <thead>
                  <tr>
                    <th>Audit ID</th>
                    <th>Type</th>
                    <th>Reference</th>
                    <th>Amount</th>
                    <th>Balance (Old &rarr; New)</th>
                    <th>SHA-256 Current Hash</th>
                    <th>Timestamp</th>
                  </tr>
                </thead>
                <tbody>
                  {auditRecords.length === 0 ? (
                    <tr>
                      <td colSpan={7} style={{ textAlign: 'center', padding: '30px', color: '#64748b' }}>
                        No audit records found for Account #{searchAccountId}.
                      </td>
                    </tr>
                  ) : (
                    auditRecords.map((r) => (
                      <tr key={r.auditId}>
                        <td style={{ fontWeight: '600' }}>#{r.auditId}</td>
                        <td>
                          <span className={`badge ${r.transactionType.includes('DEBIT') ? 'badge-rejected' : 'badge-completed'}`}>
                            {r.transactionType}
                          </span>
                        </td>
                        <td style={{ fontFamily: 'var(--font-mono)' }}>{r.referenceNo}</td>
                        <td style={{ fontWeight: '700' }}>₱{r.amount.toFixed(2)}</td>
                        <td style={{ fontSize: '12px' }}>
                          <span style={{ color: '#64748b' }}>₱{r.oldBalance.toFixed(2)}</span>
                          <span style={{ margin: '0 6px' }}>&rarr;</span>
                          <strong style={{ color: '#0f172a' }}>₱{r.newBalance.toFixed(2)}</strong>
                        </td>
                        <td style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#2563eb' }}>
                          {r.currentHash.substring(0, 16)}...
                        </td>
                        <td style={{ color: '#64748b', fontSize: '12px' }}>
                          {new Date(r.eventTimestamp).toLocaleString()}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 3. RISK HOLDS & GOVERNANCE                                                */}
      {/* ========================================================================= */}
      {activeTab === 'governance' && (
        <div className="animate-fade">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px', alignItems: 'flex-start' }}>
            {/* Impose Risk Hold Card */}
            <div className="banking-card">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
                <ShieldAlert size={20} style={{ color: '#dc2626' }} />
                <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>Impose Compliance Hold</h3>
              </div>
              <p style={{ fontSize: '12px', color: '#64748b', marginBottom: '16px' }}>
                Place an administrative hold on an account to immediately freeze all debits and outbound transfers.
              </p>

              <form onSubmit={handleImposeFlag} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                    Target Account
                  </label>
                  <select
                    value={flagAccountId}
                    onChange={(e) => setFlagAccountId(e.target.value)}
                  >
                    {accounts.map(a => (
                      <option key={a.accountId} value={a.accountId}>
                        #{a.accountId} &bull; {a.accountNumber} ({a.status})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#334155', marginBottom: '4px' }}>
                    Administrative / Compliance Reason
                  </label>
                  <input
                    type="text"
                    value={flagReason}
                    onChange={(e) => setFlagReason(e.target.value)}
                    placeholder="e.g. AML document verification, suspicious activity"
                    required
                  />
                </div>

                <button type="submit" className="banking-btn-danger" style={{ marginTop: '6px' }}>
                  <Lock size={15} />
                  <span>Impose Hold & Freeze Account</span>
                </button>
              </form>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              {/* Active Risk Holds Table */}
              <div className="banking-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span>Active Compliance Holds</span>
                    <span className="badge badge-rejected">{activeFlags.length} Active</span>
                  </h3>
                  <button type="button" onClick={loadFlags} className="banking-btn-secondary" style={{ fontSize: '12px', padding: '4px 10px' }}>
                    Refresh Holds
                  </button>
                </div>

                {activeFlags.length === 0 ? (
                  <div style={{
                    padding: '24px',
                    borderRadius: '8px',
                    background: '#ecfdf5',
                    border: '1px solid #a7f3d0',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                    color: '#059669',
                    fontSize: '13px'
                  }}>
                    <CheckCircle2 size={20} />
                    <span>No active compliance holds. All institution accounts are currently in good standing.</span>
                  </div>
                ) : (
                  <div className="banking-table-container">
                    <table className="banking-table">
                      <thead>
                        <tr>
                          <th>Hold ID</th>
                          <th>Account</th>
                          <th>Reason</th>
                          <th>Flagged Date</th>
                          <th>Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {activeFlags.map(f => (
                          <tr key={f.flagId}>
                            <td style={{ fontWeight: '600' }}>#{f.flagId}</td>
                            <td style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                              {f.accountNumber || `Account #${f.accountId}`}
                            </td>
                            <td style={{ maxWidth: '220px' }}>{f.reason}</td>
                            <td style={{ color: '#64748b', fontSize: '12px' }}>
                              {new Date(f.flaggedAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                            </td>
                            <td>
                              <button
                                type="button"
                                onClick={() => handleLiftFlag(f.accountId, f.flagId)}
                                className="banking-btn-secondary"
                                style={{ padding: '4px 10px', fontSize: '11px', color: '#059669', borderColor: '#a7f3d0' }}
                              >
                                <Unlock size={12} />
                                <span>Lift Hold</span>
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* System Accounts Overview */}
              <div className="banking-card">
                <h3 style={{ fontSize: '17px', fontWeight: '700', color: '#0f172a', marginBottom: '16px' }}>
                  Institutional Accounts Directory
                </h3>

                <div className="banking-table-container">
                  <table className="banking-table">
                    <thead>
                      <tr>
                        <th>Account</th>
                        <th>Customer</th>
                        <th>Type</th>
                        <th>Balance</th>
                        <th>Status</th>
                        <th>Hold State</th>
                      </tr>
                    </thead>
                    <tbody>
                      {accounts.map(acc => {
                        const hasActiveHold = activeFlags.some(f => f.accountId === acc.accountId);
                        return (
                          <tr key={acc.accountId}>
                            <td style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>{acc.accountNumber}</td>
                            <td>{acc.customerName || `Customer #${acc.customerId}`}</td>
                            <td><span className="badge badge-neutral">{acc.accountType}</span></td>
                            <td style={{ fontWeight: '700' }}>
                              ₱{(acc.balance ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                            </td>
                            <td>
                              <span className={`badge badge-${acc.status.toLowerCase()}`}>{acc.status}</span>
                            </td>
                            <td>
                              {hasActiveHold ? (
                                <span className="badge badge-rejected">ON HOLD</span>
                              ) : acc.status === 'FROZEN' ? (
                                <button
                                  type="button"
                                  onClick={async () => {
                                    await apiClient.updateAccountStatus(acc.accountId, 'ACTIVE');
                                    showToast('success', `Account #${acc.accountId} reactivated.`);
                                    loadAccounts();
                                    onRefreshGlobalData();
                                  }}
                                  className="banking-btn-secondary"
                                  style={{ padding: '3px 8px', fontSize: '11px', color: '#059669' }}
                                >
                                  <Unlock size={12} />
                                  <span>Unfreeze</span>
                                </button>
                              ) : (
                                <span style={{ fontSize: '12px', color: '#64748b' }}>Clear</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 4. END-OF-DAY BATCH PROCESSING & AUDIT SEAL                                */}
      {/* ========================================================================= */}
      {activeTab === 'batch' && (
        <div className="animate-fade">
          <EodBatchView showToast={showToast} />
        </div>
      )}
    </div>
  );
};
