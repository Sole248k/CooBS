import type { FC } from 'react';
import { ShieldCheck, LogOut, Users, Activity } from 'lucide-react';
import type { AuthSession } from '../types';

interface HeaderProps {
  session: AuthSession | null;
  gatewayStatus: string;
  latencyMs: number;
  onSwitchUser: () => void;
  onLogout: () => void;
}

export const Header: FC<HeaderProps> = ({
  session,
  gatewayStatus,
  latencyMs,
  onSwitchUser,
  onLogout
}) => {
  const isTeller = session?.userType === 'TELLER';
  const isAdmin = session?.userType === 'ADMIN';

  const getRoleBadge = () => {
    if (isTeller) return <span className="badge badge-role-teller">Branch Teller</span>;
    if (isAdmin) return <span className="badge badge-role-admin">Branch Manager</span>;
    return <span className="badge badge-neutral">Staff</span>;
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
              CooBS <span style={{ color: '#2563eb' }}>Staff Console</span>
            </span>
            {getRoleBadge()}
          </div>
          <div style={{ fontSize: '11px', color: '#64748b' }}>
            {isTeller && 'Teller & Branch Operations Terminal (WEB-03 to WEB-09)'}
            {isAdmin && 'Supervisory & Back-Office Branch Management (WEB-03 to WEB-10)'}
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
            width: '28px',
            height: '28px',
            borderRadius: '50%',
            background: isTeller ? '#4338ca' : '#0f172a',
            color: '#ffffff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '11px',
            fontWeight: '700'
          }}>
            {(session?.username || 'ST').substring(0, 2).toUpperCase()}
          </div>
          <div style={{ textAlign: 'left' }}>
            <div style={{ fontSize: '13px', fontWeight: '700', color: '#0f172a' }}>
              {session?.username || 'Staff'}
            </div>
            <div style={{ fontSize: '10px', color: '#64748b' }}>
              {isTeller ? 'Branch Staff' : 'Branch Manager'}
            </div>
          </div>
        </div>

        {/* Switch Staff Role Button */}
        <button
          type="button"
          onClick={onSwitchUser}
          style={{
            padding: '7px 12px',
            fontSize: '12px',
            fontWeight: '600',
            color: '#475569',
            background: '#ffffff',
            border: '1px solid #cbd5e1',
            borderRadius: '8px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }}
          title="Switch between Teller and Branch Manager"
        >
          <Users size={14} />
          <span>Switch Role</span>
        </button>

        {/* Safe Logout Button */}
        <button
          type="button"
          onClick={onLogout}
          style={{
            padding: '7px 12px',
            fontSize: '12px',
            fontWeight: '600',
            color: '#dc2626',
            background: '#fef2f2',
            border: '1px solid #fecaca',
            borderRadius: '8px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }}
        >
          <LogOut size={14} />
          <span>Sign Out</span>
        </button>
      </div>
    </header>
  );
};
