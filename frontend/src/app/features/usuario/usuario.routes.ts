import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { DocumentoViewComponent } from './documentos/documento-view/documento-view.component';
import { NotificacionesComponent } from './notificaciones/notificaciones.component';
import { IaUsuarioComponent } from './ia/ia-usuario.component';

export const usuarioRoutes: Routes = [
  { path: 'dashboard', component: DashboardComponent },
  { path: 'documentos', component: DocumentoViewComponent },
  { path: 'notificaciones', component: NotificacionesComponent },
  { path: 'ia', component: IaUsuarioComponent },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
