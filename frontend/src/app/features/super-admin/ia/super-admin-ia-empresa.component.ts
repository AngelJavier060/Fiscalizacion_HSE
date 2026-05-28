import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { IaChatComponent } from '../../shared/ia-chat/ia-chat.component';
import { EmpresaService } from '../../../core/services/empresa.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-super-admin-ia-empresa',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, SuperAdminSidebarComponent, IaChatComponent],
  template: `
    <div class="ia-page-shell">
      <app-super-admin-sidebar />

      <main class="ia-full-main">
        @if (empresaId) {
          <app-ia-chat
            [workspaceLayout]="true"
            [empresaId]="empresaId"
            [documentoId]="idDocumentoActivo()"
            [documentoTitulo]="tituloDocumentoActivo()"
            [empresaNombre]="empresaNombre"
            [empresasOpciones]="empresasOpciones"
            [empresaSeleccionadaId]="empresaId"
            (empresaSeleccionadaChange)="onEmpresaChange($event)"
            [documentosOpciones]="documentos"
            [documentoSeleccionadoId]="documentoChatId"
            (documentoSeleccionadoChange)="documentoChatId = $event"
            [loadingDocumentos]="loadingDocs"
            modo="chat"
          ></app-ia-chat>
        } @else if (!loadingRoute && !loadingEmpresas) {
          <div class="ia-pick-empresa">
            <span class="mat-icon">psychology</span>
            <h2>FISCALIZA-AI</h2>
            <p>Selecciona una empresa para comenzar</p>
            @if (empresasOpciones.length) {
              <select
                class="pick-select"
                [(ngModel)]="empresaPickId"
                (ngModelChange)="onEmpresaChange($event)">
                <option [ngValue]="null" disabled>Elegir empresa…</option>
                @for (e of empresasOpciones; track e.id) {
                  <option [ngValue]="e.id">{{ e.nombre }}</option>
                }
              </select>
            } @else {
              <p class="muted">No hay empresas registradas.</p>
              <a routerLink="/super-admin/empresas/nueva" class="btn-link">Crear empresa</a>
            }
          </div>
        }
      </main>
    </div>
  `,
  styles: [`
    @import '../dashboard/dashboard.component.scss';

    :host { display: block; height: 100vh; overflow: hidden; }

    .mat-icon {
      font-family: 'Material Symbols Outlined', sans-serif;
      line-height: 1;
      font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
    }

    .ia-page-shell {
      display: flex;
      height: 100vh;
      overflow: hidden;
      background: #f8f9ff;
    }

    .ia-full-main {
      flex: 1;
      min-width: 0;
      margin-left: 280px;
      height: 100vh;
      overflow: hidden;
      display: flex;
    }

    .ia-full-main app-ia-chat {
      flex: 1;
      min-height: 0;
      width: 100%;
    }

    .ia-pick-empresa {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 24px;
      text-align: center;
      color: #0b1c30;

      .mat-icon {
        font-size: 48px;
        color: #00356a;
        font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 48;
      }

      h2 { margin: 0; font-size: 24px; font-weight: 700; color: #00356a; }
      p { margin: 0; color: #424751; }
      .muted { color: #727782; }
    }

    .pick-select {
      margin-top: 8px;
      min-width: 280px;
      padding: 10px 12px;
      border: 1px solid #c2c6d2;
      border-radius: 8px;
      background: #fff;
      font-size: 14px;
      font-family: inherit;
    }

    .btn-link {
      color: #00356a;
      font-weight: 600;
      text-decoration: none;
      &:hover { text-decoration: underline; }
    }
  `],
})
export class SuperAdminIaEmpresaComponent implements OnInit, OnDestroy {
  empresaId = 0;
  empresaNombre = '';
  empresasOpciones: { id: number; nombre: string }[] = [];
  documentos: { id: number; titulo: string }[] = [];
  documentoChatId: number | null = null;
  loadingDocs = false;
  loadingRoute = true;
  loadingEmpresas = false;
  empresaPickId: number | null = null;

  private routeSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private empresaService: EmpresaService
  ) {}

  ngOnInit(): void {
    this.cargarEmpresas();
    this.routeSub = this.route.paramMap.subscribe((pm) => {
      this.documentoChatId = null;
      const raw = pm.get('empresaId');
      const id = raw ? +raw : 0;
      this.empresaId = Number.isFinite(id) && id > 0 ? id : 0;
      this.loadingRoute = false;
      if (this.empresaId) {
        this.cargarEmpresa();
        this.cargarDocumentos();
      } else {
        this.documentos = [];
        this.empresaNombre = '';
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  onEmpresaChange(id: number): void {
    if (!id || id <= 0) return;
    this.router.navigate(['/super-admin/empresas', id, 'ia']);
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

  private cargarEmpresas(): void {
    this.loadingEmpresas = true;
    this.empresaService.listar(0, 200).subscribe({
      next: (r) => {
        const list = r?.content ?? r ?? [];
        this.empresasOpciones = list.map((e: { id: number; nombre: string }) => ({
          id: e.id,
          nombre: e.nombre,
        }));
        this.loadingEmpresas = false;
        if (!this.empresaId && this.empresasOpciones.length === 1) {
          this.onEmpresaChange(this.empresasOpciones[0].id);
        }
      },
      error: () => {
        this.loadingEmpresas = false;
      },
    });
  }

  private cargarEmpresa(): void {
    this.empresaService.obtener(this.empresaId).subscribe({
      next: (e) => {
        this.empresaNombre = e.nombre || '';
      },
      error: () => {
        this.empresaNombre = '';
      },
    });
  }

  private cargarDocumentos(): void {
    this.loadingDocs = true;
    this.http
      .get<{ content: { id: number; titulo: string }[] }>(
        `${environment.apiUrl}/documentos/empresa/${this.empresaId}?size=200`
      )
      .subscribe({
        next: (r) => {
          this.documentos = r.content || [];
          this.loadingDocs = false;
          if (this.documentoChatId == null && this.documentos.length > 0) {
            const enap = this.documentos.find((d) =>
              /enap|esv|libro/i.test(d.titulo || '')
            );
            if (enap) {
              this.documentoChatId = enap.id;
            } else if (this.documentos.length === 1) {
              this.documentoChatId = this.documentos[0].id;
            }
          }
        },
        error: () => {
          this.loadingDocs = false;
        },
      });
  }
}
