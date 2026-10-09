// ============================================================================
// Auth Service — Customer and Staff login, logout
// ============================================================================
import type { AuthSession, ProblemDetails } from '../../types';
import { coreClient } from '../client';

function isGatewayOffline(err: any): boolean {
  return (
    !err.message ||
    err.message.includes('GATEWAY_OFFLINE') ||
    err.message.includes('Failed to fetch') ||
    err.message.includes('NetworkError') ||
    err.message.includes('502') ||
    err.message.includes('503') ||
    err.message.includes('504') ||
    err.message.includes('Bad Gateway')
  );
}

export async function loginCustomer(username: string, password = 'Password123!'): Promise<AuthSession> {
  try {
    const res = await fetch('/api/v1/auth/customers/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    if (res.ok) {
      const data = await res.json();
      const authData = data.authData;
      const session: AuthSession = {
        accessToken: authData.accessToken,
        userId: authData.userId || 1,
        username: authData.username || username,
        roles: authData.roles || ['ROLE_CUSTOMER'],
        userType: 'CUSTOMER'
      };
      coreClient.setSession(session);
      return session;
    } else if (res.status >= 500) {
      throw new Error('GATEWAY_OFFLINE');
    } else {
      const err: ProblemDetails = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Customer login failed: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (!isGatewayOffline(err)) throw err;

    // Demo standalone customer authentication fallback
    const session: AuthSession = {
      accessToken: `demo-customer-jwt-${Date.now()}`,
      userId: 1,
      username: username || 'john_doe',
      roles: ['ROLE_CUSTOMER'],
      userType: 'CUSTOMER'
    };
    coreClient.setSession(session);
    return session;
  }
}

export async function loginStaff(username: string, password = 'Password123!'): Promise<AuthSession> {
  try {
    const res = await fetch('/api/v1/auth/staff/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    if (res.ok) {
      const data = await res.json();
      const authData = data.authData;
      const roles: string[] = authData.roles || [];
      const isAdm = roles.some(r => r.toUpperCase().includes('ADMIN'));
      const session: AuthSession = {
        accessToken: authData.accessToken,
        userId: authData.userId || 10,
        username: authData.username || username,
        roles,
        userType: isAdm ? 'ADMIN' : 'TELLER'
      };
      coreClient.setSession(session);
      return session;
    } else if (res.status >= 500) {
      throw new Error('GATEWAY_OFFLINE');
    } else {
      const err: ProblemDetails = await res.json().catch(() => ({}));
      throw new Error(err.detail || `Staff authentication failed: HTTP ${res.status}`);
    }
  } catch (err: any) {
    if (!isGatewayOffline(err)) throw err;

    // Demo standalone staff authentication fallback
    const isAdm = username.toLowerCase().includes('admin');
    const session: AuthSession = {
      accessToken: `demo-staff-jwt-${Date.now()}`,
      userId: isAdm ? 99 : 20,
      username: username || (isAdm ? 'admin' : 'teller_alice'),
      roles: isAdm ? ['ROLE_ADMIN', 'ROLE_STAFF'] : ['ROLE_TELLER', 'ROLE_STAFF'],
      userType: isAdm ? 'ADMIN' : 'TELLER'
    };
    coreClient.setSession(session);
    return session;
  }
}

export async function logout(): Promise<void> {
  return coreClient.logout();
}
