import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { StaffRole, AuthData } from '../types/staff-bff';
import { staffApi } from '../services/api';

export interface StaffUser {
  userId: string;
  username: string;
  roles: StaffRole[];
  branchCode: string;
}

interface AuthContextType {
  user: StaffUser | null;
  token: string | null;
  role: StaffRole | null;
  isTeller: boolean;
  isAdmin: boolean;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (role: StaffRole) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const TOKEN_KEY = 'coobs_staff_token';
const USER_KEY = 'coobs_staff_user';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<StaffUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Initialize from localStorage
  useEffect(() => {
    try {
      const savedToken = localStorage.getItem(TOKEN_KEY);
      const savedUserStr = localStorage.getItem(USER_KEY);
      if (savedToken && savedUserStr) {
        const parsedUser: StaffUser = JSON.parse(savedUserStr);
        setToken(savedToken);
        setUser(parsedUser);
        staffApi.setAuthToken(savedToken);
      }
    } catch (err) {
      console.error('Failed to parse saved staff auth session', err);
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const authData: AuthData = await staffApi.login({ username, password });
    
    const staffUser: StaffUser = {
      userId: authData.userId,
      username: authData.username,
      roles: authData.roles,
      branchCode: authData.branchCode || 'BR-0104 MAKATI',
    };

    setToken(authData.accessToken);
    setUser(staffUser);
    staffApi.setAuthToken(authData.accessToken);

    localStorage.setItem(TOKEN_KEY, authData.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(staffUser));
  }, []);

  const logout = useCallback(async () => {
    try {
      await staffApi.logout();
    } catch {
      // Ignore backend logout error, clear local state regardless
    } finally {
      setToken(null);
      setUser(null);
      staffApi.setAuthToken(null);
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
  }, []);

  const role = user?.roles?.includes('ROLE_ADMIN')
    ? 'ROLE_ADMIN'
    : user?.roles?.includes('ROLE_TELLER')
    ? 'ROLE_TELLER'
    : null;

  const isTeller = user?.roles?.includes('ROLE_TELLER') ?? false;
  const isAdmin = user?.roles?.includes('ROLE_ADMIN') ?? false;
  const isAuthenticated = !!token && !!user;

  const hasRole = useCallback((requiredRole: StaffRole) => {
    return user?.roles?.includes(requiredRole) ?? false;
  }, [user]);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        role,
        isTeller,
        isAdmin,
        isAuthenticated,
        isLoading,
        login,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
