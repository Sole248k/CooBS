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
  adminOnly?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { name: 'Customer 360', path: '/customer-360', icon: Users },
  { name: 'Counter Desk', path: '/counter', icon: Coins },
  { name: 'Queues', path: '/queues', icon: CheckSquare },
  { name: 'Accounts', path: '/accounts', icon: CreditCard },
  { name: 'Tracker & Reversal', path: '/tracker', icon: GitBranch },
  { name: 'Merkle Audit', path: '/audit', icon: ShieldAlert },
  { name: 'End of Day Batch', path: '/admin/eod', icon: CalendarClock },
  { name: 'Dead Letters (DLQ)', path: '/admin/dlq', icon: AlertTriangle, adminOnly: true },
];

export const Sidebar: React.FC = () => {
  const { isAdmin } = useAuth();

  return (
    <aside className="w-16 bg-[#1E3A5F] flex flex-col items-center py-4 rounded-r-2xl shadow-xl z-30 select-none">
      {/* Brand Icon top */}
      <div className="mb-6">
        <div className="w-10 h-10 rounded-xl bg-blue-600/30 flex items-center justify-center text-white border border-blue-400/20 shadow-inner">
          <span className="font-bold text-lg text-blue-200">CB</span>
        </div>
      </div>

      {/* Nav List */}
      <nav className="flex-1 w-full flex flex-col items-center space-y-2">
        {NAV_ITEMS.map((item) => {
          if (item.adminOnly && !isAdmin) {
            return null;
          }

          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `group relative w-11 h-11 rounded-xl flex items-center justify-center transition-all duration-200 ${
                  isActive
                    ? 'bg-[#2563EB] text-white shadow-md shadow-blue-900/50 scale-105'
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
        STAFF
        <br />
        v1.0
      </div>
    </aside>
  );
};
