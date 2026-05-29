import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs/operators';
import { AuthService } from '../../../../core/services/auth.service';
import { environment } from '../../../../../environments/environment';
import {
  buildDocumentoContext,
  documentNavigationLinks,
  type DocumentoLinks,
} from '../../../../core/helpers/documento-routing.helper';

@Component({
  selector: 'app-documento-subir',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule],
  templateUrl: './documento-subir.component.html',
  styleUrls: ['./documento-subir.component.scss'],
})
export class DocumentoSubirComponent implements OnInit {
  /** Límite alineado con Spring multipart (application.yml). */
  private static readonly MAX_ARCHIVO_BYTES = 100 * 1024 * 1024;

  /** Solo espera la subida del archivo; extracción e IA corren en segundo plano en el servidor. */
  private static readonly UPLOAD_TIMEOUT_MS = 3 * 60 * 1000;

  form: FormGroup;
  archivoSeleccionado: File | null = null;
  subiendo = false;
  errorMessage = '';
  user: any;
  empresaId: number | null = null;
  nav: DocumentoLinks = documentNavigationLinks({
    empresaId: null,
    esRutaSuperAdmin: false,
  });
  esRutaSuperAdmin = false;

  get inicialesUsuario(): string {
    const nombre = this.user?.nombre?.trim() || 'A';
    const partes = nombre.split(/\s+/);
    if (partes.length >= 2) {
      return (partes[0][0] + partes[1][0]).toUpperCase();
    }
    return nombre.substring(0, 2).toUpperCase();
  }

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {
    this.form = this.fb.group({
      titulo: ['', Validators.required],
      descripcion: [''],
    });
  }

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    const ctx = buildDocumentoContext(this.route, this.authService);
    this.esRutaSuperAdmin = ctx.esRutaSuperAdmin;
    this.nav = documentNavigationLinks(ctx);
    this.empresaId = ctx.empresaId;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.procesarArchivo(file);
    }
  }

  onArchivoSeleccionado(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.procesarArchivo(file);
    }
  }

  private procesarArchivo(file: File): void {
    const esPdf = file.type === 'application/pdf'
      || file.type === 'application/octet-stream'
      || file.name.toLowerCase().endsWith('.pdf');
    if (!esPdf) {
      this.errorMessage = 'Solo se permiten archivos PDF';
      this.archivoSeleccionado = null;
      return;
    }
    if (file.size > DocumentoSubirComponent.MAX_ARCHIVO_BYTES) {
      this.errorMessage = 'El archivo no puede superar los 100 MB';
      this.archivoSeleccionado = null;
      return;
    }
    this.archivoSeleccionado = file;
    this.errorMessage = '';

    if (!this.form.get('titulo')?.value) {
      this.form.patchValue({
        titulo: file.name.replace('.pdf', '').replace(/_/g, ' '),
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid || !this.archivoSeleccionado || !this.empresaId) return;

    this.subiendo = true;
    this.errorMessage = '';

    const formData = new FormData();
    formData.append('titulo', this.form.get('titulo')?.value);
    formData.append('descripcion', this.form.get('descripcion')?.value || '');
    formData.append('empresaId', this.empresaId.toString());
    formData.append('archivo', this.archivoSeleccionado);

    this.http
      .post(`${environment.apiUrl}/documentos`, formData)
      .pipe(timeout(DocumentoSubirComponent.UPLOAD_TIMEOUT_MS))
      .subscribe({
      next: (doc: any) => {
        this.router.navigate(this.nav.detalle(doc.id));
      },
      error: (err) => {
        this.errorMessage = err.error?.mensaje || 'Error al subir el documento';
        this.subiendo = false;
      },
      complete: () => {
        this.subiendo = false;
      },
    });
  }

  removerArchivo(): void {
    this.archivoSeleccionado = null;
    this.errorMessage = '';
  }

  logout(): void {
    this.authService.logout();
  }
}
