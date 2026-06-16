import { Component, OnDestroy, OnInit, inject, ChangeDetectorRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { AuthService } from '../../../../core/services/auth.service';
import { environment } from '../../../../../environments/environment';
import { TextoVozService, VozDisponible } from '../../../../core/services/texto-voz.service';
import { DocumentoCacheService } from '../../../../core/services/documento-cache.service';
import { IaChatComponent } from '../../../shared/ia-chat/ia-chat.component';
import { LectorDocumentoComponent } from '../../../shared/lector-documento/lector-documento.component';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';
import { EditorTextoRicoComponent } from '../../../shared/editor-texto-rico/editor-texto-rico.component';
import {
  buildDocumentoContext,
  documentNavigationLinks,
  type DocumentoLinks,
} from '../../../../core/helpers/documento-routing.helper';

interface MarcadorLecturaDoc {
  documentoId: number;
  modo: 'todo' | 'seccion';
  seccionTitulo?: string;
  indiceFragmento: number;
  totalFragmentos: number;
  progresoPct: number;
  actualizadoEn: number;
}
import {
  buscarSeccionEnHtmlEditor,
  buscarSeccionEnLista,
  construirTextoLecturaDocumento,
  construirTextoLecturaSeccion,
  extraerSeccionesDesdeEditor,
  forzarEstructuraDesdeTexto,
  resolverTextoBaseParaEstructura,
  filtrarIndiceSidebar,
  htmlTieneEstructura,
  normalizarTextoLectura,
  normalizarRespuestaTexto,
  respuestaTextoNecesitaEstructura,
  quitarIndiceDelHtml,
  textoPlanoDesdeHtml,
  type SeccionTexto,
} from '../../../../core/helpers/texto-estructura.helper';

@Component({
  selector: 'app-documento-detalle',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, IaChatComponent, LectorDocumentoComponent, MarkdownPipe, EditorTextoRicoComponent],
  templateUrl: './documento-detalle.component.html',
  styleUrls: ['./documento-detalle.component.scss'],
})
export class DocumentoDetalleComponent implements OnInit, OnDestroy {
  documento: any = null;
  puntosClave: any[] = [];
  loading = true;
  cargandoPuntos = false;
  regenerandoIa = false;
  user: any;

  /** Pestaña activa */
  pestanaActiva: 'lectura' | 'ia' = 'lectura';
  /** Pestaña principal del topbar */
  pestanaPrincipal: 'documentos' | 'normativa' | 'lectura' = 'documentos';

  /** Vista previa del PDF (blob + URL segura para iframe) */
  pdfSafeUrl: SafeResourceUrl | null = null;
  private pdfBlobUrl: string | null = null;
  cargandoPdf = false;
  pdfError: string | null = null;

  errorMsgPuntos: string | null = null;
  // Nuevo punto manual
  nuevoPunto = '';
  get puedeAgregarPunto(): boolean {
    return this.nuevoPunto.trim().length > 0;
  }

  get puntosIa(): number {
    return this.puntosClave.filter(p => p.esIa).length;
  }

  get puntosManuales(): number {
    return this.puntosClave.filter(p => !p.esIa).length;
  }

  get puntosRevisados(): number {
    return this.puntosClave.filter(p => p.revisado).length;
  }

  get pctRevisados(): number {
    return this.puntosClave.length > 0
      ? Math.round((this.puntosRevisados / this.puntosClave.length) * 100)
      : 0;
  }

  autoExpand(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    textarea.style.height = 'auto';
    textarea.style.height = textarea.scrollHeight + 'px';
  }
  editandoPuntoId: number | null = null;
  editandoPuntoTexto = '';

  // Filtro
  filtro: 'todos' | 'ia' | 'manual' | 'no-revisados' = 'todos';
  /** Puntos con contenido largo expandidos en pantalla */
  puntosExpandidos = new Set<number>();

  nav: DocumentoLinks = documentNavigationLinks({
    empresaId: null,
    esRutaSuperAdmin: false,
  });
  esRutaSuperAdmin = false;

  // ===== LECTOR STATE =====
  textoCompleto = '';
  secciones: any[] = [];
  seccionesFiltradas: any[] = [];
  cargandoTexto = false;
  terminoBusqueda = '';
  seccionActiva: any = null;
  textoResumen = '';

  // ===== PANEL DERECHO — etiqueta fija (siempre visible) =====
  readonly libroPanelTitulo = 'Libro estructurado';
  readonly libroPanelSubtitulo =
    'Su «Word» dentro del sistema · PDF a la izquierda solo referencia · H1 títulos/apartados (Compromiso), H2 estándares';

  // ===== TEXT EDITOR =====
  modoVista: 'pdf' | 'texto' = 'texto';
  /** 'lectura' = vista renderizada como mockup ENAP; 'edicion' = editor rico */
  modoTexto: 'lectura' | 'edicion' = 'lectura';
  textoEditado = '';
  textoEstructurado = '';
  indiceEntradas = 0;
  reestructurando = false;
  estructuraProgreso = 0;
  estructuraMensaje = '';
  estructuraResultado: string | null = null;
  /** Muestra aviso para pulsar «Estructurar libro» cuando el HTML aún no tiene H1/H2. */
  avisoEstructuraLibro = false;
  guardandoTexto = false;
  textoGuardadoExitoso = false;
  textoCargado = false;
  editorLastSaved: string | null = null;

  estadoVoz = { activo: false, pausado: false, fragmentoActual: 0, totalFragmentos: 0 };
  vocesDisponibles: VozDisponible[] = [];
  vozSeleccionadaId = '';
  vozSoportada = true;
  cargandoVoces = false;
  audioError: string | null = null;
  velocidadLectura = 0.96;
  hayAudioActivo = false;
  /** Fragmento TTS que se está leyendo (vista previa en panel). */
  textoLecturaActual = '';
  progresoLecturaPct = 0;
  /** Marcador persistido para continuar tras cerrar o parar. */
  marcadorLectura: MarcadorLecturaDoc | null = null;
  modoLecturaActual: 'todo' | 'seccion' | null = null;

  /** Pocket FM player state */
  playerExpandido = false;

  private textoVoz = inject(TextoVozService);
  private cache = inject(DocumentoCacheService);
  private cdr = inject(ChangeDetectorRef);
  private syncSeccionesTimer?: ReturnType<typeof setTimeout>;
  /** Caché del último parseo del editor (evita DOMParser en cada clic del índice). */
  private seccionesEditorCache: SeccionTexto[] = [];
  private htmlEditorCache = '';
  private readonly STORAGE_MARCADOR_PREFIX = 'hse-lectura-doc-';
  private pollProcesamientoTimer?: ReturnType<typeof setInterval>;
  reprocesandoDoc = false;

  @ViewChild(EditorTextoRicoComponent) editorRico?: EditorTextoRicoComponent;

  // ─── Tamaño de fuente ─────────────────────────
  tamanoFuente: 'normal' | 'grande' | 'extra' = 'normal';

  setTamanoFuente(nuevo: 'normal' | 'grande' | 'extra'): void {
    this.tamanoFuente = nuevo;
    document.documentElement.setAttribute('data-font-size', nuevo);
    localStorage.setItem('hse-font-size', nuevo);
  }

  private restaurarTamanoFuente(): void {
    const guardado = localStorage.getItem('hse-font-size') as 'normal' | 'grande' | 'extra' | null;
    if (guardado && ['normal', 'grande', 'extra'].includes(guardado)) {
      this.tamanoFuente = guardado;
      document.documentElement.setAttribute('data-font-size', guardado);
    } else {
      document.documentElement.setAttribute('data-font-size', 'normal');
    }
  }

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    this.restaurarTamanoFuente();
    const ctx = buildDocumentoContext(this.route, this.authService);
    this.esRutaSuperAdmin = ctx.esRutaSuperAdmin;
    this.nav = documentNavigationLinks(ctx);
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargarMarcadorDesdeStorage(id);
    this.cargarPdfPreview(id);
    this.cargarPuntos(id);
    this.cargarDocumento(id);

    // Audio state subscription
    this.textoVoz.estado$.subscribe(estado => {
      this.estadoVoz = {
        activo: estado.activo,
        pausado: estado.pausado,
        fragmentoActual: estado.fragmentoActual,
        totalFragmentos: estado.totalFragmentos,
      };
      this.hayAudioActivo = estado.activo;
      if (estado.textoFragmento) {
        this.textoLecturaActual = estado.textoFragmento;
      }

      const fragmentos = this.textoVoz.fragmentosLectura;
      const indice = estado.fragmentoActual - 1;
      const hayPosicion = estado.fragmentoActual > 0 && estado.totalFragmentos > 0;

      if (hayPosicion && fragmentos.length) {
        this.progresoLecturaPct = Math.round((estado.fragmentoActual / estado.totalFragmentos) * 100);
        this.editorRico?.seguirLectura(fragmentos, indice);
        if (estado.activo && !estado.pausado) {
          this.guardarMarcadorLectura(indice, estado.totalFragmentos);
        }
      } else if (!this.textoVoz.puedeContinuarEnMemoria() && !estado.activo) {
        this.editorRico?.limpiarSeguimientoLectura();
        if (!this.marcadorLectura) {
          this.textoLecturaActual = '';
          this.progresoLecturaPct = 0;
        } else {
          this.progresoLecturaPct = this.marcadorLectura.progresoPct;
        }
      }

      this.cdr.markForCheck();
    });

    this.vozSoportada = this.textoVoz.soportado();
    this.cargarVoces();

    this.textoVoz.vocesDisponibles$.subscribe(voces => {
      this.vocesDisponibles = voces;
      if (!this.vozSeleccionadaId && voces.length) {
        this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? voces[0].id;
      }
      this.cdr.markForCheck();
    });

    this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? '';
    this.textoVoz.configurarEstiloDocumento(true);
    this.textoVoz.configurarVelocidad(this.velocidadLectura);
  }

  private async cargarVoces(): Promise<void> {
    if (!this.vozSoportada) {
      this.audioError = 'Tu navegador no soporta lectura por voz. Usa Chrome o Edge.';
      return;
    }
    this.cargandoVoces = true;
    await this.textoVoz.esperarVoces();
    this.cargandoVoces = false;
    this.vocesDisponibles = this.textoVoz.vocesDisponibles;
    if (!this.vozSeleccionadaId && this.vocesDisponibles.length) {
      this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? this.vocesDisponibles[0].id;
    }
    if (!this.vocesDisponibles.length) {
      this.audioError = 'No se detectaron voces. Recarga la página o revisa permisos del navegador.';
    }
    this.cdr.markForCheck();
  }

  probarVoz(): void {
    this.audioError = null;
    if (this.vozSeleccionadaId) {
      this.textoVoz.seleccionarVoz(this.vozSeleccionadaId);
    }
    this.textoVoz.probarVoz();
    setTimeout(() => {
      this.audioError = this.textoVoz.errorReciente;
      this.cdr.markForCheck();
    }, 500);
  }

  ngOnDestroy(): void {
    if (this.syncSeccionesTimer) clearTimeout(this.syncSeccionesTimer);
    this.detenerPollProcesamiento();
    this.revokePdfUrl();
    this.textoVoz.detener();
    document.documentElement.removeAttribute('data-font-size');
  }

  get procesandoDocumento(): boolean {
    return this.documento?.estadoProcesamiento === 'PROCESANDO';
  }

  get errorProcesamiento(): string | null {
    if (this.documento?.estadoProcesamiento !== 'ERROR') return null;
    return this.documento?.errorProcesamiento || 'Error al procesar el PDF';
  }

  private async iniciarLector(documentoId: number): Promise<void> {
    const cacheKey = DocumentoCacheService.textoKey(documentoId);
    let resp = this.cache.get<any>(cacheKey);

    if (!resp) {
      this.cargandoTexto = true;
      try {
        resp = await this.http.get<any>(
          `${environment.apiUrl}/documentos/${documentoId}/texto-completo`
        ).toPromise();
      } catch {
        this.cargandoTexto = false;
        this.textoCargado = false;
        return;
      }
    }

    if (!resp) {
      this.cargandoTexto = false;
      this.textoCargado = false;
      return;
    }

    resp = normalizarRespuestaTexto(resp, this.documento?.titulo);
    this.textoEditado = this.resolverContenidoEditor(resp);
    const tieneContenido = !!(this.textoEditado?.trim() || (resp.textoCompleto || '').trim());

    if (!tieneContenido) {
      this.cargandoTexto = false;
      this.textoCargado = false;
      this.cdr.markForCheck();
      return;
    }

    this.cache.set(cacheKey, resp);
    this.aplicarTextoCompleto(resp);
    this.sincronizarSeccionesDesdeEditor(this.textoEditado);
    this.textoCargado = true;
    this.avisoEstructuraLibro = respuestaTextoNecesitaEstructura(resp)
      || !htmlTieneEstructura(this.textoEditado);
    if (this.avisoEstructuraLibro && this.textoCompleto?.trim()) {
      setTimeout(() => this.estructurarLibroAutomaticoSiVacio(), 400);
    }
    this.cdr.markForCheck();
  }

  recargarTextoDocumento(): void {
    const id = this.documento?.id;
    if (!id) return;
    this.cache.invalidar(id);
    this.textoCargado = false;
    this.iniciarLector(id);
  }

  reprocesarDocumento(): void {
    const id = this.documento?.id;
    if (!id || this.reprocesandoDoc) return;
    this.reprocesandoDoc = true;
    this.http.post(`${environment.apiUrl}/documentos/${id}/reprocesar`, {}).subscribe({
      next: (doc: any) => {
        this.documento = doc;
        this.reprocesandoDoc = false;
        this.cache.invalidar(id);
        this.textoCargado = false;
        this.iniciarPollProcesamiento(id);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.reprocesandoDoc = false;
        this.errorMsgPuntos = err?.error?.mensaje || 'No se pudo reprocesar el documento';
        this.cdr.markForCheck();
      },
    });
  }

  private resolverContenidoEditor(resp: any): string {
    const limpiar = (html: string) => quitarIndiceDelHtml(html || '');
    const editor = limpiar(resp.textoEditor || resp.textoEstructurado || '');
    if (editor && htmlTieneEstructura(editor)) return editor;

    const enriquecido = normalizarRespuestaTexto(resp, this.documento?.titulo);
    return limpiar(enriquecido.textoEditor || enriquecido.textoEstructurado || '');
  }

  cambiarModoVista(modo: 'pdf' | 'texto'): void {
    this.modoVista = modo;
    if (modo === 'texto' && !this.textoEditado && this.textoCompleto) {
      this.textoEditado = this.textoCompleto;
    }
  }

  guardarTextoEditado(htmlContent?: string): void {
    if (!this.documento?.id) return;
    const textoAGuardar = htmlContent || this.editorRico?.getContent() || this.textoEditado;
    if (!textoAGuardar.trim()) return;

    this.guardandoTexto = true;
    this.textoGuardadoExitoso = false;

    this.http.put(`${environment.apiUrl}/documentos/${this.documento.id}/texto-extraido`, {
      texto: textoAGuardar
    }).subscribe({
      next: () => {
        this.guardandoTexto = false;
        this.textoGuardadoExitoso = true;
        this.textoEditado = textoAGuardar;
        this.textoCompleto = this.extraerTextoPlano(textoAGuardar);
        this.sincronizarSeccionesDesdeEditor(textoAGuardar);
        this.editorLastSaved = new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
        this.cache.invalidar(this.documento!.id);
        setTimeout(() => this.textoGuardadoExitoso = false, 3000);
      },
      error: () => {
        this.guardandoTexto = false;
      }
    });
  }

  private extraerTextoPlano(html: string): string {
    return textoPlanoDesdeHtml(html);
  }

  private aplicarTextoCompleto(resp: any): void {
    this.textoCompleto = resp.textoCompleto || '';
    this.textoEstructurado = resp.textoEstructurado || '';
    this.cargandoTexto = false;
    this.cdr.markForCheck();
  }

  /** Índice lateral y secciones de lectura salen del HTML del editor, no del PDF. */
  sincronizarSeccionesDesdeEditor(html?: string): void {
    const fuente = html ?? this.textoEditado;
    this.htmlEditorCache = fuente;
    const todas = extraerSeccionesDesdeEditor(fuente);
    this.seccionesEditorCache = todas;
    this.secciones = filtrarIndiceSidebar(todas).map((s) => ({
      ...s,
      duracionLabel: this.etiquetaDuracion(s.titulo, s.textoLectura),
    }));
    this.seccionesFiltradas = this.terminoBusqueda.trim()
      ? this.secciones.filter((s: any) => {
          const t = this.terminoBusqueda.toLowerCase().trim();
          return s.titulo.toLowerCase().includes(t) || s.nivel.toLowerCase().includes(t);
        })
      : [...this.secciones];
    this.indiceEntradas = this.secciones.length;
    this.cdr.markForCheck();
  }

  onEditorChange(html: string): void {
    this.textoEditado = html;
    if (this.syncSeccionesTimer) clearTimeout(this.syncSeccionesTimer);
    // Solo actualiza el índice cuando el usuario deja de escribir (no en cada tecla).
    this.syncSeccionesTimer = setTimeout(() => this.sincronizarSeccionesDesdeEditor(html), 1200);
  }

  onEditorBlur(html: string): void {
    if (this.syncSeccionesTimer) clearTimeout(this.syncSeccionesTimer);
    this.textoEditado = html;
    this.sincronizarSeccionesDesdeEditor(html);
  }

  private etiquetaDuracion(titulo: string, cuerpo?: string): string {
    const txt = construirTextoLecturaSeccion({
      nivel: 'H1',
      titulo,
      profundidad: 0,
      indiceInicio: 0,
      indiceFin: 0,
      textoLectura: cuerpo,
    });
    const chars = txt.length;
    const segs = Math.max(1, Math.round(chars / 400));
    return segs < 60 ? `${segs}s` : `${Math.floor(segs / 60)}m ${segs % 60}s`;
  }

  cambiarVelocidad(valor: number): void {
    this.velocidadLectura = Math.min(2, Math.max(0.5, Math.round(valor * 100) / 100));
    this.textoVoz.configurarVelocidad(this.velocidadLectura);
  }

  ajustarVelocidad(delta: number): void {
    this.cambiarVelocidad(this.velocidadLectura + delta);
  }

  establecerVelocidad(valor: number): void {
    this.cambiarVelocidad(valor);
  }

  get textoLecturaMini(): string {
    const t = (this.textoLecturaActual || '').trim();
    return t.slice(0, 3) || '…';
  }

  get puedeContinuarDesdeMarcador(): boolean {
    return !this.textoVoz.puedeContinuarEnMemoria()
      && !!this.marcadorLectura
      && this.marcadorLectura.documentoId === this.documento?.id
      && this.marcadorLectura.indiceFragmento >= 0;
  }

  get puedeContinuarLectura(): boolean {
    return this.textoVoz.puedeContinuarEnMemoria() || this.puedeContinuarDesdeMarcador;
  }

  get etiquetaMarcador(): string {
    if (!this.marcadorLectura) return '';
    if (this.marcadorLectura.modo === 'seccion' && this.marcadorLectura.seccionTitulo) {
      return `${this.tituloSinNumero(this.marcadorLectura.seccionTitulo)} · ${this.marcadorLectura.progresoPct}%`;
    }
    return `Documento · ${this.marcadorLectura.progresoPct}%`;
  }

  private opcionesLecturaVoz() {
    return {
      estilo: 'documento' as const,
      alCompletar: () => {
        this.limpiarMarcadorLectura();
        this.modoLecturaActual = null;
        this.cdr.markForCheck();
      },
    };
  }

  private claveMarcador(documentoId: number): string {
    return `${this.STORAGE_MARCADOR_PREFIX}${documentoId}`;
  }

  private cargarMarcadorDesdeStorage(documentoId: number): void {
    try {
      const raw = localStorage.getItem(this.claveMarcador(documentoId));
      if (!raw) {
        this.marcadorLectura = null;
        return;
      }
      const parsed = JSON.parse(raw) as MarcadorLecturaDoc;
      if (parsed?.documentoId === documentoId && parsed.indiceFragmento >= 0) {
        this.marcadorLectura = parsed;
        this.progresoLecturaPct = parsed.progresoPct;
        this.modoLecturaActual = parsed.modo;
      } else {
        this.marcadorLectura = null;
      }
    } catch {
      this.marcadorLectura = null;
    }
  }

  private guardarMarcadorLectura(indice: number, total: number): void {
    if (!this.documento?.id || total <= 0 || indice < 0) return;
    const marcador: MarcadorLecturaDoc = {
      documentoId: this.documento.id,
      modo: this.modoLecturaActual ?? 'todo',
      seccionTitulo: this.modoLecturaActual === 'seccion' ? this.seccionActiva?.titulo : undefined,
      indiceFragmento: indice,
      totalFragmentos: total,
      progresoPct: Math.round(((indice + 1) / total) * 100),
      actualizadoEn: Date.now(),
    };
    localStorage.setItem(this.claveMarcador(this.documento.id), JSON.stringify(marcador));
    this.marcadorLectura = marcador;
  }

  private limpiarMarcadorLectura(): void {
    if (this.documento?.id) {
      localStorage.removeItem(this.claveMarcador(this.documento.id));
    }
    this.marcadorLectura = null;
  }

  get puedeContinuarEnMemoria(): boolean {
    return this.textoVoz.puedeContinuarEnMemoria();
  }

  reanudarDesdeDondeQuedo(): void {
    if (this.textoVoz.puedeContinuarEnMemoria()) {
      this.textoVoz.continuarLectura();
      return;
    }
    this.continuarDesdeMarcador();
  }

  private continuarDesdeMarcador(): void {
    const m = this.marcadorLectura;
    if (!m || m.documentoId !== this.documento?.id) return;

    if (this.vozSeleccionadaId) {
      this.textoVoz.seleccionarVoz(this.vozSeleccionadaId);
    }

    if (m.modo === 'seccion' && m.seccionTitulo) {
      const sec = this.buscarSeccionEnEditor(m.seccionTitulo);
      if (sec) {
        this.seccionActiva = sec;
        this.reproducirSeccionDesde(sec, m.indiceFragmento);
        return;
      }
    }

    this.leerDocumentoCompletoDesde(m.indiceFragmento);
  }

  reiniciarLectura(): void {
    this.limpiarMarcadorLectura();
    this.textoVoz.detener();
    this.editorRico?.limpiarSeguimientoLectura();
    this.textoLecturaActual = '';
    this.progresoLecturaPct = 0;
    this.modoLecturaActual = null;
  }

  filtrarSecciones(): void {
    const t = this.terminoBusqueda.toLowerCase().trim();
    this.seccionesFiltradas = !t
      ? [...this.secciones]
      : this.secciones.filter((s: any) =>
          s.titulo.toLowerCase().includes(t) || s.nivel.toLowerCase().includes(t)
        );
  }

  tituloSinNumero(t: string): string {
    if (!t) return '';
    return t.replace(/^\d+(?:\.\d+){0,3}\s+/, '')
            .replace(/^(?:EST[ÁA]NDAR\s+DE|CAP[ÍI]TULO|SECCI[ÓO]N)\s+/i, '')
            .trim();
  }

  iconoSeccion(sec: any): string {
    const titulo = (sec.titulo || '').toLowerCase();
    const nivel = (sec.nivel || '').toLowerCase();
    if (nivel === 'int' || titulo.includes('introducción')) return 'menu_book';
    if (nivel === 'h1' || nivel === 'est') return 'article';
    if (nivel === 'cap' || nivel.startsWith('cap')) return 'auto_stories';
    // Estándares específicos
    if (titulo.includes('control de trabajo') || titulo.includes('permiso')) return 'fact_check';
    if (titulo.includes('caliente') || titulo.includes('fuego') || titulo.includes('ignición')) return 'whatshot';
    if (titulo.includes('conducción') || titulo.includes('conduccion') || titulo.includes('vehículo') || titulo.includes('vehiculo')) return 'minor_crash';
    if (titulo.includes('apertura') || titulo.includes('línea') || titulo.includes('linea') || titulo.includes('tubería')) return 'valve';
    if (titulo.includes('eléctrico') || titulo.includes('electrico') || titulo.includes('electricidad')) return 'electric_bolt';
    if (titulo.includes('confinado') || titulo.includes('espacio')) return 'emergency';
    if (titulo.includes('aislamiento') || titulo.includes('bloqueo') || titulo.includes('energía') || titulo.includes('energia')) return 'lock';
    if (titulo.includes('atmósfera') || titulo.includes('atmosfera') || titulo.includes('gas') || titulo.includes('ventilación')) return 'ac_unit';
    if (titulo.includes('altura') || titulo.includes('andamio')) return 'arrow_upward';
    if (titulo.includes('excavación') || titulo.includes('excavacion') || titulo.includes('zanja')) return 'terrain';
    if (titulo.includes('izar') || titulo.includes('grúa') || titulo.includes('grua') || titulo.includes('elevación')) return 'construction';
    if (titulo.includes('sustancia') || titulo.includes('químico') || titulo.includes('quimico') || titulo.includes('material peligroso')) return 'science';
    return 'description';
  }

  seleccionarYReproducir(sec: any): void {
    const html = this.obtenerHtmlEditor();
    this.sincronizarSeccionesDesdeEditor(html);
    const actual = this.buscarSeccionEnEditor(sec.titulo, html) ?? sec;
    this.seccionActiva = actual;
    this.textoResumen = `Reproduciendo: ${this.tituloSinNumero(actual.titulo)}`;
    this.playerExpandido = true;

    if (!this.hayAudioActivo || this.estadoVoz.pausado) {
      this.reproducirSeccion(actual);
    } else {
      this.textoVoz.detener();
      setTimeout(() => this.reproducirSeccion(actual), 80);
    }
  }

  /** HTML vivo del editor — nunca textoCompleto del PDF/API. */
  private obtenerHtmlEditor(): string {
    const live = this.editorRico?.flushContent?.()?.trim()
      || this.editorRico?.getContent()?.trim()
      || '';
    if (live) {
      this.textoEditado = live;
      return live;
    }
    return this.textoEditado?.trim() || '';
  }

  private buscarSeccionEnEditor(titulo: string, html?: string) {
    const fuente = html ?? this.obtenerHtmlEditor();
    if (fuente === this.htmlEditorCache && this.seccionesEditorCache.length) {
      return buscarSeccionEnLista(this.seccionesEditorCache, titulo);
    }
    return buscarSeccionEnHtmlEditor(fuente, titulo);
  }

  private reproducirSeccion(sec: any, indiceInicio = 0): void {
    const html = this.obtenerHtmlEditor();
    this.sincronizarSeccionesDesdeEditor(html);
    const enEditor = this.buscarSeccionEnEditor(sec.titulo, html);
    if (!enEditor) {
      this.audioError = 'No se encontró esta sección en el editor.';
      return;
    }
    const limpio = construirTextoLecturaSeccion(enEditor, { soloCuerpo: true });
    if (!limpio.trim()) {
      this.audioError = 'Esta sección no tiene texto en el editor.';
      return;
    }
    this.audioError = null;
    this.modoLecturaActual = 'seccion';
    this.seccionActiva = enEditor;
    if (this.vozSeleccionadaId) {
      this.textoVoz.seleccionarVoz(this.vozSeleccionadaId);
    }
    if (indiceInicio > 0) {
      this.textoVoz.leerDesde(limpio, this.documento?.id ?? 0, indiceInicio, this.opcionesLecturaVoz());
    } else {
      this.textoVoz.leer(limpio, this.documento?.id ?? 0, this.opcionesLecturaVoz());
    }
    setTimeout(() => {
      this.audioError = this.textoVoz.errorReciente;
      this.cdr.markForCheck();
    }, 400);
  }

  private reproducirSeccionDesde(sec: any, indice: number): void {
    this.textoVoz.detener();
    this.reproducirSeccion(sec, indice);
    this.playerExpandido = true;
  }

  leerDocumentoCompleto(): void {
    this.reiniciarLectura();
    this.leerDocumentoCompletoDesde(0);
  }

  private leerDocumentoCompletoDesde(indiceInicio: number): void {
    const html = this.obtenerHtmlEditor();
    this.sincronizarSeccionesDesdeEditor(html);
    let limpio = construirTextoLecturaDocumento(this.seccionesEditorCache);
    if (!limpio.trim()) {
      limpio = normalizarTextoLectura(this.extraerTextoPlano(html));
    }
    if (!limpio.trim()) {
      this.audioError = 'No hay texto en el editor para leer.';
      return;
    }
    if (indiceInicio === 0) {
      this.seccionActiva = null;
    }
    this.audioError = null;
    this.modoLecturaActual = 'todo';
    if (this.vozSeleccionadaId) {
      this.textoVoz.seleccionarVoz(this.vozSeleccionadaId);
    }
    if (indiceInicio > 0) {
      this.textoVoz.leerDesde(limpio, this.documento?.id ?? 0, indiceInicio, this.opcionesLecturaVoz());
    } else {
      this.textoVoz.leer(limpio, this.documento?.id ?? 0, this.opcionesLecturaVoz());
    }
    this.playerExpandido = true;
    setTimeout(() => {
      this.audioError = this.textoVoz.errorReciente;
      this.cdr.markForCheck();
    }, 400);
  }

  togglePlayPause(): void {
    this.obtenerHtmlEditor();
    if (this.hayAudioActivo) {
      this.estadoVoz.pausado ? this.textoVoz.reanudar() : this.textoVoz.pausar();
      return;
    }
    if (this.puedeContinuarLectura) {
      this.reanudarDesdeDondeQuedo();
      return;
    }
    if (this.seccionActiva) {
      this.reproducirSeccion(this.seccionActiva);
      return;
    }
    if (this.seccionesFiltradas.length > 0) {
      this.seleccionarYReproducir(this.seccionesFiltradas[0]);
    }
  }

  pararLectura(): void {
    const indice = this.estadoVoz.fragmentoActual - 1;
    if (indice >= 0 && this.estadoVoz.totalFragmentos > 0) {
      this.guardarMarcadorLectura(indice, this.estadoVoz.totalFragmentos);
    }
    this.textoVoz.parar();
  }

  detenerLectura(): void {
    this.pararLectura();
  }

  cambiarVoz(id: string): void {
    this.vozSeleccionadaId = id;
    this.textoVoz.seleccionarVoz(id);
    this.audioError = null;
  }

  etiquetaReproduccion(): string {
    if (this.audioError) return this.audioError;
    if (this.cargandoVoces) return 'Cargando voces…';
    if (!this.vocesDisponibles.length) return 'Sin voces disponibles';
    if (this.hayAudioActivo && this.estadoVoz.pausado) return 'Pausado · pulsa play para continuar';
    if (this.hayAudioActivo && this.seccionActiva) {
      return `Leyendo · ${this.tituloSinNumero(this.seccionActiva.titulo)} (${this.progresoLecturaPct}%)`;
    }
    if (this.hayAudioActivo) return `Leyendo documento · ${this.progresoLecturaPct}%`;
    if (this.textoVoz.puedeContinuarEnMemoria()) {
      return `Detenido · continúa en ${this.progresoLecturaPct}%`;
    }
    if (this.puedeContinuarDesdeMarcador) {
      return `Guardado · ${this.etiquetaMarcador}`;
    }
    return 'Sin reproducción';
  }

  /** Primera carga: estructura automática una vez si el editor viene plano del PDF. */
  private async estructurarLibroAutomaticoSiVacio(): Promise<void> {
    if (this.reestructurando || !this.avisoEstructuraLibro) return;
    const heads = (this.textoEditado?.match(/<h[12][\s>]/gi) || []).length;
    if (heads >= 3) {
      this.avisoEstructuraLibro = false;
      return;
    }
    await this.reestructurarTextoAutomatico();
  }

  async reestructurarTextoAutomatico(): Promise<void> {
    if (!this.documento?.id || this.reestructurando) return;

    this.reestructurando = true;
    this.estructuraProgreso = 5;
    this.estructuraMensaje = 'Preparando…';
    this.estructuraResultado = null;
    this.cache.invalidar(this.documento.id);
    this.cdr.markForCheck();

    const avanzar = async (pct: number, msg: string, pausaMs = 30) => {
      this.estructuraProgreso = pct;
      this.estructuraMensaje = msg;
      this.cdr.markForCheck();
      await new Promise((r) => setTimeout(r, pausaMs));
    };

    try {
      await avanzar(20, 'Organizando texto ya extraído…', 40);

      let textoApi = this.textoCompleto?.trim();
      if (!textoApi) {
        try {
          const resp = await this.http.get<any>(
            `${environment.apiUrl}/documentos/${this.documento.id}/texto-completo`
          ).toPromise();
          textoApi = resp?.textoCompleto?.trim() || '';
          if (resp?.textoCompleto) this.textoCompleto = resp.textoCompleto;
        } catch {
          textoApi = '';
        }
      }

      const htmlPrevio = this.obtenerHtmlEditor();
      const textoBase = resolverTextoBaseParaEstructura(textoApi, htmlPrevio);

      if (!textoBase?.trim()) {
        throw new Error('No hay texto. El PDF se extrae al subirlo; recarga la página o vuelve a abrir el documento.');
      }

      await avanzar(55, 'Detectando estándares y títulos…', 60);

      const tituloDoc = this.documento?.titulo || 'ESTÁNDARES QUE SALVAN VIDAS';
      const local = forzarEstructuraDesdeTexto(textoBase, tituloDoc);
      const html = quitarIndiceDelHtml(local.html);

      if (!html?.trim()) {
        throw new Error('No se pudo organizar el contenido.');
      }

      await avanzar(85, 'Actualizando editor e índice…', 50);

      this.textoEstructurado = html;
      this.textoEditado = html;
      this.textoCompleto = local.textoConSaltos;
      this.editorRico?.setContent(html);
      this.sincronizarSeccionesDesdeEditor(html);
      this.textoCargado = true;

      this.cache.set(DocumentoCacheService.textoKey(this.documento.id), {
        textoCompleto: local.textoConSaltos,
        indice: local.indice,
        secciones: local.seccionesNav,
        textoEstructurado: html,
        textoEditor: html,
      });

      await avanzar(100, 'Completado', 20);

      const n = this.secciones.length;
      this.avisoEstructuraLibro = false;
      this.estructuraResultado = n > 0
        ? `Libro estructurado: H1 + ${n} secciones (estándares en H2). Guarde para activar la IA.`
        : 'Texto organizado. Use Principal / T2 / T3 en la barra para ajustar títulos.';
    } catch (err: any) {
      this.estructuraProgreso = 0;
      this.estructuraMensaje = err?.message || 'Error al organizar';
      this.estructuraResultado = null;
    } finally {
      this.reestructurando = false;
      this.cdr.markForCheck();
      if (this.estructuraResultado) {
        setTimeout(() => {
          this.estructuraResultado = null;
          this.estructuraProgreso = 0;
          this.estructuraMensaje = '';
          this.cdr.markForCheck();
        }, 5000);
      }
    }
  }

  cambiarPestanaPrincipal(pestana: 'documentos' | 'normativa' | 'lectura'): void {
    this.pestanaPrincipal = pestana;
    if (pestana === 'normativa' && this.puntosClave.length === 0 && !this.cargandoPuntos && this.documento?.id) {
      this.cargarPuntos(this.documento.id);
    }
  }

  volver(): void {
    this.router.navigate(this.nav.documentos);
  }

  abrirIaChat(): void {
    this.pestanaActiva = 'ia';
  }

  cargarPdf(): void {
    if (this.documento?.id) {
      this.cargarPdfPreview(this.documento.id);
    }
  }

  private revokePdfUrl(): void {
    if (this.pdfBlobUrl) {
      URL.revokeObjectURL(this.pdfBlobUrl);
      this.pdfBlobUrl = null;
    }
    this.pdfSafeUrl = null;
  }

  cargarPdfPreview(documentoId: number): void {
    this.pdfError = null;
    this.cargandoPdf = true;
    this.revokePdfUrl();
    this.http
      .get(`${environment.apiUrl}/documentos/${documentoId}/archivo`, {
        responseType: 'blob',
      })
      .subscribe({
        next: (blob) => {
          this.cargandoPdf = false;
          if (!blob || blob.size === 0) {
            this.pdfError = 'El archivo PDF está vacío o no está disponible.';
            return;
          }
          this.pdfBlobUrl = URL.createObjectURL(blob);
          this.pdfSafeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfBlobUrl);
        },
        error: () => {
          this.cargandoPdf = false;
          this.pdfError =
            'No se pudo cargar el PDF. Compruebe permisos o que el archivo exista en el servidor.';
        },
      });
  }

  /** Texto de la sección activa desde el editor (no PDF). */
  obtenerTextoSeccion(): string {
    if (!this.seccionActiva) return '';
    const enEditor = this.buscarSeccionEnEditor(this.seccionActiva.titulo);
    if (!enEditor) return '';
    return construirTextoLecturaSeccion(enEditor);
  }

  get pdfListo(): boolean {
    return !!this.pdfBlobUrl;
  }

  abrirPdfNuevaPestana(): void {
    if (this.pdfBlobUrl) {
      window.open(this.pdfBlobUrl, '_blank', 'noopener,noreferrer');
    }
  }

  cargarDocumento(id: number): void {
    this.http.get(`${environment.apiUrl}/documentos/${id}`)
      .subscribe({
        next: (doc: any) => {
          this.documento = doc;
          this.loading = false;
          const estado = doc.estadoProcesamiento || 'COMPLETADO';
          if (estado === 'PROCESANDO') {
            this.iniciarPollProcesamiento(id);
            this.textoCargado = false;
            this.cargandoTexto = false;
          } else {
            this.detenerPollProcesamiento();
            if (estado !== 'ERROR') {
              this.iniciarLector(id);
            }
          }
          this.cargarMarcadorDesdeStorage(id);
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.router.navigate(this.nav.documentos);
        },
      });
  }

  private iniciarPollProcesamiento(id: number): void {
    this.detenerPollProcesamiento();
    this.pollProcesamientoTimer = setInterval(() => this.verificarEstadoProcesamiento(id), 3000);
  }

  private detenerPollProcesamiento(): void {
    if (this.pollProcesamientoTimer) {
      clearInterval(this.pollProcesamientoTimer);
      this.pollProcesamientoTimer = undefined;
    }
  }

  private verificarEstadoProcesamiento(id: number): void {
    this.http.get(`${environment.apiUrl}/documentos/${id}`).subscribe({
      next: (doc: any) => {
        const previo = this.documento?.estadoProcesamiento;
        this.documento = doc;
        if (doc.estadoProcesamiento === 'COMPLETADO' &&
            (previo === 'PROCESANDO' || !this.textoCargado)) {
          this.detenerPollProcesamiento();
          this.cache.invalidar(id);
          this.cargarPuntos(id);
          this.iniciarLector(id);
        } else if (doc.estadoProcesamiento === 'ERROR') {
          this.detenerPollProcesamiento();
        }
        this.cdr.markForCheck();
      },
    });
  }

  cargarPuntos(id: number): void {
    this.cargandoPuntos = true;
    this.errorMsgPuntos = null;
    this.http.get(`${environment.apiUrl}/puntos-clave/documento/${id}`).subscribe({
      next: (puntos: any) => {
        this.puntosClave = Array.isArray(puntos) ? puntos : [];
        this.cargandoPuntos = false;
      },
      error: (err) => {
        this.cargandoPuntos = false;
        this.errorMsgPuntos =
          err?.error?.mensaje ||
          'No se pudieron cargar los puntos clave. Revise la consola de red o vuelva a iniciar sesión.';
      },
    });
  }

  confianzaPorcentaje(punto: any): number | null {
    if (punto?.confianzaIa == null) return null;
    const c = Number(punto.confianzaIa);
    return Number.isFinite(c) ? Math.round(c * 100) : null;
  }

  get puntosFiltrados(): any[] {
    switch(this.filtro) {
      case 'ia': return this.puntosClave.filter(p => p.esIa);
      case 'manual': return this.puntosClave.filter(p => !p.esIa);
      case 'no-revisados': return this.puntosClave.filter(p => !p.revisado);
      default: return this.puntosClave;
    }
  }

  get cantidadPendientes(): number {
    return this.puntosClave.filter(p => p.esIa && !p.revisado).length;
  }

  /** Agrupa por estándar/tema (ej. ESTÁNDAR DE TRABAJO EN ALTURA) con CC1, CC7… dentro */
  get puntosAgrupadosPorTema(): { tema: string; puntos: any[] }[] {
    const map = new Map<string, any[]>();
    for (const p of this.puntosFiltrados) {
      const tema = (p.tema && String(p.tema).trim()) || 'General / otros';
      if (!map.has(tema)) {
        map.set(tema, []);
      }
      map.get(tema)!.push(p);
    }
    return Array.from(map.entries()).map(([tema, puntos]) => ({ tema, puntos }));
  }

  tituloPunto(punto: any): string {
    if (punto.codigo && punto.titulo) {
      return `${punto.codigo} — ${punto.titulo}`;
    }
    if (punto.codigo) {
      return punto.codigo;
    }
    if (punto.titulo) {
      return punto.titulo;
    }
    const linea = (punto.contenido || '').split('\n')[0]?.trim();
    return linea && linea.length > 120 ? linea.substring(0, 117) + '…' : linea || 'Punto clave';
  }

  esContenidoLargo(punto: any): boolean {
    return (punto.contenido || '').length > 700;
  }

  estaExpandido(punto: any): boolean {
    return this.puntosExpandidos.has(punto.id);
  }

  toggleExpandir(punto: any): void {
    if (this.puntosExpandidos.has(punto.id)) {
      this.puntosExpandidos.delete(punto.id);
    } else {
      this.puntosExpandidos.add(punto.id);
    }
  }

  etiquetaTipo(punto: any): string {
    if (punto.tipo === 'CONTROL_CRITICO') return 'Control crítico';
    if (punto.tipo === 'ESTANDAR') return 'Estándar';
    if (punto.tipo === 'MANUAL') return 'Manual';
    return punto.esIa ? 'IA' : 'Punto';
  }

  agregarPuntoManual(): void {
    if (!this.nuevoPunto.trim()) return;

    this.http.post(`${environment.apiUrl}/puntos-clave`, {
      documentoId: this.documento.id,
      contenido: this.nuevoPunto.trim(),
    }).subscribe({
      next: (punto: any) => {
        this.puntosClave.push(punto);
        this.nuevoPunto = '';
      },
    });
  }

  iniciarEdicion(punto: any): void {
    this.editandoPuntoId = punto.id;
    this.editandoPuntoTexto = punto.contenido;
  }

  guardarEdicion(punto: any): void {
    if (!this.editandoPuntoTexto.trim()) return;

    this.http.put(`${environment.apiUrl}/puntos-clave/${punto.id}`, {
      contenido: this.editandoPuntoTexto,
      documentoId: this.documento.id,
    }).subscribe({
      next: (actualizado: any) => {
        const idx = this.puntosClave.findIndex(p => p.id === punto.id);
        if (idx !== -1) this.puntosClave[idx] = actualizado;
        this.cancelarEdicion();
      },
    });
  }

  cancelarEdicion(): void {
    this.editandoPuntoId = null;
    this.editandoPuntoTexto = '';
  }

  marcarRevisado(punto: any): void {
    this.http.patch(`${environment.apiUrl}/puntos-clave/${punto.id}/revisado`, {})
      .subscribe({
        next: (actualizado: any) => {
          const idx = this.puntosClave.findIndex(p => p.id === punto.id);
          if (idx !== -1) this.puntosClave[idx] = actualizado;
        },
      });
  }

  marcarTodosRevisados(): void {
    this.http.post(`${environment.apiUrl}/puntos-clave/documento/${this.documento.id}/revisar-todos`, {})
      .subscribe({
        next: () => {
          this.puntosClave.forEach(p => {
            if (p.esIa) p.revisado = true;
          });
        },
      });
  }

  regenerarPuntosIa(): void {
    if (!confirm('¿Regenerar puntos clave con IA? Los puntos IA actuales serán reemplazados.')) return;

    this.regenerandoIa = true;
    this.errorMsgPuntos = null;
    this.http.post(`${environment.apiUrl}/documentos/${this.documento.id}/regenerar-puntos-ia`, {}).subscribe({
      next: (nuevosPuntos: any) => {
        const lista = Array.isArray(nuevosPuntos) ? nuevosPuntos : [];
        this.puntosClave = this.puntosClave.filter((p) => !p.esIa);
        this.puntosClave.push(...lista);
        this.documento.puntosGeneradosIa = true;
        this.regenerandoIa = false;
        if (lista.length === 0) {
          this.errorMsgPuntos =
            'La IA no devolvió puntos. Compruebe la API de IA, la extracción de texto del PDF o vuelva a intentar.';
        }
      },
      error: (err) => {
        this.regenerandoIa = false;
        this.errorMsgPuntos =
          err?.error?.mensaje || 'No se pudo regenerar puntos. Revise el backend y la configuración de IA.';
      },
    });
  }

  eliminarPunto(id: number): void {
    if (!confirm('¿Eliminar este punto clave?')) return;
    this.http.delete(`${environment.apiUrl}/puntos-clave/${id}`)
      .subscribe({
        next: () => {
          this.puntosClave = this.puntosClave.filter(p => p.id !== id);
        },
      });
  }

  getNivelConfianza(confianza: number): string {
    if (confianza >= 0.9) return '🟢 Alta';
    if (confianza >= 0.7) return '🟡 Media';
    return '🟠 Baja';
  }

  getConfianzaClase(confianza: number): string {
    if (confianza >= 0.9) return 'alta';
    if (confianza >= 0.7) return 'media';
    return 'baja';
  }

  formatearTamano(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  getIconoIdioma(idioma: string): string {
    switch(idioma) {
      case 'es': return '🇪🇸';
      case 'en': return '🇬🇧';
      default: return '🌐';
    }
  }

  logout(): void {
    this.authService.logout();
  }
}
