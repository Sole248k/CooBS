import React, { useState, useEffect, useRef } from 'react';
import {
  Play,
  RotateCcw,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  Clock,
  ShieldCheck,
  Fingerprint,
  TrendingUp,
  Receipt,
  Repeat,
  CreditCard,
  Scale,
  Copy,
  Check,
  RefreshCw,
  Sparkles,
  Calendar,
  Layers,
  ArrowRight
} from 'lucide-react';
import type {
  BatchRunDetail,
  BatchStepItem,
  BatchStepStatus,
  DailyAuditSealResponse
} from '../types';
import { apiClient } from '../services/api';

interface EodBatchViewProps {
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
}

const STEP_METADATA = [
  {
    stepNo: 1,
    stepName: 'INTEREST_ACCRUAL',
    title: 'Interest Accrual Calculation',
    description: 'Accrues daily interest on active savings and time deposit accounts',
    icon: TrendingUp,
    accentColor: '#3b82f6',
    bgColor: '#eff6ff'
  },
  {
    stepNo: 2,
    stepName: 'SERVICE_FEES',
    title: 'Service & Maintenance Fees',
    description: 'Applies monthly account maintenance, dormancy, and service charges',
    icon: Receipt,
    accentColor: '#8b5cf6',
    bgColor: '#f5f3ff'
  },
  {
    stepNo: 3,
    stepName: 'STANDING_INSTRUCTIONS',
    title: 'Standing Instructions Execution',
    description: 'Executes pre-scheduled automated recurring fund transfers and billings',
    icon: Repeat,
    accentColor: '#06b6d4',
    bgColor: '#ecfeff'
  },
  {
    stepNo: 4,
    stepName: 'LOAN_AMORTIZATION',
    title: 'Loan Amortization Repayments',
    description: 'Deducts due principal and interest installments for active loan schedules',
    icon: CreditCard,
    accentColor: '#f59e0b',
    bgColor: '#fffbeb'
  },
  {
    stepNo: 5,
    stepName: 'GL_RECONCILIATION',
    title: 'General Ledger Trial Balance',
    description: 'Double-entry audit verification: ensures total debits exactly equal credits',
    icon: Scale,
    accentColor: '#10b981',
    bgColor: '#ecfdf5'
  },
  {
    stepNo: 6,
    stepName: 'AUDIT_SEAL',
    title: 'Cryptographic Audit Seal',
    description: 'Generates immutable daily SHA-256 seal anchored to PostgreSQL audit store',
    icon: Fingerprint,
    accentColor: '#6366f1',
    bgColor: '#eef2ff'
  }
];

export const EodBatchView: React.FC<EodBatchViewProps> = ({ showToast }) => {
  const [businessDate, setBusinessDate] = useState<string>(() => {
    return new Date().toISOString().split('T')[0];
  });

  const [activeRun, setActiveRun] = useState<BatchRunDetail | null>(null);
  const [allRuns, setAllRuns] = useState<BatchRunDetail[]>([]);
  const [dailySeal, setDailySeal] = useState<DailyAuditSealResponse | null>(null);
  const [isTriggering, setIsTriggering] = useState<boolean>(false);
  const [isLoadingRuns, setIsLoadingRuns] = useState<boolean>(false);
  const [copiedHash, setCopiedHash] = useState<boolean>(false);
  const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);

  const pollingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const elapsedTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Load past runs on initial mount
  const loadRuns = async () => {
    setIsLoadingRuns(true);
    try {
      const runs = await apiClient.getAllBatchRuns();
      setAllRuns(runs);
      if (runs.length > 0 && !activeRun) {
        // Load latest run as current view
        setActiveRun(runs[0]);
        loadSealForDate(runs[0].businessDate);
      }
    } catch {
      // Fallback handled in client
    } finally {
      setIsLoadingRuns(false);
    }
  };

  const loadSealForDate = async (date: string) => {
    try {
      const seal = await apiClient.getDailyAuditSeal(date);
      setDailySeal(seal);
    } catch {
      setDailySeal(null);
    }
  };

  useEffect(() => {
    loadRuns();
    return () => {
      if (pollingTimerRef.current) clearInterval(pollingTimerRef.current);
      if (elapsedTimerRef.current) clearInterval(elapsedTimerRef.current);
    };
  }, []);

  // Timer loop for elapsed seconds during RUNNING
  useEffect(() => {
    if (activeRun?.status === 'RUNNING') {
      const startTime = activeRun.startedAt ? new Date(activeRun.startedAt).getTime() : Date.now();
      elapsedTimerRef.current = setInterval(() => {
        const secs = Math.max(0, (Date.now() - startTime) / 1000);
        setElapsedSeconds(Number(secs.toFixed(1)));
      }, 100);
    } else {
      if (elapsedTimerRef.current) {
        clearInterval(elapsedTimerRef.current);
        elapsedTimerRef.current = null;
      }
      if (activeRun?.startedAt && activeRun?.finishedAt) {
        const duration = (new Date(activeRun.finishedAt).getTime() - new Date(activeRun.startedAt).getTime()) / 1000;
        setElapsedSeconds(Number(duration.toFixed(1)));
      }
    }

    return () => {
      if (elapsedTimerRef.current) clearInterval(elapsedTimerRef.current);
    };
  }, [activeRun?.status, activeRun?.startedAt, activeRun?.finishedAt]);

  // Polling loop when activeRun is RUNNING
  useEffect(() => {
    if (activeRun && activeRun.status === 'RUNNING') {
      pollingTimerRef.current = setInterval(async () => {
        try {
          const updated = await apiClient.getBatchRun(activeRun.batchRunId);
          setActiveRun(updated);

          if (updated.status === 'COMPLETED' || updated.status === 'HALTED') {
            if (pollingTimerRef.current) {
              clearInterval(pollingTimerRef.current);
              pollingTimerRef.current = null;
            }
            // Refresh run table
            const runs = await apiClient.getAllBatchRuns();
            setAllRuns(runs);

            if (updated.status === 'COMPLETED') {
              const dur = updated.startedAt && updated.finishedAt
                ? ((new Date(updated.finishedAt).getTime() - new Date(updated.startedAt).getTime()) / 1000).toFixed(1)
                : '5.0';
              showToast('success', `EOD Batch Run #${updated.batchRunId} completed successfully in ~${dur}s.`);
              loadSealForDate(updated.businessDate);
            } else {
              showToast('error', `EOD Batch Run #${updated.batchRunId} halted due to step failure.`);
            }
          }
        } catch {
          // Keep polling or let timeout handle
        }
      }, 750);
    } else {
      if (pollingTimerRef.current) {
        clearInterval(pollingTimerRef.current);
        pollingTimerRef.current = null;
      }
    }

    return () => {
      if (pollingTimerRef.current) clearInterval(pollingTimerRef.current);
    };
  }, [activeRun?.batchRunId, activeRun?.status]);

  // Trigger Action
  const handleTriggerBatch = async () => {
    if (activeRun?.status === 'RUNNING') {
      showToast('error', 'A batch execution is already actively running. Please wait for completion.');
      return;
    }

    const todayStr = new Date().toISOString().split('T')[0];
    if (businessDate > todayStr) {
      showToast('error', `Settlement rejected: ${businessDate} is in the future. Core banking EOD only permits settlement for current or past dates.`);
      return;
    }

    setIsTriggering(true);
    setDailySeal(null);
    setElapsedSeconds(0);

    try {
      const response = await apiClient.triggerEodBatch(businessDate);
      showToast('info', `EOD Settlement initiated for ${response.businessDate} (Run #${response.batchRunId}). Pipeline running in background...`);

      // Immediately fetch new run details
      const newRun = await apiClient.getBatchRun(response.batchRunId);
      setActiveRun(newRun);

      // Refresh runs list
      loadRuns();
    } catch (err: any) {
      showToast('error', err.message || 'Failed to trigger End-of-Day batch execution.');
    } finally {
      setIsTriggering(false);
    }
  };

  // Reset demo view
  const handleResetDemo = () => {
    if (activeRun?.status === 'RUNNING') {
      showToast('info', 'Cannot reset while batch execution is in progress.');
      return;
    }
    setElapsedSeconds(0);
    const todayStr = new Date().toISOString().split('T')[0];
    setBusinessDate(todayStr);
    showToast('info', "Settlement target reset to today's active business date.");
  };

  const handleCopyHash = (hash: string) => {
    navigator.clipboard.writeText(hash);
    setCopiedHash(true);
    showToast('success', 'Cryptographic SHA-256 seal hash copied to clipboard.');
    setTimeout(() => setCopiedHash(false), 2500);
  };

  const handleInspectRun = async (run: BatchRunDetail) => {
    setActiveRun(run);
    setBusinessDate(run.businessDate);
    await loadSealForDate(run.businessDate);
    showToast('info', `Inspecting Batch Run #${run.batchRunId} (${run.businessDate}).`);
  };

  // Operational Governance: Date Gap & Backlog Catch-Up Detection
  const completedRuns = allRuns.filter(r => r.status === 'COMPLETED');
  const latestCompletedDateStr = completedRuns.reduce<string | null>((latest, r) => {
    if (!latest || r.businessDate > latest) return r.businessDate;
    return latest;
  }, null);

  const todayStr = new Date().toISOString().split('T')[0];
  const missingDates: string[] = [];

  if (latestCompletedDateStr && latestCompletedDateStr < todayStr) {
    const latestDate = new Date(latestCompletedDateStr + 'T00:00:00');
    const todayDate = new Date(todayStr + 'T00:00:00');
    const diffTime = todayDate.getTime() - latestDate.getTime();
    const diffDays = Math.round(diffTime / (1000 * 3600 * 24));

    if (diffDays >= 2) {
      const curr = new Date(latestDate);
      curr.setDate(curr.getDate() + 1);
      while (curr < todayDate && missingDates.length < 5) {
        missingDates.push(curr.toISOString().split('T')[0]);
        curr.setDate(curr.getDate() + 1);
      }
    }
  }

  // Helper to get step status badge
  const renderStepStatus = (status: BatchStepStatus) => {
    switch (status) {
      case 'COMPLETED':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '3px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: '600',
            background: '#ecfdf5',
            color: '#059669',
            border: '1px solid #a7f3d0'
          }}>
            <CheckCircle2 size={12} />
            COMPLETED
          </span>
        );
      case 'RUNNING':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '5px',
            padding: '3px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: '600',
            background: '#eff6ff',
            color: '#2563eb',
            border: '1px solid #bfdbfe'
          }}>
            <span style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              background: '#2563eb',
              animation: 'ping 1s cubic-bezier(0, 0, 0.2, 1) infinite'
            }} />
            RUNNING
          </span>
        );
      case 'FAILED':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '3px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: '600',
            background: '#fef2f2',
            color: '#dc2626',
            border: '1px solid #fecaca'
          }}>
            <AlertCircle size={12} />
            FAILED
          </span>
        );
      case 'SKIPPED':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '3px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: '600',
            background: '#f1f5f9',
            color: '#64748b',
            border: '1px solid #cbd5e1'
          }}>
            SKIPPED
          </span>
        );
      case 'PENDING':
      default:
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '3px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: '600',
            background: '#f8fafc',
            color: '#94a3b8',
            border: '1px solid #e2e8f0'
          }}>
            PENDING
          </span>
        );
    }
  };

  const getRunStatusBadge = (status?: string) => {
    switch (status) {
      case 'COMPLETED':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 12px',
            borderRadius: '9999px',
            fontSize: '12px',
            fontWeight: '700',
            background: '#ecfdf5',
            color: '#059669',
            border: '1px solid #6ee7b7'
          }}>
            <CheckCircle2 size={14} />
            PIPELINE COMPLETED
          </span>
        );
      case 'RUNNING':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 12px',
            borderRadius: '9999px',
            fontSize: '12px',
            fontWeight: '700',
            background: '#eff6ff',
            color: '#2563eb',
            border: '1px solid #93c5fd'
          }}>
            <span style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              background: '#2563eb',
              boxShadow: '0 0 8px #2563eb'
            }} />
            PROCESSING (IN PROGRESS)
          </span>
        );
      case 'HALTED':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 12px',
            borderRadius: '9999px',
            fontSize: '12px',
            fontWeight: '700',
            background: '#fef2f2',
            color: '#dc2626',
            border: '1px solid #fca5a5'
          }}>
            <AlertTriangle size={14} />
            HALTED (VARIANCE DETECTED)
          </span>
        );
      default:
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 12px',
            borderRadius: '9999px',
            fontSize: '12px',
            fontWeight: '700',
            background: '#f8fafc',
            color: '#64748b',
            border: '1px solid #cbd5e1'
          }}>
            IDLE / READY
          </span>
        );
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '28px' }}>
      {/* 1. Header & Execution Control Deck */}
      <div style={{
        background: '#ffffff',
        border: '1px solid #e2e8f0',
        borderRadius: '16px',
        padding: '24px',
        boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)'
      }}>
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '16px',
          paddingBottom: '20px',
          borderBottom: '1px solid #f1f5f9'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
              <span style={{
                background: '#eff6ff',
                color: '#2563eb',
                padding: '6px',
                borderRadius: '8px',
                display: 'inline-flex'
              }}>
                <Sparkles size={20} />
              </span>
              <h2 style={{ fontSize: '20px', fontWeight: '700', color: '#0f172a', margin: 0 }}>
                End-of-Day (EOD) Settlement Pipeline
              </h2>
              {getRunStatusBadge(activeRun?.status)}
            </div>
            <p style={{ margin: 0, fontSize: '13px', color: '#64748b', maxWidth: '680px' }}>
              Autonomous 6-stage core banking settlement: accrued interest calculation, service fees,
              standing orders, loan repayments, double-entry GL trial balance verification, and daily cryptographic audit sealing.
            </p>
          </div>

          {/* Runtime Metrics Box */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            background: '#f8fafc',
            padding: '12px 18px',
            borderRadius: '12px',
            border: '1px solid #e2e8f0'
          }}>
            <div>
              <div style={{ fontSize: '11px', fontWeight: '600', color: '#64748b', textTransform: 'uppercase' }}>
                Pipeline Elapsed
              </div>
              <div style={{
                fontSize: '18px',
                fontWeight: '700',
                fontFamily: 'JetBrains Mono, monospace',
                color: activeRun?.status === 'RUNNING' ? '#2563eb' : '#0f172a'
              }}>
                {elapsedSeconds.toFixed(1)}s
              </div>
            </div>
            <div style={{ width: '1px', height: '28px', background: '#cbd5e1' }} />
            <div>
              <div style={{ fontSize: '11px', fontWeight: '600', color: '#64748b', textTransform: 'uppercase' }}>
                Target SLA
              </div>
              <div style={{ fontSize: '14px', fontWeight: '600', color: '#059669' }}>
                5 – 8s Target
              </div>
            </div>
            {latestCompletedDateStr && (
              <>
                <div style={{ width: '1px', height: '28px', background: '#cbd5e1' }} />
                <div>
                  <div style={{ fontSize: '11px', fontWeight: '600', color: '#64748b', textTransform: 'uppercase' }}>
                    Ledger Continuity
                  </div>
                  <div style={{
                    fontSize: '12px',
                    fontWeight: '700',
                    color: missingDates.length > 0 ? '#d97706' : '#059669',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px'
                  }}>
                    {missingDates.length > 0 ? (
                      <>
                        <AlertTriangle size={13} />
                        <span>Gap ({missingDates[0]})</span>
                      </>
                    ) : (
                      <>
                        <ShieldCheck size={14} />
                        <span>Closed to {latestCompletedDateStr}</span>
                      </>
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Action Controls Bar */}
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '16px',
          marginTop: '20px'
        }}>
          {/* Business Date Selector */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <label htmlFor="input-business-date" style={{ fontSize: '13px', fontWeight: '600', color: '#334155', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Calendar size={15} color="#64748b" />
              Target Business Date:
            </label>
            <input
              id="input-business-date"
              type="date"
              value={businessDate}
              max={new Date().toISOString().split('T')[0]}
              onChange={(e) => {
                const val = e.target.value;
                const todayStr = new Date().toISOString().split('T')[0];
                if (val > todayStr) {
                  showToast('error', `Cannot select future date ${val}. Settlement is strictly restricted to current or past dates.`);
                  return;
                }
                setBusinessDate(val);
              }}
              disabled={activeRun?.status === 'RUNNING'}
              style={{
                padding: '8px 12px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '13px',
                fontFamily: 'JetBrains Mono, monospace',
                fontWeight: '600',
                color: '#0f172a',
                outline: 'none',
                background: '#ffffff'
              }}
            />
            <button
              type="button"
              id="btn-date-today"
              onClick={() => setBusinessDate(new Date().toISOString().split('T')[0])}
              disabled={activeRun?.status === 'RUNNING'}
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
                background: '#f8fafc',
                fontSize: '12px',
                fontWeight: '600',
                color: '#475569',
                cursor: 'pointer'
              }}
              title="Set to today's active business date"
            >
              Today
            </button>
            <button
              type="button"
              id="btn-date-yesterday"
              onClick={() => {
                const d = new Date();
                d.setDate(d.getDate() - 1);
                setBusinessDate(d.toISOString().split('T')[0]);
              }}
              disabled={activeRun?.status === 'RUNNING'}
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
                background: '#f8fafc',
                fontSize: '12px',
                fontWeight: '600',
                color: '#475569',
                cursor: 'pointer'
              }}
              title="Set to yesterday for retroactive backlog settlement"
            >
              Yesterday (Backlog)
            </button>
          </div>

          {/* Action CTAs */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <button
              type="button"
              id="btn-reset-demo"
              onClick={handleResetDemo}
              disabled={activeRun?.status === 'RUNNING'}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 16px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                color: '#475569',
                fontSize: '13px',
                fontWeight: '600',
                cursor: activeRun?.status === 'RUNNING' ? 'not-allowed' : 'pointer'
              }}
              title="Resets local target state to allow repeated presentation demonstrations"
            >
              <RotateCcw size={15} />
              Reset Demo State
            </button>

            <button
              type="button"
              id="btn-trigger-eod"
              onClick={handleTriggerBatch}
              disabled={isTriggering || activeRun?.status === 'RUNNING'}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 22px',
                borderRadius: '8px',
                border: 'none',
                background: activeRun?.status === 'RUNNING'
                  ? '#93c5fd'
                  : 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
                color: '#ffffff',
                fontSize: '13px',
                fontWeight: '700',
                boxShadow: '0 2px 4px rgba(37, 99, 235, 0.25)',
                cursor: (isTriggering || activeRun?.status === 'RUNNING') ? 'not-allowed' : 'pointer',
                transition: 'all 0.2s ease'
              }}
            >
              {activeRun?.status === 'RUNNING' ? (
                <>
                  <RefreshCw size={16} className="animate-spin" />
                  Executing Settlement ({elapsedSeconds.toFixed(0)}s)...
                </>
              ) : (
                <>
                  <Play size={16} fill="white" />
                  Execute End-of-Day Batch
                </>
              )}
            </button>
          </div>
        </div>

        {/* Informative Security Notice */}
        <div style={{
          marginTop: '16px',
          background: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          padding: '10px 14px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          fontSize: '12px',
          color: '#1e40af'
        }}>
          <ShieldCheck size={16} style={{ flexShrink: 0 }} />
          <span>
            <strong>Production Governance (Contract B1):</strong> Triggering is restricted to Branch Managers (<code style={{ fontFamily: 'JetBrains Mono, monospace' }}>ROLE_ADMIN</code>).
            Settlement is restricted to <strong>Today or Past Backlog</strong> dates (future dates rejected). Duplicate concurrent executions are locked with <code style={{ fontFamily: 'JetBrains Mono, monospace' }}>HTTP 409 Conflict</code>.
          </span>
        </div>
      </div>

      {/* 1B. Operational Governance Warning: Missed EOD / Date Gap Banner */}
      {missingDates.length > 0 && (
        <div style={{
          background: 'linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)',
          border: '1px solid #fcd34d',
          borderRadius: '16px',
          padding: '20px 24px',
          boxShadow: '0 4px 12px rgba(245, 158, 11, 0.08)',
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
          animation: 'fadeIn 0.3s ease-in-out'
        }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', flex: 1, minWidth: '300px' }}>
              <div style={{
                width: '42px',
                height: '42px',
                borderRadius: '10px',
                background: '#fef3c7',
                border: '1px solid #fde68a',
                color: '#d97706',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <AlertTriangle size={22} />
              </div>

              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                  <h3 style={{ fontSize: '16px', fontWeight: '800', color: '#92400e', margin: 0 }}>
                    Operational Gap Detected &mdash; Unclosed Historical Ledger
                  </h3>
                  <span style={{
                    background: '#fef3c7',
                    border: '1px solid #f59e0b',
                    color: '#b45309',
                    fontSize: '11px',
                    fontWeight: '700',
                    padding: '2px 8px',
                    borderRadius: '9999px',
                    textTransform: 'uppercase'
                  }}>
                    Core Banking Governance Rule
                  </span>
                </div>

                <p style={{ fontSize: '13px', color: '#78350f', margin: '6px 0 0 0', lineHeight: 1.5, maxWidth: '780px' }}>
                  The last verified completed EOD closure was for <strong style={{ fontFamily: 'JetBrains Mono, monospace' }}>{latestCompletedDateStr}</strong>.
                  Standard financial accounting requires business days to be closed in <strong>strict chronological sequence</strong>.
                  Closing today (<span style={{ fontFamily: 'JetBrains Mono, monospace' }}>{todayStr}</span>) without settling historical backlog will cause accrued interest and loan repayment gaps.
                </p>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '12px', flexWrap: 'wrap' }}>
                  <span style={{ fontSize: '12px', fontWeight: '700', color: '#92400e' }}>
                    Next Sequential Date Due:
                  </span>
                  <span style={{
                    fontFamily: 'JetBrains Mono, monospace',
                    fontWeight: '800',
                    fontSize: '13px',
                    background: '#ffffff',
                    padding: '3px 10px',
                    borderRadius: '6px',
                    border: '1px solid #fde68a',
                    color: '#b45309'
                  }}>
                    {missingDates[0]}
                  </span>
                  {missingDates.length > 1 && (
                    <span style={{ fontSize: '12px', color: '#b45309', fontWeight: '500' }}>
                      (+{missingDates.length - 1} subsequent unclosed day{missingDates.length > 2 ? 's' : ''})
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <button
                type="button"
                id="btn-catchup-backlog"
                onClick={() => {
                  setBusinessDate(missingDates[0]);
                  showToast('info', `Target business date set to backlog date ${missingDates[0]}. Click "Execute End-of-Day Batch" to run.`);
                }}
                disabled={activeRun?.status === 'RUNNING'}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '10px 18px',
                  borderRadius: '8px',
                  background: 'linear-gradient(135deg, #d97706 0%, #b45309 100%)',
                  color: '#ffffff',
                  border: 'none',
                  fontSize: '13px',
                  fontWeight: '700',
                  cursor: activeRun?.status === 'RUNNING' ? 'not-allowed' : 'pointer',
                  boxShadow: '0 2px 6px rgba(217, 119, 6, 0.3)',
                  transition: 'all 0.15s ease'
                }}
                title="Automatically sets Target Date to the oldest unclosed business date"
              >
                <span>Run Catch-Up ({missingDates[0]})</span>
                <ArrowRight size={15} />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2. 6-Step Visual Pipeline Stepper Cards */}
      <div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '16px'
        }}>
          <div>
            <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={18} color="#2563eb" />
              Settlement Pipeline Execution Steps (1 through 6)
            </h3>
            <span style={{ fontSize: '13px', color: '#64748b' }}>
              {activeRun ? `Currently displaying Run #${activeRun.batchRunId} for business date ${activeRun.businessDate}` : 'Ready for execution'}
            </span>
          </div>

          {activeRun && (
            <div style={{ fontSize: '12px', color: '#64748b' }}>
              Run ID: <strong style={{ color: '#0f172a' }}>#{activeRun.batchRunId}</strong> | Triggered By: Branch Manager #{activeRun.triggeredBy || 1}
            </div>
          )}
        </div>

        {/* Pipeline Step Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: '16px'
        }}>
          {STEP_METADATA.map((meta) => {
            const stepData: BatchStepItem | undefined = activeRun?.steps?.find(s => s.stepNo === meta.stepNo);
            const status: BatchStepStatus = stepData?.status || 'PENDING';
            const IconComponent = meta.icon;

            const isCardActive = status === 'RUNNING';
            const isCardFailed = status === 'FAILED';
            const isCardCompleted = status === 'COMPLETED';

            return (
              <div
                key={meta.stepNo}
                id={`batch-step-card-${meta.stepNo}`}
                style={{
                  background: '#ffffff',
                  borderRadius: '12px',
                  border: isCardActive
                    ? '2px solid #2563eb'
                    : isCardFailed
                    ? '2px solid #ef4444'
                    : isCardCompleted
                    ? '1px solid #86efac'
                    : '1px solid #e2e8f0',
                  boxShadow: isCardActive
                    ? '0 4px 14px 0 rgba(37, 99, 235, 0.15)'
                    : '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
                  padding: '18px',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  transition: 'all 0.2s ease',
                  position: 'relative',
                  overflow: 'hidden'
                }}
              >
                {/* Top strip for visual accent */}
                <div style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  height: '4px',
                  background: isCardActive
                    ? '#2563eb'
                    : isCardFailed
                    ? '#ef4444'
                    : isCardCompleted
                    ? '#10b981'
                    : '#cbd5e1'
                }} />

                <div>
                  {/* Step Header */}
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '8px', marginBottom: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <div style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '8px',
                        background: meta.bgColor,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: meta.accentColor
                      }}>
                        <IconComponent size={18} />
                      </div>
                      <div>
                        <div style={{ fontSize: '11px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase' }}>
                          Step {meta.stepNo} of 6
                        </div>
                        <h4 style={{ fontSize: '14px', fontWeight: '700', color: '#0f172a', margin: 0 }}>
                          {meta.title}
                        </h4>
                      </div>
                    </div>
                    {renderStepStatus(status)}
                  </div>

                  <p style={{ fontSize: '12px', color: '#64748b', margin: '0 0 14px 0', lineHeight: 1.4 }}>
                    {meta.description}
                  </p>
                </div>

                {/* Metrics / Diagnostics Row */}
                <div style={{
                  background: '#f8fafc',
                  border: '1px solid #f1f5f9',
                  borderRadius: '8px',
                  padding: '10px 12px',
                  marginTop: 'auto'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                    <span style={{ color: '#64748b' }}>Items Processed:</span>
                    <strong style={{ color: '#0f172a', fontFamily: 'JetBrains Mono, monospace' }}>
                      {stepData ? `${stepData.itemsProcessed} records` : '—'}
                    </strong>
                  </div>

                  {stepData?.totalAmount && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                      <span style={{ color: '#64748b' }}>Volume / Balance:</span>
                      <strong style={{ color: '#059669', fontFamily: 'JetBrains Mono, monospace' }}>
                        PHP {stepData.totalAmount}
                      </strong>
                    </div>
                  )}

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                    <span style={{ color: '#64748b' }}>Latency:</span>
                    <span style={{ color: '#475569', fontFamily: 'JetBrains Mono, monospace', fontSize: '11px' }}>
                      {stepData?.durationMs !== undefined ? `${stepData.durationMs}ms` : '—'}
                    </span>
                  </div>

                  {/* Message Banner */}
                  {stepData?.message && (
                    <div style={{
                      marginTop: '8px',
                      paddingTop: '6px',
                      borderTop: '1px dashed #e2e8f0',
                      fontSize: '11px',
                      color: isCardFailed ? '#dc2626' : '#475569',
                      fontWeight: isCardFailed ? '600' : 'normal',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px'
                    }}>
                      {isCardFailed ? <AlertCircle size={12} color="#dc2626" /> : <ArrowRight size={12} color="#2563eb" />}
                      <span>{stepData.message}</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 3. Cryptographic Daily Audit Seal Certificate */}
      {dailySeal && (
        <div
          id="audit-seal-certificate-card"
          style={{
            background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
            borderRadius: '16px',
            color: '#ffffff',
            padding: '24px 28px',
            boxShadow: '0 10px 25px -5px rgba(15, 23, 42, 0.3)',
            border: '1px solid #334155',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          {/* Decorative Security Background Emblem */}
          <div style={{
            position: 'absolute',
            right: '-20px',
            bottom: '-30px',
            opacity: 0.06,
            pointerEvents: 'none'
          }}>
            <Fingerprint size={240} />
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: '16px', marginBottom: '18px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{
                background: 'rgba(34, 197, 94, 0.2)',
                border: '1px solid #22c55e',
                color: '#4ade80',
                padding: '8px',
                borderRadius: '10px'
              }}>
                <ShieldCheck size={24} />
              </div>
              <div>
                <div style={{ fontSize: '11px', fontWeight: '700', letterSpacing: '0.08em', color: '#94a3b8', textTransform: 'uppercase' }}>
                  Forensic Security & Compliance — Section 11 / Contract U4
                </div>
                <h3 style={{ fontSize: '18px', fontWeight: '700', margin: 0, color: '#f8fafc' }}>
                  Daily Cryptographic Audit Seal Certificate
                </h3>
              </div>
            </div>

            <span style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 14px',
              borderRadius: '9999px',
              fontSize: '12px',
              fontWeight: '700',
              background: 'rgba(34, 197, 94, 0.15)',
              border: '1px solid rgba(74, 222, 128, 0.4)',
              color: '#4ade80'
            }}>
              <CheckCircle2 size={14} />
              CRYPTOGRAPHICALLY SEALED & ANCHORED
            </span>
          </div>

          {/* Certificate Detail Grid */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '16px',
            marginBottom: '20px'
          }}>
            <div style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '12px 14px', borderRadius: '10px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
              <div style={{ fontSize: '11px', color: '#94a3b8', marginBottom: '4px', textTransform: 'uppercase' }}>Business Date</div>
              <div style={{ fontSize: '15px', fontWeight: '700', fontFamily: 'JetBrains Mono, monospace', color: '#ffffff' }}>
                {dailySeal.businessDate}
              </div>
            </div>

            <div style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '12px 14px', borderRadius: '10px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
              <div style={{ fontSize: '11px', color: '#94a3b8', marginBottom: '4px', textTransform: 'uppercase' }}>Ledger Mutation Anchor</div>
              <div style={{ fontSize: '15px', fontWeight: '700', fontFamily: 'JetBrains Mono, monospace', color: '#38bdf8' }}>
                Audit Record #{dailySeal.lastAuditId}
              </div>
            </div>

            <div style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '12px 14px', borderRadius: '10px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
              <div style={{ fontSize: '11px', color: '#94a3b8', marginBottom: '4px', textTransform: 'uppercase' }}>Sealed Timestamp (UTC)</div>
              <div style={{ fontSize: '13px', fontWeight: '600', fontFamily: 'JetBrains Mono, monospace', color: '#e2e8f0' }}>
                {new Date(dailySeal.sealedAt).toLocaleString()}
              </div>
            </div>

            {dailySeal.batchRunId && (
              <div style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '12px 14px', borderRadius: '10px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                <div style={{ fontSize: '11px', color: '#94a3b8', marginBottom: '4px', textTransform: 'uppercase' }}>Batch Run Origin</div>
                <div style={{ fontSize: '15px', fontWeight: '700', fontFamily: 'JetBrains Mono, monospace', color: '#a78bfa' }}>
                  Run #{dailySeal.batchRunId}
                </div>
              </div>
            )}
          </div>

          {/* SHA-256 Hash Display Box */}
          <div style={{
            background: 'rgba(0, 0, 0, 0.4)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: '10px',
            padding: '12px 16px',
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0, flex: 1 }}>
              <span style={{ fontSize: '11px', fontWeight: '700', color: '#67e8f9', textTransform: 'uppercase', flexShrink: 0 }}>
                SHA-256 Seal Hash:
              </span>
              <code style={{
                fontFamily: 'JetBrains Mono, monospace',
                fontSize: '12px',
                color: '#f1f5f9',
                wordBreak: 'break-all',
                background: 'transparent'
              }}>
                {dailySeal.sealHash}
              </code>
            </div>

            <button
              type="button"
              id="btn-copy-seal-hash"
              onClick={() => handleCopyHash(dailySeal.sealHash)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: '6px 12px',
                borderRadius: '6px',
                background: copiedHash ? '#059669' : 'rgba(255, 255, 255, 0.15)',
                border: '1px solid rgba(255, 255, 255, 0.2)',
                color: '#ffffff',
                fontSize: '11px',
                fontWeight: '600',
                cursor: 'pointer',
                flexShrink: 0
              }}
            >
              {copiedHash ? <Check size={13} /> : <Copy size={13} />}
              {copiedHash ? 'Copied' : 'Copy Hash'}
            </button>
          </div>
        </div>
      )}

      {/* 4. Historical Batch Runs Audit Table */}
      <div style={{
        background: '#ffffff',
        border: '1px solid #e2e8f0',
        borderRadius: '16px',
        padding: '24px',
        boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)'
      }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '16px'
        }}>
          <div>
            <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#0f172a', margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Clock size={18} color="#2563eb" />
              Settlement Run History & Audit Trail
            </h3>
            <span style={{ fontSize: '13px', color: '#64748b' }}>
              Historical executions recorded in Oracle XE <code style={{ fontFamily: 'JetBrains Mono, monospace' }}>BATCH_RUN</code> and PostgreSQL <code style={{ fontFamily: 'JetBrains Mono, monospace' }}>daily_audit_seal</code>
            </span>
          </div>

          <button
            type="button"
            id="btn-refresh-runs"
            onClick={loadRuns}
            disabled={isLoadingRuns}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 12px',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              background: '#f8fafc',
              color: '#475569',
              fontSize: '12px',
              fontWeight: '600',
              cursor: 'pointer'
            }}
          >
            <RefreshCw size={13} className={isLoadingRuns ? 'animate-spin' : ''} />
            Refresh Runs
          </button>
        </div>

        {allRuns.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '36px', color: '#94a3b8' }}>
            No past batch runs found. Click "Execute End-of-Day Batch" to begin the first settlement.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #f1f5f9', color: '#64748b', fontSize: '12px', textTransform: 'uppercase' }}>
                  <th style={{ padding: '10px 12px', fontWeight: '600' }}>Run ID</th>
                  <th style={{ padding: '10px 12px', fontWeight: '600' }}>Business Date</th>
                  <th style={{ padding: '10px 12px', fontWeight: '600' }}>Status</th>
                  <th style={{ padding: '10px 12px', fontWeight: '600' }}>Steps</th>
                  <th style={{ padding: '10px 12px', fontWeight: '600' }}>Started At</th>
                  <th style={{ padding: '10px 12px', fontWeight: '600' }}>Finished At</th>
                  <th style={{ padding: '10px 12px', fontWeight: '600', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {allRuns.map((run) => {
                  const completedSteps = run.steps?.filter(s => s.status === 'COMPLETED').length || 0;
                  const totalSteps = run.steps?.length || 6;
                  const isCurrent = activeRun?.batchRunId === run.batchRunId;

                  return (
                    <tr
                      key={run.batchRunId}
                      style={{
                        borderBottom: '1px solid #f1f5f9',
                        background: isCurrent ? '#f0f9ff' : 'transparent',
                        transition: 'background 0.15s ease'
                      }}
                    >
                      <td style={{ padding: '12px', fontFamily: 'JetBrains Mono, monospace', fontWeight: '700', color: '#0f172a' }}>
                        #{run.batchRunId}
                      </td>
                      <td style={{ padding: '12px', fontFamily: 'JetBrains Mono, monospace', fontWeight: '600', color: '#334155' }}>
                        {run.businessDate}
                      </td>
                      <td style={{ padding: '12px' }}>
                        {renderStepStatus(run.status as BatchStepStatus)}
                      </td>
                      <td style={{ padding: '12px', color: '#475569' }}>
                        <span style={{ fontWeight: '600' }}>{completedSteps}</span> / {totalSteps}
                      </td>
                      <td style={{ padding: '12px', color: '#64748b', fontSize: '12px', fontFamily: 'JetBrains Mono, monospace' }}>
                        {run.startedAt ? new Date(run.startedAt).toLocaleTimeString() : '—'}
                      </td>
                      <td style={{ padding: '12px', color: '#64748b', fontSize: '12px', fontFamily: 'JetBrains Mono, monospace' }}>
                        {run.finishedAt ? new Date(run.finishedAt).toLocaleTimeString() : '—'}
                      </td>
                      <td style={{ padding: '12px', textAlign: 'right' }}>
                        <button
                          type="button"
                          id={`btn-inspect-run-${run.batchRunId}`}
                          onClick={() => handleInspectRun(run)}
                          style={{
                            padding: '4px 10px',
                            borderRadius: '6px',
                            fontSize: '11px',
                            fontWeight: '600',
                            border: isCurrent ? '1px solid #93c5fd' : '1px solid #cbd5e1',
                            background: isCurrent ? '#2563eb' : '#ffffff',
                            color: isCurrent ? '#ffffff' : '#475569',
                            cursor: 'pointer'
                          }}
                        >
                          {isCurrent ? 'Viewing' : 'Inspect'}
                        </button>
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
  );
};
