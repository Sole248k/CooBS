import React, { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { DeadLetterRecord } from '../../types/staff-bff';
import { formatTimestamp } from '../../lib/utils';
import {
  RotateCcw,
  RefreshCw,
  Code2,
  CheckCircle2,
  ShieldAlert,
  AlertCircle,
  X,
  Info,
  Check,
  Radio,
} from 'lucide-react';

const REPLAY_STORAGE_KEY = 'coobs_replayed_dlq_records';

export const DeadLetterQueue: React.FC = () => {
  const { isAdmin } = useAuth();

  const [records, setRecords] = useState<DeadLetterRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Tab filter: 'active' | 'resolved' | 'all'
  const [filterTab, setFilterTab] = useState<'active' | 'resolved' | 'all'>('active');

  // Replay State persisted in localStorage
  const [replayingId, setReplayingId] = useState<string | null>(null);
  const [replayedMap, setReplayedMap] = useState<Record<string, string>>(() => {
    try {
      const saved = localStorage.getItem(REPLAY_STORAGE_KEY);
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  // Payload JSON Modal
  const [viewingPayload, setViewingPayload] = useState<DeadLetterRecord | null>(null);

  const loadDlqRecords = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await staffApi.getDlqMessages();
      setRecords(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch DLQ records.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isAdmin) {
      loadDlqRecords();
    }
  }, [isAdmin]);

  // Access Denied for Tellers per Section 3 RBAC Matrix
  if (!isAdmin) {
    return (
      <div className="bg-white rounded-2xl border border-[#E2E8F0] p-12 text-center max-w-lg mx-auto mt-12 shadow-sm space-y-4">
        <div className="w-14 h-14 rounded-2xl bg-[#FEE2E2] text-[#DC2626] mx-auto flex items-center justify-center">
          <ShieldAlert className="w-8 h-8" />
        </div>
        <div>
          <h2 className="text-lg font-bold text-[#1E3A5F]">Access Restricted</h2>
          <p className="text-xs text-[#64748B] mt-1">
            Dead Letter Queue (DLQ) diagnostic inspection and Kafka event replays are restricted exclusively to Branch Managers.
          </p>
        </div>
      </div>
    );
  }

  const handleReplay = async (recordId: string) => {
    setReplayingId(recordId);
    setError(null);
    try {
      await staffApi.replayDlqRecord(recordId);
      const timestamp = new Date().toISOString();
      setReplayedMap((prev) => {
        const updated = { ...prev, [recordId]: timestamp };
        try {
          localStorage.setItem(REPLAY_STORAGE_KEY, JSON.stringify(updated));
        } catch {
          // ignore
        }
        return updated;
      });
      setTimeout(() => loadDlqRecords(), 1500);
    } catch (err: any) {
      setError(err.message || `Failed to replay record ${recordId} to Kafka.`);
    } finally {
      setReplayingId(null);
    }
  };

  const isRecordResolved = (rec: DeadLetterRecord) => {
    return Boolean(rec.replayedAt || replayedMap[rec.recordId]);
  };

  const activeRecords = records.filter((r) => !isRecordResolved(r));
  const resolvedRecords = records.filter((r) => isRecordResolved(r));

  const filteredRecords =
    filterTab === 'active'
      ? activeRecords
      : filterTab === 'resolved'
      ? resolvedRecords
      : records;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">Kafka Dead Letter Queue (DLQ)</h1>
          <p className="text-xs text-[#64748B]">
            Diagnose unprocessable asynchronous messages and replay idempotently to Kafka topics
          </p>
        </div>

        <button
          onClick={loadDlqRecords}
          disabled={loading}
          className="px-3.5 py-2 rounded-lg bg-white border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC] transition-colors flex items-center space-x-1.5 self-start sm:self-auto shadow-xs"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh Queue</span>
        </button>
      </div>

      {/* Kafka Stream Explanation Card */}
      <div className="p-4 rounded-xl bg-blue-50/80 border border-blue-200/90 text-xs text-[#1E3A5F] flex items-start space-x-3 shadow-xs">
        <Info className="w-4 h-4 text-blue-600 flex-shrink-0 mt-0.5" />
        <div className="space-y-1">
          <p className="font-semibold text-blue-900">
            Kafka Immutable Event Stream Architecture
          </p>
          <p className="text-slate-600 leading-relaxed text-[11px]">
            Dead Letter Topics (<code className="font-mono bg-blue-100/70 text-blue-800 px-1 py-0.5 rounded">notification.events.DLT</code>) maintain an append-only audit trail for compliance. In Kafka, replayed messages are <strong>not deleted</strong> from the topic log; instead, replaying republishes them to their original consumer topics. Messages that have been replayed and ingested are classified as <strong className="text-emerald-700 font-semibold">Resolved</strong>.
          </p>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="flex items-center space-x-2 border-b border-[#E2E8F0] pb-2">
        <button
          onClick={() => setFilterTab('active')}
          className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center space-x-2 ${
            filterTab === 'active'
              ? 'bg-[#1E3A5F] text-white shadow-xs'
              : 'text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F1F5F9]'
          }`}
        >
          <span>Active Unhandled</span>
          <span
            className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
              filterTab === 'active'
                ? activeRecords.length > 0
                  ? 'bg-rose-500 text-white'
                  : 'bg-white/20 text-white'
                : activeRecords.length > 0
                ? 'bg-rose-100 text-rose-700'
                : 'bg-[#E2E8F0] text-[#64748B]'
            }`}
          >
            {activeRecords.length}
          </span>
        </button>

        <button
          onClick={() => setFilterTab('resolved')}
          className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center space-x-2 ${
            filterTab === 'resolved'
              ? 'bg-[#1E3A5F] text-white shadow-xs'
              : 'text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F1F5F9]'
          }`}
        >
          <span>Resolved / Replayed</span>
          <span
            className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
              filterTab === 'resolved'
                ? 'bg-emerald-500 text-white'
                : 'bg-emerald-100 text-emerald-700'
            }`}
          >
            {resolvedRecords.length}
          </span>
        </button>

        <button
          onClick={() => setFilterTab('all')}
          className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center space-x-2 ${
            filterTab === 'all'
              ? 'bg-[#1E3A5F] text-white shadow-xs'
              : 'text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F1F5F9]'
          }`}
        >
          <span>All Stream Records</span>
          <span
            className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
              filterTab === 'all'
                ? 'bg-white/20 text-white'
                : 'bg-[#E2E8F0] text-[#64748B]'
            }`}
          >
            {records.length}
          </span>
        </button>
      </div>

      {filteredRecords.length === 0 ? (
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-12 text-center text-[#64748B] shadow-xs">
          <CheckCircle2 className="w-10 h-10 mx-auto text-[#10B981] mb-2 opacity-90" />
          <p className="text-sm font-semibold text-[#1E3A5F]">
            {filterTab === 'active'
              ? 'Dead Letter Queue Clean'
              : 'No Records In This View'}
          </p>
          <p className="text-xs mt-1 text-[#64748B]">
            {filterTab === 'active'
              ? 'Zero active or corrupted Kafka stream messages require manual replay.'
              : 'There are no messages matching the selected filter criteria.'}
          </p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-xs border border-[#E2E8F0] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-[#64748B] uppercase tracking-wider">
                  <th className="py-3 px-4">Record ID</th>
                  <th className="py-3 px-4">Topic / Key</th>
                  <th className="py-3 px-4">Failure Reason</th>
                  <th className="py-3 px-4">Status / Attempts</th>
                  <th className="py-3 px-4">Timestamp</th>
                  <th className="py-3 px-4 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E2E8F0]">
                {filteredRecords.map((rec) => {
                  const resolved = isRecordResolved(rec);
                  const isReplaying = replayingId === rec.recordId;

                  return (
                    <tr
                      key={rec.recordId}
                      className={`hover:bg-[#F8FAFC]/70 transition-colors ${
                        resolved ? 'bg-emerald-50/20' : ''
                      }`}
                    >
                      <td className="py-3.5 px-4 font-mono font-bold text-xs text-[#1E3A5F]">
                        {rec.recordId}
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="font-mono text-xs font-semibold text-[#1E3A5F]">
                          {rec.originalTopic}
                        </div>
                        <div className="text-[11px] font-mono text-[#64748B]">
                          Key: {rec.recordKey || 'null'} &bull; DLQ: {rec.deadLetterTopic || 'notification.events.DLT'}
                        </div>
                      </td>

                      <td className="py-3.5 px-4 max-w-xs">
                        {resolved ? (
                          <div>
                            <span className="text-xs text-slate-400 line-through font-mono block">
                              {rec.errorMessage}
                            </span>
                            <span className="text-[11px] font-semibold text-emerald-600 flex items-center space-x-1 mt-0.5">
                              <CheckCircle2 className="w-3 h-3 flex-shrink-0" />
                              <span>Resolved via Replay</span>
                            </span>
                          </div>
                        ) : (
                          <span className="text-xs text-[#DC2626] font-mono line-clamp-2">
                            {rec.errorMessage}
                          </span>
                        )}
                      </td>

                      <td className="py-3.5 px-4">
                        {resolved ? (
                          <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                            <Check className="w-3 h-3 text-emerald-600" />
                            <span>Resolved</span>
                          </span>
                        ) : (
                          <span className="pill pill-frozen text-[10px]">
                            {rec.attempts} / {rec.maxAttempts || 3} Failed
                          </span>
                        )}
                      </td>

                      <td className="py-3.5 px-4 text-xs text-[#64748B]">
                        {formatTimestamp(rec.failedAt)}
                      </td>

                      <td className="py-3.5 px-4 text-center">
                        <div className="flex items-center justify-center space-x-2">
                          <button
                            onClick={() => setViewingPayload(rec)}
                            className="p-1.5 rounded-lg border border-[#CBD5E1] text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F8FAFC] transition-colors"
                            title="Inspect Payload JSON"
                          >
                            <Code2 className="w-3.5 h-3.5" />
                          </button>

                          {resolved ? (
                            <div className="flex items-center space-x-1.5">
                              <span className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-lg text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                                <span>Resolved</span>
                              </span>
                              <button
                                onClick={() => handleReplay(rec.recordId)}
                                disabled={isReplaying}
                                title="Replay again"
                                className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:text-blue-600 hover:border-blue-300 hover:bg-blue-50 transition-colors disabled:opacity-50"
                              >
                                <RotateCcw className={`w-3.5 h-3.5 ${isReplaying ? 'animate-spin' : ''}`} />
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => handleReplay(rec.recordId)}
                              disabled={isReplaying}
                              className="px-3 py-1.5 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-semibold rounded-lg shadow-xs transition-all flex items-center space-x-1 disabled:opacity-50"
                            >
                              <RotateCcw className={`w-3 h-3 ${isReplaying ? 'animate-spin' : ''}`} />
                              <span>{isReplaying ? 'Replaying...' : 'Replay to Kafka'}</span>
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Payload JSON Inspector Modal */}
      {viewingPayload && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-lg w-full p-6 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-[#E2E8F0]">
              <div className="flex items-center space-x-2">
                <Code2 className="w-5 h-5 text-[#2563EB]" />
                <h3 className="text-base font-bold text-[#1E3A5F]">
                  Message Payload #{viewingPayload.recordId}
                </h3>
              </div>
              <button
                onClick={() => setViewingPayload(null)}
                className="text-[#64748B] hover:text-[#1E3A5F]"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="text-xs space-y-1">
              <div>
                <strong>Original Topic:</strong>{' '}
                <span className="font-mono text-[#1E3A5F]">{viewingPayload.originalTopic}</span>
              </div>
              <div>
                <strong>Error:</strong>{' '}
                <span className="text-[#DC2626]">{viewingPayload.errorMessage}</span>
              </div>
            </div>

            <div className="bg-[#0F172A] text-[#E2E8F0] p-4 rounded-xl text-xs font-mono max-h-64 overflow-y-auto">
              <pre>
                {viewingPayload.payloadJson
                  ? JSON.stringify(JSON.parse(viewingPayload.payloadJson), null, 2)
                  : JSON.stringify(
                      {
                        recordId: viewingPayload.recordId,
                        topic: viewingPayload.originalTopic,
                        key: viewingPayload.recordKey,
                        error: viewingPayload.errorMessage,
                        attempts: viewingPayload.attempts,
                        timestamp: viewingPayload.failedAt,
                      },
                      null,
                      2
                    )}
              </pre>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setViewingPayload(null)}
                className="py-2 px-5 rounded-lg bg-[#1E3A5F] text-white text-xs font-semibold hover:bg-[#182F4D]"
              >
                Close Inspector
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
