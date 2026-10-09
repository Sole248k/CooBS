import React, { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { RoleBadge } from './RoleBadge';
import { LogOut, Building2, Coins, Server, ShieldCheck, Activity } from 'lucide-react';

export const AppHeader: React.FC = () => {
  const { user, role, isAdmin, logout } = useAuth();
  const [latency, setLatency] = useState<number>(3);

  // Subtle simulated jitter between 2ms - 5ms for gateway latency readout
  useEffect(() => {
    const timer = setInterval(() => {
      setLatency(Math.floor(Math.random() * 4) + 2);
    }, 15000);
    return () => clearInterval(timer);
  }, []);

  return (
    <header className="sticky top-0 z-40 shrink-0 h-[56px] w-full bg-white border-b border-[#E2E8F0] px-6 flex items-center justify-between select-none shadow-sm">
      {/* Left: Brand & Branch / Domain Context */}
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2.5">
          <img
            src="/coobs-logo.png"
            alt="CooBS"
            className="h-7 object-contain"
          />
          <div className="h-4 w-px bg-[#E2E8F0]" />
          <span className="text-[11px] font-semibold text-[#64748B] tracking-tight">
            {isAdmin ? 'Branch Manager Console' : 'Teller Terminal'}
          </span>
        </div>

        <div className="h-4 w-px bg-[#E2E8F0]" />

        {/* Location / Branch Pill */}
        <div className="flex items-center space-x-1.5 px-2.5 py-1 rounded-md bg-[#F0F4F8] text-[#1E3A5F] text-xs font-semibold border border-[#E2E8F0]">
          {isAdmin ? (
            <>
              <Server className="w-3.5 h-3.5 text-indigo-600" />
              <span>{user?.branchCode || 'BR-0104 MAKATI'} &bull; MANAGER</span>
            </>
          ) : (
            <>
              <Building2 className="w-3.5 h-3.5 text-blue-600" />
              <span>{user?.branchCode || 'BR-0104 MAKATI'}</span>
            </>
          )}
        </div>
      </div>

      {/* Right: Operational Status, Gateway Latency, Role Badge, User, Logout */}
      <div className="flex items-center space-x-3 sm:space-x-4">
        {/* Role-Specific Status Pill */}
        {isAdmin ? (
          <div className="hidden lg:flex items-center space-x-1.5 text-xs text-[#3730A3] font-medium bg-[#EEF2FF] px-2.5 py-1 rounded-md border border-[#C7D2FE]">
            <Activity className="w-3.5 h-3.5 text-[#4F46E5]" />
            <span>6/6 Core Services Active</span>
          </div>
        ) : (
          <div className="hidden lg:flex items-center space-x-1.5 text-xs text-[#1D4ED8] font-medium bg-[#EFF6FF] px-2.5 py-1 rounded-md border border-[#BFDBFE]">
            <Coins className="w-3.5 h-3.5 text-blue-600" />
            <span>Till Float: Balanced</span>
          </div>
        )}

        {/* Gateway Health Indicator */}
        <div className="hidden sm:flex items-center space-x-1.5 text-xs text-[#64748B] font-mono bg-[#F8FAFC] px-2.5 py-1 rounded-md border border-[#E2E8F0]">
          <span className="w-2 h-2 rounded-full bg-[#10B981] animate-pulse" />
          <span>Gateway: {latency}ms</span>
        </div>

        {/* Role Badge */}
        <RoleBadge role={role} />

        {/* User Pill */}
        <div className="flex items-center space-x-2 text-xs font-medium text-[#1E3A5F] bg-[#F0F4F8] px-2.5 py-1 rounded-md border border-[#E2E8F0]">
          <ShieldCheck className={`w-3.5 h-3.5 ${isAdmin ? 'text-indigo-600' : 'text-blue-600'}`} />
          <span className="font-mono">@{user?.username || 'staff'}</span>
        </div>

        <div className="h-4 w-px bg-[#E2E8F0]" />

        {/* Logout Button */}
        <button
          onClick={() => logout()}
          className="flex items-center space-x-1.5 text-xs font-semibold text-[#64748B] hover:text-[#EF4444] px-2 py-1 rounded-md hover:bg-[#FEE2E2]/40 transition-colors"
          title="Sign out of staff console"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span className="hidden md:inline">Sign Out</span>
        </button>
      </div>
    </header>
  );
};
