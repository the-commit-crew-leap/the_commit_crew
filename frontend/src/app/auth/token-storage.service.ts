import { Injectable } from '@angular/core';

interface StoredSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  accountId: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class TokenStorageService {
  private readonly sessionKey = 'auth_session';

  saveSession(session: StoredSession, rememberMe: boolean): void {
    this.clearSession();

    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem(this.sessionKey, JSON.stringify(session));
  }

  getSession(): StoredSession | null {
    const raw =
      sessionStorage.getItem(this.sessionKey) ??
      localStorage.getItem(this.sessionKey);

    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as StoredSession;
    } catch {
      this.clearSession();
      return null;
    }
  }

  clearSession(): void {
    sessionStorage.removeItem(this.sessionKey);
    localStorage.removeItem(this.sessionKey);
  }

  getAccessToken(): string | null {
    return this.getSession()?.accessToken ?? null;
  }

  getExpiresAt(): number | null {
    return this.getSession()?.expiresAt ?? null;
  }

  getAccountId(): string | null {
    return this.getSession()?.accountId ?? null;
  }

  getRole(): string | null {
    return this.getSession()?.role ?? null;
  }

  isExpired(nowMs = Date.now()): boolean {
    const expiresAt = this.getExpiresAt();

    if (!expiresAt) {
      return true;
    }

    return nowMs >= expiresAt;
  }
}