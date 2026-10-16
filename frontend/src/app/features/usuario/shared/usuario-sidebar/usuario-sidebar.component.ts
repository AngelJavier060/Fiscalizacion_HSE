import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

interface NavItem {
  id: string;
  label: string;
  faIcon: string;
  route?: string;
  badge?: string;
  badgeKind?: 'count' | 'ai';
}

@Component({
  selector: 'app-usuario-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './usuario-sidebar.component.html',
  styleUrls: ['./usuario-sidebar.component.scss'],
})
export class UsuarioSidebarComponent {
  user: any;

  readonly items: NavItem[] = [
    { id: 'dashboard', label: 'Dashboard', faIcon: 'fa-solid fa-table-cells-large', route: '/usuario/dashboard' },
    { id: 'documentos', label: 'Documentos', faIcon: 'fa-regular fa-file-lines', route: '/usuario/documentos' },
    { id: 'notificaciones', label: 'Recordatorios', faIcon: 'fa-regular fa-bell', route: '/usuario/notificaciones' },
    {
      id: 'ia',
      label: 'FISCALIZA-AI',
      faIcon: 'fa-solid fa-wand-magic-sparkles',
      route: '/usuario/ia',
      badge: 'IA Activa',
      badgeKind: 'ai',
    },
    { id: 'analizar', label: 'Analizar reporte', faIcon: 'fa-solid fa-chart-simple', route: '/usuario/analizar-reporte' },
    { id: 'simulador', label: 'Simulador', faIcon: 'fa-solid fa-clipboard-list', route: '/usuario/simulador' },
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    this.user = this.authService.getUserData();
  }

  visible(item: NavItem): boolean {
    if (item.id === 'dashboard') {
      return true;
    }
    if (item.id === 'documentos') {
      return this.authService.tieneModulo('documentos');
    }
    if (item.id === 'notificaciones') {
      return this.authService.tieneModulo('recordatorios') || this.authService.tieneModulo('notificaciones');
    }
    if (item.id === 'ia' || item.id === 'analizar') {
      return this.authService.tieneModulo('ia');
    }
    if (item.id === 'simulador') {
      return this.authService.tieneModulo('simulador');
    }
    return true;
  }

  esActivo(item: NavItem): boolean {
    const url = this.router.url;
    if (!item.route) {
      return false;
    }
    return url === item.route || url.startsWith(item.route + '/');
  }

  get iniciales(): string {
    const nombre = (this.user?.nombre || 'U').trim();
    const partes = nombre.split(/\s+/).filter(Boolean);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return nombre.slice(0, 2).toUpperCase();
  }

  get empresaNombre(): string {
    return this.user?.empresaNombre || 'Mi empresa';
  }

  get cargoUsuario(): string {
    const empresa = this.empresaNombre;
    return empresa ? `Usuario HSE · ${empresa}` : 'Usuario HSE';
  }

  logout(): void {
    this.authService.logout();
  }
}
