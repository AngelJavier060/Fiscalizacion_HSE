import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

const AVISO_SUSCRIPCION = 'hse.avisoSuscripcion';

function suscripcionLocalVencida(): string | null {
  const crudo = localStorage.getItem('usuario');
  if (!crudo) return null;
  try {
    const usuario = JSON.parse(crudo);
    if (usuario?.rol === 'SUPER_ADMIN' || !usuario?.accesoHasta) return null;
    const fin = String(usuario.accesoHasta).slice(0, 10);
    const hoy = new Date();
    const hoyIso = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
    if (fin < hoyIso) {
      return 'Su suscripción caducó. Notifique o pague la suscripción al administrador para volver a entrar.';
    }
  } catch {
    return null;
  }
  return null;
}

function cerrarPorSuscripcion(router: Router, mensaje: string): void {
  sessionStorage.setItem(AVISO_SUSCRIPCION, mensaje);
  localStorage.removeItem('token');
  localStorage.removeItem('usuario');
  const path = router.url.split('?')[0];
  if (path !== '/auth/login' && path !== '/') {
    router.navigate(['/auth/login']);
  }
}

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
  const avisoLocal = suscripcionLocalVencida();
  if (avisoLocal && !esLogin) {
    cerrarPorSuscripcion(router, avisoLocal);
    return throwError(() => ({ status: 403, error: { codigo: 'SUSCRIPCION_VENCIDA', mensaje: avisoLocal } }));
  }

  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(req).pipe(
    catchError((error) => {
      if (error?.error?.codigo === 'SUSCRIPCION_VENCIDA') {
        cerrarPorSuscripcion(router, error.error.mensaje || 'Su suscripción caducó. Notifique o pague la suscripción al administrador.');
        return throwError(() => error);
      }
      if (
        error.status === 401 &&
        !req.url.includes('/simulador/') &&
        !esLogin
      ) {
        localStorage.removeItem('token');
        localStorage.removeItem('usuario');
        const path = router.url.split('?')[0];
        if (path !== '/' && path !== '') {
          router.navigateByUrl('/');
        }
      }
      return throwError(() => error);
    })
  );
};
