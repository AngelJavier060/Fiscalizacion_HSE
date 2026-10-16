import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SuperAdminSidebarComponent } from '../../super-admin/shared/super-admin-sidebar/super-admin-sidebar.component';
import { UsuarioSidebarComponent } from '../../usuario/shared/usuario-sidebar/usuario-sidebar.component';
import { AuthService } from '../../../core/services/auth.service';
import { SimuladorService } from '../../../core/services/simulador.service';
import { SsoExamen } from '../../../core/models/simulador.model';

@Component({
  selector: 'app-simulador-examen',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent, UsuarioSidebarComponent],
  templateUrl: './simulador-examen.component.html',
  styleUrls: ['./simulador-examen.component.scss'],
})
export class SimuladorExamenComponent implements OnInit, OnDestroy {
  private api = inject(SimuladorService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  examen: SsoExamen | null = null;
  indice = 0;
  error: string | null = null;
  confirmando = false;
  finalizando = false;
  restanteSeg = 0;
  marcadas = new Set<number>();
  private timer: ReturnType<typeof setInterval> | null = null;
  rol = this.auth.getUserRole();

  get base(): string {
    if (this.rol === 'SUPER_ADMIN') return '/super-admin/simulador';
    if (this.rol === 'ADMIN_EMPRESA') return '/admin-empresa/simulador';
    return '/usuario/simulador';
  }
  get esSuper(): boolean { return this.rol === 'SUPER_ADMIN'; }
  get esUsuario(): boolean { return this.rol === 'USUARIO'; }

  get actual() {
    return this.examen?.preguntas[this.indice] ?? null;
  }

  get reloj(): string {
    const m = Math.floor(this.restanteSeg / 60);
    const s = this.restanteSeg % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  get progreso(): number {
    if (!this.examen?.totalPreguntas) return 0;
    return Math.round((this.examen.respondidas / this.examen.totalPreguntas) * 100);
  }

  get modoLabel(): string {
    const modo = this.examen?.modo || '';
    if (modo === 'falladas') return 'Solo falladas';
    return `Modo ${modo} reactivos`;
  }

  get esUltima(): boolean {
    return !!this.examen && this.indice === this.examen.preguntas.length - 1;
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.obtenerExamen(id).subscribe({
      next: (ex) => {
        this.examen = ex;
        this.arrancarTimer(ex);
        const firstUnanswered = ex.preguntas.findIndex((p) => p.opcionElegidaId == null);
        this.indice = firstUnanswered >= 0 ? firstUnanswered : 0;
      },
      error: (e) => {
        const msg = e?.error?.mensaje || '';
        if (/agotó|ya no está en curso/i.test(msg)) {
          this.router.navigate([this.base, 'resultado', id]);
          return;
        }
        this.error = msg || 'No se pudo abrir el examen';
      },
    });
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  @HostListener('window:keydown', ['$event'])
  onKey(ev: KeyboardEvent): void {
    if (!this.examen || this.confirmando || this.finalizando) return;
    const target = ev.target as HTMLElement | null;
    if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;

    if (ev.key === 'Enter') {
      ev.preventDefault();
      if (!this.esUltima) this.siguiente();
      return;
    }
    if (ev.key === 'm' || ev.key === 'M') {
      ev.preventDefault();
      this.toggleMarca();
      return;
    }
    const n = Number(ev.key);
    if (n >= 1 && n <= 9 && this.actual?.opciones[n - 1]) {
      ev.preventDefault();
      this.elegir(this.actual.opciones[n - 1].id);
    }
  }

  elegir(opcionId: number): void {
    if (!this.examen || !this.actual || this.finalizando) return;
    this.actual.opcionElegidaId = opcionId;
    this.api.responder(this.examen.id, this.actual.id, opcionId).subscribe({
      next: (ex) => (this.examen = ex),
      error: (e) => {
        const msg = e?.error?.mensaje || '';
        if (/agotó/i.test(msg) && this.examen) {
          this.irAResultado();
          return;
        }
        this.error = msg || 'No se guardó la respuesta';
      },
    });
  }

  irA(i: number): void {
    if (!this.examen || i < 0 || i >= this.examen.preguntas.length) return;
    this.indice = i;
  }

  anterior(): void {
    if (this.indice > 0) this.indice--;
  }

  siguiente(): void {
    if (this.examen && this.indice < this.examen.preguntas.length - 1) this.indice++;
  }

  toggleMarca(): void {
    const id = this.actual?.id;
    if (id == null) return;
    const next = new Set(this.marcadas);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.marcadas = next;
  }

  marcada(id: number): boolean {
    return this.marcadas.has(id);
  }

  letra(i: number): string {
    return String.fromCharCode(65 + i);
  }

  estadoNav(i: number): 'actual' | 'respondida' | 'marcada' | 'pendiente' {
    if (i === this.indice) return 'actual';
    const p = this.examen?.preguntas[i];
    if (!p) return 'pendiente';
    if (this.marcadas.has(p.id)) return 'marcada';
    if (p.opcionElegidaId != null) return 'respondida';
    return 'pendiente';
  }

  criticidad(tema?: string): string {
    const t = (tema || '').toLowerCase();
    if (/izaje|asme|altura|ansi|incendio|extintor|el[eé]ctric/.test(t)) return 'Alta';
    if (/legislaci|decreto|epp|clasificad|atex/.test(t)) return 'Alta';
    return 'Media';
  }

  pedirFinalizar(): void {
    const sin = this.examen?.preguntas.filter((p) => p.opcionElegidaId == null).length ?? 0;
    if (sin > 0) {
      this.confirmando = true;
      return;
    }
    this.finalizar();
  }

  finalizar(): void {
    if (!this.examen || this.finalizando) return;
    this.finalizando = true;
    this.api.finalizar(this.examen.id).subscribe({
      next: (r) => this.router.navigate([this.base, 'resultado', r.id]),
      error: (e) => {
        this.finalizando = false;
        const msg = e?.error?.mensaje || '';
        if (/agotó|ya no está en curso/i.test(msg) && this.examen) {
          this.irAResultado();
          return;
        }
        this.error = msg || 'No se pudo calificar';
      },
    });
  }

  private irAResultado(): void {
    if (!this.examen) return;
    this.router.navigate([this.base, 'resultado', this.examen.id]);
  }

  private arrancarTimer(ex: SsoExamen): void {
    const inicio = new Date(ex.fechaInicio).getTime();
    const limite = (ex.tiempoLimiteMin || 0) * 60 * 1000;
    const tick = () => {
      this.restanteSeg = Math.max(0, Math.floor((inicio + limite - Date.now()) / 1000));
      if (this.restanteSeg <= 0) {
        if (this.timer) clearInterval(this.timer);
        this.finalizar();
      }
    };
    tick();
    this.timer = setInterval(tick, 1000);
  }
}
