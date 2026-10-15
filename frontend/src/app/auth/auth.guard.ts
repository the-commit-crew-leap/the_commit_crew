import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';
import { environment } from '../../environments/environment';

export const authGuard: CanActivateFn = (_route, state) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (!environment.production && environment.bypassAuth) {
    return true;
  }
  
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