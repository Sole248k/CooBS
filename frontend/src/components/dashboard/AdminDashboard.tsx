import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { StaffAccountSummary, BatchRunDetail, DeadLetterRecord } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp } from '../../lib/utils';
import {
  ShieldAlert,
  CalendarClock,
  AlertTriangle,
  FolderX,
  CreditCard,
  ShieldCheck,
  RefreshCw,
  Server,
  Activity,
  ArrowRight,
  PlayCircle,
  RotateCcw,
  CheckCircle2,
  Lock,
  Layers,
  Coins,
  FileCheck2,
  TrendingUp,
  XCircle,
  Clock
} from 'lucide-react';

export const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [accounts, setAccounts] = useState<StaffAccountSummary[]>([]);
  const [queues, setQueues] = useState<{ pendingKyc: number; pendingClosures: number }>({
    pendingKyc: 0,
    pendingClosures: 0,
  });
  const [batchRuns, setBatchRuns] = useState<BatchRunDetail[]>([]);
  const [dlqRecords, setDlqRecords] = useState<DeadLetterRecord[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date());

  const loadAdminMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const [accs, qSummary, runs, dlq] = await Promise.all([
        staffApi.getAllAccounts().catch(() => []),
        staffApi.getQueuesSummary().catch(() => ({ totalPendingKyc: 0, totalPendingClosures: 0 })),
        staffApi.getAllBatchRuns().catch(() => []),
        staffApi.getDlqMessages().catch(() => []),
      ]);

      setAccounts(accs || []);
      setQueues({
        pendingKyc: qSummary.totalPendingKyc || 0,
        pendingClosures: qSummary.totalPendingClosures || 0,
      });
      setBatchRuns(runs || []);
      setDlqRecords(dlq || []);
      setLastRefreshed(new Date());
    } catch (err: any) {
      setError(err.message || 'Failed to load branch management metrics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAdminMetrics();
  }, []);

  // Compute Core Metrics
  const totalAccounts = accounts.length;
  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE').length;
  const frozenAccounts = accounts.filter((a) => a.status === 'FROZEN').length;
  const totalLiquidity = accounts.reduce((acc, curr) => {
    const amt = parseFloat(curr.availableBalance || '0');
    return isNaN(amt) ? acc : acc + amt;
  }, 0);

  const latestBatchRun = batchRuns.length > 0 ? batchRuns[0] : null;
  const totalFlags = accounts.reduce((sum, a) => sum + (a.activeFlags?.length || 0), 0);

  return (
    <div className="space-y-6">
      {/* Top Banner: Supervisory & Governance Hero (Blue-Indigo Gradient) */}
      <div className="bg-gradient-to-r from-[#0F172A] via-[#312E81] to-[#1E1B4B] rounded-2xl p-6 sm:p-7 text-white shadow-lg border border-indigo-800/50 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
        <div className="space-y-2">
          <div className="flex items-center space-x-2 text-indigo-300 text-xs font-semibold uppercase tracking-wider">
            <Server className="w-4 h-4 text-indigo-400" />
            <span>Branch Management &bull; Command Center</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Branch Manager &bull; <span className="text-indigo-300">@{user?.username || 'manager'}</span>
          </h1>
          <p className="text-sm text-indigo-200/90 max-w-xl">
            Branch manager command center. Monitor core service health, authorize dual-control account closures, execute daily EOD batch pipelines, inspect dead letter events, and audit Merkle trees.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <div className="bg-white/10 backdrop-blur-md rounded-xl p-3 border border-white/10 text-xs font-mono space-y-1">
            <div className="flex items-center space-x-1.5 text-indigo-200">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="font-semibold">6/6 Services Online</span>
            </div>
            <div className="text-indigo-300 text-[11px]">
              Last heartbeat: {lastRefreshed.toLocaleTimeString()}
            </div>
          </div>

          <button
            onClick={loadAdminMetrics}
            disabled={loading}
            className="p-3 bg-white/15 hover:bg-white/20 active:scale-95 text-white rounded-xl transition-all border border-white/20 flex items-center justify-center disabled:opacity-50"
            title="Refresh branch metrics"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Row 1: Branch Management KPI Quad */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
        {/* Metric 1: Total Core Ledger Liquidity */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Core Ledger Liquidity
            </span>
            <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-700 flex items-center justify-center">
              <Coins className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-[#1E3A5F] font-mono tracking-tight">
              {formatCurrency(totalLiquidity)}
            </div>
            <div className="mt-1 flex items-center text-xs text-emerald-600 font-medium">
              <TrendingUp className="w-3.5 h-3.5 mr-1" />
              <span>Real-time Oracle Ledger balance</span>
            </div>
          </div>
        </div>

        {/* Metric 2: Maker-Checker Closure Sign-offs */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Closure Sign-Offs
            </span>
            <div className={`w-9 h-9 rounded-xl flex items-center justify-center ${
              queues.pendingClosures > 0 ? 'bg-amber-50 text-amber-600' : 'bg-emerald-50 text-emerald-600'
            }`}>
              <FolderX className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-[#1E3A5F] font-mono tracking-tight">
              {queues.pendingClosures}
            </div>
            <div className="mt-1 flex items-center text-xs text-[#64748B]">
              <span className="text-amber-600 font-semibold mr-1">Checker Approval</span>
              <span>required by Branch Manager</span>
            </div>
          </div>
        </div>

        {/* Metric 3: Dead Letter Queue (DLQ) Alerts */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Dead Letter Queue (DLQ)
            </span>
            <div className={`w-9 h-9 rounded-xl flex items-center justify-center ${
              dlqRecords.length > 0 ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'
            }`}>
              <AlertTriangle className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-[#1E3A5F] font-mono tracking-tight">
              {dlqRecords.length}
            </div>
            <div className="mt-1 flex items-center text-xs text-[#64748B]">
              <span>{dlqRecords.length === 0 ? 'All Kafka topics healthy' : 'Messages require replay'}</span>
            </div>
          </div>
        </div>

        {/* Metric 4: Compliance Holds & Flags */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Compliance Holds
            </span>
            <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <Lock className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-[#1E3A5F] font-mono tracking-tight">
              {totalFlags}
            </div>
            <div className="mt-1 flex items-center text-xs text-[#64748B]">
              <span className="text-indigo-600 font-semibold mr-1">{frozenAccounts} Frozen</span>
              &bull;
              <span className="ml-1">AML flags active</span>
            </div>
          </div>
        </div>
      </div>

      {/* Row 2: End of Day Batch Status Widget (Admin Core Responsibility) */}
      <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#E2E8F0] pb-3">
          <div className="flex items-center space-x-2.5">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <CalendarClock className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#1E3A5F]">End of Day (EOD) Batch Pipeline Status</h2>
              <p className="text-xs text-[#64748B]">
                Daily interest accrual, service fees, standing orders, amortization &amp; audit seal
              </p>
            </div>
          </div>

          <button
            onClick={() => navigate('/admin/eod')}
            className="px-3.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold transition-colors flex items-center space-x-1.5 self-start sm:self-auto shadow-sm"
          >
            <PlayCircle className="w-4 h-4" />
            <span>Manage &amp; Trigger EOD Run</span>
          </button>
        </div>

        {latestBatchRun ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3 bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl p-4">
              <div className="flex items-center space-x-4">
                <div>
                  <span className="text-[11px] text-[#64748B] block font-medium">Business Date</span>
                  <span className="text-sm font-bold text-[#1E3A5F] font-mono">
                    {latestBatchRun.businessDate}
                  </span>
                </div>
                <div className="h-7 w-px bg-[#CBD5E1]" />
                <div>
                  <span className="text-[11px] text-[#64748B] block font-medium">Latest Run ID</span>
                  <span className="text-sm font-bold text-[#1E3A5F] font-mono">
                    #{latestBatchRun.batchRunId}
                  </span>
                </div>
              </div>

              <div className="flex items-center space-x-3">
                <span className="text-xs text-[#64748B]">Pipeline Status:</span>
                <span className={`px-2.5 py-1 rounded-full text-xs font-bold font-mono ${
                  latestBatchRun.status === 'COMPLETED'
                    ? 'bg-emerald-100 text-emerald-800'
                    : latestBatchRun.status === 'RUNNING'
                    ? 'bg-indigo-100 text-indigo-800 animate-pulse'
                    : 'bg-rose-100 text-rose-800'
                }`}>
                  {latestBatchRun.status}
                </span>
              </div>
            </div>

            {/* 6 Step Progress Pills */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2 text-xs">
              {[
                { no: 1, name: 'Interest Accrual' },
                { no: 2, name: 'Service Fees' },
                { no: 3, name: 'Standing Orders' },
                { no: 4, name: 'Amortization' },
                { no: 5, name: 'GL Reconcile' },
                { no: 6, name: 'Audit Seal' },
              ].map((step) => (
                <div
                  key={step.no}
                  className="p-2.5 rounded-lg border border-[#E2E8F0] bg-white flex items-center space-x-2 text-[11px]"
                >
                  <span className="w-5 h-5 rounded-full bg-indigo-50 text-indigo-700 font-bold flex items-center justify-center text-[10px] flex-shrink-0">
                    {step.no}
                  </span>
                  <span className="font-medium text-[#1E3A5F] truncate">{step.name}</span>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="p-4 bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl text-center text-xs text-[#64748B]">
            No batch runs executed today. Navigate to the pipeline to initiate end-of-day operations.
          </div>
        )}
      </div>

      {/* Row 3: Admin Quick Workspaces Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Module 1: Dual-Control Queues */}
        <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div className="flex items-center space-x-2">
              <FolderX className="w-5 h-5 text-amber-600" />
              <h3 className="font-bold text-sm text-[#1E3A5F]">Dual-Control Approvals</h3>
            </div>
            <button
              onClick={() => navigate('/queues')}
              className="text-xs text-amber-600 font-semibold hover:underline flex items-center space-x-1"
            >
              <span>Review</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
          <p className="text-xs text-[#64748B]">
            Review pending account closure submissions. Branch Manager dual-control approval permanently closes accounts with zero balance.
          </p>
          <div className="bg-amber-50/50 border border-amber-200/60 rounded-xl p-3.5 flex items-center justify-between text-xs">
            <span className="font-semibold text-amber-900">
              {queues.pendingClosures} Pending Closures
            </span>
            <button
              onClick={() => navigate('/queues')}
              className="px-3 py-1 bg-amber-600 text-white rounded-lg font-semibold hover:bg-amber-700 transition-colors"
            >
              Authorize
            </button>
          </div>
        </div>

        {/* Module 2: Dead Letter Queue (DLQ) */}
        <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div className="flex items-center space-x-2">
              <AlertTriangle className="w-5 h-5 text-rose-600" />
              <h3 className="font-bold text-sm text-[#1E3A5F]">Dead Letter Replay</h3>
            </div>
            <button
              onClick={() => navigate('/admin/dlq')}
              className="text-xs text-rose-600 font-semibold hover:underline flex items-center space-x-1"
            >
              <span>Inspect</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
          <p className="text-xs text-[#64748B]">
            Kafka messaging dead-letter records. Inspect failed consumer events, error traces, and execute manual message replays.
          </p>
          <div className="bg-rose-50/50 border border-rose-200/60 rounded-xl p-3.5 flex items-center justify-between text-xs">
            <span className="font-semibold text-rose-900">
              {dlqRecords.length} Failed Events
            </span>
            <button
              onClick={() => navigate('/admin/dlq')}
              className="px-3 py-1 bg-rose-600 text-white rounded-lg font-semibold hover:bg-rose-700 transition-colors"
            >
              Open DLQ
            </button>
          </div>
        </div>

        {/* Module 3: Merkle Forensic Audit */}
        <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div className="flex items-center space-x-2">
              <ShieldAlert className="w-5 h-5 text-indigo-600" />
              <h3 className="font-bold text-sm text-[#1E3A5F]">Merkle Audit Integrity</h3>
            </div>
            <button
              onClick={() => navigate('/audit')}
              className="text-xs text-indigo-600 font-semibold hover:underline flex items-center space-x-1"
            >
              <span>Audit</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
          <p className="text-xs text-[#64748B]">
            Cryptographic SHA-256 hash chains across general ledger entries. Inspect daily seals and detect tampering.
          </p>
          <div className="bg-indigo-50/50 border border-indigo-200/60 rounded-xl p-3.5 flex items-center justify-between text-xs">
            <span className="font-semibold text-indigo-900 flex items-center space-x-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              <span>Chain Intact</span>
            </span>
            <button
              onClick={() => navigate('/audit')}
              className="px-3 py-1 bg-indigo-700 text-white rounded-lg font-semibold hover:bg-indigo-800 transition-colors"
            >
              Verify Seals
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
