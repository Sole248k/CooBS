import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { BatchRunDetail, BatchStepItem } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp, formatDuration, getStatusClass } from '../../lib/utils';
import {
  CalendarClock,
  PlayCircle,
  RefreshCw,
  CheckCircle2,
  Clock,
  AlertOctagon,
  ChevronRight,
  ShieldCheck,
  Coins,
  FileCheck,
  Repeat,
  AlertCircle,
} from 'lucide-react';

const STEP_ICONS: Record<string, React.ElementType> = {
  INTEREST_ACCRUAL: Coins,
  SERVICE_FEES: FileCheck,
  STANDING_INSTRUCTIONS: Repeat,
  LOAN_AMORTIZATION: CalendarClock,
  GL_RECONCILIATION: CheckCircle2,
  AUDIT_SEAL: ShieldCheck,
};

export const EODBatchPipeline: React.FC = () => {
  const { isAdmin } = useAuth();

  const [batchRuns, setBatchRuns] = useState<any[]>([]);
  const [selectedRun, setSelectedRun] = useState<any | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Trigger Modal
  const [isTriggerModalOpen, setIsTriggerModalOpen] = useState(false);
  const [businessDate, setBusinessDate] = useState(new Date().toISOString().slice(0, 10));
  const [triggering, setTriggering] = useState(false);

  const pollIntervalRef = useRef<any | null>(null);


  const loadBatchRuns = async () => {
    setLoading(true);
    setError(null);
    try {
      const runs = await staffApi.getAllBatchRuns();
      setBatchRuns(runs || []);
      if (runs && runs.length > 0) {
        // Keep current selected if exists, else pick latest
        setSelectedRun((prev: any) => {
          if (!prev) return runs[0];
          const found = runs.find((r: any) => r.batchRunId === prev.batchRunId);
          return found || runs[0];
        });

      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch batch runs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatchRuns();
  }, []);

  // Poll while run is RUNNING
  useEffect(() => {
    if (selectedRun?.status === 'RUNNING') {
      pollIntervalRef.current = setInterval(async () => {
        try {
          const updated = await staffApi.getBatchRun(selectedRun.batchRunId);
          setSelectedRun(updated);
          if (updated.status !== 'RUNNING') {
            loadBatchRuns();
          }
        } catch {
          // Ignore polling errors
        }
      }, 2500);
    } else if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
    }
    return () => {
      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    };
  }, [selectedRun?.status, selectedRun?.batchRunId]);

  const handleTriggerEod = async () => {
    setTriggering(true);
    setError(null);
    try {
      const res = await staffApi.triggerEodBatch({ businessDate });
      setIsTriggerModalOpen(false);
      await loadBatchRuns();
      if (res?.batchRunId) {
        const newRun = await staffApi.getBatchRun(res.batchRunId);
        setSelectedRun(newRun);
      }
    } catch (err: any) {
      if (err.message?.includes('BATCH_ALREADY_RUNNING') || err.message?.includes('409')) {
        setError('A batch run is already in progress. Wait for it to complete.');
      } else {
        setError(err.message || 'Failed to trigger End of Day batch run.');
      }
    } finally {
      setTriggering(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">End of Day (EOD) Batch Pipeline</h1>
          <p className="text-xs text-[#64748B]">
            6-step daily interest calculation, loan amortization, trial balance reconciliation &amp; audit seal
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={loadBatchRuns}
            disabled={loading}
            className="p-2 rounded-lg bg-white border border-[#CBD5E1] text-[#1E3A5F] hover:bg-[#F8FAFC]"
            title="Refresh runs"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>

          {/* Trigger button: Admin Only per RBAC matrix */}
          {isAdmin && (
            <button
              onClick={() => setIsTriggerModalOpen(true)}
              className="px-4 py-2 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold rounded-lg shadow-sm transition-all flex items-center space-x-1.5"
            >
              <PlayCircle className="w-4 h-4" />
              <span>Trigger EOD Run</span>
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Grid: Left Run Selector / Right 6-Step Pipeline */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Runs List */}
        <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-4 space-y-3">
          <div className="flex items-center justify-between pb-2 border-b border-[#E2E8F0]">
            <h3 className="text-xs font-bold text-[#1E3A5F] uppercase tracking-wider">
              Batch Execution History
            </h3>
            <span className="text-[11px] text-[#64748B] font-mono">{batchRuns.length} Runs</span>
          </div>

          <div className="space-y-2">
            {batchRuns.map((run) => {
              const isSelected = selectedRun?.batchRunId === run.batchRunId;
              return (
                <div
                  key={run.batchRunId}
                  onClick={() => setSelectedRun(run)}
                  className={`p-3 rounded-lg border text-xs cursor-pointer transition-all ${
                    isSelected
                      ? 'border-[#2563EB] bg-[#EFF6FF]'
                      : 'border-[#E2E8F0] hover:bg-[#F8FAFC]'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold font-mono text-[#1E3A5F]">
                      Run #{run.batchRunId} &bull; {run.businessDate}
                    </span>
                    <span className={`pill text-[10px] ${getStatusClass(run.status)}`}>
                      {run.status}
                    </span>
                  </div>
                  <div className="text-[11px] text-[#64748B] flex justify-between items-center mt-2">
                    <span>Started: {formatTimestamp(run.startedAt)}</span>
                    <ChevronRight className="w-3.5 h-3.5 text-[#64748B]" />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right: Selected Run Details & 6-Step Progress Pipeline */}
        <div className="lg:col-span-2 space-y-6">
          {selectedRun ? (
            <>
              {/* Selected Run Hero Card */}
              <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-6 space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[#E2E8F0]">
                  <div>
                    <div className="flex items-center space-x-3">
                      <h2 className="text-lg font-bold text-[#1E3A5F]">
                        Batch Run #{selectedRun.batchRunId}
                      </h2>
                      <span className={`pill ${getStatusClass(selectedRun.status)}`}>
                        {selectedRun.status}
                      </span>
                    </div>
                    <p className="text-xs text-[#64748B] mt-0.5">
                      Business Date:{' '}
                      <strong className="font-mono text-[#1E3A5F]">{selectedRun.businessDate}</strong>{' '}
                      &bull; Triggered by Operator #{selectedRun.triggeredBy || 1}
                    </p>
                  </div>

                  <div className="text-right text-xs">
                    <span className="text-[#64748B] block">Execution Time</span>
                    <span className="font-mono font-bold text-[#1E3A5F]">
                      {formatTimestamp(selectedRun.startedAt)}
                    </span>
                  </div>
                </div>

                {/* 6-Step Progress Visual */}
                <div className="space-y-4">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-[#64748B]">
                    6-Step Progress Execution
                  </h4>

                  <div className="space-y-3">
                    {selectedRun.steps?.map((step: BatchStepItem) => {
                      const Icon = STEP_ICONS[step.stepName] || Clock;
                      const isComplete = step.status === 'COMPLETED';
                      const isRunning = step.status === 'RUNNING';
                      const isFailed = step.status === 'FAILED';
                      const isSkipped = step.status === 'SKIPPED';

                      return (
                        <div
                          key={step.stepNo}
                          className={`p-4 rounded-xl border transition-all ${
                            isRunning
                              ? 'bg-[#EFF6FF] border-[#93C5FD]'
                              : isFailed
                              ? 'bg-[#FEE2E2] border-[#FCA5A5]'
                              : 'bg-[#F8FAFC] border-[#E2E8F0]'
                          }`}
                        >
                          <div className="flex items-start justify-between gap-4">
                            <div className="flex items-start space-x-3">
                              <div
                                className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5 ${
                                  isComplete
                                    ? 'bg-[#D1FAE5] text-[#10B981]'
                                    : isRunning
                                    ? 'bg-[#DBEAFE] text-[#2563EB]'
                                    : isFailed
                                    ? 'bg-[#FEE2E2] text-[#EF4444]'
                                    : 'bg-[#E2E8F0] text-[#64748B]'
                                }`}
                              >
                                <Icon className={`w-4 h-4 ${isRunning ? 'animate-spin' : ''}`} />
                              </div>

                              <div>
                                <div className="flex items-center space-x-2">
                                  <span className="font-mono font-bold text-xs text-[#1E3A5F]">
                                    Step {step.stepNo}: {step.stepName}
                                  </span>
                                  <span className={`pill text-[10px] ${getStatusClass(step.status)}`}>
                                    {step.status}
                                  </span>
                                </div>
                                <p className="text-xs text-[#64748B] mt-1">
                                  {step.message || 'Execution in progress according to financial rules'}
                                </p>
                              </div>
                            </div>

                            <div className="text-right font-mono text-xs flex-shrink-0">
                              {step.totalAmount && (
                                <div className="font-bold text-[#1E3A5F] tabular-nums">
                                  {formatCurrency(step.totalAmount)}
                                </div>
                              )}
                              <div className="text-[11px] text-[#64748B]">
                                {step.itemsProcessed !== undefined &&
                                  `${step.itemsProcessed} records`}
                                {step.durationMs !== undefined &&
                                  ` (${step.durationMs}ms)`}
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className="bg-white rounded-xl border border-[#E2E8F0] p-12 text-center text-[#64748B]">
              <CalendarClock className="w-10 h-10 mx-auto text-[#64748B] mb-2 opacity-60" />
              <p className="text-sm font-semibold text-[#1E3A5F]">Select a Batch Run</p>
              <p className="text-xs mt-1">Choose an execution from the left to inspect step details.</p>
            </div>
          )}
        </div>
      </div>

      {/* Confirmation Modal to Trigger EOD */}
      {isTriggerModalOpen && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-4">
            <div className="w-12 h-12 rounded-full bg-[#EFF6FF] text-[#2563EB] mx-auto flex items-center justify-center mb-1">
              <PlayCircle className="w-6 h-6" />
            </div>

            <div className="text-center space-y-1">
              <h3 className="text-base font-bold text-[#1E3A5F]">
                Trigger End of Day Batch Run
              </h3>
              <p className="text-xs text-[#64748B]">
                Initiates automated interest accrual, service fees, standing instructions, and cryptographic seal.
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                Target Business Date
              </label>
              <input
                type="date"
                value={businessDate}
                onChange={(e) => setBusinessDate(e.target.value)}
                className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB]"
              />
            </div>

            <div className="p-3 rounded-lg bg-[#FEF3C7] border border-[#FDE68A] text-xs text-[#92400E]">
              <strong>Note:</strong> End of Day cannot be cancelled once initiated. Tellers will enter read-only ledger reconciliation during active execution.
            </div>

            <div className="flex space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setIsTriggerModalOpen(false)}
                className="flex-1 py-2 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleTriggerEod}
                disabled={triggering}
                className="flex-1 py-2 px-4 rounded-lg bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold disabled:opacity-50"
              >
                {triggering ? 'Initiating Batch...' : 'Confirm & Execute'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
