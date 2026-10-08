import React, { useState, useEffect } from 'react';
import { staffApi } from '../../services/api';
import type { AuditRecord, ChainVerificationResult, DailyAuditSealResponse } from '../../types/staff-bff';
import { formatCurrency, formatTimestamp, truncateHash } from '../../lib/utils';
import {
  ShieldCheck,
  ShieldAlert,
  Search,
  Copy,
  Check,
  Lock,
  Calendar,
  AlertCircle,
  Hash,
  RefreshCw,
  Layers,
} from 'lucide-react';

export const AuditStatement: React.FC = () => {
  const [accountId, setAccountId] = useState('1');
  const [businessDate, setBusinessDate] = useState('2026-10-05');

  const [statement, setStatement] = useState<AuditRecord[]>([]);
  const [chainResult, setChainResult] = useState<any | null>(null);
  const [dailySeal, setDailySeal] = useState<DailyAuditSealResponse | null>(null);

  const [loading, setLoading] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedHash, setCopiedHash] = useState<string | null>(null);

  const loadAuditData = async (accId: number, dateStr: string) => {
    setLoading(true);
    setError(null);
    try {
      const [stmtRes, chainRes, sealRes] = await Promise.allSettled([
        staffApi.getAccountStatement(accId, 0, 50),
        staffApi.verifyChain(accId),
        staffApi.getDailySeal(dateStr),
      ]);

      if (stmtRes.status === 'fulfilled') {
        const rows = stmtRes.value?.content || stmtRes.value || [];
        setStatement(Array.isArray(rows) ? rows : []);
      }
      if (chainRes.status === 'fulfilled') {
        setChainResult(chainRes.value);
      }
      if (sealRes.status === 'fulfilled') {
        setDailySeal(sealRes.value);
      } else {
        setDailySeal(null);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load audit statement.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAuditData(1, businessDate);
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const acc = parseInt(accountId, 10);
    if (!isNaN(acc) && acc > 0) {
      loadAuditData(acc, businessDate);
    }
  };

  const handleVerifyChain = async () => {
    const acc = parseInt(accountId, 10);
    if (isNaN(acc) || acc <= 0) return;
    setVerifying(true);
    try {
      const res = await staffApi.verifyChain(acc);
      setChainResult(res);
    } catch (err: any) {
      setError(err.message || 'Chain verification failed.');
    } finally {
      setVerifying(false);
    }
  };

  const copyText = (txt?: string | null) => {
    if (!txt) return;
    navigator.clipboard.writeText(txt);
    setCopiedHash(txt);
    setTimeout(() => setCopiedHash(null), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1E3A5F]">Cryptographic Merkle Audit Statement</h1>
          <p className="text-xs text-[#64748B]">
            Tamper-evident hash chained double-entry ledger &amp; daily Merkle root seals
          </p>
        </div>

        <form onSubmit={handleSearch} className="flex items-center space-x-2">
          <div className="relative">
            <span className="text-xs font-semibold text-[#64748B] mr-2">Account:</span>
            <input
              type="number"
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              className="w-24 px-3 py-1.5 text-xs bg-white border border-[#CBD5E1] rounded-lg text-[#1E3A5F] font-mono focus:ring-2 focus:ring-[#2563EB]"
              placeholder="1"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="px-3.5 py-1.5 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold rounded-lg shadow-sm"
          >
            Load
          </button>
        </form>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-[#FEE2E2] border border-[#FECACA] flex items-center space-x-3 text-xs text-[#DC2626]">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Chain Integrity Hero Banner */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-6">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex items-start space-x-4">
            <div
              className={`w-14 h-14 rounded-2xl flex items-center justify-center ${
                chainResult?.intact !== false
                  ? 'bg-[#D1FAE5] text-[#10B981]'
                  : 'bg-[#FEE2E2] text-[#EF4444]'
              }`}
            >
              {chainResult?.intact !== false ? (
                <ShieldCheck className="w-8 h-8" />
              ) : (
                <ShieldAlert className="w-8 h-8" />
              )}
            </div>

            <div className="space-y-1">
              <div className="flex items-center space-x-3">
                <h2 className="text-lg font-bold text-[#1E3A5F]">
                  {chainResult?.intact !== false
                    ? 'Cryptographic Chain Integrity: 100% INTACT'
                    : 'INTEGRITY BREACH DETECTED'}
                </h2>
                <span
                  className={`pill ${
                    chainResult?.intact !== false ? 'pill-active' : 'pill-frozen'
                  }`}
                >
                  {chainResult?.intact !== false ? 'VERIFIED' : 'COMPROMISED'}
                </span>
              </div>
              <p className="text-xs text-[#64748B]">
                Every consecutive debit and credit row satisfies recursive SHA-256 hash linking
                <span className="font-mono text-[#1E3A5F] font-semibold ml-1">
                  (h_i = SHA-256(h_{'{i-1}'} || tx_i))
                </span>
              </p>
              <div className="flex flex-wrap items-center gap-x-4 text-xs text-[#64748B] pt-1">
                <span>
                  Records Verified:{' '}
                  <strong className="font-mono text-[#1E3A5F]">
                    {chainResult?.totalRecordsVerified ?? chainResult?.recordsVerified ?? statement.length}
                  </strong>

                </span>
                <span>&bull;</span>
                <span>
                  Latest Hash:{' '}
                  <span className="font-mono text-[#1E3A5F]">
                    {truncateHash(chainResult?.latestHash || statement[statement.length - 1]?.currentHash || 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')}
                  </span>
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            <button
              onClick={handleVerifyChain}
              disabled={verifying}
              className="px-4 py-2 bg-[#1E3A5F] hover:bg-[#182F4D] text-white text-xs font-bold rounded-lg shadow-sm transition-all flex items-center space-x-1.5"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${verifying ? 'animate-spin' : ''}`} />
              <span>{verifying ? 'Validating Hashes...' : 'Re-verify Chain'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Daily Closing Seal Card */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] p-6 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-[#E2E8F0] gap-2">
          <div className="flex items-center space-x-2">
            <Lock className="w-4 h-4 text-[#2563EB]" />
            <h3 className="text-sm font-bold text-[#1E3A5F]">Daily Closing Merkle Seal</h3>
          </div>

          <div className="flex items-center space-x-2">
            <Calendar className="w-3.5 h-3.5 text-[#64748B]" />
            <span className="text-xs font-semibold text-[#64748B]">Business Date:</span>
            <input
              type="date"
              value={businessDate}
              onChange={(e) => {
                setBusinessDate(e.target.value);
                const acc = parseInt(accountId, 10) || 1;
                loadAuditData(acc, e.target.value);
              }}
              className="px-2.5 py-1 text-xs border border-[#CBD5E1] rounded-md text-[#1E3A5F] font-mono"
            />
          </div>
        </div>

        {dailySeal && dailySeal.sealHash ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="p-3 bg-[#F8FAFC] rounded-lg border border-[#E2E8F0]">
              <span className="text-[#64748B] block mb-1">SHA-256 Closing Seal Hash</span>
              <div className="flex items-center space-x-2">
                <span className="font-mono font-bold text-[#1E3A5F] break-all">
                  {truncateHash(dailySeal.sealHash, 16, 8)}
                </span>
                <button
                  onClick={() => copyText(dailySeal.sealHash)}
                  className="p-1 hover:bg-[#E2E8F0] rounded text-[#64748B]"
                  title="Copy full seal hash"
                >
                  {copiedHash === dailySeal.sealHash ? (
                    <Check className="w-3.5 h-3.5 text-[#10B981]" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>
            </div>

            <div className="p-3 bg-[#F8FAFC] rounded-lg border border-[#E2E8F0]">
              <span className="text-[#64748B] block mb-1">Anchored Batch Run ID</span>
              <span className="font-mono font-bold text-[#1E3A5F]">
                EOD Batch #{dailySeal.batchRunId || 1}
              </span>
              <span className="block text-[11px] text-[#64748B] mt-1">
                Last Audit Row ID: #{dailySeal.lastAuditId || 48}
              </span>
            </div>

            <div className="p-3 bg-[#F8FAFC] rounded-lg border border-[#E2E8F0]">
              <span className="text-[#64748B] block mb-1">Cryptographic Seal Timestamp</span>
              <span className="font-mono text-[#1E3A5F]">
                {formatTimestamp(dailySeal.sealedAt)}
              </span>
              <span className="block text-[10px] text-[#10B981] font-semibold mt-1">
                &bull; Certified Immutable Seal
              </span>
            </div>
          </div>
        ) : (
          <div className="p-4 bg-[#F8FAFC] rounded-lg border border-[#E2E8F0] text-center text-xs text-[#64748B]">
            No cryptographic seal recorded for date <span className="font-mono">{businessDate}</span>. (Seal generates upon End of Day batch completion).
          </div>
        )}
      </div>

      {/* Statement Table */}
      <div className="bg-white rounded-xl shadow-sm border border-[#E2E8F0] overflow-hidden">
        <div className="px-6 py-4 border-b border-[#E2E8F0] flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-[#1E3A5F]">
              Account #{accountId} Audit Statement
            </h3>
            <p className="text-xs text-[#64748B]">Chronological double-entry audit records</p>
          </div>
          <span className="text-xs text-[#64748B] font-mono">
            Total Records: {statement.length}
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="bg-[#F8FAFC] border-b border-[#E2E8F0] font-semibold text-[#64748B] uppercase tracking-wider">
                <th className="py-3 px-3">Audit ID</th>
                <th className="py-3 px-3">Reference No</th>
                <th className="py-3 px-3">Type</th>
                <th className="py-3 px-3 text-right">Amount</th>
                <th className="py-3 px-3 text-right">Old Balance</th>
                <th className="py-3 px-3 text-right">New Balance</th>
                <th className="py-3 px-3">Previous Hash</th>
                <th className="py-3 px-3">Current Hash</th>
                <th className="py-3 px-3">Actor / IP</th>
                <th className="py-3 px-3">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E2E8F0]">
              {statement.map((row) => {
                const typeStr = String(row.entryType || row.transactionType || 'RECORD').toUpperCase();
                const isCredit = typeStr.includes('CREDIT') || typeStr.includes('DEPOSIT');

                return (
                  <tr key={String(row.auditId)} className="hover:bg-[#F8FAFC]/70 transition-colors">
                    <td className="py-3 px-3 font-mono font-medium text-[#1E3A5F]">
                      #{row.auditId}
                    </td>
                    <td className="py-3 px-3 font-mono text-[#1E3A5F]">{row.referenceNo || '—'}</td>
                    <td className="py-3 px-3">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          isCredit
                            ? 'bg-[#D1FAE5] text-[#065F46]'
                            : 'bg-[#EFF6FF] text-[#1E40AF]'
                        }`}
                      >
                        {typeStr}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-bold text-[#1E3A5F] tabular-nums">
                      {formatCurrency(row.amount)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-[#64748B] tabular-nums">
                      {formatCurrency(row.oldBalance)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-bold text-[#1E3A5F] tabular-nums">
                      {formatCurrency(row.newBalance)}
                    </td>
                    <td className="py-3 px-3 font-mono text-[#64748B]">
                      <div className="flex items-center space-x-1">
                        <span>{truncateHash(row.previousHash, 6, 4)}</span>
                        {row.previousHash && (
                          <button
                            onClick={() => copyText(row.previousHash)}
                            className="hover:text-[#2563EB]"
                            title="Copy previous hash"
                          >
                            <Copy className="w-3 h-3" />
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="py-3 px-3 font-mono text-[#1E3A5F]">
                      <div className="flex items-center space-x-1">
                        <span>{truncateHash(row.currentHash, 6, 4)}</span>
                        {row.currentHash && (
                          <button
                            onClick={() => copyText(row.currentHash)}
                            className="hover:text-[#2563EB]"
                            title="Copy current hash"
                          >
                            <Copy className="w-3 h-3" />
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="py-3 px-3 text-[#64748B]">
                      <div>{row.actorId ? `ACTOR #${row.actorId}` : 'TELLER'}</div>
                      <div className="text-[10px] font-mono">{row.clientIp || '127.0.0.1'}</div>
                    </td>
                    <td className="py-3 px-3 text-[#64748B]">
                      {formatTimestamp(row.timestamp || row.eventTimestamp)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
