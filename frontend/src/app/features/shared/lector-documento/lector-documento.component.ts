import { Component, Input, OnInit, OnDestroy, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { environment } from '../../../../environments/environment';
import { TextoVozService, VozDisponible } from '../../../core/services/texto-voz.service';
import { DocumentoCacheService } from '../../../core/services/documento-cache.service';

interface Seccion {
  nivel: string;
  titulo: string;
  indiceInicio: number;
  indiceFin: number;
  profundidad: number;
}

interface TextoCompletoResponse {
  id: number;
  titulo: string;
  textoCompleto: string;
  secciones: Seccion[];
  idioma: string;
}

@Component({
  selector: 'app-lector-documento',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="lector-shell">

      <!-- Player Bar -->
      <div class="player-bar">
        <div class="player-left">
          <button class="play-btn" 
                  [class.active]="hayAudioActivo && !estadoVoz.pausado"
                  (click)="togglePlayPause()"
                  [disabled]="!seccionActiva && seccionesFiltradas.length === 0"
                  [title]="hayAudioActivo && !estadoVoz.pausado ? 'Pausar' : 'Reproducir'">
            <span class="material-symbols-outlined">
              {{ hayAudioActivo && !estadoVoz.pausado ? 'pause' : 'play_arrow' }}
            </span>
          </button>
          <button class="icon-btn" (click)="detenerLectura()" [disabled]="!hayAudioActivo" title="Detener">
            <span class="material-symbols-outlined">stop</span>
          </button>
          @if (seccionActiva) {
            <div class="now-playing">
              <span class="np-indicator">{{ hayAudioActivo ? (estadoVoz.pausado ? '⏸' : '🔊') : '📌' }}</span>
              <span class="np-text">{{ tituloSinNumero(seccionActiva.titulo) }}</span>
            </div>
          } @else if (!cargandoTexto) {
            <span class="np-hint">Selecciona una sección para escuchar</span>
          }
        </div>
        <div class="player-right">
          @if (hayAudioActivo) {
            <span class="frag-counter">{{ estadoVoz.fragmentoActual }}/{{ estadoVoz.totalFragmentos }}</span>
          }
          <select class="voice-select" [ngModel]="vozSeleccionadaId" (ngModelChange)="cambiarVoz($event)">
            @for (v of vocesDisponibles; track v.id) {
              <option [value]="v.id">{{ v.nombre }}</option>
            }
          </select>
          <div class="speed-ctrl">
            <span class="speed-label">Vel</span>
            <input type="range" min="0.5" max="1.5" step="0.1" [(ngModel)]="velocidadLectura" class="speed-range">
            <span class="speed-val">{{ velocidadLectura }}x</span>
          </div>
        </div>
      </div>

      <!-- Main Content: Section Tree + PDF Viewer -->
      <div class="content-split">
        
        <!-- LEFT: Section Index -->
        <aside class="index-panel">
          <div class="index-header">
            <div class="index-title-row">
              <h2 class="index-title">
                <span class="material-symbols-outlined">list_alt</span>
                Secciones
              </h2>
              <span class="index-count">{{ seccionesFiltradas.length }}</span>
            </div>
            <div class="search-box">
              <span class="material-symbols-outlined search-icon">search</span>
              <input type="text" [(ngModel)]="terminoBusqueda"
                     (input)="filtrarSecciones()"
                     placeholder="Buscar en secciones..." class="search-input">
              @if (terminoBusqueda) {
                <button class="search-clear" (click)="terminoBusqueda='';filtrarSecciones()">
                  <span class="material-symbols-outlined">close</span>
                </button>
              }
            </div>
          </div>

          <div class="index-scroll">
            @if (cargandoTexto) {
              <div class="state-msg"><span class="spinner-sm"></span> Cargando...</div>
            } @else if (seccionesFiltradas.length === 0) {
              <div class="state-msg dim">{{ terminoBusqueda ? 'Sin resultados' : 'No hay secciones disponibles' }}</div>
            } @else {
              <div class="section-list">
                @for (sec of seccionesFiltradas; track sec.indiceInicio) {
                  <div class="section-item"
                       [class.active]="seccionActiva === sec"
                       [class.playing]="seccionActiva === sec && hayAudioActivo && !estadoVoz.pausado"
                       [class.depth-1]="sec.profundidad >= 1"
                       [class.depth-2]="sec.profundidad >= 2"
                       (click)="seleccionarYReproducir(sec)">
                    <div class="section-play-col">
                      <div class="section-play-btn" [class.on]="seccionActiva === sec && hayAudioActivo && !estadoVoz.pausado">
                        @if (seccionActiva === sec && hayAudioActivo && !estadoVoz.pausado) {
                          <div class="eq-anim">
                            <span></span><span></span><span></span>
                          </div>
                        } @else {
                          <span class="material-symbols-outlined play-icon">play_arrow</span>
                        }
                      </div>
                    </div>
                    <div class="section-info">
                      <span class="section-title">{{ tituloSinNumero(sec.titulo) }}</span>
                      @if (sec.nivel !== 'EST' && sec.nivel !== 'CAP' && sec.nivel !== 'SEC' && sec.nivel !== 'mayuscula') {
                        <span class="section-meta">
                          <span class="meta-pill">{{ sec.nivel }}</span>
                          <span class="meta-duration">{{ duracionEstimada(sec) }}</span>
                        </span>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </div>

          <div class="index-footer">
            <div class="file-info">
              <span class="material-symbols-outlined">description</span>
              <span class="file-text">Documento · {{ secciones.length }} secciones</span>
            </div>
          </div>
        </aside>

        <!-- RIGHT: PDF Viewer -->
        <main class="pdf-viewer">
          @if (cargandoPdf) {
            <div class="pdf-loading">
              <span class="spinner-sm"></span>
              <span>Cargando PDF...</span>
            </div>
          } @else if (pdfSafeUrl) {
            <iframe class="pdf-frame" [src]="pdfSafeUrl" title="PDF Viewer"></iframe>
          } @else {
            <div class="pdf-placeholder" (click)="cargarPdf()">
              <span class="material-symbols-outlined pdf-icon">picture_as_pdf</span>
              <p>Haz clic para cargar el PDF</p>
            </div>
          }
        </main>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }

    .spinner-sm {
      width: 16px; height: 16px; border: 2px solid #e1e2e4; border-top-color: #003d9b;
      border-radius: 50%; animation: spin .6s linear infinite; display: inline-block;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .material-symbols-outlined {
      font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 20;
      font-size: 18px;
    }

    .lector-shell {
      display: flex; flex-direction: column; height: 100%;
      background: #f8f9fb; font-family: 'Hanken Grotesk', sans-serif;
    }

    /* ===== PLAYER BAR ===== */
    .player-bar {
      display: flex; align-items: center; justify-content: space-between;
      padding: 0.5rem 1rem; background: #ffffff;
      border-bottom: 1px solid #e1e2e4; gap: 0.75rem; min-height: 48px;
    }
    .player-left { display: flex; align-items: center; gap: 0.5rem; flex: 1; min-width: 0; }
    .player-right { display: flex; align-items: center; gap: 0.75rem; flex-shrink: 0; }

    .play-btn {
      display: flex; align-items: center; justify-content: center;
      width: 36px; height: 36px; border: none; border-radius: 50%;
      background: #003d9b; color: white; cursor: pointer; transition: all .2s;
      box-shadow: 0 2px 8px rgba(0,61,155,.25);
    }
    .play-btn:hover:not(:disabled) { background: #0040a2; transform: scale(1.05); }
    .play-btn:disabled { opacity: .35; cursor: not-allowed; }
    .play-btn.active { background: #059669; box-shadow: 0 2px 8px rgba(5,150,105,.25); }
    .play-btn .material-symbols-outlined { font-size: 20px; }

    .icon-btn {
      display: flex; align-items: center; justify-content: center;
      width: 32px; height: 32px; border: 1px solid #e1e2e4; border-radius: 8px;
      background: transparent; color: #434654; cursor: pointer; transition: all .15s;
    }
    .icon-btn:hover:not(:disabled) { background: #f3f4f6; border-color: #c3c6d6; }
    .icon-btn:disabled { opacity: .3; cursor: not-allowed; }
    .icon-btn .material-symbols-outlined { font-size: 16px; }

    .now-playing { display: flex; align-items: center; gap: 0.4rem; min-width: 0; }
    .np-indicator { font-size: .85rem; flex-shrink: 0; }
    .np-text {
      font-size: .82rem; color: #191c1e; font-weight: 500;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .np-hint { font-size: .78rem; color: #737685; }

    .frag-counter { font-size: .7rem; color: #737685; min-width: 30px; text-align: center; }

    .voice-select {
      padding: 0.2rem 0.4rem; background: #f3f4f6; border: 1px solid #e1e2e4;
      border-radius: 6px; color: #191c1e; font-size: .72rem; max-width: 120px; cursor: pointer;
      font-family: 'Hanken Grotesk', sans-serif;
    }
    .speed-ctrl { display: flex; align-items: center; gap: 0.3rem; }
    .speed-label { font-size: .65rem; color: #737685; }
    .speed-range { width: 48px; accent-color: #003d9b; }
    .speed-val { font-size: .68rem; color: #434654; min-width: 24px; }

    /* ===== CONTENT SPLIT ===== */
    .content-split { display: flex; flex: 1; overflow: hidden; }

    /* ===== INDEX PANEL ===== */
    .index-panel {
      width: 300px; min-width: 240px; border-right: 1px solid #e1e2e4;
      display: flex; flex-direction: column; background: #ffffff;
    }
    .index-header { padding: 0.75rem 0.85rem; border-bottom: 1px solid #e1e2e4; background: #fafbfc; }
    .index-title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.6rem; }
    .index-title {
      margin: 0; font-size: .85rem; font-weight: 700; color: #191c1e;
      display: flex; align-items: center; gap: 0.4rem;
    }
    .index-title .material-symbols-outlined { color: #003d9b; font-size: 18px; }
    .index-count {
      font-size: .65rem; color: #737685; background: #edeef0;
      padding: 0.1rem 0.45rem; border-radius: 4px; font-weight: 600;
    }

    .search-box {
      position: relative; display: flex; align-items: center;
    }
    .search-icon {
      position: absolute; left: 8px; color: #737685;
      font-size: 16px !important; pointer-events: none;
    }
    .search-input {
      width: 100%; padding: 0.4rem 2rem 0.4rem 2rem;
      background: #ffffff; border: 1px solid #e1e2e4; border-radius: 8px;
      color: #191c1e; font-size: .78rem; outline: none; box-sizing: border-box;
      font-family: 'Hanken Grotesk', sans-serif;
    }
    .search-input::placeholder { color: #a0a3b1; }
    .search-input:focus { border-color: #003d9b; box-shadow: 0 0 0 3px rgba(0,61,155,.08); }
    .search-clear {
      position: absolute; right: 4px; background: none; border: none;
      color: #737685; cursor: pointer; padding: 2px; display: flex;
    }
    .search-clear .material-symbols-outlined { font-size: 14px; }

    .index-scroll { flex: 1; overflow-y: auto; }
    .state-msg {
      display: flex; align-items: center; justify-content: center; gap: 0.5rem;
      padding: 2rem; color: #737685; font-size: .8rem;
    }
    .state-msg.dim { color: #a0a3b1; }

    .section-list { padding: 0.25rem 0; }

    .section-item {
      display: flex; align-items: center; gap: 0.4rem;
      padding: 0.45rem 0.75rem; cursor: pointer; transition: background .12s;
      border-left: 3px solid transparent; position: relative;
    }
    .section-item:hover { background: #f3f4f6; }
    .section-item.active {
      background: rgba(0,61,155,.04);
      border-left-color: #003d9b;
    }
    .section-item.active::before {
      content: ''; position: absolute; left: 0; top: 50%;
      transform: translateY(-50%); width: 3px; height: 18px;
      background: #003d9b; border-radius: 0 4px 4px 0;
    }
    .section-item.playing {
      background: rgba(5,150,105,.06);
      border-left-color: #059669;
    }
    .section-item.depth-1 { padding-left: 1.2rem; }
    .section-item.depth-2 { padding-left: 2rem; }
    .section-item.depth-1 .section-title { font-size: .76rem; color: #434654; }
    .section-item.depth-2 .section-title { font-size: .72rem; color: #5f6271; }

    .section-play-col { flex-shrink: 0; }
    .section-play-btn {
      width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
      background: #f3f4f6; border: 1px solid #e1e2e4; border-radius: 50%;
      color: #434654; transition: all .2s;
    }
    .section-item:hover .section-play-btn {
      background: #e1e2e4; border-color: #c3c6d6; color: #191c1e;
    }
    .section-play-btn.on {
      background: #059669; border-color: #059669; color: white;
    }
    .section-play-btn .play-icon { font-size: 14px; }
    .section-play-btn .material-symbols-outlined { font-size: 14px; }

    .eq-anim { display: flex; align-items: center; gap: 2px; height: 12px; }
    .eq-anim span {
      width: 2px; background: white; border-radius: 1px;
      animation: eq .5s ease-in-out infinite alternate;
    }
    .eq-anim span:nth-child(1) { height: 4px; animation-delay: 0s; }
    .eq-anim span:nth-child(2) { height: 8px; animation-delay: .15s; }
    .eq-anim span:nth-child(3) { height: 6px; animation-delay: .3s; }
    @keyframes eq { 0% { height: 3px; } 100% { height: 11px; } }

    .section-info { flex: 1; min-width: 0; }
    .section-title {
      font-size: .8rem; color: #191c1e; line-height: 1.3; font-weight: 500;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
    }
    .section-item.active .section-title { color: #003d9b; font-weight: 600; }
    .section-meta { display: flex; align-items: center; gap: 0.4rem; margin-top: 0.15rem; }
    .meta-pill {
      font-size: .6rem; font-weight: 600; color: #737685;
      background: #edeef0; padding: 0.05rem 0.35rem; border-radius: 3px;
    }
    .meta-duration { font-size: .6rem; color: #a0a3b1; }

    .index-footer {
      padding: 0.5rem 0.75rem; border-top: 1px solid #e1e2e4;
      background: #fafbfc;
    }
    .file-info {
      display: flex; align-items: center; gap: 0.35rem;
      font-size: .7rem; color: #737685;
    }
    .file-info .material-symbols-outlined { font-size: 14px; }

    /* ===== PDF VIEWER ===== */
    .pdf-viewer {
      flex: 1; display: flex; align-items: center; justify-content: center;
      background: #f0f1f3; overflow: hidden; position: relative;
    }
    .pdf-frame {
      width: 100%; height: 100%; border: none; background: white;
    }
    .pdf-loading {
      display: flex; align-items: center; gap: 0.5rem; color: #737685; font-size: .85rem;
    }
    .pdf-placeholder {
      display: flex; flex-direction: column; align-items: center; gap: 0.75rem;
      color: #a0a3b1; cursor: pointer; padding: 2rem; transition: color .2s;
    }
    .pdf-placeholder:hover { color: #737685; }
    .pdf-icon { font-size: 48px; width: 48px; height: 48px; }
    .pdf-placeholder p { font-size: .85rem; margin: 0; }

    @media (max-width: 768px) {
      .index-panel { width: 200px; min-width: 160px; }
    }
  `]
})
export class LectorDocumentoComponent implements OnInit, OnDestroy {
  @Input() documentoId!: number;

  private http = inject(HttpClient);
  private textoVoz = inject(TextoVozService);
  private sanitizer = inject(DomSanitizer);
  private cache = inject(DocumentoCacheService);
  private cdr = inject(ChangeDetectorRef);

  textoCompleto = '';
  secciones: Seccion[] = [];
  seccionesFiltradas: Seccion[] = [];
  cargandoTexto = false;

  pdfSafeUrl: SafeResourceUrl | null = null;
  cargandoPdf = false;
  private pdfBlobUrl: string | null = null;

  estadoVoz = { activo: false, pausado: false, fragmentoActual: 0, totalFragmentos: 0 };
  vocesDisponibles: VozDisponible[] = [];
  vozSeleccionadaId = '';
  velocidadLectura = 0.94;
  seccionActiva: Seccion | null = null;
  hayAudioActivo = false;
  terminoBusqueda = '';

  ngOnInit(): void {
    this.textoVoz.estado$.subscribe(estado => {
      this.estadoVoz = { activo: estado.activo, pausado: estado.pausado, fragmentoActual: estado.fragmentoActual, totalFragmentos: estado.totalFragmentos };
      this.hayAudioActivo = estado.activo;
      this.cdr.markForCheck();
    });
    this.textoVoz.vocesDisponibles$.subscribe(voces => {
      this.vocesDisponibles = voces;
      if (!this.vozSeleccionadaId && voces.length) this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? voces[0].id;
      this.cdr.markForCheck();
    });
    this.vozSeleccionadaId = this.textoVoz.vozSeleccionadaId ?? '';
    this.iniciarCarga();
  }

  ngOnDestroy(): void {
    this.textoVoz.detener();
    if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
  }

  private async iniciarCarga(): Promise<void> {
    const cacheKey = DocumentoCacheService.textoKey(this.documentoId);
    const cached = this.cache.get<TextoCompletoResponse>(cacheKey);
    if (cached) { this.aplicarTexto(cached); } else { this.cargandoTexto = true; }
    this.cargarPdf();
    if (!cached) {
      try {
        const resp = await this.http.get<TextoCompletoResponse>(
          `${environment.apiUrl}/documentos/${this.documentoId}/texto-completo`
        ).toPromise();
        if (resp) { this.cache.set(cacheKey, resp); if (!this.textoCompleto) this.aplicarTexto(resp); }
      } catch { this.cargandoTexto = false; }
    }
  }

  private aplicarTexto(resp: TextoCompletoResponse): void {
    this.textoCompleto = resp.textoCompleto || '';
    this.secciones = resp.secciones || [];
    this.seccionesFiltradas = [...this.secciones];
    this.cargandoTexto = false;
    this.cdr.markForCheck();
  }

  cargarPdf(): void {
    if (this.pdfSafeUrl) return;
    const ck = DocumentoCacheService.pdfKey(this.documentoId);
    const cb = this.cache.get<Blob>(ck);
    if (cb) { this.asignarPdf(cb); return; }
    this.cargandoPdf = true;
    this.http.get(`${environment.apiUrl}/documentos/${this.documentoId}/archivo`, { responseType: 'blob' })
      .subscribe({ next: (b) => { this.cache.set(ck, b); this.asignarPdf(b); },
        error: () => { this.cargandoPdf = false; this.cdr.markForCheck(); } });
  }
  private asignarPdf(blob: Blob): void {
    if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
    this.pdfBlobUrl = URL.createObjectURL(blob);
    this.pdfSafeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfBlobUrl);
    this.cargandoPdf = false;
    this.cdr.markForCheck();
  }

  seleccionarYReproducir(sec: Seccion): void {
    this.seccionActiva = sec;
    if (!this.hayAudioActivo || this.estadoVoz.pausado) { this.reproducir(sec); }
    else { this.textoVoz.detener(); setTimeout(() => this.reproducir(sec), 80); }
  }

  private reproducir(sec: Seccion): void {
    if (!this.textoCompleto) return;
    const txt = this.textoCompleto.substring(sec.indiceInicio, sec.indiceFin);
    const limpio = this.limpiarAudio(txt);
    if (!limpio.trim()) return;
    this.textoVoz.seleccionarVoz(this.vozSeleccionadaId);
    this.textoVoz.leer(limpio, this.documentoId);
  }

  togglePlayPause(): void {
    if (!this.seccionActiva && this.seccionesFiltradas.length > 0) { this.seleccionarYReproducir(this.seccionesFiltradas[0]); return; }
    if (this.hayAudioActivo) { this.estadoVoz.pausado ? this.textoVoz.reanudar() : this.textoVoz.pausar(); }
    else if (this.seccionActiva) { this.reproducir(this.seccionActiva); }
  }

  detenerLectura(): void { this.textoVoz.detener(); }
  cambiarVoz(id: string): void { this.vozSeleccionadaId = id; this.textoVoz.seleccionarVoz(id); }

  private limpiarAudio(txt: string): string {
    return txt
      .replace(/^\s*\d+(?:\.\d+){0,3}\s+/, '')
      .replace(/^EST[ÁA]NDAR\s+DE\s+/i, '')
      .replace(/^(?:CAP[ÍI]TULO|SECCI[ÓO]N)\s+[\dIVXLC]+\.?\s*/i, '')
      .replace(/```[\s\S]*?```/g, '')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/!\[[^\]]*\]\([^)]+\)/g, '')
      .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/\*\*([^*]+)\*\*/g, '$1').replace(/\*([^*]+)\*/g, '$1')
      .replace(/\s+/g, ' ').trim();
  }

  tituloSinNumero(t: string): string {
    if (!t) return '';
    return t.replace(/^\d+(?:\.\d+){0,3}\s+/, '')
            .replace(/^(?:EST[ÁA]NDAR\s+DE|CAP[ÍI]TULO|SECCI[ÓO]N)\s+/i, '').trim();
  }

  duracionEstimada(s: Seccion): string {
    const segs = Math.round((s.indiceFin - s.indiceInicio) / 400);
    return segs < 60 ? `${segs}s` : `${Math.floor(segs/60)}m ${segs%60}s`;
  }

  filtrarSecciones(): void {
    const t = this.terminoBusqueda.toLowerCase().trim();
    this.seccionesFiltradas = !t ? [...this.secciones] :
      this.secciones.filter(s => s.titulo.toLowerCase().includes(t) || s.nivel.toLowerCase().includes(t));
  }
}
