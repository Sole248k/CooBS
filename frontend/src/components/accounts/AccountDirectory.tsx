import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { staffApi } from '../../services/api';
import type { StaffAccountSummary } from '../../types/staff-bff';
import { formatCurrency, getStatusClass } from '../../lib/utils';
import { OpenAccountDrawer } from './OpenAccountDrawer';
import {
  CreditCard,
  Plus,
  Search,
  Filter,
  Lock,
  Unlock,
  Flag,
  Trash2,
  AlertCircle,
  Copy,
  Check,
  RefreshCw,
  Coins,
  FileX,
  CheckCircle2,
} from 'lucide-react';

export const AccountDirectory: React.FC = () => {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [accounts, setAccounts] = useState<StaffAccountSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successBanner, setSuccessBanner] = useState<string | null>(null);

  // Filters & Search
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Drawer & Modals
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [createdAccountModalData, setCreatedAccountModalData] = useState<any | null>(null);

  // Flagging
  const [flaggingAccount, setFlaggingAccount] = useState<StaffAccountSummary | null>(null);
  const [flagReason, setFlagReason] = useState('AML_WATCH');
  const [flagDescription, setFlagDescription] = useState('Suspicious multi-branch high frequency deposits');

  // Closure Request
  const [closureTargetAccount, setClosureTargetAccount] = useState<StaffAccountSummary | null>(null);
  const [closureReason, setClosureReason] = useState('Customer requested account closure');
  const [closureNotes, setClosureNotes] = useState('');
  const [closureSubmitting, setClosureSubmitting] = useState(false);
  const [closureError, setClosureError] = useState<string | null>(null);

  const [actionLoading, setActionLoading] = useState(false);
  const [copiedAccount, setCopiedAccount] = useState<string | null>(null);

  const loadAccounts = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await staffApi.getAllAccounts();
      setAccounts(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch accounts directory.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAccounts();
    if (searchParams.get('openCustomer')) {
      setIsDrawerOpen(true);
    }
  }, [searchParams]);

  const handleToggleStatus = async (account: StaffAccountSummary) => {
    const nextStatus = account.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    setActionLoading(true);
    setError(null);
    try {
      await staffApi.updateAccountStatus(account.accountId, nextStatus);
      setSuccessBanner(`Account ${account.accountNumber} successfully updated to ${nextStatus}.`);
      setTimeout(() => setSuccessBanner(null), 4000);
      loadAccounts();
    } catch (err: any) {
      setError(err.message || `Failed to update status to ${nextStatus}.`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddFlag = async () => {
    if (!flaggingAccount) return;
    setActionLoading(true);
    try {
      await staffApi.addAccountFlag(flaggingAccount.accountId, `${flagReason}: ${flagDescription}`);
      setSuccessBanner(`Compliance flag added to account ${flaggingAccount.accountNumber}.`);
      setTimeout(() => setSuccessBanner(null), 4000);
      setFlaggingAccount(null);
      loadAccounts();
    } catch (err: any) {
      setError(err.message || 'Failed to add flag.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemoveFlag = async (accountId: number, flagIndex: number) => {
    setActionLoading(true);
    try {
      await staffApi.removeAccountFlag(accountId, flagIndex + 1);
      setSuccessBanner('Compliance flag removed.');
      setTimeout(() => setSuccessBanner(null), 4000);
      loadAccounts();
    } catch (err: any) {
      setError(err.message || 'Failed to remove flag.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmitClosureRequest = async () => {
    if (!closureTargetAccount) return;
    setClosureSubmitting(true);
    setClosureError(null);
    try {
      const fullReason = closureNotes.trim()
        ? `${closureReason} - ${closureNotes.trim()}`
        : closureReason;
      await staffApi.requestAccountClosure(closureTargetAccount.accountId, fullReason);
      setSuccessBanner(
        `Closure request submitted for account ${closureTargetAccount.accountNumber}. It is now pending Admin approval in the Queues.`
      );
      setTimeout(() => setSuccessBanner(null), 6000);
      setClosureTargetAccount(null);
      loadAccounts();
    } catch (err: any) {
      setClosureError(err.message || 'Failed to submit closure request.');
    } finally {
      setClosureSubmitting(false);
    }
  };

  const copyToClipboard = (num: string) => {
    navigator.clipboard.writeText(num);
    setCopiedAccount(num);
    setTimeout(() => setCopiedAccount(null), 2000);
  };

  // Filtered List
  const filteredAccounts = accounts.filter((acc) => {
    const matchesSearch =
      acc.accountNumber?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      acc.customerName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      String(acc.accountId).includes(searchTerm);

    const matchesStatus = statusFilter === 'ALL' || acc.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">Accounts Governance Directory</h1>
          <p className="text-xs text-[#64748B]">
            Manage account lifecycle, administrative freeze locks, and AML governance flags
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={loadAccounts}
            disabled={loading}
            className="p-2 rounded-lg bg-white border border-[#CBD5E1] text-[#64748B] hover:text-[#1E3A5F] hover:bg-[#F8FAFC]"
            title="Refresh accounts"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => setIsDrawerOpen(true)}
            className="px-4 py-2 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold rounded-lg shadow-sm transition-all flex items-center space-x-1.5"
          >
            <Plus className="w-4 h-4" />
            <span>Open Account</span>
          </button>
        </div>
      </div>

      {successBanner && (
        <div className="p-4 rounded-xl bg-[#ECFDF5] border border-[#A7F3D0] flex items-center space-x-3 text-xs text-[#065F46] animate-in fade-in duration-200">
          <CheckCircle2 className="w-4 h-4 text-[#10B981] flex-shrink-0" />
          <span className="font-medium">{successBanner}</span>
        </div>
      )}

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-4 flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative flex-1 w-full">
          <Search className="w-4 h-4 text-[#64748B] absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by account number, ID or member name..."
            className="w-full pl-9 pr-3 py-2 text-xs bg-[#F8FAFC] border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:outline-none focus:ring-2 focus:ring-[#2563EB]"
          />
        </div>

        <div className="flex items-center space-x-2 w-full md:w-auto">
          <Filter className="w-4 h-4 text-[#64748B]" />
          <div className="flex bg-[#F1F5F9] p-1 rounded-lg">
            {['ALL', 'ACTIVE', 'FROZEN', 'CLOSED'].map((st) => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${
                  statusFilter === st
                    ? 'bg-white text-[#1E3A5F] shadow-2xs'
                    : 'text-[#64748B] hover:text-[#1E3A5F]'
                }`}
              >
                {st}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Accounts Table */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-[#64748B] uppercase tracking-wider">
                <th className="py-3 px-4">Account Number</th>
                <th className="py-3 px-4">Customer Name</th>
                <th className="py-3 px-4">Type</th>
                <th className="py-3 px-4 text-right">Available Balance</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Active Flags</th>
                <th className="py-3 px-4 text-right">Lifecycle Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E2E8F0]">
              {filteredAccounts.map((acc) => (
                <tr key={acc.accountId} className="hover:bg-[#F8FAFC]/70 transition-colors">
                  <td className="py-3.5 px-4">
                    <div className="flex items-center space-x-2">
                      <span className="font-mono font-bold text-[#1E3A5F]">{acc.accountNumber}</span>
                      <button
                        onClick={() => copyToClipboard(acc.accountNumber)}
                        className="text-[#64748B] hover:text-[#2563EB] p-1 rounded hover:bg-[#F1F5F9]"
                        title="Copy account number"
                      >
                        {copiedAccount === acc.accountNumber ? (
                          <Check className="w-3.5 h-3.5 text-[#10B981]" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>
                    <div className="text-[11px] font-mono text-[#64748B]">ID #{acc.accountId}</div>
                  </td>

                  <td className="py-3.5 px-4">
                    <span className="font-semibold text-[#1E3A5F]">
                      {acc.customerName || 'Retail Member'}
                    </span>
                  </td>

                  <td className="py-3.5 px-4">
                    <span className="px-2 py-0.5 rounded text-xs font-medium bg-[#F1F5F9] text-[#1E3A5F]">
                      {acc.accountType}
                    </span>
                  </td>

                  <td className="py-3.5 px-4 text-right font-mono font-bold text-[#1E3A5F] tabular-nums">
                    {formatCurrency(acc.availableBalance)}
                  </td>

                  <td className="py-3.5 px-4">
                    <span className={`pill ${getStatusClass(acc.status)}`}>{acc.status}</span>
                  </td>

                  <td className="py-3.5 px-4">
                    {acc.activeFlags && acc.activeFlags.length > 0 ? (
                      <div className="flex flex-wrap items-center gap-1.5">
                        {acc.activeFlags.map((flag, idx) => (
                          <div
                            key={idx}
                            className="inline-flex items-center space-x-1 px-2 py-0.5 rounded bg-[#FEE2E2] text-[#DC2626] text-[10px] font-semibold"
                          >
                            <span>{flag}</span>
                            {/* Remove flag is Admin only per RBAC matrix */}
                            {isAdmin && (
                              <button
                                onClick={() => handleRemoveFlag(acc.accountId, idx)}
                                className="hover:text-[#991B1B] p-0.5"
                                title="Admin: Remove Flag"
                              >
                                <Trash2 className="w-3 h-3" />
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    ) : (
                      <span className="text-xs text-[#94A3B8]">None</span>
                    )}
                  </td>

                  <td className="py-3.5 px-4 text-right">
                    <div className="flex items-center justify-end space-x-1.5">
                      {/* Transact in Counter Desk */}
                      <button
                        onClick={() => navigate(`/counter?accountId=${acc.accountNumber}`)}
                        className="p-1.5 text-[#2563EB] hover:bg-[#EFF6FF] rounded-md transition-colors"
                        title="Transact in Counter Desk"
                      >
                        <Coins className="w-4 h-4" />
                      </button>

                      {/* Add Flag Modal Trigger */}
                      <button
                        onClick={() => setFlaggingAccount(acc)}
                        className="p-1.5 text-[#D97706] hover:bg-[#FEF3C7] rounded-md transition-colors"
                        title="Add Compliance Flag"
                      >
                        <Flag className="w-4 h-4" />
                      </button>

                      {/* Freeze / Activate Button */}
                      {acc.status !== 'CLOSED' && (
                        <button
                          onClick={() => handleToggleStatus(acc)}
                          disabled={actionLoading}
                          className={`px-2.5 py-1 text-xs font-semibold rounded-md transition-colors flex items-center space-x-1 ${
                            acc.status === 'ACTIVE'
                              ? 'bg-[#FEE2E2] text-[#DC2626] hover:bg-[#FECACA]'
                              : 'bg-[#D1FAE5] text-[#065F46] hover:bg-[#A7F3D0]'
                          }`}
                        >
                          {acc.status === 'ACTIVE' ? (
                            <>
                              <Lock className="w-3 h-3" />
                              <span>Freeze</span>
                            </>
                          ) : (
                            <>
                              <Unlock className="w-3 h-3" />
                              <span>Activate</span>
                            </>
                          )}
                        </button>
                      )}

                      {/* Request Closure Button */}
                      {acc.status !== 'CLOSED' && (
                        <button
                          onClick={() => {
                            setClosureTargetAccount(acc);
                            setClosureReason('Customer requested account closure');
                            setClosureNotes('');
                            setClosureError(null);
                          }}
                          className="p-1.5 text-[#DC2626] hover:bg-[#FEE2E2] rounded-md transition-colors"
                          title="Request Account Closure"
                        >
                          <FileX className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Account Origination Receipt / Success Modal */}
      {createdAccountModalData && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-5">
            <div className="flex items-center space-x-3">
              <div className="w-12 h-12 rounded-full bg-[#ECFDF5] border border-[#A7F3D0] flex items-center justify-center flex-shrink-0 text-[#059669]">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[#1E3A5F]">Account Opened Successfully!</h3>
                <p className="text-xs text-[#64748B]">New portfolio has been originated and registered in the core ledger.</p>
              </div>
            </div>

            <div className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl p-4 space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-[#E2E8F0]">
                <span className="text-xs text-[#64748B] font-medium">Account Number</span>
                <div className="flex items-center space-x-1.5 font-mono font-bold text-sm text-[#1E3A5F]">
                  <span>{createdAccountModalData.accountNumber}</span>
                  <button
                    onClick={() => copyToClipboard(createdAccountModalData.accountNumber)}
                    className="p-1 hover:bg-[#E2E8F0] rounded text-[#64748B] transition-colors"
                    title="Copy account number"
                  >
                    {copiedAccount === createdAccountModalData.accountNumber ? (
                      <Check className="w-3.5 h-3.5 text-[#10B981]" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div>
                  <span className="text-[#64748B] block text-[11px]">Account ID</span>
                  <span className="font-semibold text-[#1E3A5F] font-mono">#{createdAccountModalData.accountId}</span>
                </div>
                <div>
                  <span className="text-[#64748B] block text-[11px]">Customer / Member ID</span>
                  <span className="font-semibold text-[#1E3A5F] font-mono">#{createdAccountModalData.customerId}</span>
                </div>
                <div>
                  <span className="text-[#64748B] block text-[11px]">Account Type</span>
                  <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-[#EFF6FF] text-[#1D4ED8] inline-block">
                    {createdAccountModalData.accountType}
                  </span>
                </div>
                <div>
                  <span className="text-[#64748B] block text-[11px]">Initial Balance</span>
                  <span className="font-semibold text-[#1E3A5F] font-mono">
                    {formatCurrency(createdAccountModalData.availableBalance ?? createdAccountModalData.balance ?? 0)}
                  </span>
                </div>
              </div>

              <div className="flex items-center justify-between pt-2 border-t border-[#E2E8F0] text-xs">
                <span className="text-[#64748B]">Status</span>
                <span className="pill pill-success text-[11px] font-semibold">
                  {createdAccountModalData.status || 'ACTIVE'}
                </span>
              </div>
            </div>

            <p className="text-[11px] text-[#64748B] leading-relaxed">
              The account is now available for counter operations, cash deposits, assisted transfers, and scheduled interest accrual.
            </p>

            <div className="flex items-center space-x-2 pt-2">
              <button
                onClick={() => setCreatedAccountModalData(null)}
                className="flex-1 py-2 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC] transition-colors"
              >
                Close
              </button>
              <button
                onClick={() => {
                  const accId = createdAccountModalData.accountId;
                  setCreatedAccountModalData(null);
                  navigate(`/counter?accountId=${accId}`);
                }}
                className="flex-1 py-2 px-4 rounded-lg bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-semibold transition-colors flex items-center justify-center space-x-1.5"
              >
                <Coins className="w-3.5 h-3.5" />
                <span>Transact at Counter</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Account Closure Request Modal */}
      {closureTargetAccount && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-4">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-full bg-[#FEE2E2] border border-[#FECACA] flex items-center justify-center flex-shrink-0 text-[#DC2626]">
                <FileX className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[#1E3A5F]">Request Account Closure</h3>
                <p className="text-xs text-[#64748B]">
                  Submit formal closure request for {closureTargetAccount.accountNumber}
                </p>
              </div>
            </div>

            {closureError && (
              <div className="p-3 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-start space-x-2 text-xs text-[#DC2626]">
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{closureError}</span>
              </div>
            )}

            <div className="p-3.5 rounded-xl bg-[#F8FAFC] border border-[#E2E8F0] space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-[#64748B]">Customer Name</span>
                <span className="font-semibold text-[#1E3A5F]">{closureTargetAccount.customerName || 'Retail Member'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#64748B]">Account Type</span>
                <span className="font-semibold text-[#1E3A5F]">{closureTargetAccount.accountType}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-[#64748B]">Available Balance</span>
                <span className={`font-mono font-bold ${parseFloat(String(closureTargetAccount.availableBalance || '0')) > 0 ? 'text-[#DC2626]' : 'text-[#1E3A5F]'}`}>
                  {formatCurrency(closureTargetAccount.availableBalance)}
                </span>
              </div>
            </div>

            {parseFloat(String(closureTargetAccount.availableBalance || '0')) > 0 && (
              <div className="p-3 rounded-xl bg-[#FEF3C7] border border-[#FDE68A] text-xs text-[#92400E] space-y-2">
                <div className="flex items-start space-x-2">
                  <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5 text-[#D97706]" />
                  <span>
                    <strong>Non-Zero Balance Warning:</strong> Accounts must have an available balance of exactly ₱0.00 before final closure can be approved. Liquidate or withdraw the remaining balance at the Counter Desk first.
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    const accId = closureTargetAccount.accountId;
                    setClosureTargetAccount(null);
                    navigate(`/counter?accountId=${accId}`);
                  }}
                  className="w-full py-1.5 px-3 bg-[#D97706] hover:bg-[#B45309] text-white rounded-lg font-semibold text-[11px] transition-colors flex items-center justify-center space-x-1"
                >
                  <Coins className="w-3.5 h-3.5" />
                  <span>Withdraw / Disburse Balance at Counter Desk</span>
                </button>
              </div>
            )}

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                  Reason for Closure
                </label>
                <select
                  value={closureReason}
                  onChange={(e) => setClosureReason(e.target.value)}
                  className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                >
                  <option value="Customer requested account closure">Customer requested account closure</option>
                  <option value="Consolidating accounts into primary portfolio">Consolidating accounts into primary portfolio</option>
                  <option value="Relocation / Changing primary financial provider">Relocation / Changing primary financial provider</option>
                  <option value="Estate settlement / Probate execution">Estate settlement / Probate execution</option>
                  <option value="Dormancy liquidation and final settlement">Dormancy liquidation and final settlement</option>
                  <option value="Administrative / Regulatory compliance termination">Administrative / Regulatory compliance termination</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                  Branch Notes / Justification
                </label>
                <textarea
                  value={closureNotes}
                  onChange={(e) => setClosureNotes(e.target.value)}
                  rows={2}
                  className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                  placeholder="Optional branch notes or member signature reference..."
                />
              </div>
            </div>

            <div className="p-2.5 bg-[#EFF6FF] border border-[#DBEAFE] rounded-lg text-[11px] text-[#1E40AF]">
              Submitting this request routes it to the <strong>Staff Approval Queues</strong> under Account Closures for Admin verification and decisioning.
            </div>

            <div className="flex space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setClosureTargetAccount(null)}
                className="flex-1 py-2 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC] transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSubmitClosureRequest}
                disabled={closureSubmitting}
                className="flex-1 py-2 px-4 rounded-lg bg-[#DC2626] hover:bg-[#B91C1C] text-white text-xs font-semibold transition-colors disabled:opacity-50"
              >
                {closureSubmitting ? 'Submitting...' : 'Submit Request'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Flag Modal */}
      {flaggingAccount && (
        <div className="fixed inset-0 z-50 bg-[#0F1E33]/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-[#E2E8F0] max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-bold text-[#1E3A5F]">
              Add Flag: {flaggingAccount.accountNumber}
            </h3>
            <p className="text-xs text-[#64748B]">
              Administrative flags place regulatory restrictions on account closures and transfers.
            </p>

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                  Flag Category
                </label>
                <select
                  value={flagReason}
                  onChange={(e) => setFlagReason(e.target.value)}
                  className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                >
                  <option value="AML_WATCH">AML_WATCH — Anti-Money Laundering Monitoring</option>
                  <option value="DORMANT_WARNING">DORMANT_WARNING — Inactivity Flag</option>
                  <option value="LEGAL_HOLD">LEGAL_HOLD — Court Ordered Restriction</option>
                  <option value="FRAUD_SUSPECT">FRAUD_SUSPECT — High Velocity Investigation</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#1E3A5F] uppercase tracking-wider mb-1">
                  Justification Details
                </label>
                <textarea
                  value={flagDescription}
                  onChange={(e) => setFlagDescription(e.target.value)}
                  rows={3}
                  required
                  className="w-full p-2.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] focus:ring-2 focus:ring-[#2563EB] focus:outline-none"
                  placeholder="Document specific reasons for applying this governance hold..."
                />
              </div>
            </div>

            <div className="flex space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setFlaggingAccount(null)}
                className="flex-1 py-2 px-4 rounded-lg border border-[#CBD5E1] text-[#1E3A5F] text-xs font-semibold hover:bg-[#F8FAFC]"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleAddFlag}
                disabled={actionLoading || !flagDescription.trim()}
                className="flex-1 py-2 px-4 rounded-lg bg-[#D97706] hover:bg-[#B45309] text-white text-xs font-semibold disabled:opacity-50"
              >
                {actionLoading ? 'Flagging...' : 'Apply Flag'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Open Account Slide-Over Drawer */}
      <OpenAccountDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        onSuccess={(created) => {
          loadAccounts();
          if (created) {
            setCreatedAccountModalData(created);
          }
        }}
        initialCustomerId={searchParams.get('openCustomer') || '1'}
      />
    </div>
  );
};
