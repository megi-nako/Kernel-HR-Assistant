import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session.service';

// UX guard — blocks routes until logged in (#3, client side).
// The real enforcement is Spring Security on the backend.
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (session.user() !== null) {
    return true;
  }
  return router.createUrlTree(['/login']);
};
