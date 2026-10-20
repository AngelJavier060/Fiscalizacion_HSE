import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { IaChatComponent } from '../../shared/ia-chat/ia-chat.component';
import { environment } from '../../../../environments/environment';
import { AdminEmpresaSidebarComponent } from '../shared/admin-empresa-sidebar/admin-empresa-sidebar.component';

@Component({
  selector: 'app-ia-page',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, IaChatComponent, AdminEmpresaSidebarComponent],
  template: `
    <div class="dashboard-container ia-page-shell">
      <app-admin-empresa-sidebar />

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
      height: 100%;
      overflow: hidden;
      display: flex;
      padding: 0;
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
