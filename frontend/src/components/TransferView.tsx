import React, { useState } from 'react';
import { ArrowLeftRight, CheckCircle2, AlertCircle, RefreshCw, Copy, Zap, Clock, ShieldCheck } from 'lucide-react';
import type { Account, TransferRequest, TransferResponse } from '../types';

interface TransferViewProps {
  accounts: Account[];
  initialSourceId?: number;
  onExecuteTransfer: (
    payload: TransferRequest,
    idempotencyKey: string,
    correlationId: string
  ) => Promise<{ response: TransferResponse; isCachedReplay: boolean; latencyMs: number }>;
}

export const TransferView: React.FC<TransferViewProps> = ({
  accounts,
  initialSourceId,
  onExecuteTransfer
}) => {
  const generateUuid = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  };

  const generateRef = () => `REF-UI-${Math.floor(Math.random() * 900000 + 100000)}`;

  const [sourceAccountId, setSourceAccountId] = useState<number>(
    initialSourceId || (accounts.length > 0 ? accounts[0].accountId : 1)
  );
  const [destAccountId, setDestAccountId] = useState<number>(
    accounts.length > 1 ? accounts[1].accountId : 2
  );
  const [amount, setAmount] = useState<string>('250.00');
  const [referenceNo, setReferenceNo] = useState<string>(generateRef);
  const [remarks, setRemarks] = useState<string>('Real-time inter-account settlement');

  // Idempotency & Tracing State
  const [idempotencyKey, setIdempotencyKey] = useState<string>(generateUuid);
  const [correlationId, setCorrelationId] = useState<string>(generateUuid);
  const [lastUsedKey, setLastUsedKey] = useState<string | null>(null);

  // Submission State
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [lastResult, setLastResult] = useState<{
    response: TransferResponse;
    isCachedReplay: boolean;
    latencyMs: number;
  } | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState<boolean>(false);

  const sourceAccount = accounts.find(a => a.accountId === sourceAccountId);
  const destAccount = accounts.find(a => a.accountId === destAccountId);
  const numAmount = parseFloat(amount) || 0;
  const isInsufficient = sourceAccount ? sourceAccount.balance < numAmount : false;
  const isSourceInactive = Boolean(sourceAccount && sourceAccount.status && sourceAccount.status !== 'ACTIVE');
  const isDestInactive = Boolean(destAccount && destAccount.status && destAccount.status !== 'ACTIVE');

  const handleRefreshKeys = () => {
    setIdempotencyKey(generateUuid());
    setCorrelationId(generateUuid());
    setReferenceNo(`REF-UI-${Math.floor(Math.random() * 900000 + 100000)}`);
    setErrorMessage(null);
  };

  const handleSimulateReplay = () => {
    if (lastUsedKey) {
      setIdempotencyKey(lastUsedKey);
      setCorrelationId(generateUuid()); // Different correlation ID, same idempotency key
      setErrorMessage(null);
    }
  };

  const handleCopyKey = () => {
    navigator.clipboard.writeText(idempotencyKey);
    setCopiedKey(true);
    setTimeout(() => setCopiedKey(false), 2000);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSourceInactive) {
      setErrorMessage(`Source account is ${sourceAccount?.status}. Transfers are prohibited.`);
      return;
    }
    if (isDestInactive) {
      setErrorMessage(`Destination account is ${destAccount?.status}. Transfers to frozen or closed accounts are prohibited.`);
      return;
    }
    if (sourceAccountId === destAccountId) {
      setErrorMessage('Source and Destination accounts must be distinct accounts.');
      return;
    }
    if (numAmount <= 0) {
      setErrorMessage('Transfer amount must be greater than zero.');
      return;
    }
    if (numAmount > 500000.00) {
      setErrorMessage('Transfer exceeds maximum allowable single transaction limit (₱500,000.00).');
      return;
    }
    if (isInsufficient) {
      setErrorMessage('Insufficient funds in the source account.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    // Sanitize remarks: strip dangerous injection characters and clamp length
    const sanitizedRemarks = remarks
      .replace(/[<>]/g, '')
      .replace(/[\r\n\t]/g, ' ')
      .trim()
      .slice(0, 150);

    try {
      const payload: TransferRequest = {
        sourceAccountId,
        destinationAccountId: destAccountId,
        amount: numAmount,
        referenceNo,
        remarks: sanitizedRemarks || 'Inter-account transfer'
      };

      const result = await onExecuteTransfer(payload, idempotencyKey, correlationId);
      setLastResult(result);
      setLastUsedKey(idempotencyKey);
    } catch (err: any) {
      setErrorMessage(err.message || 'Transfer failed via API Gateway');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '1320px', margin: '0 auto', width: 'calc(100% - 32px)', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      
      {/* Title & Architecture Subtitle */}
      <div>
        <h2 style={{ fontSize: '24px', fontWeight: '800', color: '#ffffff', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <ArrowLeftRight size={24} color="var(--accent-cyan)" />
          <span>Atomic Double-Entry Fund Transfer Engine</span>
        </h2>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
          Enforces strict 2-phase atomic debit & credit balance mutations with Redis pre-flight idempotency locks
        </p>
      </div>

      <div className="grid-2">
        
        {/* Left Column: Interactive Transfer Form */}
        <div className="glass-card" style={{ padding: '28px' }}>
          <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#ffffff', marginBottom: '20px' }}>
            Transfer Configuration
          </h3>

          <form onSubmit={handleSubmit}>
            {/* Source Account */}
            <div className="form-group">
              <label className="form-label">
                <span>SOURCE ACCOUNT (DEBIT LEG)</span>
                {sourceAccount && (
                  <span style={{ color: isInsufficient ? 'var(--color-danger)' : 'var(--color-success)', fontFamily: 'var(--font-mono)' }}>
                    Bal: ₱{sourceAccount.balance.toFixed(2)}
                  </span>
                )}
              </label>
              <select
                className="form-select"
                value={sourceAccountId}
                onChange={(e) => setSourceAccountId(Number(e.target.value))}
              >
                {accounts.map(a => {
                  const isInactive = a.status && a.status !== 'ACTIVE';
                  return (
                    <option key={a.accountId} value={a.accountId} disabled={isInactive}>
                      {a.customerName ? `[${a.customerName}] ` : ''}{a.accountType} - {a.accountNumber} (₱{a.balance.toFixed(2)}){isInactive ? ` [${a.status}]` : ''}
                    </option>
                  );
                })}
              </select>
            </div>

            {/* Destination Account */}
            <div className="form-group">
              <label className="form-label">
                <span>DESTINATION ACCOUNT (CREDIT LEG)</span>
                {destAccount && (
                  <span style={{ color: 'var(--accent-cyan)', fontFamily: 'var(--font-mono)' }}>
                    {destAccount.customerName ? `[${destAccount.customerName}] ` : ''}Bal: ₱{destAccount.balance.toFixed(2)}
                  </span>
                )}
              </label>
              <select
                className="form-select"
                value={destAccountId}
                onChange={(e) => setDestAccountId(Number(e.target.value))}
              >
                {accounts.map(a => {
                  const isInactive = a.status && a.status !== 'ACTIVE';
                  return (
                    <option key={a.accountId} value={a.accountId} disabled={a.accountId === sourceAccountId || isInactive}>
                      {a.customerName ? `[${a.customerName}] ` : ''}{a.accountType} - {a.accountNumber} (₱{a.balance.toFixed(2)}){isInactive ? ` [${a.status}]` : ''}
                    </option>
                  );
                })}
              </select>
            </div>

            {/* Amount & Reference Number */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="form-group">
                <label className="form-label">TRANSFER AMOUNT (₱ - PHP)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  className="form-input"
                  style={{ fontFamily: 'var(--font-mono)', fontWeight: '700', fontSize: '16px' }}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">REFERENCE NUMBER</label>
                <input
                  type="text"
                  className="form-input"
                  style={{ fontFamily: 'var(--font-mono)' }}
                  value={referenceNo}
                  onChange={(e) => setReferenceNo(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Remarks */}
            <div className="form-group">
              <label className="form-label">TRANSACTION REMARKS</label>
              <input
                type="text"
                className="form-input"
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                placeholder="e.g. Supplier payment, payroll settlement"
              />
            </div>

            {/* Error Banner */}
            {errorMessage && (
              <div style={{
                margin: '16px 0',
                padding: '12px 16px',
                borderRadius: '10px',
                background: 'var(--color-danger-bg)',
                border: '1px solid var(--color-danger-border)',
                color: 'var(--color-danger)',
                fontSize: '13px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}>
                <AlertCircle size={16} />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isSubmitting || isInsufficient || sourceAccountId === destAccountId || isSourceInactive || isDestInactive}
              className="btn btn-primary"
              style={{ width: '100%', padding: '14px', fontSize: '15px', marginTop: '12px' }}
            >
              {isSubmitting ? (
                <>
                  <RefreshCw size={18} className="animate-spin" />
                  <span>Processing Double-Entry Ledger Mutation...</span>
                </>
              ) : (
                <>
                  <Zap size={18} />
                  <span>Execute Atomic Transfer (₱{numAmount.toFixed(2)})</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Right Column: Idempotency & Distributed Tracing Control Deck */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          
          {/* Idempotency Deck */}
          <div className="glass-card" style={{ padding: '24px', borderColor: 'rgba(99, 102, 241, 0.3)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Clock size={18} color="var(--accent-indigo)" />
                <h3 style={{ fontSize: '15px', fontWeight: '700', color: '#ffffff' }}>
                  Redis Distributed Idempotency Key
                </h3>
              </div>
              <button
                type="button"
                onClick={handleRefreshKeys}
                className="btn-outline"
                style={{ padding: '4px 10px', fontSize: '11px', borderRadius: '6px' }}
              >
                <RefreshCw size={12} />
                <span>New UUID</span>
              </button>
            </div>

            <div style={{
              background: 'rgba(2, 6, 23, 0.7)',
              padding: '12px 14px',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontFamily: 'var(--font-mono)',
              fontSize: '12px',
              color: 'var(--accent-cyan)'
            }}>
              <span>{idempotencyKey}</span>
              <button
                type="button"
                onClick={handleCopyKey}
                style={{ background: 'transparent', border: 'none', color: 'var(--text-subtle)', cursor: 'pointer' }}
                title="Copy to Clipboard"
              >
                <Copy size={14} color={copiedKey ? 'var(--color-success)' : 'var(--text-muted)'} />
              </button>
            </div>

            {/* Distributed Tracing Header */}
            <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '11px' }}>
              <span style={{ color: 'var(--text-subtle)' }}>X-Correlation-ID:</span>
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>{correlationId.substring(0, 18)}...</span>
            </div>

            {/* Replay Simulation Action */}
            {lastUsedKey && (
              <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle)' }}>
                <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '8px' }}>
                  Demonstrate FinTech Defense: Re-send with identical key to prove zero double-debiting.
                </p>
                <button
                  type="button"
                  onClick={handleSimulateReplay}
                  className="btn btn-secondary"
                  style={{ width: '100%', fontSize: '12px', padding: '8px 12px', borderColor: 'rgba(245, 158, 11, 0.4)' }}
                >
                  <Zap size={14} color="var(--color-warning)" />
                  <span style={{ color: 'var(--color-warning)' }}>Simulate Idempotent Replay (Same Key)</span>
                </button>
              </div>
            )}
          </div>

          {/* Double-Entry Ledger Leg Breakdown Visualizer */}
          <div className="glass-card" style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '15px', fontWeight: '700', color: '#ffffff', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <ShieldCheck size={18} color="var(--color-success)" />
              <span>Atomic Double-Entry Posting Preview</span>
            </h3>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div style={{ padding: '14px', borderRadius: '10px', background: 'rgba(244, 63, 94, 0.08)', border: '1px solid rgba(244, 63, 94, 0.25)' }}>
                <div style={{ fontSize: '11px', fontWeight: '700', color: 'var(--color-danger)', textTransform: 'uppercase' }}>
                  Leg 1: Source Debit
                </div>
                <div style={{ marginTop: '6px', fontSize: '12px', color: 'var(--text-main)', fontFamily: 'var(--font-mono)' }}>
                  Acc #{sourceAccountId}
                </div>
                <div style={{ marginTop: '4px', fontSize: '18px', fontWeight: '800', color: 'var(--color-danger)', fontFamily: 'var(--font-mono)' }}>
                  -₱{numAmount.toFixed(2)}
                </div>
              </div>

              <div style={{ padding: '14px', borderRadius: '10px', background: 'rgba(16, 185, 129, 0.08)', border: '1px solid rgba(16, 185, 129, 0.25)' }}>
                <div style={{ fontSize: '11px', fontWeight: '700', color: 'var(--color-success)', textTransform: 'uppercase' }}>
                  Leg 2: Dest Credit
                </div>
                <div style={{ marginTop: '6px', fontSize: '12px', color: 'var(--text-main)', fontFamily: 'var(--font-mono)' }}>
                  Acc #{destAccountId}
                </div>
                <div style={{ marginTop: '4px', fontSize: '18px', fontWeight: '800', color: 'var(--color-success)', fontFamily: 'var(--font-mono)' }}>
                  +₱{numAmount.toFixed(2)}
                </div>
              </div>
            </div>

            <p style={{ marginTop: '12px', fontSize: '11px', color: 'var(--text-subtle)', textAlign: 'center' }}>
              Sum of Debits equals Sum of Credits: Balanced Ledger Δ = ₱0.00
            </p>
          </div>

          {/* Last Result Card */}
          {lastResult && (
            <div className="glass-card" style={{
              padding: '20px',
              border: `1px solid ${lastResult.isCachedReplay ? 'rgba(245, 158, 11, 0.5)' : 'rgba(16, 185, 129, 0.5)'}`,
              background: lastResult.isCachedReplay ? 'rgba(245, 158, 11, 0.06)' : 'rgba(16, 185, 129, 0.06)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                <CheckCircle2 size={18} color={lastResult.isCachedReplay ? 'var(--color-warning)' : 'var(--color-success)'} />
                <span style={{ fontSize: '14px', fontWeight: '800', color: '#ffffff' }}>
                  {lastResult.isCachedReplay ? 'IDEMPOTENT CACHE HIT (REPLAY VERIFIED)' : 'TRANSACTION POSTED TO MASTER LEDGER'}
                </span>
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Txn ID: <strong style={{ color: '#ffffff', fontFamily: 'var(--font-mono)' }}>{lastResult.response.transactionId}</strong> | Latency: {lastResult.latencyMs}ms
              </div>
              {lastResult.isCachedReplay && (
                <div style={{ marginTop: '6px', fontSize: '11px', color: 'var(--color-warning)', fontWeight: '600' }}>
                  ✔ Redis Mutex detected duplicate key. Zero duplicate debit executed.
                </div>
              )}
            </div>
          )}

        </div>
      </div>
    </div>
  );
};
