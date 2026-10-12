import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { staffApi } from '../../services/api';
import type { TimelineResponse, TimelineStage, TransactionSummary } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp, getStatusClass, generateUUID } from '../../lib/utils';
import {
  Search,
  GitBranch,
  RotateCcw,
  Clock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  RefreshCw,
  Copy,
  Check,
  Radio,
  ArrowRight,
} from 'lucide-react';

const ALL_11_STAGES: Array<{ name: string; service: string }> = [
  { name: 'INITIATED', service: 'api-gateway' },
  { name: 'VALIDATED', service: 'transfer-service' },
  { name: 'AUTHENTICATED', service: 'auth-service' },
  { name: 'FRAUD_CHECK', service: 'fraud-detection' },
  { name: 'LIMIT_CHECK', service: 'policy-service' },
  { name: 'FUNDS_CHECK', service: 'ledger-service' },
  { name: 'AUTHORIZED', service: 'transfer-service' },
  { name: 'POSTED', service: 'ledger-service' },
  { name: 'LEDGER_UPDATE', service: 'ledger-service' },
  { name: 'NOTIFICATION', service: 'notification-service' },
  { name: 'RECONCILIATION', service: 'eod-batch-service' },
];

export const TransactionTracker: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchQuery, setSearchQuery] = useState(
    searchParams.get('ref') || 'TX-20261003-8F3A21C0'
  );

  const [timelineData, setTimelineData] = useState<any | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Polling State
  const [isPolling, setIsPolling] = useState(false);
  const pollingTimerRef = useRef<any | null>(null);

  // Reversal Modal
  const [isReversalOpen, setIsReversalOpen] = useState(false);
  const [reversalReason, setReversalReason] = useState('Customer duplicate entry reversal request');
  const [reversing, setReversing] = useState(false);
  const [reversalError, setReversalError] = useState<string | null>(null);
  const [copiedRef, setCopiedRef] = useState(false);

  const fetchTimeline = async (txIdOrRef: string, isPoll = false) => {
    if (!isPoll) setLoading(true);
    setError(null);
    try {
      const data = await staffApi.getTransactionTimeline(txIdOrRef);
      setTimelineData(data);

      const status = (data as any)?.transaction?.status || (data as any)?.currentStatus;
      // Stop polling if completed or terminal
      if (status === 'POSTED' || status === 'FAILED' || status === 'REVERSED') {
        setIsPolling(false);
      }
    } catch (err: any) {
      if (!isPoll) {
        setError(err.message || 'Transaction not found.');
        setTimelineData(null);
      }
    } finally {
      if (!isPoll) setLoading(false);
    }
  };

  useEffect(() => {
    if (searchQuery) {
      fetchTimeline(searchQuery);
    }
  }, []);

  // Polling handler (3 seconds)
  useEffect(() => {
    if (isPolling && searchQuery) {
      pollingTimerRef.current = setInterval(() => {
        fetchTimeline(searchQuery, true);
      }, 3000);
    } else if (pollingTimerRef.current) {
      clearInterval(pollingTimerRef.current);
    }
    return () => {
      if (pollingTimerRef.current) clearInterval(pollingTimerRef.current);
    };
  }, [isPolling, searchQuery]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setSearchParams({ ref: searchQuery });
    fetchTimeline(searchQuery);
  };

  const txSummary: TransactionSummary | null = React.useMemo(() => {
    if (!timelineData) return null;
    const tx = timelineData.transaction;
    if (tx) {
      return {
        transactionId: tx.transactionId || timelineData.trackingReference || timelineData.trackingRef || searchQuery,
        referenceNo: tx.referenceNo || timelineData.trackingReference || timelineData.trackingRef || searchQuery,
        status: (tx.status || timelineData.status || timelineData.currentStatus || 'POSTED') as any,
        channel: tx.channel || timelineData.channel || 'COUNTER_DESK',
        sourceAccount:
          tx.sourceAccountNumber ||
          tx.sourceAccount ||
          (tx.sourceAccountId ? `ACC_${10000000 + Number(tx.sourceAccountId)}` : '') ||
          String(timelineData.sourceAccountNumber || (timelineData.sourceAccountId ? `ACC_${10000000 + Number(timelineData.sourceAccountId)}` : 'ACC_10000001')),
        destinationAccount:
          tx.destinationAccountNumber ||
          tx.destinationAccount ||
          (tx.destinationAccountId ? `ACC_${10000000 + Number(tx.destinationAccountId)}` : '') ||
          String(timelineData.destinationAccountNumber || (timelineData.destinationAccountId ? `ACC_${10000000 + Number(timelineData.destinationAccountId)}` : 'ACC_10000002')),
        amount: String(tx.amount ?? timelineData.amount ?? '0.00'),
        currency: tx.currency || timelineData.currency || 'PHP',
        createdAt: tx.createdAt || timelineData.createdAt || timelineData.lastUpdated || new Date().toISOString(),
        completedAt: tx.completedAt || timelineData.completedAt || timelineData.lastUpdated || new Date().toISOString(),
        reversedByTransactionId: tx.reversedByTransactionId || timelineData.reversedByTransactionId,
        failureCode: tx.failureCode || timelineData.failureMessage,
        failureReason: tx.failureReason,
      };
    }
    return {
      transactionId: timelineData.transactionId || timelineData.trackingReference || timelineData.trackingRef || searchQuery,
      referenceNo: timelineData.trackingReference || timelineData.trackingRef || searchQuery,
      status: (timelineData.status || timelineData.currentStatus || 'POSTED') as any,
      channel: timelineData.channel || 'COUNTER_DESK',
      sourceAccount:
        timelineData.sourceAccountNumber ||
        String(timelineData.sourceAccountId ? `ACC_${10000000 + Number(timelineData.sourceAccountId)}` : 'ACC_10000001'),
      destinationAccount:
        timelineData.destinationAccountNumber ||
        String(timelineData.destinationAccountId ? `ACC_${10000000 + Number(timelineData.destinationAccountId)}` : 'ACC_10000002'),
      amount: String(timelineData.amount ?? '0.00'),
      currency: timelineData.currency || 'PHP',
      createdAt: timelineData.createdAt || timelineData.lastUpdated || new Date().toISOString(),
      completedAt: timelineData.completedAt || timelineData.lastUpdated || new Date().toISOString(),
      reversedByTransactionId: timelineData.reversedByTransactionId,
      failureCode: timelineData.failureMessage,
    };
  }, [timelineData, searchQuery]);

  const handleInitiateReversal = async () => {
    if (!txSummary?.transactionId) return;
    setReversing(true);
    setReversalError(null);
    try {
      const idempotencyKey = generateUUID();
      const res = await staffApi.requestReversal(
        txSummary.transactionId,
        reversalReason,
        idempotencyKey
      );
      setIsReversalOpen(false);

      // Switch to reversal transaction if returned
      const newRef = res?.referenceNo || res?.reversalTransactionId || searchQuery;
      setSearchQuery(newRef);
      setSearchParams({ ref: newRef });
      fetchTimeline(newRef);
    } catch (err: any) {
      if (err.message?.includes('ALREADY_REVERSED')) {
        setReversalError('This transfer has already been reversed.');
      } else if (err.message?.includes('INSUFFICIENT_FUNDS')) {
        setReversalError('The recipient no longer has the funds; the reversal cannot be posted.');
      } else {
        setReversalError(err.message || 'Reversal request failed.');
      }
    } finally {
      setReversing(false);
    }
  };

  const copyRef = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedRef(true);
    setTimeout(() => setCopiedRef(false), 2000);
  };

  const isReversalTx = React.useMemo(() => {
    if (txSummary?.referenceNo?.startsWith('RV-')) return true;
    const backendStages = timelineData?.stages || [];
    return backendStages.some((s: any) =>
      s.detail?.toLowerCase().includes('reversal')
    );
  }, [txSummary, timelineData]);

  const reversedOriginalRef = React.useMemo(() => {
    const backendStages = timelineData?.stages || [];
    for (const s of backendStages) {
      const match = s.detail?.match(/Reversal posted for (TX-[A-Z0-9-]+)/i);
      if (match?.[1]) return match[1];
    }
    return null;
  }, [timelineData]);

  // Reconcile all 11 stages
  const normalizedStages: TimelineStage[] = React.useMemo(() => {
    const backendStages = timelineData?.stages || [];
    const stage8Passed = backendStages.some(
      (s: any) =>
        (s.stageName?.toUpperCase() === 'POSTED' || s.stageNumber === 8) &&
        s.outcome === 'PASSED'
    );

    return ALL_11_STAGES.map((specStage, idx) => {
      const matched = backendStages.find(
        (s: any) => s.stageName.toUpperCase() === specStage.name.toUpperCase()
      );

      // For reversal transactions (RV-...), stages 4-7 (Fraud, Limit, Funds Hold, Auth)
      // are bypassed by design because it is a staff-authorized compensating ledger entry.
      if (
        isReversalTx &&
        stage8Passed &&
        idx >= 3 &&
        idx <= 6 &&
        (!matched || matched.outcome === 'PENDING')
      ) {
        return {
          stageNumber: idx + 1,
          stageName: specStage.name,
          service: matched?.service || specStage.service,
          outcome: 'SKIPPED' as any,
          detail: 'Bypassed — Staff compensating ledger reversal',
          timestamp: undefined,
        };
      }

      if (matched) return matched;

      // Default stage rendering
      return {
        stageNumber: idx + 1,
        stageName: specStage.name,
        service: specStage.service,
        outcome: idx === 0 && !timelineData ? 'IN_PROGRESS' : 'PENDING',
        detail: `Handled by ${specStage.service}`,
      };
    });
  }, [timelineData, isReversalTx]);

  const canReverse =
    txSummary?.status === 'POSTED' &&
    !txSummary.reversedByTransactionId &&
    !isReversalTx;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">Transaction Tracker &amp; Reversals</h1>
          <p className="text-xs text-[#64748B]">
            End-to-end 11-stage pipeline audit trace with live ledger reversal capabilities
          </p>
        </div>

        {/* Polling Toggle */}
        <div className="flex items-center space-x-3">
          <button
            onClick={() => setIsPolling(!isPolling)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center space-x-2 border transition-all ${
              isPolling
                ? 'bg-[#D1FAE5] text-[#065F46] border-[#A7F3D0]'
                : 'bg-white text-[#64748B] border-[#CBD5E1] hover:bg-[#F8FAFC]'
            }`}
          >
            <Radio className={`w-3.5 h-3.5 ${isPolling ? 'animate-pulse text-[#10B981]' : ''}`} />
            <span>{isPolling ? 'Polling Live (3s)' : 'Live Polling Off'}</span>
          </button>

          <button
            onClick={() => fetchTimeline(searchQuery)}
            disabled={loading}
            className="p-2 rounded-lg bg-white border border-[#CBD5E1] text-[#1E3A5F] hover:bg-[#F8FAFC]"
            title="Refresh Timeline"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Search Input Bar */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-4">
        <form onSubmit={handleSearch} className="flex items-center space-x-3">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-[#64748B] absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by Reference (TX-...) or Transaction UUID..."
              className="w-full pl-9 pr-4 py-2 text-xs bg-[#F8FAFC] border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="px-5 py-2 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold rounded-lg shadow-sm transition-all disabled:opacity-50"
          >
            {loading ? 'Tracing...' : 'Trace Pipeline'}
          </button>
        </form>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Summary Card */}
      {txSummary && (
        <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-6 space-y-4">
          <div className="flex flex-col md:flex-row md:items-center justify-between pb-4 border-b border-[#E2E8F0] gap-4">
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-xs uppercase font-semibold text-[#64748B]">Reference:</span>
                <span className="font-mono font-bold text-base text-[#1E3A5F]">
                  {txSummary.referenceNo}
                </span>
                <button
                  onClick={() => copyRef(txSummary.referenceNo)}
                  className="p-1 hover:bg-[#F1F5F9] rounded text-[#64748B]"
                  title="Copy reference"
                >
                  {copiedRef ? (
                    <Check className="w-3.5 h-3.5 text-[#10B981]" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>
              <div className="text-xs text-[#64748B] mt-0.5">
                Channel: <span className="font-medium">{txSummary.channel || 'COUNTER_DESK'}</span>
              </div>
            </div>

            <div className="flex items-center space-x-4">
              <div className="text-right">
                <div className="text-2xl font-bold font-mono text-[#1E3A5F] tabular-nums">
                  {formatCurrency(txSummary.amount)}
                </div>
                <span className={`pill ${getStatusClass(txSummary.status)}`}>
                  {txSummary.status}
                </span>
              </div>

              {/* Initiate Reversal Button */}
              {canReverse && (
                <button
                  onClick={() => setIsReversalOpen(true)}
                  className="px-4 py-2.5 bg-[#EF4444] hover:bg-[#DC2626] text-white text-xs font-bold rounded-lg shadow-sm transition-all flex items-center space-x-1.5"
                >
                  <RotateCcw className="w-4 h-4" />
                  <span>Initiate Reversal</span>
                </button>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
            <div>
              <span className="text-[#64748B] block">Source Account</span>
              <span className="font-mono font-semibold text-[#1E3A5F]">
                {txSummary.sourceAccount}
              </span>
            </div>
            <div>
              <span className="text-[#64748B] block">Destination Account</span>
              <span className="font-mono font-semibold text-[#1E3A5F]">
                {txSummary.destinationAccount}
              </span>
            </div>
            <div>
              <span className="text-[#64748B] block">Created Timestamp</span>
              <span className="text-[#1E3A5F]">
                {formatTimestamp(txSummary.createdAt)}
              </span>
            </div>
            <div>
              <span className="text-[#64748B] block">Completed Timestamp</span>
              <span className="text-[#1E3A5F]">
                {formatTimestamp(txSummary.completedAt)}
              </span>
            </div>
          </div>

          {isReversalTx && (
            <div className="p-3.5 rounded-xl bg-indigo-50/90 border border-indigo-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-indigo-950">
              <div className="flex items-start space-x-2.5">
                <RotateCcw className="w-4 h-4 text-indigo-600 flex-shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold block text-indigo-900">
                    Compensating Ledger Reversal Entry ({txSummary.referenceNo})
                  </span>
                  <span className="text-indigo-800/90 text-[11px]">
                    In double-entry banking, reversals execute as a new compensating transaction that debits the recipient and credits the original sender. Stages #4–#7 are bypassed for staff-authorized reversals, while Stages #8–#11 confirm the reversal entry was posted to the ledger.
                  </span>
                </div>
              </div>
              {reversedOriginalRef && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery(reversedOriginalRef);
                    setSearchParams({ ref: reversedOriginalRef });
                    fetchTimeline(reversedOriginalRef);
                  }}
                  className="px-3 py-1.5 rounded-lg bg-white border border-indigo-300 text-indigo-700 font-semibold hover:bg-indigo-100/60 transition-colors flex items-center space-x-1.5 flex-shrink-0 self-start sm:self-center"
                >
                  <span>View Original ({reversedOriginalRef})</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          )}

          {!isReversalTx && (txSummary.status === 'REVERSED' || txSummary.reversedByTransactionId) && (
            <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-amber-950">
              <div className="flex items-start space-x-2.5">
                <RotateCcw className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold block text-amber-900">
                    Transfer Reversed via Compensating Entry
                  </span>
                  <span className="text-amber-800/90 text-[11px]">
                    This transfer originally completed all 11 stages, and was subsequently reversed by a compensating ledger transaction.
                  </span>
                </div>
              </div>
              {txSummary.reversedByTransactionId && (
                <button
                  type="button"
                  onClick={() => {
                    const revId = String(txSummary.reversedByTransactionId);
                    setSearchQuery(revId);
                    setSearchParams({ ref: revId });
                    fetchTimeline(revId);
                  }}
                  className="px-3 py-1.5 rounded-lg bg-white border border-amber-300 text-amber-800 font-semibold hover:bg-amber-100/60 transition-colors flex items-center space-x-1.5 flex-shrink-0 self-start sm:self-center"
                >
                  <span>Trace Reversal Entry</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          )}

          {txSummary.failureCode && (
            <div className="p-3 rounded-lg bg-[#FEE2E2] border border-[#FECACA] text-xs text-[#DC2626]">
              <strong>Failure Code:</strong> {txSummary.failureCode} &mdash;{' '}
              {txSummary.failureReason || 'No detailed reason provided.'}
            </div>
          )}
        </div>
      )}

      {/* 11-Stage Vertical Pipeline */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-6 space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h3 className="text-base font-bold text-[#1E3A5F]">
              {isReversalTx ? '11-Stage Reversal Pipeline' : '11-Stage Transaction Pipeline'}
            </h3>
            <p className="text-xs text-[#334155]">
              {isReversalTx
                ? 'Compensating double-entry reversal lifecycle verification'
                : 'Cryptographic, fraud, balance and dual-entry ledger lifecycle verification'}
            </p>
          </div>
          {!isReversalTx && (txSummary?.status === 'REVERSED' || txSummary?.reversedByTransactionId) && (
            <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800 border border-amber-300 self-start sm:self-auto">
              <RotateCcw className="w-3.5 h-3.5" />
              <span>REVERSED / COMPENSATED</span>
            </span>
          )}
        </div>

        <div className="relative pl-6 space-y-6 before:absolute before:left-3 before:top-2 before:bottom-2 before:w-0.5 before:bg-[#E2E8F0]">
          {normalizedStages.map((st, idx) => {
            const isPassed = st.outcome === 'PASSED';
            const isFailed = st.outcome === 'FAILED';
            const isInProgress = st.outcome === 'IN_PROGRESS';
            const isSkipped = (st.outcome as string) === 'SKIPPED';
            const isOriginalReversed =
              !isReversalTx &&
              (txSummary?.status === 'REVERSED' || Boolean(txSummary?.reversedByTransactionId));

            return (
              <div key={idx} className="relative flex items-start space-x-4">
                {/* Node Dot */}
                <div
                  className={`absolute -left-[30px] top-0 w-6 h-6 rounded-full flex items-center justify-center border-2 bg-white ${
                    isPassed
                      ? 'border-[#10B981] text-[#10B981]'
                      : isFailed
                      ? 'border-[#EF4444] text-[#EF4444]'
                      : isInProgress
                      ? 'border-[#2563EB] text-[#2563EB] animate-spin'
                      : isSkipped
                      ? 'border-slate-300 text-slate-400 bg-slate-50'
                      : 'border-[#CBD5E1] text-[#475569]'
                  }`}
                >
                  {isPassed ? (
                    <CheckCircle2 className="w-3.5 h-3.5 fill-emerald-100" />
                  ) : isFailed ? (
                    <XCircle className="w-3.5 h-3.5 fill-red-100" />
                  ) : isInProgress ? (
                    <RefreshCw className="w-3 h-3" />
                  ) : (
                    <span className="text-[10px] font-bold font-mono">{idx + 1}</span>
                  )}
                </div>

                {/* Stage Info Card */}
                <div
                  className={`flex-1 p-3.5 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-2 ${
                    isSkipped
                      ? 'bg-slate-50/60 border-slate-200/80 opacity-75'
                      : 'bg-[#F8FAFC] border-[#E2E8F0]'
                  }`}
                >
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono font-bold text-xs text-[#1E3A5F]">
                        #{idx + 1} {st.stageName}
                      </span>
                      {isSkipped ? (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-200/80 text-slate-600 border border-slate-300">
                          BYPASSED
                        </span>
                      ) : (
                        <span className={`pill text-[10px] ${getStatusClass(st.outcome)}`}>
                          {st.outcome}
                        </span>
                      )}
                      {isOriginalReversed && idx >= 7 && isPassed && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-100 text-amber-800 border border-amber-200">
                          REVERSED LATER
                        </span>
                      )}
                    </div>
                    <div className="text-[11px] text-[#334155] mt-0.5">
                      Service: <span className="font-mono text-[#1E3A5F]">{st.service}</span>
                      {st.detail && (
                        <>
                          {' '}
                          &bull; <span>{st.detail}</span>
                        </>
                      )}
                    </div>
                  </div>

                  <div className="text-right text-[11px] font-mono text-[#334155]">
                    {st.timestamp
                      ? formatTimestamp(st.timestamp)
                      : isSkipped
                      ? 'Bypassed (Reversal)'
                      : 'Pending Execution'}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Initiate Reversal Modal */}
      {isReversalOpen && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-bold text-[#1E3A5F]">Initiate Ledger Reversal</h3>
            <p className="text-xs text-[#64748B]">
              Reversing this transfer will debit the recipient and credit the sender. A documented business justification is required.
            </p>

            {reversalError && (
              <div className="p-3 rounded-lg bg-[#FEE2E2] border border-[#FECACA] text-xs text-[#DC2626]">
                {reversalError}
              </div>
            )}

            <div>
              <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                Reason for Reversal
              </label>
              <textarea
                value={reversalReason}
                onChange={(e) => setReversalReason(e.target.value)}
                rows={3}
                required
                className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#EF4444] focus:outline-none"
                placeholder="Document teller authorization and reason..."
              />
            </div>

            <div className="flex space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setIsReversalOpen(false)}
                className="flex-1 py-2 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleInitiateReversal}
                disabled={reversing || !reversalReason.trim()}
                className="flex-1 py-2 px-4 rounded-lg bg-[#EF4444] hover:bg-[#DC2626] text-white text-xs font-semibold disabled:opacity-50"
              >
                {reversing ? 'Reversing Transfer...' : 'Confirm Reversal'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
