import { useState } from 'react';
import { ShieldCheck, Briefcase, Lock, User, ArrowRight, AlertCircle, Sparkles } from 'lucide-react';
import type { AuthSession } from '../types';

interface LoginPageProps {
  onLoginSuccess: (session: AuthSession) => void;
  onLoginCustomer: (username: string, pass: string) => Promise<AuthSession>;
  onLoginStaff: (username: string, pass: string) => Promise<AuthSession>;
}

export const LoginPage: React.FC<LoginPageProps> = ({
  onLoginSuccess,
  onLoginCustomer,
  onLoginStaff
}) => {
  const [activePortal, setActivePortal] = useState<'CUSTOMER' | 'STAFF'>('CUSTOMER');
  const [username, setUsername] = useState('john_doe');
  const [password, setPassword] = useState('Password123!');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handlePortalSwitch = (portal: 'CUSTOMER' | 'STAFF') => {
    setActivePortal(portal);
    setErrorMessage(null);
    if (portal === 'CUSTOMER') {
      setUsername('john_doe');
      setPassword('Password123!');
    } else {
      setUsername('teller_alice');
      setPassword('Password123!');
    }
  };

  const handleDemoSelect = (user: string, pass: string, portal: 'CUSTOMER' | 'STAFF') => {
    setActivePortal(portal);
    setUsername(user);
    setPassword(pass);
    setErrorMessage(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      setErrorMessage('Username is required.');
      return;
    }
    if (!password) {
      setErrorMessage('Password is required.');
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    try {
      let session: AuthSession;
      if (activePortal === 'CUSTOMER') {
        session = await onLoginCustomer(username.trim(), password);
      } else {
        session = await onLoginStaff(username.trim(), password);
      }
      onLoginSuccess(session);
    } catch (err: any) {
      setErrorMessage(err.message || 'Authentication failed. Please verify your credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      background: 'linear-gradient(135deg, #f8fafc 0%, #eff6ff 50%, #f1f5f9 100%)',
      color: '#0f172a'
    }}>
      {/* Top Header */}
      <header style={{
        padding: '20px 32px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderBottom: '1px solid #e2e8f0',
        background: '#ffffff'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '40px',
            height: '40px',
            borderRadius: '10px',
            background: 'linear-gradient(135deg, #1d4ed8, #2563eb)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#ffffff',
            boxShadow: '0 4px 10px rgba(37, 99, 235, 0.3)'
          }}>
            <ShieldCheck size={24} />
          </div>
          <div>
            <div style={{ fontSize: '18px', fontWeight: '800', letterSpacing: '-0.02em', color: '#0f172a' }}>
              CooBS <span style={{ color: '#2563eb' }}>Core Banking</span>
            </div>
            <div style={{ fontSize: '12px', color: '#64748b' }}>Enterprise Financial Platform</div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: '#059669', background: '#ecfdf5', padding: '6px 12px', borderRadius: '20px', border: '1px solid #a7f3d0' }}>
          <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#059669' }}></span>
          <span>TLS 1.3 256-Bit Encrypted Portal</span>
        </div>
      </header>

      {/* Main Login Container */}
      <main style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px 20px'
      }}>
        <div style={{
          width: '100%',
          maxWidth: '480px',
          background: '#ffffff',
          borderRadius: '16px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
          overflow: 'hidden'
        }}>
          {/* Dual Portal Switcher Tabs */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc'
          }}>
            <button
              type="button"
              onClick={() => handlePortalSwitch('CUSTOMER')}
              style={{
                padding: '16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                fontWeight: '600',
                fontSize: '14px',
                color: activePortal === 'CUSTOMER' ? '#1d4ed8' : '#64748b',
                background: activePortal === 'CUSTOMER' ? '#ffffff' : 'transparent',
                borderBottom: activePortal === 'CUSTOMER' ? '2px solid #2563eb' : '2px solid transparent',
                transition: 'all 0.2s'
              }}
            >
              <User size={18} />
              <span>Customer Portal</span>
            </button>

            <button
              type="button"
              onClick={() => handlePortalSwitch('STAFF')}
              style={{
                padding: '16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                fontWeight: '600',
                fontSize: '14px',
                color: activePortal === 'STAFF' ? '#1e293b' : '#64748b',
                background: activePortal === 'STAFF' ? '#ffffff' : 'transparent',
                borderBottom: activePortal === 'STAFF' ? '2px solid #0f172a' : '2px solid transparent',
                transition: 'all 0.2s'
              }}
            >
              <Briefcase size={18} />
              <span>Staff & Internal</span>
            </button>
          </div>

          <div style={{ padding: '32px' }}>
            <div style={{ marginBottom: '24px' }}>
              <h2 style={{ fontSize: '20px', fontWeight: '700', color: '#0f172a' }}>
                {activePortal === 'CUSTOMER' ? 'Online Banking Sign In' : 'Internal Staff Terminal'}
              </h2>
              <p style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
                {activePortal === 'CUSTOMER'
                  ? 'Access your deposit accounts, transfers, and KYC services.'
                  : 'Restricted to authorized branch tellers and bank administrators.'}
              </p>
            </div>

            {/* Error Banner */}
            {errorMessage && (
              <div style={{
                marginBottom: '20px',
                padding: '12px 14px',
                borderRadius: '8px',
                background: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#dc2626',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                fontSize: '13px'
              }}>
                <AlertCircle size={18} style={{ flexShrink: 0 }} />
                <span>{errorMessage}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                  Username / User ID
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Enter your username"
                    required
                    style={{ paddingLeft: '38px' }}
                  />
                  <User size={18} style={{ position: 'absolute', left: '12px', top: '11px', color: '#94a3b8' }} />
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#334155', marginBottom: '6px' }}>
                  Password
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    required
                    style={{ paddingLeft: '38px' }}
                  />
                  <Lock size={18} style={{ position: 'absolute', left: '12px', top: '11px', color: '#94a3b8' }} />
                </div>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="banking-btn-primary"
                style={{
                  marginTop: '8px',
                  padding: '12px',
                  fontSize: '15px',
                  background: activePortal === 'CUSTOMER' ? '#2563eb' : '#0f172a'
                }}
              >
                {isLoading ? (
                  <span>Authenticating...</span>
                ) : (
                  <>
                    <span>Sign In to {activePortal === 'CUSTOMER' ? 'Banking' : 'Terminal'}</span>
                    <ArrowRight size={18} />
                  </>
                )}
              </button>
            </form>

            {/* Quick Demo Logins Section */}
            <div style={{ marginTop: '28px', paddingTop: '20px', borderTop: '1px solid #f1f5f9' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: '600', color: '#64748b', marginBottom: '12px' }}>
                <Sparkles size={14} style={{ color: '#2563eb' }} />
                <span>Quick Demo Accounts:</span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <button
                  type="button"
                  onClick={() => handleDemoSelect('john_doe', 'Password123!', 'CUSTOMER')}
                  style={{
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #bfdbfe',
                    background: '#eff6ff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: '12px',
                    textAlign: 'left'
                  }}
                >
                  <div>
                    <span style={{ fontWeight: '700', color: '#1d4ed8' }}>Customer: </span>
                    <span style={{ color: '#334155' }}>john_doe</span>
                  </div>
                  <span className="badge badge-role-customer" style={{ padding: '2px 8px' }}>Personal Banking</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleDemoSelect('teller_alice', 'Password123!', 'STAFF')}
                  style={{
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #c7d2fe',
                    background: '#e0e7ff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: '12px',
                    textAlign: 'left'
                  }}
                >
                  <div>
                    <span style={{ fontWeight: '700', color: '#4338ca' }}>Teller: </span>
                    <span style={{ color: '#334155' }}>teller_alice</span>
                  </div>
                  <span className="badge badge-role-teller" style={{ padding: '2px 8px' }}>Branch Operations</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleDemoSelect('admin', 'Password123!', 'STAFF')}
                  style={{
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #f5d0fe',
                    background: '#fae8ff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: '12px',
                    textAlign: 'left'
                  }}
                >
                  <div>
                    <span style={{ fontWeight: '700', color: '#86198f' }}>Admin: </span>
                    <span style={{ color: '#334155' }}>admin</span>
                  </div>
                  <span className="badge badge-role-admin" style={{ padding: '2px 8px' }}>Supervision & Audit</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer style={{
        padding: '20px 32px',
        textAlign: 'center',
        fontSize: '12px',
        color: '#64748b',
        borderTop: '1px solid #e2e8f0',
        background: '#ffffff'
      }}>
        CooBS Core Banking System &bull; Double-Entry Accounting Ledger &bull; Oracle XE / PostgreSQL Audit Trail
      </footer>
    </div>
  );
};
