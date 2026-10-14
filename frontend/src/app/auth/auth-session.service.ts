import { Injectable } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import { AuthUser, LoginRequest, RegisterRequest, RegisterResponse } from './auth.models';
import { TokenStorageService } from './token-storage.service';

@Injectable({
  providedIn: 'root'
})
export class AuthSessionService {
  constructor(
    private readonly authApi: AuthApiService,
    private readonly tokenStorage: TokenStorageService
  ) {}

  login(payload: LoginRequest): Observable<void> {
    return this.authApi.login(payload).pipe(
      tap((response) => {
        localStorage.setItem('auth.accountId', response.accountId);
        const expiresAt = Date.now() + response.expiresIn * 1000;
        this.tokenStorage.saveSession({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          expiresAt,
          accountId: response.accountId,
          role: response.role
        });
      }),
      map(() => void 0)
    );
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

    return this.tokenStorage.getAccountId();
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