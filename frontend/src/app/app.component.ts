import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <router-outlet></router-outlet>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
    }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Fiscalización HSE';

  /** Tiempo de inactividad permitido antes de cerrar sesión (5 minutos). */
  private readonly limiteMs = 5 * 60 * 1000;
  private timeoutId: any = null;

  /** Eventos que cuentan como "actividad" del usuario. */
  private readonly eventos = [
    'mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click', 'wheel',
  ];

  private readonly onActividad = (): void => this.reiniciar();

  constructor(private auth: AuthService, private zone: NgZone) {}

  ngOnInit(): void {
    // Los listeners corren FUERA de Angular para no disparar detección de
    // cambios en cada movimiento del ratón (rendimiento).
    this.zone.runOutsideAngular(() => {
      for (const ev of this.eventos) {
        document.addEventListener(ev, this.onActividad, { passive: true });
      }
    });
    this.reiniciar();
  }

  ngOnDestroy(): void {
    for (const ev of this.eventos) {
      document.removeEventListener(ev, this.onActividad);
    }
    if (this.timeoutId) clearTimeout(this.timeoutId);
  }

  private reiniciar(): void {
    if (this.timeoutId) clearTimeout(this.timeoutId);
    this.zone.runOutsideAngular(() => {
      this.timeoutId = setTimeout(() => {
        this.zone.run(() => this.cerrarPorInactividad());
      }, this.limiteMs);
    });
  }

  private cerrarPorInactividad(): void {
    // Solo cierra si hay una sesión activa (no afecta a login ni landing).
    if (this.auth.isAuthenticated()) {
      this.auth.logout();
    }
  }
}
