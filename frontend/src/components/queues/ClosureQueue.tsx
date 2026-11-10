import React, { useState } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import { formatCurrency, formatTimestamp, getStatusClass } from '../../lib/utils';
import { FolderX, Check, X, AlertCircle, Info } from 'lucide-react';

interface ClosureQueueProps {
  requests: Array<{
    closureId: number;
    accountId: number;
    accountNumber: string;
    customerId: number;
    customerName?: string;
    availableBalance: number | string;
    reason: string;
    status: string;
    requestedAt: string;
  }>;
  onRefresh: () => void;
}

export const ClosureQueue: React.FC<ClosureQueueProps> = ({ requests, onRefresh }) => {
  const { isAdmin } = useAuth();

  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('Account closure criteria not satisfied');
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const handleApprove = async (id: number) => {
    setSubmitting(true);
    setActionError(null);
    try {
      await staffApi.approveClosure(id);
      onRefresh();
    } catch (err: any) {
      if (err.message?.includes('ACCOUNT_NON_ZERO_BALANCE') || err.message?.includes('non-zero')) {
        setActionError('Account cannot be closed: outstanding available balance must be ₱0.00.');
      } else if (err.message?.includes('ACCOUNT_ACTIVE_FLAG_EXISTS') || err.message?.includes('flag')) {
        setActionError('Account cannot be closed: active governance flags exist on this account.');
      } else {
        setActionError(err.message || 'Failed to approve account closure.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmReject = async () => {
    if (!rejectingId) return;
    setSubmitting(true);
    setActionError(null);
    try {
      await staffApi.rejectClosure(rejectingId, rejectReason);
      setRejectingId(null);
      onRefresh();
    } catch (err: any) {
      setActionError(err.message || 'Failed to reject account closure.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      {actionError && (
        <div className="p-3.5 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-2 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{actionError}</span>
        </div>
      )}

      {/* Read-only notice for tellers */}
      {!isAdmin && (
        <div className="p-3 rounded-lg bg-[#EFF6FF] border border-[#DBEAFE] flex items-center space-x-2 text-xs text-[#1E40AF]">
          <Info className="w-4 h-4 flex-shrink-0" />
          <span>
            <strong>Maker-Checker Dual Control:</strong> Branch Tellers have read-only visibility for Account Closure requests. Branch Manager authorization (Checker role) is required to approve or reject closures.
          </span>
        </div>
      )}

      {/* Action requirement for Admins */}
      {isAdmin && (
        <div className="p-3 rounded-lg bg-indigo-50 border border-indigo-200 flex items-center space-x-2 text-xs text-indigo-900">
          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-200/80 text-indigo-800 flex-shrink-0">
            CHECKER ROLE
          </span>
          <span>
            <strong>Supervisory Decision Required:</strong> Approving closures permanently closes accounts. Available balance must be ₱0.00 and active governance flags must be resolved.
          </span>
        </div>
      )}

      {requests.length === 0 ? (
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-12 text-center text-[#64748B]">
          <FolderX className="w-10 h-10 mx-auto text-[#64748B] mb-2 opacity-60" />
          <p className="text-sm font-semibold text-[#1E3A5F]">Closure Queue Empty</p>
          <p className="text-xs mt-1">There are no account closure requests pending review.</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-[#64748B] uppercase tracking-wider">
                  <th className="py-3 px-4">Closure ID</th>
                  <th className="py-3 px-4">Account Number</th>
                  <th className="py-3 px-4">Customer</th>
                  <th className="py-3 px-4 text-right">Available Balance</th>
                  <th className="py-3 px-4">Member Reason</th>
                  <th className="py-3 px-4">Requested At</th>
                  {isAdmin && <th className="py-3 px-4 text-center">Admin Decisions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E2E8F0]">
                {requests.map((req, index) => {
                  const clsId = req.closureId ?? (req as any).id ?? index + 1;
                  return (
                    <tr key={clsId} className="hover:bg-[#F8FAFC]/70 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-medium text-[#1E3A5F]">
                        #CLS-{clsId}
                      </td>
                      <td className="py-3.5 px-4 font-mono font-bold text-[#1E3A5F]">
                        {req.accountNumber}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-semibold text-[#1E3A5F]">
                          {req.customerName || `Customer #${req.customerId}`}
                        </div>
                        <div className="text-xs text-[#64748B] font-mono">ID: {req.customerId}</div>
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono font-bold text-[#1E3A5F] tabular-nums">
                        {formatCurrency(req.availableBalance)}
                      </td>
                      <td className="py-3.5 px-4 text-xs text-[#64748B] max-w-xs truncate">
                        {req.reason || 'Member requested closure'}
                      </td>
                      <td className="py-3.5 px-4 text-xs text-[#64748B]">
                        {formatTimestamp(req.requestedAt)}
                      </td>
                      {isAdmin && (
                        <td className="py-3.5 px-4 text-center">
                          <div className="flex items-center justify-center space-x-2">
                            <button
                              onClick={() => handleApprove(clsId)}
                              disabled={submitting}
                              className="px-3 py-1.5 rounded-lg bg-[#D1FAE5] text-[#065F46] hover:bg-[#A7F3D0] text-xs font-semibold transition-colors flex items-center space-x-1"
                            >
                              <Check className="w-3.5 h-3.5" />
                              <span>Approve</span>
                            </button>
                            <button
                              onClick={() => {
                                setRejectingId(clsId);
                                setRejectReason('Closure denied: requirements unmet');
                              }}
                              disabled={submitting}
                              className="px-3 py-1.5 rounded-lg bg-[#FEE2E2] text-[#991B1B] hover:bg-[#FECACA] text-xs font-semibold transition-colors flex items-center space-x-1"
                            >
                            <X className="w-3.5 h-3.5" />
                            <span>Reject</span>
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                );
              })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Mandatory Reject Reason Modal */}
      {rejectingId && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-bold text-[#1E3A5F]">
              Reject Account Closure #{rejectingId}
            </h3>
            <p className="text-xs text-[#64748B]">
              Provide an administrative rejection reason to document why this account closure is refused.
            </p>

            <div>
              <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                Reason for Rejection
              </label>
              <textarea
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                rows={3}
                required
                className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                placeholder="e.g. Non-zero ledger balance or pending scheduled transactions..."
              />
            </div>

            <div className="flex space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setRejectingId(null)}
                className="flex-1 py-2 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmReject}
                disabled={submitting || !rejectReason.trim()}
                className="flex-1 py-2 px-4 rounded-lg bg-[#EF4444] hover:bg-[#DC2626] text-white text-xs font-semibold disabled:opacity-50"
              >
                {submitting ? 'Rejecting...' : 'Confirm Rejection'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
