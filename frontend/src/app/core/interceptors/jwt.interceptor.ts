import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token = localStorage.getItem('token');
  const esLogin = req.url.includes('/auth/login');

  // El login no debe llevar un token viejo: en producción eso provoca 401.
  if (esLogin) {
    const limpia = req.clone({ headers: req.headers.delete('Authorization') });
    return next(limpia);
  }

  // Agregar token si existe
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(req).pipe(
    catchError((error) => {
      if (
        error.status === 401 &&
        !req.url.includes('/simulador/') &&
        !esLogin
      ) {
        localStorage.removeItem('token');
        localStorage.removeItem('usuario');
        router.navigate(['/auth/login']);
      }
      return throwError(() => error);
    })
  );
};
