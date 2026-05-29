import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';
import {
  buildDocumentoContext,
  documentNavigationLinks,
  type DocumentoLinks,
} from '../../../../core/helpers/documento-routing.helper';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';

export interface BusquedaAsistidaUi {
  consulta: string;
  analisis: string;
  advertencia?: string | null;
  resultados?: unknown[];
}

@Component({
  selector: 'app-documento-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MarkdownPipe],
  templateUrl: './documento-list.component.html',
  styleUrls: ['./documento-list.component.scss'],
})
export class DocumentoListComponent implements OnInit, OnDestroy {
  documentos: any[] = [];
  loading = true;
  page = 0;
  pageSize = 50;
  totalElements = 0;
  totalPages = 0;
  user: any;
  empresaId: number | null = null;
  private pollListaTimer?: ReturnType<typeof setInterval>;
  /** Navegación coherente (admin empresa vs super admin por empresa) */
  nav: DocumentoLinks = documentNavigationLinks({
    empresaId: null,
    esRutaSuperAdmin: false,
  });
  esRutaSuperAdmin = false;

  /** Búsqueda asistida (solo documentos de la empresa; backend usa DeepSeek con contexto cerrado). */
  consultaBusqueda = '';
  buscandoIa = false;
  busquedaAsistida: BusquedaAsistidaUi | null = null;

  /** Edición de metadata (renombrar título / descripción) */
  docEditando: any = null;
  tituloEditado = '';
  descripcionEditada = '';
  guardandoEdicion = false;
  errorEdicion: string | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    const ctx = buildDocumentoContext(this.route, this.authService);
    this.esRutaSuperAdmin = ctx.esRutaSuperAdmin;
    this.nav = documentNavigationLinks(ctx);
    this.empresaId = ctx.empresaId;
    if (this.empresaId) {
      this.cargarDocumentos();
    } else {
      this.loading = false;
    }
  }

  buscarAsistido(): void {
    if (!this.empresaId || !this.consultaBusqueda.trim()) {
      return;
    }
    this.buscandoIa = true;
    this.http
      .post<BusquedaAsistidaUi>(`${environment.apiUrl}/ia/buscar-asistido`, {
        consulta: this.consultaBusqueda.trim(),
        empresaId: this.empresaId,
        limite: 12,
      })
      .subscribe({
        next: (r) => {
          this.busquedaAsistida = r;
          this.buscandoIa = false;
        },
        error: () => {
          this.buscandoIa = false;
        },
      });
  }

  limpiarBusquedaAsistida(): void {
    this.busquedaAsistida = null;
    this.consultaBusqueda = '';
  }

  cargarDocumentos(): void {
    this.loading = true;
    this.http.get(
      `${environment.apiUrl}/documentos/empresa/${this.empresaId}?page=${this.page}&size=${this.pageSize}`
    )
      .subscribe({
        next: (response: any) => {
          this.documentos = response.content || response || [];
          this.totalElements = response.totalElements ?? this.documentos.length;
          this.totalPages = response.totalPages ?? 1;
          this.loading = false;
          this.actualizarPollLista();
        },
        error: () => this.loading = false,
      });
  }

  irPagina(nueva: number): void {
    if (nueva < 0 || nueva >= this.totalPages || nueva === this.page) {
      return;
    }
    this.page = nueva;
    this.cargarDocumentos();
  }

  ngOnDestroy(): void {
    this.detenerPollLista();
  }

  private actualizarPollLista(): void {
    const hayProcesando = this.documentos.some(
      (d) => (d.estadoProcesamiento || 'COMPLETADO') === 'PROCESANDO'
    );
    if (hayProcesando && !this.pollListaTimer) {
      this.pollListaTimer = setInterval(() => this.refrescarSilencioso(), 5000);
    } else if (!hayProcesando) {
      this.detenerPollLista();
    }
  }

  private detenerPollLista(): void {
    if (this.pollListaTimer) {
      clearInterval(this.pollListaTimer);
      this.pollListaTimer = undefined;
    }
  }

  private refrescarSilencioso(): void {
    if (!this.empresaId) return;
    this.http.get(
      `${environment.apiUrl}/documentos/empresa/${this.empresaId}?page=${this.page}&size=${this.pageSize}`
    )
      .subscribe({
        next: (response: any) => {
          this.documentos = response.content || response || [];
          this.totalElements = response.totalElements ?? this.documentos.length;
          this.totalPages = response.totalPages ?? 1;
          this.actualizarPollLista();
        },
      });
  }

  estadoProcesamiento(doc: any): string {
    return doc?.estadoProcesamiento || 'COMPLETADO';
  }

  etiquetaEstado(doc: any): string | null {
    switch (this.estadoProcesamiento(doc)) {
      case 'PROCESANDO': return 'Procesando…';
      case 'ERROR': return 'Error al procesar';
      default: return null;
    }
  }

  eliminar(doc: any): void {
    if (confirm(`¿Eliminar el documento "${doc.titulo}"?`)) {
      this.http.delete(`${environment.apiUrl}/documentos/${doc.id}`)
        .subscribe({
          next: () => {
            this.documentos = this.documentos.filter(d => d.id !== doc.id);
          },
        });
    }
  }

  abrirEdicion(doc: any): void {
    this.docEditando = doc;
    this.tituloEditado = doc.titulo || '';
    this.descripcionEditada = doc.descripcion || '';
    this.errorEdicion = null;
  }

  cerrarEdicion(): void {
    this.docEditando = null;
    this.tituloEditado = '';
    this.descripcionEditada = '';
    this.guardandoEdicion = false;
    this.errorEdicion = null;
  }

  guardarEdicion(): void {
    const titulo = this.tituloEditado.trim();
    if (!titulo || !this.docEditando) {
      this.errorEdicion = 'El título es obligatorio.';
      return;
    }

    this.guardandoEdicion = true;
    this.errorEdicion = null;

    this.http.put(`${environment.apiUrl}/documentos/${this.docEditando.id}`, {
      titulo,
      descripcion: this.descripcionEditada.trim() || null,
      empresaId: this.empresaId,
    }).subscribe({
      next: (actualizado: any) => {
        const idx = this.documentos.findIndex(d => d.id === this.docEditando.id);
        if (idx !== -1) {
          this.documentos[idx] = { ...this.documentos[idx], ...actualizado };
        }
        this.cerrarEdicion();
      },
      error: (err) => {
        this.guardandoEdicion = false;
        this.errorEdicion =
          err?.error?.mensaje || 'No se pudo guardar. Verifica los datos e inténtalo de nuevo.';
      },
    });
  }

  getIconoIdioma(idioma: string): string {
    switch(idioma) {
      case 'es': return '🇪🇸';
      case 'en': return '🇬🇧';
      case 'pt': return '🇵🇹';
      default: return '🌐';
    }
  }

  getNombreIdioma(idioma: string): string {
    switch(idioma) {
      case 'es': return 'Español';
      case 'en': return 'Inglés';
      case 'pt': return 'Portugués';
      default: return idioma || 'Desconocido';
    }
  }

  getCodigoIdioma(idioma: string): string {
    return (idioma || 'es').slice(0, 2).toLowerCase();
  }

  get inicialesUsuario(): string {
    const nombre = (this.user?.nombre || 'A').trim();
    const partes = nombre.split(/\s+/).filter(Boolean);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return nombre.slice(0, 2).toUpperCase();
  }

  formatearTamano(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  logout(): void {
    this.authService.logout();
  }
}
