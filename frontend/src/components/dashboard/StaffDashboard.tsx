import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { StaffAccountSummary } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp, getStatusClass } from '../../lib/utils';
import {
  LayoutDashboard,
  Users,
  Coins,
  CheckSquare,
  CreditCard,
  GitBranch,
  ShieldAlert,
  CalendarClock,
  AlertTriangle,
  ArrowRight,
  TrendingUp,
  Activity,
  Building2,
  RefreshCw,
  Clock,
  AlertCircle,
  FileCheck
} from 'lucide-react';

export const StaffDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { user, role, isAdmin } = useAuth();

  const [accounts, setAccounts] = useState<StaffAccountSummary[]>([]);
  const [queues, setQueues] = useState<{ pendingKyc: number; pendingClosures: number }>({
    pendingKyc: 0,
    pendingClosures: 0,
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date());

  const loadDashboardData = async () => {
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
      setError(err.message || 'Failed to load operational metrics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboardData();
  }, []);

  // Compute metrics
  const totalAccounts = accounts.length;
  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE').length;
  const frozenAccounts = accounts.filter((a) => a.status === 'FROZEN').length;
  const totalLiquidity = accounts.reduce((acc, curr) => {
    const amt = parseFloat(curr.availableBalance || '0');
    return isNaN(amt) ? acc : acc + amt;
  }, 0);

  const totalPendingAction = queues.pendingKyc + queues.pendingClosures;

  return (
    <div className="space-y-6">
      {/* Top Banner / Hero Greeting */}
      <div className="bg-gradient-to-r from-[#1E3A5F] via-[#264A73] to-[#1E3A5F] rounded-2xl p-6 sm:p-8 text-white shadow-lg border border-blue-900/30 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
        <div className="space-y-2">
          <div className="flex items-center space-x-2 text-blue-200 text-xs font-semibold uppercase tracking-wider">
            <Building2 className="w-4 h-4 text-blue-400" />
            <span>{user?.branchCode || 'BR-0104 MAKATI'} &bull; Branch Terminal</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Welcome back, <span className="text-blue-300">@{user?.username || 'staff'}</span>
          </h1>
          <p className="text-sm text-slate-300 max-w-xl">
            {isAdmin
              ? 'Supervisory & Administration Console. Monitor system-wide queues, manage EOD batches, unflag accounts, and inspect forensic audit chains.'
              : 'Branch Operations Terminal. Execute counter deposits, withdrawals, assisted customer transfers, and process membership inquiries.'}
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <div className="bg-white/10 backdrop-blur-md rounded-xl p-3 border border-white/10 text-xs font-mono space-y-1">
            <div className="flex items-center space-x-1.5 text-emerald-300">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="font-semibold">Core Gateway: Online</span>
            </div>
            <div className="text-slate-300 text-[11px]">
              Last updated: {lastRefreshed.toLocaleTimeString()}
            </div>
          </div>

          <button
            onClick={loadDashboardData}
            disabled={loading}
            className="p-3 bg-white/15 hover:bg-white/20 active:scale-95 text-white rounded-xl transition-all border border-white/20 flex items-center justify-center disabled:opacity-50"
            title="Refresh metrics"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* KPI Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
        {/* Metric 1: Total Core Liquidity */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Core Ledger Liquidity
            </span>
            <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
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

        {/* Metric 2: Core Accounts */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Managed Accounts
            </span>
            <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <CreditCard className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-[#1E3A5F] font-mono tracking-tight">
              {totalAccounts}
            </div>
            <div className="mt-1 flex items-center text-xs text-[#64748B]">
              <span className="text-emerald-600 font-semibold mr-1">{activeAccounts} Active</span>
              &bull;
              <span className="text-amber-600 font-semibold ml-1 mr-1">{frozenAccounts} Frozen</span>
            </div>
          </div>
        </div>

        {/* Metric 3: Pending Queues */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Action Queues
            </span>
            <div className={`w-9 h-9 rounded-xl flex items-center justify-center ${
              totalPendingAction > 0 ? 'bg-amber-50 text-amber-600' : 'bg-emerald-50 text-emerald-600'
            }`}>
              <CheckSquare className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-[#1E3A5F] font-mono tracking-tight">
              {totalPendingAction}
            </div>
            <div className="mt-1 flex items-center text-xs text-[#64748B]">
              <span>{queues.pendingKyc} KYC requests</span>
              <span className="mx-1">&bull;</span>
              <span>{queues.pendingClosures} closures</span>
            </div>
          </div>
        </div>

        {/* Metric 4: System Security & Integrity */}
        <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] shadow-sm hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-[#64748B] uppercase tracking-wider">
              Forensic Integrity
            </span>
            <div className="w-9 h-9 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center">
              <ShieldAlert className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold text-emerald-600 tracking-tight flex items-center">
              <span>SECURE</span>
            </div>
            <div className="mt-1 flex items-center text-xs text-[#64748B]">
              <FileCheck className="w-3.5 h-3.5 text-emerald-600 mr-1" />
              <span>SHA-256 Hash chain intact</span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Grid: Quick Navigation Hub + Queue Snapshot */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: Operation Workspaces Hub */}
        <div className="lg:col-span-2 bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
            <div>
              <h2 className="text-lg font-bold text-[#1E3A5F] tracking-tight">
                Staff Operations Hub
              </h2>
              <p className="text-xs text-[#64748B]">
                Direct workspace navigation for branch tellers and back-office supervisors
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 pt-1">
            {/* Action 1: Customer 360 */}
            <button
              onClick={() => navigate('/customer-360')}
              className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-blue-50/50 hover:border-blue-300 transition-all text-left flex items-start space-x-3.5"
            >
              <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                <Users className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-blue-700">
                    Customer 360
                  </span>
                  <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-blue-600 group-hover:translate-x-0.5 transition-all" />
                </div>
                <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                  Inquire full profile, credit scores, active holdings & spending
                </p>
              </div>
            </button>

            {/* Action 2: Counter Desk */}
            <button
              onClick={() => navigate('/counter')}
              className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-emerald-50/50 hover:border-emerald-300 transition-all text-left flex items-start space-x-3.5"
            >
              <div className="w-10 h-10 rounded-lg bg-emerald-100 text-emerald-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                <Coins className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-emerald-700">
                    Counter Desk
                  </span>
                  <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-all" />
                </div>
                <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                  Process cash deposits, counter withdrawals & assisted transfers
                </p>
              </div>
            </button>

            {/* Action 3: Queues */}
            <button
              onClick={() => navigate('/queues')}
              className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-amber-50/50 hover:border-amber-300 transition-all text-left flex items-start space-x-3.5"
            >
              <div className="w-10 h-10 rounded-lg bg-amber-100 text-amber-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                <CheckSquare className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-amber-700">
                    Review Queues
                  </span>
                  <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-amber-600 group-hover:translate-x-0.5 transition-all" />
                </div>
                <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                  Approve/reject KYC updates and account closure requests
                </p>
              </div>
            </button>

            {/* Action 4: Accounts Directory */}
            <button
              onClick={() => navigate('/accounts')}
              className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-indigo-50/50 hover:border-indigo-300 transition-all text-left flex items-start space-x-3.5"
            >
              <div className="w-10 h-10 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                <CreditCard className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-indigo-700">
                    Accounts Directory
                  </span>
                  <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-indigo-600 group-hover:translate-x-0.5 transition-all" />
                </div>
                <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                  Open accounts, freeze/activate, and manage security hold flags
                </p>
              </div>
            </button>

            {/* Action 5: Transaction Tracker */}
            <button
              onClick={() => navigate('/tracker')}
              className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-cyan-50/50 hover:border-cyan-300 transition-all text-left flex items-start space-x-3.5"
            >
              <div className="w-10 h-10 rounded-lg bg-cyan-100 text-cyan-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                <GitBranch className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-cyan-700">
                    Tracker & Reversals
                  </span>
                  <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-cyan-600 group-hover:translate-x-0.5 transition-all" />
                </div>
                <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                  Inspect 11-stage Saga pipelines and execute reversals
                </p>
              </div>
            </button>

            {/* Action 6: Merkle Audit Statement */}
            <button
              onClick={() => navigate('/audit')}
              className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-purple-50/50 hover:border-purple-300 transition-all text-left flex items-start space-x-3.5"
            >
              <div className="w-10 h-10 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                <ShieldAlert className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-purple-700">
                    Merkle Audit Trail
                  </span>
                  <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-purple-600 group-hover:translate-x-0.5 transition-all" />
                </div>
                <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                  Verify cryptographic chain integrity and inspect ledger statements
                </p>
              </div>
            </button>

            {/* Admin Extra 1: EOD Batch Pipeline */}
            {isAdmin && (
              <button
                onClick={() => navigate('/admin/eod')}
                className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-orange-50/50 hover:border-orange-300 transition-all text-left flex items-start space-x-3.5"
              >
                <div className="w-10 h-10 rounded-lg bg-orange-100 text-orange-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                  <CalendarClock className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-orange-700">
                      End-of-Day Batch
                    </span>
                    <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-orange-600 group-hover:translate-x-0.5 transition-all" />
                  </div>
                  <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                    Execute interest accrual, fee deductions & seal the day
                  </p>
                </div>
              </button>
            )}

            {/* Admin Extra 2: Dead Letter Queue */}
            {isAdmin && (
              <button
                onClick={() => navigate('/admin/dlq')}
                className="group p-4 rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] hover:bg-red-50/50 hover:border-red-300 transition-all text-left flex items-start space-x-3.5"
              >
                <div className="w-10 h-10 rounded-lg bg-red-100 text-red-600 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-sm text-[#1E3A5F] group-hover:text-red-700">
                      Dead Letter Queue (DLQ)
                    </span>
                    <ArrowRight className="w-4 h-4 text-[#94A3B8] group-hover:text-red-600 group-hover:translate-x-0.5 transition-all" />
                  </div>
                  <p className="text-xs text-[#64748B] mt-0.5 line-clamp-1">
                    Inspect failed event records and trigger dead-letter replays
                  </p>
                </div>
              </button>
            )}
          </div>
        </div>

        {/* Right 1 Col: Branch Summary & System Status */}
        <div className="space-y-4">
          {/* Quick Queue Status Card */}
          <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-4">
            <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
              <h3 className="font-bold text-sm text-[#1E3A5F] flex items-center">
                <CheckSquare className="w-4 h-4 text-blue-600 mr-2" />
                <span>Pending Queue Action</span>
              </h3>
              <button
                onClick={() => navigate('/queues')}
                className="text-xs font-semibold text-blue-600 hover:text-blue-800"
              >
                View all
              </button>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between p-3 rounded-xl bg-[#F8FAFC] border border-[#E2E8F0]">
                <div>
                  <div className="text-xs font-bold text-[#1E3A5F]">KYC Identity Updates</div>
                  <div className="text-[11px] text-[#64748B]">Member details changes</div>
                </div>
                <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                  queues.pendingKyc > 0 ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'
                }`}>
                  {queues.pendingKyc}
                </span>
              </div>

              <div className="flex items-center justify-between p-3 rounded-xl bg-[#F8FAFC] border border-[#E2E8F0]">
                <div>
                  <div className="text-xs font-bold text-[#1E3A5F]">Account Closure Requests</div>
                  <div className="text-[11px] text-[#64748B]">Requires zero balance</div>
                </div>
                <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                  queues.pendingClosures > 0 ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'
                }`}>
                  {queues.pendingClosures}
                </span>
              </div>
            </div>
          </div>

          {/* Core Topology Health Indicator */}
          <div className="bg-white rounded-2xl p-6 border border-[#E2E8F0] shadow-sm space-y-3">
            <h3 className="font-bold text-sm text-[#1E3A5F] flex items-center">
              <Activity className="w-4 h-4 text-emerald-600 mr-2" />
              <span>Core Service Status</span>
            </h3>

            <div className="space-y-2 text-xs">
              <div className="flex items-center justify-between py-1 border-b border-slate-100">
                <span className="text-slate-600">Oracle Database (XEPDB1)</span>
                <span className="text-emerald-600 font-semibold flex items-center">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5" />
                  Connected
                </span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-slate-100">
                <span className="text-slate-600">PostgreSQL (Audit Store)</span>
                <span className="text-emerald-600 font-semibold flex items-center">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5" />
                  Synced
                </span>
              </div>
              <div className="flex items-center justify-between py-1 border-b border-slate-100">
                <span className="text-slate-600">Staff BFF Microservice</span>
                <span className="text-emerald-600 font-semibold flex items-center">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5" />
                  Port 8095
                </span>
              </div>
              <div className="flex items-center justify-between py-1">
                <span className="text-slate-600">Kafka KRaft Broker</span>
                <span className="text-emerald-600 font-semibold flex items-center">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5" />
                  Online
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StaffDashboard;
