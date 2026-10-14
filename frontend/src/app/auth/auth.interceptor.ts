import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn
} from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthSessionService } from './auth-session.service';
import { environment } from '../../environments/environment';

function isPublicAuthEndpoint(url: string): boolean {
  const normalized = url.toLowerCase();
  const authBase = environment.authApiBaseUrl.toLowerCase();

  return (
    normalized.startsWith(`${authBase}/login`) ||
    normalized.startsWith(`${authBase}/register`) ||
    normalized.startsWith(`${authBase}/refresh`) ||
    normalized.startsWith(`${authBase}/health`) ||
    normalized.endsWith('/api/auth/login') ||
    normalized.endsWith('/api/auth/register') ||
    normalized.endsWith('/api/auth/refresh') ||
    normalized.endsWith('/api/auth/health')
  );
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  const token = authSession.getAccessToken();

  // Force reauthentication if session is expired.
  if (authSession.isSessionExpired() && !isPublicAuthEndpoint(req.url)) {
    authSession.logout();
    void router.navigate(['/login'], {
      queryParams: { expired: '1', returnUrl: router.url }
    });
    return throwError(() => new Error('Session expired'));
  }

  const authorizedReq =
    token && !isPublicAuthEndpoint(req.url)
      ? req.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isPublicAuthEndpoint(req.url)) {
        authSession.logout();
        void router.navigate(['/login'], {
          queryParams: { expired: '1', returnUrl: router.url }
        });
      }
      return throwError(() => error);
    })
  );
};