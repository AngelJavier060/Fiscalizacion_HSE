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
  badgeKind?: 'ai';
  modulo?: string;
}

interface NavSection {
  id: string;
  label: string;
  items: NavItem[];
}

@Component({
  selector: 'app-admin-empresa-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './admin-empresa-sidebar.component.html',
  styleUrls: ['./admin-empresa-sidebar.component.scss'],
})
export class AdminEmpresaSidebarComponent {
  user: any;

  readonly secciones: NavSection[] = [
    {
      id: 'empresa',
      label: 'Mi Empresa',
      items: [
        { id: 'dashboard', label: 'Dashboard', faIcon: 'fa-solid fa-table-cells-large', route: '/admin-empresa/dashboard' },
        { id: 'documentos', label: 'Documentos', faIcon: 'fa-regular fa-folder-open', route: '/admin-empresa/documentos', modulo: 'documentos' },
      ],
    },
    {
      id: 'accesos',
      label: 'Accesos',
      items: [
        { id: 'usuarios', label: 'Usuarios', faIcon: 'fa-solid fa-users', route: '/admin-empresa/usuarios', modulo: 'usuarios' },
        { id: 'roles', label: 'Roles', faIcon: 'fa-solid fa-id-badge', route: '/admin-empresa/roles', modulo: 'usuarios' },
      ],
    },
    {
      id: 'ia',
      label: 'Inteligencia',
      items: [
        {
          id: 'ia',
          label: 'FISCALIZA-AI',
          faIcon: 'fa-solid fa-wand-magic-sparkles',
          route: '/admin-empresa/ia',
          modulo: 'ia',
          badge: 'IA',
          badgeKind: 'ai',
        },
        { id: 'analizar', label: 'Analizar reporte', faIcon: 'fa-solid fa-chart-simple', route: '/admin-empresa/analizar-reporte', modulo: 'ia' },
      ],
    },
    {
      id: 'config',
      label: 'Configuración',
      items: [
        { id: 'recordatorios', label: 'Recordatorios', faIcon: 'fa-regular fa-bell', route: '/admin-empresa/recordatorios', modulo: 'recordatorios' },
        { id: 'preguntas', label: 'Preguntas SSO', faIcon: 'fa-solid fa-book-open', route: '/admin-empresa/preguntas', modulo: 'simulador' },
      ],
    },
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    this.user = this.authService.getUserData();
  }

  visible(item: NavItem): boolean {
    if (!item.modulo) {
      return true;
    }
    return this.authService.tieneModulo(item.modulo);
  }

  seccionVisible(seccion: NavSection): boolean {
    return seccion.items.some((item) => this.visible(item));
  }

  esActivo(item: NavItem): boolean {
    const url = this.router.url;
    if (!item.route) {
      return false;
    }
    if (item.id === 'ia') {
      return url.includes('/admin-empresa/ia') && !url.includes('analizar-reporte');
    }
    if (item.id === 'analizar') {
      return url.includes('analizar-reporte');
    }
    if (item.id === 'documentos') {
      return url.includes('/admin-empresa/documentos');
    }
    if (item.id === 'dashboard') {
      return url === item.route || url.startsWith(item.route + '/');
    }
    return url === item.route || url.startsWith(item.route + '/');
  }

  get iniciales(): string {
    const nombre = (this.user?.nombre || 'AE').trim();
    const partes = nombre.split(/\s+/).filter(Boolean);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return nombre.slice(0, 2).toUpperCase();
  }

  get empresaNombre(): string {
    return this.user?.empresaNombre || 'Mi Empresa';
  }

  logout(): void {
    this.authService.logout();
  }
}
