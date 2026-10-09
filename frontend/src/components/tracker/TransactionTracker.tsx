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

  // Reconcile all 11 stages
  const normalizedStages: TimelineStage[] = React.useMemo(() => {
    const backendStages = timelineData?.stages || [];
    return ALL_11_STAGES.map((specStage, idx) => {
      const matched = backendStages.find(
        (s: any) => s.stageName.toUpperCase() === specStage.name.toUpperCase()
      );
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
  }, [timelineData]);

  const canReverse =
    txSummary?.status === 'POSTED' &&
    !txSummary.reversedByTransactionId &&
    !txSummary.referenceNo?.startsWith('RV-');

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
        <div>
          <h3 className="text-base font-bold text-[#1E3A5F]">11-Stage Transaction Pipeline</h3>
          <p className="text-xs text-[#64748B]">
            Cryptographic, fraud, balance and dual-entry ledger lifecycle verification
          </p>
        </div>

        <div className="relative pl-6 space-y-6 before:absolute before:left-3 before:top-2 before:bottom-2 before:w-0.5 before:bg-[#E2E8F0]">
          {normalizedStages.map((st, idx) => {
            const isPassed = st.outcome === 'PASSED';
            const isFailed = st.outcome === 'FAILED';
            const isInProgress = st.outcome === 'IN_PROGRESS';

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
                      : 'border-[#CBD5E1] text-[#94A3B8]'
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
                <div className="flex-1 bg-[#F8FAFC] p-3.5 rounded-xl border border-[#E2E8F0] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="font-mono font-bold text-xs text-[#1E3A5F]">
                        #{idx + 1} {st.stageName}
                      </span>
                      <span className={`pill text-[10px] ${getStatusClass(st.outcome)}`}>
                        {st.outcome}
                      </span>
                    </div>
                    <div className="text-[11px] text-[#64748B] mt-0.5">
                      Service: <span className="font-mono text-[#1E3A5F]">{st.service}</span>
                      {st.detail && ` &bull; ${st.detail}`}
                    </div>
                  </div>

                  <div className="text-right text-[11px] font-mono text-[#64748B]">
                    {st.timestamp ? formatTimestamp(st.timestamp) : 'Pending Execution'}
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
