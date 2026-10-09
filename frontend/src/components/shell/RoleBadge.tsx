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
        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#EDE9FE] text-[#6D28D9] border border-[#DDD6FE] ${className}`}
      >
        <span className="w-1.5 h-1.5 rounded-full bg-[#6D28D9] mr-1.5"></span>
        System Admin
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#CCFBF1] text-[#0F766E] border border-[#99F6E4] ${className}`}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-[#0F766E] mr-1.5"></span>
      Branch Teller
    </span>
  );
};
