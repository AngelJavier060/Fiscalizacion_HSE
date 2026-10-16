import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { timeout } from 'rxjs/operators';
import { AuthService } from '../../../core/services/auth.service';
import { EmpresaService } from '../../../core/services/empresa.service';
import { Empresa } from '../../../core/models/empresa.model';
import { environment } from '../../../../environments/environment';
import { SuperAdminSidebarComponent } from '../../super-admin/shared/super-admin-sidebar/super-admin-sidebar.component';
import { UsuarioSidebarComponent } from '../../usuario/shared/usuario-sidebar/usuario-sidebar.component';
import { MarkdownPipe } from '../pipes/markdown.pipe';

const EMPRESA_FISCALIZACION_KEY = 'hse.empresaFiscalizacionId';

export interface CoincidenciaReporte {
  documentoId: number;
  documentoTitulo: string;
  cita: string;
  relevancia: number;
}

export interface AnalizarReporteUi {
  motor: string;
  ollamaListo: boolean;
  modelo: string;
  avisoMotor: string;
  caracteresInforme: number;
  archivoNombre: string | null;
  documentosConsultados: number;
  coincidencias: CoincidenciaReporte[];
  huecos: string[];
  textoPropuesto: string;
}

@Component({
  selector: 'app-analizar-reporte',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, SuperAdminSidebarComponent, UsuarioSidebarComponent, MarkdownPipe],
  templateUrl: './analizar-reporte.component.html',
  styleUrls: ['./analizar-reporte.component.scss'],
})
export class AnalizarReporteComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private empresaService = inject(EmpresaService);

  esSuperAdmin = this.auth.getUserRole() === 'SUPER_ADMIN';
  esUsuario = this.auth.getUserRole() === 'USUARIO';
  empresa: Empresa | null = null;
  empresaId: number | null = null;
  empresaNombre = '';
  loadingEmpresa = true;

  texto = '';
  archivo: File | null = null;
  archivoNombre = '';
  analizando = false;
  error: string | null = null;
  resultado: AnalizarReporteUi | null = null;
  copiado = false;

  ollamaListo = false;
  ollamaMensaje = 'Comprobando motor local…';

  get rutaDocumentos(): (string | number)[] {
    if (this.esSuperAdmin) {
      return ['/super-admin', 'documentos'];
    }
    if (this.esUsuario) {
      return ['/usuario', 'documentos'];
    }
    return ['/admin-empresa', 'documentos'];
  }

  get puedeAnalizar(): boolean {
    return !!this.empresaId && (!!this.texto.trim() || !!this.archivo) && !this.analizando;
  }

  ngOnInit(): void {
    this.cargarEmpresa();
    this.cargarOllama();
  }

  onArchivo(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.archivo = file;
    this.archivoNombre = file?.name || '';
  }

  quitarArchivo(input?: HTMLInputElement): void {
    this.archivo = null;
    this.archivoNombre = '';
    if (input) {
      input.value = '';
    }
  }

  analizar(): void {
    if (!this.puedeAnalizar || !this.empresaId) {
      return;
    }
    this.analizando = true;
    this.error = null;
    this.resultado = null;
    this.copiado = false;

    const body = new FormData();
    body.append('empresaId', String(this.empresaId));
    if (this.texto.trim()) {
      body.append('texto', this.texto.trim());
    }
    if (this.archivo) {
      body.append('archivo', this.archivo, this.archivo.name);
    }

    this.http
      .post<AnalizarReporteUi>(`${environment.apiUrl}/ia/analizar-reporte`, body)
      .pipe(timeout(190000))
      .subscribe({
        next: (res) => {
          this.resultado = {
            ...res,
            coincidencias: res.coincidencias || [],
            huecos: res.huecos || [],
            textoPropuesto: res.textoPropuesto || '',
          };
          this.analizando = false;
          this.ollamaListo = !!res.ollamaListo;
          if (res.avisoMotor) {
            this.ollamaMensaje = res.avisoMotor;
          }
        },
        error: (err: HttpErrorResponse) => {
          this.analizando = false;
          this.error =
            err?.error?.mensaje ||
            (err.status === 0
              ? 'No hay conexión con el servidor.'
              : 'No se pudo analizar el informe. Revise el archivo y que el backend esté en marcha.');
        },
      });
  }

  copiarPropuesto(): void {
    const t = this.resultado?.textoPropuesto;
    if (!t) {
      return;
    }
    navigator.clipboard.writeText(t).then(() => {
      this.copiado = true;
      setTimeout(() => (this.copiado = false), 2200);
    });
  }

  private cargarOllama(): void {
    this.http.get<any>(`${environment.apiUrl}/ia/ollama`).subscribe({
      next: (st) => {
        this.ollamaListo = !!st.listo;
        this.ollamaMensaje = st.mensaje || '';
      },
      error: () => {
        this.ollamaListo = false;
        this.ollamaMensaje = 'No se pudo consultar Ollama. El cruce con PDF igual funciona.';
      },
    });
  }

  private cargarEmpresa(): void {
    const user = this.auth.getUserData();
    if (!this.esSuperAdmin) {
      this.empresaId = user?.empresaId ?? null;
      this.empresaNombre = user?.empresaNombre || '';
      this.loadingEmpresa = false;
      return;
    }
    this.empresaService.listar(0, 50).subscribe({
      next: (response) => {
        const empresas: Empresa[] = response.content || [];
        this.empresa = this.elegirEmpresaUnica(empresas);
        this.empresaId = this.empresa?.id ?? null;
        this.empresaNombre = this.empresa?.nombre || '';
        this.loadingEmpresa = false;
      },
      error: () => {
        this.loadingEmpresa = false;
        this.error = 'No se pudo cargar la empresa.';
      },
    });
  }

  private elegirEmpresaUnica(empresas: Empresa[]): Empresa | null {
    if (!empresas.length) {
      return null;
    }
    const activas = empresas.filter((e) => e.activa);
    const pool = activas.length ? activas : empresas;
    const saved = Number(localStorage.getItem(EMPRESA_FISCALIZACION_KEY));
    const encontrada = pool.find((e) => e.id === saved);
    const elegida = encontrada ?? pool[0];
    localStorage.setItem(EMPRESA_FISCALIZACION_KEY, String(elegida.id));
    return elegida;
  }
}
