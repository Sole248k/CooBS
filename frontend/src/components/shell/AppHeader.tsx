import React, { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { RoleBadge } from './RoleBadge';
import { LogOut, Building2, Activity, ShieldCheck } from 'lucide-react';

export const AppHeader: React.FC = () => {
  const { user, role, logout } = useAuth();
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
      {/* Left: Brand & Branch Context */}
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2.5">
          <img
            src="/coobs-logo.png"
            alt="CooBS"
            className="h-7 object-contain"
          />
          <div className="h-4 w-px bg-[#E2E8F0]" />
          <span className="text-[11px] font-semibold text-[#64748B] tracking-tight">
            Staff Console
          </span>
        </div>


        <div className="h-4 w-px bg-[#E2E8F0]" />

        {/* Branch Pill */}
        <div className="flex items-center space-x-1.5 px-2.5 py-1 rounded-md bg-[#F0F4F8] text-[#1E3A5F] text-xs font-semibold border border-[#E2E8F0]">
          <Building2 className="w-3.5 h-3.5 text-[#64748B]" />
          <span>{user?.branchCode || 'BR-0104 MAKATI'}</span>
        </div>
      </div>

      {/* Right: Gateway Latency, Role Badge, User, Logout */}
      <div className="flex items-center space-x-4">
        {/* Gateway Health Indicator */}
        <div className="hidden sm:flex items-center space-x-1.5 text-xs text-[#64748B] font-mono bg-[#F8FAFC] px-2.5 py-1 rounded-md border border-[#E2E8F0]">
          <span className="w-2 h-2 rounded-full bg-[#10B981] animate-pulse" />
          <span>Gateway: {latency}ms</span>
        </div>

        {/* Role Badge */}
        <RoleBadge role={role} />

        {/* User Pill */}
        <div className="flex items-center space-x-2 text-xs font-medium text-[#1E3A5F] bg-[#F0F4F8] px-2.5 py-1 rounded-md border border-[#E2E8F0]">
          <ShieldCheck className="w-3.5 h-3.5 text-[#2563EB]" />
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
