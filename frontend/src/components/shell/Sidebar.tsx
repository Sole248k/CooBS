import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import {
  LayoutDashboard,
  Users,
  Coins,
  CheckSquare,
  CreditCard,
  GitBranch,
  ShieldAlert,
  CalendarClock,
  AlertTriangle,
} from 'lucide-react';

interface NavItem {
  name: string;
  path: string;
  icon: React.ElementType;
}

const TELLER_NAV_ITEMS: NavItem[] = [
  { name: 'Teller Workstation', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Counter Desk', path: '/counter', icon: Coins },
  { name: 'Customer 360', path: '/customer-360', icon: Users },
  { name: 'Accounts Directory', path: '/accounts', icon: CreditCard },
  { name: 'Review Queues', path: '/queues', icon: CheckSquare },
  { name: 'Tracker & Reversals', path: '/tracker', icon: GitBranch },
  { name: 'Merkle Audit Trail', path: '/audit', icon: ShieldAlert },
];

const ADMIN_NAV_ITEMS: NavItem[] = [
  { name: 'Command Center', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Dual-Control Queues', path: '/queues', icon: CheckSquare },
  { name: 'Governance & Accounts', path: '/accounts', icon: CreditCard },
  { name: 'End of Day Batch', path: '/admin/eod', icon: CalendarClock },
  { name: 'Dead Letters (DLQ)', path: '/admin/dlq', icon: AlertTriangle },
  { name: 'Forensic Merkle Audit', path: '/audit', icon: ShieldAlert },
  { name: 'Saga Tracker & Reversals', path: '/tracker', icon: GitBranch },
  { name: 'Customer 360', path: '/customer-360', icon: Users },
];

export const Sidebar: React.FC = () => {
  const { isAdmin } = useAuth();
  const navItems = isAdmin ? ADMIN_NAV_ITEMS : TELLER_NAV_ITEMS;

  return (
    <aside className="w-16 shrink-0 h-full bg-[#1E3A5F] flex flex-col items-center py-4 rounded-r-2xl shadow-xl z-30 select-none">
      {/* Brand Icon top */}
      <div className="mb-6">
        <div
          className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-lg shadow-inner border ${
            isAdmin
              ? 'bg-indigo-600/30 text-indigo-200 border-indigo-400/20'
              : 'bg-blue-600/30 text-blue-200 border-blue-400/20'
          }`}
        >
          <span>CB</span>
        </div>
      </div>

      {/* Nav List */}
      <nav className="flex-1 w-full flex flex-col items-center space-y-2">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `group relative w-11 h-11 rounded-xl flex items-center justify-center transition-all duration-200 ${
                  isActive
                    ? isAdmin
                      ? 'bg-[#4F46E5] text-white shadow-md shadow-indigo-950/60 scale-105'
                      : 'bg-[#2563EB] text-white shadow-md shadow-blue-900/50 scale-105'
                    : 'text-slate-300 hover:bg-[#264A73] hover:text-white'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={`w-5 h-5 ${isActive ? 'stroke-[2.5]' : 'stroke-2'}`} />

                  {/* Tooltip on hover */}
                  <div className="absolute left-16 px-2.5 py-1 bg-[#0F1E33] text-white text-xs font-medium rounded-md opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50 shadow-lg border border-slate-700">
                    {item.name}
                  </div>
                </>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* Security badge at bottom */}
      <div className="mt-auto text-slate-400 text-[10px] text-center font-mono tracking-tight opacity-70">
        {isAdmin ? 'ADMIN' : 'TELLER'}
        <br />
        v1.0
      </div>
    </aside>
  );
};
