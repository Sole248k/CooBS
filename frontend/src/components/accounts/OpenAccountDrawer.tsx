import React, { useState } from 'react';
import { staffApi } from '../../services/api';
import type { AccountType } from '../../types/staff-bff';
import { X, PlusCircle, AlertCircle, CheckCircle2 } from 'lucide-react';

interface OpenAccountDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (createdAccount?: any) => void;
  initialCustomerId?: string;
}

export const OpenAccountDrawer: React.FC<OpenAccountDrawerProps> = ({
  isOpen,
  onClose,
  onSuccess,
  initialCustomerId = '',
}) => {
  const [customerId, setCustomerId] = useState(initialCustomerId || '1');
  const [accountType, setAccountType] = useState<AccountType>('SAVINGS');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const parsedCustomerId = parseInt(customerId, 10);
    if (isNaN(parsedCustomerId) || parsedCustomerId <= 0) {
      setError('Please provide a valid numeric customer ID.');
      setLoading(false);
      return;
    }

    try {
      const created = await staffApi.openAccount({
        customerId: parsedCustomerId,
        accountType,
        currency: 'PHP',
      });
      onSuccess(created);
      onClose();
    } catch (err: any) {
      if (err.message?.includes('KYC') || err.message?.includes('unverified')) {
        setError('Cannot open account: customer KYC is not verified.');
      } else {
        setError(err.message || 'Failed to open account.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-[#0F1E33]/50 backdrop-blur-xs flex justify-end">
      <div className="w-full max-w-md bg-white h-full shadow-2xl border-l border-[#E2E8F0] flex flex-col animate-in slide-in-from-right duration-200">
        {/* Drawer Header */}
        <div className="p-6 border-b border-[#E2E8F0] flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-[#1E3A5F]">Open New Account</h2>
            <p className="text-xs text-[#64748B]">
              Originate a primary savings, checking, current or loan portfolio
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F1F5F9] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {error && (
            <div className="p-3.5 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-start space-x-2 text-xs text-[#DC2626]">
              <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
              Customer ID
            </label>
            <input
              type="number"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              required
              className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
              placeholder="e.g. 1"
            />
            <span className="text-[11px] text-[#64748B] mt-1 block">
              Customer must hold an active VERIFIED KYC standing.
            </span>
          </div>

          <div>
            <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
              Account Type
            </label>
            <select
              value={accountType}
              onChange={(e) => setAccountType(e.target.value as AccountType)}
              className="w-full px-3.5 py-2.5 text-sm bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
            >
              <option value="SAVINGS">SAVINGS — Daily Interest Bearing</option>
              <option value="CHECKING">CHECKING — Transactional Checking</option>
              <option value="CURRENT">CURRENT — Institutional Commercial</option>
              <option value="LOAN">LOAN — Amortized Credit Schedule</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-[#1E3A5F] uppercase tracking-wider mb-1.5">
              Currency
            </label>
            <input
              type="text"
              value="PHP (₱)"
              disabled
              className="w-full px-3.5 py-2.5 text-sm bg-[#F8FAFC] border border-[#CBD5E1] rounded-lg text-[#64748B] font-mono cursor-not-allowed"
            />
            <span className="text-[11px] text-[#64748B] mt-1 block">
              Fixed to Philippine Peso. Initial balance opens at ₱0.00 per banking specification.
            </span>
          </div>

          <div className="p-4 rounded-xl bg-[#EFF6FF] border border-[#DBEAFE] space-y-2 text-xs text-[#1E40AF]">
            <div className="font-semibold flex items-center space-x-1.5">
              <CheckCircle2 className="w-4 h-4 text-[#2563EB]" />
              <span>Zero-Balance Inception Policy</span>
            </div>
            <p className="text-[11px] leading-relaxed text-[#1E3A5F]/80">
              New accounts open immediately with a ₱0.00 ledger balance. Tellers can subsequently fund the account via Counter Desk cash deposit.
            </p>
          </div>
        </form>

        {/* Drawer Actions Footer */}
        <div className="p-6 border-t border-[#E2E8F0] flex space-x-3 bg-[#F8FAFC]">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 py-2.5 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-white transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={loading}
            className="flex-1 py-2.5 px-4 rounded-lg bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold shadow-sm transition-all flex items-center justify-center space-x-1.5 disabled:opacity-50"
          >
            <PlusCircle className="w-4 h-4" />
            <span>{loading ? 'Creating...' : 'Open Account'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
