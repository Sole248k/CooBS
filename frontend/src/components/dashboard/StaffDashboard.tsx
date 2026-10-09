import React from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { AdminDashboard } from './AdminDashboard';
import { TellerDashboard } from './TellerDashboard';

export const StaffDashboard: React.FC = () => {
  const { isAdmin } = useAuth();

  if (isAdmin) {
    return <AdminDashboard />;
  }

  return <TellerDashboard />;
};

export default StaffDashboard;
