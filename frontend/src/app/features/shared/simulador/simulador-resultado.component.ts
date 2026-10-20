import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { SuperAdminSidebarComponent } from '../../super-admin/shared/super-admin-sidebar/super-admin-sidebar.component';
import { UsuarioSidebarComponent } from '../../usuario/shared/usuario-sidebar/usuario-sidebar.component';
import { AuthService } from '../../../core/services/auth.service';
import { SimuladorService } from '../../../core/services/simulador.service';
import { SsoResultado, SsoRevision } from '../../../core/models/simulador.model';

type FiltroTema = 'todos' | 'criticos' | 'aprobados';
type Criticidad = 'Crítico' | 'Obligatorio' | 'Alto' | 'Medio';

export interface TemaFila {
  tema: string;
  total: number;
  correctas: number;
  pct: number;
  criticidad: Criticidad;
  aprobado: boolean;
  parcial: boolean;
}

@Component({
  selector: 'app-simulador-resultado',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent, UsuarioSidebarComponent],
  templateUrl: './simulador-resultado.component.html',
  styleUrls: ['./simulador-resultado.component.scss'],
})
export class SimuladorResultadoComponent implements OnInit, OnDestroy {
  private api = inject(SimuladorService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);

  res: SsoResultado | null = null;
  error: string | null = null;
  cargando = true;
  iniciando: 'misma' | 'otra' | null = null;
  verRevision = true;
  filtro: FiltroTema = 'todos';
  notaMinima = 70;
  rol = this.auth.getUserRole();
  abiertaId: number | null = null;
  mostrarSustentoId: number | null = null;
  previewUrls: Record<number, string> = {};
  previewSafe: Record<number, SafeResourceUrl> = {};
  cargandoSustento: Record<number, boolean> = {};

  get base(): string {
    if (this.rol === 'SUPER_ADMIN') return '/super-admin/simulador';
    if (this.rol === 'ADMIN_EMPRESA') return '/admin-empresa/simulador';
    return '/usuario/simulador';
  }

  get documentosBase(): string {
    if (this.rol === 'SUPER_ADMIN') return '/super-admin/documentos';
    if (this.rol === 'ADMIN_EMPRESA') return '/admin-empresa/documentos';
    return '/usuario/documentos';
  }

  get esSuper(): boolean { return this.rol === 'SUPER_ADMIN'; }
  get esUsuario(): boolean { return this.rol === 'USUARIO'; }

  get nota(): number {
    return Number(this.res?.nota ?? 0);
  }

  get incorrectas(): number {
    if (!this.res) return 0;
    return Math.max(0, this.res.totalPreguntas - this.res.correctas);
  }

  get tasaError(): number {
    if (!this.res?.totalPreguntas) return 0;
    return (this.incorrectas / this.res.totalPreguntas) * 100;
  }

  get aciertosNecesarios(): number {
    if (!this.res) return 0;
    return Math.ceil((this.res.totalPreguntas * this.notaMinima) / 100);
  }

  get brecha(): number {
    return this.nota - this.notaMinima;
  }

  get dasharray(): string {
    const pct = Math.max(0, Math.min(100, this.nota));
    return `${pct}, 100`;
  }

  get tiempoEmpleado(): string {
    if (!this.res?.fechaInicio || !this.res.fechaFin) return '—';
    const ms = new Date(this.res.fechaFin).getTime() - new Date(this.res.fechaInicio).getTime();
    if (!Number.isFinite(ms) || ms < 0) return '—';
    const totalSec = Math.round(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}m ${s.toString().padStart(2, '0')}s`;
  }

  get promedioReactivo(): string {
    if (!this.res?.fechaInicio || !this.res.fechaFin || !this.res.totalPreguntas) return '—';
    const ms = new Date(this.res.fechaFin).getTime() - new Date(this.res.fechaInicio).getTime();
    if (!Number.isFinite(ms) || ms < 0) return '—';
    const seg = Math.round(ms / 1000 / this.res.totalPreguntas);
    return `${seg} seg / reactivo`;
  }

  get fechaEval(): string {
    const raw = this.res?.fechaFin || this.res?.fechaInicio;
    if (!raw) return '';
    const txt = new Date(raw).toLocaleDateString('es-EC', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
    return txt.charAt(0).toUpperCase() + txt.slice(1);
  }

  get modoLabel(): string {
    const modo = this.res?.modo || '';
    if (modo === 'falladas') return 'Solo falladas';
    if (modo === 'banco' || modo === '20' || modo === '40') return 'Banco de preguntas';
    return `Modo ${modo}`;
  }

  get filas(): TemaFila[] {
    return (this.res?.aciertosPorTema || []).map((t) => {
      const pct = t.total ? (t.correctas / t.total) * 100 : 0;
      const criticidad = this.criticidadDe(t.tema);
      return {
        tema: t.tema,
        total: t.total,
        correctas: t.correctas,
        pct,
        criticidad,
        aprobado: pct >= this.notaMinima,
        parcial: pct >= 50 && pct < this.notaMinima,
      };
    });
  }

  get filasVisibles(): TemaFila[] {
    if (this.filtro === 'criticos') {
      return this.filas.filter((f) => f.criticidad === 'Crítico' || f.criticidad === 'Obligatorio');
    }
    if (this.filtro === 'aprobados') {
      return this.filas.filter((f) => f.aprobado || f.parcial);
    }
    return this.filas;
  }

  get totalCriticos(): number {
    return this.filas.filter((f) => f.criticidad === 'Crítico' || f.criticidad === 'Obligatorio').length;
  }

  get totalAprobados(): number {
    return this.filas.filter((f) => f.aprobado || f.parcial).length;
  }

  get temasSobre50(): number {
    return this.filas.filter((f) => f.pct >= 50).length;
  }

  get planRefuerzo(): TemaFila[] {
    return [...this.filas]
      .sort((a, b) => a.pct - b.pct || b.total - a.total)
      .filter((f) => !f.aprobado)
      .slice(0, 3);
  }

  get diagnostico(): string {
    const debiles = [...this.filas]
      .sort((a, b) => a.pct - b.pct)
      .filter((f) => f.pct < this.notaMinima)
      .slice(0, 2)
      .map((f) => f.tema);
    if (this.res?.aprobado) {
      return debiles.length
        ? `Desempeño dentro del umbral operativo. Conviene reforzar ${debiles.join(' y ')} en el próximo ciclo.`
        : 'Desempeño dentro del umbral operativo. Mantenga la lectura periódica de la normativa vigente.';
    }
    if (debiles.length) {
      return `Vulnerabilidad en ${debiles.join(' y ')}. Se recomienda un ciclo de nivelación antes de un nuevo intento de habilitación.`;
    }
    return 'El resultado no alcanza el umbral de habilitación operativa. Revise las respuestas y reintente el simulacro.';
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.inicio().subscribe({
      next: (d) => {
        if (d?.notaMinima) this.notaMinima = d.notaMinima;
      },
    });
    this.api.resultado(id).subscribe({
      next: (r) => {
        this.res = r;
        this.cargando = false;
      },
      error: (e) => {
        this.cargando = false;
        this.error = e?.error?.mensaje || 'No se pudo cargar el resultado';
      },
    });
  }

  ngOnDestroy(): void {
    Object.values(this.previewUrls).forEach((u) => URL.revokeObjectURL(u));
  }

  volver(): void {
    this.router.navigate([this.base]);
  }

  get bancoDesdeResultado(): number {
    const n = this.res?.totalPreguntas || 0;
    return n === 20 || n === 40 || n === 60 ? n : 0;
  }

  empezarDesdeResultado(nuevoSet: boolean): void {
    if (!this.esUsuario || !this.res || this.iniciando) return;
    this.iniciando = nuevoSet ? 'otra' : 'misma';
    this.error = null;
    const modo = this.res.modo === 'falladas' ? 'falladas' : 'banco';
    this.api.iniciarExamen(
      modo,
      this.res.temaId ?? null,
      this.bancoDesdeResultado,
      nuevoSet,
      this.res.id,
    ).subscribe({
      next: (ex) => this.router.navigate([this.base, 'examen', ex.id]),
      error: (e) => {
        this.iniciando = null;
        this.error = e?.error?.mensaje || 'No se pudo iniciar el examen';
      },
    });
  }

  irADocumentos(): void {
    this.router.navigate([this.documentosBase]);
  }

  toggleRevision(): void {
    this.verRevision = true;
    this.irARevision();
  }

  irARevision(): void {
    this.verRevision = true;
    const primera = (this.res?.revision || []).find((p) => !p.esCorrecta);
    if (primera && this.abiertaId == null) {
      this.abrir(primera);
    }
    setTimeout(() => {
      document.getElementById('rev-lista')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  abrirTema(tema: string): void {
    const p = (this.res?.revision || []).find((r) => !r.esCorrecta && r.temaNombre === tema);
    if (!p) return;
    this.verRevision = true;
    this.abrir(p);
    setTimeout(() => {
      document.getElementById('rev-' + p.preguntaId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  abrir(p: SsoRevision): void {
    if (p.esCorrecta) {
      return;
    }
    this.abiertaId = p.preguntaId;
    this.cerrarVisor();
  }

  recoger(): void {
    this.cerrarVisor();
    const lista = this.res?.revision || [];
    const idx = lista.findIndex((p) => p.preguntaId === this.abiertaId);
    const siguiente = lista.slice(idx + 1).find((p) => !p.esCorrecta);
    this.abiertaId = siguiente ? siguiente.preguntaId : null;
    if (this.abiertaId != null) {
      setTimeout(() => {
        document.getElementById('rev-' + this.abiertaId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    }
  }

  get visorPregunta(): SsoRevision | null {
    if (this.mostrarSustentoId == null || !this.res) {
      return null;
    }
    return this.res.revision.find((p) => p.preguntaId === this.mostrarSustentoId) || null;
  }

  cerrarVisor(): void {
    this.mostrarSustentoId = null;
  }

  esImagen(p: SsoRevision): boolean {
    const t = (p.sustentoTipo || '').toLowerCase();
    const n = (p.sustentoNombre || '').toLowerCase();
    return t.startsWith('image/') || /\.(jpg|jpeg|png|webp|gif)$/.test(n);
  }

  esPdf(p: SsoRevision): boolean {
    const t = (p.sustentoTipo || '').toLowerCase();
    const n = (p.sustentoNombre || '').toLowerCase();
    return t.includes('pdf') || n.endsWith('.pdf');
  }

  verEvidencia(p: SsoRevision): void {
    this.mostrarSustentoId = p.preguntaId;
    this.cargarPreview(p);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.mostrarSustentoId != null) {
      this.cerrarVisor();
    }
  }

  private mimeSustento(p: SsoRevision): string {
    if (p.sustentoTipo) {
      return p.sustentoTipo;
    }
    if (this.esPdf(p)) {
      return 'application/pdf';
    }
    return '';
  }

  private blobTipado(blob: Blob, p: SsoRevision): Blob {
    const mime = this.mimeSustento(p);
    if (!mime || blob.type === mime) {
      return blob;
    }
    return new Blob([blob], { type: mime });
  }

  private cargarPreview(p: SsoRevision): void {
    if (!p.tieneSustento || this.previewUrls[p.preguntaId] || this.cargandoSustento[p.preguntaId]) {
      return;
    }
    this.cargandoSustento[p.preguntaId] = true;
    this.api.descargarSustento(p.preguntaId).subscribe({
      next: (blob) => {
        const typed = this.blobTipado(blob, p);
        const url = URL.createObjectURL(typed);
        this.previewUrls[p.preguntaId] = url;
        this.previewSafe[p.preguntaId] = this.sanitizer.bypassSecurityTrustResourceUrl(url);
        this.cargandoSustento[p.preguntaId] = false;
      },
      error: () => {
        this.cargandoSustento[p.preguntaId] = false;
        this.error = 'No se pudo abrir el sustento de la pregunta';
      },
    });
  }

  prioridad(fila: TemaFila, index: number): string {
    if (fila.criticidad === 'Crítico' || fila.pct < 30) return 'Prioridad 1';
    return index === 0 ? 'Prioridad 1' : 'Prioridad 2';
  }

  private criticidadDe(tema: string): Criticidad {
    const t = (tema || '').toLowerCase();
    if (/izaje|asme|altura|ansi|incendio|extintor|el[eé]ctric/.test(t)) return 'Crítico';
    if (/legislaci|decreto|2393|c[oó]digo del trabajo/.test(t)) return 'Obligatorio';
    if (/epp|protecci[oó]n|clasificad|atex|alarma|detecci[oó]n/.test(t)) return 'Alto';
    return 'Medio';
  }
}
