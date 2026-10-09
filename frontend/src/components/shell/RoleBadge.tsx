import React from 'react';
import type { StaffRole } from '../../types/staff-bff';

interface RoleBadgeProps {
  role?: StaffRole | null;
  className?: string;
}

export const RoleBadge: React.FC<RoleBadgeProps> = ({ role, className = '' }) => {
  if (role === 'ROLE_ADMIN') {
    return (
      <span
        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#EEF2FF] text-[#4338CA] border border-[#C7D2FE] ${className}`}
      >
        <span className="w-1.5 h-1.5 rounded-full bg-[#4F46E5] mr-1.5"></span>
        Branch Manager
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#EFF6FF] text-[#1D4ED8] border border-[#BFDBFE] ${className}`}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-[#2563EB] mr-1.5"></span>
      Branch Teller
    </span>
  );
};
