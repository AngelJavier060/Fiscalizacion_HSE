import { Component, ElementRef, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { UsuarioSidebarComponent } from '../shared/usuario-sidebar/usuario-sidebar.component';
import { AlertaIncidente, AlertasIncidentesService } from '../../../core/services/alertas-incidentes.service';

@Component({
  selector: 'app-alertas-usuario',
  standalone: true,
  imports: [CommonModule, FormsModule, UsuarioSidebarComponent],
  templateUrl: './alertas-usuario.component.html',
  styleUrls: [
    '../../super-admin/alertas/alertas-incidentes.component.scss',
    './alertas-usuario.component.scss',
  ],
})
/** Consulta de alertas. Solo lectura: indicadores, búsqueda y PDF. */
export class AlertasUsuarioComponent implements OnInit {
  private readonly api = inject(AlertasIncidentesService);
  private readonly sanitizer = inject(DomSanitizer);

  @ViewChild('hoja') hoja?: ElementRef<HTMLElement>;

  pdfAbierto: AlertaIncidente | null = null;
  pdfUrl: SafeResourceUrl | null = null;
  pdfEsImagen = false;
  errorCarga = '';
  busqueda = '';
  filtroAmbito = '';
  filtroArea = '';
  filtroRapido: 'todos' | 'alta' = 'todos';
  alertas: AlertaIncidente[] = [];
  zoom = 1;
  pagina = 1;
  paginas = 1;
  pesoArchivo = '';
  private pdfObjectUrl = '';
  private pdfDatos: ArrayBuffer | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.errorCarga = '';
    this.api.listar().subscribe({
      next: (rows) => { this.alertas = this.ordenar(rows.map((r) => this.fila(r))); },
      error: () => { this.errorCarga = 'No se pudo leer las alertas.'; },
    });
  }

  get ambitos(): string[] {
    return this.opciones('ambito');
  }

  get areas(): string[] {
    return this.opciones('area');
  }

  get total(): number {
    return this.alertas.length;
  }

  get altas(): number {
    return this.alertas.filter((a) => a.prioridad === 'alta').length;
  }

  get visibles(): AlertaIncidente[] {
    const q = this.busqueda.trim().toLowerCase();
    return this.alertas.filter((a) => {
      if (this.filtroAmbito && a.ambito.trim().toLowerCase() !== this.filtroAmbito.trim().toLowerCase()) return false;
      if (this.filtroArea && a.area.trim().toLowerCase() !== this.filtroArea.trim().toLowerCase()) return false;
      if (this.filtroRapido === 'alta' && a.prioridad !== 'alta') return false;
      if (!q) return true;
      return `${a.codigo} ${a.titulo} ${a.detalle} ${a.area} ${a.ambito} ${a.empresa}`.toLowerCase().includes(q);
    });
  }

  get porEmpresa(): { nombre: string; total: number; ancho: number }[] {
    return this.agrupar('empresa');
  }

  get porAmbito(): { nombre: string; total: number; ancho: number }[] {
    return this.agrupar('ambito');
  }

  get puedeArchivo(): boolean {
    return !!this.pdfObjectUrl;
  }

  get zoomTexto(): string {
    return `${Math.round(this.zoom * 100)}%`;
  }

  fechaVisible(iso: string): string {
    const [y, m, d] = (iso || '').split('-');
    return d && m && y ? `${d}/${m}/${y}` : iso;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.pdfAbierto) this.cerrarPdf();
  }

  verPdf(alerta: AlertaIncidente): void {
    this.cerrarPdf();
    this.pdfAbierto = alerta;
    if (!alerta.tieneArchivo) return;
    this.api.archivo(alerta.id).subscribe({
      next: (blob) => {
        this.pdfEsImagen = blob.type.startsWith('image/');
        this.pdfObjectUrl = URL.createObjectURL(blob);
        this.pesoArchivo = this.peso(blob.size);
        this.zoom = 1;
        this.pagina = 1;
        if (this.pdfEsImagen) {
          this.paginas = 1;
          this.pdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfObjectUrl);
          return;
        }
        this.dibujarPdf(blob);
      },
    });
  }

  acercar(): void {
    this.zoom = Math.min(1.6, Math.round((this.zoom + 0.1) * 10) / 10);
  }

  alejar(): void {
    this.zoom = Math.max(0.6, Math.round((this.zoom - 0.1) * 10) / 10);
  }

  ajustar(): void {
    this.zoom = 1;
  }

  irPagina(delta: number): void {
    const siguiente = this.pagina + delta;
    if (siguiente < 1 || siguiente > this.paginas || !this.pdfDatos) return;
    this.pagina = siguiente;
    void this.dibujarPagina(this.pdfDatos, siguiente);
  }

  descargar(): void {
    if (!this.pdfObjectUrl || !this.pdfAbierto) return;
    const enlace = document.createElement('a');
    enlace.href = this.pdfObjectUrl;
    enlace.download = this.pdfAbierto.archivoNombre || `${this.pdfAbierto.codigo}.pdf`;
    enlace.click();
  }

  imprimir(): void {
    if (!this.pdfObjectUrl) return;
    const ventana = window.open(this.pdfObjectUrl, '_blank', 'noopener');
    if (!ventana) return;
    ventana.addEventListener('load', () => ventana.print());
  }

  verPdfCompleto(): void {
    if (!this.pdfObjectUrl) return;
    window.open(this.pdfObjectUrl, '_blank', 'noopener');
  }

  cerrarPdf(): void {
    this.pdfAbierto = null;
    this.pdfUrl = null;
    this.pdfEsImagen = false;
    this.zoom = 1;
    this.pagina = 1;
    this.paginas = 1;
    this.pesoArchivo = '';
    this.pdfDatos = null;
    if (this.pdfObjectUrl) {
      URL.revokeObjectURL(this.pdfObjectUrl);
      this.pdfObjectUrl = '';
    }
  }

  private agrupar(campo: 'empresa' | 'ambito'): { nombre: string; total: number; ancho: number }[] {
    const filas: { nombre: string; total: number }[] = [];
    for (const alerta of this.alertas) {
      const nombre = (alerta[campo] || '').trim();
      if (!nombre) continue;
      const previo = filas.find((fila) => fila.nombre.toLowerCase() === nombre.toLowerCase());
      if (previo) previo.total += 1;
      else filas.push({ nombre, total: 1 });
    }
    const maximo = filas.reduce((mayor, fila) => Math.max(mayor, fila.total), 1);
    return filas.map((fila) => ({ ...fila, ancho: Math.max(8, Math.round((fila.total / maximo) * 100)) }));
  }

  private opciones(campo: 'ambito' | 'area'): string[] {
    const vistos = new Set<string>();
    const lista: string[] = [];
    for (const alerta of this.alertas) {
      const nombre = (alerta[campo] || '').trim();
      const clave = nombre.toLowerCase();
      if (!nombre || vistos.has(clave)) continue;
      vistos.add(clave);
      lista.push(nombre);
    }
    return lista;
  }

  private ordenar(lista: AlertaIncidente[]): AlertaIncidente[] {
    return [...lista].sort((a, b) => {
      const porFecha = b.fecha.localeCompare(a.fecha);
      if (porFecha !== 0) return porFecha;
      return b.id - a.id;
    });
  }

  private fila(r: Omit<AlertaIncidente, 'pdf'>): AlertaIncidente {
    return { ...r, pdf: r.archivoNombre || 'Sin archivo' };
  }

  private dibujarPdf(blob: Blob): void {
    requestAnimationFrame(() => {
      void this.pintarHoja(blob);
    });
  }

  private async pintarHoja(blob: Blob): Promise<void> {
    const caja = this.hoja?.nativeElement;
    if (!caja) return;
    try {
      const datos = await blob.arrayBuffer();
      this.pdfDatos = datos;
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = 'assets/pdf.worker.min.js';
      const pdf = await pdfjs.getDocument({ data: datos.slice(0) }).promise;
      this.paginas = pdf.numPages;
      await this.dibujarPagina(datos, 1);
    } catch {
      caja.textContent = 'No se pudo mostrar el PDF.';
    }
  }

  private async dibujarPagina(datos: ArrayBuffer, numero: number): Promise<void> {
    const caja = this.hoja?.nativeElement;
    if (!caja) return;
    const pdfjs = await import('pdfjs-dist');
    pdfjs.GlobalWorkerOptions.workerSrc = 'assets/pdf.worker.min.js';
    const pdf = await pdfjs.getDocument({ data: datos.slice(0) }).promise;
    const pagina = await pdf.getPage(numero);
    const ancho = caja.clientWidth;
    const alto = caja.clientHeight;
    if (!ancho || !alto) return;
    const base = pagina.getViewport({ scale: 1 });
    const escala = Math.min(ancho / base.width, alto / base.height);
    const nitidez = window.devicePixelRatio || 1;
    const vista = pagina.getViewport({ scale: escala * nitidez });
    const lienzo = document.createElement('canvas');
    lienzo.width = Math.floor(vista.width);
    lienzo.height = Math.floor(vista.height);
    const contexto = lienzo.getContext('2d');
    if (!contexto) return;
    await pagina.render({ canvasContext: contexto, viewport: vista }).promise;
    caja.replaceChildren(lienzo);
  }

  private peso(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
