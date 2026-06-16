import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

interface NavItem {
  id: string;
  label: string;
  icon: string;
  route?: string;
  disabled?: boolean;
  title?: string;
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
        { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', route: '/super-admin/dashboard' },
        { id: 'usuarios', label: 'Usuarios', icon: 'group', route: '/super-admin/usuarios' },
        { id: 'roles', label: 'Roles y Permisos', icon: 'admin_panel_settings', route: '/super-admin/roles' },
        { id: 'empresas', label: 'Empresas', icon: 'business', route: '/super-admin/empresas' },
      ],
    },
    {
      id: 'hse',
      label: 'GestiÃ³n HSE',
      collapsed: false,
      items: [
        { id: 'documentos', label: 'Documentos', icon: 'description', route: '/super-admin/documentos' },
        {
          id: 'actividades',
          label: 'Actividades diarias',
          icon: 'event_note',
          disabled: true,
          title: 'PrÃ³ximamente',
        },
        {
          id: 'controles',
          label: 'Controles crÃ­ticos',
          icon: 'security',
          disabled: true,
          title: 'PrÃ³ximamente',
        },
                { id: 'permiso', label: 'Permisos', icon: 'lock_person', route: '/super-admin/permisos' },
        {
          id: 'conocimientos',
          label: 'Conocimientos',
          icon: 'menu_book',
          disabled: true,
          title: 'PrÃ³ximamente',
        },
        {
          id: 'puntos',
          label: 'Puntos clave',
          icon: 'star',
          disabled: true,
          title: 'PrÃ³ximamente',
        },
      ],
    },
    {
      id: 'ia',
      label: 'Inteligencia',
      collapsed: false,
      items: [
        { id: 'fiscaliza-ai', label: 'FISCALIZA-AI', icon: 'psychology', route: '/super-admin/ia' },
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
      return url.includes('/ia');
    }
    if (item.id === 'permiso') {
      return url.includes('/super-admin/permisos');
    }
    if (item.id === 'empresas') {
      return url.includes('/super-admin/empresas');
    }
    if (item.id === 'documentos') {
      return url.includes('/super-admin/documentos');
    }
    return url === item.route || url.startsWith(item.route + '/');
  }

  get iniciales(): string {
    const nombre = (this.user?.nombre || 'SA').trim();
    const partes = nombre.split(/\s+/).filter(Boolean);
    if (partes.length >= 2) return (partes[0][0] + partes[1][0]).toUpperCase();
    return nombre.slice(0, 2).toUpperCase();
  }

  logout(): void {
    this.authService.logout();
  }
}


