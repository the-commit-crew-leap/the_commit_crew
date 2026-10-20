import { Injectable, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import { AuthUser, LoginRequest, RegisterRequest, RegisterResponse } from './auth.models';
import { TokenStorageService } from './token-storage.service';

@Injectable({
  providedIn: 'root'
})
export class AuthSessionService {
  readonly accountId = signal<string | null>(null);
  readonly username = signal<string | null>(null);

  constructor(
    private readonly authApi: AuthApiService,
    private readonly tokenStorage: TokenStorageService
  ) {
    this.accountId.set(this.tokenStorage.getAccountId());
  }

  login(payload: LoginRequest, rememberMe = false): Observable<void> {
    return this.authApi.login(payload).pipe(
      tap((response) => {
        localStorage.setItem('auth.accountId', response.accountId);
        this.accountId.set(response.accountId);
        this.username.set(response.username || payload.username);

        const expiresAt = Date.now() + response.expiresIn * 1000;

        this.tokenStorage.saveSession({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          expiresAt,
          accountId: response.accountId,
          role: response.role
        }, rememberMe);
      }),
      map(() => void 0)
    );
  }

  completeGoogleLogin(fragment: string, rememberMe = false): boolean {
    const params = new URLSearchParams(fragment.replace(/^#/, ''));
    const accessToken = params.get('accessToken');
    const refreshToken = params.get('refreshToken');
    const expiresIn = Number(params.get('expiresIn'));
    const accountId = params.get('accountId');
    const role = params.get('role');
    const username = params.get('username');

    if (!accessToken || !refreshToken || !Number.isFinite(expiresIn) || !accountId || !role) {
      return false;
    }

    localStorage.setItem('auth.accountId', accountId);
    this.accountId.set(accountId);
    this.username.set(username);
    this.tokenStorage.saveSession({
      accessToken,
      refreshToken,
      expiresAt: Date.now() + expiresIn * 1000,
      accountId,
      role
    }, rememberMe);
    return true;
  }

  validateSession(): Observable<void> {
    const token = this.tokenStorage.getAccessToken();

    if (!token || this.isSessionExpired()) {
      this.logout();
      return of(void 0);
    }

    return this.authApi.validate(token).pipe(
      tap((response) => {
        if (!response.valid) {
          this.logout();
        } else {
          this.username.set(response.username);
        }
      }),
      map(() => void 0),
      catchError(() => {
        this.logout();
        return of(void 0);
      })
    );
  }

  register(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.authApi.register(payload);
  }

  logout(): void {
    this.tokenStorage.clearSession();
    localStorage.removeItem('auth.accountId');
    this.accountId.set(null);
    this.username.set(null);
  }

  getAccessToken(): string | null {
    if (this.isSessionExpired()) {
      this.logout();
      return null;
    }
    return this.tokenStorage.getAccessToken();
  }

  getAccountId(): string | null {
    if (this.isSessionExpired()) {
      this.logout();
      return null;
    }

    const accountId = this.tokenStorage.getAccountId();
    this.accountId.set(accountId);
    return accountId;
  }

  getRole(): string | null {
    if (this.isSessionExpired()) {
      this.logout();
      return null;
    }

    return this.tokenStorage.getRole();
  }

  isAuthenticated(): boolean {
    const token = this.tokenStorage.getAccessToken();
    return !!token && !this.isSessionExpired();
  }

  isSessionExpired(): boolean {
    return this.tokenStorage.isExpired();
  }
}