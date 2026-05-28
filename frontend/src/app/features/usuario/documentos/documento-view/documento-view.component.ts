import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../../core/services/auth.service';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-documento-view',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './documento-view.component.html',
  styleUrls: ['./documento-view.component.scss'],
})
export class DocumentoViewComponent implements OnInit {
  documentos: any[] = [];
  puntosVisibles: { [key: number]: boolean } = {};
  user: any;
  loading = true;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    const empresaId = this.user?.empresaId;
    if (empresaId) {
      this.cargarDocumentos(empresaId);
    }
  }

  cargarDocumentos(empresaId: number): void {
    this.http.get(`${environment.apiUrl}/documentos/empresa/${empresaId}`)
      .subscribe({
        next: (res: any) => {
          this.documentos = res.content || [];
          this.loading = false;
          // Cargar puntos para cada documento
          this.documentos.forEach(doc => this.cargarPuntos(doc.id));
        },
        error: () => this.loading = false,
      });
  }

  cargarPuntos(documentoId: number): void {
    this.http.get(`${environment.apiUrl}/puntos-clave/documento/${documentoId}`)
      .subscribe({
        next: (puntos: any) => {
          const doc = this.documentos.find(d => d.id === documentoId);
          if (doc) doc.puntos = puntos;
        },
      });
  }

  togglePuntos(docId: number): void {
    this.puntosVisibles[docId] = !this.puntosVisibles[docId];
  }

  getIconoIdioma(idioma: string): string {
    switch(idioma) {
      case 'es': return '🇪🇸';
      case 'en': return '🇬🇧';
      default: return '🌐';
    }
  }

  logout(): void {
    this.authService.logout();
  }
}
