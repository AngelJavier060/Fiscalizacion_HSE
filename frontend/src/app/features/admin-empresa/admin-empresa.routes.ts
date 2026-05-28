import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { DocumentoListComponent } from './documentos/documento-list/documento-list.component';
import { DocumentoSubirComponent } from './documentos/documento-subir/documento-subir.component';
import { DocumentoDetalleComponent } from './documentos/documento-detalle/documento-detalle.component';
import { RecordatorioListComponent } from './recordatorios/recordatorio-list/recordatorio-list.component';
import { RecordatorioFormComponent } from './recordatorios/recordatorio-form/recordatorio-form.component';
import { IaPageComponent } from './ia/ia-page.component';

import { AdminUsuariosListComponent } from './usuarios/admin-usuarios-list.component';
import { AdminRolesPanelComponent } from './roles/admin-roles-panel.component';

export const adminEmpresaRoutes: Routes = [
  { path: 'dashboard', component: DashboardComponent },
  { path: 'usuarios', component: AdminUsuariosListComponent },
  { path: 'roles', component: AdminRolesPanelComponent },
  { path: 'documentos', component: DocumentoListComponent },
  { path: 'documentos/subir', component: DocumentoSubirComponent },
  { path: 'documentos/:id', component: DocumentoDetalleComponent },
  { path: 'recordatorios', component: RecordatorioListComponent },
  { path: 'recordatorios/nuevo', component: RecordatorioFormComponent },
  { path: 'ia', component: IaPageComponent },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
