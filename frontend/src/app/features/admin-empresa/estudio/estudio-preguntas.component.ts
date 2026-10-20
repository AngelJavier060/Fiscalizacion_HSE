import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { AuthService } from '../../../core/services/auth.service';
import { SimuladorService } from '../../../core/services/simulador.service';
import { SsoEstudioPregunta, SsoTema } from '../../../core/models/simulador.model';
import { AdminEmpresaSidebarComponent } from '../shared/admin-empresa-sidebar/admin-empresa-sidebar.component';

@Component({
  selector: 'app-estudio-preguntas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AdminEmpresaSidebarComponent],
  templateUrl: './estudio-preguntas.component.html',
  styleUrls: ['./estudio-preguntas.component.scss'],
})
export class EstudioPreguntasComponent implements OnInit, OnDestroy {
  private api = inject(SimuladorService);
  private auth = inject(AuthService);
  private sanitizer = inject(DomSanitizer);

  user = this.auth.getUserData();
  temas: SsoTema[] = [];
  preguntas: SsoEstudioPregunta[] = [];
  cargando = true;
  error: string | null = null;
  q = '';
  filtroTema: number | null = null;
  abiertaId: number | null = null;
  mostrarSustentoId: number | null = null;
  previewUrls: Record<number, string> = {};
  previewSafe: Record<number, SafeResourceUrl> = {};
  cargandoSustento: Record<number, boolean> = {};

  ngOnInit(): void {
    this.api.listarTemasEstudio().subscribe({ next: (t) => (this.temas = t) });
    this.recargar();
  }

  ngOnDestroy(): void {
    Object.values(this.previewUrls).forEach((u) => URL.revokeObjectURL(u));
  }

  recargar(): void {
    this.cargando = true;
    this.error = null;
    this.api.listarEstudio(this.q || undefined, this.filtroTema).subscribe({
      next: (p) => {
        this.preguntas = p;
        this.cargando = false;
        if (this.abiertaId != null && !p.some((x) => x.id === this.abiertaId)) {
          this.recoger();
        }
      },
      error: (e) => {
        this.cargando = false;
        this.error = e?.error?.mensaje || 'No se pudo cargar el banco de estudio';
      },
    });
  }

  abrir(p: SsoEstudioPregunta): void {
    this.abiertaId = p.id;
    this.mostrarSustentoId = null;
  }

  recoger(): void {
    this.abiertaId = null;
    this.cerrarVisor();
  }

  get visorPregunta(): SsoEstudioPregunta | null {
    if (this.mostrarSustentoId == null) {
      return null;
    }
    return this.preguntas.find((p) => p.id === this.mostrarSustentoId) || null;
  }

  cerrarVisor(): void {
    this.mostrarSustentoId = null;
  }

  irA(indice: number): void {
    const p = this.preguntas[indice];
    if (p) {
      this.abrir(p);
    }
  }

  esImagen(p: SsoEstudioPregunta): boolean {
    const t = (p.sustentoTipo || '').toLowerCase();
    const n = (p.sustentoNombre || '').toLowerCase();
    return t.startsWith('image/') || /\.(jpg|jpeg|png|webp|gif)$/.test(n);
  }

  esPdf(p: SsoEstudioPregunta): boolean {
    const t = (p.sustentoTipo || '').toLowerCase();
    const n = (p.sustentoNombre || '').toLowerCase();
    return t.includes('pdf') || n.endsWith('.pdf');
  }

  verEvidencia(p: SsoEstudioPregunta): void {
    this.mostrarSustentoId = p.id;
    this.cargarPreview(p);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.mostrarSustentoId != null) {
      this.cerrarVisor();
    }
  }

  tieneModulo(codigo: string): boolean {
    return this.auth.tieneModulo(codigo);
  }

  logout(): void {
    this.auth.logout();
  }

  private mimeSustento(p: SsoEstudioPregunta): string {
    if (p.sustentoTipo) {
      return p.sustentoTipo;
    }
    if (this.esPdf(p)) {
      return 'application/pdf';
    }
    return '';
  }

  private blobTipado(blob: Blob, p: SsoEstudioPregunta): Blob {
    const mime = this.mimeSustento(p);
    if (!mime || blob.type === mime) {
      return blob;
    }
    return new Blob([blob], { type: mime });
  }

  private cargarPreview(p: SsoEstudioPregunta): void {
    if (!p.tieneSustento || this.previewUrls[p.id] || this.cargandoSustento[p.id]) {
      return;
    }
    this.cargandoSustento[p.id] = true;
    this.api.descargarSustento(p.id).subscribe({
      next: (blob) => {
        const typed = this.blobTipado(blob, p);
        const url = URL.createObjectURL(typed);
        this.previewUrls[p.id] = url;
        this.previewSafe[p.id] = this.sanitizer.bypassSecurityTrustResourceUrl(url);
        this.cargandoSustento[p.id] = false;
      },
      error: () => {
        this.cargandoSustento[p.id] = false;
        this.error = 'No se pudo cargar el archivo adjunto';
      },
    });
  }
}
