// ============================================================================
// Batch Service — EOD batch monitoring and triggering
// ============================================================================
import type { BatchRunDetail, BatchRunDetails, BatchTriggerResponse, DailyAuditSealResponse } from '../../types';
import { coreClient } from '../client';
import { SEED_EOD_BATCH_RUNS, SEED_BATCH_RUNS, SEED_BATCH_STEPS } from '../seeds';
import { getDailySealsMap } from './audit';

let localBatchRuns: BatchRunDetails[] = [...SEED_BATCH_RUNS];
let localEodRuns: BatchRunDetail[] = [...SEED_EOD_BATCH_RUNS];

export async function getBatchRuns(): Promise<BatchRunDetails[]> {
  try {
    const res = await fetch('/api/v1/batch/eod/runs', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        if (data[0].batchRunId !== undefined) {
          return data.map((d: any) => ({
            runId: d.batchRunId,
            batchJobName: 'EOD_SETTLEMENT_AND_INTEREST_ACCRUAL',
            status: d.status,
            startTime: d.startedAt,
            endTime: d.finishedAt,
            totalRecordsProcessed: d.steps?.reduce((sum: number, s: any) => sum + (s.itemsProcessed || 0), 0) || 0,
            steps: d.steps?.map((s: any) => ({
              stepId: s.stepNo,
              stepName: s.stepName,
              status: s.status,
              recordsProcessed: s.itemsProcessed,
              durationMs: s.durationMs
            })) || []
          }));
        }
        localBatchRuns = data;
        return data;
      }
    }
  } catch { /* fallback */ }
  return localBatchRuns;
}

export async function getAllBatchRuns(): Promise<BatchRunDetail[]> {
  try {
    const res = await fetch('/api/v1/batch/eod/runs', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const list = await res.json();
      if (Array.isArray(list)) {
        localEodRuns = list;
        return list;
      }
    }
  } catch {
    // fallback
  }
  return [...localEodRuns];
}

export async function getBatchRun(batchRunId: number): Promise<BatchRunDetail> {
  try {
    const res = await fetch(`/api/v1/batch/eod/runs/${batchRunId}`, {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      return await res.json();
    }
  } catch {
    // fallback
  }

  const found = localEodRuns.find(r => r.batchRunId === batchRunId);
  if (found) return found;
  throw new Error(`Batch run #${batchRunId} not found`);
}

export async function triggerEodBatch(businessDate?: string): Promise<BatchTriggerResponse> {
  const payload = businessDate ? { businessDate } : {};
  try {
    const res = await fetch('/api/v1/batch/eod/trigger', {
      method: 'POST',
      headers: coreClient.getAuthHeaders(),
      body: JSON.stringify(payload)
    });

    if (res.status === 202) {
      return await res.json();
    } else if (res.status === 409) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || 'An End-of-Day batch execution is currently in progress (BATCH_ALREADY_RUNNING).');
    } else {
      const err: any = await res.json().catch(() => ({}));
      throw new Error(err.message || err.detail || `Failed to trigger EOD batch: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (err.message && !err.message.includes('Failed to fetch') && !err.message.includes('NetworkError')) {
      throw err;
    }
  }

  // Offline / standalone fallback simulation
  const todayStr = new Date().toISOString().split('T')[0];
  const targetDate = businessDate || todayStr;
  if (targetDate > todayStr) {
    throw new Error(`Business date cannot be in the future (requested: ${targetDate}, today: ${todayStr}).`);
  }

  const newId = localEodRuns.length > 0 ? Math.max(...localEodRuns.map(r => r.batchRunId)) + 1 : 1;
  const initialRun: BatchRunDetail = {
    batchRunId: newId,
    businessDate: targetDate,
    status: 'RUNNING',
    triggeredBy: coreClient.getSession()?.userId || 1,
    startedAt: new Date().toISOString(),
    steps: [
      { stepNo: 1, stepName: 'INTEREST_ACCRUAL', status: 'RUNNING', itemsProcessed: 0, durationMs: 0 },
      { stepNo: 2, stepName: 'SERVICE_FEES', status: 'PENDING', itemsProcessed: 0, durationMs: 0 },
      { stepNo: 3, stepName: 'STANDING_INSTRUCTIONS', status: 'PENDING', itemsProcessed: 0, durationMs: 0 },
      { stepNo: 4, stepName: 'LOAN_AMORTIZATION', status: 'PENDING', itemsProcessed: 0, durationMs: 0 },
      { stepNo: 5, stepName: 'GL_RECONCILIATION', status: 'PENDING', itemsProcessed: 0, durationMs: 0 },
      { stepNo: 6, stepName: 'AUDIT_SEAL', status: 'PENDING', itemsProcessed: 0, durationMs: 0 }
    ]
  };
  localEodRuns.unshift(initialRun);

  setTimeout(() => { initialRun.steps[0] = { ...SEED_BATCH_STEPS[0], status: 'COMPLETED', message: 'Processed interest accrual' }; initialRun.steps[1].status = 'RUNNING'; }, 1200);
  setTimeout(() => { initialRun.steps[1] = { ...SEED_BATCH_STEPS[1], status: 'COMPLETED', message: 'Applied maintenance fees' }; initialRun.steps[2].status = 'RUNNING'; }, 2400);
  setTimeout(() => { initialRun.steps[2] = { ...SEED_BATCH_STEPS[2], status: 'COMPLETED', message: 'Executed recurring transfers' }; initialRun.steps[3].status = 'RUNNING'; }, 3600);
  setTimeout(() => { initialRun.steps[3] = { ...SEED_BATCH_STEPS[3], status: 'COMPLETED', message: 'Processed amortization deductions' }; initialRun.steps[4].status = 'RUNNING'; }, 4800);
  setTimeout(() => { initialRun.steps[4] = { ...SEED_BATCH_STEPS[4], status: 'COMPLETED', message: 'Debits equal credits' }; initialRun.steps[5].status = 'RUNNING'; }, 6000);
  setTimeout(() => {
    initialRun.steps[5] = { ...SEED_BATCH_STEPS[5], status: 'COMPLETED', message: 'Daily cryptographic seal recorded' };
    initialRun.status = 'COMPLETED';
    initialRun.finishedAt = new Date().toISOString();

    const seal: DailyAuditSealResponse = {
      businessDate: targetDate,
      lastAuditId: 48 + newId,
      sealHash: 'a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8',
      sealedAt: initialRun.finishedAt!,
      batchRunId: newId
    };
    getDailySealsMap().set(targetDate, seal);
  }, 7200);

  return { batchRunId: newId, businessDate: targetDate, status: 'RUNNING', startedAt: initialRun.startedAt };
}
