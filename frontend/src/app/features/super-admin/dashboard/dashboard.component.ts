import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  faIcon: string;
  value: number;
  bar: 'teal' | 'teal-dark' | 'blue' | 'rose' | 'teal-light';
}

interface ActividadItem {
  id: number;
  tipo: 'login' | 'upload' | 'empresa' | 'auditoria' | 'error';
  mensaje: string;
  tiempo: string;
  detalle?: string;
}

@Component({
  selector: 'app-super-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss', './sa-dashboard.scss'],
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

  get pctAuditoriaMeta(): number {
    return Math.min(100, Math.round((this.registrosAuditoria / 120) * 100));
  }

  readonly systemMetrics: SystemMetric[] = [
    { label: 'Disponibilidad API', icon: 'cloud_done', faIcon: 'fa-solid fa-cloud', value: 98, bar: 'teal' },
    { label: 'Documentos indexados', icon: 'description', faIcon: 'fa-solid fa-file-lines', value: 84, bar: 'teal-dark' },
    { label: 'Sesiones activas', icon: 'people', faIcon: 'fa-solid fa-users-rectangle', value: 61, bar: 'blue' },
    { label: 'Almacenamiento', icon: 'storage', faIcon: 'fa-solid fa-server', value: 43, bar: 'rose' },
    { label: 'Fiscaliza AI', icon: 'smart_toy', faIcon: 'fa-solid fa-microchip', value: 95, bar: 'teal-light' },
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

  get emailUsuario(): string {
    return this.authService.getUserData()?.email || 'admin@fiscalizacionhse.com';
  }

  recargar(): void {
    this.cargarStats();
  }

  ngOnInit(): void {
    this.cargarStats();
  }

  private cargarStats(): void {
    this.cargandoStats = true;
    this.totalPermisos = 0;
    this.permisosVigentes = 0;
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
        detalle: e.activa
          ? 'Certificado de prevención ambiental y seguridad verificado.'
          : 'Revisar vigencia de acceso y licencias.',
      });
    });

    // Mostrar Ãºltimos usuarios
    usuarios.slice(-3).reverse().forEach((u, i) => {
      items.push({
        id: 100 + i,
        tipo: 'login',
        mensaje: `Usuario "${u.nombre}" ${u.activo ? 'activo' : 'inactivo'}`,
        tiempo: 'Reciente',
        detalle: u.activo
          ? 'Inicio de sesión o actividad reciente en el sistema.'
          : 'Cuenta inactiva en el directorio.',
      });
    });

    if (this.registrosAuditoria > 0) {
      items.unshift({
        id: 999,
        tipo: 'auditoria',
        mensaje: `${this.registrosAuditoria} registros de auditoría este mes`,
        tiempo: 'Este mes',
        detalle: 'Consolidación automática ejecutada por el sistema central.',
      });
    }

    this.actividadReciente = items.slice(0, 4);
  }

  private _buildActividadFallback(): void {
    this.actividadReciente = [
      {
        id: 1,
        tipo: 'login',
        mensaje: 'Inicio de sesión — Super Admin',
        tiempo: 'Ahora',
        detalle: 'Inicio de sesión exitoso desde estación de control autorizada.',
      },
      {
        id: 2,
        tipo: 'upload',
        mensaje: 'Sistema iniciado correctamente',
        tiempo: 'Hoy',
        detalle: 'Servicios de fiscalización disponibles para operación.',
      },
      {
        id: 3,
        tipo: 'auditoria',
        mensaje: 'Backend pendiente de conexión',
        tiempo: '—',
        detalle: 'No se pudieron cargar los eventos más recientes.',
      },
    ];
  }
}


