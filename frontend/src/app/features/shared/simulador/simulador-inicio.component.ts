import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { SuperAdminSidebarComponent } from '../../super-admin/shared/super-admin-sidebar/super-admin-sidebar.component';
import { UsuarioSidebarComponent } from '../../usuario/shared/usuario-sidebar/usuario-sidebar.component';
import { AuthService } from '../../../core/services/auth.service';
import { SimuladorService } from '../../../core/services/simulador.service';
import { SsoInicio } from '../../../core/models/simulador.model';

@Component({
  selector: 'app-simulador-inicio',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, SuperAdminSidebarComponent, UsuarioSidebarComponent],
  templateUrl: './simulador-inicio.component.html',
  styleUrls: ['./simulador-inicio.component.scss'],
})
export class SimuladorInicioComponent implements OnInit {
  private api = inject(SimuladorService);
  private auth = inject(AuthService);
  private router = inject(Router);

  data: SsoInicio | null = null;
  cargando = true;
  error: string | null = null;
  iniciando = false;
  alcance: 'general' | 'tema' | 'falladas' = 'general';
  temaId: number | null = null;
  bancoElegido = 20;
  readonly bancos = [
    { n: 20, min: 15, label: '20 preguntas', tiempo: '15 min' },
    { n: 40, min: 30, label: '40 preguntas', tiempo: '30 min' },
    { n: 60, min: 50, label: '60 preguntas', tiempo: '50 min' },
    { n: 0, min: 90, label: 'Todas', tiempo: '1 h 30 min' },
  ];
  rol = this.auth.getUserRole();

  get base(): string {
    if (this.rol === 'SUPER_ADMIN') return '/super-admin/simulador';
    if (this.rol === 'ADMIN_EMPRESA') return '/admin-empresa/simulador';
    return '/usuario/simulador';
  }

  get esSuper(): boolean { return this.rol === 'SUPER_ADMIN'; }
  get esUsuario(): boolean { return this.rol === 'USUARIO'; }

  ngOnInit(): void {
    this.api.inicio().subscribe({
      next: (d) => {
        this.data = d;
        this.cargando = false;
        if (!this.temaId && d.temas.length) {
          this.temaId = d.temas[0].id;
        }
        const n = d.preguntasPorBanco;
        this.bancoElegido = n === 0 || n === 20 || n === 40 || n === 60 ? n : 20;
      },
      error: () => {
        this.cargando = false;
        this.error = 'No se pudo cargar el simulador';
      },
    });
  }

  get pctVuelta(): number {
    if (!this.data?.preguntasBanco) return 0;
    return Math.min(100, Math.round((this.data.preguntasVistasVuelta / this.data.preguntasBanco) * 100));
  }

  continuar(): void {
    if (!this.data?.intentoPendienteId) return;
    this.router.navigate([this.base, 'examen', this.data.intentoPendienteId]);
  }

  get alcanceLabel(): string {
    if (this.alcance === 'falladas') return 'Falladas';
    if (this.alcance === 'tema') {
      return this.data?.temas.find((t) => t.id === this.temaId)?.nombre || 'Tema';
    }
    return 'General';
  }

  get totalAlcance(): number {
    if (!this.data) return 0;
    if (this.alcance === 'tema') {
      return this.data.temas.find((t) => t.id === this.temaId)?.preguntasActivas || 0;
    }
    return this.data.preguntasBanco;
  }

  get cumplidasAlcance(): number {
    if (!this.data) return 0;
    if (this.alcance === 'tema') {
      return this.data.temas.find((t) => t.id === this.temaId)?.cumplidasVuelta || 0;
    }
    return this.data.preguntasVistasVuelta;
  }

  get puedeRepetir(): boolean {
    if (!this.data?.bloqueRepetir || this.alcance === 'falladas') return false;
    const tid = this.data.bloqueRepetirTemaId ?? null;
    return this.alcance === 'general' ? tid == null : tid === this.temaId;
  }

  get bancoN(): number {
    return this.bancoElegido === 0 || this.bancoElegido === 20 || this.bancoElegido === 40 || this.bancoElegido === 60
      ? this.bancoElegido
      : 20;
  }

  get esTodas(): boolean {
    return this.bancoN === 0;
  }

  get tiempoMin(): number {
    const n = this.preguntasEsteExamen;
    if (n < 10) return 10;
    if (n <= 20) return 15;
    if (n <= 40) return 30;
    if (n <= 60) return 50;
    return 90;
  }

  get tiempoLabel(): string {
    const min = this.tiempoMin;
    if (min >= 60) {
      const h = Math.floor(min / 60);
      const m = min % 60;
      return m ? `${h} h ${m} min` : `${h} h`;
    }
    return `${min} min`;
  }

  get bancoLabel(): string {
    return this.esTodas ? 'Todas las preguntas' : `${this.bancoN} preguntas`;
  }

  get preguntasEsteExamen(): number {
    if (!this.data) return 0;
    if (this.alcance === 'falladas') {
      const n = this.data.falladasPendientes;
      return this.esTodas ? n : Math.min(this.bancoN, n);
    }
    const pendientes = Math.max(0, this.totalAlcance - this.cumplidasAlcance);
    const pool = pendientes > 0 ? pendientes : (this.alcance === 'general' ? this.totalAlcance : pendientes);
    return this.esTodas ? pool : Math.min(this.bancoN, pool);
  }

  empezar(nuevoSet = true): void {
    this.iniciando = true;
    this.error = null;
    const modo = this.alcance === 'falladas' ? 'falladas' : 'banco';
    const temaId = this.alcance === 'tema' ? this.temaId : null;
    this.api.iniciarExamen(modo, temaId, this.bancoN, nuevoSet).subscribe({
      next: (ex) => this.router.navigate([this.base, 'examen', ex.id]),
      error: (e) => {
        this.iniciando = false;
        this.error = e?.error?.mensaje || 'No se pudo iniciar el examen';
      },
    });
  }

  verResultado(id: number): void {
    this.router.navigate([this.base, 'resultado', id]);
  }
}
