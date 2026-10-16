import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { DocumentoViewComponent } from './documentos/documento-view/documento-view.component';
import { NotificacionesComponent } from './notificaciones/notificaciones.component';
import { IaUsuarioComponent } from './ia/ia-usuario.component';
import { AnalizarReporteComponent } from '../shared/analizar-reporte/analizar-reporte.component';
import { SimuladorInicioComponent } from '../shared/simulador/simulador-inicio.component';
import { SimuladorExamenComponent } from '../shared/simulador/simulador-examen.component';
import { SimuladorResultadoComponent } from '../shared/simulador/simulador-resultado.component';

export const usuarioRoutes: Routes = [
  { path: 'dashboard', component: DashboardComponent },
  { path: 'documentos', component: DocumentoViewComponent },
  { path: 'notificaciones', component: NotificacionesComponent },
  { path: 'ia', component: IaUsuarioComponent },
  { path: 'analizar-reporte', component: AnalizarReporteComponent },
  { path: 'simulador', component: SimuladorInicioComponent },
  { path: 'simulador/examen/:id', component: SimuladorExamenComponent },
  { path: 'simulador/resultado/:id', component: SimuladorResultadoComponent },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
