import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SuperAdminSidebarComponent } from '../../super-admin/shared/super-admin-sidebar/super-admin-sidebar.component';
import { UsuarioSidebarComponent } from '../../usuario/shared/usuario-sidebar/usuario-sidebar.component';
import { AuthService } from '../../../core/services/auth.service';
import { SimuladorService } from '../../../core/services/simulador.service';
import { SsoResultado } from '../../../core/models/simulador.model';

type FiltroTema = 'todos' | 'criticos' | 'aprobados';
type Criticidad = 'Crítico' | 'Obligatorio' | 'Alto' | 'Medio';

export interface TemaFila {
  tema: string;
  total: number;
  correctas: number;
  pct: number;
  criticidad: Criticidad;
  aprobado: boolean;
  parcial: boolean;
}

@Component({
  selector: 'app-simulador-resultado',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent, UsuarioSidebarComponent],
  templateUrl: './simulador-resultado.component.html',
  styleUrls: ['./simulador-resultado.component.scss'],
})
export class SimuladorResultadoComponent implements OnInit {
  private api = inject(SimuladorService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  res: SsoResultado | null = null;
  error: string | null = null;
  cargando = true;
  verRevision = false;
  filtro: FiltroTema = 'todos';
  notaMinima = 70;
  rol = this.auth.getUserRole();

  get base(): string {
    if (this.rol === 'SUPER_ADMIN') return '/super-admin/simulador';
    if (this.rol === 'ADMIN_EMPRESA') return '/admin-empresa/simulador';
    return '/usuario/simulador';
  }

  get documentosBase(): string {
    if (this.rol === 'SUPER_ADMIN') return '/super-admin/documentos';
    if (this.rol === 'ADMIN_EMPRESA') return '/admin-empresa/documentos';
    return '/usuario/documentos';
  }

  get esSuper(): boolean { return this.rol === 'SUPER_ADMIN'; }
  get esUsuario(): boolean { return this.rol === 'USUARIO'; }

  get nota(): number {
    return Number(this.res?.nota ?? 0);
  }

  get incorrectas(): number {
    if (!this.res) return 0;
    return Math.max(0, this.res.totalPreguntas - this.res.correctas);
  }

  get tasaError(): number {
    if (!this.res?.totalPreguntas) return 0;
    return (this.incorrectas / this.res.totalPreguntas) * 100;
  }

  get aciertosNecesarios(): number {
    if (!this.res) return 0;
    return Math.ceil((this.res.totalPreguntas * this.notaMinima) / 100);
  }

  get brecha(): number {
    return this.nota - this.notaMinima;
  }

  get dasharray(): string {
    const pct = Math.max(0, Math.min(100, this.nota));
    return `${pct}, 100`;
  }

  get tiempoEmpleado(): string {
    if (!this.res?.fechaInicio || !this.res.fechaFin) return '—';
    const ms = new Date(this.res.fechaFin).getTime() - new Date(this.res.fechaInicio).getTime();
    if (!Number.isFinite(ms) || ms < 0) return '—';
    const totalSec = Math.round(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}m ${s.toString().padStart(2, '0')}s`;
  }

  get promedioReactivo(): string {
    if (!this.res?.fechaInicio || !this.res.fechaFin || !this.res.totalPreguntas) return '—';
    const ms = new Date(this.res.fechaFin).getTime() - new Date(this.res.fechaInicio).getTime();
    if (!Number.isFinite(ms) || ms < 0) return '—';
    const seg = Math.round(ms / 1000 / this.res.totalPreguntas);
    return `${seg} seg / reactivo`;
  }

  get fechaEval(): string {
    const raw = this.res?.fechaFin || this.res?.fechaInicio;
    if (!raw) return '';
    const txt = new Date(raw).toLocaleDateString('es-EC', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
    return txt.charAt(0).toUpperCase() + txt.slice(1);
  }

  get modoLabel(): string {
    const modo = this.res?.modo || '';
    if (modo === 'falladas') return 'Solo falladas';
    return `Modo ${modo}`;
  }

  get filas(): TemaFila[] {
    return (this.res?.aciertosPorTema || []).map((t) => {
      const pct = t.total ? (t.correctas / t.total) * 100 : 0;
      const criticidad = this.criticidadDe(t.tema);
      return {
        tema: t.tema,
        total: t.total,
        correctas: t.correctas,
        pct,
        criticidad,
        aprobado: pct >= this.notaMinima,
        parcial: pct >= 50 && pct < this.notaMinima,
      };
    });
  }

  get filasVisibles(): TemaFila[] {
    if (this.filtro === 'criticos') {
      return this.filas.filter((f) => f.criticidad === 'Crítico' || f.criticidad === 'Obligatorio');
    }
    if (this.filtro === 'aprobados') {
      return this.filas.filter((f) => f.aprobado || f.parcial);
    }
    return this.filas;
  }

  get totalCriticos(): number {
    return this.filas.filter((f) => f.criticidad === 'Crítico' || f.criticidad === 'Obligatorio').length;
  }

  get totalAprobados(): number {
    return this.filas.filter((f) => f.aprobado || f.parcial).length;
  }

  get temasSobre50(): number {
    return this.filas.filter((f) => f.pct >= 50).length;
  }

  get planRefuerzo(): TemaFila[] {
    return [...this.filas]
      .sort((a, b) => a.pct - b.pct || b.total - a.total)
      .filter((f) => !f.aprobado)
      .slice(0, 3);
  }

  get diagnostico(): string {
    const debiles = [...this.filas]
      .sort((a, b) => a.pct - b.pct)
      .filter((f) => f.pct < this.notaMinima)
      .slice(0, 2)
      .map((f) => f.tema);
    if (this.res?.aprobado) {
      return debiles.length
        ? `Desempeño dentro del umbral operativo. Conviene reforzar ${debiles.join(' y ')} en el próximo ciclo.`
        : 'Desempeño dentro del umbral operativo. Mantenga la lectura periódica de la normativa vigente.';
    }
    if (debiles.length) {
      return `Vulnerabilidad en ${debiles.join(' y ')}. Se recomienda un ciclo de nivelación antes de un nuevo intento de habilitación.`;
    }
    return 'El resultado no alcanza el umbral de habilitación operativa. Revise las respuestas y reintente el simulacro.';
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.inicio().subscribe({
      next: (d) => {
        if (d?.notaMinima) this.notaMinima = d.notaMinima;
      },
    });
    this.api.resultado(id).subscribe({
      next: (r) => {
        this.res = r;
        this.cargando = false;
      },
      error: (e) => {
        this.cargando = false;
        this.error = e?.error?.mensaje || 'No se pudo cargar el resultado';
      },
    });
  }

  volver(): void {
    this.router.navigate([this.base]);
  }

  irADocumentos(): void {
    this.router.navigate([this.documentosBase]);
  }

  toggleRevision(): void {
    this.verRevision = !this.verRevision;
  }

  prioridad(fila: TemaFila, index: number): string {
    if (fila.criticidad === 'Crítico' || fila.pct < 30) return 'Prioridad 1';
    return index === 0 ? 'Prioridad 1' : 'Prioridad 2';
  }

  private criticidadDe(tema: string): Criticidad {
    const t = (tema || '').toLowerCase();
    if (/izaje|asme|altura|ansi|incendio|extintor|el[eé]ctric/.test(t)) return 'Crítico';
    if (/legislaci|decreto|2393|c[oó]digo del trabajo/.test(t)) return 'Obligatorio';
    if (/epp|protecci[oó]n|clasificad|atex|alarma|detecci[oó]n/.test(t)) return 'Alto';
    return 'Medio';
  }
}
