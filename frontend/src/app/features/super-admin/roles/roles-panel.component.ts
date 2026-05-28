import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import {
  PermisosService,
  RolColumna,
  RolId,
  Matriz,
} from '../../../core/services/permisos.service';

@Component({
  selector: 'app-roles-panel',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent],
  templateUrl: './roles-panel.component.html',
  styleUrls: ['./roles-panel.component.scss'],
})
export class RolesPanelComponent implements OnInit {
  get roles(): RolColumna[] {
    return this.permisos.roles;
  }

  get grupos() {
    return this.permisos.grupos;
  }

  get totalModulos(): number {
    return this.permisos.totalModulos;
  }

  matriz: Matriz = {};
  guardado = false;
  guardando = false;
  cargando = true;
  errorMsg: string | null = null;

  constructor(private permisos: PermisosService) {}

  ngOnInit(): void {
    this.matriz = this.permisos.matrizPorDefecto();
    this.permisos.getMatriz().subscribe({
      next: (m) => {
        this.matriz = m;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'No se pudo cargar la matriz de permisos.';
        this.cargando = false;
      },
    });
  }

  estaActivo(moduloId: string, rolId: RolId): boolean {
    return this.matriz[moduloId]?.[rolId] ?? false;
  }

  toggle(moduloId: string, rol: RolColumna): void {
    if (rol.bloqueado) {
      return;
    }
    if (!this.matriz[moduloId]) {
      this.matriz[moduloId] = { SUPER_ADMIN: true, ADMIN_EMPRESA: false, USUARIO: false };
    }
    this.matriz[moduloId][rol.id] = !this.matriz[moduloId][rol.id];
    this.guardado = false;
  }

  contarHabilitados(rolId: RolId): number {
    return Object.values(this.matriz).filter((m) => m?.[rolId]).length;
  }

  guardar(): void {
    this.guardando = true;
    this.errorMsg = null;
    this.permisos.guardarMatriz(this.matriz).subscribe({
      next: () => {
        this.guardando = false;
        this.guardado = true;
        setTimeout(() => (this.guardado = false), 2500);
      },
      error: () => {
        this.guardando = false;
        this.errorMsg = 'No se pudo guardar. Verifica tu sesión e inténtalo de nuevo.';
      },
    });
  }

  restaurar(): void {
    this.matriz = this.permisos.matrizPorDefecto();
    this.guardado = false;
  }
}
