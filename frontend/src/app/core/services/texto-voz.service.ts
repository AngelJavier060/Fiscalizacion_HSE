import { Injectable, NgZone } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { limpiarTextoParaVoz, normalizarTextoLectura } from '../helpers/texto-estructura.helper';

export interface EstadoLecturaVoz {
  activo: boolean;
  mensajeId: number | null;
  pausado: boolean;
  /** Fragmento actual (1-based) mientras lee textos largos. */
  fragmentoActual: number;
  totalFragmentos: number;
  /** Texto del fragmento que se está leyendo ahora (seguimiento visual). */
  textoFragmento: string;
}

export interface VozDisponible {
  id: string;
  nombre: string;
  idioma: string;
  calidad: 'natural' | 'online' | 'neural' | 'estandar';
  genero: 'femenina' | 'masculina' | 'neutra';
  etiqueta: string;
}

export interface OpcionesLecturaVoz {
  /** Narración fluida (documento) o chat rápido. */
  estilo?: 'documento' | 'chat';
  /** Se invoca al terminar todo el texto (no al parar). */
  alCompletar?: () => void;
}

interface FragmentoVoz {
  texto: string;
  pausaMs: number;
}

@Injectable({ providedIn: 'root' })
export class TextoVozService {
  private readonly estadoSubject = new BehaviorSubject<EstadoLecturaVoz>({
    activo: false,
    mensajeId: null,
    pausado: false,
    fragmentoActual: 0,
    totalFragmentos: 0,
    textoFragmento: '',
  });

  private readonly vocesSubject = new BehaviorSubject<VozDisponible[]>([]);

  readonly estado$ = this.estadoSubject.asObservable();
  readonly vocesDisponibles$ = this.vocesSubject.asObservable();

  private fragmentos: FragmentoVoz[] = [];
  private indiceFragmento = 0;
  private mensajeActualId: number | null = null;
  private vozActiva: SpeechSynthesisVoice | null = null;
  private keepAliveTimer?: ReturnType<typeof setInterval>;
  private vozGuardadaId: string | null = null;
  private velocidadLectura = 0.96;
  private tonoLectura = 1;
  private estiloLectura: 'documento' | 'chat' = 'documento';
  private ultimoError: string | null = null;
  private alCompletarCallback?: () => void;

  private readonly STORAGE_VOZ = 'ia-chat-voz-id';

  constructor(private zone: NgZone) {
    this.vozGuardadaId = localStorage.getItem(this.STORAGE_VOZ);
    if (this.soportado()) {
      this.refrescarVoces();
      window.speechSynthesis.onvoiceschanged = () => this.refrescarVoces();
      // Algunos navegadores cargan voces tarde; reintentar.
      setTimeout(() => this.refrescarVoces(), 400);
      setTimeout(() => this.refrescarVoces(), 1500);
    }
  }

  soportado(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window;
  }

  get estado(): EstadoLecturaVoz {
    return this.estadoSubject.value;
  }

  get vocesDisponibles(): VozDisponible[] {
    return this.vocesSubject.value;
  }

  get vozSeleccionadaId(): string | null {
    return this.vozActiva?.voiceURI ?? this.vozGuardadaId;
  }

  get errorReciente(): string | null {
    return this.ultimoError;
  }

  /** Fragmentos de la lectura en curso (para resaltar en el editor). */
  get fragmentosLectura(): string[] {
    return this.fragmentos.map((f) => f.texto);
  }

  /** Hay texto preparado y posición guardada en memoria (parada, no reinicio). */
  puedeContinuarEnMemoria(): boolean {
    return this.fragmentos.length > 0 && !this.estado.activo;
  }

  get posicionEnMemoria(): { indice: number; total: number } | null {
    if (!this.fragmentos.length) return null;
    return { indice: this.indiceFragmento, total: this.fragmentos.length };
  }

  /** Espera a que el navegador cargue las voces (Chrome las entrega tarde). */
  async esperarVoces(timeoutMs = 4000): Promise<SpeechSynthesisVoice[]> {
    if (!this.soportado()) return [];
    let voces = window.speechSynthesis.getVoices();
    if (voces.length) {
      this.refrescarVoces();
      return voces;
    }
    return new Promise((resolve) => {
      const finish = () => {
        voces = window.speechSynthesis.getVoices();
        this.refrescarVoces();
        resolve(voces);
      };
      const timer = setTimeout(finish, timeoutMs);
      const prev = window.speechSynthesis.onvoiceschanged;
      window.speechSynthesis.onvoiceschanged = () => {
        clearTimeout(timer);
        window.speechSynthesis.onvoiceschanged = prev;
        finish();
      };
    });
  }

  probarVoz(): void {
    this.leer(
      'Prueba de lectura. Uno, dos, tres. El texto fluye de forma continua, como un audiolibro.',
      -1,
      { estilo: 'documento' }
    );
  }

  configurarEstiloDocumento(activo = true): void {
    this.estiloLectura = activo ? 'documento' : 'chat';
    this.tonoLectura = 1;
  }

  configurarVelocidad(rate: number): void {
    this.velocidadLectura = Math.min(2, Math.max(0.5, rate));
  }

  seleccionarVoz(voiceId: string): void {
    if (!voiceId?.trim()) return;
    const voces = window.speechSynthesis.getVoices();
    let v = voces.find((x) => x.voiceURI === voiceId);
    if (!v) v = voces.find((x) => x.name === voiceId);
    if (v) {
      this.vozActiva = v;
      this.vozGuardadaId = voiceId;
      localStorage.setItem(this.STORAGE_VOZ, voiceId);
      this.ultimoError = null;
    }
  }

  private asegurarVozActiva(): void {
    if (this.vozActiva) return;
    const voces = window.speechSynthesis.getVoices();
    if (this.vozGuardadaId) {
      const guardada = voces.find((v) => v.voiceURI === this.vozGuardadaId);
      if (guardada) {
        this.vozActiva = guardada;
        return;
      }
    }
    const catalogo = this.vocesSubject.value;
    if (catalogo.length) {
      const preferida =
        catalogo.find((v) => v.idioma.toLowerCase().startsWith('es') && v.calidad === 'natural') ??
        catalogo.find((v) => v.idioma.toLowerCase().startsWith('es') && v.calidad === 'estandar') ??
        catalogo.find((v) => v.idioma.toLowerCase().startsWith('es')) ??
        catalogo[0];
      const match = voces.find((v) => v.voiceURI === preferida.id);
      if (match) {
        this.vozActiva = match;
        return;
      }
    }
    const es = voces.find((v) => v.lang?.toLowerCase().startsWith('es'));
    this.vozActiva = es ?? voces[0] ?? null;
  }

  leer(texto: string, mensajeId: number, opciones?: OpcionesLecturaVoz): void {
    this.iniciarLectura(texto, mensajeId, opciones, 0);
  }

  /** Reanuda la lectura desde un fragmento concreto (p. ej. marcador guardado). */
  leerDesde(
    texto: string,
    mensajeId: number,
    indiceInicio: number,
    opciones?: OpcionesLecturaVoz
  ): void {
    this.iniciarLectura(texto, mensajeId, opciones, indiceInicio);
  }

  private iniciarLectura(
    texto: string,
    mensajeId: number,
    opciones: OpcionesLecturaVoz | undefined,
    indiceInicio: number
  ): void {
    if (!this.soportado()) {
      this.ultimoError = 'Tu navegador no soporta lectura por voz.';
      return;
    }
    if (!texto?.trim()) {
      this.ultimoError = 'No hay texto para leer.';
      return;
    }

    const estilo = opciones?.estilo ?? this.estiloLectura;
    this.alCompletarCallback = opciones?.alCompletar;
    this.ultimoError = null;
    this.refrescarVoces();
    this.asegurarVozActiva();
    if (this.vozGuardadaId) {
      this.seleccionarVoz(this.vozGuardadaId);
    }

    this.detenerKeepAlive();
    window.speechSynthesis.cancel();

    const fragmentos = this.prepararFragmentos(texto, estilo);
    if (!fragmentos.length) {
      this.ultimoError = 'El texto quedó vacío tras limpiarlo para voz.';
      return;
    }

    const idx = Math.max(0, Math.min(indiceInicio, fragmentos.length - 1));

    this.fragmentos = fragmentos;
    this.mensajeActualId = mensajeId;
    this.indiceFragmento = idx;
    this.publicarEstado(
      true,
      mensajeId,
      false,
      idx + 1,
      fragmentos.length,
      fragmentos[idx]?.texto ?? ''
    );
    this.iniciarKeepAlive();

    setTimeout(() => this.hablarFragmento(idx), 120);
  }

  /** Para la voz pero conserva posición y fragmentos para continuar. */
  parar(): void {
    if (!this.soportado()) return;
    this.detenerKeepAlive();
    window.speechSynthesis.cancel();
    if (this.fragmentos.length) {
      this.publicarEstado(
        false,
        this.mensajeActualId,
        false,
        this.indiceFragmento + 1,
        this.fragmentos.length,
        this.fragmentos[this.indiceFragmento]?.texto ?? ''
      );
    } else {
      this.publicarEstado(false, null, false, 0, 0, '');
    }
  }

  /** Sigue desde el fragmento actual (tras parar o pausar largo). */
  continuarLectura(): void {
    if (!this.soportado() || !this.fragmentos.length) return;
    this.ultimoError = null;
    this.refrescarVoces();
    this.asegurarVozActiva();
    this.detenerKeepAlive();
    window.speechSynthesis.cancel();
    this.iniciarKeepAlive();
    this.publicarEstado(
      true,
      this.mensajeActualId,
      false,
      this.indiceFragmento + 1,
      this.fragmentos.length,
      this.fragmentos[this.indiceFragmento]?.texto ?? ''
    );
    setTimeout(() => this.hablarFragmento(this.indiceFragmento), 120);
  }

  detener(): void {
    if (!this.soportado()) {
      return;
    }
    this.detenerKeepAlive();
    window.speechSynthesis.cancel();
    this.fragmentos = [];
    this.indiceFragmento = 0;
    this.mensajeActualId = null;
    this.alCompletarCallback = undefined;
    this.publicarEstado(false, null, false, 0, 0);
  }

  pausar(): void {
    if (!this.soportado() || !this.estado.activo) {
      return;
    }
    window.speechSynthesis.pause();
    this.publicarEstado(
      true,
      this.mensajeActualId,
      true,
      this.indiceFragmento + 1,
      this.fragmentos.length,
      this.fragmentos[this.indiceFragmento]?.texto ?? ''
    );
  }

  reanudar(): void {
    if (!this.soportado() || !this.estado.activo) {
      return;
    }
    window.speechSynthesis.resume();
    this.publicarEstado(
      true,
      this.mensajeActualId,
      false,
      this.indiceFragmento + 1,
      this.fragmentos.length,
      this.fragmentos[this.indiceFragmento]?.texto ?? ''
    );
  }

  alternarPausa(): void {
    if (this.estado.pausado) {
      this.reanudar();
    } else {
      this.pausar();
    }
  }

  estaLeyendoMensaje(mensajeId: number): boolean {
    return this.estado.activo && this.estado.mensajeId === mensajeId;
  }

  /** Convierte markdown a texto hablable conservando números y fluidez. */
  textoParaVoz(raw: string, estilo: 'documento' | 'chat' = this.estiloLectura): string {
    if (!raw?.trim()) {
      return '';
    }

    // Conservar saltos de sección (pausa silenciosa, sin palabra «pausa»)
    const conSecciones = raw.replace(/\n\n+/g, '\n\n');
    let t = estilo === 'documento' ? normalizarTextoLectura(conSecciones) : limpiarTextoParaVoz(conSecciones);

    t = t
      .replace(/```[\s\S]*?```/g, (bloque) =>
        bloque.replace(/```\w*\n?/g, ' ').replace(/```/g, ' ').trim()
      )
      .replace(/`([^`]+)`/g, '$1')
      .replace(/!\[[^\]]*]\([^)]+\)/g, ' ')
      .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/\*\*([^*]+)\*\*/g, '$1')
      .replace(/\*([^*]+)\*/g, '$1')
      .replace(/__([^_]+)__/g, '$1')
      .replace(/_([^_]+)_/g, '$1')
      .replace(/^>\s+/gm, '')
      .replace(/^[-*•]\s+/gm, '')
      .replace(/---+/g, '. ')
      .replace(/\|/g, ', ')
      .replace(/[#*_~]/g, ' ')
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, ' y ')
      .replace(/&lt;/g, ' menor que ')
      .replace(/&gt;/g, ' mayor que ')
      .replace(/\bCC(\d+)\b/gi, ' control crítico $1 ')
      .replace(/\bEPP\b/gi, ' equipo de protección personal ')
      .replace(/\bHSE\b/gi, ' seguridad, salud y medio ambiente ')
      .replace(/\bPDF\b/gi, ' documento P D F ')
      .replace(/\.{3,}/g, '. ')
      .replace(/[ \t]+/g, ' ')
      .replace(/\n[ \t]+/g, '\n')
      .trim();

    return t;
  }

  private refrescarVoces(): void {
    if (!this.soportado()) {
      return;
    }

    const todas = window.speechSynthesis.getVoices();
    if (!todas.length) {
      return;
    }

    const mapeadas = todas.map((v) => this.mapearVoz(v));
    mapeadas.sort((a, b) => {
      const aEs = a.idioma.toLowerCase().startsWith('es') ? 0 : 1;
      const bEs = b.idioma.toLowerCase().startsWith('es') ? 0 : 1;
      if (aEs !== bEs) return aEs - bEs;
      return this.pesoCalidad(b) - this.pesoCalidad(a);
    });

    this.vocesSubject.next(mapeadas);

    if (this.vozGuardadaId) {
      const guardada = todas.find((v) => v.voiceURI === this.vozGuardadaId);
      if (guardada) {
        this.vozActiva = guardada;
        return;
      }
    }

    if (!this.vozActiva && mapeadas.length) {
      const preferida =
        mapeadas.find((v) => v.idioma.toLowerCase().startsWith('es') && v.calidad === 'natural') ??
        mapeadas.find((v) => v.idioma.toLowerCase().startsWith('es') && v.calidad === 'estandar') ??
        mapeadas.find((v) => v.idioma.toLowerCase().startsWith('es')) ??
        mapeadas[0];
      const nativa = todas.find((v) => v.voiceURI === preferida.id);
      if (nativa) {
        this.vozActiva = nativa;
        this.vozGuardadaId = nativa.voiceURI;
      }
    }
  }

  private mapearVoz(v: SpeechSynthesisVoice): VozDisponible {
    const nombre = v.name;
    const lang = v.lang || 'es';
    const lower = nombre.toLowerCase();

    let calidad: VozDisponible['calidad'] = 'estandar';
    if (/natural|neural/i.test(nombre)) {
      calidad = 'natural';
    } else if (/online/i.test(nombre)) {
      calidad = 'online';
    } else if (/premium|enhanced|wavenet|studio/i.test(nombre)) {
      calidad = 'neural';
    }

    let genero: VozDisponible['genero'] = 'neutra';
    if (/sabina|helena|laura|elena|dalia|paulina|lucia|soledad|irina|karla|paloma|mia|female|mujer/i.test(lower)) {
      genero = 'femenina';
    } else if (/pablo|jorge|raul|alvaro|diego|male|hombre|carlos|andres/i.test(lower)) {
      genero = 'masculina';
    }

    const calidadLabel =
      calidad === 'natural'
        ? 'Natural'
        : calidad === 'online'
          ? 'Online'
          : calidad === 'neural'
            ? 'Neural'
            : 'Estándar';

    const generoLabel =
      genero === 'femenina' ? 'Femenina' : genero === 'masculina' ? 'Masculina' : '';

    const region = this.etiquetaRegion(lang);
    const etiqueta = [calidadLabel, generoLabel, region].filter(Boolean).join(' · ');

    return {
      id: v.voiceURI,
      nombre: this.nombreLimpio(nombre),
      idioma: lang,
      calidad,
      genero,
      etiqueta: `${this.nombreLimpio(nombre)} (${etiqueta})`,
    };
  }

  private nombreLimpio(nombre: string): string {
    return nombre
      .replace(/Microsoft\s+/i, '')
      .replace(/Google\s+/i, '')
      .replace(/\s*Online\s*\(Natural\)/i, ' Natural')
      .replace(/\s*\(.*?\)\s*$/g, '')
      .trim();
  }

  private etiquetaRegion(lang: string): string {
    const map: Record<string, string> = {
      'es-CL': 'Chile',
      'es-MX': 'México',
      'es-ES': 'España',
      'es-AR': 'Argentina',
      'es-CO': 'Colombia',
      'es-PE': 'Perú',
      'es-US': 'EE.UU.',
    };
    return map[lang] ?? lang.replace('es-', '').toUpperCase();
  }

  private pesoCalidad(v: VozDisponible): number {
    const cal =
      v.calidad === 'natural'
        ? 400
        : v.calidad === 'neural'
          ? 360
          : v.calidad === 'online'
            ? 320
            : v.calidad === 'estandar'
              ? 280
              : 100;
    const gen = v.genero === 'femenina' ? 10 : v.genero === 'masculina' ? 8 : 0;
    const cl = v.idioma === 'es-CL' ? 15 : v.idioma.startsWith('es') ? 12 : 0;
    return cal + gen + cl;
  }

  private prepararFragmentos(texto: string, estilo: 'documento' | 'chat'): FragmentoVoz[] {
    const limpio = this.textoParaVoz(texto, estilo);
    if (!limpio) return [];

    const bloques = limpio.split(/\n\n+/).map((b) => b.trim()).filter(Boolean);
    const fragmentos: FragmentoVoz[] = [];
    const maxGrupo = estilo === 'documento' ? 420 : 280;

    for (let bi = 0; bi < bloques.length; bi++) {
      const oraciones = this.dividirOraciones(bloques[bi], estilo);
      const grupos = estilo === 'documento'
        ? this.agruparOracionesFluidas(oraciones, maxGrupo)
        : oraciones;

      for (let i = 0; i < grupos.length; i++) {
        const trozo = grupos[i].trim();
        if (trozo.length < 2) continue;
        const ultimoDelBloque = i === grupos.length - 1;
        const pausaMs = ultimoDelBloque && bi < bloques.length - 1
          ? 420
          : this.pausaTrasOracion(trozo, estilo);
        fragmentos.push({ texto: trozo, pausaMs });
      }
    }

    return fragmentos.length ? fragmentos : [{ texto: limpio, pausaMs: 200 }];
  }

  /** Une oraciones cortas en bloques más largos (lectura fluida tipo audiolibro). */
  private agruparOracionesFluidas(oraciones: string[], maxLen: number): string[] {
    const grupos: string[] = [];
    let acum = '';

    for (const oracion of oraciones) {
      const candidato = acum ? `${acum} ${oracion}` : oracion;
      if (candidato.length <= maxLen) {
        acum = candidato;
      } else {
        if (acum) grupos.push(acum);
        acum = oracion;
      }
    }
    if (acum) grupos.push(acum);
    return grupos;
  }

  private dividirOraciones(texto: string, estilo: 'documento' | 'chat'): string[] {
    const maxLen = estilo === 'documento' ? 420 : 280;
    const partes: string[] = [];
    const re = /([^.!?]+[.!?]+)/g;
    let m: RegExpExecArray | null;
    let lastIndex = 0;

    while ((m = re.exec(texto)) !== null) {
      const trozo = m[1].trim();
      if (trozo.length > maxLen) {
        partes.push(...this.partirTextoLargo(trozo, maxLen));
      } else if (trozo) {
        partes.push(trozo);
      }
      lastIndex = re.lastIndex;
    }

    const resto = texto.slice(lastIndex).trim();
    if (resto) {
      if (resto.length > maxLen) partes.push(...this.partirTextoLargo(resto, maxLen));
      else partes.push(resto);
    }

    return partes.filter((p) => p.length > 1);
  }

  private partirTextoLargo(texto: string, maxLen: number): string[] {
    const partes: string[] = [];
    let restante = texto;
    while (restante.length > maxLen) {
      let corte = restante.lastIndexOf(', ', maxLen);
      if (corte < maxLen * 0.4) corte = restante.lastIndexOf(' ', maxLen);
      if (corte <= 0) corte = maxLen;
      partes.push(restante.slice(0, corte).trim());
      restante = restante.slice(corte).trim();
    }
    if (restante) partes.push(restante);
    return partes;
  }

  private pausaTrasOracion(oracion: string, estilo: 'documento' | 'chat'): number {
    if (estilo === 'chat') return 80;
    const fin = oracion.trim().slice(-1);
    if (fin === '.' || fin === '!' || fin === '?') return 180;
    if (fin === ',' || fin === ';' || fin === ':') return 120;
    return 150;
  }

  private hablarFragmento(idx: number): void {
    if (idx >= this.fragmentos.length) {
      this.finalizarLectura();
      return;
    }

    this.indiceFragmento = idx;
    const frag = this.fragmentos[idx];
    this.publicarEstado(
      true,
      this.mensajeActualId,
      false,
      idx + 1,
      this.fragmentos.length,
      frag.texto
    );

    const u = new SpeechSynthesisUtterance(frag.texto);
    const voces = window.speechSynthesis.getVoices();
    const voz =
      this.vozActiva ??
      voces.find((v) => v.voiceURI === this.vozGuardadaId) ??
      voces.find((v) => v.lang?.toLowerCase().startsWith('es')) ??
      voces[0] ??
      null;

    u.lang = voz?.lang ?? 'es-CL';
    u.rate = this.velocidadLectura;
    u.pitch = this.tonoLectura;
    u.volume = 1;
    if (voz) {
      u.voice = voz;
      this.vozActiva = voz;
    }

    u.onstart = () =>
      this.zone.run(() => {
        this.ultimoError = null;
        this.publicarEstado(
          true,
          this.mensajeActualId,
          false,
          idx + 1,
          this.fragmentos.length,
          frag.texto
        );
      });

    u.onend = () =>
      this.zone.run(() => {
        setTimeout(() => this.hablarFragmento(idx + 1), frag.pausaMs);
      });

    u.onerror = (ev) =>
      this.zone.run(() => {
        const err = (ev as SpeechSynthesisErrorEvent).error;
        if (err !== 'interrupted' && err !== 'canceled') {
          this.ultimoError = `Error de voz: ${err || 'desconocido'}`;
        }
        setTimeout(() => this.hablarFragmento(idx + 1), frag.pausaMs);
      });

    window.speechSynthesis.speak(u);
  }

  /** Evita que Chrome corte la lectura a los ~15 s. */
  private iniciarKeepAlive(): void {
    this.detenerKeepAlive();
    this.keepAliveTimer = setInterval(() => {
      if (window.speechSynthesis.speaking && !window.speechSynthesis.paused) {
        window.speechSynthesis.pause();
        window.speechSynthesis.resume();
      }
    }, 8000);
  }

  private detenerKeepAlive(): void {
    if (this.keepAliveTimer != null) {
      clearInterval(this.keepAliveTimer);
      this.keepAliveTimer = undefined;
    }
  }

  private finalizarLectura(): void {
    this.detenerKeepAlive();
    const callback = this.alCompletarCallback;
    this.fragmentos = [];
    this.indiceFragmento = 0;
    this.mensajeActualId = null;
    this.alCompletarCallback = undefined;
    this.publicarEstado(false, null, false, 0, 0, '');
    callback?.();
  }

  private publicarEstado(
    activo: boolean,
    mensajeId: number | null,
    pausado: boolean,
    fragmentoActual: number,
    totalFragmentos: number,
    textoFragmento = ''
  ): void {
    this.estadoSubject.next({
      activo,
      mensajeId,
      pausado,
      fragmentoActual,
      totalFragmentos,
      textoFragmento,
    });
  }
}
