import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (authSession.isAuthenticated()) {
    return true;
  }

  if (authSession.isSessionExpired()) {
    authSession.logout();
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url, expired: '1' }
    });
  }

  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};