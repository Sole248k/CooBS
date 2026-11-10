import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

export const StaffSignIn: React.FC = () => {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const [username, setUsername] = useState('teller_alice');
  const [password, setPassword] = useState('Password123!');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err: any) {
      if (err.message?.includes('GATEWAY_OFFLINE') || err.message?.includes('Failed to fetch')) {
        setError('The gateway cannot be reached. Please verify backend connectivity.');
      } else if (
        err.message?.includes('401') ||
        err.message?.includes('Bad credentials') ||
        err.message?.includes('authentication failed')
      ) {
        setError('Incorrect username or password');
      } else if (err.message?.includes('403')) {
        setError('Account is disabled or unauthorized for staff console access.');
      } else {
        setError(err.message || 'Incorrect username or password');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleQuickSelect = (userType: 'teller' | 'admin') => {
    if (userType === 'teller') {
      setUsername('teller_alice');
      setPassword('Password123!');
    } else {
      setUsername('admin');
      setPassword('Password123!');
    }
    setError(null);
  };

  return (
    <div className="min-h-screen bg-[#F0F4F8] flex flex-col justify-center items-center p-6 sm:p-10">
      {/* Central Square Login Card */}
      <div className="w-full max-w-[500px] sm:w-[500px] sm:min-h-[500px] bg-white rounded-2xl shadow-xl shadow-slate-200/70 border border-[#E2E8F0] p-8 sm:p-11 flex flex-col justify-between space-y-7">
        {/* Top: Logo & Headings */}
        <div className="space-y-4">
          {/* Brand Header */}
          <div className="flex flex-col items-center justify-center space-y-1.5">
            <img
              src="/coobs-logo.png"
              alt="CooBS"
              className="h-12 sm:h-13 object-contain max-w-[210px]"
            />
            <span className="text-sm font-semibold text-[#64748B] tracking-tight">
              Staff Console
            </span>
          </div>

          {/* Heading & Subtitle */}
          <div className="text-center pt-1">
            <h1 className="text-2xl sm:text-[26px] font-bold text-[#1E3A5F] tracking-tight">
              Sign in to your account
            </h1>
            <p className="text-sm text-[#64748B] mt-1.5">
              Enter your staff credentials to continue.
            </p>
          </div>
        </div>

        {/* Middle: Sign In Form */}
        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm font-semibold text-[#1E3A5F] mb-2">
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Username"
              required
              className="w-full px-4 py-3 text-base bg-white border border-[#CBD5E1] rounded-xl text-[#1E3A5F] placeholder-[#94A3B8] focus:outline-none focus:ring-2 focus:ring-[#2563EB] focus:border-transparent transition-all"
            />
          </div>

          <div>
            <label className="block text-sm font-semibold text-[#1E3A5F] mb-2">
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              required
              className="w-full px-4 py-3 text-base bg-white border border-[#CBD5E1] rounded-xl text-[#1E3A5F] placeholder-[#94A3B8] focus:outline-none focus:ring-2 focus:ring-[#2563EB] focus:border-transparent transition-all"
            />
          </div>

          {/* Error Message */}
          {error && (
            <div className="text-sm text-[#EF4444] font-medium pt-0.5">
              {error}
            </div>
          )}

          {/* Submit Button */}
          <button
            type="submit"
            disabled={loading}
            className="w-full mt-3 py-3.5 px-5 bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-base font-semibold rounded-xl shadow-sm hover:shadow transition-all flex items-center justify-center space-x-2 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {loading ? (
              <span className="inline-flex items-center space-x-2">
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                <span>Signing in...</span>
              </span>
            ) : (
              <span>Sign In</span>
            )}
          </button>
        </form>

        {/* Bottom: IT Support & Demo Profiles */}
        <div className="space-y-4 pt-1">
          <div className="text-center">
            <span className="text-sm text-[#64748B] hover:text-[#1E3A5F] transition-colors cursor-pointer">
              Contact IT Support if you cannot sign in
            </span>
          </div>

          {/* Quick Demo Environment Selector */}
          <div className="pt-3 border-t border-[#E2E8F0]">
            <div className="flex items-center justify-between text-xs text-[#64748B] font-medium">
              <span>Demo Profiles:</span>
              <div className="space-x-2">
                <button
                  type="button"
                  onClick={() => handleQuickSelect('teller')}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold border transition-colors ${
                    username === 'teller_alice'
                      ? 'bg-[#CCFBF1] text-[#0F766E] border-[#99F6E4]'
                      : 'bg-[#F8FAFC] text-[#64748B] border-[#E2E8F0] hover:bg-[#F1F5F9]'
                  }`}
                >
                  Teller (alice)
                </button>
                <button
                  type="button"
                  onClick={() => handleQuickSelect('admin')}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold border transition-colors ${
                    username === 'admin'
                      ? 'bg-[#EDE9FE] text-[#6D28D9] border-[#DDD6FE]'
                      : 'bg-[#F8FAFC] text-[#64748B] border-[#E2E8F0] hover:bg-[#F1F5F9]'
                  }`}
                >
                  Branch Manager
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
