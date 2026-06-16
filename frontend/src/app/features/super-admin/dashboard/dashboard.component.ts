import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { EmpresaService } from '../../../core/services/empresa.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthService } from '../../../core/services/auth.service';
import { HttpClient } from '@angular/common/http';
import { PermisosTrabajoService } from '../../../core/services/permisos-trabajo.service';
import { environment } from '../../../../environments/environment';

interface SystemMetric {
  label: string;
  icon: string;
  value: number;
}

interface ActividadItem {
  id: number;
  tipo: 'login' | 'upload' | 'empresa' | 'auditoria' | 'error';
  mensaje: string;
  tiempo: string;
}

@Component({
  selector: 'app-super-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent, DatePipe],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit {
  private empresaService = inject(EmpresaService);
  private usuarioService = inject(UsuarioService);
  private authService   = inject(AuthService);
  private http          = inject(HttpClient);
  private permisosService = inject(PermisosTrabajoService);

  // â”€â”€ Estado â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  cargandoStats    = true;
  totalEmpresas    = 0;
  empresasActivas  = 0;
  empresasSuspendidas = 0;
  totalUsuarios    = 0;
  usuariosActivos  = 0;
  registrosAuditoria = 0;
  totalPermisos = 0;
  permisosVigentes = 0;

  // â”€â”€ MÃ©tricas calculadas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  get pctEmpresasActivas(): number {
    return this.totalEmpresas ? Math.round((this.empresasActivas / this.totalEmpresas) * 100) : 0;
  }
  get pctUsuariosActivos(): number {
    return this.totalUsuarios ? Math.round((this.usuariosActivos / this.totalUsuarios) * 100) : 0;
  }
  get pctSuspendidas(): number {
    return this.totalEmpresas ? Math.round((this.empresasSuspendidas / this.totalEmpresas) * 100) : 0;
  }

  // â”€â”€ Salud del sistema â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  readonly systemMetrics: SystemMetric[] = [
    { label: 'Disponibilidad API',   icon: 'cloud_done',      value: 98 },
    { label: 'Documentos indexados', icon: 'description',     value: 84 },
    { label: 'Sesiones activas',     icon: 'people',          value: 61 },
    { label: 'Almacenamiento',       icon: 'storage',         value: 43 },
    { label: 'Fiscaliza AI',         icon: 'smart_toy',       value: 95 },
  ];

  // â”€â”€ Actividad reciente (se llena dinÃ¡micamente) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  actividadReciente: ActividadItem[] = [];

  // â”€â”€ Fecha â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  readonly fechaHoy: string = new Intl.DateTimeFormat('es-ES', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  }).format(new Date()).replace(/^\w/, c => c.toUpperCase());

  get nombreUsuario(): string {
    return this.authService.getUserData()?.nombre || 'Super Administrador';
  }

  // â”€â”€ Init â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  ngOnInit(): void {
    forkJoin({
      empresas: this.empresaService.listar(0, 500),
      usuarios: this.usuarioService.listar(0, 500),
      auditoria: this.http
        .get<{ total: number }>(`${environment.apiUrl}/auditoria/count`)
        .pipe(catchError(() => of({ total: 0 }))),
    }).subscribe({
      next: ({ empresas, usuarios, auditoria }) => {
        const listaEmpresas = empresas?.content ?? [];
        const listaUsuarios = usuarios?.content ?? [];

        this.totalEmpresas      = listaEmpresas.length;
        this.empresasActivas    = listaEmpresas.filter((e: { activa?: boolean }) => e.activa).length;
        this.empresasSuspendidas = this.totalEmpresas - this.empresasActivas;

        this.totalUsuarios   = listaUsuarios.length;
        this.usuariosActivos = listaUsuarios.filter((u: { activo?: boolean }) => u.activo).length;

        this.registrosAuditoria = auditoria?.total ?? 0;

        // Cargar stats de permisos
        const empresasIds = listaEmpresas.filter((e: any) => e.id).map((e: any) => e.id);
        Promise.allSettled(
          empresasIds.map((id: number) => this.permisosService.contar(id).toPromise())
        ).then((resultados) => {
          for (const r of resultados) {
            if (r.status === 'fulfilled' && r.value) {
              this.totalPermisos += r.value.total;
              this.permisosVigentes += r.value.vigentes;
            }
          }
        }).finally(() => {
          this.cargandoStats = false;
          this._buildActividadReciente(listaEmpresas, listaUsuarios);
        });
      },
      error: () => {
        this.cargandoStats = false;
        this._buildActividadFallback();
      },
    });
  }

  // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  private _buildActividadReciente(
    empresas: Array<{ nombre?: string; activa?: boolean }>,
    usuarios: Array<{ nombre?: string; activo?: boolean }>,
  ): void {
    const items: ActividadItem[] = [];

    // Mostrar Ãºltimas empresas registradas
    empresas.slice(-3).reverse().forEach((e, i) => {
      items.push({
        id: i,
        tipo: e.activa ? 'empresa' : 'error',
        mensaje: e.activa
          ? `Empresa "${e.nombre}" activa en el sistema`
          : `Empresa "${e.nombre}" suspendida`,
        tiempo: 'Hoy',
      });
    });

    // Mostrar Ãºltimos usuarios
    usuarios.slice(-3).reverse().forEach((u, i) => {
      items.push({
        id: 100 + i,
        tipo: 'login',
        mensaje: `Usuario "${u.nombre}" ${u.activo ? 'activo' : 'inactivo'}`,
        tiempo: 'Reciente',
      });
    });

    if (this.registrosAuditoria > 0) {
      items.unshift({
        id: 999,
        tipo: 'auditoria',
        mensaje: `${this.registrosAuditoria} registros de auditorÃ­a este mes`,
        tiempo: 'Este mes',
      });
    }

    this.actividadReciente = items.slice(0, 6);
  }

  private _buildActividadFallback(): void {
    this.actividadReciente = [
      { id: 1, tipo: 'login',    mensaje: 'Inicio de sesiÃ³n â€” Super Admin',   tiempo: 'Ahora' },
      { id: 2, tipo: 'upload',   mensaje: 'Sistema iniciado correctamente',    tiempo: 'Hoy' },
      { id: 3, tipo: 'auditoria',mensaje: 'Backend pendiente de conexiÃ³n',     tiempo: 'â€”' },
    ];
  }
}


