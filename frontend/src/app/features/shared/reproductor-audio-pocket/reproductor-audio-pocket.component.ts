import { Component, Input, Output, EventEmitter, inject, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TextoVozService, VozDisponible } from '../../../core/services/texto-voz.service';

export interface SeccionAudio {
  titulo: string;
  indiceInicio: number;
  indiceFin: number;
  nivel: string;
  profundidad: number;
}

@Component({
  selector: 'app-reproductor-audio-pocket',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="pocket-player" [class.expanded]="expandido" [class.active]="hayAudioActivo">
      <!-- Mini bar when collapsed -->
      <div class="pocket-mini" (click)="toggleExpandido()" *ngIf="!expandido">
        <div class="mini-left">
          <div class="mini-wave" [class.playing]="hayAudioActivo && !estadoVoz.pausado">
            <span></span><span></span><span></span><span></span>
          </div>
          <div class="mini-info">
            <span class="mini-label">{{ hayAudioActivo ? 'Reproduciendo' : 'Audio' }}</span>
            <span class="mini-title">{{ seccionActiva ? tituloSinNumero(seccionActiva.titulo) : (hayAudioActivo ? '...' : 'Selecciona una sección') }}</span>
          </div>
        </div>
        <div class="mini-right">
          <button class="mini-play-btn" (click)="$event.stopPropagation(); togglePlayPause()" [class.active]="hayAudioActivo && !estadoVoz.pausado">
            <span class="mat-icon">{{ hayAudioActivo && !estadoVoz.pausado ? 'pause' : 'play_arrow' }}</span>
          </button>
        </div>
      </div>

      <!-- Expanded player -->
      <div class="pocket-expanded" *ngIf="expandido">
        <!-- Header drag area -->
        <div class="pocket-header" (click)="toggleExpandido()">
          <div class="pocket-handle"></div>
          <div class="pocket-header-left">
            <span class="mat-icon head-icon">graphic_eq</span>
            <span class="head-title">Reproductor de Audio</span>
          </div>
          <button class="head-close" (click)="toggleExpandido()">
            <span class="mat-icon">expand_more</span>
          </button>
        </div>

        <!-- Now Playing Art -->
        <div class="pocket-art">
          <div class="art-circle" [class.spinning]="hayAudioActivo && !estadoVoz.pausado">
            <div class="art-ring"></div>
            <div class="art-center">
              <span class="mat-icon art-icon">graphic_eq</span>
            </div>
            <div class="art-ring-outer"></div>
          </div>
        </div>

        <!-- Track Info -->
        <div class="pocket-track-info">
          <h3 class="track-title">{{ seccionActiva ? tituloSinNumero(seccionActiva.titulo) : 'Ninguna sección seleccionada' }}</h3>
          <p class="track-meta">{{ seccionActiva ? seccionActiva.nivel + ' · ' + duracionEstimada(seccionActiva) : 'Selecciona una sección del panel izquierdo' }}</p>
        </div>

        <!-- Progress Bar -->
        <div class="pocket-progress">
          <div class="progress-bar">
            <div class="progress-fill" [style.width.%]="progreso"></div>
            <div class="progress-thumb" [style.left.%]="progreso"></div>
          </div>
          <div class="progress-time">
            <span class="time-current">{{ tiempoActual }}</span>
            <span class="time-total">{{ tiempoTotal }}</span>
          </div>
        </div>

        <!-- Main Controls -->
        <div class="pocket-controls">
          <button class="ctrl-btn" (click)="anterior()" [disabled]="!seccionActiva" title="Sección anterior">
            <span class="mat-icon">skip_previous</span>
          </button>
          <button class="ctrl-btn ctrl-replay" (click)="retroceder()" [disabled]="!hayAudioActivo" title="Retroceder 10s">
            <span class="mat-icon">replay_10</span>
          </button>
          <button class="ctrl-btn ctrl-main" (click)="togglePlayPause()" [class.active]="hayAudioActivo && !estadoVoz.pausado">
            <span class="mat-icon main-icon">{{ hayAudioActivo && !estadoVoz.pausado ? 'pause' : 'play_arrow' }}</span>
          </button>
          <button class="ctrl-btn ctrl-forward" (click)="adelantar()" [disabled]="!hayAudioActivo" title="Adelantar 10s">
            <span class="mat-icon">forward_10</span>
          </button>
          <button class="ctrl-btn" (click)="siguiente()" [disabled]="!seccionActiva" title="Sección siguiente">
            <span class="mat-icon">skip_next</span>
          </button>
        </div>

        <!-- Secondary Controls -->
        <div class="pocket-secondary">
          <div class="voice-section">
            <span class="sec-label">Voz</span>
            <select class="voice-select" [(ngModel)]="vozSeleccionadaId" (ngModelChange)="cambiarVoz($event)">
              @for (v of vocesDisponibles; track v.id) {
                <option [value]="v.id">{{ v.nombre }}</option>
              }
            </select>
          </div>
          <div class="speed-section">
            <span class="sec-label">Velocidad</span>
            <div class="speed-btns">
              @for (s of velocidades; track s) {
                <button class="speed-btn" [class.active]="velocidadActual === s" (click)="cambiarVelocidad(s)">
                  {{ s }}x
                </button>
              }
            </div>
          </div>
          <button class="detener-btn" (click)="detener()" [disabled]="!hayAudioActivo">
            <span class="mat-icon">stop</span>
            Detener
          </button>
        </div>

        <!-- Chapter List -->
        @if (secciones.length > 0) {
          <div class="pocket-chapters">
            <div class="chapters-header">
              <span class="mat-icon">list</span>
              <span>Secciones · {{ secciones.length }}</span>
            </div>
            <div class="chapters-list">
              @for (sec of secciones; track sec.indiceInicio) {
                <div class="chapter-item"
                     [class.active]="seccionActiva === sec"
                     [class.playing]="seccionActiva === sec && hayAudioActivo && !estadoVoz.pausado"
                     (click)="seleccionarSeccion.emit(sec)">
                  <div class="chapter-num">
                    @if (seccionActiva === sec && hayAudioActivo && !estadoVoz.pausado) {
                      <div class="ch-eq"><span></span><span></span><span></span></div>
                    } @else {
                      <span class="mat-icon ch-play-icon">play_arrow</span>
                    }
                  </div>
                  <div class="chapter-info">
                    <span class="chapter-title">{{ tituloSinNumero(sec.titulo) }}</span>
                    <span class="chapter-meta">{{ sec.nivel }} · {{ duracionEstimada(sec) }}</span>
                  </div>
                </div>
              }
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }

    .mat-icon {
      font-family: 'Material Symbols Outlined', sans-serif;
      font-size: 20px;
      line-height: 1;
      font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 20;
    }

    .pocket-player {
      background: #ffffff;
      border-radius: 14px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.06);
      border: 1px solid #e2e5ec;
      overflow: hidden;
      transition: all 0.3s ease;
    }

    .pocket-player.active {
      border-color: rgba(0, 61, 155, 0.15);
      box-shadow: 0 4px 20px rgba(0, 61, 155, 0.1);
    }

    /* ===== MINI PLAYER ===== */
    .pocket-mini {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.6rem 0.75rem;
      cursor: pointer;
      transition: background 0.15s;
    }

    .pocket-mini:hover {
      background: #f8f9fc;
    }

    .mini-left {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      flex: 1;
      min-width: 0;
    }

    .mini-wave {
      display: flex;
      align-items: center;
      gap: 2px;
      height: 20px;
    }

    .mini-wave span {
      width: 3px;
      background: #c3c6d6;
      border-radius: 2px;
      transition: all 0.3s;
    }

    .mini-wave span:nth-child(1) { height: 8px; }
    .mini-wave span:nth-child(2) { height: 14px; }
    .mini-wave span:nth-child(3) { height: 10px; }
    .mini-wave span:nth-child(4) { height: 16px; }

    .mini-wave.playing span {
      background: #059669;
      animation: wave 0.8s ease-in-out infinite alternate;
    }

    .mini-wave.playing span:nth-child(1) { animation-delay: 0s; }
    .mini-wave.playing span:nth-child(2) { animation-delay: 0.2s; }
    .mini-wave.playing span:nth-child(3) { animation-delay: 0.4s; }
    .mini-wave.playing span:nth-child(4) { animation-delay: 0.6s; }

    @keyframes wave {
      0% { height: 4px; }
      100% { height: 18px; }
    }

    .mini-info {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .mini-label {
      font-size: 0.6rem;
      font-weight: 600;
      color: #059669;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .mini-title {
      font-size: 0.78rem;
      color: #1a1d23;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .mini-play-btn {
      width: 34px;
      height: 34px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #003d9b;
      border: none;
      border-radius: 50%;
      color: white;
      cursor: pointer;
      transition: all 0.2s;
      box-shadow: 0 2px 8px rgba(0, 61, 155, 0.25);
    }

    .mini-play-btn:hover {
      transform: scale(1.05);
    }

    .mini-play-btn.active {
      background: #059669;
      box-shadow: 0 2px 8px rgba(5, 150, 105, 0.25);
    }

    .mini-play-btn .mat-icon {
      font-size: 18px;
    }

    /* ===== EXPANDED PLAYER ===== */
    .pocket-expanded {
      display: flex;
      flex-direction: column;
    }

    .pocket-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.5rem 0.75rem;
      cursor: pointer;
      border-bottom: 1px solid #f0f1f3;
    }

    .pocket-handle {
      width: 32px;
      height: 4px;
      background: #e2e5ec;
      border-radius: 4px;
      position: absolute;
      left: 50%;
      top: 6px;
      transform: translateX(-50%);
    }

    .pocket-header-left {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .head-icon {
      color: #003d9b;
      font-size: 18px;
    }

    .head-title {
      font-size: 0.78rem;
      font-weight: 700;
      color: #1a1d23;
    }

    .head-close {
      background: none;
      border: none;
      color: #737685;
      cursor: pointer;
      padding: 2px;
    }

    /* ===== ART ===== */
    .pocket-art {
      display: flex;
      justify-content: center;
      padding: 1.25rem 0 0.75rem;
    }

    .art-circle {
      position: relative;
      width: 80px;
      height: 80px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .art-circle.spinning .art-ring-outer {
      animation: spin-ring 3s linear infinite;
    }

    .art-ring {
      position: absolute;
      width: 100%;
      height: 100%;
      border-radius: 50%;
      border: 2px solid #e2e5ec;
    }

    .art-ring-outer {
      position: absolute;
      width: 110%;
      height: 110%;
      border-radius: 50%;
      border: 2px dashed transparent;
      border-top-color: #003d9b;
      border-right-color: #059669;
    }

    @keyframes spin-ring {
      to { transform: rotate(360deg); }
    }

    .art-center {
      width: 52px;
      height: 52px;
      background: linear-gradient(135deg, #003d9b, #0052cc);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      z-index: 1;
    }

    .art-icon {
      font-size: 26px !important;
    }

    /* ===== TRACK INFO ===== */
    .pocket-track-info {
      text-align: center;
      padding: 0 1rem 0.5rem;
    }

    .track-title {
      font-size: 0.9rem;
      font-weight: 700;
      color: #1a1d23;
      margin: 0 0 0.15rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .track-meta {
      font-size: 0.68rem;
      color: #737685;
      margin: 0;
    }

    /* ===== PROGRESS ===== */
    .pocket-progress {
      padding: 0 1rem 0.5rem;
    }

    .progress-bar {
      position: relative;
      height: 4px;
      background: #edeef0;
      border-radius: 4px;
      cursor: pointer;
      margin-bottom: 0.3rem;
    }

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #003d9b, #0052cc);
      border-radius: 4px;
      transition: width 0.3s ease;
    }

    .progress-thumb {
      position: absolute;
      top: 50%;
      width: 12px;
      height: 12px;
      background: #003d9b;
      border-radius: 50%;
      transform: translate(-50%, -50%);
      opacity: 0;
      transition: opacity 0.2s;
    }

    .progress-bar:hover .progress-thumb {
      opacity: 1;
    }

    .progress-time {
      display: flex;
      justify-content: space-between;
      font-size: 0.62rem;
      color: #737685;
    }

    /* ===== MAIN CONTROLS ===== */
    .pocket-controls {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.6rem;
      padding: 0.25rem 0 0.5rem;
    }

    .ctrl-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      background: transparent;
      border: none;
      border-radius: 50%;
      color: #434654;
      cursor: pointer;
      transition: all 0.15s;
    }

    .ctrl-btn:hover:not(:disabled) {
      background: #f3f4f6;
      color: #1a1d23;
    }

    .ctrl-btn:disabled {
      opacity: 0.3;
      cursor: not-allowed;
    }

    .ctrl-btn .mat-icon {
      font-size: 22px;
    }

    .ctrl-main {
      width: 52px;
      height: 52px;
      background: #003d9b;
      color: white;
      box-shadow: 0 3px 10px rgba(0, 61, 155, 0.3);
    }

    .ctrl-main:hover:not(:disabled) {
      background: #0052cc;
      transform: scale(1.05);
    }

    .ctrl-main.active {
      background: #059669;
      box-shadow: 0 3px 10px rgba(5, 150, 105, 0.3);
    }

    .ctrl-main .main-icon {
      font-size: 26px;
    }

    .ctrl-replay .mat-icon, .ctrl-forward .mat-icon {
      font-size: 18px;
    }

    /* ===== SECONDARY ===== */
    .pocket-secondary {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      padding: 0.5rem 1rem;
      border-top: 1px solid #f0f1f3;
      border-bottom: 1px solid #f0f1f3;
      flex-wrap: wrap;
    }

    .voice-section, .speed-section {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .sec-label {
      font-size: 0.65rem;
      color: #737685;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .voice-select {
      padding: 0.2rem 0.35rem;
      background: #f8f9fc;
      border: 1px solid #e2e5ec;
      border-radius: 6px;
      color: #1a1d23;
      font-size: 0.68rem;
      max-width: 110px;
      cursor: pointer;
      font-family: 'Hanken Grotesk', sans-serif;
    }

    .speed-btns {
      display: flex;
      gap: 0.15rem;
    }

    .speed-btn {
      padding: 0.15rem 0.4rem;
      background: transparent;
      border: 1px solid #e2e5ec;
      border-radius: 4px;
      color: #434654;
      font-size: 0.62rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.12s;
      font-family: 'JetBrains Mono', monospace;
    }

    .speed-btn:hover {
      background: #edeef0;
    }

    .speed-btn.active {
      background: #003d9b;
      border-color: #003d9b;
      color: white;
    }

    .detener-btn {
      display: flex;
      align-items: center;
      gap: 0.2rem;
      padding: 0.25rem 0.6rem;
      background: transparent;
      border: 1px solid #e2e5ec;
      border-radius: 6px;
      color: #dc2626;
      font-size: 0.68rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.12s;
      font-family: 'Hanken Grotesk', sans-serif;
    }

    .detener-btn:hover:not(:disabled) {
      background: #fef2f2;
      border-color: #fecaca;
    }

    .detener-btn:disabled {
      opacity: 0.3;
      cursor: not-allowed;
    }

    .detener-btn .mat-icon {
      font-size: 14px;
    }

    /* ===== CHAPTERS ===== */
    .pocket-chapters {
      max-height: 200px;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    .chapters-header {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.5rem 0.75rem;
      font-size: 0.7rem;
      font-weight: 600;
      color: #737685;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      border-bottom: 1px solid #f0f1f3;
      background: #fafbfc;
    }

    .chapters-header .mat-icon {
      font-size: 14px;
    }

    .chapters-list {
      overflow-y: auto;
      flex: 1;
    }

    .chapter-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 0.75rem;
      cursor: pointer;
      transition: background 0.12s;
      border-left: 3px solid transparent;
    }

    .chapter-item:hover {
      background: #f8f9fc;
    }

    .chapter-item.active {
      background: rgba(0, 61, 155, 0.04);
      border-left-color: #003d9b;
    }

    .chapter-item.playing {
      background: rgba(5, 150, 105, 0.06);
      border-left-color: #059669;
    }

    .chapter-num {
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .ch-play-icon {
      font-size: 14px !important;
      color: #737685;
    }

    .chapter-item:hover .ch-play-icon {
      color: #003d9b;
    }

    .ch-eq {
      display: flex;
      align-items: center;
      gap: 2px;
      height: 14px;
    }

    .ch-eq span {
      width: 2px;
      background: #059669;
      border-radius: 2px;
      animation: ch-wave 0.6s ease-in-out infinite alternate;
    }

    .ch-eq span:nth-child(1) { height: 6px; animation-delay: 0s; }
    .ch-eq span:nth-child(2) { height: 10px; animation-delay: 0.15s; }
    .ch-eq span:nth-child(3) { height: 8px; animation-delay: 0.3s; }

    @keyframes ch-wave {
      0% { height: 3px; }
      100% { height: 12px; }
    }

    .chapter-info {
      flex: 1;
      min-width: 0;
    }

    .chapter-title {
      font-size: 0.75rem;
      color: #1a1d23;
      font-weight: 500;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .chapter-item.active .chapter-title {
      color: #003d9b;
      font-weight: 600;
    }

    .chapter-meta {
      font-size: 0.6rem;
      color: #a0a3b1;
    }
  `],
})
export class ReproductorAudioPocketComponent implements OnDestroy {
  @Input() secciones: SeccionAudio[] = [];
  @Input() textoCompleto = '';
  @Input() hayAudioActivo = false;

  @Output() seleccionarSeccion = new EventEmitter<SeccionAudio>();
  @Output() playPause = new EventEmitter<void>();
  @Output() detenerAudio = new EventEmitter<void>();

  expandido = false;
  estadoVoz = { activo: false, pausado: false, fragmentoActual: 0, totalFragmentos: 0 };
  seccionActiva: SeccionAudio | null = null;
  vocesDisponibles: VozDisponible[] = [];
  vozSeleccionadaId = '';
  velocidadActual = 1;
  progreso = 0;
  tiempoActual = '0:00';
  tiempoTotal = '0:00';

  readonly velocidades = [0.5, 0.75, 1, 1.25, 1.5, 2];

  private textoVoz = inject(TextoVozService);
  private cdr = inject(ChangeDetectorRef);
  private progressInterval: any = null;

  constructor() {
    this.textoVoz.estado$.subscribe(estado => {
      this.estadoVoz = {
        activo: estado.activo,
        pausado: estado.pausado,
        fragmentoActual: estado.fragmentoActual,
        totalFragmentos: estado.totalFragmentos,
      };
      this.actualizarProgreso();
      this.cdr.markForCheck();
    });

    this.textoVoz.vocesDisponibles$.subscribe(voces => {
      this.vocesDisponibles = voces;
      if (!this.vozSeleccionadaId && voces.length) {
        this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? voces[0].id;
      }
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.limpiarInterval();
  }

  setEstado(seccion: SeccionAudio | null, activo: boolean, pausado: boolean): void {
    this.seccionActiva = seccion;
    this.estadoVoz = { ...this.estadoVoz, activo, pausado };
    this.actualizarProgreso();
    this.cdr.markForCheck();
  }

  setSeccionActiva(sec: SeccionAudio | null): void {
    this.seccionActiva = sec;
    if (sec) {
      this.tiempoTotal = this.duracionEstimada(sec);
    }
    this.cdr.markForCheck();
  }

  toggleExpandido(): void {
    this.expandido = !this.expandido;
  }

  togglePlayPause(): void {
    this.playPause.emit();
  }

  detener(): void {
    this.limpiarInterval();
    this.progreso = 0;
    this.tiempoActual = '0:00';
    this.detenerAudio.emit();
  }

  cambiarVoz(id: string): void {
    this.vozSeleccionadaId = id;
    this.textoVoz.seleccionarVoz(id);
  }

  cambiarVelocidad(vel: number): void {
    this.velocidadActual = vel;
    // La velocidad se maneja en el componente padre
  }

  anterior(): void {
    if (!this.seccionActiva || this.secciones.length === 0) return;
    const idx = this.secciones.indexOf(this.seccionActiva);
    if (idx > 0) {
      this.seleccionarSeccion.emit(this.secciones[idx - 1]);
    }
  }

  siguiente(): void {
    if (!this.seccionActiva || this.secciones.length === 0) return;
    const idx = this.secciones.indexOf(this.seccionActiva);
    if (idx < this.secciones.length - 1) {
      this.seleccionarSeccion.emit(this.secciones[idx + 1]);
    }
  }

  retroceder(): void {
    // Simular retroceso de 10s
    this.progreso = Math.max(0, this.progreso - 8);
    this.actualizarTiempo();
  }

  adelantar(): void {
    // Simular adelanto de 10s
    this.progreso = Math.min(100, this.progreso + 8);
    this.actualizarTiempo();
  }

  private actualizarProgreso(): void {
    if (this.estadoVoz.activo && this.estadoVoz.totalFragmentos > 0) {
      this.progreso = (this.estadoVoz.fragmentoActual / this.estadoVoz.totalFragmentos) * 100;
      this.actualizarTiempo();

      // Simular el tiempo real
      this.limpiarInterval();
      if (!this.estadoVoz.pausado) {
        this.progressInterval = setInterval(() => {
          if (this.estadoVoz.activo && !this.estadoVoz.pausado) {
            // Avance suave
          }
        }, 1000);
      }
    } else if (!this.estadoVoz.activo) {
      this.limpiarInterval();
      this.progreso = 0;
      this.tiempoActual = '0:00';
    }
  }

  private actualizarTiempo(): void {
    if (this.seccionActiva) {
      const totalSecs = Math.round((this.seccionActiva.indiceFin - this.seccionActiva.indiceInicio) / 400);
      const currentSecs = Math.round((totalSecs * this.progreso) / 100);
      this.tiempoActual = this.formatearTiempo(currentSecs);
      this.tiempoTotal = this.formatearTiempo(totalSecs);
    }
  }

  private formatearTiempo(segundos: number): string {
    const m = Math.floor(segundos / 60);
    const s = segundos % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  private limpiarInterval(): void {
    if (this.progressInterval) {
      clearInterval(this.progressInterval);
      this.progressInterval = null;
    }
  }

  tituloSinNumero(t: string): string {
    if (!t) return '';
    return t.replace(/^\d+(?:\.\d+){0,3}\s+/, '')
            .replace(/^(?:EST[ÁA]NDAR\s+DE|CAP[ÍI]TULO|SECCI[ÓO]N)\s+/i, '').trim();
  }

  duracionEstimada(s: SeccionAudio): string {
    const segs = Math.round((s.indiceFin - s.indiceInicio) / 400);
    const m = Math.floor(segs / 60);
    const seg = segs % 60;
    return segs < 60 ? `${segs}s` : `${m}:${seg.toString().padStart(2, '0')}`;
  }
}
