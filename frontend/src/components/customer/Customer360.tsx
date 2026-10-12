import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { staffApi } from '../../services/api';
import type { StaffCustomer360Response, StaffAccountSummary } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp, getStatusClass, truncateHash } from '../../lib/utils';
import {
  Search,
  Copy,
  Check,
  AlertCircle,
  Plus,
  ArrowUpRight,
  ShieldAlert,
  Clock,
  Sparkles,
  Building2,
} from 'lucide-react';

// Seed mapping to assist search by name / account / ID
const CUSTOMER_DIRECTORY = [
  { id: 1, name: 'John Doe', username: 'john_doe', accounts: ['ACC_10000001'] },
  { id: 2, name: 'Maria Santos', username: 'maria_santos', accounts: ['ACC_10000002'] },
  { id: 3, name: 'David Kim', username: 'david_kim', accounts: ['ACC_10000003'] },
  { id: 4, name: 'Juan Dela Cruz', username: 'juan_delacruz', accounts: ['ACC_10000004', 'ACC_10000005', 'LN_10000006'] },
  { id: 5, name: 'Andrea Reyes', username: 'andrea_reyes', accounts: ['ACC_10000007'] },
  { id: 6, name: 'Jose Bautista', username: 'jose_bautista', accounts: ['ACC_10000008'] },
  { id: 7, name: 'Liza Mercado', username: 'liza_mercado', accounts: ['ACC_10000009', 'LN_10000010'] },
  { id: 8, name: 'Ramon Villanueva', username: 'ramon_villanueva', accounts: ['ACC_10000011'] },
  { id: 9, name: 'Grace Pangilinan', username: 'grace_pangilinan', accounts: ['ACC_10000012'] },
  { id: 10, name: 'Carmela Garcia', username: 'carmela_garcia', accounts: ['ACC_10000013'] },
];

export const Customer360: React.FC = () => {
  const navigate = useNavigate();

  const [searchQuery, setSearchQuery] = useState('1');
  const [activeFilter, setActiveFilter] = useState<'ALL' | 'PROCESSING' | 'IN_REVIEW' | 'VERIFIED' | 'FLAGGED'>('ALL');
  const [data, setData] = useState<StaffCustomer360Response | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedItem, setCopiedItem] = useState<string | null>(null);
  const [lastSyncTime, setLastSyncTime] = useState<string>('10:45:28');

  // Flag account modal state
  const [showFlagModal, setShowFlagModal] = useState(false);
  const [flagReason, setFlagReason] = useState('');
  const [flaggingAccount, setFlaggingAccount] = useState<StaffAccountSummary | null>(null);
  const [flagSuccess, setFlagSuccess] = useState<string | null>(null);

  const fetchCustomer = async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      const res = await staffApi.getCustomer360(id);

      // Enrich recentTransactions with live timeline status & Risk Engine failure details (e.g. RISK_DECLINED / LIMIT_EXCEEDED)
      if (Array.isArray(res?.recentTransactions) && res.recentTransactions.length > 0) {
        const enrichedTx = await Promise.all(
          res.recentTransactions.map(async (tx: any) => {
            const statusUpper = String(tx.status || '').toUpperCase();
            const lookupId = tx.transactionId || tx.referenceNo || tx.refId;
            if (
              !lookupId ||
              String(lookupId).startsWith('EOD-') ||
              (['POSTED', 'COMPLETED', 'REVERSED'].includes(statusUpper) && tx.failureCode)
            ) {
              return tx;
            }
            if (['FAILED', 'REJECTED', 'PROCESSING', 'INITIATED', 'PENDING'].includes(statusUpper)) {
              try {
                const tl: any = await staffApi.getTransactionTimeline(String(lookupId));
                const stages: any[] = Array.isArray(tl?.stages) ? tl.stages : [];
                const failedStage = stages.find(
                  (s) =>
                    String(s.status || '').toUpperCase() === 'FAILED' ||
                    String(s.outcome || '').toUpperCase() === 'FAILED' ||
                    String(s.outcome || '').toUpperCase() === 'REJECTED'
                );
                const liveStatus = failedStage
                  ? 'FAILED'
                  : String(tl?.status || tl?.overallStatus || tx.status || 'POSTED').toUpperCase();
                const failureCode =
                  tx.failureCode ||
                  tl?.failureMessage ||
                  tl?.transaction?.failureCode ||
                  tl?.failureCode ||
                  failedStage?.stageName ||
                  null;
                return {
                  ...tx,
                  status: liveStatus === 'COMPLETED' ? 'POSTED' : liveStatus,
                  failureCode,
                  failedStageNo: failedStage?.stageNo ?? failedStage?.stageNumber ?? null,
                  failedStageName: failedStage?.stageName ?? failedStage?.stageKey ?? null,
                  failedStageTitle: failedStage?.title ?? null,
                  failedStageDetail: failedStage?.detail ?? tl?.transaction?.failureReason ?? null,
                };
              } catch {
                return tx;
              }
            }
            return tx;
          })
        );
        res.recentTransactions = enrichedTx;
      }

      // Enrich recentTransactions with daily/monthly EOD Interest Accrual credits from account audit statements
      if (res?.accounts && res.accounts.length > 0) {
        const stmtResults = await Promise.allSettled(
          res.accounts.map((acc) => staffApi.getAccountStatement(acc.accountId, 0, 30))
        );

        const eodInterestByAccountDay = new Map<string, any>();
        const reversalsByAccountRef = new Map<string, any>();
        stmtResults.forEach((stmtRes, idx) => {
          if (stmtRes.status !== 'fulfilled') return;
          const acc = res.accounts[idx];
          const rows = stmtRes.value?.content || stmtRes.value || [];
          if (!Array.isArray(rows)) return;

          rows.forEach((row: any) => {
            const refUpper = String(row.referenceNo || '').toUpperCase();
            const typeUpper = String(row.transactionType || row.entryType || '').toUpperCase();
            const amt = Number(row.amount || 0);
            const oldBal = Number(row.oldBalance || 0);

            const isReversal =
              typeUpper === 'REVERSAL_CREDIT' ||
              typeUpper === 'REVERSAL_DEBIT' ||
              refUpper.startsWith('RV-');

            if (isReversal) {
              const rawTs = row.eventTimestamp || row.timestamp || new Date().toISOString();
              const isCredit = typeUpper === 'REVERSAL_CREDIT' || (!typeUpper.includes('DEBIT') && amt > 0);
              const cleanRef = row.referenceNo ? String(row.referenceNo) : `RV-${acc.accountId}`;
              reversalsByAccountRef.set(`${acc.accountId}-${cleanRef.toUpperCase()}`, {
                createdAt: rawTs,
                timestamp: rawTs,
                referenceNo: cleanRef,
                refId: cleanRef,
                counterparty: isCredit
                  ? `Reversal Refund (${acc.accountNumber})`
                  : `Reversal Debit (${acc.accountNumber})`,
                category: 'REVERSAL',
                type: 'Reversal',
                direction: isCredit ? 'IN' : 'OUT',
                amount: isCredit ? `+₱${Math.abs(amt).toFixed(2)}` : `-₱${Math.abs(amt).toFixed(2)}`,
                status: 'POSTED',
              });
              return;
            }

            const dailyExpected = Math.round(((oldBal * 0.02) / 365) * 100) / 100;
            const monthly30Expected = Math.round(((oldBal * 0.02 * 30) / 365) * 100) / 100;
            const monthly31Expected = Math.round(((oldBal * 0.02 * 31) / 365) * 100) / 100;

            const isExplicitInterest = refUpper.includes('-INT-') || typeUpper === 'INTEREST_CREDIT';
            const isMatchedSavingsInterest =
              acc.accountType === 'SAVINGS' &&
              typeUpper === 'CREDIT' &&
              !refUpper.startsWith('TX-') &&
              !refUpper.startsWith('RV-') &&
              !refUpper.startsWith('DEP-') &&
              oldBal >= 1000 &&
              amt > 0 &&
              (Math.abs(amt - dailyExpected) <= 0.02 ||
                Math.abs(amt - monthly30Expected) <= 0.05 ||
                Math.abs(amt - monthly31Expected) <= 0.05);

            if (!isExplicitInterest && !isMatchedSavingsInterest) return;

            const rawTs = row.eventTimestamp || row.timestamp || new Date().toISOString();
            const dateOnly = String(rawTs).slice(0, 10);
            const dateCompact = dateOnly.replace(/-/g, '') || '20261012';
            const dedupeKey = `${acc.accountId}-${dateOnly}`;

            // Keep the latest interest accrual entry per savings account per day
            if (eodInterestByAccountDay.has(dedupeKey)) return;

            const isMonthly =
              Math.abs(amt - monthly30Expected) <= 0.05 ||
              Math.abs(amt - monthly31Expected) <= 0.05 ||
              /^EOD-\d{6}-INT-/i.test(refUpper);

            const cleanRef =
              row.referenceNo
                ? String(row.referenceNo).replace(/-[DC]$/i, '')
                : `EOD-${dateCompact}-INT-${String(acc.accountId).padStart(6, '0')}`;

            eodInterestByAccountDay.set(dedupeKey, {
              createdAt: rawTs,
              timestamp: rawTs,
              referenceNo: cleanRef,
              refId: cleanRef,
              counterparty: isMonthly
                ? `Monthly EOD Interest Capitalization (${acc.accountNumber})`
                : `Daily EOD Interest Accrual (${acc.accountNumber})`,
              type: 'Interest Accrual',
              direction: 'IN',
              amount: `+₱${amt.toFixed(2)}`,
              status: 'POSTED',
            });
          });
        });

        const enrichedEntries = [
          ...Array.from(reversalsByAccountRef.values()),
          ...Array.from(eodInterestByAccountDay.values()),
        ];

        if (enrichedEntries.length > 0) {
          const existingList = Array.isArray(res.recentTransactions) ? res.recentTransactions : [];
          const existingRefs = new Set(
            existingList.map((t: any) => String(t.referenceNo || t.refId || '').toUpperCase())
          );
          const newEntries = enrichedEntries.filter(
            (e) => !existingRefs.has(String(e.referenceNo).toUpperCase())
          );
          res.recentTransactions = [...newEntries, ...existingList].sort((a: any, b: any) => {
            const tA = new Date(a.createdAt || a.timestamp || 0).getTime();
            const tB = new Date(b.createdAt || b.timestamp || 0).getTime();
            return tB - tA;
          });
        }
      }

      setData(res);
      setLastSyncTime(new Date().toLocaleTimeString('en-US', { hour12: false }));
    } catch (err: any) {
      if (err.message?.includes('404') || err.message?.includes('not found')) {
        setError(`No customer found with identifier: ${id}`);
      } else {
        setError(err.message || 'Failed to retrieve customer 360 profile.');
      }
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCustomer(1);
  }, []);

  const handleSearchSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const q = searchQuery.trim().toLowerCase();
    if (!q) return;

    // Check if pure numeric Customer ID (e.g. "1", "22")
    if (/^\d+$/.test(q)) {
      const num = parseInt(q, 10);
      if (!isNaN(num) && num > 0 && num < 10000000) {
        fetchCustomer(num);
        return;
      }
    }

    // Check if user searched by Account Number (e.g. ACC_10000026) or Customer Name in live accounts directory
    try {
      const allAccounts = await staffApi.getAllAccounts();
      if (Array.isArray(allAccounts)) {
        const matchedAcc: any = allAccounts.find(
          (a: any) =>
            String(a.accountNumber || '').toLowerCase() === q ||
            String(a.accountNumber || '').toLowerCase() === `acc_${q}` ||
            String(a.customerName || '').toLowerCase().includes(q)
        );
        if (matchedAcc?.customerId) {
          fetchCustomer(Number(matchedAcc.customerId));
          return;
        }
      }
    } catch {
      // Fallback to static directory below
    }

    // Lookup by name or account number in static directory
    const found = CUSTOMER_DIRECTORY.find(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.username.toLowerCase().includes(q) ||
        c.accounts.some((acc) => acc.toLowerCase().includes(q))
    );

    if (found) {
      fetchCustomer(found.id);
    } else {
      setError(`No customer found matching "${searchQuery}". Please enter a Customer ID (e.g. 1, 22), name, or Account Number (e.g. ACC_10000026).`);
    }
  };

  const copyToClipboard = (text: string, identifier: string) => {
    navigator.clipboard.writeText(text);
    setCopiedItem(identifier);
    setTimeout(() => setCopiedItem(null), 2000);
  };

  const handleFlagAccount = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!flaggingAccount || !flagReason.trim()) return;
    try {
      await staffApi.addAccountFlag(flaggingAccount.accountId, flagReason.trim());
      setFlagSuccess(`Flag successfully placed on ${flaggingAccount.accountNumber}`);
      setShowFlagModal(false);
      setFlagReason('');
      if (data?.customerId) {
        fetchCustomer(data.customerId);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to add security flag on account.');
    }
  };

  // Derive values for 4 metrics
  const totalLedger = data?.accounts?.reduce((sum, acc) => {
    const amt = parseFloat(acc.ledgerBalance?.replace(/[^0-9.-]/g, '') || '0');
    return isNaN(amt) ? sum : sum + amt;
  }, 0) || (data?.totals?.totalDeposits ? parseFloat(data.totals.totalDeposits.replace(/[^0-9.-]/g, '')) : 0);

  const availableTotal = data?.accounts?.reduce((sum, acc) => {
    const amt = parseFloat(acc.availableBalance?.replace(/[^0-9.-]/g, '') || '0');
    return isNaN(amt) ? sum : sum + amt;
  }, 0) || totalLedger;

  const heldTotal = data?.accounts?.reduce((sum, acc) => {
    const amt = parseFloat(acc.heldAmount?.replace(/[^0-9.-]/g, '') || '0');
    return isNaN(amt) ? sum : sum + amt;
  }, 0) || 0;

  // Initials for avatar
  const initials = data?.displayName
    ? data.displayName
        .split(' ')
        .map((n) => n[0])
        .join('')
        .substring(0, 2)
        .toUpperCase()
    : 'ES';

  // Seeded / real beneficiaries fallback
  const beneficiariesList = React.useMemo(() => {
    if (data?.savedBeneficiaries && data.savedBeneficiaries.length > 0) {
      return data.savedBeneficiaries;
    }
    return [
      { name: 'Mia V.', accountNumber: 'ACC_10000428' },
      { name: 'Northstar Retail', accountNumber: 'ACC_10000694' },
      { name: 'Paolo Cruz', accountNumber: 'ACC_10000012' },
    ];
  }, [data?.savedBeneficiaries]);

  // Seeded / real recent transfers fallback
  const recentTransfersList = React.useMemo(() => {
    if (data?.recentTransactions && data.recentTransactions.length > 0) {
      return data.recentTransactions;
    }
    return [
      {
        timestamp: '2026-10-03 10:42',
        refId: 'TX-20261003-8F3A21C0',
        counterparty: 'Maria Villanueva',
        type: 'Transfer out',
        amount: '-₱24,500.00',
        status: 'POSTED',
      },
      {
        timestamp: '2026-10-03 09:18',
        refId: 'TX-20261003-49DC89A2',
        counterparty: 'Meralco',
        type: 'Bills payment',
        amount: '-₱8,420.00',
        status: 'POSTED',
      },
      {
        timestamp: '2026-10-03 08:54',
        refId: 'TX-20261003-B719DCE4',
        counterparty: data?.displayName || 'Eduardo Santos',
        type: 'Cash deposit',
        amount: '+₱50,000.00',
        status: 'PROCESSING',
      },
      {
        timestamp: '2026-10-02 16:21',
        refId: 'TX-20261002-751A8CF8',
        counterparty: 'Northstar Retail',
        type: 'Transfer out',
        amount: '-₱12,000.00',
        status: 'FAILED',
      },
      {
        timestamp: '2026-10-02 13:06',
        refId: 'TX-20261002-7A2E91D5',
        counterparty: 'PLDT Home',
        type: 'Bills payment',
        amount: '-₱2,199.00',
        status: 'POSTED',
      },
    ];
  }, [data?.recentTransactions, data?.displayName]);

  return (
    <div className="space-y-6 select-none">
      {/* Top Breadcrumb & Page Heading */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <div className="text-[11px] font-bold uppercase tracking-wider text-[#94A3B8]">
            Clients &bull; Customer Record
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-[#1E3A5F] tracking-tight">
            CUSTOMER 360
          </h1>
        </div>
        <div className="flex items-center space-x-2 text-xs font-mono font-semibold text-[#64748B]">
          <span>BUSINESS DATE</span>
          <span className="px-2.5 py-1 rounded-md bg-white border border-[#E2E8F0] text-[#1E3A5F]">
            03 OCT 2026
          </span>
        </div>
      </div>

      {/* Search Bar with Tabs Header */}
      <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm p-5 space-y-4">
        {/* Search Input Box */}
        <form onSubmit={handleSearchSubmit} className="relative flex items-center">
          <Search className="w-5 h-5 text-[#94A3B8] absolute left-4 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by Customer ID, name or account number..."
            className="w-full pl-12 pr-20 py-3 text-sm bg-white border border-[#E2E8F0] rounded-xl text-[#1E3A5F] placeholder-[#94A3B8] focus:outline-none focus:ring-2 focus:ring-[#2563EB] focus:border-transparent transition-all"
          />
          <div className="absolute right-3.5 flex items-center space-x-2">
            <span className="hidden sm:inline-block px-2 py-1 text-[11px] font-mono font-semibold text-[#64748B] bg-[#F1F5F9] border border-[#E2E8F0] rounded-md">
              Ctrl+K
            </span>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-1.5 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-semibold rounded-lg shadow-sm transition-all disabled:opacity-50"
            >
              {loading ? 'Searching...' : 'Search'}
            </button>
          </div>
        </form>

        {/* Filter Navigation Tabs */}
        <div className="flex items-center space-x-6 border-b border-[#E2E8F0] pt-1 text-xs font-semibold overflow-x-auto">
          <button
            type="button"
            onClick={() => setActiveFilter('ALL')}
            className={`pb-2.5 flex items-center space-x-1.5 transition-all ${
              activeFilter === 'ALL'
                ? 'text-[#2563EB] border-b-2 border-[#2563EB]'
                : 'text-[#64748B] hover:text-[#1E3A5F]'
            }`}
          >
            <span>ALL</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-blue-100 text-[#2563EB] font-bold">
              50
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveFilter('PROCESSING')}
            className={`pb-2.5 flex items-center space-x-1.5 transition-all ${
              activeFilter === 'PROCESSING'
                ? 'text-[#2563EB] border-b-2 border-[#2563EB]'
                : 'text-[#64748B] hover:text-[#1E3A5F]'
            }`}
          >
            <span>PROCESSING</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-slate-100 text-[#64748B]">
              5
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveFilter('IN_REVIEW')}
            className={`pb-2.5 flex items-center space-x-1.5 transition-all ${
              activeFilter === 'IN_REVIEW'
                ? 'text-[#2563EB] border-b-2 border-[#2563EB]'
                : 'text-[#64748B] hover:text-[#1E3A5F]'
            }`}
          >
            <span>IN REVIEW</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-amber-100 text-amber-700 font-bold">
              10
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveFilter('VERIFIED')}
            className={`pb-2.5 flex items-center space-x-1.5 transition-all ${
              activeFilter === 'VERIFIED'
                ? 'text-[#2563EB] border-b-2 border-[#2563EB]'
                : 'text-[#64748B] hover:text-[#1E3A5F]'
            }`}
          >
            <span>VERIFIED</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-emerald-100 text-emerald-700 font-bold">
              2
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveFilter('FLAGGED')}
            className={`pb-2.5 flex items-center space-x-1.5 transition-all ${
              activeFilter === 'FLAGGED'
                ? 'text-[#2563EB] border-b-2 border-[#2563EB]'
                : 'text-[#64748B] hover:text-[#1E3A5F]'
            }`}
          >
            <span>FLAGGED</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-rose-100 text-rose-700 font-bold">
              1
            </span>
          </button>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-sm text-[#DC2626]">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="font-medium">{error}</span>
        </div>
      )}

      {/* Success Notification */}
      {flagSuccess && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between text-sm text-emerald-700">
          <div className="flex items-center space-x-2">
            <Check className="w-5 h-5 text-emerald-600" />
            <span className="font-semibold">{flagSuccess}</span>
          </div>
          <button
            onClick={() => setFlagSuccess(null)}
            className="text-xs text-emerald-700 hover:underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Main Customer 360 View */}
      {data && !loading && (
        <>
          {/* Customer Profile Card */}
          <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm p-6 sm:p-7">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
              {/* Left: Avatar, Name, Member ID, Badges */}
              <div className="flex items-center space-x-5">
                <div className="w-16 h-16 rounded-full bg-[#E0F2FE] text-[#0284C7] flex items-center justify-center font-bold text-2xl shadow-inner border border-sky-100 flex-shrink-0">
                  {initials}
                </div>
                <div className="space-y-1.5">
                  <h2 className="text-2xl sm:text-[26px] font-extrabold text-[#1E3A5F] tracking-tight">
                    {data.displayName}
                  </h2>
                  <div className="flex flex-wrap items-center gap-2.5">
                    <div className="flex items-center space-x-1.5 font-mono text-xs text-[#64748B]">
                      <span>{data.membershipNumber || `CUS-${String(data.customerId).padStart(8, '0')}`}</span>
                      <button
                        type="button"
                        onClick={() =>
                          copyToClipboard(
                            data.membershipNumber || `CUS-${String(data.customerId).padStart(8, '0')}`,
                            'member'
                          )
                        }
                        className="p-0.5 hover:text-[#2563EB] text-[#94A3B8] transition-colors"
                        title="Copy Customer ID"
                      >
                        {copiedItem === 'member' ? (
                          <Check className="w-3.5 h-3.5 text-emerald-600" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    {/* Verified Pill */}
                    <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#DCFCE7] text-[#16A34A] border border-[#BBF7D0]">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#16A34A]" />
                      <span>{data.kycStatus || 'VERIFIED'}</span>
                    </span>

                    {/* Tier Pill */}
                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#F1F5F9] text-[#64748B] border border-[#E2E8F0]">
                      {data.kycTier?.replace('_FULL', '') || 'TIER 3'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Right: Risk Score & Credit Score Blocks */}
              <div className="flex items-center space-x-8 bg-[#F8FAFC] p-4 sm:p-5 rounded-xl border border-[#E2E8F0] self-start md:self-auto">
                <div className="text-right">
                  <div className="text-[11px] font-bold uppercase tracking-wider text-[#64748B]">
                    RISK SCORE
                  </div>
                  <div className="text-2xl font-black text-emerald-600 font-mono tracking-tight mt-0.5">
                    12<span className="text-xs font-semibold text-[#94A3B8]">/100</span>
                  </div>
                </div>

                <div className="h-8 w-px bg-[#E2E8F0]" />

                <div className="text-right">
                  <div className="text-[11px] font-bold uppercase tracking-wider text-[#64748B]">
                    CREDIT SCORE
                  </div>
                  <div className="text-2xl font-black text-[#1E3A5F] font-mono tracking-tight mt-0.5">
                    {data.creditScore || '750'}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* 4 Metric KPI Cards Row */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
            {/* Total Ledger - Dark Navy Top Border */}
            <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] border-t-[3px] border-t-[#1E3A5F] shadow-sm">
              <span className="text-xs font-semibold text-[#64748B] tracking-tight">
                Total Ledger
              </span>
              <div className="text-2xl font-bold font-mono text-[#1E3A5F] tracking-tight mt-2">
                {formatCurrency(totalLedger)}
              </div>
            </div>

            {/* Available - Electric/Vivid Blue Top Border */}
            <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] border-t-[3px] border-t-[#2563EB] shadow-sm">
              <span className="text-xs font-semibold text-[#64748B] tracking-tight">
                Available
              </span>
              <div className="text-2xl font-bold font-mono text-[#1E3A5F] tracking-tight mt-2">
                {formatCurrency(availableTotal)}
              </div>
            </div>

            {/* Held / Escrow - Warm Amber/Orange Top Border */}
            <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] border-t-[3px] border-t-[#F59E0B] shadow-sm">
              <span className="text-xs font-semibold text-[#64748B] tracking-tight">
                Held / Escrow
              </span>
              <div className="text-2xl font-bold font-mono text-[#1E3A5F] tracking-tight mt-2">
                {formatCurrency(heldTotal)}
              </div>
            </div>

            {/* 30-Day Velocity - Indigo/Purple Top Border & Sparkline */}
            <div className="bg-white rounded-2xl p-5 border border-[#E2E8F0] border-t-[3px] border-t-[#6366F1] shadow-sm flex items-end justify-between">
              <div>
                <span className="text-xs font-semibold text-[#64748B] tracking-tight">
                  30-Day Velocity
                </span>
                <div className="text-2xl font-bold font-mono text-[#1E3A5F] tracking-tight mt-2">
                  ₱184,200.00
                </div>
              </div>
              {/* Mini Sparkline SVG */}
              <div className="w-16 h-8 text-[#6366F1] mb-1">
                <svg viewBox="0 0 64 32" className="w-full h-full stroke-current fill-none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M2 24 L 14 24 L 24 16 L 34 20 L 46 12 L 54 16 L 62 8" />
                </svg>
              </div>
            </div>
          </div>

          {/* Main 2-Column Split: Content Tables (Left) + Side Panel Widgets (Right) */}
          <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
            {/* Left 2 Cols: Accounts & Recent Activity */}
            <div className="xl:col-span-2 space-y-6">
              {/* Accounts Table Card */}
              <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm overflow-hidden">
                <div className="p-6 border-b border-[#E2E8F0] flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-[#1E3A5F] tracking-tight">Accounts</h3>
                    <p className="text-xs text-[#64748B] mt-0.5">
                      {data.accounts?.length || 0} active relationships
                    </p>
                  </div>
                  <div className="text-xs font-mono font-medium text-[#94A3B8]">
                    LAST SYNC {lastSyncTime}
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm border-collapse">
                    <thead>
                      <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-[11px] font-bold text-[#64748B] uppercase tracking-wider">
                        <th className="py-3 px-5">Account Number</th>
                        <th className="py-3 px-5">Type</th>
                        <th className="py-3 px-5">Status</th>
                        <th className="py-3 px-5 text-right">Available Balance (₱)</th>
                        <th className="py-3 px-5 text-right">Ledger Balance (₱)</th>
                        <th className="py-3 px-5 text-right">Held</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E2E8F0]">
                      {data.accounts && data.accounts.length > 0 ? (
                        data.accounts.map((acc: StaffAccountSummary) => {
                          const isLoan = acc.accountType === 'LOAN';
                          return (
                            <tr key={acc.accountId} className="hover:bg-[#F8FAFC]/80 transition-colors">
                              <td className="py-3.5 px-5">
                                <div className="flex items-center space-x-2">
                                  <span className="font-mono font-semibold text-[#1E3A5F]">
                                    {acc.accountNumber}
                                  </span>
                                  <button
                                    onClick={() => copyToClipboard(acc.accountNumber, acc.accountNumber)}
                                    className="text-[#94A3B8] hover:text-[#2563EB] p-0.5"
                                    title="Copy Account Number"
                                  >
                                    {copiedItem === acc.accountNumber ? (
                                      <Check className="w-3.5 h-3.5 text-emerald-600" />
                                    ) : (
                                      <Copy className="w-3.5 h-3.5" />
                                    )}
                                  </button>
                                </div>
                              </td>
                              <td className="py-3.5 px-5">
                                <span className="text-xs font-bold text-[#64748B]">
                                  {acc.accountType}
                                </span>
                              </td>
                              <td className="py-3.5 px-5">
                                <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#DCFCE7] text-[#16A34A] border border-[#BBF7D0]">
                                  <span className="w-1.5 h-1.5 rounded-full bg-[#16A34A]" />
                                  <span>{acc.status}</span>
                                </span>
                              </td>
                              <td className="py-3.5 px-5 text-right font-mono font-bold text-[#1E3A5F] tabular-nums">
                                {isLoan ? '—' : formatCurrency(acc.availableBalance)}
                              </td>
                              <td className="py-3.5 px-5 text-right font-mono text-[#1E3A5F] tabular-nums">
                                {isLoan ? (
                                  <div>
                                    <span className="text-[10px] text-[#64748B] block">Outstanding</span>
                                    <span className="font-bold text-rose-600">
                                      {formatCurrency(acc.outstandingPrincipal || '250000.00')}
                                    </span>
                                  </div>
                                ) : (
                                  formatCurrency(acc.ledgerBalance)
                                )}
                              </td>
                              <td className="py-3.5 px-5 text-right font-mono text-[#64748B] tabular-nums">
                                {isLoan ? (
                                  <div>
                                    <span className="text-[10px] text-[#64748B] block">Next Due</span>
                                    <span>{acc.nextDueDate || '2026-11-01'}</span>
                                  </div>
                                ) : (
                                  formatCurrency(acc.heldAmount)
                                )}
                              </td>
                            </tr>
                          );
                        })
                      ) : (
                        <tr>
                          <td colSpan={6} className="py-8 text-center text-xs text-[#64748B]">
                            No bank accounts associated with this customer record.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Recent Transfers Activity Card */}
              <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm overflow-hidden">
                <div className="p-6 border-b border-[#E2E8F0] flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-[#1E3A5F] tracking-tight">Recent transfers</h3>
                    <p className="text-xs text-[#64748B] mt-0.5">Latest account activity &amp; Risk Engine telemetry</p>
                  </div>
                  {data?.customerId && (
                    <button
                      type="button"
                      onClick={() => fetchCustomer(data.customerId)}
                      disabled={loading}
                      className="px-3 py-1.5 rounded-lg border border-[#CBD5E1] hover:border-[#2563EB] text-xs font-semibold text-[#1E3A5F] hover:text-[#2563EB] transition-colors"
                    >
                      Refresh Activity
                    </button>
                  )}
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm border-collapse">
                    <thead>
                      <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-[11px] font-bold text-[#64748B] uppercase tracking-wider">
                        <th className="py-3 px-5">Timestamp</th>
                        <th className="py-3 px-5">Ref ID</th>
                        <th className="py-3 px-5">Counterparty</th>
                        <th className="py-3 px-5">Type</th>
                        <th className="py-3 px-5 text-right">Amount (₱)</th>
                        <th className="py-3 px-5">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E2E8F0]">
                      {recentTransfersList.length > 0 ? (
                        recentTransfersList.map((tx: any, idx: number) => {
                          const rawTimestamp = tx.createdAt || tx.timestamp;
                          const formattedTime = rawTimestamp ? formatTimestamp(rawTimestamp) : '—';

                          const displayRef =
                            tx.referenceNo ||
                            tx.refId ||
                            (tx.transactionId ? truncateHash(tx.transactionId, 8, 4) : '—');

                          let displayCounterparty =
                            tx.counterpartyName ||
                            tx.counterparty ||
                            tx.counterpartyAccountNumber;
                          if (!displayCounterparty) {
                            if (tx.category === 'DEPOSIT') displayCounterparty = 'Counter Deposit';
                            else if (tx.category === 'WITHDRAWAL') displayCounterparty = 'Counter Withdrawal';
                            else if (tx.category === 'TRANSFER') displayCounterparty = 'Internal Transfer';
                            else if (tx.category === 'REVERSAL') displayCounterparty = tx.direction === 'IN' ? 'Reversal Refund' : 'Reversal Debit';
                            else displayCounterparty = 'Counter Desk';
                          }

                          let displayType = tx.type;
                          if (!displayType) {
                            if (tx.category === 'DEPOSIT') {
                              displayType = 'Cash Deposit';
                            } else if (tx.category === 'WITHDRAWAL') {
                              displayType = 'Cash Withdrawal';
                            } else if (tx.category === 'TRANSFER') {
                              displayType = tx.direction === 'IN' ? 'Transfer In' : 'Transfer Out';
                            } else if (tx.category === 'REVERSAL') {
                              displayType = 'Reversal';
                            } else if (tx.category) {
                              displayType = tx.category.charAt(0) + tx.category.slice(1).toLowerCase();
                            } else if (tx.direction) {
                              displayType = tx.direction === 'IN' ? 'Transfer In' : 'Transfer Out';
                            } else {
                              displayType = 'Transfer';
                            }
                          }

                          const isCredit =
                            tx.direction === 'IN' ||
                            (typeof tx.amount === 'string' && tx.amount.trim().startsWith('+'));
                          const isDebit =
                            tx.direction === 'OUT' ||
                            (typeof tx.amount === 'string' && tx.amount.trim().startsWith('-'));

                          let rawNum = 0;
                          if (typeof tx.amount === 'number') {
                            rawNum = tx.amount;
                          } else if (typeof tx.amount === 'string') {
                            rawNum = parseFloat(tx.amount.replace(/[^0-9.-]/g, '')) || 0;
                          }
                          const formattedNum = formatCurrency(Math.abs(rawNum));
                          const displayAmount = isCredit
                            ? `+${formattedNum}`
                            : isDebit
                            ? `-${formattedNum}`
                            : formattedNum;

                          const statusUpper = (tx.status || 'POSTED').toUpperCase();
                          const failureCodeUpper = String(tx.failureCode || '').toUpperCase();
                          const failedStageUpper = String(tx.failedStageName || '').toUpperCase();
                          const isRiskFlagged =
                            ['RISK_DECLINED', 'LIMIT_EXCEEDED', 'HIGH_RISK_SCORE', 'VELOCITY_EXCEEDED', 'AMLA_THRESHOLD'].includes(failureCodeUpper) ||
                            ['FRAUD_CHECK', 'LIMIT_CHECK'].includes(failedStageUpper);
                          const isTrackable =
                            displayRef !== '—' &&
                            !String(displayRef).startsWith('EOD-') &&
                            !String(displayRef).startsWith('DEP-');

                          return (
                            <tr key={tx.transactionId || tx.referenceNo || idx} className="hover:bg-[#F8FAFC]/80 transition-colors">
                              <td className="py-3.5 px-5 font-mono text-xs text-[#64748B]">
                                {formattedTime}
                              </td>
                              <td className="py-3.5 px-5 font-mono text-xs font-semibold text-[#1E3A5F]">
                                <div className="inline-flex items-center space-x-1.5">
                                  {isTrackable ? (
                                    <button
                                      type="button"
                                      onClick={() => navigate(`/tracker?ref=${displayRef}`)}
                                      className="hover:text-[#2563EB] hover:underline text-left"
                                      title="View 11-Stage Pipeline in Tracker"
                                    >
                                      {displayRef}
                                    </button>
                                  ) : (
                                    <span>{displayRef}</span>
                                  )}
                                  {displayRef !== '—' && (
                                    <button
                                      type="button"
                                      onClick={() => copyToClipboard(displayRef, `ref-${idx}-${displayRef}`)}
                                      className="p-1 rounded hover:bg-[#F1F5F9] text-[#94A3B8] hover:text-[#2563EB] transition-colors"
                                      title="Copy Ref ID"
                                    >
                                      {copiedItem === `ref-${idx}-${displayRef}` ? (
                                        <Check className="w-3.5 h-3.5 text-emerald-600" />
                                      ) : (
                                        <Copy className="w-3.5 h-3.5" />
                                      )}
                                    </button>
                                  )}
                                </div>
                              </td>
                              <td className="py-3.5 px-5 text-sm font-medium text-[#1E3A5F]">
                                <div>{displayCounterparty}</div>
                                {tx.failedStageName && (
                                  <div className="text-[11px] text-rose-600 font-normal mt-0.5">
                                    Halted at Stage #{tx.failedStageNo || '?'}: {tx.failedStageTitle || tx.failedStageName}
                                  </div>
                                )}
                              </td>
                              <td className="py-3.5 px-5 text-xs text-[#64748B]">
                                {displayType}
                              </td>
                              <td
                                className={`py-3.5 px-5 text-right font-mono font-bold tabular-nums ${
                                  isCredit ? 'text-emerald-600' : isDebit ? 'text-rose-600' : 'text-[#1E3A5F]'
                                }`}
                              >
                                {displayAmount}
                              </td>
                              <td className="py-3.5 px-5">
                                {['POSTED', 'COMPLETED', 'SUCCESS', 'SETTLED'].includes(statusUpper) && (
                                  <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#DCFCE7] text-[#16A34A] border border-[#BBF7D0]">
                                    <span className="w-1.5 h-1.5 rounded-full bg-[#16A34A]" />
                                    <span>POSTED</span>
                                  </span>
                                )}
                                {statusUpper === 'REVERSED' && (
                                  <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#FEE2E2] text-[#B91C1C] border border-[#FECACA]">
                                    <span className="w-1.5 h-1.5 rounded-full bg-[#B91C1C]" />
                                    <span>REVERSED</span>
                                  </span>
                                )}
                                {['PROCESSING', 'IN_REVIEW', 'RUNNING', 'PENDING', 'INITIATED'].includes(statusUpper) && (
                                  <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#FEF3C7] text-amber-700 border border-[#FDE68A]">
                                    <span className="w-1.5 h-1.5 rounded-full bg-amber-600 animate-pulse" />
                                    <span>{statusUpper}</span>
                                  </span>
                                )}
                                {['FAILED', 'REJECTED', 'HALTED', 'CANCELLED'].includes(statusUpper) && (
                                  <div className="flex flex-col items-start gap-1">
                                    <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#FEE2E2] text-rose-700 border border-[#FECACA]">
                                      <span className="w-1.5 h-1.5 rounded-full bg-rose-600" />
                                      <span>{isRiskFlagged ? `FLAGGED • ${failureCodeUpper || failedStageUpper}` : failureCodeUpper ? `FAILED • ${failureCodeUpper}` : 'FAILED'}</span>
                                    </span>
                                  </div>
                                )}
                                {!['POSTED', 'COMPLETED', 'SUCCESS', 'SETTLED', 'REVERSED', 'PROCESSING', 'IN_REVIEW', 'RUNNING', 'PENDING', 'INITIATED', 'FAILED', 'REJECTED', 'HALTED', 'CANCELLED'].includes(statusUpper) && (
                                  <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#F1F5F9] text-[#64748B] border border-[#E2E8F0]">
                                    <span>{statusUpper}</span>
                                  </span>
                                )}
                              </td>
                            </tr>
                          );
                        })
                      ) : (
                        <tr>
                          <td colSpan={6} className="py-8 text-center text-xs text-[#64748B]">
                            No recent transactions found for this customer.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* Right 1 Col: Side Cards (Accounts, Quick Actions, Spending, Beneficiaries) */}
            <div className="space-y-6">
              {/* Card 1: ACCOUNTS */}
              <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm p-6 space-y-4">
                <h4 className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
                  Accounts
                </h4>

                <div className="space-y-4">
                  {data.accounts?.map((acc: StaffAccountSummary, idx: number) => {
                    const isLoan = acc.accountType === 'LOAN';
                    return (
                      <div key={acc.accountId} className={idx > 0 ? 'pt-3 border-t border-[#E2E8F0]' : ''}>
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-bold text-[#64748B] uppercase tracking-wider">
                            {acc.accountType}
                          </span>
                          <span className="inline-flex items-center space-x-1 text-xs font-semibold text-emerald-600">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                            <span>ACTIVE</span>
                          </span>
                        </div>

                        <div className="flex items-center space-x-1.5 font-mono text-sm font-semibold text-[#1E3A5F] mt-1">
                          <span>{acc.accountNumber}</span>
                          <button
                            type="button"
                            onClick={() => copyToClipboard(acc.accountNumber, `mini-${acc.accountNumber}`)}
                            className="text-[#94A3B8] hover:text-[#2563EB]"
                            title="Copy"
                          >
                            {copiedItem === `mini-${acc.accountNumber}` ? (
                              <Check className="w-3.5 h-3.5 text-emerald-600" />
                            ) : (
                              <Copy className="w-3.5 h-3.5" />
                            )}
                          </button>
                        </div>

                        <div className="flex items-center justify-between text-xs mt-1.5">
                          <span className="text-[#64748B]">
                            {isLoan ? 'Outstanding' : 'Available'}
                          </span>
                          <span className="font-mono font-bold text-[#1E3A5F]">
                            {isLoan
                              ? formatCurrency(acc.outstandingPrincipal || '250000.00')
                              : formatCurrency(acc.availableBalance)}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Card 2: QUICK ACTIONS */}
              <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm p-6 space-y-3.5">
                <h4 className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
                  Quick Actions
                </h4>

                <div className="space-y-2.5">
                  <button
                    type="button"
                    onClick={() => {
                      const firstAcc = data.accounts?.[0]?.accountNumber || data.accounts?.[0]?.accountId;
                      navigate(firstAcc ? `/counter?mode=deposit&accountId=${firstAcc}` : '/counter');
                    }}
                    className="w-full py-2.5 px-4 rounded-xl border border-[#E2E8F0] hover:border-blue-400 hover:bg-blue-50/50 text-[#1E3A5F] text-xs font-semibold flex items-center justify-center space-x-2 transition-all"
                  >
                    <Plus className="w-4 h-4 text-[#2563EB]" />
                    <span>Cash Deposit</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      const firstAcc = data.accounts?.[0]?.accountNumber || data.accounts?.[0]?.accountId;
                      navigate(firstAcc ? `/counter?mode=transfer&accountId=${firstAcc}` : '/counter');
                    }}
                    className="w-full py-2.5 px-4 rounded-xl border border-[#E2E8F0] hover:border-blue-400 hover:bg-blue-50/50 text-[#1E3A5F] text-xs font-semibold flex items-center justify-center space-x-2 transition-all"
                  >
                    <ArrowUpRight className="w-4 h-4 text-[#2563EB]" />
                    <span>Assisted Transfer</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      if (data.accounts?.[0]) {
                        setFlaggingAccount(data.accounts[0]);
                        setShowFlagModal(true);
                      } else {
                        navigate('/accounts');
                      }
                    }}
                    className="w-full py-2.5 px-4 rounded-xl border border-rose-200 hover:bg-rose-50 text-rose-600 text-xs font-semibold flex items-center justify-center space-x-2 transition-all"
                  >
                    <ShieldAlert className="w-4 h-4" />
                    <span>Flag Account</span>
                  </button>
                </div>
              </div>

              {/* Card 3: SPENDING BREAKDOWN */}
              <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm p-6 space-y-4">
                <h4 className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
                  Spending Breakdown
                </h4>

                <div className="flex items-center justify-between gap-4">
                  {/* Circular Donut Graphic */}
                  <div className="relative w-28 h-28 flex items-center justify-center flex-shrink-0">
                    <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                      <path
                        className="text-slate-100"
                        strokeWidth="3.8"
                        stroke="currentColor"
                        fill="none"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      />
                      {/* Segment 1: Transfer 42% */}
                      <path
                        className="text-blue-600"
                        strokeDasharray="42, 100"
                        strokeWidth="3.8"
                        strokeLinecap="round"
                        stroke="currentColor"
                        fill="none"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      />
                      {/* Segment 2: Bills 24% */}
                      <path
                        className="text-indigo-900"
                        strokeDasharray="24, 100"
                        strokeDashoffset="-42"
                        strokeWidth="3.8"
                        stroke="currentColor"
                        fill="none"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      />
                      {/* Segment 3: Savings 18% */}
                      <path
                        className="text-emerald-500"
                        strokeDasharray="18, 100"
                        strokeDashoffset="-66"
                        strokeWidth="3.8"
                        stroke="currentColor"
                        fill="none"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      />
                      {/* Segment 4: Fees 8% */}
                      <path
                        className="text-amber-400"
                        strokeDasharray="8, 100"
                        strokeDashoffset="-84"
                        strokeWidth="3.8"
                        stroke="currentColor"
                        fill="none"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      />
                    </svg>
                    <div className="absolute flex flex-col items-center justify-center text-center">
                      <span className="font-bold text-xs font-mono text-[#1E3A5F]">₱184K</span>
                      <span className="text-[9px] uppercase tracking-wider text-[#94A3B8]">30 DAYS</span>
                    </div>
                  </div>

                  {/* Category Legend */}
                  <div className="space-y-1.5 text-xs text-[#64748B] flex-1">
                    <div className="flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <span className="w-2 h-2 rounded-full bg-blue-600" />
                        <span>Transfer</span>
                      </span>
                      <span className="font-mono font-semibold text-[#1E3A5F]">42%</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <span className="w-2 h-2 rounded-full bg-indigo-900" />
                        <span>Bills</span>
                      </span>
                      <span className="font-mono font-semibold text-[#1E3A5F]">24%</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <span className="w-2 h-2 rounded-full bg-amber-400" />
                        <span>Fees</span>
                      </span>
                      <span className="font-mono font-semibold text-[#1E3A5F]">8%</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <span className="w-2 h-2 rounded-full bg-emerald-500" />
                        <span>Savings</span>
                      </span>
                      <span className="font-mono font-semibold text-[#1E3A5F]">18%</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <span className="w-2 h-2 rounded-full bg-slate-400" />
                        <span>Other</span>
                      </span>
                      <span className="font-mono font-semibold text-[#1E3A5F]">8%</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Card 4: BENEFICIARIES */}
              <div className="bg-white rounded-2xl border border-[#E2E8F0] shadow-sm p-6 space-y-4">
                <h4 className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
                  Beneficiaries
                </h4>

                <div className="space-y-3.5">
                  {beneficiariesList.map((b: any, idx: number) => {
                    const bInitials = (b.name || b.nickname || 'BN')
                      .split(' ')
                      .map((p: string) => p[0])
                      .join('')
                      .substring(0, 2)
                      .toUpperCase();

                    return (
                      <div key={idx} className="flex items-center space-x-3 text-xs">
                        <div className="w-8 h-8 rounded-full bg-[#F1F5F9] text-[#64748B] font-bold flex items-center justify-center flex-shrink-0 text-[11px] border border-[#E2E8F0]">
                          {bInitials}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="font-semibold text-sm text-[#1E3A5F] truncate">
                            {b.name || b.nickname}
                          </div>
                          <div className="font-mono text-xs text-[#64748B]">
                            {b.accountNumber}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Flag Account Modal */}
      {showFlagModal && flaggingAccount && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-[#E2E8F0] space-y-4">
            <div className="flex items-center space-x-2 text-rose-600">
              <ShieldAlert className="w-5 h-5" />
              <h3 className="font-bold text-lg text-[#1E3A5F]">Flag Account for Review</h3>
            </div>
            <p className="text-xs text-[#64748B]">
              Place a supervisory security hold flag on account{' '}
              <strong className="text-[#1E3A5F] font-mono">{flaggingAccount.accountNumber}</strong>.
            </p>

            <form onSubmit={handleFlagAccount} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#1E3A5F] mb-1">
                  Reason for Flagging (Required)
                </label>
                <textarea
                  rows={3}
                  value={flagReason}
                  onChange={(e) => setFlagReason(e.target.value)}
                  placeholder="e.g. Suspected AML structuring, court order or lost card notice"
                  required
                  className="w-full p-3 text-sm border border-[#E2E8F0] rounded-xl focus:ring-2 focus:ring-rose-500 focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowFlagModal(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-[#64748B] hover:bg-[#F1F5F9]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!flagReason.trim()}
                  className="px-5 py-2 rounded-xl text-xs font-semibold bg-rose-600 hover:bg-rose-700 text-white shadow-sm disabled:opacity-50"
                >
                  Confirm Security Flag
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Customer360;
