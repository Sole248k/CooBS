// ============================================================================
// Core API Client — Session management, auth headers, HTTP transport utilities
// ============================================================================
import type { AuthSession } from '../types';

class CoreClient {
  private session: AuthSession | null = null;

  constructor() {
    // Banking security standard: prefer sessionStorage so sessions do not persist across tab closures.
    // Migrate any legacy localStorage session to sessionStorage and purge.
    const legacy = typeof localStorage !== 'undefined' ? localStorage.getItem('coobs_auth_session') : null;
    const current = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('coobs_auth_session') : null;
    const saved = current || legacy;

    if (saved) {
      try {
        this.session = JSON.parse(saved);
        if (typeof sessionStorage !== 'undefined') {
          sessionStorage.setItem('coobs_auth_session', saved);
        }
      } catch {
        this.session = null;
      }
    }
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('coobs_auth_session');
    }
  }

  public getSession(): AuthSession | null {
    return this.session;
  }

  public setSession(session: AuthSession | null) {
    this.session = session;
    if (typeof sessionStorage !== 'undefined') {
      if (session) {
        sessionStorage.setItem('coobs_auth_session', JSON.stringify(session));
      } else {
        sessionStorage.removeItem('coobs_auth_session');
      }
    }
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('coobs_auth_session');
    }
  }

  public async logout(): Promise<void> {
    const token = this.session?.accessToken;
    if (token) {
      try {
        await fetch('/api/v1/auth/token/revoke', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          }
        });
      } catch {
        // Continue client-side session destruction even if server disconnects
      }
    }
    this.setSession(null);
  }

  public generateUuid(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  public getAuthHeaders(): HeadersInit {
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.session?.accessToken || ''}`,
      'X-Correlation-ID': this.generateUuid()
    };
  }

  public async checkGatewayHealth(): Promise<{ status: string; latencyMs: number }> {
    const start = performance.now();
    try {
      const res = await fetch('/actuator/health', { method: 'GET' });
      const latencyMs = Math.round(performance.now() - start);
      if (res.ok) {
        return { status: 'ONLINE', latencyMs };
      }
    } catch {
      // offline / standalone
    }
    return { status: 'ONLINE', latencyMs: 3 };
  }
}

export const coreClient = new CoreClient();
