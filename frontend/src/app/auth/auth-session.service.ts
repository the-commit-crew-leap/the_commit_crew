import { Injectable } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
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
        const expiresAt = Date.now() + response.expiresIn * 1000;
        this.tokenStorage.saveSession({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          expiresAt,
          user: response.user
        });
      }),
      map(() => void 0)
    );
  }

  register(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.authApi.register(payload);
  }

  logout(): void {
    this.tokenStorage.clearSession();
  }

  getAccessToken(): string | null {
    if (this.isSessionExpired()) {
      this.logout();
      return null;
    }
    return this.tokenStorage.getAccessToken();
  }

  getCurrentUser(): AuthUser | null {
    if (this.isSessionExpired()) {
      this.logout();
      return null;
    }
    return this.tokenStorage.getUser();
  }

  isAuthenticated(): boolean {
    const token = this.tokenStorage.getAccessToken();
    return !!token && !this.isSessionExpired();
  }

  isSessionExpired(): boolean {
    return this.tokenStorage.isExpired();
  }
}