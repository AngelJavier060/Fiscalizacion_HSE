import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const requiredRoles = route.data?.['roles'] as string[];
  if (!requiredRoles || requiredRoles.length === 0) {
    return true;
  }

  const userRole = authService.getUserRole();
  if (userRole && requiredRoles.includes(userRole)) {
    return true;
  }

  // Redirigir según el rol que tenga
  switch (userRole) {
    case 'SUPER_ADMIN':
      router.navigate(['/super-admin/dashboard']);
      break;
    case 'ADMIN_EMPRESA':
      router.navigate(['/admin-empresa/dashboard']);
      break;
    case 'USUARIO':
      router.navigate(['/usuario/dashboard']);
      break;
    default:
      router.navigate(['/auth/login']);
  }

  return false;
};
