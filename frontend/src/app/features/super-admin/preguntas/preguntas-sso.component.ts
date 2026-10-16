import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { SimuladorService } from '../../../core/services/simulador.service';
import { SsoImportPreview, SsoOpcionAdmin, SsoPreguntaAdmin, SsoTema } from '../../../core/models/simulador.model';

@Component({
  selector: 'app-preguntas-sso',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, SuperAdminSidebarComponent],
  templateUrl: './preguntas-sso.component.html',
  styleUrls: ['./preguntas-sso.component.scss'],
})
export class PreguntasSsoComponent implements OnInit {
  private api = inject(SimuladorService);

  tab: 'preguntas' | 'temas' | 'importar' = 'preguntas';
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

  nuevoTema = '';
  editTemaId: number | null = null;
  editTemaNombre = '';

  archivo: File | null = null;
  preview: SsoImportPreview | null = null;
  importando = false;

  readonly tiempos = [5, 10, 20, 30];
  tiempoExamenMin = 30;
  guardandoTiempo = false;
  okTiempo: string | null = null;

  ngOnInit(): void {
    this.recargar();
    this.cargarConfig();
  }

  cargarConfig(): void {
    this.api.obtenerConfiguracion().subscribe({
      next: (c) => (this.tiempoExamenMin = c.tiempoExamenMin),
    });
  }

  guardarTiempo(minutos: number): void {
    this.guardandoTiempo = true;
    this.okTiempo = null;
    this.api.guardarConfiguracion(minutos).subscribe({
      next: (c) => {
        this.tiempoExamenMin = c.tiempoExamenMin;
        this.guardandoTiempo = false;
        this.okTiempo = `Los exámenes durarán ${c.tiempoExamenMin} minutos. Al vencerse se cierran y califican solos.`;
      },
      error: (e) => {
        this.guardandoTiempo = false;
        this.error = e?.error?.mensaje || 'No se pudo guardar el tiempo';
      },
    });
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
    this.editorAbierto = true;
    this.error = null;
  }

  abrirEditar(p: SsoPreguntaAdmin): void {
    this.editando = p;
    this.formTemaId = p.temaId;
    this.formEnunciado = p.enunciado;
    this.formExplicacion = p.explicacion || '';
    this.formFuente = p.fuente || '';
    this.formActivo = p.activo;
    this.formOpciones = p.opciones.map((o) => ({ ...o }));
    this.editorAbierto = true;
    this.error = null;
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
    if (this.formOpciones.some((o) => !o.esCorrecta && !o.argumento?.trim())) {
      this.error = 'En cada opción incorrecta indica por qué está mal';
      return;
    }
    this.guardando = true;
    this.error = null;
    const body = {
      temaId: this.formTemaId,
      enunciado: this.formEnunciado.trim(),
      explicacion: this.formExplicacion.trim() || undefined,
      fuente: this.formFuente.trim() || undefined,
      activo: this.formActivo,
      opciones: this.formOpciones.map((o) => ({
        id: o.id,
        texto: o.texto.trim(),
        esCorrecta: o.esCorrecta,
        argumento: o.esCorrecta ? undefined : o.argumento?.trim(),
      })),
    };
    const req = this.editando
      ? this.api.actualizarPregunta(this.editando.id, body)
      : this.api.crearPregunta(body);
    req.subscribe({
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
