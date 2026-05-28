import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { TextoVozService, VozDisponible } from '../../../core/services/texto-voz.service';
import { environment } from '../../../../environments/environment';
import { MarkdownPipe } from '../pipes/markdown.pipe';
import { timeout, finalize, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

interface CatalogoEmpresaDoc {
  id: number;
  titulo: string;
  descripcion?: string | null;
}

interface SesionChat {
  id: string;
  titulo: string;
  mensajes: Mensaje[];
}

interface BloqueCodigo {
  lang: string;
  code: string;
}

interface Mensaje {
  tipo: 'usuario' | 'ia';
  contenido: string;
  catalogoEmpresa?: CatalogoEmpresaDoc[];
  fecha: Date;
  cargando?: boolean;
}

@Component({
  selector: 'app-ia-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, MarkdownPipe],
  templateUrl: './ia-chat.component.html',
  styleUrls: ['./ia-chat.component.scss'],
})
export class IaChatComponent implements OnInit, OnChanges, OnDestroy {
  @Input() empresaId!: number;
  @Input() modo = 'chat'; // 'chat' | 'busqueda' | 'resumen'
  @Input() documentoId?: number;
  /** Mostrar nombre del PDF cuando se elige desde la página general de IA */
  @Input() documentoTitulo = '';
  /** Layout tipo Claude: historial lateral + panel de código. */
  @Input() workspaceLayout = false;
  /** Selector de ámbito integrado en el panel lateral (sin barra superior). */
  @Input() documentosOpciones: { id: number; titulo: string }[] = [];
  @Input() loadingDocumentos = false;
  @Input() documentoSeleccionadoId: number | null = null;
  @Output() documentoSeleccionadoChange = new EventEmitter<number | null>();
  @Input() empresasOpciones: { id: number; nombre: string }[] = [];
  @Input() empresaSeleccionadaId: number | null = null;
  @Input() empresaNombre = '';
  @Output() empresaSeleccionadaChange = new EventEmitter<number>();

  @ViewChild('scrollContainer') scrollContainer?: ElementRef<HTMLElement>;

  mensajes: Mensaje[] = [];
  sesiones: SesionChat[] = [];
  sesionActivaId = 'default';
  panelCodigoVisible = true;
  /** Panel lateral izquierdo (historial / herramientas) visible en layout workspace. */
  panelHistorialVisible = true;
  /** Claves: empresa, documento, historial, agente, herramientas — true = sección cerrada. */
  seccionesColapsadas: Record<string, boolean> = {};
  mostrarAjustes = false;
  private ultimaPregunta = '';
  pregunta = '';
  enviando = false;
  /** Segundos desde que la IA está “pensando” (solo una solicitud activa). */
  segundosPensando = 0;
  private timerPensando?: ReturnType<typeof setInterval>;
  /** Lee en voz alta cada respuesta nueva de la IA. */
  leerRespuestasAuto = false;
  vozSoportada = false;
  vocesDisponibles: VozDisponible[] = [];
  vozSeleccionadaId = '';
  estadoVoz = {
    activo: false,
    mensajeId: null as number | null,
    pausado: false,
    fragmentoActual: 0,
    totalFragmentos: 0,
  };
  user: any;
  /** false = backend caído (ERR_CONNECTION_REFUSED en :8080) */
  backendDisponible = true;
  mensajeBackend = '';

  estadoMotor: {
    motor?: string;
    deepseekActivo?: boolean;
    documentosActivos?: number;
    embeddingsIndexados?: number;
    listo?: boolean;
    mensaje?: string;
    agente?: string;
    documentos?: { id: number; titulo: string; descripcion?: string }[];
  } | null = null;

  // Sugerencias rápidas (solo documentos de la empresa)
  sugerencias = [
    '¿Qué documentos HSE tenemos cargados?',
    '¿Qué dice la normativa sobre EPP en nuestros archivos?',
    'Resume los requisitos de trabajo en altura según nuestros PDF',
    '¿Qué obligaciones del empleador aparecen en los documentos?',
    '¿Qué controles críticos están definidos en la empresa?',
  ];

  sugerenciasSobreDocumento = [
    '¿De qué trata este documento?',
    'Resume los puntos clave de este PDF',
    '¿Qué obligaciones del trabajador aparecen aquí?',
    '¿Qué dice sobre EPP o equipos de protección?',
    'Extrae los requisitos de seguridad principales',
  ];

  /** Lista estable para @for (evita NG0956 al alternar documentoId). */
  get chipsSugerencias(): readonly string[] {
    return this.documentoId != null && this.documentoId > 0
      ? this.sugerenciasSobreDocumento
      : this.sugerencias;
  }

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private textoVoz: TextoVozService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    this.empresaId = this.empresaId || this.user?.empresaId;
    this.vozSoportada = this.textoVoz.soportado();
    this.leerRespuestasAuto = localStorage.getItem('ia-chat-leer-auto') === '1';
    this.textoVoz.estado$.subscribe((estado) => {
      this.estadoVoz = estado;
      this.cdr.markForCheck();
    });
    this.textoVoz.vocesDisponibles$.subscribe((voces) => {
      this.vocesDisponibles = voces;
      if (!this.vozSeleccionadaId && voces.length) {
        this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? voces[0].id;
      }
      this.cdr.markForCheck();
    });
    this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? '';

    this.mensajes.push({
      tipo: 'ia',
      contenido: this.textoBienvenida(),
      fecha: new Date(),
    });
    this.sesiones = [{ id: 'default', titulo: 'Nueva conversación', mensajes: this.mensajes }];
    this.sesionActivaId = 'default';
    this.verificarBackend();
  }

  private verificarBackend(): void {
    this.http.get<{ ok?: boolean }>(`${environment.apiUrl}/ia/salud`).subscribe({
      next: () => {
        this.backendDisponible = true;
        this.mensajeBackend = '';
        this.cargarEstadoMotor();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.backendDisponible = false;
        this.estadoMotor = null;
        this.mensajeBackend = this.mensajeBackendCaido(err);
        if (this.mensajes.length >= 1 && this.mensajes[0].tipo === 'ia') {
          this.mensajes[0].contenido = this.mensajeBackend;
          this.syncSesionActiva();
        }
        this.cdr.markForCheck();
      },
    });
  }

  private mensajeBackendCaido(err: { status?: number; message?: string }): string {
    if (err?.status === 0 || `${err?.message || ''}`.includes('Http failure')) {
      return `⚠️ **El servidor backend no está disponible** (puerto **8080**).

En una terminal, desde la carpeta \`backend\`, ejecute:

\`mvn spring-boot:run\`

Espere el mensaje **«Tomcat started on port 8080»** y recargue esta página. Sin el backend, el chat no puede leer el Editor de Contenido.`;
    }
    return `⚠️ No se pudo conectar con el servidor (HTTP ${err?.status ?? 'error'}).`;
  }

  private cargarEstadoMotor(): void {
    if (!this.empresaId || this.empresaId <= 0) return;
    this.http.get<any>(`${environment.apiUrl}/ia/estado/${this.empresaId}`).subscribe({
      next: (estado) => {
        this.estadoMotor = estado;
        if (this.mensajes.length === 1 && this.mensajes[0].tipo === 'ia' && !this.ultimaPregunta) {
          this.mensajes[0].contenido = this.textoBienvenida();
          this.syncSesionActiva();
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.estadoMotor = null;
      },
    });
  }

  ngOnDestroy(): void {
    this.detenerTimerPensando();
    this.textoVoz.detener();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['empresaId'] && !changes['empresaId'].firstChange) {
      this.cargarEstadoMotor();
    }
    if (!changes['documentoId'] || changes['documentoId'].firstChange) {
      return;
    }
    this.reiniciarConversacion();
  }

  private reiniciarConversacion(): void {
    this.textoVoz.detener();
    this.mensajes = [
      {
        tipo: 'ia',
        contenido: this.textoBienvenida(),
        fecha: new Date(),
      },
    ];
    this.pregunta = '';
    this.enviando = false;
    this.ultimaPregunta = '';
    this.detenerTimerPensando();
    this.syncSesionActiva();
  }

  nuevoChat(): void {
    const id = `s-${Date.now()}`;
    const bienvenida: Mensaje = {
      tipo: 'ia',
      contenido: this.textoBienvenida(),
      fecha: new Date(),
    };
    this.textoVoz.detener();
    this.mensajes = [bienvenida];
    this.sesiones.unshift({ id, titulo: 'Nueva conversación', mensajes: this.mensajes });
    this.sesionActivaId = id;
    this.pregunta = '';
    this.enviando = false;
    this.ultimaPregunta = '';
  }

  seleccionarSesion(id: string): void {
    const sesion = this.sesiones.find((s) => s.id === id);
    if (!sesion) return;
    this.textoVoz.detener();
    this.sesionActivaId = id;
    this.mensajes = sesion.mensajes;
    this.cdr.markForCheck();
  }

  private syncSesionActiva(): void {
    const sesion = this.sesiones.find((s) => s.id === this.sesionActivaId);
    if (sesion) sesion.mensajes = this.mensajes;
  }

  private tituloDesdePregunta(pregunta: string): string {
    const t = pregunta.trim();
    return t.length > 42 ? `${t.slice(0, 42)}…` : t || 'Nueva conversación';
  }

  private iniciarTimerPensando(): void {
    this.detenerTimerPensando();
    this.segundosPensando = 0;
    this.timerPensando = setInterval(() => {
      this.segundosPensando++;
      this.cdr.markForCheck();
    }, 1000);
  }

  private detenerTimerPensando(): void {
    if (this.timerPensando != null) {
      clearInterval(this.timerPensando);
      this.timerPensando = undefined;
    }
    this.segundosPensando = 0;
  }

  private textoBienvenida(): string {
    const nombreEmpresa = this.empresaNombre?.trim() || 'su empresa';
    const sobreDoc =
      this.documentoId != null && this.documentoId > 0;
    const nombreDoc =
      this.documentoTitulo?.trim() ||
      (sobreDoc ? 'este documento' : '');

    if (sobreDoc) {
      return `Hola. Soy **FISCALIZA-AI**. Respondo **solo** con el **Libro estructurado** guardado de **${nombreDoc}** (Editor de Contenido en la base de datos). Motor: **DeepSeek**.`;
    }

    const docs = this.estadoMotor?.documentosActivos;
    const motorOk = this.estadoMotor?.deepseekActivo !== false;
    const base = `Hola. Soy **FISCALIZA-AI** para **${nombreEmpresa}**.

Respondo **únicamente** con el **Editor de Contenido** guardado (agente cerrado: lee títulos H1/H2 y el texto debajo). **Seleccione el libro** en el panel lateral. Apartados como el Compromiso no usan DeepSeek.`;

    if (docs != null && docs === 0) {
      return `${base}

⚠️ Aún **no hay documentos** cargados. Suba PDF en **Documentos** y vuelva a consultar.`;
    }

    if (!motorOk) {
      return `${base}

⚠️ El motor **DeepSeek** no está configurado en el servidor. Contacte al administrador para activar \`DEEPSEEK_API_KEY\`.`;
    }

    return `${base}

**Motor:** DeepSeek · **Modo:** agente documental · ${docs ?? 0} PDF en su biblioteca.

Pregunte: *«¿Qué documentos tengo?»* o sobre el contenido de algún PDF.`;
  }

  verMisDocumentos(): void {
    this.pregunta = '¿Qué documentos HSE tengo cargados en la empresa?';
    this.enviarMensaje();
  }

  preguntarSobreDocumento(titulo: string): void {
    this.pregunta = `¿De qué trata el documento «${titulo}»?`;
    this.enviarMensaje();
  }

  enviarMensaje(): void {
    if (!this.pregunta.trim() || this.enviando) return;

    if (!this.backendDisponible) {
      this.mensajes.push({
        tipo: 'ia',
        contenido: this.mensajeBackend || this.mensajeBackendCaido({ status: 0 }),
        fecha: new Date(),
      });
      this.syncSesionActiva();
      return;
    }

    if (!this.empresaId || this.empresaId <= 0) {
      this.mensajes.push({
        tipo: 'ia',
        contenido: '⚠️ No hay empresa seleccionada. Elija una empresa para consultar sus documentos.',
        fecha: new Date(),
      });
      return;
    }

    const pregunta = this.pregunta.trim();
    this.pregunta = '';
    this.ultimaPregunta = pregunta;

    const sesion = this.sesiones.find((s) => s.id === this.sesionActivaId);
    if (sesion && sesion.titulo === 'Nueva conversación') {
      sesion.titulo = this.tituloDesdePregunta(pregunta);
    }

    // Agregar mensaje del usuario
    this.mensajes.push({
      tipo: 'usuario',
      contenido: pregunta,
      fecha: new Date(),
    });
    this.syncSesionActiva();
    this.scrollAlFinal();

    // Agregar placeholder de carga
    const idxCarga = this.mensajes.push({
      tipo: 'ia',
      contenido: '',
      fecha: new Date(),
      cargando: true,
    }) - 1;

    this.enviando = true;
    this.iniciarTimerPensando();

    // Llamar a la API
    const body: Record<string, string | number> = {
      pregunta: pregunta,
      empresaId: this.empresaId,
    };
    if (this.documentoId != null && this.documentoId > 0) {
      body['documentoId'] = this.documentoId;
    }
    this.http
      .post(`${environment.apiUrl}/ia/consultar`, body, { responseType: 'json' })
      .pipe(
        timeout(45_000),
        catchError((err) => {
          if (err?.name === 'TimeoutError') {
            return throwError(() => ({
              status: 0,
              message: 'La consulta tardó más de 45 s. Reinicie el backend y verifique el Libro estructurado guardado.',
            }));
          }
          return throwError(() => err);
        }),
        finalize(() => {
          this.enviando = false;
          this.detenerTimerPensando();
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: (respuesta: any) => {
          let contenido = respuesta?.respuesta ?? respuesta?.data?.respuesta ?? '';
          if (!contenido?.trim()) {
            contenido =
              '⚠️ El servidor respondió sin texto. Revise que el libro esté **guardado** en el Editor y que el backend esté actualizado.';
          }
          if (respuesta.advertencia?.trim()) {
            contenido += `\n\n---\n*${respuesta.advertencia}*`;
          }
          const msgActual: Mensaje = {
            tipo: 'ia',
            contenido,
            catalogoEmpresa: respuesta.catalogoEmpresa ?? [],
            fecha: new Date(),
            cargando: false,
          };
          this.mensajes[idxCarga] = msgActual;
          this.syncSesionActiva();
          this.scrollAlFinal();
          if (msgActual.contenido.includes('```')) {
            this.panelCodigoVisible = true;
          }
          if (this.leerRespuestasAuto && msgActual.contenido?.trim()) {
            setTimeout(() => this.escucharMensaje(idxCarga), 350);
          }
        },
        error: (err) => {
          if (err.status === 0) {
            this.backendDisponible = false;
            this.mensajeBackend = this.mensajeBackendCaido(err);
          }
          const detalle =
            err.error?.mensaje ||
            err.error?.message ||
            err.message ||
            (err.status === 0
              ? 'Sin conexión o tiempo de espera agotado (puerto 8080).'
              : `HTTP ${err.status}`);
          this.mensajes[idxCarga] = {
            tipo: 'ia',
            contenido: `❌ No se pudo completar la consulta. ${detalle}`,
            fecha: new Date(),
            cargando: false,
          };
          this.syncSesionActiva();
          this.scrollAlFinal();
        },
      });
  }

  onEnterComposer(ev: Event): void {
    const e = ev as KeyboardEvent;
    if (e.shiftKey) return;
    e.preventDefault();
    this.enviarMensaje();
  }

  reintentarUltima(): void {
    if (!this.ultimaPregunta || this.enviando) return;
    this.pregunta = this.ultimaPregunta;
    this.enviarMensaje();
  }

  togglePanelCodigo(): void {
    this.panelCodigoVisible = !this.panelCodigoVisible;
  }

  togglePanelHistorial(): void {
    this.panelHistorialVisible = !this.panelHistorialVisible;
  }

  toggleSeccion(id: string): void {
    this.seccionesColapsadas = {
      ...this.seccionesColapsadas,
      [id]: !this.seccionesColapsadas[id],
    };
  }

  seccionColapsada(id: string): boolean {
    return !!this.seccionesColapsadas[id];
  }

  toggleAjustes(): void {
    this.mostrarAjustes = !this.mostrarAjustes;
  }

  get etiquetaRol(): string {
    const rol = (this.user?.rol || '').trim();
    if (!rol) return 'USUARIO';
    return rol.replace(/_/g, ' ');
  }

  onDocumentoScopeChange(id: number | null): void {
    this.documentoSeleccionadoChange.emit(id);
  }

  onEmpresaScopeChange(id: number | null): void {
    if (id != null && id > 0) {
      this.empresaSeleccionadaChange.emit(id);
    }
  }

  get ultimoCodigo(): string | null {
    for (let i = this.mensajes.length - 1; i >= 0; i--) {
      const m = this.mensajes[i];
      if (m.tipo === 'ia' && !m.cargando && m.contenido) {
        const blocks = this.bloquesCodigo(m.contenido);
        if (blocks.length) return blocks[0].code;
      }
    }
    return null;
  }

  get nombreArchivoCodigo(): string {
    for (let i = this.mensajes.length - 1; i >= 0; i--) {
      const m = this.mensajes[i];
      if (m.tipo === 'ia' && m.contenido) {
        const blocks = this.bloquesCodigo(m.contenido);
        if (blocks.length) {
          const ext = blocks[0].lang === 'python' ? 'py' : blocks[0].lang || 'txt';
          return `fiscaliza_snippet.${ext}`;
        }
      }
    }
    return 'snippet.txt';
  }

  get lineasCodigo(): number {
    return this.ultimoCodigo?.split('\n').length ?? 0;
  }

  bloquesCodigo(contenido: string): BloqueCodigo[] {
    if (!contenido?.includes('```')) return [];
    const blocks: BloqueCodigo[] = [];
    const re = /```(\w*)\n?([\s\S]*?)```/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(contenido)) !== null) {
      blocks.push({ lang: m[1] || 'code', code: m[2].trim() });
    }
    return blocks;
  }

  copiarTexto(texto: string): void {
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(texto).catch(() => {});
    }
  }

  exportarChat(): void {
    const lineas = this.mensajes
      .filter((m) => !m.cargando)
      .map((m) => `[${m.tipo === 'usuario' ? 'Usuario' : 'FISCALIZA-AI'}]\n${m.contenido}`)
      .join('\n\n---\n\n');
    const blob = new Blob([lineas], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fiscaliza-ai-chat.txt';
    a.click();
    URL.revokeObjectURL(url);
  }

  private scrollAlFinal(): void {
    setTimeout(() => {
      const el = this.scrollContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 80);
  }

  usarSugerencia(sugerencia: string): void {
    this.pregunta = sugerencia;
    this.enviarMensaje();
  }

  enviarFeedback(idx: number, positivo: boolean): void {
    // Aquí se enviaría feedback a la API
    console.log(`Feedback ${positivo ? '👍' : '👎'} para mensaje ${idx}`);
  }

  alternarLecturaAutomatica(): void {
    this.leerRespuestasAuto = !this.leerRespuestasAuto;
    localStorage.setItem('ia-chat-leer-auto', this.leerRespuestasAuto ? '1' : '0');
    if (!this.leerRespuestasAuto) {
      this.textoVoz.detener();
    }
  }

  cambiarVoz(voiceId: string): void {
    this.vozSeleccionadaId = voiceId;
    this.textoVoz.seleccionarVoz(voiceId);
  }

  probarVoz(): void {
    if (!this.vozSeleccionadaId) {
      return;
    }
    this.textoVoz.seleccionarVoz(this.vozSeleccionadaId);
    this.textoVoz.leer(
      'Hola. Soy FISCALIZA-AI. Esta es una prueba de voz profesional para lectura de normativas HSE.',
      -1
    );
  }

  progresoLectura(idx: number): string | null {
    if (!this.estaLeyendoMensaje(idx) || this.estadoVoz.totalFragmentos <= 1) {
      return null;
    }
    return `${this.estadoVoz.fragmentoActual} / ${this.estadoVoz.totalFragmentos}`;
  }

  escucharMensaje(idx: number): void {
    const msg = this.mensajes[idx];
    if (!msg || msg.tipo !== 'ia' || msg.cargando || !msg.contenido?.trim()) {
      return;
    }
    if (this.textoVoz.estaLeyendoMensaje(idx)) {
      this.textoVoz.detener();
      return;
    }
    this.textoVoz.leer(msg.contenido, idx);
  }

  alternarPausaVoz(idx: number): void {
    if (this.textoVoz.estaLeyendoMensaje(idx)) {
      this.textoVoz.alternarPausa();
    }
  }

  estaLeyendoMensaje(idx: number): boolean {
    return this.textoVoz.estaLeyendoMensaje(idx);
  }

  mensajePausado(idx: number): boolean {
    return this.estaLeyendoMensaje(idx) && this.estadoVoz.pausado;
  }

}
