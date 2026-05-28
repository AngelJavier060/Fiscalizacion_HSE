import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/public/landing/landing-page.component').then(
        (m) => m.LandingPageComponent
      ),
  },
  {
    path: 'pdf-reader',
    loadComponent: () =>
      import('./features/pdf-reader/pdf-reader.component').then(
        (m) => m.PdfReaderComponent
      ),
  },
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then(m => m.authRoutes),
  },
  {
    path: 'super-admin',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPER_ADMIN'] },
    loadChildren: () => import('./features/super-admin/super-admin.routes').then(m => m.superAdminRoutes),
  },
  {
    path: 'admin-empresa',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN_EMPRESA'] },
    loadChildren: () => import('./features/admin-empresa/admin-empresa.routes').then(m => m.adminEmpresaRoutes),
  },
  {
    path: 'usuario',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['USUARIO'] },
    loadChildren: () => import('./features/usuario/usuario.routes').then(m => m.usuarioRoutes),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
