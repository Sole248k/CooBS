import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { StaffAccountSummary } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp } from '../../lib/utils';
import {
  Coins,
  ArrowDownCircle,
  ArrowUpCircle,
  Send,
  Users,
  CreditCard,
  CheckSquare,
  RefreshCw,
  Building2,
  Clock,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
  Calculator,
  ArrowRight,
  Printer,
  Sparkles,
  FileCheck2,
  X
} from 'lucide-react';

export const TellerDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [accounts, setAccounts] = useState<StaffAccountSummary[]>([]);
  const [queues, setQueues] = useState<{ pendingKyc: number; pendingClosures: number }>({
    pendingKyc: 0,
    pendingClosures: 0,
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date());

  // Till & Cash Drawer Simulation State
  const [openingFloat] = useState<number>(50000.00);
  const [cashInToday, setCashInToday] = useState<number>(18500.00);
  const [cashOutToday, setCashOutToday] = useState<number>(6200.00);
  const [tillProofModalOpen, setTillProofModalOpen] = useState<boolean>(false);
  const [physicalCount, setPhysicalCount] = useState<string>('62300.00');
  const [proofVerified, setProofVerified] = useState<boolean>(false);

  const currentTillBalance = openingFloat + cashInToday - cashOutToday;
  const maxTillLimit = 250000.00;
  const tillFillPercentage = Math.min(100, Math.round((currentTillBalance / maxTillLimit) * 100));

  const loadTellerData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [accs, qSummary] = await Promise.all([
        staffApi.getAllAccounts().catch(() => []),
        staffApi.getQueuesSummary().catch(() => ({ totalPendingKyc: 0, totalPendingClosures: 0 })),
      ]);

      setAccounts(accs || []);
      setQueues({
        pendingKyc: qSummary.totalPendingKyc || 0,
        pendingClosures: qSummary.totalPendingClosures || 0,
      });
      setLastRefreshed(new Date());
    } catch (err: any) {
      setError(err.message || 'Failed to load teller operational metrics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTellerData();
  }, []);

  const handleVerifyTillProof = () => {
    const entered = parseFloat(physicalCount);
    if (!isNaN(entered) && Math.abs(entered - currentTillBalance) < 0.01) {
      setProofVerified(true);
    } else {
      setProofVerified(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Banner: Teller Terminal Session Context (Blue Gradient) */}
      <div className="bg-gradient-to-r from-[#1E40AF] via-[#2563EB] to-[#1D4ED8] rounded-2xl p-6 sm:p-7 text-white shadow-lg border border-blue-700/40 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
        <div className="space-y-2">
          <div className="flex items-center space-x-2 text-blue-200 text-xs font-semibold uppercase tracking-wider">
            <Building2 className="w-4 h-4 text-blue-300" />
            <span>{user?.branchCode || 'BR-0104 MAKATI'} &bull; TELLER WORKSTATION #1</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Branch Operations &bull; <span className="text-blue-200">@{user?.username || 'teller'}</span>
          </h1>
          <p className="text-sm text-blue-100 max-w-xl">
            Front-line counter teller terminal. Manage physical cash drawer float, process deposits, withdrawals, assisted member transfers, and customer account inquiries.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <div className="bg-black/15 backdrop-blur-md rounded-xl p-3 border border-white/10 text-xs font-mono space-y-1">
            <div className="flex items-center space-x-1.5 text-blue-200">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="font-semibold">Shift: Active (Morning)</span>
            </div>
            <div className="text-blue-100 text-[11px]">
              Last synced: {lastRefreshed.toLocaleTimeString()}
            </div>
          </div>

          <button
            onClick={loadTellerData}
            disabled={loading}
            className="p-3 bg-white/15 hover:bg-white/20 active:scale-95 text-white rounded-xl transition-all border border-white/20 flex items-center justify-center disabled:opacity-50"
            title="Refresh teller terminal"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Row 1: Till Float & Cash Drawer Management (Essential Teller Feature) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Till Balance & Drawer Limits Card */}
        <div className="lg:col-span-2 bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-5">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div className="flex items-center space-x-2.5">
              <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-700 flex items-center justify-center">
                <Coins className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-[#1E3A5F]">Active Cash Drawer / Till Float</h2>
                <p className="text-xs text-[#64748B]">Real-time physical currency accounting for active teller session</p>
              </div>
            </div>

            <button
              onClick={() => {
                setPhysicalCount(currentTillBalance.toFixed(2));
                setProofVerified(false);
                setTillProofModalOpen(true);
              }}
              className="px-3.5 py-1.5 rounded-lg bg-blue-50 border border-blue-200 text-blue-800 text-xs font-bold hover:bg-blue-100 transition-colors flex items-center space-x-1.5"
            >
              <Calculator className="w-3.5 h-3.5" />
              <span>Balance Till / Shift Proof</span>
            </button>
          </div>

          {/* 3 Float Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl p-4">
              <span className="text-[11px] font-bold text-[#64748B] uppercase tracking-wider block">
                Starting Float (SOD)
              </span>
              <div className="text-xl font-bold text-[#1E3A5F] font-mono mt-1">
                {formatCurrency(openingFloat)}
              </div>
              <span className="text-[11px] text-[#64748B] mt-0.5 block">Vault float allocated</span>
            </div>

            <div className="bg-emerald-50/60 border border-emerald-100 rounded-xl p-4">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-emerald-800 uppercase tracking-wider">
                  Cash In (Deposits)
                </span>
                <ArrowDownCircle className="w-4 h-4 text-emerald-600" />
              </div>
              <div className="text-xl font-bold text-emerald-700 font-mono mt-1">
                +{formatCurrency(cashInToday)}
              </div>
              <span className="text-[11px] text-emerald-600 mt-0.5 block">Total counter receipts</span>
            </div>

            <div className="bg-rose-50/60 border border-rose-100 rounded-xl p-4">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-rose-800 uppercase tracking-wider">
                  Cash Out (Disbursed)
                </span>
                <ArrowUpCircle className="w-4 h-4 text-rose-600" />
              </div>
              <div className="text-xl font-bold text-rose-700 font-mono mt-1">
                -{formatCurrency(cashOutToday)}
              </div>
              <span className="text-[11px] text-rose-600 mt-0.5 block">Total counter withdrawals</span>
            </div>
          </div>

          {/* Current Till Progress against Drawer Limit */}
          <div className="space-y-2 pt-1">
            <div className="flex items-center justify-between text-xs font-semibold">
              <span className="text-[#1E3A5F] flex items-center space-x-1.5">
                <span>Current Till Cash on Hand:</span>
                <strong className="text-blue-700 font-mono text-sm">{formatCurrency(currentTillBalance)}</strong>
              </span>
              <span className="text-[#64748B]">
                Drawer Capacity: <span className="font-mono">{tillFillPercentage}%</span> (Max: {formatCurrency(maxTillLimit)})
              </span>
            </div>
            <div className="w-full bg-[#E2E8F0] h-2.5 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  tillFillPercentage > 85 ? 'bg-amber-500' : 'bg-blue-600'
                }`}
                style={{ width: `${tillFillPercentage}%` }}
              />
            </div>
            <div className="flex items-center justify-between text-[11px] text-[#64748B]">
              <span>Status: <strong className="text-emerald-600">Within Threshold</strong></span>
              <span>Vault drop required if exceeding ₱250,000.00</span>
            </div>
          </div>
        </div>

        {/* Teller Quick Stats & Shift Proof Card */}
        <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-[#E2E8F0]">
              <span className="text-xs font-bold text-[#64748B] uppercase tracking-wider">Daily Teller Proof</span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 flex items-center space-x-1">
                <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                <span>BALANCED</span>
              </span>
            </div>

            <div className="mt-4 space-y-3 text-xs">
              <div className="flex items-center justify-between py-1 border-b border-[#F1F5F9]">
                <span className="text-[#64748B]">Postings Executed Today:</span>
                <span className="font-mono font-bold text-[#1E3A5F]">14 Postings</span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-[#F1F5F9]">
                <span className="text-[#64748B]">Assisted Transfers Sent:</span>
                <span className="font-mono font-bold text-[#1E3A5F]">3 Transfers</span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-[#F1F5F9]">
                <span className="text-[#64748B]">Pending KYC Updates:</span>
                <span className="font-mono font-bold text-amber-600">{queues.pendingKyc} Members</span>
              </div>
              <div className="flex items-center justify-between py-1">
                <span className="text-[#64748B]">Cash Discrepancy (Variance):</span>
                <span className="font-mono font-bold text-emerald-600">₱0.00</span>
              </div>
            </div>
          </div>

          <div className="p-3 bg-blue-50/70 rounded-xl border border-blue-100 text-[11px] text-blue-800 space-y-1">
            <span className="font-bold block flex items-center space-x-1">
              <ShieldCheck className="w-3.5 h-3.5 text-blue-600" />
              <span>Audit Logging Active</span>
            </span>
            <p className="text-blue-700 leading-relaxed">
              Every cash posting is cryptographically signed and tracked with teller ID &amp; correlation headers.
            </p>
          </div>
        </div>
      </div>

      {/* Row 2: Teller Rapid Action Launchpad */}
      <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
        <div className="border-b border-[#E2E8F0] pb-3">
          <h2 className="text-base font-bold text-[#1E3A5F]">Branch Counter Launchpad</h2>
          <p className="text-xs text-[#64748B]">One-click navigation for immediate customer counter transactions</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 pt-1">
          {/* Action 1: Cash Deposit */}
          <button
            onClick={() => navigate('/counter')}
            className="group p-4 rounded-xl border border-emerald-100 bg-emerald-50/30 hover:bg-emerald-50 hover:border-emerald-300 transition-all text-left flex items-start space-x-3.5"
          >
            <div className="w-10 h-10 rounded-lg bg-emerald-100 text-emerald-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
              <ArrowDownCircle className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm text-[#1E3A5F] group-hover:text-emerald-800">
                  Cash Deposit
                </span>
                <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-all" />
              </div>
              <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                Take counter cash deposit &amp; credit ledger
              </p>
            </div>
          </button>

          {/* Action 2: Cash Withdrawal */}
          <button
            onClick={() => navigate('/counter')}
            className="group p-4 rounded-xl border border-rose-100 bg-rose-50/30 hover:bg-rose-50 hover:border-rose-300 transition-all text-left flex items-start space-x-3.5"
          >
            <div className="w-10 h-10 rounded-lg bg-rose-100 text-rose-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
              <ArrowUpCircle className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm text-[#1E3A5F] group-hover:text-rose-800">
                  Cash Withdrawal
                </span>
                <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-rose-600 group-hover:translate-x-0.5 transition-all" />
              </div>
              <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                Disburse cash &amp; debit member account
              </p>
            </div>
          </button>

          {/* Action 3: Assisted Transfer (Teller Exclusive) */}
          <button
            onClick={() => navigate('/counter')}
            className="group p-4 rounded-xl border border-blue-100 bg-blue-50/30 hover:bg-blue-50 hover:border-blue-300 transition-all text-left flex items-start space-x-3.5"
          >
            <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
              <Send className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm text-[#1E3A5F] group-hover:text-blue-800">
                  Assisted Transfer
                </span>
                <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-blue-600 group-hover:translate-x-0.5 transition-all" />
              </div>
              <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                Teller-authorized OTC inter-account transfer
              </p>
            </div>
          </button>

          {/* Action 4: Open New Account */}
          <button
            onClick={() => navigate('/accounts?openCustomer=true')}
            className="group p-4 rounded-xl border border-indigo-100 bg-indigo-50/30 hover:bg-indigo-50 hover:border-indigo-300 transition-all text-left flex items-start space-x-3.5"
          >
            <div className="w-10 h-10 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
              <CreditCard className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm text-[#1E3A5F] group-hover:text-indigo-800">
                  Originate Account
                </span>
                <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-indigo-600 group-hover:translate-x-0.5 transition-all" />
              </div>
              <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                Open savings, checking, or loan accounts
              </p>
            </div>
          </button>
        </div>
      </div>

      {/* Row 3: Customer Service & Verification Queues Snapshot */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Customer 360 Inquiries */}
        <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div className="flex items-center space-x-2">
              <Users className="w-5 h-5 text-blue-600" />
              <h3 className="font-bold text-sm text-[#1E3A5F]">Member 360 &amp; Verification</h3>
            </div>
            <button
              onClick={() => navigate('/customer-360')}
              className="text-xs text-blue-600 font-semibold hover:underline flex items-center space-x-1"
            >
              <span>Full Lookup</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <p className="text-xs text-[#64748B]">
            Inquire member balances, loan schedules, risk ratings, and credit scores before counter processing.
          </p>

          <div className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl p-4 flex items-center justify-between">
            <div className="space-y-1">
              <span className="text-xs font-bold text-[#1E3A5F]">Quick Customer Lookup</span>
              <p className="text-[11px] text-[#64748B]">Search by Member ID, username, or account number</p>
            </div>
            <button
              onClick={() => navigate('/customer-360')}
              className="px-3.5 py-1.5 bg-[#1E3A5F] text-white rounded-lg text-xs font-semibold hover:bg-[#264A73] transition-colors"
            >
              Inquire Profile
            </button>
          </div>
        </div>

        {/* KYC Queue for Tellers */}
        <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div className="flex items-center space-x-2">
              <CheckSquare className="w-5 h-5 text-amber-600" />
              <h3 className="font-bold text-sm text-[#1E3A5F]">KYC Profile Verification Queue</h3>
            </div>
            <button
              onClick={() => navigate('/queues')}
              className="text-xs text-amber-600 font-semibold hover:underline flex items-center space-x-1"
            >
              <span>View All ({queues.pendingKyc})</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <p className="text-xs text-[#64748B]">
            Branch tellers may review and approve customer address, occupation, and contact updates submitted via mobile.
          </p>

          <div className="bg-amber-50/60 border border-amber-200/60 rounded-xl p-4 flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-amber-900 block">
                {queues.pendingKyc} Pending KYC Submissions
              </span>
              <span className="text-[11px] text-amber-700">Awaiting teller identity document verification</span>
            </div>
            <button
              onClick={() => navigate('/queues')}
              className="px-3.5 py-1.5 bg-amber-600 text-white rounded-lg text-xs font-semibold hover:bg-amber-700 transition-colors"
            >
              Review Submissions
            </button>
          </div>
        </div>
      </div>

      {/* Till Proof Modal */}
      {tillProofModalOpen && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-5 animate-in fade-in duration-200">
            <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
              <div className="flex items-center space-x-2 text-blue-800">
                <Calculator className="w-5 h-5 text-blue-600" />
                <h3 className="text-base font-bold text-[#1E3A5F]">Shift Cash Proof &amp; Balancing</h3>
              </div>
              <button
                onClick={() => setTillProofModalOpen(false)}
                className="p-1 hover:bg-[#F1F5F9] rounded-lg text-[#64748B]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl p-3 space-y-2">
                <div className="flex justify-between text-[#64748B]">
                  <span>Morning Float Allocated:</span>
                  <span className="font-mono font-bold text-[#1E3A5F]">{formatCurrency(openingFloat)}</span>
                </div>
                <div className="flex justify-between text-emerald-700">
                  <span>+ Total Counter Deposits:</span>
                  <span className="font-mono font-bold">{formatCurrency(cashInToday)}</span>
                </div>
                <div className="flex justify-between text-rose-700">
                  <span>- Total Counter Withdrawals:</span>
                  <span className="font-mono font-bold">{formatCurrency(cashOutToday)}</span>
                </div>
                <div className="pt-2 border-t border-[#E2E8F0] flex justify-between font-bold text-sm text-[#1E3A5F]">
                  <span>System Expected Till Total:</span>
                  <span className="font-mono text-blue-700">{formatCurrency(currentTillBalance)}</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-[#1E3A5F] mb-1">
                  Physical Cash Counted in Drawer (₱):
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={physicalCount}
                  onChange={(e) => {
                    setPhysicalCount(e.target.value);
                    setProofVerified(false);
                  }}
                  className="w-full px-3 py-2 border border-[#CBD5E1] rounded-lg font-mono font-bold text-sm focus:outline-none focus:border-blue-600"
                />
              </div>

              {proofVerified ? (
                <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs flex items-center space-x-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                  <span className="font-semibold">Shift balanced! Zero variance detected. You are cleared for handover.</span>
                </div>
              ) : (
                <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-700 text-xs">
                  Enter physical drawer cash count to verify against ledger audit records.
                </div>
              )}
            </div>

            <div className="flex space-x-3 pt-2">
              <button
                type="button"
                onClick={handleVerifyTillProof}
                className="flex-1 py-2.5 px-4 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl transition-colors shadow-sm"
              >
                Verify Reconciliation
              </button>
              <button
                type="button"
                onClick={() => setTillProofModalOpen(false)}
                className="py-2.5 px-4 bg-[#F1F5F9] hover:bg-[#E2E8F0] text-[#1E3A5F] font-bold text-xs rounded-xl transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
