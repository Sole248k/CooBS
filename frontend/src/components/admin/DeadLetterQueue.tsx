import React, { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { DeadLetterRecord } from '../../types/staff-bff';
import { formatTimestamp } from '../../lib/utils';
import {
  AlertTriangle,
  RotateCcw,
  RefreshCw,
  Code2,
  CheckCircle2,
  ShieldAlert,
  AlertCircle,
  X,
} from 'lucide-react';

export const DeadLetterQueue: React.FC = () => {
  const { isAdmin } = useAuth();

  const [records, setRecords] = useState<DeadLetterRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Replay State
  const [replayingId, setReplayingId] = useState<string | null>(null);
  const [replayedMap, setReplayedMap] = useState<Record<string, string>>({});

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
            Dead Letter Queue (DLQ) diagnostic inspection and Kafka event replays are restricted exclusively to System Administrators.
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
      setReplayedMap((prev) => ({
        ...prev,
        [recordId]: new Date().toISOString(),
      }));
      setTimeout(() => loadDlqRecords(), 2000);
    } catch (err: any) {
      setError(err.message || `Failed to replay record ${recordId} to Kafka.`);
    } finally {
      setReplayingId(null);
    }
  };

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
          className="px-3.5 py-2 rounded-lg bg-white border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC] transition-colors flex items-center space-x-1.5 self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh Queue</span>
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {records.length === 0 ? (
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-12 text-center text-[#64748B]">
          <CheckCircle2 className="w-10 h-10 mx-auto text-[#10B981] mb-2 opacity-80" />
          <p className="text-sm font-semibold text-[#1E3A5F]">Dead Letter Queue Clean</p>
          <p className="text-xs mt-1">Zero corrupted or unprocessable Kafka stream messages present.</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-[#64748B] uppercase tracking-wider">
                  <th className="py-3 px-4">Record ID</th>
                  <th className="py-3 px-4">Topic / Key</th>
                  <th className="py-3 px-4">Failure Reason</th>
                  <th className="py-3 px-4">Attempts</th>
                  <th className="py-3 px-4">Timestamp</th>
                  <th className="py-3 px-4 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E2E8F0]">
                {records.map((rec) => {
                  const isReplayed = !!replayedMap[rec.recordId] || !!rec.replayedAt;
                  const isReplaying = replayingId === rec.recordId;

                  return (
                    <tr key={rec.recordId} className="hover:bg-[#F8FAFC]/70 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-bold text-xs text-[#1E3A5F]">
                        {rec.recordId}
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="font-mono text-xs font-semibold text-[#1E3A5F]">
                          {rec.originalTopic}
                        </div>
                        <div className="text-[11px] font-mono text-[#64748B]">
                          Key: {rec.recordKey || 'null'} &bull; DLQ: {rec.deadLetterTopic}
                        </div>
                      </td>

                      <td className="py-3.5 px-4 max-w-xs">
                        <span className="text-xs text-[#DC2626] font-mono line-clamp-2">
                          {rec.errorMessage}
                        </span>
                      </td>

                      <td className="py-3.5 px-4">
                        <span className="pill pill-frozen text-[10px]">
                          {rec.attempts} / {rec.maxAttempts || 3} Failed
                        </span>
                      </td>

                      <td className="py-3.5 px-4 text-xs text-[#64748B]">
                        {formatTimestamp(rec.failedAt)}
                      </td>

                      <td className="py-3.5 px-4 text-center">
                        <div className="flex items-center justify-center space-x-2">
                          <button
                            onClick={() => setViewingPayload(rec)}
                            className="p-1.5 rounded-lg border border-[#CBD5E1] text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F8FAFC]"
                            title="Inspect Payload JSON"
                          >
                            <Code2 className="w-3.5 h-3.5" />
                          </button>

                          {isReplayed ? (
                            <span className="pill pill-active text-[10px]">
                              Replayed
                            </span>
                          ) : (
                            <button
                              onClick={() => handleReplay(rec.recordId)}
                              disabled={isReplaying}
                              className="px-3 py-1.5 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-semibold rounded-lg shadow-sm transition-all flex items-center space-x-1 disabled:opacity-50"
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
