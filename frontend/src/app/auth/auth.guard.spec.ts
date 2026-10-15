import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { authGuard } from './auth.guard';
import { AuthSessionService } from './auth-session.service';
import { environment } from '../../environments/environment';

describe('authGuard', () => {
  let authSession: jest.Mocked<AuthSessionService>;
  let router: jest.Mocked<Router>;

  beforeEach(() => {
    environment.bypassAuth = false;

    authSession = {
      isAuthenticated: jest.fn(),
      isSessionExpired: jest.fn(),
      logout: jest.fn()
    } as unknown as jest.Mocked<AuthSessionService>;

    router = {
      createUrlTree: jest.fn()
    } as unknown as jest.Mocked<Router>;

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: Router, useValue: router }
      ]
    });
  });

  afterEach(() => {
    environment.bypassAuth = true;
  });

  function runGuard(url = '/dashboard') {
    return TestBed.runInInjectionContext(() =>
      authGuard(
        {} as never,
        { url } as never
      )
    );
  }

  it('allows authenticated users through', () => {
    authSession.isAuthenticated.mockReturnValue(true);

    expect(runGuard()).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects unauthenticated users to login', () => {
    const loginTree = {} as UrlTree;

    authSession.isAuthenticated.mockReturnValue(false);
    authSession.isSessionExpired.mockReturnValue(false);
    router.createUrlTree.mockReturnValue(loginTree);

    expect(runGuard('/dashboard')).toBe(loginTree);

    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: {
        returnUrl: '/dashboard'
      }
    });

    expect(authSession.logout).not.toHaveBeenCalled();
  });

  it('protects the orders route for unauthenticated users', () => {
    const loginTree = {} as UrlTree;

    authSession.isAuthenticated.mockReturnValue(false);
    authSession.isSessionExpired.mockReturnValue(false);
    router.createUrlTree.mockReturnValue(loginTree);

    expect(runGuard('/orders')).toBe(loginTree);

    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: {
        returnUrl: '/orders'
      }
    });
  });

  it('clears expired sessions and redirects to login', () => {
    const loginTree = {} as UrlTree;

    authSession.isAuthenticated.mockReturnValue(false);
    authSession.isSessionExpired.mockReturnValue(true);
    router.createUrlTree.mockReturnValue(loginTree);

    expect(runGuard('/orders')).toBe(loginTree);

    expect(authSession.logout).toHaveBeenCalled();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: {
        returnUrl: '/orders',
        expired: '1'
      }
    });
  });
});