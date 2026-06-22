import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription, interval } from 'rxjs';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { PermisosTrabajoService, PermisoTrabajoResponse } from '../../../core/services/permisos-trabajo.service';
import { EmpresaService } from '../../../core/services/empresa.service';
import { AuthService } from '../../../core/services/auth.service';

export interface ParsedFile {
  nombre: string;
  ruta: string;
  tipo: 'pdf' | 'image' | 'word' | 'excel' | 'other';
  label: string;
  ext: string;
}

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

  // Filtro extra
  filtroTipo = '';

  // Modal: archivos del permiso
  arqModal: {
    visible: boolean;
    permiso: PermisoTrabajoResponse | null;
    archivos: ParsedFile[];
    tipoDoc: string;
    subiendo: boolean;
    error: string;
  } = { visible: false, permiso: null, archivos: [], tipoDoc: 'Permiso_de_trabajo', subiendo: false, error: '' };

  // Modal: nuevo permiso
  nuevoModal: {
    visible: boolean;
    saving: boolean;
    error: string;
    form: {
      title: string; area: string; responsible: string;
      startDate: string; endDate: string; empresaId: number | null;
      criticalTask: string; description: string;
      emisor: string; ejecutante: string; empresaEjecutante: string; nota: string;
    };
  } = {
    visible: false, saving: false, error: '',
    form: { title: '', area: '', responsible: '', startDate: '', endDate: '',
            empresaId: null, criticalTask: '', description: '',
            emisor: '', ejecutante: '', empresaEjecutante: '', nota: '' }
  };

  readonly tiposDocumento = [
    { value: 'Permiso_de_trabajo',    label: 'Permiso de trabajo' },
    { value: 'Verificacion_permiso',  label: 'Verificación de permiso' },
    { value: 'Analisis_riesgo',       label: 'Análisis de riesgo' },
    { value: 'Permiso_altura',        label: 'Permiso de altura' },
    { value: 'Espacio_confinado',     label: 'Espacio confinado' },
    { value: 'Trabajo_caliente',      label: 'Trabajo en caliente' },
    { value: 'Fotografias',           label: 'Fotografías' },
    { value: 'Otro',                  label: 'Otro' },
  ];

  readonly tiposTarea = [
    'Trabajo en altura',
    'Trabajo en caliente',
    'Trabajo eléctrico',
    'Espacio confinado',
    'Excavación',
    'Trabajo con químicos',
    'Izaje de cargas',
    'Demolición',
  ];

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

    if (this.filtroTipo) {
      lista = lista.filter((p) => p.criticalTask === this.filtroTipo);
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

  // ─── Archivos ──────────────────────────────────────────────────────

  contarArchivos(p: PermisoTrabajoResponse): number {
    if (!p.imagePath) return 0;
    return p.imagePath.split('|').filter(r => r.trim()).length;
  }

  abrirArchivos(p: PermisoTrabajoResponse): void {
    this.arqModal.permiso  = p;
    this.arqModal.archivos = this.parsearArchivos(p);
    this.arqModal.error    = '';
    this.arqModal.visible  = true;
  }

  cerrarArchivos(): void {
    this.arqModal.visible = false;
    this.arqModal.permiso = null;
    this.arqModal.archivos = [];
  }

  parsearArchivos(p: PermisoTrabajoResponse): ParsedFile[] {
    if (!p.imagePath) return [];
    return p.imagePath
      .split('|')
      .filter(r => r.trim())
      .map(ruta => {
        const nombre = ruta.replace(/\\/g, '/').split('/').pop() || ruta;
        const ext    = nombre.toLowerCase().split('.').pop() || '';
        const tipo   = this.getTipoArchivo(ext);
        const label  = this.getLabelArchivo(nombre, p.id);
        return { nombre, ruta, tipo, label, ext };
      });
  }

  private getTipoArchivo(ext: string): ParsedFile['tipo'] {
    if (ext === 'pdf') return 'pdf';
    if (['jpg','jpeg','png','webp','gif'].includes(ext)) return 'image';
    if (['doc','docx'].includes(ext)) return 'word';
    if (['xls','xlsx'].includes(ext)) return 'excel';
    return 'other';
  }

  private getLabelArchivo(nombre: string, permisoId: string): string {
    const prefix = permisoId.replace(/[^a-zA-Z0-9_\-]/g, '_') + '_';
    const sinId  = nombre.startsWith(prefix) ? nombre.substring(prefix.length) : nombre;
    for (const tipo of this.tiposDocumento) {
      if (sinId.toLowerCase().startsWith(tipo.value.toLowerCase())) {
        const resto = sinId.substring(tipo.value.length).replace(/^_/, '').replace(/_/g, ' ');
        return `${tipo.label}${resto ? ': ' + resto : ''}`;
      }
    }
    return sinId.replace(/_/g, ' ');
  }

  get archivosPdf():    ParsedFile[] { return this.arqModal.archivos.filter(f => f.tipo === 'pdf'); }
  get archivosImagen(): ParsedFile[] { return this.arqModal.archivos.filter(f => f.tipo === 'image'); }
  get archivosOtros():  ParsedFile[] { return this.arqModal.archivos.filter(f => f.tipo !== 'pdf' && f.tipo !== 'image'); }

  verArchivo(p: PermisoTrabajoResponse, nombre: string): void {
    this.permisosService.descargarArchivo(p.id, nombre).subscribe({
      next: blob => { const u = URL.createObjectURL(blob); window.open(u, '_blank'); },
      error: ()  => alert('No se pudo abrir el archivo')
    });
  }

  descargarArchivoBtn(p: PermisoTrabajoResponse, nombre: string): void {
    this.permisosService.descargarArchivo(p.id, nombre).subscribe({
      next: blob => {
        const url  = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href     = url;
        link.download = nombre;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => alert('No se pudo descargar el archivo')
    });
  }

  subirArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.arqModal.permiso) return;

    const file      = input.files[0];
    const tipoVal   = this.arqModal.tipoDoc;
    const renamed   = new File([file], tipoVal + '_' + file.name, { type: file.type });

    this.arqModal.subiendo = true;
    this.arqModal.error    = '';

    this.permisosService.subirArchivo(this.arqModal.permiso.id, renamed).subscribe({
      next: ruta => {
        const p = this.arqModal.permiso!;
        p.imagePath = p.imagePath ? p.imagePath + '|' + ruta : ruta;
        this.arqModal.archivos = this.parsearArchivos(p);
        const idx = this.permisos.findIndex(x => x.id === p.id);
        if (idx >= 0) this.permisos[idx] = { ...p };
        this.aplicarFiltros();
        this.arqModal.subiendo = false;
        input.value = '';
      },
      error: e => {
        this.arqModal.error    = 'Error al subir: ' + (e.error?.mensaje || e.message || 'Desconocido');
        this.arqModal.subiendo = false;
      }
    });
  }

  // ─── Nuevo Permiso ─────────────────────────────────────────────────

  abrirNuevoPermiso(): void {
    const hoy = new Date().toISOString().substring(0, 10);
    const fin = new Date(Date.now() + 30 * 86_400_000).toISOString().substring(0, 10);
    this.nuevoModal.form = {
      title: '', area: '', responsible: '', startDate: hoy, endDate: fin,
      empresaId: this.empresas.length === 1 ? this.empresas[0].id : null,
      criticalTask: '', description: '', emisor: '', ejecutante: '', empresaEjecutante: '', nota: ''
    };
    this.nuevoModal.error   = '';
    this.nuevoModal.visible = true;
  }

  cerrarNuevoPermiso(): void { this.nuevoModal.visible = false; }

  guardarNuevoPermiso(): void {
    const f = this.nuevoModal.form;
    if (!f.title || !f.startDate || !f.endDate || !f.empresaId) {
      this.nuevoModal.error = 'Complete los campos obligatorios marcados con *';
      return;
    }
    this.nuevoModal.saving = true;
    this.nuevoModal.error  = '';

    const payload: any = {
      id:               'TEMP-' + crypto.randomUUID(),
      title:            f.title,
      area:             f.area             || 'Sin asignar',
      responsible:      f.responsible      || 'Sin asignar',
      startDate:        f.startDate        + 'T08:00:00',
      endDate:          f.endDate          + 'T17:00:00',
      empresaId:        f.empresaId,
      criticalTask:     f.criticalTask     || null,
      description:      f.description      || null,
      emisor:           f.emisor           || null,
      ejecutante:       f.ejecutante       || null,
      empresaEjecutante:f.empresaEjecutante|| null,
      nota:             f.nota             || null,
    };

    this.permisosService.crear(payload).subscribe({
      next: permiso => {
        const conNombre = {
          ...permiso,
          empresaNombre: this.empresas.find(e => e.id === permiso.empresaId)?.nombre || ''
        };
        this.permisos.unshift(conNombre);
        this.aplicarFiltros();
        this.nuevoModal.visible = false;
        this.nuevoModal.saving  = false;
      },
      error: e => {
        this.nuevoModal.error  = 'Error: ' + (e.error?.mensaje || e.error?.message || e.message || 'Desconocido');
        this.nuevoModal.saving = false;
      }
    });
  }
}
