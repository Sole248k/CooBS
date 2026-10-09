import { X, Bell, CheckCheck, CreditCard, ArrowRightLeft, ShieldCheck, XCircle, Info } from 'lucide-react';
import type { AuthSession, NotificationItem } from '../types';

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  notifications: NotificationItem[];
  onMarkAsRead: (id: number) => void;
  session: AuthSession | null;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({
  isOpen,
  onClose,
  notifications,
  onMarkAsRead,
  session
}) => {
  if (!isOpen) return null;

  const isCustomer = session?.userType === 'CUSTOMER';
  const isTeller = session?.userType === 'TELLER';
  const isAdmin = session?.userType === 'ADMIN';

  const getIconForType = (type: string) => {
    switch (type) {
      case 'TRANSACTION_CREDIT':
        return <CreditCard size={18} style={{ color: '#059669' }} />;
      case 'TRANSACTION_TRANSFER':
      case 'TRANSACTION_DEBIT':
        return <ArrowRightLeft size={18} style={{ color: '#2563eb' }} />;
      case 'KYC_STATUS':
      case 'KYC_REVIEW_REQUIRED':
        return <ShieldCheck size={18} style={{ color: '#0284c7' }} />;
      case 'CLOSURE_STATUS':
      case 'CLOSURE_REVIEW_REQUIRED':
        return <XCircle size={18} style={{ color: '#dc2626' }} />;
      default:
        return <Info size={18} style={{ color: '#64748b' }} />;
    }
  };

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      background: 'rgba(15, 23, 42, 0.4)',
      backdropFilter: 'blur(3px)',
      zIndex: 90,
      display: 'flex',
      justifyContent: 'flex-end',
      animation: 'fadeIn 0.2s ease-out'
    }}>
      <div style={{
        width: '100%',
        maxWidth: '420px',
        height: '100%',
        background: '#ffffff',
        boxShadow: '-4px 0 25px rgba(0,0,0,0.1)',
        display: 'flex',
        flexDirection: 'column',
        borderLeft: '1px solid #e2e8f0'
      }}>
        {/* Header */}
        <div style={{
          padding: '20px 24px',
          borderBottom: '1px solid #e2e8f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#f8fafc'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              background: '#eff6ff',
              color: '#2563eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Bell size={18} />
            </div>
            <div>
              <div style={{ fontSize: '15px', fontWeight: '700', color: '#0f172a' }}>
                {isCustomer && 'Customer Activity Notices'}
                {isTeller && 'Branch Teller Alert Feed'}
                {isAdmin && 'Supervisory Audit Alerts'}
              </div>
              <div style={{ fontSize: '11px', color: '#64748b' }}>
                {notifications.filter(n => !n.isRead).length} unread notices
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            style={{
              padding: '6px',
              borderRadius: '6px',
              color: '#64748b'
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* List of Notifications */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
          {notifications.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 10px', color: '#94a3b8' }}>
              <Bell size={36} style={{ margin: '0 auto 10px', opacity: 0.5 }} />
              <div style={{ fontSize: '14px', fontWeight: '600' }}>No notifications</div>
              <div style={{ fontSize: '12px', marginTop: '4px' }}>You're all caught up with recent activity.</div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => !n.isRead && onMarkAsRead(n.id)}
                  style={{
                    padding: '14px',
                    borderRadius: '10px',
                    border: '1px solid',
                    borderColor: n.isRead ? '#f1f5f9' : '#bfdbfe',
                    background: n.isRead ? '#ffffff' : '#eff6ff',
                    cursor: n.isRead ? 'default' : 'pointer',
                    transition: 'all 0.15s ease',
                    position: 'relative'
                  }}
                >
                  <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                    <div style={{
                      marginTop: '2px',
                      padding: '6px',
                      borderRadius: '8px',
                      background: n.isRead ? '#f1f5f9' : '#ffffff',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
                    }}>
                      {getIconForType(n.type)}
                    </div>

                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '13px', color: '#0f172a', fontWeight: n.isRead ? '400' : '600', lineHeight: 1.4 }}>
                        {n.message}
                      </div>
                      <div style={{ fontSize: '11px', color: '#64748b', marginTop: '6px' }}>
                        {new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' })}
                      </div>
                    </div>

                    {!n.isRead && (
                      <span style={{
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        background: '#2563eb',
                        flexShrink: 0,
                        marginTop: '6px'
                      }} />
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div style={{
          padding: '16px 20px',
          borderTop: '1px solid #e2e8f0',
          background: '#f8fafc',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: '12px'
        }}>
          <span style={{ color: '#64748b' }}>Real-time SSE Notification Stream</span>
          <button
            type="button"
            onClick={() => {
              notifications.forEach(n => !n.isRead && onMarkAsRead(n.id));
            }}
            style={{
              color: '#2563eb',
              fontWeight: '600',
              display: 'flex',
              alignItems: 'center',
              gap: '4px'
            }}
          >
            <CheckCheck size={14} />
            <span>Mark all read</span>
          </button>
        </div>
      </div>
    </div>
  );
};
