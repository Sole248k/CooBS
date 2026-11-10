import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { StaffAccountSummary } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp, generateUUID } from '../../lib/utils';
import {
  Coins,
  ArrowDownCircle,
  ArrowUpCircle,
  Send,
  AlertCircle,
  CheckCircle2,
  Printer,
  QrCode,
  ExternalLink,
  ShieldAlert,
} from 'lucide-react';

type CounterTab = 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER';

const normalizeInitialAccountInput = (raw: string | null): string => {
  if (!raw) return 'ACC_10000001';
  const trimmed = raw.trim().toUpperCase();
  if (trimmed.startsWith('ACC_') || trimmed.startsWith('LN_')) return trimmed;
  const num = parseInt(trimmed, 10);
  if (!isNaN(num) && num > 0) {
    return num > 10000000 ? `ACC_${num}` : `ACC_${10000000 + num}`;
  }
  return trimmed;
};

export const CounterDesk: React.FC = () => {
  const { isTeller, isAdmin } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const initialMode = searchParams.get('mode')?.toUpperCase();
  const [activeTab, setActiveTab] = useState<CounterTab>(
    initialMode === 'WITHDRAW' || initialMode === 'WITHDRAWAL'
      ? 'WITHDRAW'
      : initialMode === 'TRANSFER'
      ? 'TRANSFER'
      : 'DEPOSIT'
  );

  // Accounts Directory for resolving ACC_XXXXXXX <-> accountId
  const [accounts, setAccounts] = useState<StaffAccountSummary[]>([]);

  // Form State (accepts ACC_XXXXXXX format)
  const [accountInput, setAccountInput] = useState(() =>
    normalizeInitialAccountInput(searchParams.get('accountId') || searchParams.get('accountNumber'))
  );
  const [destinationAccount, setDestinationAccount] = useState('ACC_10000002');
  const [amount, setAmount] = useState('1000.00');
  const [remarks, setRemarks] = useState('Counter posting transaction');
  const [category, setCategory] = useState('TRANSFER');

  // Execution State
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receiptData, setReceiptData] = useState<any | null>(null);

  const loadAccountsDirectory = async () => {
    try {
      const list = await staffApi.getAllAccounts();
      if (Array.isArray(list)) {
        setAccounts(list);
        // If URL passed a numeric accountId that maps to a non-sequential ACC_XXXXXXX, sync display
        const rawParam = searchParams.get('accountId')?.trim();
        if (rawParam && /^\d+$/.test(rawParam)) {
          const matchById = list.find((a) => a.accountId === parseInt(rawParam, 10));
          if (matchById?.accountNumber) {
            setAccountInput(matchById.accountNumber);
          }
        }
      }
    } catch {
      // Non-blocking fallback to pattern resolution if directory fetch fails
    }
  };

  useEffect(() => {
    loadAccountsDirectory();
  }, []);

  // Resolve user input (e.g. "ACC_10000001", "ACC_D3CF90C0", or "1") to internal numeric ID + display number
  const resolveAccount = (
    rawInput: string
  ): { accountId: number; accountNumber: string; summary?: StaffAccountSummary } | null => {
    const cleaned = rawInput.trim().toUpperCase();
    if (!cleaned) return null;

    // 1. Exact match by accountNumber in directory (handles ACC_10000001 and hex ACC_D3CF90C0)
    const exactMatch = accounts.find((a) => a.accountNumber?.toUpperCase() === cleaned);
    if (exactMatch) {
      return {
        accountId: exactMatch.accountId,
        accountNumber: exactMatch.accountNumber,
        summary: exactMatch,
      };
    }

    // 2. Match if user omitted "ACC_" prefix (e.g. "10000001" or "D3CF90C0")
    const prefixedMatch = accounts.find(
      (a) => a.accountNumber?.toUpperCase() === `ACC_${cleaned}`
    );
    if (prefixedMatch) {
      return {
        accountId: prefixedMatch.accountId,
        accountNumber: prefixedMatch.accountNumber,
        summary: prefixedMatch,
      };
    }

    // 3. Sequential ACC_100000XX or raw numeric ID fallback
    const seqMatch = cleaned.match(/^(?:ACC_)?(\d+)$/i);
    if (seqMatch) {
      const num = parseInt(seqMatch[1], 10);
      if (!isNaN(num) && num > 0) {
        const internalId = num > 10000000 ? num - 10000000 : num;
        const byId = accounts.find((a) => a.accountId === internalId);
        if (byId) {
          return {
            accountId: byId.accountId,
            accountNumber: byId.accountNumber,
            summary: byId,
          };
        }
        if (internalId > 0) {
          return {
            accountId: internalId,
            accountNumber: num > 10000000 ? `ACC_${num}` : `ACC_${10000000 + internalId}`,
          };
        }
      }
    }

    return null;
  };

  const resolvedSourceAccount = resolveAccount(accountInput);
  const resolvedDestAccount =
    activeTab === 'TRANSFER' ? resolveAccount(destinationAccount) : null;

  // If Branch Manager attempts to select assisted transfer, revert to Deposit
  useEffect(() => {
    if (isAdmin && !isTeller && activeTab === 'TRANSFER') {
      setActiveTab('DEPOSIT');
    }
  }, [isAdmin, isTeller, activeTab]);

  const handleDenomination = (addAmount: number) => {
    const current = parseFloat(amount) || 0;
    setAmount((current + addAmount).toFixed(2));
  };

  const handleClearAmount = () => {
    setAmount('0.00');
  };

  const pending = useRef<{ operation: string; idempotencyKey: string; referenceNo: string } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const resolved = resolveAccount(accountInput);
    const parsedAmount = parseFloat(amount);

    if (!resolved || resolved.accountId <= 0) {
      setError(
        'Please enter a valid Account Number (e.g. ACC_10000001) or select an active account from the list.'
      );
      return;
    }

    const parsedAccountId = resolved.accountId;
    const normalizedDest = destinationAccount.trim().toUpperCase();

    if (activeTab === 'TRANSFER' && !normalizedDest) {
      setError('Please provide a valid Destination Account Number (e.g. ACC_10000002).');
      return;
    }

    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Amount must be greater than ₱0.00.');
      return;
    }

    setLoading(true);
    const startedAt = Date.now();

    // Generate a fresh key per submission after any settled request so velocity tests create distinct transactions
    const operation = `${activeTab}|${parsedAccountId}|${parsedAmount.toFixed(2)}|${normalizedDest}`;
    if (pending.current?.operation !== operation) {
      pending.current = {
        operation,
        idempotencyKey: generateUUID(),
        referenceNo: `TX-${Date.now()}-${Math.floor(1000 + Math.random() * 9000)}`,
      };
    }
    const { idempotencyKey, referenceNo } = pending.current;

    try {
      let result;
      if (activeTab === 'DEPOSIT') {
        result = await staffApi.depositCash(
          {
            accountId: parsedAccountId,
            amount: parsedAmount.toFixed(2),
            referenceNo,
            remarks,
          },
          idempotencyKey
        );
      } else if (activeTab === 'WITHDRAW') {
        result = await staffApi.withdrawCash(
          {
            accountId: parsedAccountId,
            amount: parsedAmount.toFixed(2),
            referenceNo,
            remarks,
          },
          idempotencyKey
        );
      } else if (activeTab === 'TRANSFER') {
        result = await staffApi.assistedTransfer(
          {
            sourceAccountId: parsedAccountId,
            destinationAccountNumber: resolvedDestAccount?.accountNumber || normalizedDest,
            destinationBankCode: 'COOBS',
            amount: parsedAmount.toFixed(2),
            currency: 'PHP',
            category,
            remarks,
          },
          idempotencyKey
        );
      }

      // Server answered definitively; clear idempotency cache so next click creates a new transaction
      pending.current = null;

      const txRef = result?.referenceNo || result?.transactionReference || referenceNo;
      const txId = result?.transactionId || generateUUID();

      // For asynchronous transfers, wait briefly for outcome settlement while the CooBS buffering animation plays
      let pipelineStatus: 'COMPLETED' | 'FAILED' | 'PROCESSING' = 'COMPLETED';
      let failureCode: string | null = null;
      let failedStageNo: number | null = null;
      let failedStageName: string | null = null;
      let failedStageTitle: string | null = null;
      let failedStageDetail: string | null = null;

      if (activeTab === 'TRANSFER') {
        pipelineStatus = 'PROCESSING';
        const lookupKey = result?.transactionId || txRef;
        for (let attempt = 0; attempt < 16; attempt++) {
          await new Promise((r) => setTimeout(r, 350));
          try {
            const tl: any = await staffApi.getTransactionTimeline(lookupKey);
            const rawStatus = String(tl?.status || tl?.overallStatus || '').toUpperCase();
            const stages: any[] = Array.isArray(tl?.stages) ? tl.stages : [];
            const failedStage = stages.find(
              (s) =>
                String(s.status || '').toUpperCase() === 'FAILED' ||
                String(s.outcome || '').toUpperCase() === 'FAILED' ||
                String(s.outcome || '').toUpperCase() === 'REJECTED'
            );
            const stage8Passed = stages.some(
              (s) =>
                (s.stageNo === 8 || s.stageNumber === 8 || s.stageName === 'POSTED') &&
                (String(s.outcome || '').toUpperCase() === 'PASSED' ||
                  String(s.status || '').toUpperCase() === 'COMPLETED')
            );

            if (failedStage || ['FAILED', 'REJECTED', 'DECLINED'].includes(rawStatus)) {
              pipelineStatus = 'FAILED';
              failureCode =
                tl?.failureMessage ||
                tl?.transaction?.failureCode ||
                tl?.failureCode ||
                failedStage?.stageName ||
                'PIPELINE_DECLINED';
              failedStageNo = failedStage?.stageNo ?? failedStage?.stageNumber ?? null;
              failedStageName = failedStage?.stageName ?? failedStage?.stageKey ?? null;
              failedStageTitle = failedStage?.title ?? failedStageName ?? 'Pipeline Check';
              failedStageDetail =
                failedStage?.detail ||
                tl?.transaction?.failureReason ||
                `Halted at ${failedStageTitle || 'pipeline validation'} (${failureCode})`;
              break;
            }

            if (['POSTED', 'COMPLETED', 'SETTLED'].includes(rawStatus) || stage8Passed) {
              pipelineStatus = 'COMPLETED';
              break;
            }
          } catch {
            // Continue polling until timeout
          }
        }
      }

      // Ensure the branded CooBS buffering animation plays smoothly for at least 750ms before transitioning to the modal
      const elapsed = Date.now() - startedAt;
      if (elapsed < 750) {
        await new Promise((r) => setTimeout(r, 750 - elapsed));
      }

      setReceiptData({
        type: activeTab,
        accountId: parsedAccountId,
        accountNumber: resolved.accountNumber,
        destinationAccount:
          activeTab === 'TRANSFER'
            ? resolvedDestAccount?.accountNumber || normalizedDest
            : null,
        amount: parsedAmount.toFixed(2),
        referenceNo: txRef,
        transactionId: txId,
        timestamp: new Date().toISOString(),
        newBalance: result?.newBalance ?? result?.balance ?? null,
        pipelineStatus,
        failureCode,
        failedStageNo,
        failedStageName,
        failedStageTitle,
        failedStageDetail,
      });
      loadAccountsDirectory();
    } catch (err: any) {
      pending.current = null;
      setError(err.message || 'Counter operation failed. Please check balance and account status.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">Counter Desk</h1>
          <p className="text-xs text-[#64748B]">
            Real-time cash ledger postings &amp; assisted member transfers
          </p>
        </div>
      </div>

      {/* Supervisory Notice for Admins */}
      {isAdmin && (
        <div className="p-4 rounded-xl bg-indigo-50 border border-indigo-200 flex items-start space-x-3 text-xs text-indigo-950 shadow-xs">
          <ShieldAlert className="w-5 h-5 text-indigo-700 flex-shrink-0 mt-0.5" />
          <div className="space-y-1">
            <span className="font-bold text-sm block">Supervisory Oversight Mode</span>
            <p className="text-indigo-800 leading-relaxed">
              You are accessing the Branch Counter Desk with Branch Manager credentials. In accordance with banking Segregation of Duties (SoD), physical cash deposits and counter disbursements are designated for branch tellers. Any action taken here will be logged under supervisory audit records.
            </p>
          </div>
        </div>
      )}

      {/* Teller Active Till Status Banner */}
      {isTeller && (
        <div className="p-3.5 rounded-xl bg-blue-50/80 border border-blue-200 flex items-center justify-between text-xs text-blue-900 shadow-xs">
          <div className="flex items-center space-x-2">
            <Coins className="w-4 h-4 text-blue-700" />
            <span>Active Shift Till Status: <strong className="font-mono">₱62,300.00</strong> (Drawer Float In-Balance)</span>
          </div>
          <span className="text-[11px] text-blue-700 font-semibold bg-blue-100/70 px-2 py-0.5 rounded-md">Terminal #1 &bull; Ready</span>
        </div>
      )}

      {/* Segmented Mode Switcher */}
      <div className="bg-white rounded-xl p-1.5 border border-[#E2E8F0] shadow-sm flex items-center space-x-1">
        <button
          onClick={() => setActiveTab('DEPOSIT')}
          className={`flex-1 py-2.5 px-4 rounded-lg text-xs font-bold transition-all flex items-center justify-center space-x-2 ${
            activeTab === 'DEPOSIT'
              ? 'bg-[#1E3A5F] text-white shadow-sm'
              : 'text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F8FAFC]'
          }`}
        >
          <ArrowDownCircle className="w-4 h-4 text-[#10B981]" />
          <span>Cash Deposit</span>
        </button>

        <button
          onClick={() => setActiveTab('WITHDRAW')}
          className={`flex-1 py-2.5 px-4 rounded-lg text-xs font-bold transition-all flex items-center justify-center space-x-2 ${
            activeTab === 'WITHDRAW'
              ? 'bg-[#1E3A5F] text-white shadow-sm'
              : 'text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F8FAFC]'
          }`}
        >
          <ArrowUpCircle className="w-4 h-4 text-[#EF4444]" />
          <span>Cash Withdrawal</span>
        </button>

        {/* Assisted Transfer is Teller Only per RBAC matrix */}
        {isTeller && (
          <button
            onClick={() => setActiveTab('TRANSFER')}
            className={`flex-1 py-2.5 px-4 rounded-lg text-xs font-bold transition-all flex items-center justify-center space-x-2 ${
              activeTab === 'TRANSFER'
                ? 'bg-[#1E3A5F] text-white shadow-sm'
                : 'text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F8FAFC]'
            }`}
          >
            <Send className="w-4 h-4 text-[#2563EB]" />
            <span>Assisted Transfer (Teller Only)</span>
          </button>
        )}
      </div>

      {/* Main Transaction Form Card */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-6 space-y-6">
        {error && (
          <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <div className="font-semibold leading-relaxed">{error}</div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Account Directory Datalist for Quick Autocomplete */}
          <datalist id="counter-accounts-list">
            {accounts
              .filter((a) => a.status !== 'CLOSED')
              .map((acc) => (
                <option
                  key={acc.accountId}
                  value={acc.accountNumber}
                  label={`${acc.accountType} • ${acc.status} • Avail: ${formatCurrency(acc.availableBalance)}`}
                />
              ))}
          </datalist>

          {/* Account Details */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                {activeTab === 'TRANSFER' ? 'Source Account Number' : 'Target Account Number'}
              </label>
              <input
                type="text"
                list="counter-accounts-list"
                value={accountInput}
                onChange={(e) => setAccountInput(e.target.value.toUpperCase())}
                required
                className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB] focus:outline-none uppercase"
                placeholder="e.g. ACC_10000001"
              />
              {resolvedSourceAccount?.summary ? (
                <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11px] font-mono text-[#1E3A5F] bg-[#F8FAFC] border border-[#E2E8F0] px-2.5 py-1 rounded-md">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                  <span className="font-bold">{resolvedSourceAccount.accountNumber}</span>
                  <span>&bull;</span>
                  <span className="text-[#64748B]">{resolvedSourceAccount.summary.accountType}</span>
                  <span>&bull;</span>
                  <span
                    className={
                      resolvedSourceAccount.summary.status === 'ACTIVE'
                        ? 'text-emerald-600 font-semibold'
                        : 'text-amber-600 font-semibold'
                    }
                  >
                    {resolvedSourceAccount.summary.status}
                  </span>
                  <span>&bull;</span>
                  <span className="text-[#64748B]">
                    Avail:{' '}
                    <strong className="text-[#1E3A5F]">
                      {formatCurrency(resolvedSourceAccount.summary.availableBalance)}
                    </strong>
                  </span>
                </div>
              ) : resolvedSourceAccount ? (
                <span className="text-[11px] font-mono text-emerald-700 mt-1 block">
                  Resolved Account: <strong>{resolvedSourceAccount.accountNumber}</strong>
                </span>
              ) : (
                <span className="text-[11px] text-[#64748B] mt-1 block">
                  Enter account number in <span className="font-mono font-semibold">ACC_XXXXXXX</span> format
                </span>
              )}
            </div>

            {activeTab === 'TRANSFER' && (
              <div>
                <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                  Destination Account Number
                </label>
                <input
                  type="text"
                  list="counter-accounts-list"
                  value={destinationAccount}
                  onChange={(e) => setDestinationAccount(e.target.value.toUpperCase())}
                  required
                  className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB] focus:outline-none uppercase"
                  placeholder="e.g. ACC_10000002"
                />
                {resolvedDestAccount?.summary ? (
                  <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11px] font-mono text-[#1E3A5F] bg-[#F8FAFC] border border-[#E2E8F0] px-2.5 py-1 rounded-md">
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                    <span className="font-bold">{resolvedDestAccount.accountNumber}</span>
                    <span>&bull;</span>
                    <span className="text-[#64748B]">{resolvedDestAccount.summary.accountType}</span>
                    <span>&bull;</span>
                    <span className="text-emerald-600 font-semibold">
                      {resolvedDestAccount.summary.status}
                    </span>
                  </div>
                ) : (
                  <span className="text-[11px] text-[#64748B] mt-1 block">
                    Target beneficiary account in <span className="font-mono font-semibold">ACC_XXXXXXX</span> format
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Prominent Amount Input & Denomination Chips */}
          <div className="p-5 rounded-xl bg-[#F8FAFC] border border-[#E2E8F0] space-y-4">
            <div>
              <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                Posting Amount (PHP)
              </label>
              <div className="relative">
                <span className="absolute left-4 top-1/2 -translate-y-1/2 font-bold text-2xl text-[#1E3A5F]">
                  ₱
                </span>
                <input
                  type="text"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  required
                  className="w-full pl-10 pr-4 py-3 text-2xl font-bold font-mono text-[#1E3A5F] bg-white border border-[#CBD5E1] rounded-lg focus:ring-2 focus:ring-[#2563EB] focus:outline-none tabular-nums"
                  placeholder="0.00"
                />
              </div>
            </div>

            {/* Quick Denomination Chips */}
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold text-[#64748B] mr-2">Quick Add:</span>
              <button
                type="button"
                onClick={() => handleDenomination(1000)}
                className="px-3 py-1.5 rounded-lg bg-white border border-[#CBD5E1] hover:border-[#2563EB] text-xs font-mono font-semibold text-[#1E3A5F] transition-colors shadow-2xs"
              >
                +₱1,000
              </button>
              <button
                type="button"
                onClick={() => handleDenomination(5000)}
                className="px-3 py-1.5 rounded-lg bg-white border border-[#CBD5E1] hover:border-[#2563EB] text-xs font-mono font-semibold text-[#1E3A5F] transition-colors shadow-2xs"
              >
                +₱5,000
              </button>
              <button
                type="button"
                onClick={() => handleDenomination(10000)}
                className="px-3 py-1.5 rounded-lg bg-white border border-[#CBD5E1] hover:border-[#2563EB] text-xs font-mono font-semibold text-[#1E3A5F] transition-colors shadow-2xs"
              >
                +₱10,000
              </button>
              <button
                type="button"
                onClick={() => handleDenomination(50000)}
                className="px-3 py-1.5 rounded-lg bg-white border border-[#CBD5E1] hover:border-[#2563EB] text-xs font-mono font-semibold text-[#1E3A5F] transition-colors shadow-2xs"
              >
                +₱50,000
              </button>
              <button
                type="button"
                onClick={handleClearAmount}
                className="px-3 py-1.5 rounded-lg bg-[#F1F5F9] hover:bg-[#E2E8F0] text-xs font-semibold text-[#64748B] transition-colors"
              >
                Clear
              </button>
            </div>
          </div>

          {/* Remarks & Category */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {activeTab === 'TRANSFER' && (
              <div>
                <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                  Transfer Purpose
                </label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                >
                  <option value="TRANSFER">General Transfer</option>
                  <option value="BILLS">Utility &amp; Bills</option>
                  <option value="LOAN_REPAYMENT">Loan Repayment</option>
                  <option value="SAVINGS">Savings Deposit</option>
                  <option value="FEES">Institutional Fees</option>
                  <option value="OTHER">Other Counter Remarks</option>
                </select>
              </div>
            )}

            <div className={activeTab === 'TRANSFER' ? '' : 'col-span-2'}>
              <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                Posting Audit Remarks
              </label>
              <input
                type="text"
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                placeholder="Branch counter teller notes..."
              />
            </div>
          </div>

          {/* Submit Action */}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-6 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-sm font-bold rounded-xl shadow-md transition-all flex items-center justify-center space-x-2 disabled:opacity-50"
          >
            <Coins className="w-4 h-4" />
            <span>
              Confirm &amp; Post {activeTab === 'DEPOSIT' ? 'Deposit' : activeTab === 'WITHDRAW' ? 'Withdrawal' : 'Transfer'} (₱{parseFloat(amount || '0').toLocaleString('en-PH', { minimumFractionDigits: 2 })})
            </span>
          </button>
        </form>
      </div>

      {/* CooBS Branded Buffering Animation Modal */}
      {loading && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-sm w-full p-8 flex flex-col items-center text-center space-y-5 animate-in fade-in zoom-in-95 duration-150">
            {/* Dual Orbiting CooBS Ring + Brand Logo Centerpiece */}
            <div className="relative w-28 h-28 flex items-center justify-center">
              {/* Soft outer ambient glow */}
              <div className="absolute inset-0 rounded-full bg-gradient-to-tr from-[#1E3A5F]/10 via-[#0284C7]/15 to-[#2563EB]/10 animate-pulse" />
              {/* Primary clockwise CooBS navy/teal ring */}
              <div className="absolute inset-0 rounded-full border-[3.5px] border-[#E2E8F0] border-t-[#1E3A5F] border-r-[#0284C7] animate-spin" />
              {/* Secondary counter-rotating accent ring */}
              <div
                className="absolute inset-2.5 rounded-full border-[2.5px] border-transparent border-b-[#2563EB] border-l-[#0284C7]/60 animate-spin"
                style={{ animationDirection: 'reverse', animationDuration: '1.4s' }}
              />
              {/* Center CooBS Logo Badge */}
              <div className="relative z-10 w-20 h-20 rounded-full bg-white shadow-inner border border-[#F1F5F9] flex flex-col items-center justify-center px-2.5">
                <img
                  src="/coobs-logo.png"
                  alt="CooBS"
                  className="w-full object-contain animate-pulse"
                />
              </div>
            </div>

            {/* Branded Status Copy */}
            <div className="space-y-1.5">
              <h3 className="text-base font-bold text-[#1E3A5F] tracking-tight">
                Processing {activeTab === 'DEPOSIT' ? 'Deposit' : activeTab === 'WITHDRAW' ? 'Withdrawal' : 'Transfer'}
              </h3>
              <p className="text-xs text-[#64748B]">
                Please wait while CooBS Core settles your transaction&hellip;
              </p>
            </div>

            {/* CooBS Branded Bouncing Dots */}
            <div className="flex items-center justify-center space-x-2 pt-1">
              <span
                className="w-2 h-2 rounded-full bg-[#1E3A5F] animate-bounce"
                style={{ animationDelay: '0ms' }}
              />
              <span
                className="w-2 h-2 rounded-full bg-[#0284C7] animate-bounce"
                style={{ animationDelay: '150ms' }}
              />
              <span
                className="w-2 h-2 rounded-full bg-[#2563EB] animate-bounce"
                style={{ animationDelay: '300ms' }}
              />
            </div>
          </div>
        </div>
      )}

      {/* Transaction Outcome Receipt Modal */}
      {receiptData && (() => {
        const isFailed = receiptData.pipelineStatus === 'FAILED';
        const isProcessing = receiptData.pipelineStatus === 'PROCESSING';
        const isRiskDecline =
          isFailed &&
          (['RISK_DECLINED', 'LIMIT_EXCEEDED', 'HIGH_RISK_SCORE', 'VELOCITY_EXCEEDED', 'AMLA_THRESHOLD'].includes(
            String(receiptData.failureCode || '').toUpperCase()
          ) ||
            ['FRAUD_CHECK', 'LIMIT_CHECK'].includes(String(receiptData.failedStageName || '').toUpperCase()));

        return (
          <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-6 animate-in fade-in zoom-in-95 duration-150">
              {/* Header */}
              <div className="text-center space-y-1">
                {isFailed ? (
                  <>
                    <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-600 mx-auto flex items-center justify-center mb-2">
                      <ShieldAlert className="w-6 h-6" />
                    </div>
                    <h3 className="text-lg font-bold text-rose-700">
                      {isRiskDecline ? 'Transaction Declined by Risk Engine' : 'Transaction Failed Mid-Pipeline'}
                    </h3>
                    <p className="text-xs text-rose-600 font-medium">
                      {receiptData.failedStageNo
                        ? `Halted at Stage #${receiptData.failedStageNo} (${receiptData.failedStageTitle || receiptData.failedStageName}) — Funds Not Posted`
                        : `Pipeline Declined (${receiptData.failureCode || 'FAILED'}) — Funds Not Posted`}
                    </p>
                  </>
                ) : isProcessing ? (
                  <>
                    <div className="w-12 h-12 rounded-full bg-amber-100 text-amber-600 mx-auto flex items-center justify-center mb-2">
                      <AlertCircle className="w-6 h-6" />
                    </div>
                    <h3 className="text-lg font-bold text-[#1E3A5F]">Transfer Processing</h3>
                    <p className="text-xs text-amber-700">
                      11-Stage Pipeline In Progress &bull; Pending Final Settlement
                    </p>
                  </>
                ) : (
                  <>
                    <div className="w-12 h-12 rounded-full bg-[#D1FAE5] text-[#10B981] mx-auto flex items-center justify-center mb-2">
                      <CheckCircle2 className="w-6 h-6" />
                    </div>
                    <h3 className="text-lg font-bold text-[#1E3A5F]">Transaction Complete</h3>
                    <p className="text-xs text-[#64748B]">Official Teller Posting Slip</p>
                  </>
                )}
              </div>

              {/* Receipt Summary */}
              <div
                className={`p-4 rounded-xl border space-y-3 font-mono text-xs ${
                  isFailed
                    ? 'bg-rose-50/50 border-rose-200'
                    : isProcessing
                    ? 'bg-amber-50/40 border-amber-200'
                    : 'bg-[#F8FAFC] border-[#E2E8F0]'
                }`}
              >
                <div className="flex justify-between">
                  <span className="text-[#64748B]">Reference No:</span>
                  <span className="font-bold text-[#1E3A5F]">{receiptData.referenceNo}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#64748B]">Operation:</span>
                  <span className="font-bold text-[#1E3A5F]">{receiptData.type}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[#64748B]">Pipeline Status:</span>
                  {isFailed ? (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-700 border border-rose-200">
                      FAILED &bull; {receiptData.failureCode || receiptData.failedStageName || 'DECLINED'}
                    </span>
                  ) : isProcessing ? (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-700 border border-amber-200">
                      PROCESSING (11-STAGE SAGA)
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-700 border border-emerald-200">
                      POSTED &amp; SETTLED
                    </span>
                  )}
                </div>
                {isFailed && receiptData.failedStageName && (
                  <div className="flex justify-between">
                    <span className="text-[#64748B]">Halted Stage:</span>
                    <span className="font-bold text-rose-700">
                      #{receiptData.failedStageNo || '?'} {receiptData.failedStageName}
                    </span>
                  </div>
                )}
                {isFailed && receiptData.failedStageDetail && (
                  <div className="pt-1 border-t border-rose-200/70 text-[11px] font-sans text-rose-700">
                    <span className="font-semibold">Decline Reason: </span>
                    <span>{receiptData.failedStageDetail}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-[#64748B]">Account:</span>
                  <span className="font-bold text-[#1E3A5F]">
                    {receiptData.accountNumber || `ACC_${10000000 + Number(receiptData.accountId)}`}
                  </span>
                </div>
                {receiptData.destinationAccount && (
                  <div className="flex justify-between">
                    <span className="text-[#64748B]">Destination:</span>
                    <span className="font-bold text-[#1E3A5F]">{receiptData.destinationAccount}</span>
                  </div>
                )}
                <div className="flex justify-between text-sm pt-2 border-t border-[#E2E8F0]">
                  <span className="text-[#64748B] font-sans font-semibold">
                    {isFailed ? 'Amount Attempted:' : isProcessing ? 'Amount In Flight:' : 'Amount Posted:'}
                  </span>
                  <span
                    className={`font-bold tabular-nums ${
                      isFailed
                        ? 'text-rose-600 line-through'
                        : isProcessing
                        ? 'text-amber-600'
                        : 'text-[#10B981]'
                    }`}
                  >
                    {formatCurrency(receiptData.amount)}
                  </span>
                </div>
                {isFailed && (
                  <div className="flex justify-between text-xs">
                    <span className="text-[#64748B] font-sans">Actual Ledger Debit:</span>
                    <span className="font-bold text-[#1E3A5F]">₱0.00 (No Funds Moved)</span>
                  </div>
                )}
                {!isFailed && receiptData.newBalance !== null && (
                  <div className="flex justify-between pt-1">
                    <span className="text-[#64748B]">New Balance:</span>
                    <span className="font-bold text-[#1E3A5F] tabular-nums">
                      {formatCurrency(receiptData.newBalance)}
                    </span>
                  </div>
                )}
                <div className="flex justify-between pt-2 border-t border-[#E2E8F0] text-[10px] text-[#64748B]">
                  <span>Timestamp:</span>
                  <span>{formatTimestamp(receiptData.timestamp)}</span>
                </div>
              </div>

              {/* Verification / Failure Footer */}
              {isFailed ? (
                <div className="flex items-center justify-center space-x-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded-lg py-2 px-3">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>Pipeline halted prior to Ledger Posting &mdash; recorded as FAILED</span>
                </div>
              ) : (
                <div className="flex items-center justify-center space-x-2 text-xs text-[#64748B]">
                  <QrCode className="w-5 h-5 text-[#1E3A5F]" />
                  <span>
                    {isProcessing
                      ? 'Pipeline Telemetry Active in Tracker'
                      : 'Digital Verification Proof Anchored'}
                  </span>
                </div>
              )}

              {/* Actions */}
              <div className="space-y-2">
                {receiptData.type === 'TRANSFER' && (
                  <button
                    onClick={() => navigate(`/tracker?ref=${receiptData.referenceNo}`)}
                    className={`w-full py-2.5 px-4 rounded-lg text-xs font-bold transition-colors flex items-center justify-center space-x-1.5 ${
                      isFailed
                        ? 'bg-rose-600 text-white hover:bg-rose-700 shadow-xs'
                        : 'bg-[#EFF6FF] text-[#2563EB] hover:bg-[#DBEAFE]'
                    }`}
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>
                      {isFailed
                        ? 'Inspect Failed Stage in 11-Stage Tracker'
                        : 'View 11-Stage Pipeline in Tracker'}
                    </span>
                  </button>
                )}
                <div className="flex space-x-2">
                  <button
                    onClick={() => window.print()}
                    className="flex-1 py-2.5 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC] transition-colors flex items-center justify-center space-x-1.5"
                  >
                    <Printer className="w-3.5 h-3.5" />
                    <span>Print Slip</span>
                  </button>
                  <button
                    onClick={() => setReceiptData(null)}
                    className="flex-1 py-2.5 px-4 rounded-lg bg-[#1E3A5F] text-white text-xs font-semibold hover:bg-[#182F4D] transition-colors"
                  >
                    Done
                  </button>
                </div>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
};
