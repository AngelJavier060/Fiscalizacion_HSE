import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription, interval } from 'rxjs';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { PermisosTrabajoService, PermisoTrabajoResponse } from '../../../core/services/permisos-trabajo.service';
import { EmpresaService } from '../../../core/services/empresa.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-permisos-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, SuperAdminSidebarComponent, DatePipe],
  templateUrl: './permisos-list.component.html',
  styleUrls: ['./permisos-list.component.scss'],
})
export class PermisosListComponent implements OnInit, OnDestroy {
  private permisosService = inject(PermisosTrabajoService);
  private empresaService = inject(EmpresaService);
  private authService = inject(AuthService);
  private router = inject(Router);

  permisos: PermisoTrabajoResponse[] = [];
  permisosFiltrados: PermisoTrabajoResponse[] = [];
  empresas: any[] = [];
  cargando = true;
  error = '';

  // Filtros
  filtroTexto = '';
  filtroEmpresa = '';
  filtroStatus = '';

  // Paginacion local
  pagina = 1;
  porPagina = 20;

  // Polling auto cada 30 s
  private polling$?: Subscription;

  get nombreUsuario(): string {
    return this.authService.getUserData()?.nombre || 'Super Administrador';
  }

  get totalVigentes(): number {
    return this.permisos.filter((p) => p.status === 'active').length;
  }

  get totalPorVencer(): number {
    return this.permisos.filter((p) => p.status === 'warning').length;
  }

  get totalExpirados(): number {
    return this.permisos.filter((p) => p.status === 'expired').length;
  }

  get contarPdfs(): number {
    return this.permisos.filter((p) => !!p.imagePath && p.imagePath.length > 0).length;
  }

  ngOnDestroy(): void {
    this.polling$?.unsubscribe();
  }

  ngOnInit(): void {
    this.cargarEmpresas();
    this.polling$ = interval(30000).subscribe(() => {
      if (this.empresas.length > 0) this.recargarPermisos();
    });
  }

  /** Recarga solo permisos */
  recargarPermisos(): void {
    const solicitudes = this.empresas.map((emp) =>
      this.permisosService.listarTodos(emp.id).toPromise()
    );
    Promise.allSettled(solicitudes).then((resultados) => {
      const nuevos: PermisoTrabajoResponse[] = [];
      for (let i = 0; i < resultados.length; i++) {
        const r = resultados[i];
        if (r.status === 'fulfilled' && r.value) {
          const empresa = this.empresas[i];
          const items = (r.value as PermisoTrabajoResponse[]).map((p) => ({
            ...p,
            empresaNombre: p.empresaNombre || empresa.nombre || `Empresa #${empresa.id}`,
          }));
          nuevos.push(...items);
        }
      }
      nuevos.sort((a, b) => {
        const da = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const db = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return db - da;
      });
      this.permisos = nuevos;
      this.aplicarFiltros();
    });
  }

  cargarEmpresas(): void {
    this.empresaService.listar(0, 200).subscribe({
      next: (res) => {
        this.empresas = res?.content ?? [];
        this.cargarTodosLosPermisos();
      },
      error: () => {
        this.error = 'Error al cargar empresas';
        this.cargando = false;
      },
    });
  }

  cargarTodosLosPermisos(): void {
    this.cargando = true;
    this.permisos = [];

    const solicitudes = this.empresas.map((emp) =>
      this.permisosService.listarTodos(emp.id).toPromise()
    );

    Promise.allSettled(solicitudes).then((resultados) => {
      for (let i = 0; i < resultados.length; i++) {
        const r = resultados[i];
        if (r.status === 'fulfilled' && r.value) {
          const empresa = this.empresas[i];
          const items = (r.value as PermisoTrabajoResponse[]).map((p) => ({
            ...p,
            empresaNombre: p.empresaNombre || empresa.nombre || `Empresa #${empresa.id}`,
          }));
          this.permisos.push(...items);
        }
      }

      this.permisos.sort((a, b) => {
        const da = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const db = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return db - da;
      });

      this.aplicarFiltros();
      this.cargando = false;
    });
  }

  aplicarFiltros(): void {
    let lista = [...this.permisos];

    if (this.filtroTexto.trim()) {
      const q = this.filtroTexto.toLowerCase().trim();
      lista = lista.filter(
        (p) =>
          p.id.toLowerCase().includes(q) ||
          p.title.toLowerCase().includes(q) ||
          (p.area && p.area.toLowerCase().includes(q)) ||
          (p.responsible && p.responsible.toLowerCase().includes(q))
      );
    }

    if (this.filtroEmpresa) {
      lista = lista.filter((p) => p.empresaId === Number(this.filtroEmpresa));
    }

    if (this.filtroStatus) {
      lista = lista.filter((p) => p.status === this.filtroStatus);
    }

    this.permisosFiltrados = lista;
    this.pagina = 1;
  }

  get totalPaginas(): number[] {
    const total = Math.ceil(this.permisosFiltrados.length / this.porPagina);
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  get permisosPagina(): PermisoTrabajoResponse[] {
    const inicio = (this.pagina - 1) * this.porPagina;
    return this.permisosFiltrados.slice(inicio, inicio + this.porPagina);
  }

  cambiarPagina(p: number): void {
    if (p >= 1 && p <= this.totalPaginas.length) this.pagina = p;
  }

  statusClass(status: string): string {
    switch (status) {
      case 'active': return 'status-active';
      case 'warning': return 'status-warning';
      case 'expired': return 'status-expired';
      default: return '';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'active': return 'Vigente';
      case 'warning': return 'Por vencer';
      case 'expired': return 'Expirado';
      default: return status;
    }
  }

  statusColor(status: string): string {
    switch (status) {
      case 'active': return '#3B6D11';
      case 'warning': return '#A67C00';
      case 'expired': return '#BA1A1A';
      default: return '#747686';
    }
  }

  verDetalle(id: string): void {
    this.router.navigate(['/super-admin/permisos', id]);
  }

  editar(id: string): void {
    this.router.navigate(['/super-admin/permisos', id, 'editar']);
  }

  async eliminar(permit: PermisoTrabajoResponse): Promise<void> {
    if (!confirm(`Eliminar permiso ${permit.id}?`)) return;
    try {
      await this.permisosService.eliminar(permit.id).toPromise();
      this.permisos = this.permisos.filter((p) => p.id !== permit.id);
      this.aplicarFiltros();
    } catch {
      alert('Error al eliminar');
    }
  }

  tienePdf(p: PermisoTrabajoResponse): boolean {
    return !!p.imagePath && p.imagePath.length > 0;
  }

  paginationArray(): number[] {
    return this.totalPaginas;
  }

  trackById(_: number, item: PermisoTrabajoResponse): string {
    return item.id;
  }
}
