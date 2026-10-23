import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpEvent, HttpEventType, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';

import { AuthSessionService } from './auth-session.service';
import { authInterceptor } from './auth.interceptor';
import { environment } from '../../environments/environment';

describe('authInterceptor', () => {
  let authSession: jest.Mocked<AuthSessionService>;
  let router: jest.Mocked<Router>;

  beforeEach(() => {
    authSession = {
      getAccessToken: jest.fn().mockReturnValue('access-token'),
      isSessionExpired: jest.fn().mockReturnValue(false),
      logout: jest.fn()
    } as unknown as jest.Mocked<AuthSessionService>;
    router = {
      url: '/account?tab=activity',
      navigate: jest.fn().mockResolvedValue(true)
    } as unknown as jest.Mocked<Router>;
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: Router, useValue: router }
      ]
    });
  });

  function run(
    url: string,
    next: (request: HttpRequest<unknown>) => Observable<HttpEvent<unknown>>
  ) {
    const request = new HttpRequest('GET', url);
    return TestBed.runInInjectionContext(() => authInterceptor(request, next));
  }

  it('adds a bearer token to protected requests', async () => {
    let authorizedRequest: HttpRequest<unknown> | undefined;
    await firstValueFrom(run('/api/positions', (request) => {
      authorizedRequest = request;
      return of({ type: HttpEventType.Sent });
    }));

    expect(authorizedRequest?.headers.get('Authorization')).toBe('Bearer access-token');
    expect(authSession.logout).not.toHaveBeenCalled();
  });

  it('does not attach credentials to public auth endpoints', async () => {
    const publicUrls = [
      `${environment.authApiBaseUrl}/login`,
      `${environment.authApiBaseUrl}/register`,
      `${environment.authApiBaseUrl}/refresh`,
      `${environment.authApiBaseUrl}/health`,
      `${environment.authApiBaseUrl}/validate`,
      'http://localhost/api/auth/login',
      'http://localhost/api/auth/register',
      'http://localhost/api/auth/refresh',
      'http://localhost/api/auth/health',
      'http://localhost/api/auth/validate'
    ];

    for (const url of publicUrls) {
      let requestSeen: HttpRequest<unknown> | undefined;
      await firstValueFrom(run(url, (request) => {
        requestSeen = request;
        return of({ type: HttpEventType.Sent });
      }));
      expect(requestSeen?.headers.has('Authorization')).toBe(false);
    }
    expect(authSession.logout).not.toHaveBeenCalled();
  });

  it('redirects and rejects protected requests when the session is expired', async () => {
    authSession.isSessionExpired.mockReturnValue(true);
    const next = jest.fn((request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> =>
      of({ type: HttpEventType.Sent as HttpEventType.Sent })
    );

    await expect(firstValueFrom(run('/api/positions', next))).rejects.toThrow('Session expired');

    expect(next).not.toHaveBeenCalled();
    expect(authSession.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { expired: '1', returnUrl: '/account?tab=activity' }
    });
  });

  it('allows public auth requests even when the session is expired', async () => {
    authSession.isSessionExpired.mockReturnValue(true);
    const url = `${environment.authApiBaseUrl}/login`;
    let requestSeen: HttpRequest<unknown> | undefined;
    await firstValueFrom(run(url, (request) => {
      requestSeen = request;
      return of({ type: HttpEventType.Sent });
    }));

    expect(requestSeen?.url).toBe(url);
    expect(authSession.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('logs out and redirects on 401 from a protected endpoint', async () => {
    const unauthorized = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });

    await expect(firstValueFrom(run('/api/positions', () => throwError(() => unauthorized))))
      .rejects.toBe(unauthorized);

    expect(authSession.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { expired: '1', returnUrl: '/account?tab=activity' }
    });
  });

  it('preserves public-endpoint 401s without logging out', async () => {
    const unauthorized = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });

    await expect(firstValueFrom(run(
      `${environment.authApiBaseUrl}/login`,
      () => throwError(() => unauthorized)
    ))).rejects.toBe(unauthorized);

    expect(authSession.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('passes through non-401 failures without logging out', async () => {
    const unavailable = new HttpErrorResponse({ status: 503, statusText: 'Unavailable' });

    await expect(firstValueFrom(run('/api/positions', () => throwError(() => unavailable))))
      .rejects.toBe(unavailable);

    expect(authSession.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
