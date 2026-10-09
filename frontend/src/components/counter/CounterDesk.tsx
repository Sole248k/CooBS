import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
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
  RotateCcw,
  ShieldAlert,
} from 'lucide-react';

type CounterTab = 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER';

export const CounterDesk: React.FC = () => {
  const { isTeller, isAdmin } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [activeTab, setActiveTab] = useState<CounterTab>('DEPOSIT');

  // Form State
  const [accountId, setAccountId] = useState(searchParams.get('accountId') || '1');
  const [destinationAccount, setDestinationAccount] = useState('ACC_10000002');
  const [amount, setAmount] = useState('1000.00');
  const [remarks, setRemarks] = useState('Counter posting transaction');
  const [category, setCategory] = useState('TRANSFER');

  // Execution State
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receiptData, setReceiptData] = useState<any | null>(null);

  // If Admin attempts to select assisted transfer, revert to Deposit
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
    setLoading(true);

    const parsedAccountId = parseInt(accountId, 10);
    const parsedAmount = parseFloat(amount);

    if (isNaN(parsedAccountId) || parsedAccountId <= 0) {
      setError('Please provide a valid numeric account ID.');
      setLoading(false);
      return;
    }

    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Amount must be greater than ₱0.00.');
      setLoading(false);
      return;
    }

    // One key and one reference per operation, not per click. If the answer is lost after the
    // ledger has posted, pressing the button again sends the same two and the ledger answers
    // from what it already did; a new pair would post the cash a second time.
    const operation = `${activeTab}|${parsedAccountId}|${parsedAmount.toFixed(2)}|${destinationAccount}`;
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
            destinationAccountNumber: destinationAccount,
            destinationBankCode: 'COOBS',
            amount: parsedAmount.toFixed(2),
            currency: 'PHP',
            category,
            remarks,
          },
          idempotencyKey
        );
      }

      setReceiptData({
        type: activeTab,
        accountId: parsedAccountId,
        destinationAccount: activeTab === 'TRANSFER' ? destinationAccount : null,
        amount: parsedAmount.toFixed(2),
        referenceNo: result?.referenceNo || result?.transactionReference || referenceNo,
        transactionId: result?.transactionId || generateUUID(),
        timestamp: new Date().toISOString(),
        newBalance: result?.newBalance ?? result?.balance ?? null,
      });
      pending.current = null;
    } catch (err: any) {
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
          {/* Account Details */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                {activeTab === 'TRANSFER' ? 'Source Account ID' : 'Target Account ID'}
              </label>
              <input
                type="number"
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                required
                className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                placeholder="e.g. 1"
              />
              <span className="text-[11px] text-[#64748B] mt-1 block">
                Internal Account primary ID key
              </span>
            </div>

            {activeTab === 'TRANSFER' && (
              <div>
                <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
                  Destination Account Number
                </label>
                <input
                  type="text"
                  value={destinationAccount}
                  onChange={(e) => setDestinationAccount(e.target.value)}
                  required
                  className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                  placeholder="e.g. ACC_10000002"
                />
                <span className="text-[11px] text-[#64748B] mt-1 block">
                  Target destination account string
                </span>
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
            {loading ? (
              <span className="inline-flex items-center space-x-2">
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                <span>Posting to Ledger...</span>
              </span>
            ) : (
              <>
                <Coins className="w-4 h-4" />
                <span>
                  Confirm &amp; Post {activeTab === 'DEPOSIT' ? 'Deposit' : activeTab === 'WITHDRAW' ? 'Withdrawal' : 'Transfer'} (₱{parseFloat(amount || '0').toLocaleString('en-PH', { minimumFractionDigits: 2 })})
                </span>
              </>
            )}
          </button>
        </form>
      </div>

      {/* Transaction Success Receipt Modal */}
      {receiptData && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-6 animate-in fade-in zoom-in-95 duration-150">
            {/* Header */}
            <div className="text-center space-y-1">
              <div className="w-12 h-12 rounded-full bg-[#D1FAE5] text-[#10B981] mx-auto flex items-center justify-center mb-2">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-[#1E3A5F]">Transaction Complete</h3>
              <p className="text-xs text-[#64748B]">Official Teller Posting Slip</p>
            </div>

            {/* Receipt Summary */}
            <div className="p-4 rounded-xl bg-[#F8FAFC] border border-[#E2E8F0] space-y-3 font-mono text-xs">
              <div className="flex justify-between">
                <span className="text-[#64748B]">Reference No:</span>
                <span className="font-bold text-[#1E3A5F]">{receiptData.referenceNo}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#64748B]">Operation:</span>
                <span className="font-bold text-[#1E3A5F]">{receiptData.type}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#64748B]">Account:</span>
                <span className="font-bold text-[#1E3A5F]">ACC_ID #{receiptData.accountId}</span>
              </div>
              {receiptData.destinationAccount && (
                <div className="flex justify-between">
                  <span className="text-[#64748B]">Destination:</span>
                  <span className="font-bold text-[#1E3A5F]">{receiptData.destinationAccount}</span>
                </div>
              )}
              <div className="flex justify-between text-sm pt-2 border-t border-[#E2E8F0]">
                <span className="text-[#64748B] font-sans font-semibold">Amount Posted:</span>
                <span className="font-bold text-[#10B981] tabular-nums">
                  {formatCurrency(receiptData.amount)}
                </span>
              </div>
              {receiptData.newBalance !== null && (
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

            {/* QR Mockup */}
            <div className="flex items-center justify-center space-x-2 text-xs text-[#64748B]">
              <QrCode className="w-5 h-5 text-[#1E3A5F]" />
              <span>Digital Verification Proof Anchored</span>
            </div>

            {/* Actions */}
            <div className="space-y-2">
              {receiptData.type === 'TRANSFER' && (
                <button
                  onClick={() => navigate(`/tracker?ref=${receiptData.referenceNo}`)}
                  className="w-full py-2.5 px-4 rounded-lg bg-[#EFF6FF] text-[#2563EB] text-xs font-bold hover:bg-[#DBEAFE] transition-colors flex items-center justify-center space-x-1.5"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  <span>View 11-Stage Pipeline in Tracker</span>
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
      )}
    </div>
  );
};
