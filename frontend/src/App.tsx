import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './contexts/AuthContext';
import { AppLayout } from './components/shell/AppLayout';
import { StaffSignIn } from './components/shell/StaffSignIn';
import { StaffDashboard } from './components/dashboard/StaffDashboard';
import { Customer360 } from './components/customer/Customer360';
import { CounterDesk } from './components/counter/CounterDesk';
import { QueuesManager } from './components/queues/QueuesManager';
import { AccountDirectory } from './components/accounts/AccountDirectory';
import { TransactionTracker } from './components/tracker/TransactionTracker';
import { AuditStatement } from './components/audit/AuditStatement';
import { EODBatchPipeline } from './components/admin/EODBatchPipeline';
import { DeadLetterQueue } from './components/admin/DeadLetterQueue';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#F0F4F8] flex items-center justify-center">
        <div className="flex flex-col items-center space-y-3">
          <div className="w-8 h-8 border-3 border-[#2563EB]/20 border-t-[#2563EB] rounded-full animate-spin" />
          <span className="text-xs font-semibold text-[#1E3A5F]">Loading CooBS Staff Console...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

const AdminRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAdmin, isLoading } = useAuth();

  if (isLoading) {
    return null;
  }

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

const FallbackRoute: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return null;
  }
  return <Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />;
};

export const App: React.FC = () => {
  return (
    <Routes>
      {/* Public Authentication Route */}
      <Route path="/login" element={<StaffSignIn />} />

      {/* Authenticated Staff Shell */}
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<StaffDashboard />} />
        <Route path="customer-360" element={<Customer360 />} />
        <Route path="counter" element={<CounterDesk />} />
        <Route path="queues" element={<QueuesManager />} />
        <Route path="accounts" element={<AccountDirectory />} />
        <Route path="tracker" element={<TransactionTracker />} />
        <Route path="audit" element={<AuditStatement />} />
        
        {/* Admin Exclusive Workspaces */}
        <Route
          path="admin/eod"
          element={
            <AdminRoute>
              <EODBatchPipeline />
            </AdminRoute>
          }
        />
        <Route
          path="admin/dlq"
          element={
            <AdminRoute>
              <DeadLetterQueue />
            </AdminRoute>
          }
        />
      </Route>

      {/* Fallback */}
      <Route path="*" element={<FallbackRoute />} />
    </Routes>
  );
};

export default App;
