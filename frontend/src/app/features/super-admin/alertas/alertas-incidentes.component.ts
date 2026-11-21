import { Component, HostListener, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { AlertaIncidente, AlertasIncidentesService } from '../../../core/services/alertas-incidentes.service';

interface IngresoAlerta {
  codigo: string;
  titulo: string;
  unidad: string;
  ambito: string;
  fecha: string;
  area: string;
  dueno: string;
  hora: string;
  empresa: string;
  impacto: string;
  grave: string;
  iap: string;
  eorpv: string;
}

@Component({
  selector: 'app-alertas-incidentes',
  standalone: true,
  imports: [CommonModule, FormsModule, SuperAdminSidebarComponent],
  templateUrl: './alertas-incidentes.component.html',
  styleUrls: ['./alertas-incidentes.component.scss'],
})
/** Lista y alta de alertas de incidentes, persistidas en PostgreSQL. */
export class AlertasIncidentesComponent implements OnInit {
  private readonly api = inject(AlertasIncidentesService);
  private readonly sanitizer = inject(DomSanitizer);

  ingresoAbierto = false;
  pdfAbierto: AlertaIncidente | null = null;
  pdfUrl: SafeResourceUrl | null = null;
  pdfEsImagen = false;
  aviso = '';
  errorCarga = '';
  guardando = false;
  archivoNombre = '';
  archivoPeso = '';
  private archivo: File | null = null;
  private pdfObjectUrl = '';

  form: IngresoAlerta = this.vacio();
  busqueda = '';
  filtroAmbito = '';
  filtroArea = '';
  filtroRapido: 'todos' | 'alta' = 'todos';

  alertas: AlertaIncidente[] = [];

  readonly eorpvOpciones = ['No aplica', 'Sí aplica'];

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.errorCarga = '';
    this.api.listar().subscribe({
      next: (rows) => { this.alertas = this.ordenar(rows.map((r) => this.fila(r))); },
      error: () => { this.errorCarga = 'No se pudo leer las alertas de la base de datos.'; },
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

  get visibles(): AlertaIncidente[] {
    const q = this.busqueda.trim().toLowerCase();
    return this.alertas.filter((a) => {
      if (this.filtroAmbito && a.ambito.trim().toLowerCase() !== this.filtroAmbito.trim().toLowerCase()) return false;
      if (this.filtroArea && a.area.trim().toLowerCase() !== this.filtroArea.trim().toLowerCase()) return false;
      if (this.filtroRapido === 'alta' && a.prioridad !== 'alta') return false;
      if (!q) return true;
      return `${a.codigo} ${a.titulo} ${a.detalle} ${a.area} ${a.ambito}`.toLowerCase().includes(q);
    });
  }

  get porEmpresa(): { nombre: string; total: number; ancho: number }[] {
    return this.agrupar('empresa');
  }

  get porAmbito(): { nombre: string; total: number; ancho: number }[] {
    return this.agrupar('ambito');
  }

  private agrupar(campo: 'empresa' | 'ambito'): { nombre: string; total: number; ancho: number }[] {
    const filas: { nombre: string; total: number }[] = [];
    for (const alerta of this.alertas) {
      const nombre = (alerta[campo] || '').trim();
      if (!nombre) continue;
      const clave = nombre.toLowerCase();
      const previo = filas.find((fila) => fila.nombre.toLowerCase() === clave);
      if (previo) previo.total += 1;
      else filas.push({ nombre, total: 1 });
    }
    const maximo = filas.reduce((mayor, fila) => Math.max(mayor, fila.total), 1);
    return filas.map((fila) => ({ ...fila, ancho: Math.max(8, Math.round((fila.total / maximo) * 100)) }));
  }

  fechaVisible(iso: string): string {
    const [y, m, d] = (iso || '').split('-');
    return d && m && y ? `${d}/${m}/${y}` : iso;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.ingresoAbierto) this.cerrarIngreso();
    else if (this.pdfAbierto) this.cerrarPdf();
  }

  abrirIngreso(): void {
    this.form = this.vacio();
    this.archivo = null;
    this.archivoNombre = '';
    this.archivoPeso = '';
    this.aviso = '';
    this.ingresoAbierto = true;
  }

  cerrarIngreso(): void {
    this.ingresoAbierto = false;
  }

  elegirArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.archivo = file;
    this.archivoNombre = file.name;
    this.archivoPeso = file.size >= 1048576
      ? `${(file.size / 1048576).toFixed(1)} MB`
      : `${Math.max(1, Math.round(file.size / 1024))} KB`;
  }

  quitarArchivo(input: HTMLInputElement): void {
    input.value = '';
    this.archivo = null;
    this.archivoNombre = '';
    this.archivoPeso = '';
  }

  guardarBorrador(): void {
    this.aviso = 'El borrador queda solo en esta ventana. Use Registrar alerta para guardarlo en la base.';
  }

  registrar(): void {
    const f = this.form;
    const falta = !f.codigo.trim() || !f.titulo.trim() || !f.unidad.trim() || !f.ambito.trim()
      || !f.fecha || !f.area.trim() || !f.dueno.trim() || !f.hora
      || !f.empresa.trim() || !f.impacto || !f.grave || !f.iap || !f.eorpv;
    if (falta) {
      this.aviso = 'Complete los campos obligatorios marcados con *.';
      return;
    }
    this.guardando = true;
    this.aviso = '';
    this.api.crear({
      codigo: f.codigo.trim(),
      titulo: f.titulo.trim(),
      unidad: f.unidad.trim(),
      ambito: f.ambito.trim(),
      fecha: f.fecha,
      area: f.area.trim(),
      dueno: f.dueno.trim(),
      hora: f.hora,
      empresa: f.empresa.trim(),
      impacto: f.impacto,
      accidenteGrave: f.grave === 'Sí',
      iap: f.iap === 'Sí',
      eorpv: f.eorpv,
    }, this.archivo).subscribe({
      next: (creada) => {
        this.alertas = this.ordenar([this.fila(creada), ...this.alertas]);
        this.guardando = false;
        this.ingresoAbierto = false;
      },
      error: (err) => {
        this.guardando = false;
        const body = err?.error;
        this.aviso = body?.errores?.join(' ') || body?.mensaje || 'No se pudo guardar la alerta.';
      },
    });
  }

  verPdf(alerta: AlertaIncidente): void {
    this.cerrarPdf();
    this.pdfAbierto = alerta;
    if (!alerta.tieneArchivo) return;
    this.api.archivo(alerta.id).subscribe({
      next: (blob) => {
        this.pdfEsImagen = blob.type.startsWith('image/');
        this.pdfObjectUrl = URL.createObjectURL(blob);
        this.pdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfObjectUrl);
      },
      error: () => { this.aviso = ''; },
    });
  }

  verPdfCompleto(): void {
    if (!this.pdfObjectUrl) return;
    window.open(this.pdfObjectUrl, '_blank', 'noopener');
  }

  cerrarPdf(): void {
    this.pdfAbierto = null;
    this.pdfUrl = null;
    this.pdfEsImagen = false;
    if (this.pdfObjectUrl) {
      URL.revokeObjectURL(this.pdfObjectUrl);
      this.pdfObjectUrl = '';
    }
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

  private vacio(): IngresoAlerta {
    return {
      codigo: '', titulo: '', unidad: '', ambito: '', fecha: '', area: '',
      dueno: '', hora: '', empresa: '', impacto: '', grave: '', iap: '', eorpv: '',
    };
  }
}
