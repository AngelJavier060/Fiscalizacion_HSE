import { Injectable, signal, DestroyRef, inject } from '@angular/core';

export interface EstadoTts {
  activo: boolean;
  pausado: boolean;
  tituloActual: string;
  indiceTitulo: number | null; // índice en la lista de títulos
}

@Injectable({ providedIn: 'root' })
export class TtsSimpleService {
  private destroyRef = inject(DestroyRef);

  readonly estado = signal<EstadoTts>({
    activo: false,
    pausado: false,
    tituloActual: '',
    indiceTitulo: null,
  });

  private utterance: SpeechSynthesisUtterance | null = null;

  constructor() {
    // Limpiar al destruir el servicio
    this.destroyRef.onDestroy(() => {
      this.stop();
    });
  }

  /** Verifica si el navegador soporta Web Speech API */
  soportado(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window;
  }

  /**
   * Lee un título en voz alta con la configuración exacta del brief:
   * - lang: es-ES
   * - rate: 0.9
   * - pitch: 1
   * - volume: 1
   */
  speak(texto: string, indiceTitulo?: number): void {
    if (!this.soportado() || !texto?.trim()) {
      return;
    }

    // 1. Detener cualquier lectura anterior
    this.stop();

    // 2. Crear nuevo utterance
    const utterance = new SpeechSynthesisUtterance(texto.trim());

    // 3. Configuración exacta del brief
    utterance.lang = 'es-ES';    // Español de España (también funciona con es-MX si hay voces)
    utterance.rate = 0.9;        // Velocidad natural, no robótica
    utterance.pitch = 1;         // Tono neutral, agradable
    utterance.volume = 1;        // Volumen máximo

    // 4. Intentar usar una voz en español si está disponible
    const voces = window.speechSynthesis.getVoices();
    const vozEspanol = voces.find(
      (v) => v.lang.startsWith('es') && v.lang.includes('ES')
    ) || voces.find((v) => v.lang.startsWith('es'));
    if (vozEspanol) {
      utterance.voice = vozEspanol;
    }

    // 5. Eventos
    utterance.onstart = () => {
      this.estado.set({
        activo: true,
        pausado: false,
        tituloActual: texto.trim(),
        indiceTitulo: indiceTitulo ?? null,
      });
    };

    utterance.onend = () => {
      this.estado.set({
        activo: false,
        pausado: false,
        tituloActual: '',
        indiceTitulo: null,
      });
      this.utterance = null;
    };

    utterance.onerror = (event) => {
      console.warn('🔊 Error en Web Speech API:', event.error);
      this.estado.set({
        activo: false,
        pausado: false,
        tituloActual: '',
        indiceTitulo: null,
      });
      this.utterance = null;
    };

    this.utterance = utterance;

    // 6. Leer
    window.speechSynthesis.speak(utterance);
  }

  /** Pausar la lectura actual */
  pause(): void {
    if (!this.soportado() || !this.estado().activo) return;

    window.speechSynthesis.pause();
    this.estado.update((e) => ({ ...e, pausado: true }));
  }

  /** Reanudar la lectura pausada */
  resume(): void {
    if (!this.soportado() || !this.estado().pausado) return;

    window.speechSynthesis.resume();
    this.estado.update((e) => ({ ...e, pausado: false }));
  }

  /** Alternar entre pausa y reanudar */
  togglePause(): void {
    if (this.estado().pausado) {
      this.resume();
    } else {
      this.pause();
    }
  }

  /** Detener completamente la lectura */
  stop(): void {
    if (!this.soportado()) return;

    window.speechSynthesis.cancel();
    this.utterance = null;
    this.estado.set({
      activo: false,
      pausado: false,
      tituloActual: '',
      indiceTitulo: null,
    });
  }
}
