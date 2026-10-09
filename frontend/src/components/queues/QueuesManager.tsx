import React, { useState, useEffect } from 'react';
import { staffApi } from '../../services/api';
import type { StaffQueuesSummaryResponse } from '../../types/staff-bff';
import { KYCQueue } from './KYCQueue';
import { ClosureQueue } from './ClosureQueue';
import { RefreshCw, CheckSquare, FolderX, AlertCircle } from 'lucide-react';

export const QueuesManager: React.FC = () => {
  const [tab, setTab] = useState<'KYC' | 'CLOSURES'>('KYC');
  const [data, setData] = useState<StaffQueuesSummaryResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadQueues = async () => {
    setLoading(true);
    setError(null);
    try {
      const summary = await staffApi.getQueuesSummary();
      setData(summary);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch queue submissions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadQueues();
  }, []);

  const kycCount = data?.pendingKycRequests?.length ?? 0;
  const closureCount = data?.pendingClosureRequests?.length ?? 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">Staff Approval Queues</h1>
          <p className="text-xs text-[#64748B]">
            Governance verification for member KYC profile updates &amp; formal account closures
          </p>
        </div>

        <button
          onClick={loadQueues}
          disabled={loading}
          className="px-3.5 py-2 rounded-lg bg-white border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC] transition-colors flex items-center space-x-1.5 self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh Queues</span>
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Tabs */}
      <div className="flex space-x-2 border-b border-[#E2E8F0] pb-px">
        <button
          onClick={() => setTab('KYC')}
          className={`pb-3 px-4 text-xs font-bold transition-colors flex items-center space-x-2 border-b-2 ${
            tab === 'KYC'
              ? 'border-[#2563EB] text-[#2563EB]'
              : 'border-transparent text-[#64748B] hover:text-[#1E3A5F]'
          }`}
        >
          <CheckSquare className="w-4 h-4" />
          <span>KYC Profile Updates</span>
          <span
            className={`px-2 py-0.5 rounded-full text-[10px] font-mono ${
              tab === 'KYC' ? 'bg-[#EFF6FF] text-[#2563EB]' : 'bg-[#F1F5F9] text-[#64748B]'
            }`}
          >
            {kycCount}
          </span>
        </button>

        <button
          onClick={() => setTab('CLOSURES')}
          className={`pb-3 px-4 text-xs font-bold transition-colors flex items-center space-x-2 border-b-2 ${
            tab === 'CLOSURES'
              ? 'border-[#2563EB] text-[#2563EB]'
              : 'border-transparent text-[#64748B] hover:text-[#1E3A5F]'
          }`}
        >
          <FolderX className="w-4 h-4" />
          <span>Account Closures</span>
          <span
            className={`px-2 py-0.5 rounded-full text-[10px] font-mono ${
              tab === 'CLOSURES' ? 'bg-[#EFF6FF] text-[#2563EB]' : 'bg-[#F1F5F9] text-[#64748B]'
            }`}
          >
            {closureCount}
          </span>
        </button>
      </div>

      {/* Content */}
      {tab === 'KYC' ? (
        <KYCQueue requests={data?.pendingKycRequests || []} onRefresh={loadQueues} />
      ) : (
        <ClosureQueue requests={data?.pendingClosureRequests || []} onRefresh={loadQueues} />
      )}
    </div>
  );
};
