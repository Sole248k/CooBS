import React, { useState } from 'react';
import { X, KeyRound, UserCheck, LogIn, CheckCircle2, AlertCircle } from 'lucide-react';
import type { AuthSession } from '../types';
import { apiClient } from '../services/api';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentSession: AuthSession | null;
  onSessionChange: (newSession: AuthSession | null) => void;
}

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  onClose,
  currentSession,
  onSessionChange,
}) => {
  const [activeTab, setActiveTab] = useState<'personas' | 'login' | 'register'>('personas');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [mfaRequired, _setMfaRequired] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  if (!isOpen) return null;

  const personas = [
    {
      id: 1,
      username: 'john_doe',
      role: 'ROLE_CUSTOMER',
      userType: 'CUSTOMER' as const,
      description: 'Primary customer (Account #1 - Savings: ₱49,675.00 in Oracle DB)',
      color: 'var(--accent-cyan)'
    },
    {
      id: 2,
      username: 'maria_santos',
      role: 'ROLE_CUSTOMER',
      userType: 'CUSTOMER' as const,
      description: 'Counterparty recipient (Account #2 - Checking: ₱25,325.00 in Oracle DB)',
      color: 'var(--color-success)'
    },
    {
      id: 3,
      username: 'teller_alice',
      role: 'ROLE_TELLER',
      userType: 'TELLER' as const,
      description: 'Authorized branch teller with KYC verification & teller privileges',
      color: 'var(--accent-indigo)'
    },
    {
      id: 4,
      username: 'admin',
      role: 'ROLE_ADMIN',
      userType: 'ADMIN' as const,
      description: 'System administrator with full gateway actuator & audit inspection access',
      color: 'var(--color-warning)'
    }
  ];

  const handleSelectPersona = async (p: typeof personas[0]) => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      let session: AuthSession;
      if (p.userType === 'CUSTOMER') {
        session = await apiClient.loginCustomer(p.username, 'Password123!');
      } else {
        session = await apiClient.loginStaff(p.username, 'Password123!');
      }
      onSessionChange(session);
      setSuccessMsg(`Live authentication successful for ${p.username} (${p.userType})`);
      setTimeout(() => {
        setSuccessMsg(null);
        onClose();
      }, 700);
    } catch (err: any) {
      setErrorMsg(`Authentication failed: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const handleLiveLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setErrorMsg(null);

    try {
      let session: AuthSession;
      const isStaff = username === 'admin' || username.startsWith('teller');
      if (isStaff) {
        session = await apiClient.loginStaff(username, password);
      } else {
        session = await apiClient.loginCustomer(username, password);
      }
      onSessionChange(session);
      setSuccessMsg(`Authenticated as ${username} via Core Auth Service`);
      setTimeout(() => {
        onClose();
      }, 800);
    } catch (err: any) {
      setErrorMsg(err.message || 'Login failed. Please check credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(2, 6, 23, 0.8)',
      backdropFilter: 'blur(8px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 100,
      padding: '16px'
    }}>
      <div className="glass-card" style={{
        maxWidth: '560px',
        width: '100%',
        padding: '28px',
        borderRadius: '20px',
        position: 'relative',
        boxShadow: '0 20px 50px rgba(0, 0, 0, 0.6)',
        border: '1px solid rgba(56, 189, 248, 0.3)'
      }}>
        {/* Close Button */}
        <button
          onClick={onClose}
          style={{
            position: 'absolute',
            top: '20px',
            right: '20px',
            background: 'transparent',
            border: 'none',
            color: 'var(--text-subtle)',
            cursor: 'pointer'
          }}
        >
          <X size={20} />
        </button>

        {/* Modal Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
          <div style={{
            width: '40px',
            height: '40px',
            borderRadius: '10px',
            background: 'linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <KeyRound size={20} color="#ffffff" />
          </div>
          <div>
            <h3 style={{ fontSize: '18px', fontWeight: '800', color: '#ffffff' }}>
              Identity & Token Authority
            </h3>
            <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
              JWT Bearer Authentication & Spring Security RBAC Persona Switcher
            </p>
          </div>
        </div>

        {/* Tab Toggle */}
        <div style={{
          display: 'flex',
          background: 'rgba(15, 23, 42, 0.8)',
          borderRadius: '10px',
          padding: '4px',
          gap: '4px',
          marginBottom: '20px'
        }}>
          <button
            type="button"
            onClick={() => setActiveTab('personas')}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: '8px',
              border: 'none',
              background: activeTab === 'personas' ? 'rgba(56, 189, 248, 0.2)' : 'transparent',
              color: activeTab === 'personas' ? '#ffffff' : 'var(--text-muted)',
              fontSize: '12px',
              fontWeight: '700',
              cursor: 'pointer'
            }}
          >
            Seeded Personas
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('login')}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: '8px',
              border: 'none',
              background: activeTab === 'login' ? 'rgba(56, 189, 248, 0.2)' : 'transparent',
              color: activeTab === 'login' ? '#ffffff' : 'var(--text-muted)',
              fontSize: '12px',
              fontWeight: '700',
              cursor: 'pointer'
            }}
          >
            Direct Login
          </button>
        </div>

        {/* Feedback Banners */}
        {successMsg && (
          <div style={{
            marginBottom: '16px',
            padding: '10px 14px',
            borderRadius: '8px',
            background: 'var(--color-success-bg)',
            border: '1px solid var(--color-success-border)',
            color: 'var(--color-success)',
            fontSize: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <CheckCircle2 size={16} />
            <span>{successMsg}</span>
          </div>
        )}

        {errorMsg && (
          <div style={{
            marginBottom: '16px',
            padding: '10px 14px',
            borderRadius: '8px',
            background: 'var(--color-danger-bg)',
            border: '1px solid var(--color-danger-border)',
            color: 'var(--color-danger)',
            fontSize: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <AlertCircle size={16} />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Tab 1: Seeded Personas */}
        {activeTab === 'personas' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '4px' }}>
              Select a pre-configured banking identity to simulate RBAC permissions:
            </p>
            {personas.map(p => {
              const isSelected = currentSession?.username === p.username;
              return (
                <button
                  key={p.id}
                  onClick={() => handleSelectPersona(p)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '12px 16px',
                    borderRadius: '12px',
                    background: isSelected ? 'rgba(56, 189, 248, 0.15)' : 'rgba(30, 41, 59, 0.5)',
                    border: `1px solid ${isSelected ? 'var(--accent-cyan)' : 'var(--border-subtle)'}`,
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'all 0.2s'
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontSize: '14px', fontWeight: '700', color: '#ffffff' }}>
                        {p.username}
                      </span>
                      <span className="badge badge-info" style={{ fontSize: '10px' }}>
                        {p.role}
                      </span>
                      {isSelected && (
                        <span className="badge badge-success" style={{ fontSize: '10px' }}>
                          ACTIVE
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-subtle)', marginTop: '4px' }}>
                      {p.description}
                    </div>
                  </div>
                  <UserCheck size={18} color={isSelected ? 'var(--accent-cyan)' : 'var(--text-subtle)'} />
                </button>
              );
            })}
          </div>
        )}

        {/* Tab 2: Direct Login */}
        {activeTab === 'login' && (
          <form onSubmit={handleLiveLogin} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div className="form-group">
              <label className="form-label">USERNAME</label>
              <input
                type="text"
                className="form-input"
                placeholder="customer1"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">PASSWORD</label>
              <input
                type="password"
                className="form-input"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            {mfaRequired && (
              <div className="form-group">
                <label className="form-label" style={{ color: 'var(--color-warning)' }}>
                  MFA ONE-TIME PASSCODE (6 DIGITS)
                </label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="123456"
                  maxLength={6}
                  value={mfaCode}
                  onChange={(e) => setMfaCode(e.target.value)}
                />
              </div>
            )}

            <button
              type="submit"
              disabled={isLoading}
              className="btn btn-primary"
              style={{ width: '100%', padding: '12px', fontSize: '14px', marginTop: '6px' }}
            >
              <LogIn size={16} />
              <span>{isLoading ? 'Verifying Credentials...' : 'Authenticate with Edge Gateway'}</span>
            </button>
          </form>
        )}

        {/* Active Token Inspector Footer */}
        <div style={{
          marginTop: '20px',
          paddingTop: '16px',
          borderTop: '1px solid var(--border-subtle)',
          fontSize: '11px',
          color: 'var(--text-subtle)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span>Active Bearer Token:</span>
            <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent-cyan)' }}>
              {currentSession?.accessToken ? `${currentSession.accessToken.substring(0, 24)}...` : 'None'}
            </span>
          </div>
          <div>
            RBAC Scope: <strong style={{ color: 'var(--text-main)' }}>{currentSession?.roles.join(', ') || 'ANONYMOUS'}</strong>
          </div>
        </div>

      </div>
    </div>
  );
};
