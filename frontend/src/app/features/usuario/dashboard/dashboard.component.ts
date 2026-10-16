import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';
import { UsuarioSidebarComponent } from '../shared/usuario-sidebar/usuario-sidebar.component';

@Component({
  selector: 'app-usuario-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, UsuarioSidebarComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./ud-dashboard.scss'],
})
export class DashboardComponent implements OnInit {
  private auth = inject(AuthService);
  private http = inject(HttpClient);

  user: any;
  totalDocumentos = 0;
  totalNotificaciones = 0;
  documentos: any[] = [];
  filtroDocumento = '';
  loading = true;

  get nombreUsuario(): string {
    return this.user?.nombre || 'Usuario';
  }

  get empresaNombre(): string {
    return this.user?.empresaNombre || 'su empresa';
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

  get herramientasActivas(): number {
    let n = 0;
    if (this.tieneModulo('documentos')) n += 1;
    if (this.tieneModulo('ia')) n += 2;
    if (this.tieneModulo('recordatorios') || this.tieneModulo('notificaciones')) n += 1;
    if (this.tieneModulo('simulador')) n += 1;
    return n;
  }

  get documentosFiltrados(): any[] {
    const q = this.filtroDocumento.trim().toLowerCase();
    if (!q) {
      return this.documentos;
    }
    return this.documentos.filter((doc) => {
      const titulo = (doc.titulo || '').toLowerCase();
      const archivo = (doc.archivoNombre || '').toLowerCase();
      const id = String(doc.id || '');
      return titulo.includes(q) || archivo.includes(q) || id.includes(q);
    });
  }

  ngOnInit(): void {
    this.user = this.auth.getUserData();
    this.cargar();
  }

  tieneModulo(codigo: string): boolean {
    return this.auth.tieneModulo(codigo);
  }

  categoriaDocumento(doc: any): string {
    const desc = (doc.descripcion || '').trim();
    if (desc) {
      return desc.length > 42 ? desc.slice(0, 42) + '…' : desc;
    }
    if (doc.idiomaOriginal || doc.idiomaDetectado) {
      const idioma = (doc.idiomaOriginal || doc.idiomaDetectado || '').toUpperCase();
      return idioma === 'EN' ? 'Normativa (EN)' : 'Normativa HSE';
    }
    return 'Normativa HSE';
  }

  codigoDocumento(doc: any): string {
    if (doc.archivoNombre) {
      return doc.archivoNombre.replace(/\.pdf$/i, '');
    }
    return `DOC-${doc.id}`;
  }

  estadoDocumento(doc: any): { label: string; kind: 'ok' | 'warn' | 'err' } {
    const estado = (doc.estadoProcesamiento || '').toUpperCase();
    if (doc.activo === false) {
      return { label: 'Inactivo', kind: 'err' };
    }
    if (estado === 'ERROR' || estado === 'FALLIDO') {
      return { label: 'Con error', kind: 'err' };
    }
    if (estado === 'PROCESANDO' || estado === 'PENDIENTE') {
      return { label: 'En actualización', kind: 'warn' };
    }
    return { label: 'Vigente', kind: 'ok' };
  }

  irADocumento(doc: any): string[] {
    return ['/usuario/documentos'];
  }

  consultarIa(doc: any): string[] {
    return ['/usuario/ia'];
  }

  private cargar(): void {
    const empresaId = this.user?.empresaId;
    const docs$ = empresaId
      ? this.http.get<any>(`${environment.apiUrl}/documentos/empresa/${empresaId}?page=0&size=5`)
      : null;
    const notif$ = this.http.get<any>(`${environment.apiUrl}/notificaciones/bandeja?page=0&size=1`);

    let pending = 0;
    const done = () => {
      pending -= 1;
      if (pending <= 0) {
        this.loading = false;
      }
    };

    if (docs$) {
      pending += 1;
      docs$.subscribe({
        next: (r) => {
          this.totalDocumentos = r.totalElements ?? (r.content || []).length;
          this.documentos = r.content || [];
          done();
        },
        error: () => done(),
      });
    }

    pending += 1;
    notif$.subscribe({
      next: (r) => {
        this.totalNotificaciones = r.totalElements ?? (r.content || []).length;
        done();
      },
      error: () => done(),
    });

    if (pending === 0) {
      this.loading = false;
    }
  }
}
