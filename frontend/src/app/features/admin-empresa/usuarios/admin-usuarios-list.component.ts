import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';
import { Usuario } from '../../../core/models/usuario.model';

@Component({
  selector: 'app-admin-usuarios-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './admin-usuarios-list.component.html',
  styleUrls: ['./admin-usuarios-list.component.scss'],
})
export class AdminUsuariosListComponent implements OnInit {
  usuarios: Usuario[] = [];
  loading = true;
  empresaNombre = '';
  empresaId: number | null = null;

  constructor(
    private http: HttpClient,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    const u = this.auth.getUserData();
    this.empresaNombre = u?.empresaNombre || 'Mi empresa';
    this.empresaId = u?.empresaId ?? null;
    if (this.empresaId) {
      this.cargar();
    } else {
      this.loading = false;
    }
  }

  cargar(): void {
    if (!this.empresaId) return;
    this.loading = true;
    this.http
      .get(`${environment.apiUrl}/usuarios/empresa/${this.empresaId}?page=0&size=200`)
      .subscribe({
        next: (r: any) => {
          this.usuarios = r.content || [];
          this.loading = false;
        },
        error: () => (this.loading = false),
      });
  }

  etiquetaRol(rol: string): string {
    switch (rol) {
      case 'ADMIN_EMPRESA':
        return 'Admin empresa';
      case 'USUARIO':
        return 'Usuario';
      case 'SUPER_ADMIN':
        return 'Super Admin';
      default:
        return rol;
    }
  }
}
