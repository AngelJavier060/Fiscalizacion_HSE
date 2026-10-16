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
  modo: '20' | '40' | 'falladas' = '20';
  temaId: number | null = null;
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

  empezar(): void {
    this.iniciando = true;
    this.error = null;
    this.api.iniciarExamen(this.modo, this.temaId).subscribe({
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
