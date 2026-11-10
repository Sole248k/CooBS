import React from 'react';
import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { Sidebar } from './Sidebar';

export const AppLayout: React.FC = () => {
  return (
    <div className="h-screen max-h-screen w-screen overflow-hidden bg-[#F0F4F8] flex flex-col antialiased text-[#1E3A5F]">
      {/* Top Header */}
      <AppHeader />

      {/* Main Body: Sidebar + Content */}
      <div className="flex-1 flex overflow-hidden min-h-0">
        {/* Left Navigation Sidebar */}
        <Sidebar />

        {/* Dynamic Screen Content Canvas */}
        <main className="flex-1 overflow-y-auto p-6 md:p-8">
          <div className="max-w-7xl mx-auto space-y-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};
