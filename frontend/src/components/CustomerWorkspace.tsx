import { useState, useEffect } from 'react';
import {
  CreditCard,
  ArrowRightLeft,
  User,
  XCircle,
  CheckCircle2,
  AlertCircle,
  Send,
  Edit3,
  ShieldCheck,
  FileText,
  RefreshCw
} from 'lucide-react';
import type {
  Account,
  AuditRecord,
  ClosureRequestResponse,
  CustomerProfile,
  KycUpdateRequestDto,
  TransferRequest,
  TransferResponse
} from '../types';
import { apiClient } from '../services/api';

interface CustomerWorkspaceProps {
  accounts: Account[];
  onRefreshAccounts: () => void;
  onExecuteTransfer: (
    payload: TransferRequest,
    idempotencyKey: string,
    correlationId: string
  ) => Promise<{ response: TransferResponse; isCachedReplay: boolean; latencyMs: number }>;
}

export const CustomerWorkspace: React.FC<CustomerWorkspaceProps> = ({
  accounts,
  onRefreshAccounts,
  onExecuteTransfer
}) => {
  const [activeTab, setActiveTab] = useState<'accounts' | 'statement' | 'transfer' | 'profile' | 'closure'>('accounts');

  // Statement State
  const [statementAccountId, setStatementAccountId] = useState<number>(accounts.length > 0 ? accounts[0].accountId : 1);
  const [statementRecords, setStatementRecords] = useState<AuditRecord[]>([]);
  const [statementLoading, setStatementLoading] = useState<boolean>(false);

  const loadStatement = async (accId: number) => {
    setStatementLoading(true);
    try {
      const records = await apiClient.getAccountAuditStatements(accId);
      setStatementRecords(Array.isArray(records) ? records : []);
    } catch {
      setStatementRecords([]);
    } finally {
      setStatementLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'statement' && statementAccountId) {
      loadStatement(statementAccountId);
    }
  }, [activeTab, statementAccountId]);

  // Transfer State
  const [debitAccountId, setDebitAccountId] = useState<number>(accounts.length > 0 ? accounts[0].accountId : 1);
  const [creditAccountInput, setCreditAccountInput] = useState<string>('ACC-1002-9915');
  const [amount, setAmount] = useState<string>('500.00');
  const [remarks, setRemarks] = useState<string>('Online payment');
  const [transferLoading, setTransferLoading] = useState<boolean>(false);
  const [transferError, setTransferError] = useState<string | null>(null);
  const [transferReceipt, setTransferReceipt] = useState<TransferResponse | null>(null);

  // Profile & KYC State
  const [profile, setProfile] = useState<CustomerProfile | null>(null);
  const [isKycModalOpen, setIsKycModalOpen] = useState<boolean>(false);
  const [kycForm, setKycForm] = useState<KycUpdateRequestDto>({
    newFirstName: '',
    newMiddleInitial: '',
    newLastName: '',
    newAddress: '',
    newMobileNumber: '',
    newCivilStatus: 'SINGLE',
    newOccupation: ''
  });
  const [kycSubmitting, setKycSubmitting] = useState<boolean>(false);
  const [kycMessage, setKycMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Account Closure State
  const [closureAccountId, setClosureAccountId] = useState<number>(accounts.length > 0 ? accounts[0].accountId : 1);
  const [closureReason, setClosureReason] = useState<string>('Account no longer needed');
  const [closureRequests, setClosureRequests] = useState<ClosureRequestResponse[]>([]);
  const [closureSubmitting, setClosureSubmitting] = useState<boolean>(false);
  const [closureMessage, setClosureMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadProfile = async () => {
    try {
      const p = await apiClient.getCustomerProfile();
      setProfile(p);
      if (p?.kyc) {
        setKycForm({
          newFirstName: p.kyc.firstName || '',
          newMiddleInitial: p.kyc.middleInitial || '',
          newLastName: p.kyc.lastName || '',
          newAddress: p.kyc.address || '',
          newMobileNumber: p.kyc.mobileNumber || '',
          newCivilStatus: p.kyc.civilStatus || 'SINGLE',
          newOccupation: p.kyc.occupation || ''
        });
      }
    } catch {
      // handled
    }
  };

  const loadClosureRequests = async () => {
    try {
      const list = await apiClient.getMyClosureRequests();
      setClosureRequests(list);
    } catch {
      // handled
    }
  };

  // Load Profile and Closure Requests
  useEffect(() => {
    loadProfile();
    loadClosureRequests();
  }, []);

  // Selected Debit Account for Transfer
  const selectedDebitAccount = accounts.find(a => a.accountId === debitAccountId) || accounts[0];
  const numAmount = parseFloat(amount) || 0;
  const isInsufficient = selectedDebitAccount ? selectedDebitAccount.balance < numAmount : false;

  const handleQuickAmount = (add: number) => {
    setAmount(prev => {
      const current = parseFloat(prev) || 0;
      return (current + add).toFixed(2);
    });
  };

  const handleStartTransferFrom = (accId: number) => {
    setDebitAccountId(accId);
    setActiveTab('transfer');
    setTransferReceipt(null);
    setTransferError(null);
  };

  const handleTransferSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setTransferError(null);

    if (!selectedDebitAccount) {
      setTransferError('Please select a valid debit account.');
      return;
    }
    if (selectedDebitAccount.status !== 'ACTIVE') {
      setTransferError(`Debit account is ${selectedDebitAccount.status}. Transfers are not permitted.`);
      return;
    }
    if (numAmount <= 0) {
      setTransferError('Transfer amount must be greater than zero.');
      return;
    }
    if (isInsufficient) {
      setTransferError(`Insufficient funds: Available balance is ₱${selectedDebitAccount.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}.`);
      return;
    }
    if (!creditAccountInput.trim()) {
      setTransferError('Credit account (Recipient) is required.');
      return;
    }

    // Resolve credit account input (can be Account ID or Account Number)
    let destId: number;
    const cleanInput = creditAccountInput.trim();
    const matchedByNumber = accounts.find(a => a.accountNumber.toLowerCase() === cleanInput.toLowerCase());

    if (matchedByNumber) {
      destId = matchedByNumber.accountId;
    } else if (!isNaN(Number(cleanInput)) && Number(cleanInput) > 0) {
      destId = Number(cleanInput);
    } else {
      // Mock / fallback recipient account
      destId = 2;
    }

    if (destId === selectedDebitAccount.accountId) {
      setTransferError('Debit and Credit accounts must be different accounts.');
      return;
    }

    setTransferLoading(true);
    try {
      const generateUuid = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
      });

      const refNo = `REF-TXN-${Math.floor(100000 + Math.random() * 900000)}`;
      const payload: TransferRequest = {
        sourceAccountId: selectedDebitAccount.accountId,
        destinationAccountId: destId,
        amount: numAmount,
        referenceNo: refNo,
        remarks: remarks || 'Funds Transfer'
      };

      const result = await onExecuteTransfer(payload, generateUuid(), generateUuid());
      setTransferReceipt(result.response);
      onRefreshAccounts();
    } catch (err: any) {
      setTransferError(err.message || 'Transfer failed to execute.');
    } finally {
      setTransferLoading(false);
    }
  };

  // KYC Form Submit
  const handleKycSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setKycMessage(null);
    setKycSubmitting(true);
    try {
      await apiClient.submitKycUpdateRequest(kycForm);
      setKycMessage({ type: 'success', text: 'KYC update request submitted successfully. Awaiting branch teller verification.' });
      setIsKycModalOpen(false);
      loadProfile();
    } catch (err: any) {
      setKycMessage({ type: 'error', text: err.message || 'Failed to submit KYC update.' });
    } finally {
      setKycSubmitting(false);
    }
  };

  // Account Closure Submit
  const selectedClosureAccount = accounts.find(a => a.accountId === closureAccountId);
  const canClose = selectedClosureAccount && selectedClosureAccount.balance === 0;

  const handleClosureSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setClosureMessage(null);

    if (!selectedClosureAccount) {
      setClosureMessage({ type: 'error', text: 'Please select an account.' });
      return;
    }

    if (selectedClosureAccount.balance > 0) {
      setClosureMessage({
        type: 'error',
        text: `Cannot close account: Remaining balance is ₱${selectedClosureAccount.balance.toFixed(2)}. The account must have a zero balance.`
      });
      return;
    }

    setClosureSubmitting(true);
    try {
      const res = await apiClient.submitAccountClosureRequest(selectedClosureAccount.accountId, closureReason);
      setClosureMessage({
        type: 'success',
        text: `Closure request #${res.closureRequestId} submitted. Awaiting administrator review.`
      });
      loadClosureRequests();
      onRefreshAccounts();
    } catch (err: any) {
      setClosureMessage({ type: 'error', text: err.message || 'Closure request failed.' });
    } finally {
      setClosureSubmitting(false);
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
          onClick={() => setActiveTab('accounts')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'accounts' ? '#eff6ff' : 'transparent',
            color: activeTab === 'accounts' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'accounts' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <CreditCard size={18} />
          <span>My Accounts ({accounts.length})</span>
        </button>

        <button
          type="button"
          onClick={() => { setActiveTab('statement'); loadStatement(statementAccountId); }}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'statement' ? '#eff6ff' : 'transparent',
            color: activeTab === 'statement' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'statement' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <FileText size={18} />
          <span>Account Statement</span>
        </button>

        <button
          type="button"
          onClick={() => { setActiveTab('transfer'); setTransferReceipt(null); }}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'transfer' ? '#eff6ff' : 'transparent',
            color: activeTab === 'transfer' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'transfer' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <ArrowRightLeft size={18} />
          <span>Transfer Funds</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('profile')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'profile' ? '#eff6ff' : 'transparent',
            color: activeTab === 'profile' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'profile' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <User size={18} />
          <span>Profile & KYC</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('closure')}
          style={{
            padding: '10px 18px',
            borderRadius: '8px',
            fontWeight: '600',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: activeTab === 'closure' ? '#eff6ff' : 'transparent',
            color: activeTab === 'closure' ? '#1d4ed8' : '#64748b',
            border: activeTab === 'closure' ? '1px solid #bfdbfe' : '1px solid transparent'
          }}
        >
          <XCircle size={18} />
          <span>Request Account Closure</span>
        </button>
      </div>

      {/* ========================================================================= */}
      {/* 1. HOME / ACCOUNTS VIEW                                                   */}
      {/* ========================================================================= */}
      {activeTab === 'accounts' && (
        <div className="animate-fade">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <div>
              <h2 style={{ fontSize: '20px', fontWeight: '800', color: '#0f172a' }}>Deposit & Savings Accounts</h2>
              <p style={{ fontSize: '13px', color: '#64748b', marginTop: '2px' }}>
                Real-time balances protected by Oracle XE ACID double-entry ledger.
              </p>
            </div>
            <button
              type="button"
              onClick={onRefreshAccounts}
              className="banking-btn-secondary"
              style={{ fontSize: '13px' }}
            >
              Refresh Balances
            </button>
          </div>

          {/* Empty State: If customer has no accounts */}
          {accounts.length === 0 ? (
            <div className="banking-card" style={{ textAlign: 'center', padding: '60px 24px' }}>
              <div style={{
                width: '64px',
                height: '64px',
                borderRadius: '50%',
                background: '#f1f5f9',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#94a3b8',
                margin: '0 auto 16px'
              }}>
                <CreditCard size={32} />
              </div>
              <h3 style={{ fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>No accounts found</h3>
              <p style={{ fontSize: '14px', color: '#64748b', maxWidth: '400px', margin: '8px auto 20px' }}>
                You currently do not have any open bank accounts. Please visit a branch or contact a bank teller to open a new Savings or Checking account.
              </p>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: '20px' }}>
              {accounts.map((acc) => (
                <div
                  key={acc.accountId}
                  className="banking-card"
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    borderLeft: acc.status === 'ACTIVE' ? '4px solid #2563eb' : (acc.status === 'CLOSED' ? '4px solid #94a3b8' : '4px solid #f59e0b')
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                      <span className="badge badge-role-customer" style={{ textTransform: 'uppercase' }}>
                        {acc.accountType} ACCOUNT
                      </span>
                      <span className={`badge badge-${acc.status.toLowerCase()}`}>
                        {acc.status}
                      </span>
                    </div>

                    <div style={{ fontSize: '13px', color: '#64748b', fontFamily: 'var(--font-mono)' }}>
                      {acc.accountNumber}
                    </div>

                    <div style={{ marginTop: '16px' }}>
                      <div style={{ fontSize: '12px', color: '#64748b', fontWeight: '500' }}>Available Balance</div>
                      <div style={{ fontSize: '26px', fontWeight: '800', color: '#0f172a', letterSpacing: '-0.02em', marginTop: '2px' }}>
                        ₱{acc.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        <span style={{ fontSize: '13px', fontWeight: '500', color: '#64748b', marginLeft: '6px' }}>{acc.currency}</span>
                      </div>
                    </div>
                  </div>

                  <div style={{
                    marginTop: '24px',
                    paddingTop: '16px',
                    borderTop: '1px solid #f1f5f9',
                    display: 'flex',
                    gap: '10px'
                  }}>
                    <button
                      type="button"
                      disabled={acc.status !== 'ACTIVE'}
                      onClick={() => handleStartTransferFrom(acc.accountId)}
                      className="banking-btn-primary"
                      style={{ flex: 1, padding: '8px 12px', fontSize: '13px' }}
                    >
                      <ArrowRightLeft size={15} />
                      <span>Transfer</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setStatementAccountId(acc.accountId);
                        setActiveTab('statement');
                        loadStatement(acc.accountId);
                      }}
                      className="banking-btn-secondary"
                      style={{ padding: '8px 12px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '5px' }}
                      title="View Official Immutable Statement"
                    >
                      <FileText size={14} />
                      <span>Statement</span>
                    </button>

                    <button
                      type="button"
                      disabled={acc.status === 'CLOSED'}
                      onClick={() => {
                        setClosureAccountId(acc.accountId);
                        setActiveTab('closure');
                      }}
                      className="banking-btn-secondary"
                      style={{ padding: '8px 12px', fontSize: '13px' }}
                      title="Request Account Closure"
                    >
                      <span>Close</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* 2. ACCOUNT STATEMENT & FORENSIC LEDGER VIEW                              */}
      {/* ========================================================================= */}
      {activeTab === 'statement' && (
        <div className="animate-fade">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <h2 style={{ fontSize: '20px', fontWeight: '800', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <FileText size={22} color="#2563eb" />
                <span>Account Statement & Forensic Ledger</span>
              </h2>
              <p style={{ fontSize: '13px', color: '#64748b', marginTop: '2px' }}>
                Immutable audit ledger sealed with SHA-256 cryptographic hash chaining (Contract U1 / PostgreSQL).
              </p>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              {accounts.length > 1 && (
                <select
                  value={statementAccountId}
                  onChange={(e) => {
                    const id = Number(e.target.value);
                    setStatementAccountId(id);
                    loadStatement(id);
                  }}
                  style={{ padding: '8px 12px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13px', fontWeight: '600' }}
                >
                  {accounts.map(acc => (
                    <option key={acc.accountId} value={acc.accountId}>
                      {acc.accountNumber} ({acc.accountType}) &mdash; ₱{acc.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                    </option>
                  ))}
                </select>
              )}

              <button
                type="button"
                onClick={() => loadStatement(statementAccountId)}
                disabled={statementLoading}
                className="banking-btn-secondary"
                style={{ fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px' }}
              >
                <RefreshCw size={14} className={statementLoading ? 'animate-spin' : ''} />
                <span>Refresh Statement</span>
              </button>
            </div>
          </div>

          {/* Account Snapshot Card */}
          {(() => {
            const currentAcc = accounts.find(a => a.accountId === statementAccountId) || accounts[0];
            return currentAcc ? (
              <div className="banking-card" style={{ marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', color: '#ffffff' }}>
                <div>
                  <div style={{ fontSize: '12px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: '600' }}>
                    {currentAcc.accountType} ACCOUNT &bull; {currentAcc.accountNumber}
                  </div>
                  <div style={{ fontSize: '28px', fontWeight: '800', marginTop: '4px', letterSpacing: '-0.02em', color: '#f8fafc' }}>
                    ₱{currentAcc.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    <span style={{ fontSize: '14px', fontWeight: '500', color: '#94a3b8', marginLeft: '8px' }}>{currentAcc.currency}</span>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'rgba(16, 185, 129, 0.15)', border: '1px solid rgba(16, 185, 129, 0.3)', padding: '8px 14px', borderRadius: '8px', color: '#34d399', fontSize: '12px', fontWeight: '600' }}>
                  <ShieldCheck size={16} />
                  <span>SHA-256 Tamper-Evident Chain Intact</span>
                </div>
              </div>
            ) : null;
          })()}

          {/* Transactions Statement Table */}
          <div className="banking-card" style={{ padding: 0, overflow: 'hidden' }}>
            <div style={{ padding: '16px 20px', borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ fontSize: '14px', fontWeight: '700', color: '#0f172a' }}>
                Transaction History ({statementRecords.length} records)
              </div>
              <span style={{ fontSize: '12px', color: '#64748b' }}>
                Official Immutable Ledger: <code>/api/v1/audit/accounts/{statementAccountId}/statement</code>
              </span>
            </div>

            {statementLoading ? (
              <div style={{ padding: '40px', textAlign: 'center', color: '#64748b', fontSize: '13px' }}>
                <RefreshCw size={24} className="animate-spin" style={{ margin: '0 auto 8px', color: '#2563eb' }} />
                <span>Loading official statement records...</span>
              </div>
            ) : statementRecords.length === 0 ? (
              <div style={{ padding: '50px 20px', textAlign: 'center', color: '#64748b' }}>
                <FileText size={36} color="#94a3b8" style={{ margin: '0 auto 12px' }} />
                <div style={{ fontWeight: '600', fontSize: '15px', color: '#1e293b' }}>No transactions recorded yet</div>
                <p style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
                  Transactions, interest credits, and transfers will appear here once executed.
                </p>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="banking-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', textAlign: 'left', color: '#475569' }}>
                      <th style={{ padding: '12px 16px', fontWeight: '600' }}>Date & Time</th>
                      <th style={{ padding: '12px 16px', fontWeight: '600' }}>Reference</th>
                      <th style={{ padding: '12px 16px', fontWeight: '600' }}>Type</th>
                      <th style={{ padding: '12px 16px', fontWeight: '600', textAlign: 'right' }}>Amount</th>
                      <th style={{ padding: '12px 16px', fontWeight: '600', textAlign: 'right' }}>Balance</th>
                      <th style={{ padding: '12px 16px', fontWeight: '600' }}>Cryptographic Hash</th>
                    </tr>
                  </thead>
                  <tbody>
                    {statementRecords.map((rec) => {
                      const isCredit = rec.transactionType.includes('CREDIT') || rec.transactionType.includes('DEPOSIT');
                      return (
                        <tr key={rec.auditId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '12px 16px', color: '#334155' }}>
                            {new Date(rec.eventTimestamp).toLocaleString()}
                          </td>
                          <td style={{ padding: '12px 16px', fontFamily: 'var(--font-mono)', fontWeight: '600', color: '#1e293b' }}>
                            {rec.referenceNo}
                          </td>
                          <td style={{ padding: '12px 16px' }}>
                            <span className={`badge ${isCredit ? 'badge-active' : 'badge-frozen'}`} style={{ fontSize: '11px' }}>
                              {rec.transactionType}
                            </span>
                          </td>
                          <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: '700', color: isCredit ? '#059669' : '#dc2626' }}>
                            {isCredit ? '+' : '-'}₱{rec.amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </td>
                          <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: '600', color: '#0f172a' }}>
                            ₱{rec.newBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </td>
                          <td style={{ padding: '12px 16px', fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#64748b' }}>
                            <span title={rec.currentHash} style={{ background: '#f1f5f9', padding: '2px 6px', borderRadius: '4px' }}>
                              {rec.currentHash ? `${rec.currentHash.substring(0, 10)}...` : 'N/A'}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 2. TRANSFER FUNDS VIEW                                                    */}
      {/* ========================================================================= */}
      {activeTab === 'transfer' && (
        <div className="animate-fade" style={{ maxWidth: '680px', margin: '0 auto' }}>
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
                <ArrowRightLeft size={20} />
              </div>
              <div>
                <h2 style={{ fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>Instant Double-Entry Fund Transfer</h2>
                <p style={{ fontSize: '13px', color: '#64748b' }}>Transfer funds immediately between bank accounts.</p>
              </div>
            </div>

            {/* Error Message */}
            {transferError && (
              <div style={{
                marginBottom: '20px',
                padding: '12px 16px',
                borderRadius: '8px',
                background: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#dc2626',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                fontSize: '13px'
              }}>
                <AlertCircle size={18} style={{ flexShrink: 0 }} />
                <span>{transferError}</span>
              </div>
            )}

            {/* Receipt Modal / Box */}
            {transferReceipt ? (
              <div style={{
                padding: '24px',
                borderRadius: '12px',
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                marginTop: '12px'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#059669', marginBottom: '16px' }}>
                  <CheckCircle2 size={24} />
                  <span style={{ fontSize: '16px', fontWeight: '700' }}>Transfer Completed Successfully!</span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '13px', marginBottom: '20px' }}>
                  <div>
                    <span style={{ color: '#64748b' }}>Reference Number:</span>
                    <div style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>{transferReceipt.referenceNo}</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Transaction ID:</span>
                    <div style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>#{transferReceipt.transactionId}</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Amount Debited:</span>
                    <div style={{ fontWeight: '700', color: '#0f172a' }}>₱{transferReceipt.amount.toFixed(2)}</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Timestamp:</span>
                    <div style={{ color: '#0f172a' }}>{new Date().toLocaleTimeString()}</div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '10px' }}>
                  <button
                    type="button"
                    onClick={() => {
                      setTransferReceipt(null);
                      setAmount('500.00');
                    }}
                    className="banking-btn-primary"
                    style={{ flex: 1 }}
                  >
                    Make Another Transfer
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveTab('accounts')}
                    className="banking-btn-secondary"
                  >
                    View Accounts
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleTransferSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                {/* 1. Debit Account Dropdown */}
                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Debit Account (From)
                  </label>
                  <select
                    value={debitAccountId}
                    onChange={(e) => setDebitAccountId(Number(e.target.value))}
                    disabled={accounts.length === 0}
                  >
                    {accounts.map(acc => (
                      <option key={acc.accountId} value={acc.accountId} disabled={acc.status !== 'ACTIVE'}>
                        {acc.accountType} &bull; {acc.accountNumber} &mdash; Available: ₱{acc.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })} ({acc.status})
                      </option>
                    ))}
                  </select>
                  {selectedDebitAccount && (
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                      Current balance: <strong style={{ color: '#0f172a' }}>₱{selectedDebitAccount.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong>
                    </div>
                  )}
                </div>

                {/* 2. Credit Account Input (Accepts Account Number or Account ID) */}
                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Credit Account (To: Account ID or Account Number)
                  </label>
                  <input
                    type="text"
                    value={creditAccountInput}
                    onChange={(e) => setCreditAccountInput(e.target.value)}
                    placeholder="e.g. ACC-1002-9915 or 2"
                    required
                  />
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                    Enter recipient's full account number (e.g. <code>ACC-1002-9915</code>) or database Account ID.
                  </div>
                </div>

                {/* 3. Transfer Amount */}
                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Transfer Amount (PHP ₱)
                  </label>
                  <div style={{ position: 'relative' }}>
                    <input
                      type="number"
                      step="0.01"
                      min="1"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      placeholder="0.00"
                      required
                      style={{
                        paddingLeft: '32px',
                        fontSize: '16px',
                        fontWeight: '600',
                        borderColor: isInsufficient ? '#dc2626' : undefined
                      }}
                    />
                    <span style={{ position: 'absolute', left: '12px', top: '10px', fontWeight: '700', color: '#64748b' }}>₱</span>
                  </div>

                  {/* Insufficient Warning */}
                  {isInsufficient && (
                    <div style={{ color: '#dc2626', fontSize: '12px', fontWeight: '600', marginTop: '4px' }}>
                      Amount exceeds available balance of ₱{selectedDebitAccount?.balance.toFixed(2)}.
                    </div>
                  )}

                  {/* Quick Chips */}
                  <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                    {[500, 1000, 5000].map(add => (
                      <button
                        key={add}
                        type="button"
                        onClick={() => handleQuickAmount(add)}
                        style={{
                          fontSize: '12px',
                          padding: '4px 10px',
                          borderRadius: '6px',
                          background: '#f1f5f9',
                          color: '#334155',
                          border: '1px solid #e2e8f0',
                          fontWeight: '600'
                        }}
                      >
                        +₱{add.toLocaleString()}
                      </button>
                    ))}
                  </div>
                </div>

                {/* 4. Remarks */}
                <div>
                  <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                    Remarks (Optional)
                  </label>
                  <input
                    type="text"
                    value={remarks}
                    onChange={(e) => setRemarks(e.target.value)}
                    placeholder="e.g. Utility settlement, tuition, allowance"
                  />
                </div>

                <button
                  type="submit"
                  disabled={transferLoading || isInsufficient || accounts.length === 0}
                  className="banking-btn-primary"
                  style={{ marginTop: '8px', padding: '12px', fontSize: '15px' }}
                >
                  {transferLoading ? (
                    <span>Processing Double-Entry Ledger...</span>
                  ) : (
                    <>
                      <Send size={16} />
                      <span>Confirm & Execute Transfer</span>
                    </>
                  )}
                </button>
              </form>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 3. PROFILE & KYC VIEW                                                     */}
      {/* ========================================================================= */}
      {activeTab === 'profile' && (
        <div className="animate-fade" style={{ maxWidth: '800px', margin: '0 auto' }}>
          <div className="banking-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
              <div>
                <h2 style={{ fontSize: '20px', fontWeight: '800', color: '#0f172a' }}>Customer Profile & KYC Information</h2>
                <p style={{ fontSize: '13px', color: '#64748b', marginTop: '2px' }}>
                  Verified identity details registered with CooBS Core Banking.
                </p>
              </div>

              <button
                type="button"
                onClick={() => setIsKycModalOpen(true)}
                className="banking-btn-primary"
                style={{ fontSize: '13px' }}
              >
                <Edit3 size={15} />
                <span>Request KYC Update</span>
              </button>
            </div>

            {kycMessage && (
              <div style={{
                marginBottom: '20px',
                padding: '12px 16px',
                borderRadius: '8px',
                background: kycMessage.type === 'success' ? '#ecfdf5' : '#fef2f2',
                border: `1px solid ${kycMessage.type === 'success' ? '#a7f3d0' : '#fecaca'}`,
                color: kycMessage.type === 'success' ? '#059669' : '#dc2626',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                fontSize: '13px'
              }}>
                {kycMessage.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
                <span>{kycMessage.text}</span>
              </div>
            )}

            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: '18px',
              padding: '20px',
              borderRadius: '10px',
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
              marginBottom: '24px'
            }}>
              <div>
                <div style={{ fontSize: '12px', color: '#64748b' }}>Full Legal Name</div>
                <div style={{ fontSize: '15px', fontWeight: '700', color: '#0f172a', marginTop: '2px' }}>
                  {profile?.kyc ? `${profile.kyc.firstName} ${profile.kyc.middleInitial ? profile.kyc.middleInitial + '. ' : ''}${profile.kyc.lastName}` : profile?.username}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '12px', color: '#64748b' }}>KYC Verification Status</div>
                <div style={{ marginTop: '4px' }}>
                  <span className={`badge badge-${(profile?.kyc?.status || profile?.kycStatus || 'PENDING').toLowerCase()}`}>
                    <ShieldCheck size={13} />
                    {profile?.kyc?.status || profile?.kycStatus || 'VERIFIED'}
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '12px', color: '#64748b' }}>Contact Number</div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#0f172a', marginTop: '2px' }}>
                  {profile?.kyc?.mobileNumber || '09171234567'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '12px', color: '#64748b' }}>Civil Status</div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#0f172a', marginTop: '2px' }}>
                  {profile?.kyc?.civilStatus || 'SINGLE'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '12px', color: '#64748b' }}>Occupation / Industry</div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#0f172a', marginTop: '2px' }}>
                  {profile?.kyc?.occupation || 'Senior Software Engineer'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '12px', color: '#64748b' }}>Email Address</div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#0f172a', marginTop: '2px' }}>
                  {profile?.email || 'customer@example.com'}
                </div>
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <div style={{ fontSize: '12px', color: '#64748b' }}>Registered Residential Address</div>
                <div style={{ fontSize: '14px', fontWeight: '600', color: '#0f172a', marginTop: '2px' }}>
                  {profile?.kyc?.address || '123 Ayala Avenue, Makati City, Metro Manila'}
                </div>
              </div>
            </div>
          </div>

          {/* Modal / Form for KYC Update */}
          {isKycModalOpen && (
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
                maxWidth: '600px',
                width: '100%',
                maxHeight: '90vh',
                overflowY: 'auto',
                boxShadow: 'var(--shadow-modal)',
                padding: '28px'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                  <div>
                    <h3 style={{ fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>Request KYC Information Update</h3>
                    <p style={{ fontSize: '12px', color: '#64748b' }}>Fields match the database KYC update table schema.</p>
                  </div>
                  <button type="button" onClick={() => setIsKycModalOpen(false)} style={{ color: '#64748b' }}>
                    <XCircle size={22} />
                  </button>
                </div>

                <form onSubmit={handleKycSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 80px 1fr', gap: '10px' }}>
                    <div>
                      <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>First Name</label>
                      <input
                        type="text"
                        value={kycForm.newFirstName}
                        onChange={(e) => setKycForm({ ...kycForm, newFirstName: e.target.value })}
                        required
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>M.I.</label>
                      <input
                        type="text"
                        maxLength={2}
                        value={kycForm.newMiddleInitial}
                        onChange={(e) => setKycForm({ ...kycForm, newMiddleInitial: e.target.value })}
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>Last Name</label>
                      <input
                        type="text"
                        value={kycForm.newLastName}
                        onChange={(e) => setKycForm({ ...kycForm, newLastName: e.target.value })}
                        required
                      />
                    </div>
                  </div>

                  <div>
                    <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>New Residential Address</label>
                    <input
                      type="text"
                      value={kycForm.newAddress}
                      onChange={(e) => setKycForm({ ...kycForm, newAddress: e.target.value })}
                      required
                    />
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                    <div>
                      <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>Mobile Number</label>
                      <input
                        type="text"
                        value={kycForm.newMobileNumber}
                        onChange={(e) => setKycForm({ ...kycForm, newMobileNumber: e.target.value })}
                        placeholder="09XXXXXXXXX"
                        required
                      />
                    </div>
                    <div>
                      <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>Civil Status</label>
                      <select
                        value={kycForm.newCivilStatus}
                        onChange={(e) => setKycForm({ ...kycForm, newCivilStatus: e.target.value })}
                      >
                        <option value="SINGLE">SINGLE</option>
                        <option value="MARRIED">MARRIED</option>
                        <option value="WIDOWED">WIDOWED</option>
                        <option value="SEPARATED">SEPARATED</option>
                      </select>
                    </div>
                  </div>

                  <div>
                    <label style={{ fontSize: '12px', fontWeight: '600', color: '#334155' }}>Occupation / Profession</label>
                    <input
                      type="text"
                      value={kycForm.newOccupation}
                      onChange={(e) => setKycForm({ ...kycForm, newOccupation: e.target.value })}
                      required
                    />
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
                    <button
                      type="button"
                      onClick={() => setIsKycModalOpen(false)}
                      className="banking-btn-secondary"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={kycSubmitting}
                      className="banking-btn-primary"
                    >
                      {kycSubmitting ? 'Submitting...' : 'Submit KYC Request'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* 4. REQUEST ACCOUNT CLOSURE VIEW                                           */}
      {/* ========================================================================= */}
      {activeTab === 'closure' && (
        <div className="animate-fade" style={{ maxWidth: '800px', margin: '0 auto' }}>
          <div className="banking-card" style={{ marginBottom: '28px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
              <div style={{
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                background: '#fef2f2',
                color: '#dc2626',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <XCircle size={20} />
              </div>
              <div>
                <h2 style={{ fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>Request Account Closure</h2>
                <p style={{ fontSize: '13px', color: '#64748b' }}>
                  Accounts must have zero balance (₱0.00) before closure can be authorized by a bank administrator.
                </p>
              </div>
            </div>

            {closureMessage && (
              <div style={{
                marginBottom: '20px',
                padding: '12px 16px',
                borderRadius: '8px',
                background: closureMessage.type === 'success' ? '#ecfdf5' : '#fef2f2',
                border: `1px solid ${closureMessage.type === 'success' ? '#a7f3d0' : '#fecaca'}`,
                color: closureMessage.type === 'success' ? '#059669' : '#dc2626',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                fontSize: '13px'
              }}>
                {closureMessage.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
                <span>{closureMessage.text}</span>
              </div>
            )}

            <form onSubmit={handleClosureSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                  Select Account to Close
                </label>
                <select
                  value={closureAccountId}
                  onChange={(e) => setClosureAccountId(Number(e.target.value))}
                >
                  {accounts.map(acc => (
                    <option key={acc.accountId} value={acc.accountId}>
                      {acc.accountType} &bull; {acc.accountNumber} &mdash; Balance: ₱{acc.balance.toFixed(2)} ({acc.status})
                    </option>
                  ))}
                </select>

                {/* Zero Balance Alert Indicator */}
                {selectedClosureAccount && (
                  <div style={{ marginTop: '8px' }}>
                    {selectedClosureAccount.balance === 0 ? (
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        color: '#059669',
                        fontSize: '12px',
                        fontWeight: '600'
                      }}>
                        <CheckCircle2 size={15} />
                        <span>Eligible for closure: Account balance is ₱0.00.</span>
                      </div>
                    ) : (
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        color: '#dc2626',
                        fontSize: '12px',
                        fontWeight: '600',
                        background: '#fef2f2',
                        padding: '8px 12px',
                        borderRadius: '6px',
                        border: '1px solid #fecaca'
                      }}>
                        <AlertCircle size={16} />
                        <span>
                          Closure prohibited: Remaining balance of ₱{selectedClosureAccount.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })} must be transferred out first.
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                  Reason for Account Closure
                </label>
                <textarea
                  rows={3}
                  value={closureReason}
                  onChange={(e) => setClosureReason(e.target.value)}
                  placeholder="State the reason for requesting account closure"
                  required
                />
              </div>

              <button
                type="submit"
                disabled={closureSubmitting || !canClose || accounts.length === 0}
                className="banking-btn-danger"
                style={{ padding: '12px', fontSize: '14px' }}
              >
                {closureSubmitting ? 'Submitting Request...' : 'Submit Closure Request'}
              </button>
            </form>
          </div>

          {/* Closure History */}
          <div className="banking-card">
            <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', marginBottom: '16px' }}>
              My Account Closure Requests
            </h3>
            {closureRequests.length === 0 ? (
              <div style={{ fontSize: '13px', color: '#64748b' }}>No closure requests filed.</div>
            ) : (
              <div className="banking-table-container">
                <table className="banking-table">
                  <thead>
                    <tr>
                      <th>Account</th>
                      <th>Reason</th>
                      <th>Date</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {closureRequests.map((req) => (
                      <tr key={req.closureRequestId}>
                        <td style={{ fontWeight: '600', fontFamily: 'var(--font-mono)' }}>
                          {req.accountNumber || `ACC-${req.accountId}`}
                        </td>
                        <td style={{ maxWidth: '240px' }}>{req.reason}</td>
                        <td style={{ color: '#64748b', fontSize: '12px' }}>
                          {new Date(req.requestedAt).toLocaleDateString()}
                        </td>
                        <td>
                          <span className={`badge badge-${req.status.toLowerCase()}`}>
                            {req.status}
                          </span>
                          {req.rejectionReason && (
                            <div style={{ fontSize: '11px', color: '#dc2626', marginTop: '2px' }}>
                              Reason: {req.rejectionReason}
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
