import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { EmpresaListComponent } from './empresas/empresa-list/empresa-list.component';
import { EmpresaFormComponent } from './empresas/empresa-form/empresa-form.component';
import { AuditoriaComponent } from './auditoria/auditoria.component';
import { DocumentoListComponent } from '../admin-empresa/documentos/documento-list/documento-list.component';
import { DocumentoSubirComponent } from '../admin-empresa/documentos/documento-subir/documento-subir.component';
import { DocumentoDetalleComponent } from '../admin-empresa/documentos/documento-detalle/documento-detalle.component';
import { DocumentosHubComponent } from './documentos-hub/documentos-hub.component';
import { PermisosListComponent } from './permisos/permisos-list.component';

import { UsuariosListComponent } from './usuarios/usuarios-list.component';
import { RolesPanelComponent } from './roles/roles-panel.component';

import { SuperAdminIaEmpresaComponent } from './ia/super-admin-ia-empresa.component';
import { AnalizarReporteComponent } from '../shared/analizar-reporte/analizar-reporte.component';
import { PreguntasSsoComponent } from './preguntas/preguntas-sso.component';
import { SimuladorInicioComponent } from '../shared/simulador/simulador-inicio.component';
import { SimuladorExamenComponent } from '../shared/simulador/simulador-examen.component';
import { SimuladorResultadoComponent } from '../shared/simulador/simulador-resultado.component';

export const superAdminRoutes: Routes = [
  { path: 'dashboard', component: DashboardComponent },
  { path: 'usuarios', component: UsuariosListComponent },
  { path: 'roles', component: RolesPanelComponent },
  { path: 'documentos', component: DocumentosHubComponent },
  { path: 'analizar-reporte', component: AnalizarReporteComponent },
  { path: 'ia', component: SuperAdminIaEmpresaComponent },
  { path: 'empresas/nueva', component: EmpresaFormComponent },
  { path: 'empresas/:empresaId/ia', component: SuperAdminIaEmpresaComponent },
  { path: 'empresas/:empresaId/documentos/subir', component: DocumentoSubirComponent },
  { path: 'empresas/:empresaId/documentos/:id', component: DocumentoDetalleComponent },
  { path: 'empresas/:empresaId/documentos', component: DocumentoListComponent },
  { path: 'empresas/:id/editar', component: EmpresaFormComponent },
  { path: 'empresas', component: EmpresaListComponent },
  { path: 'permisos', component: PermisosListComponent },
  { path: 'preguntas', component: PreguntasSsoComponent },
  { path: 'simulador', component: SimuladorInicioComponent },
  { path: 'simulador/examen/:id', component: SimuladorExamenComponent },
  { path: 'simulador/resultado/:id', component: SimuladorResultadoComponent },
  { path: 'auditoria', component: AuditoriaComponent },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];

