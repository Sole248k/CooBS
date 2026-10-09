import React, { useState } from 'react';
import { staffApi } from '../../services/api';
import { formatTimestamp } from '../../lib/utils';
import { ShieldCheck, Check, X, AlertCircle } from 'lucide-react';

interface KYCQueueProps {
  requests: Array<{
    requestId?: number;
    kycRequestId?: number;
    kycId?: number;
    customerId: number;
    customerName?: string;
    requestedFields?: Record<string, string>;
    status: string;
    submittedAt?: string;
    requestedAt?: string;
    newFirstName?: string;
    newMiddleInitial?: string;
    newLastName?: string;
    newAddress?: string;
    newMobileNumber?: string;
    newCivilStatus?: string;
    newOccupation?: string;
  }>;
  onRefresh: () => void;
}

export const KYCQueue: React.FC<KYCQueueProps> = ({ requests, onRefresh }) => {
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('Insufficient identity verification proof');
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const handleApprove = async (id: number) => {
    if (!id) {
      setActionError('Cannot approve request: missing Request ID.');
      return;
    }
    setSubmitting(true);
    setActionError(null);
    try {
      await staffApi.approveKyc(id);
      onRefresh();
    } catch (err: any) {
      setActionError(err.message || 'Failed to approve KYC update request.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmReject = async () => {
    if (!rejectingId) return;
    setSubmitting(true);
    setActionError(null);
    try {
      await staffApi.rejectKyc(rejectingId, rejectReason);
      setRejectingId(null);
      onRefresh();
    } catch (err: any) {
      setActionError(err.message || 'Failed to reject KYC update request.');
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

      {requests.length === 0 ? (
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-12 text-center text-[#64748B]">
          <ShieldCheck className="w-10 h-10 mx-auto text-[#10B981] mb-2 opacity-80" />
          <p className="text-sm font-semibold text-[#1E3A5F]">KYC Queue Clear</p>
          <p className="text-xs mt-1">There are no pending KYC update submissions awaiting decision.</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-[#64748B] uppercase tracking-wider">
                  <th className="py-3 px-4">Request ID</th>
                  <th className="py-3 px-4">Customer</th>
                  <th className="py-3 px-4">Requested Updates</th>
                  <th className="py-3 px-4">Submitted At</th>
                  <th className="py-3 px-4 text-center">Decisions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E2E8F0]">
                {requests.map((req, index) => {
                  const reqId = req.kycRequestId ?? req.requestId ?? req.kycId ?? (req as any).id ?? index + 1;
                  const submittedTime = req.requestedAt || req.submittedAt;

                  // Extract human-friendly updates to display
                  const updates: { label: string; value: string }[] = [];
                  if (req.newAddress) updates.push({ label: 'Address', value: req.newAddress });
                  if (req.newMobileNumber) updates.push({ label: 'Mobile', value: req.newMobileNumber });
                  if (req.newFirstName || req.newLastName) {
                    updates.push({
                      label: 'Name',
                      value: `${req.newFirstName || ''} ${req.newMiddleInitial ? req.newMiddleInitial + '. ' : ''}${req.newLastName || ''}`.trim(),
                    });
                  }
                  if (req.newCivilStatus) updates.push({ label: 'Civil Status', value: req.newCivilStatus });
                  if (req.newOccupation) updates.push({ label: 'Occupation', value: req.newOccupation });
                  if (req.requestedFields && Object.keys(req.requestedFields).length > 0) {
                    Object.entries(req.requestedFields).forEach(([k, v]) =>
                      updates.push({ label: k, value: String(v) })
                    );
                  }

                  return (
                    <tr key={reqId} className="hover:bg-[#F8FAFC]/70 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-medium text-[#1E3A5F]">
                        #KYC-{reqId}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-semibold text-[#1E3A5F]">
                          {req.customerName || `Customer #${req.customerId}`}
                        </div>
                        <div className="text-xs text-[#64748B] font-mono">ID: {req.customerId}</div>
                      </td>
                      <td className="py-3.5 px-4">
                        {updates.length > 0 ? (
                          <div className="space-y-1">
                            {updates.map((u, i) => (
                              <div key={i} className="text-xs">
                                <span className="font-medium text-[#64748B] uppercase text-[10px] mr-1">
                                  {u.label}:
                                </span>
                                <span className="font-mono text-[#1E3A5F]">{u.value}</span>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <span className="text-xs text-[#64748B]">Personal Details Verification</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-xs text-[#64748B]">
                        {formatTimestamp(submittedTime)}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <div className="flex items-center justify-center space-x-2">
                          <button
                            onClick={() => handleApprove(reqId)}
                            disabled={submitting}
                            className="px-3 py-1.5 rounded-lg bg-[#D1FAE5] text-[#065F46] hover:bg-[#A7F3D0] text-xs font-semibold transition-colors flex items-center space-x-1"
                          >
                            <Check className="w-3.5 h-3.5" />
                            <span>Approve</span>
                          </button>
                          <button
                            onClick={() => {
                              setRejectingId(reqId);
                              setRejectReason('Insufficient proof of documentation');
                            }}
                            disabled={submitting}
                            className="px-3 py-1.5 rounded-lg bg-[#FEE2E2] text-[#991B1B] hover:bg-[#FECACA] text-xs font-semibold transition-colors flex items-center space-x-1"
                          >
                            <X className="w-3.5 h-3.5" />
                            <span>Reject</span>
                          </button>
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

      {/* Mandatory Reject Reason Modal */}
      {rejectingId && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-bold text-[#1E3A5F]">Reject KYC Request #{rejectingId}</h3>
            <p className="text-xs text-[#64748B]">
              Per banking compliance regulations, a documented rejection justification is mandatory.
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
                placeholder="e.g. Expired ID presented or unreadable document scan..."
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
