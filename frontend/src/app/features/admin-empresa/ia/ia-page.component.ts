import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { IaChatComponent } from '../../shared/ia-chat/ia-chat.component';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-ia-page',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, IaChatComponent],
  template: `
    <div class="dashboard-container ia-page-shell">
      <aside class="sidebar">
        <div class="sidebar-header">
          <div class="logo-small">
            <svg width="32" height="32" viewBox="0 0 48 48" fill="none">
              <rect width="48" height="48" rx="12" fill="#059669"/>
              <path d="M14 24L21 31L34 18" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="brand">Fiscalización HSE</span>
        </div>
        <nav class="sidebar-nav">
          <div class="nav-label">Mi Empresa</div>
          <a routerLink="/admin-empresa/dashboard" class="nav-item"><span class="nav-icon">📊</span> Dashboard</a>
          <a routerLink="/admin-empresa/documentos" class="nav-item"><span class="nav-icon">📄</span> Documentos</a>
          <a class="nav-item"><span class="nav-icon">👥</span> Usuarios</a>
          <div class="nav-label">Inteligencia</div>
          <a routerLink="/admin-empresa/ia" class="nav-item active"><span class="nav-icon">🤖</span> FISCALIZA-AI</a>
          <a routerLink="/admin-empresa/recordatorios" class="nav-item"><span class="nav-icon">🔔</span> Recordatorios</a>
          <a class="nav-item"><span class="nav-icon">⚙️</span> Ajustes</a>
        </nav>
        <div class="sidebar-footer">
          <div class="user-info">
            <div class="user-avatar admin">{{ user?.nombre?.charAt(0) || 'A' }}</div>
            <div>
              <p class="user-name">{{ user?.nombre || 'Admin' }}</p>
              <p class="user-role">{{ user?.empresaNombre || 'Mi Empresa' }}</p>
            </div>
          </div>
          <button (click)="logout()" class="btn-logout">Cerrar Sesión</button>
        </div>
      </aside>

      <main class="ia-full-main">
        <app-ia-chat
          [workspaceLayout]="true"
          [empresaId]="empresaId"
          [documentoId]="idDocumentoActivo()"
          [documentoTitulo]="tituloDocumentoActivo()"
          [empresaNombre]="user?.empresaNombre || ''"
          [documentosOpciones]="documentos"
          [documentoSeleccionadoId]="documentoChatId"
          (documentoSeleccionadoChange)="documentoChatId = $event"
          [loadingDocumentos]="loadingDocs"
          modo="chat"
        ></app-ia-chat>
      </main>
    </div>
  `,
  styles: [`
    @import '../../admin-empresa/dashboard/dashboard.component.scss';

    :host { display: block; height: 100vh; overflow: hidden; }

    .ia-page-shell {
      height: 100vh;
      overflow: hidden;
    }

    .ia-full-main {
      flex: 1;
      min-width: 0;
      height: 100vh;
      overflow: hidden;
      display: flex;
      padding: 0;
      margin-left: 260px;
    }

    .ia-full-main app-ia-chat {
      flex: 1;
      min-height: 0;
      width: 100%;
    }
  `],
})
export class IaPageComponent implements OnInit {
  user: any;
  empresaId: number = 0;
  documentos: { id: number; titulo: string }[] = [];
  documentoChatId: number | null = null;
  loadingDocs = false;

  constructor(
    private authService: AuthService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    this.empresaId = this.user?.empresaId || 0;
    this.cargarDocumentos();
  }

  idDocumentoActivo(): number | undefined {
    return this.documentoChatId != null && this.documentoChatId > 0
      ? this.documentoChatId
      : undefined;
  }

  tituloDocumentoActivo(): string {
    if (this.documentoChatId == null || this.documentoChatId <= 0) {
      return '';
    }
    const d = this.documentos.find((x) => x.id === this.documentoChatId);
    return d?.titulo ?? '';
  }

  private cargarDocumentos(): void {
    if (!this.empresaId) {
      return;
    }
    this.loadingDocs = true;
    this.http
      .get<{ content: { id: number; titulo: string }[] }>(
        `${environment.apiUrl}/documentos/empresa/${this.empresaId}?size=200`
      )
      .subscribe({
        next: (r) => {
          this.documentos = r.content || [];
          this.loadingDocs = false;
        },
        error: () => {
          this.loadingDocs = false;
        },
      });
  }

  logout(): void {
    this.authService.logout();
  }
}
