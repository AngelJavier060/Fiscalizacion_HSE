import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

interface NavItem {
  id: string;
  label: string;
  icon: string;
  faIcon: string;
  route?: string;
  disabled?: boolean;
  title?: string;
  badge?: string;
  badgeKind?: 'nuevo' | 'ai';
}

interface NavSection {
  id: string;
  label: string;
  items: NavItem[];
  collapsed: boolean;
}

@Component({
  selector: 'app-super-admin-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './super-admin-sidebar.component.html',
  styleUrls: ['./super-admin-sidebar.component.scss'],
})
export class SuperAdminSidebarComponent {
  user: any;

  readonly secciones: NavSection[] = [
    {
      id: 'principal',
      label: 'Principal',
      collapsed: false,
      items: [
        { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', faIcon: 'fa-solid fa-table-columns', route: '/super-admin/dashboard' },
        { id: 'usuarios', label: 'Usuarios', icon: 'group', faIcon: 'fa-solid fa-users', route: '/super-admin/usuarios' },
        { id: 'roles', label: 'Roles y Permisos', icon: 'admin_panel_settings', faIcon: 'fa-solid fa-id-badge', route: '/super-admin/roles' },
        { id: 'empresas', label: 'Empresas', icon: 'business', faIcon: 'fa-regular fa-building', route: '/super-admin/empresas' },
      ],
    },
    {
      id: 'hse',
      label: 'Gestión HSE',
      collapsed: false,
      items: [
        { id: 'documentos', label: 'Documentos HSE', icon: 'description', faIcon: 'fa-regular fa-folder-open', route: '/super-admin/documentos' },
        {
          id: 'actividades',
          label: 'Actividades Diarias',
          icon: 'event_note',
          faIcon: 'fa-regular fa-calendar-check',
          disabled: true,
          title: 'Próximamente',
        },
        {
          id: 'controles',
          label: 'Controles Críticos',
          icon: 'security',
          faIcon: 'fa-solid fa-shield-virus',
          disabled: true,
          title: 'Próximamente',
        },
        { id: 'permiso', label: 'Permisos', icon: 'lock_person', faIcon: 'fa-solid fa-lock-open', route: '/super-admin/permisos' },
        {
          id: 'preguntas-fis',
          label: 'Preguntas Fis.',
          icon: 'quiz',
          faIcon: 'fa-solid fa-clipboard-question',
          route: '/super-admin/preguntas',
          badge: 'Nuevo',
          badgeKind: 'nuevo',
        },
        {
          id: 'conocimientos',
          label: 'Conocimientos',
          icon: 'menu_book',
          faIcon: 'fa-solid fa-book-bookmark',
          disabled: true,
          title: 'Próximamente',
        },
        {
          id: 'puntos',
          label: 'Puntos Clave',
          icon: 'star',
          faIcon: 'fa-regular fa-star',
          disabled: true,
          title: 'Próximamente',
        },
      ],
    },
    {
      id: 'ia',
      label: 'Inteligencia',
      collapsed: false,
      items: [
        {
          id: 'fiscaliza-ai',
          label: 'FISCALIZA-AI',
          icon: 'psychology',
          faIcon: 'fa-solid fa-robot',
          route: '/super-admin/ia',
          badge: 'AI',
          badgeKind: 'ai',
        },
        { id: 'analizar-reporte', label: 'Analizar Reporte', icon: 'analytics', faIcon: 'fa-solid fa-chart-pie', route: '/super-admin/analizar-reporte' },
      ],
    },
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    this.user = this.authService.getUserData();
  }

  toggleSeccion(seccion: NavSection): void {
    seccion.collapsed = !seccion.collapsed;
  }

  esActivo(item: NavItem): boolean {
    if (!item.route) {
      return false;
    }
    const url = this.router.url;
    if (item.id === 'fiscaliza-ai') {
      return url.includes('/ia') && !url.includes('analizar-reporte');
    }
    if (item.id === 'analizar-reporte') {
      return url.includes('analizar-reporte');
    }
    if (item.id === 'permiso') {
      return url.includes('/super-admin/permisos');
    }
    if (item.id === 'empresas') {
      return url.includes('/super-admin/empresas') && !url.includes('/documentos') && !url.includes('/ia');
    }
    if (item.id === 'documentos') {
      return url.includes('/documentos');
    }
    return url === item.route || url.startsWith(item.route + '/');
  }

  get iniciales(): string {
    const nombre = (this.user?.nombre || 'SA').trim();
    const partes = nombre.split(/\s+/).filter(Boolean);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return nombre.slice(0, 2).toUpperCase();
  }

  get emailUsuario(): string {
    return this.user?.email || 'admin@fiscalizacionhse.com';
  }

  logout(): void {
    this.authService.logout();
  }
}
