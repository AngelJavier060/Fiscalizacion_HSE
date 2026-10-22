import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { of, switchMap } from 'rxjs';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { SimuladorService } from '../../../core/services/simulador.service';
import {
  SsoCobertura,
  SsoEstadisticaPregunta,
  SsoEstadisticas,
  SsoImportPreview,
  SsoOpcionAdmin,
  SsoPreguntaAdmin,
  SsoTema,
} from '../../../core/models/simulador.model';

@Component({
  selector: 'app-preguntas-sso',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, SuperAdminSidebarComponent],
  templateUrl: './preguntas-sso.component.html',
  styleUrls: ['./preguntas-sso.component.scss'],
})
export class PreguntasSsoComponent implements OnInit, OnDestroy {
  private api = inject(SimuladorService);

  tab: 'preguntas' | 'temas' | 'importar' | 'cobertura' | 'estadisticas' = 'preguntas';
  temas: SsoTema[] = [];
  preguntas: SsoPreguntaAdmin[] = [];
  cargando = true;
  q = '';
  filtroTema: number | null = null;
  filtroActivo: boolean | null = null;
  error: string | null = null;

  editorAbierto = false;
  guardando = false;
  editando: SsoPreguntaAdmin | null = null;
  formTemaId: number | null = null;
  formEnunciado = '';
  formExplicacion = '';
  formFuente = '';
  formActivo = true;
  formOpciones: SsoOpcionAdmin[] = [];
  formSustentoFile: File | null = null;
  formSustentoNombre: string | null = null;
  formSustentoTipo: string | null = null;
  formTieneSustento = false;
  formQuitarSustento = false;
  formSustentoPreviewUrl: string | null = null;

  nuevoTema = '';
  editTemaId: number | null = null;
  editTemaNombre = '';

  archivo: File | null = null;
  preview: SsoImportPreview | null = null;
  importando = false;

  readonly notas = [80, 90];
  readonly bancos = [
    { n: 20, min: 15, label: '20 preguntas', tiempo: '15 min' },
    { n: 40, min: 30, label: '40 preguntas', tiempo: '30 min' },
    { n: 60, min: 50, label: '60 preguntas', tiempo: '50 min' },
    { n: 0, min: 90, label: 'Todas', tiempo: '1 h 30 min' },
  ];
  tiempoExamenMin = 15;
  notaMinima = 90;
  preguntasPorBanco = 20;
  guardandoTiempo = false;
  okTiempo: string | null = null;
  cobertura: SsoCobertura | null = null;
  cargandoCobertura = false;
  stats: SsoEstadisticas | null = null;
  cargandoStats = false;
  statsDias = 0;
  statsQ = '';
  statsTema: number | null = null;
  statsDificultad: 'todas' | 'critica' | 'media' | 'dominada' = 'todas';
  statsUso: 'evaluadas' | 'nunca' | 'repetidas' | 'todas' = 'evaluadas';
  statsChip: 'ninguno' | 'falladas' | 'repetidas' | 'faltan' = 'ninguno';
  statsPagina = 1;
  readonly statsTamPagina = 8;
  statsSel: SsoEstadisticaPregunta | null = null;

  ngOnInit(): void {
    this.recargar();
    this.cargarConfig();
  }

  cargarConfig(): void {
    this.api.obtenerConfiguracion().subscribe({
      next: (c) => {
        this.tiempoExamenMin = c.tiempoExamenMin;
        this.notaMinima = c.notaMinima;
        this.preguntasPorBanco = this.leerBanco(c.preguntasPorBanco);
      },
    });
  }

  guardarConfig(parcial: { tiempoExamenMin?: number; notaMinima?: number; preguntasPorBanco?: number }): void {
    this.guardandoTiempo = true;
    this.okTiempo = null;
    this.api.guardarConfiguracion({
      tiempoExamenMin: parcial.tiempoExamenMin ?? this.tiempoExamenMin,
      notaMinima: parcial.notaMinima ?? this.notaMinima,
      preguntasPorBanco: parcial.preguntasPorBanco ?? this.preguntasPorBanco,
    }).subscribe({
      next: (c) => {
        this.tiempoExamenMin = c.tiempoExamenMin;
        this.notaMinima = c.notaMinima;
        this.preguntasPorBanco = this.leerBanco(c.preguntasPorBanco);
        this.guardandoTiempo = false;
        this.okTiempo = this.textoRegla(this.preguntasPorBanco, c.tiempoExamenMin, c.notaMinima);
      },
      error: (e) => {
        this.guardandoTiempo = false;
        this.error = e?.error?.mensaje || 'No se pudo guardar la configuración';
      },
    });
  }

  textoBanco(n: number): string {
    return n === 0 ? 'todas las preguntas' : `${n} preguntas`;
  }

  textoTiempo(min: number): string {
    if (min >= 60) {
      const h = Math.floor(min / 60);
      const m = min % 60;
      return m ? `${h} h ${m} min` : `${h} h`;
    }
    return `${min} min`;
  }

  textoRegla(banco: number, min: number, nota: number): string {
    return `Banco de ${this.textoBanco(banco)} · ${this.textoTiempo(min)} · avanza con ${nota}%.`;
  }

  private leerBanco(n?: number | null): number {
    return n === 0 || n === 20 || n === 40 || n === 60 ? n : 20;
  }

  elegirBanco(n: number): void {
    const item = this.bancos.find((b) => b.n === n);
    this.guardarConfig({ preguntasPorBanco: n, tiempoExamenMin: item?.min ?? 15 });
  }

  abrirCobertura(): void {
    this.tab = 'cobertura';
    this.cargandoCobertura = true;
    this.api.cobertura().subscribe({
      next: (c) => {
        this.cobertura = c;
        this.cargandoCobertura = false;
      },
      error: () => {
        this.cargandoCobertura = false;
        this.error = 'No se pudo cargar la cobertura del banco';
      },
    });
  }

  abrirEstadisticas(): void {
    this.tab = 'estadisticas';
    this.cargarEstadisticas();
  }

  cargarEstadisticas(): void {
    this.cargandoStats = true;
    this.api.estadisticas(this.statsDias).subscribe({
      next: (s) => {
        this.stats = s;
        this.cargandoStats = false;
        this.statsPagina = 1;
        if (this.statsSel) {
          this.statsSel = s.preguntas.find((p) => p.id === this.statsSel?.id) || s.preguntas[0] || null;
        } else {
          this.statsSel = s.preguntas.find((p) => !p.nuncaSalio) || s.preguntas[0] || null;
        }
      },
      error: () => {
        this.cargandoStats = false;
        this.error = 'No se pudieron cargar las estadísticas del banco';
      },
    });
  }

  get statsFiltradas(): SsoEstadisticaPregunta[] {
    if (!this.stats) {
      return [];
    }
    const q = this.statsQ.trim().toLowerCase();
    return this.stats.preguntas.filter((p) => {
      if (this.statsTema != null && p.temaId !== this.statsTema) {
        return false;
      }
      if (this.statsUso === 'evaluadas' && p.nuncaSalio) {
        return false;
      }
      if (this.statsUso === 'nunca' && !p.nuncaSalio) {
        return false;
      }
      if (this.statsUso === 'repetidas' && !p.repetida) {
        return false;
      }
      if (this.statsChip === 'falladas' && (p.nuncaSalio || p.aciertoPct >= 45)) {
        return false;
      }
      if (this.statsChip === 'repetidas' && !p.repetida) {
        return false;
      }
      if (this.statsChip === 'faltan' && !p.nuncaSalio) {
        return false;
      }
      if (this.statsDificultad === 'critica' && (p.nuncaSalio || p.aciertoPct >= 50)) {
        return false;
      }
      if (this.statsDificultad === 'media' && (p.nuncaSalio || p.aciertoPct < 50 || p.aciertoPct > 75)) {
        return false;
      }
      if (this.statsDificultad === 'dominada' && (p.nuncaSalio || p.aciertoPct < 75)) {
        return false;
      }
      if (q && !(`${p.enunciado} ${p.temaNombre}`).toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }

  get statsPaginas(): number {
    return Math.max(1, Math.ceil(this.statsFiltradas.length / this.statsTamPagina));
  }

  get statsVista(): SsoEstadisticaPregunta[] {
    const start = (this.statsPagina - 1) * this.statsTamPagina;
    return this.statsFiltradas.slice(start, start + this.statsTamPagina);
  }

  get statsTemasEvaluados() {
    return (this.stats?.porTema || []).filter((t) => t.intentos > 0);
  }

  colorAcierto(pct: number): 'ok' | 'mid' | 'bad' {
    if (pct >= 75) {
      return 'ok';
    }
    if (pct >= 50) {
      return 'mid';
    }
    return 'bad';
  }

  inspeccionar(p: SsoEstadisticaPregunta): void {
    this.statsSel = p;
  }

  irAEditarDesdeStats(id: number): void {
    const p = this.preguntas.find((x) => x.id === id);
    if (p) {
      this.abrirEditar(p);
      return;
    }
    this.api.obtenerPregunta(id).subscribe({
      next: (full) => this.abrirEditar(full),
      error: () => (this.error = 'No se pudo abrir la pregunta'),
    });
  }

  aplicarChip(chip: 'ninguno' | 'falladas' | 'repetidas' | 'faltan'): void {
    if (this.statsChip === chip) {
      this.statsChip = 'ninguno';
      this.statsUso = 'evaluadas';
    } else {
      this.statsChip = chip;
      this.statsUso = chip === 'faltan' ? 'nunca' : chip === 'repetidas' ? 'repetidas' : 'evaluadas';
    }
    this.statsPagina = 1;
  }

  exportarCsv(): void {
    const rows = this.statsFiltradas;
    const header = ['id', 'tema', 'enunciado', 'veces_salio', 'intentos', 'aciertos', 'acierto_pct', 'distractor', 'faltante', 'repetida'];
    const lines = [header.join(';')];
    for (const p of rows) {
      lines.push([
        p.id,
        `"${(p.temaNombre || '').replace(/"/g, '""')}"`,
        `"${(p.enunciado || '').replace(/"/g, '""')}"`,
        p.vecesSalio,
        p.intentos,
        p.aciertos,
        p.aciertoPct,
        `"${(p.distractorTexto || '').replace(/"/g, '""')}"`,
        p.nuncaSalio ? 1 : 0,
        p.repetida ? 1 : 0,
      ].join(';'));
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'estadisticas-preguntas-sso.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  recargar(): void {
    this.cargando = true;
    this.api.listarTemas().subscribe({
      next: (t) => (this.temas = t),
      error: () => {},
    });
    this.api.listarPreguntas(this.q || undefined, this.filtroTema, this.filtroActivo).subscribe({
      next: (p) => {
        this.preguntas = p;
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
        this.error = 'No se pudieron cargar las preguntas';
      },
    });
  }

  get totalActivas(): number {
    return this.preguntas.filter((p) => p.activo).length;
  }

  abrirNueva(): void {
    this.editando = null;
    this.formTemaId = this.temas[0]?.id ?? null;
    this.formEnunciado = '';
    this.formExplicacion = '';
    this.formFuente = '';
    this.formActivo = true;
    this.formOpciones = [
      { texto: '', esCorrecta: true, argumento: '' },
      { texto: '', esCorrecta: false, argumento: '' },
    ];
    this.resetSustentoForm();
    this.editorAbierto = true;
    this.error = null;
  }

  abrirEditar(p: SsoPreguntaAdmin): void {
    this.editando = p;
    this.formTemaId = p.temaId;
    this.formEnunciado = p.enunciado;
    const argumentoOpcion = p.opciones.find((o) => !o.esCorrecta && o.argumento?.trim())?.argumento?.trim();
    this.formExplicacion = p.explicacion?.trim() || argumentoOpcion || '';
    this.formFuente = p.fuente || '';
    this.formActivo = p.activo;
    this.formOpciones = p.opciones.map((o) => ({ ...o, argumento: '' }));
    this.resetSustentoForm();
    this.formTieneSustento = !!p.tieneSustento;
    this.formSustentoNombre = p.sustentoNombre || (p.tieneSustento ? 'evidencia' : null);
    this.formSustentoTipo = p.sustentoTipo || null;
    this.editorAbierto = true;
    this.error = null;
    this.cargarPreviewExistente(p);
  }

  ngOnDestroy(): void {
    this.revokePreview();
  }

  agregarOpcion(): void {
    if (this.formOpciones.length >= 5) return;
    this.formOpciones.push({ texto: '', esCorrecta: false, argumento: '' });
  }

  quitarOpcion(i: number): void {
    if (this.formOpciones.length <= 2) return;
    const eraCorrecta = this.formOpciones[i].esCorrecta;
    this.formOpciones.splice(i, 1);
    if (eraCorrecta && this.formOpciones.length) {
      this.formOpciones[0].esCorrecta = true;
    }
  }

  marcarCorrecta(i: number): void {
    this.formOpciones.forEach((o, idx) => (o.esCorrecta = idx === i));
  }

  guardarPregunta(): void {
    const correctas = this.formOpciones.filter((o) => o.esCorrecta).length;
    if (!this.formTemaId || !this.formEnunciado.trim()) {
      this.error = 'Tema y enunciado son obligatorios';
      return;
    }
    if (this.formOpciones.some((o) => !o.texto.trim())) {
      this.error = 'Todas las opciones deben tener texto';
      return;
    }
    if (correctas !== 1) {
      this.error = 'Marca exactamente una opción como correcta';
      return;
    }
    if (!this.formExplicacion.trim()) {
      this.error = 'Indica por qué está mal';
      return;
    }
    this.guardando = true;
    this.error = null;
    const body = {
      temaId: this.formTemaId,
      enunciado: this.formEnunciado.trim(),
      explicacion: this.formExplicacion.trim(),
      fuente: this.formFuente.trim() || undefined,
      activo: this.formActivo,
      opciones: this.formOpciones.map((o) => ({
        id: o.id,
        texto: o.texto.trim(),
        esCorrecta: o.esCorrecta,
        argumento: undefined,
      })),
    };
    const req = this.editando
      ? this.api.actualizarPregunta(this.editando.id, body)
      : this.api.crearPregunta(body);
    req.pipe(
      switchMap((saved) => {
        this.editando = saved;
        if (this.formSustentoFile) {
          return this.api.subirSustento(saved.id, this.formSustentoFile);
        }
        if (this.formQuitarSustento && saved.tieneSustento) {
          return this.api.eliminarSustento(saved.id);
        }
        return of(saved);
      }),
    ).subscribe({
      next: () => {
        this.guardando = false;
        this.editorAbierto = false;
        this.recargar();
      },
      error: (e) => {
        this.guardando = false;
        this.error = e?.error?.mensaje || 'No se pudo guardar';
      },
    });
  }

  onSustento(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.revokePreview();
    this.formSustentoFile = null;
    if (!file) return;
    if (file.size > 15 * 1024 * 1024) {
      this.error = 'El archivo no puede superar 15 MB';
      input.value = '';
      return;
    }
    const ok = /\.(pdf|jpe?g|png|webp|gif)$/i.test(file.name) || /^(application\/pdf|image\/)/.test(file.type);
    if (!ok) {
      this.error = 'Solo PDF o imagen (JPG, PNG, WEBP, GIF)';
      input.value = '';
      return;
    }
    this.error = null;
    this.formSustentoFile = file;
    this.formSustentoNombre = file.name;
    this.formSustentoTipo = file.type;
    this.formTieneSustento = true;
    this.formQuitarSustento = false;
    if (file.type.startsWith('image/')) {
      this.formSustentoPreviewUrl = URL.createObjectURL(file);
    }
  }

  quitarSustentoForm(): void {
    this.revokePreview();
    this.formSustentoFile = null;
    this.formSustentoNombre = null;
    this.formSustentoTipo = null;
    this.formTieneSustento = false;
    this.formQuitarSustento = true;
  }

  verSustentoEditor(): void {
    if (this.formSustentoFile) {
      const url = URL.createObjectURL(this.formSustentoFile);
      window.open(url, '_blank');
      return;
    }
    if (this.editando?.id && this.formTieneSustento && !this.formQuitarSustento) {
      this.abrirSustento(this.editando.id);
    }
  }

  abrirSustento(preguntaId: number): void {
    this.api.descargarSustento(preguntaId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: () => (this.error = 'No se pudo abrir el sustento'),
    });
  }

  esImagenSustento(): boolean {
    return !!this.formSustentoTipo?.startsWith('image/');
  }

  private resetSustentoForm(): void {
    this.revokePreview();
    this.formSustentoFile = null;
    this.formSustentoNombre = null;
    this.formSustentoTipo = null;
    this.formTieneSustento = false;
    this.formQuitarSustento = false;
  }

  private cargarPreviewExistente(p: SsoPreguntaAdmin): void {
    if (!p.tieneSustento || !p.sustentoTipo?.startsWith('image/')) return;
    this.api.descargarSustento(p.id).subscribe({
      next: (blob) => {
        this.revokePreview();
        this.formSustentoPreviewUrl = URL.createObjectURL(blob);
      },
    });
  }

  private revokePreview(): void {
    if (this.formSustentoPreviewUrl) {
      URL.revokeObjectURL(this.formSustentoPreviewUrl);
      this.formSustentoPreviewUrl = null;
    }
  }

  letra(i: number): string {
    return String.fromCharCode(65 + i);
  }

  toggle(p: SsoPreguntaAdmin): void {
    this.api.togglePregunta(p.id).subscribe({
      next: () => this.recargar(),
      error: (e) => (this.error = e?.error?.mensaje || 'No se pudo cambiar el estado'),
    });
  }

  crearTema(): void {
    const nombre = this.nuevoTema.trim();
    if (!nombre) return;
    this.api.crearTema(nombre).subscribe({
      next: () => {
        this.nuevoTema = '';
        this.recargar();
      },
      error: (e) => (this.error = e?.error?.mensaje || 'No se pudo crear el tema'),
    });
  }

  guardarTema(t: SsoTema): void {
    const nombre = (this.editTemaId === t.id ? this.editTemaNombre : t.nombre).trim();
    this.api.actualizarTema(t.id, nombre, t.activo).subscribe({
      next: () => {
        this.editTemaId = null;
        this.recargar();
      },
      error: (e) => (this.error = e?.error?.mensaje || 'No se pudo actualizar el tema'),
    });
  }

  toggleTema(t: SsoTema): void {
    this.api.actualizarTema(t.id, t.nombre, !t.activo).subscribe({
      next: () => this.recargar(),
    });
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.archivo = input.files?.[0] ?? null;
    this.preview = null;
  }

  previsualizar(): void {
    if (!this.archivo) return;
    this.importando = true;
    this.api.previewImport(this.archivo).subscribe({
      next: (p) => {
        this.preview = p;
        this.importando = false;
      },
      error: (e) => {
        this.importando = false;
        this.error = e?.error?.mensaje || 'No se pudo leer el archivo';
      },
    });
  }

  confirmarCarga(): void {
    if (!this.preview) return;
    this.importando = true;
    this.api.confirmarImport(this.preview).subscribe({
      next: (p) => {
        this.importando = false;
        this.preview = p;
        this.recargar();
        this.tab = 'preguntas';
      },
      error: (e) => {
        this.importando = false;
        this.error = e?.error?.mensaje || 'No se pudo importar';
      },
    });
  }
}
