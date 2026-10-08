import React from 'react';
import { ShieldCheck, Bell, LogOut, Users, Activity } from 'lucide-react';
import type { AuthSession } from '../types';

interface HeaderProps {
  session: AuthSession | null;
  gatewayStatus: string;
  latencyMs: number;
  unreadCount: number;
  onOpenNotifications: () => void;
  onSwitchUser: () => void;
  onLogout: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  session,
  gatewayStatus,
  latencyMs,
  unreadCount,
  onOpenNotifications,
  onSwitchUser,
  onLogout
}) => {
  const isCustomer = session?.userType === 'CUSTOMER';
  const isTeller = session?.userType === 'TELLER';
  const isAdmin = session?.userType === 'ADMIN';

  const getRoleBadge = () => {
    if (isCustomer) return <span className="badge badge-role-customer">Customer</span>;
    if (isTeller) return <span className="badge badge-role-teller">Branch Teller</span>;
    if (isAdmin) return <span className="badge badge-role-admin">System Admin</span>;
    return <span className="badge badge-neutral">User</span>;
  };

  return (
    <header style={{
      background: '#ffffff',
      borderBottom: '1px solid #e2e8f0',
      padding: '14px 28px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 40,
      boxShadow: '0 1px 3px rgba(0,0,0,0.04)'
    }}>
      {/* Brand Identity */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        <div style={{
          width: '38px',
          height: '38px',
          borderRadius: '9px',
          background: 'linear-gradient(135deg, #1d4ed8, #2563eb)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#ffffff',
          boxShadow: '0 2px 8px rgba(37, 99, 235, 0.25)'
        }}>
          <ShieldCheck size={22} />
        </div>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '17px', fontWeight: '800', color: '#0f172a', letterSpacing: '-0.02em' }}>
              CooBS <span style={{ color: '#2563eb' }}>Banking</span>
            </span>
            {getRoleBadge()}
          </div>
          <div style={{ fontSize: '11px', color: '#64748b' }}>
            {isCustomer && 'Online Personal & Business Banking'}
            {isTeller && 'Teller & Branch Operations Terminal'}
            {isAdmin && 'Supervisory & Ledger Administration Console'}
          </div>
        </div>
      </div>

      {/* Right Controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        {/* Gateway Health Indicator */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          fontSize: '12px',
          color: '#059669',
          background: '#ecfdf5',
          padding: '4px 10px',
          borderRadius: '20px',
          border: '1px solid #a7f3d0'
        }}>
          <Activity size={13} />
          <span>Core Gateway: {gatewayStatus}</span>
          <span style={{ color: '#047857', fontWeight: '600' }}>({latencyMs}ms)</span>
        </div>

        {/* Notification Bell */}
        <button
          type="button"
          onClick={onOpenNotifications}
          style={{
            position: 'relative',
            width: '38px',
            height: '38px',
            borderRadius: '8px',
            border: '1px solid #e2e8f0',
            background: '#ffffff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#334155'
          }}
          title="Notifications"
        >
          <Bell size={18} />
          {unreadCount > 0 && (
            <span style={{
              position: 'absolute',
              top: '-4px',
              right: '-4px',
              background: '#dc2626',
              color: '#ffffff',
              fontSize: '10px',
              fontWeight: '700',
              borderRadius: '10px',
              padding: '1px 6px',
              border: '2px solid #ffffff'
            }}>
              {unreadCount}
            </span>
          )}
        </button>

        {/* User Identity Details */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '4px 12px',
          borderRadius: '8px',
          background: '#f8fafc',
          border: '1px solid #e2e8f0'
        }}>
          <div style={{
            width: '30px',
            height: '30px',
            borderRadius: '50%',
            background: isCustomer ? '#dbeafe' : (isTeller ? '#e0e7ff' : '#fae8ff'),
            color: isCustomer ? '#1d4ed8' : (isTeller ? '#4338ca' : '#86198f'),
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: '700',
            fontSize: '13px'
          }}>
            {session?.username ? session.username.charAt(0).toUpperCase() : 'U'}
          </div>
          <div style={{ textAlign: 'left' }}>
            <div style={{ fontSize: '13px', fontWeight: '600', color: '#0f172a' }}>
              {session?.username || 'Guest'}
            </div>
            <div style={{ fontSize: '11px', color: '#64748b' }}>
              ID: #{session?.userId || 1}
            </div>
          </div>
        </div>

        {/* Switch User Action */}
        <button
          type="button"
          onClick={onSwitchUser}
          className="banking-btn-secondary"
          style={{ padding: '7px 12px', fontSize: '12px' }}
          title="Switch User / Portal"
        >
          <Users size={14} />
          <span>Switch Portal</span>
        </button>

        {/* Logout Action */}
        <button
          type="button"
          onClick={onLogout}
          className="banking-btn-secondary"
          style={{ padding: '7px 12px', fontSize: '12px', color: '#dc2626', borderColor: '#fecaca' }}
          title="Log Out"
        >
          <LogOut size={14} />
          <span>Log Out</span>
        </button>
      </div>
    </header>
  );
};
