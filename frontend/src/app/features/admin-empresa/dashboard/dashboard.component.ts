import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AdminEmpresaSidebarComponent } from '../shared/admin-empresa-sidebar/admin-empresa-sidebar.component';

@Component({
  selector: 'app-admin-empresa-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, AdminEmpresaSidebarComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit {
  user: any;

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
  }

  get nombreUsuario(): string {
    return this.user?.nombre || 'Administrador';
  }

  get empresaNombre(): string {
    return this.user?.empresaNombre || 'Mi Empresa';
  }

  get iniciales(): string {
    const nombre = this.nombreUsuario.trim();
    const partes = nombre.split(/\s+/).filter(Boolean);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return nombre.slice(0, 2).toUpperCase();
  }

  get fechaHoy(): string {
    const raw = new Date().toLocaleDateString('es-EC', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
    return raw.charAt(0).toUpperCase() + raw.slice(1);
  }

  tieneModulo(codigo: string): boolean {
    return this.authService.tieneModulo(codigo);
  }

  logout(): void {
    this.authService.logout();
  }
}
