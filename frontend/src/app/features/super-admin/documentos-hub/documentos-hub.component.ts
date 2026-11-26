import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { EmpresaService } from '../../../core/services/empresa.service';
import { AuthService } from '../../../core/services/auth.service';
import { Empresa } from '../../../core/models/empresa.model';
import { environment } from '../../../../environments/environment';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';

const EMPRESA_FISCALIZACION_KEY = 'hse.empresaFiscalizacionId';

@Component({
  selector: 'app-documentos-hub',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, SuperAdminSidebarComponent],
  templateUrl: './documentos-hub.component.html',
  styleUrls: ['./documentos-hub.component.scss'],
})
export class DocumentosHubComponent implements OnInit, OnDestroy {
  private empresaService = inject(EmpresaService);
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  empresa: Empresa | null = null;
  documentos: any[] = [];
  loading = true;
  loadingDocs = false;
  errorCarga: string | null = null;
  filtro = '';

  docEditando: any = null;
  tituloEditado = '';
  descripcionEditada = '';
  guardandoEdicion = false;
  errorEdicion: string | null = null;

  docActualizando: any = null;
  archivoVersion: File | null = null;
  guardandoVersion = false;
  errorVersion: string | null = null;

  docHistorial: any = null;
  versiones: any[] = [];
  cargandoHistorial = false;

  private pollTimer?: ReturnType<typeof setInterval>;

  get nombreUsuario(): string {
    return this.authService.getUserData()?.nombre || 'Super Admin';
  }

  get documentosFiltrados(): any[] {
    const q = this.filtro.trim().toLowerCase();
    if (!q) return this.documentos;
    return this.documentos.filter((d) =>
      `${d.titulo || ''} ${d.descripcion || ''}`.toLowerCase().includes(q)
    );
  }

  get totalPdfs(): number {
    return this.documentos.length;
  }

  get conIa(): number {
    return this.documentos.filter((d) => d.puntosGeneradosIa).length;
  }

  get procesando(): number {
    return this.documentos.filter((d) => this.estaExtrayendo(d)).length;
  }

  get rutaSubir(): (string | number)[] {
    return this.empresa ? ['/super-admin', 'empresas', this.empresa.id, 'documentos', 'subir'] : ['/super-admin', 'documentos'];
  }

  get rutaIa(): (string | number)[] {
    return this.empresa ? ['/super-admin', 'empresas', this.empresa.id, 'ia'] : ['/super-admin', 'ia'];
  }

  get rutaAnalizar(): (string | number)[] {
    return ['/super-admin', 'analizar-reporte'];
  }

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.detenerPoll();
  }

  cargar(): void {
    this.loading = true;
    this.errorCarga = null;
    this.empresaService.listar(0, 50).subscribe({
      next: (response) => {
        const empresas: Empresa[] = response.content || [];
        this.empresa = this.elegirEmpresaUnica(empresas);
        this.loading = false;
        if (this.empresa) {
          this.cargarDocumentos(false);
        }
      },
      error: () => {
        this.loading = false;
        this.errorCarga = 'No se pudo cargar la empresa de fiscalización.';
      },
    });
  }

  rutaDetalle(docId: number): (string | number)[] {
    if (!this.empresa) return ['/super-admin', 'documentos'];
    return ['/super-admin', 'empresas', this.empresa.id, 'documentos', docId];
  }

  cargarDocumentos(silencioso = false): void {
    if (!this.empresa) return;
    if (!silencioso) this.loadingDocs = true;
    this.http
      .get(`${environment.apiUrl}/documentos/empresa/${this.empresa.id}?page=0&size=200`)
      .subscribe({
        next: (response: any) => {
          this.documentos = response.content || response || [];
          this.loadingDocs = false;
          this.actualizarPoll();
        },
        error: () => {
          this.loadingDocs = false;
          if (!silencioso) this.errorCarga = 'No se pudieron cargar los documentos.';
        },
      });
  }

  eliminar(doc: any): void {
    if (!confirm(`¿Eliminar «${doc.titulo}» de la biblioteca?`)) return;
    this.http.delete(`${environment.apiUrl}/documentos/${doc.id}`).subscribe({
      next: () => {
        this.documentos = this.documentos.filter((d) => d.id !== doc.id);
        this.actualizarPoll();
      },
    });
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

  abrirActualizar(doc: any): void {
    this.docActualizando = doc;
    this.archivoVersion = null;
    this.errorVersion = null;
    this.guardandoVersion = false;
  }

  cerrarActualizar(): void {
    if (this.guardandoVersion) return;
    this.docActualizando = null;
    this.archivoVersion = null;
    this.errorVersion = null;
  }

  elegirPdfVersion(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.archivoVersion = file && file.name.toLowerCase().endsWith('.pdf') ? file : null;
    this.errorVersion = file && !this.archivoVersion ? 'Solo se acepta un archivo PDF.' : null;
  }

  publicarVersion(): void {
    if (!this.docActualizando || !this.archivoVersion) {
      this.errorVersion = 'Elija el PDF de la nueva versión.';
      return;
    }
    const doc = this.docActualizando;
    const datos = new FormData();
    datos.append('archivo', this.archivoVersion);
    this.guardandoVersion = true;
    this.errorVersion = null;
    this.http.post(`${environment.apiUrl}/documentos/${doc.id}/versiones`, datos).subscribe({
      next: (actualizado: any) => {
        const idx = this.documentos.findIndex((d) => d.id === doc.id);
        if (idx !== -1) this.documentos[idx] = { ...this.documentos[idx], ...actualizado };
        this.guardandoVersion = false;
        this.cerrarActualizar();
        this.actualizarPoll();
      },
      error: (err) => {
        this.guardandoVersion = false;
        this.errorVersion = err?.error?.mensaje || err?.error?.message || 'No se pudo publicar la versión.';
      },
    });
  }

  abrirHistorial(doc: any): void {
    this.docHistorial = doc;
    this.versiones = [];
    this.cargandoHistorial = true;
    this.http.get<any[]>(`${environment.apiUrl}/documentos/${doc.id}/versiones`).subscribe({
      next: (lista) => {
        this.versiones = lista || [];
        this.cargandoHistorial = false;
      },
      error: () => {
        this.cargandoHistorial = false;
      },
    });
  }

  cerrarHistorial(): void {
    this.docHistorial = null;
    this.versiones = [];
  }

  verVersion(version: any): void {
    if (!this.docHistorial) return;
    this.http
      .get(`${environment.apiUrl}/documentos/${this.docHistorial.id}/versiones/${version.id}/archivo`, { responseType: 'blob' })
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank', 'noopener');
          setTimeout(() => URL.revokeObjectURL(url), 60000);
        },
      });
  }

  guardarEdicion(): void {
    const titulo = this.tituloEditado.trim();
    if (!titulo || !this.docEditando || !this.empresa) {
      this.errorEdicion = 'El título es obligatorio.';
      return;
    }
    this.guardandoEdicion = true;
    this.errorEdicion = null;
    this.http
      .put(`${environment.apiUrl}/documentos/${this.docEditando.id}`, {
        titulo,
        descripcion: this.descripcionEditada.trim() || null,
        empresaId: this.empresa.id,
      })
      .subscribe({
        next: (actualizado: any) => {
          const idx = this.documentos.findIndex((d) => d.id === this.docEditando.id);
          if (idx !== -1) {
            this.documentos[idx] = { ...this.documentos[idx], ...actualizado };
          }
          this.cerrarEdicion();
        },
        error: (err) => {
          this.guardandoEdicion = false;
          this.errorEdicion =
            err?.error?.mensaje || 'No se pudo guardar. Inténtalo de nuevo.';
        },
      });
  }

  formatearTamano(bytes: number): string {
    if (!bytes) return '—';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  estado(doc: any): string {
    return doc?.estadoProcesamiento || 'COMPLETADO';
  }

  estaExtrayendo(doc: any): boolean {
    return this.estado(doc) === 'PROCESANDO' && !doc?.textoListo;
  }

  idiomaCorto(idioma: string): string {
    const map: Record<string, string> = { es: 'ES', en: 'EN', pt: 'PT' };
    const code = (idioma || 'es').slice(0, 2).toLowerCase();
    return map[code] || code.toUpperCase();
  }

  private elegirEmpresaUnica(empresas: Empresa[]): Empresa | null {
    if (!empresas.length) return null;
    const activas = empresas.filter((e) => e.activa);
    const pool = activas.length ? activas : empresas;
    const saved = Number(localStorage.getItem(EMPRESA_FISCALIZACION_KEY));
    const encontrada = pool.find((e) => e.id === saved);
    const elegida = encontrada ?? pool[0];
    localStorage.setItem(EMPRESA_FISCALIZACION_KEY, String(elegida.id));
    return elegida;
  }

  private actualizarPoll(): void {
    const hayProcesando = this.documentos.some((d) => this.estaExtrayendo(d));
    if (hayProcesando && !this.pollTimer) {
      this.pollTimer = setInterval(() => this.cargarDocumentos(true), 5000);
    } else if (!hayProcesando) {
      this.detenerPoll();
    }
  }

  private detenerPoll(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = undefined;
    }
  }
}
