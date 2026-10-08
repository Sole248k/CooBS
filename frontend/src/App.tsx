import { useState, useEffect, useCallback } from 'react';
import { Header } from './components/Header';
import { LoginPage } from './components/LoginPage';
import { CustomerWorkspace } from './components/CustomerWorkspace';
import { TellerWorkspace } from './components/TellerWorkspace';
import { AdminWorkspace } from './components/AdminWorkspace';
import { NotificationDrawer } from './components/NotificationDrawer';
import { apiClient } from './services/api';
import type { Account, AuthSession, NotificationItem, TransferRequest, TransferResponse } from './types';
import { CheckCircle2, AlertCircle, Info, X, ShieldCheck, User, Briefcase } from 'lucide-react';

interface Toast {
  type: 'success' | 'info' | 'error';
  message: string;
}

export function App() {
  const [session, setSession] = useState<AuthSession | null>(apiClient.getSession());
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [gatewayStatus, setGatewayStatus] = useState<string>('ONLINE');
  const [latencyMs, setLatencyMs] = useState<number>(3);
  const [isNotificationOpen, setIsNotificationOpen] = useState<boolean>(false);
  const [isSwitchUserOpen, setIsSwitchUserOpen] = useState<boolean>(false);
  const [toast, setToast] = useState<Toast | null>(null);

  const showToast = (type: 'success' | 'info' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => {
      setToast(null);
    }, 4500);
  };

  const loadData = useCallback(async () => {
    if (!session) return;
    try {
      const health = await apiClient.checkGatewayHealth();
      setGatewayStatus(health.status);
      setLatencyMs(health.latencyMs);

      const accList = await apiClient.getAccounts();
      setAccounts(accList);

      const notifList = await apiClient.getNotifications();
      setNotifications(notifList);
    } catch {
      // offline fallback handled by api client
    }
  }, [session]);

  useEffect(() => {
    loadData();
    const interval = setInterval(() => {
      if (session) {
        apiClient.checkGatewayHealth().then(h => {
          setGatewayStatus(h.status);
          setLatencyMs(h.latencyMs);
        }).catch(() => {});
      }
    }, 25000);

    return () => clearInterval(interval);
  }, [session, loadData]);

  // Banking Standard: 15-minute Session Inactivity Timeout (PCI-DSS 4.0 / BSP)
  useEffect(() => {
    if (!session) return;
    const INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000;
    let timer: ReturnType<typeof setTimeout>;

    const resetTimer = () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        await apiClient.logout();
        setSession(null);
        setIsSwitchUserOpen(false);
        setIsNotificationOpen(false);
        showToast('error', 'Session expired due to 15 minutes of inactivity for your security.');
      }, INACTIVITY_TIMEOUT_MS);
    };

    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach(evt => window.addEventListener(evt, resetTimer, { passive: true }));
    resetTimer();

    return () => {
      clearTimeout(timer);
      events.forEach(evt => window.removeEventListener(evt, resetTimer));
    };
  }, [session]);

  // Handle Login
  const handleLoginSuccess = (newSession: AuthSession) => {
    setSession(newSession);
    showToast('success', `Welcome back, ${newSession.username}! Authenticated as ${newSession.userType}.`);
  };

  // Handle Logout
  const handleLogout = async () => {
    await apiClient.logout();
    setSession(null);
    setIsSwitchUserOpen(false);
    setIsNotificationOpen(false);
    showToast('info', 'You have been safely signed out.');
  };

  // Fast Switch Role
  const handleFastSwitch = async (role: 'CUSTOMER' | 'TELLER' | 'ADMIN') => {
    setIsSwitchUserOpen(false);
    let s: AuthSession;
    if (role === 'CUSTOMER') {
      s = await apiClient.loginCustomer('john_doe', 'Password123!');
    } else if (role === 'TELLER') {
      s = await apiClient.loginStaff('teller_alice', 'Password123!');
    } else {
      s = await apiClient.loginStaff('admin', 'Password123!');
    }
    setSession(s);
    loadData();
    showToast('info', `Switched active portal to ${role} (${s.username})`);
  };

  // Transfer Execution
  const handleExecuteTransfer = async (
    payload: TransferRequest,
    idempotencyKey: string,
    correlationId: string
  ): Promise<{ response: TransferResponse; isCachedReplay: boolean; latencyMs: number }> => {
    const result = await apiClient.executeTransfer(payload, idempotencyKey, correlationId);
    await loadData();
    showToast('success', `Transfer of ₱${payload.amount.toFixed(2)} completed! Ref: ${payload.referenceNo}`);
    return result;
  };

  const handleMarkAsRead = async (id: number) => {
    await apiClient.markNotificationAsRead(id);
    const updated = await apiClient.getNotifications();
    setNotifications([...updated]);
  };

  const unreadCount = notifications.filter(n => !n.isRead).length;

  // 1. If not authenticated, render Dedicated Dual-Portal LoginPage
  if (!session) {
    return (
      <LoginPage
        onLoginSuccess={handleLoginSuccess}
        onLoginCustomer={(u, p) => apiClient.loginCustomer(u, p)}
        onLoginStaff={(u, p) => apiClient.loginStaff(u, p)}
      />
    );
  }

  // 2. Authenticated Application
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg-base)' }}>
      {/* Toast Notification Pill */}
      {toast && (
        <div style={{
          position: 'fixed',
          bottom: '24px',
          right: '24px',
          zIndex: 1000,
          padding: '12px 20px',
          borderRadius: '10px',
          background: toast.type === 'success' ? '#059669' : (toast.type === 'error' ? '#dc2626' : '#2563eb'),
          color: '#ffffff',
          boxShadow: '0 10px 25px rgba(0,0,0,0.15)',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          fontSize: '13px',
          fontWeight: '600',
          animation: 'fadeIn 0.25s ease-out'
        }}>
          {toast.type === 'success' && <CheckCircle2 size={18} />}
          {toast.type === 'error' && <AlertCircle size={18} />}
          {toast.type === 'info' && <Info size={18} />}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Application Header */}
      <Header
        session={session}
        gatewayStatus={gatewayStatus}
        latencyMs={latencyMs}
        unreadCount={unreadCount}
        onOpenNotifications={() => setIsNotificationOpen(true)}
        onSwitchUser={() => setIsSwitchUserOpen(true)}
        onLogout={handleLogout}
      />

      {/* Role Workspace View */}
      <main style={{ flex: 1, paddingBottom: '48px' }}>
        {session.userType === 'CUSTOMER' && (
          <CustomerWorkspace
            accounts={accounts}
            onRefreshAccounts={loadData}
            onExecuteTransfer={handleExecuteTransfer}
          />
        )}

        {session.userType === 'TELLER' && (
          <TellerWorkspace
            onRefreshGlobalData={loadData}
            showToast={showToast}
          />
        )}

        {session.userType === 'ADMIN' && (
          <AdminWorkspace
            onRefreshGlobalData={loadData}
            showToast={showToast}
          />
        )}
      </main>

      {/* Role-Specific Notification Drawer */}
      <NotificationDrawer
        isOpen={isNotificationOpen}
        onClose={() => setIsNotificationOpen(false)}
        notifications={notifications}
        onMarkAsRead={handleMarkAsRead}
        session={session}
      />

      {/* Switch Portal / User Modal */}
      {isSwitchUserOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(15, 23, 42, 0.5)',
          backdropFilter: 'blur(3px)',
          zIndex: 100,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '20px'
        }}>
          <div style={{
            background: '#ffffff',
            borderRadius: '16px',
            maxWidth: '460px',
            width: '100%',
            padding: '28px',
            boxShadow: 'var(--shadow-modal)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '18px', fontWeight: '700', color: '#0f172a' }}>Switch Active Identity / Portal</h3>
              <button type="button" onClick={() => setIsSwitchUserOpen(false)} style={{ color: '#64748b' }}>
                <X size={20} />
              </button>
            </div>

            <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '20px' }}>
              Switch instantly between customer and internal staff access to test end-to-end multi-role workflows.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <button
                type="button"
                onClick={() => handleFastSwitch('CUSTOMER')}
                style={{
                  padding: '14px',
                  borderRadius: '10px',
                  border: session.userType === 'CUSTOMER' ? '2px solid #2563eb' : '1px solid #e2e8f0',
                  background: session.userType === 'CUSTOMER' ? '#eff6ff' : '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  textAlign: 'left'
                }}
              >
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '8px',
                  background: '#dbeafe',
                  color: '#1d4ed8',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <User size={18} />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '14px', fontWeight: '700', color: '#0f172a' }}>
                    Customer: John Doe (john_doe)
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b' }}>
                    View deposit accounts, transfer funds, submit KYC update & closure requests
                  </div>
                </div>
              </button>

              <button
                type="button"
                onClick={() => handleFastSwitch('TELLER')}
                style={{
                  padding: '14px',
                  borderRadius: '10px',
                  border: session.userType === 'TELLER' ? '2px solid #4338ca' : '1px solid #e2e8f0',
                  background: session.userType === 'TELLER' ? '#e0e7ff' : '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  textAlign: 'left'
                }}
              >
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '8px',
                  background: '#e0e7ff',
                  color: '#4338ca',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <Briefcase size={18} />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '14px', fontWeight: '700', color: '#0f172a' }}>
                    Branch Teller: Alice Vance (teller_alice)
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b' }}>
                    Verify pending KYC updates, authorize high transfers, over-the-counter cash desk
                  </div>
                </div>
              </button>

              <button
                type="button"
                onClick={() => handleFastSwitch('ADMIN')}
                style={{
                  padding: '14px',
                  borderRadius: '10px',
                  border: session.userType === 'ADMIN' ? '2px solid #86198f' : '1px solid #e2e8f0',
                  background: session.userType === 'ADMIN' ? '#fae8ff' : '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  textAlign: 'left'
                }}
              >
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '8px',
                  background: '#fae8ff',
                  color: '#86198f',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <ShieldCheck size={18} />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '14px', fontWeight: '700', color: '#0f172a' }}>
                    System Admin: Core Administrator (admin)
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b' }}>
                    Approve account closures, inspect transaction history, verify SHA-256 chains
                  </div>
                </div>
              </button>
            </div>

            <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #e2e8f0', display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={handleLogout}
                className="banking-btn-secondary"
                style={{ color: '#dc2626', borderColor: '#fecaca' }}
              >
                Sign Out Completely
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Platform Architecture Footer */}
      <footer style={{
        marginTop: 'auto',
        borderTop: '1px solid var(--border-subtle)',
        background: '#ffffff',
        padding: '20px 32px',
        fontSize: '12px',
        color: '#64748b'
      }}>
        <div style={{
          maxWidth: '1200px',
          margin: '0 auto',
          display: 'flex',
          flexWrap: 'wrap',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: '14px'
        }}>
          <div>
            <span style={{ fontWeight: '700', color: '#0f172a' }}>CooBS Core Banking System</span> &mdash; Fullstack Capstone Architecture
            <div style={{ marginTop: '3px' }}>
              Microservices: Edge Gateway (8080) &bull; Auth (8081) &bull; Account (8082) &bull; Ledger (8083) &bull; Notification (8084) &bull; Audit (8085)
            </div>
          </div>
          <div style={{ display: 'flex', gap: '14px', fontFamily: 'var(--font-mono)' }}>
            <span>Oracle Free 23c (1522)</span>
            <span>&bull;</span>
            <span>PostgreSQL 16 (5434)</span>
            <span>&bull;</span>
            <span>Redis 7.4 (6379)</span>
            <span>&bull;</span>
            <span>Kafka KRaft (9092)</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
