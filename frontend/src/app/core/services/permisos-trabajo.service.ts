import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PermisoTrabajoResponse {
  id: string;
  title: string;
  area: string;
  responsible: string;
  startDate: string;
  endDate: string;
  imagePath?: string;
  criticalTask?: string;
  description?: string;
  emisor?: string;
  ejecutante?: string;
  empresaEjecutante?: string;
  nota?: string;
  startTime?: string;
  endTime?: string;
  activo: boolean;
  empresaId: number;
  empresaNombre?: string;
  creadoPorId?: number;
  creadoPorNombre?: string;
  createdAt?: string;
  updatedAt?: string;
  status: 'active' | 'warning' | 'expired';
  remainingDays: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ContadoresResponse {
  total: number;
  vigentes: number;
  expirados: number;
}

@Injectable({ providedIn: 'root' })
export class PermisosTrabajoService {
  private readonly apiUrl = `${environment.apiUrl}/permisos-trabajo`;

  constructor(private http: HttpClient) {}

  /** Lista paginada de permisos de una empresa */
  listarPorEmpresa(empresaId: number, page = 0, size = 50): Observable<PageResponse<PermisoTrabajoResponse>> {
    return this.http.get<PageResponse<PermisoTrabajoResponse>>(
      `${this.apiUrl}/empresa/${empresaId}`,
      { params: new HttpParams().set('page', page).set('size', size) }
    );
  }

  /** Lista todos los permisos de una empresa (sin paginación) */
  listarTodos(empresaId: number): Observable<PermisoTrabajoResponse[]> {
    return this.http.get<PermisoTrabajoResponse[]>(`${this.apiUrl}/empresa/${empresaId}/todos`);
  }

  /** Obtiene un permiso por ID */
  obtener(id: string): Observable<PermisoTrabajoResponse> {
    return this.http.get<PermisoTrabajoResponse>(`${this.apiUrl}/${id}`);
  }

  /** Crea un nuevo permiso */
  crear(data: any): Observable<PermisoTrabajoResponse> {
    return this.http.post<PermisoTrabajoResponse>(this.apiUrl, data);
  }

  /** Actualiza un permiso existente */
  actualizar(id: string, data: any): Observable<PermisoTrabajoResponse> {
    return this.http.put<PermisoTrabajoResponse>(`${this.apiUrl}/${id}`, data);
  }

  /** Elimina un permiso */
  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Contadores resumidos */
  contar(empresaId: number): Observable<ContadoresResponse> {
    return this.http.get<ContadoresResponse>(`${this.apiUrl}/empresa/${empresaId}/contar`);
  }

  /** Sube un archivo (PDF/imagen/doc) a un permiso existente */
  subirArchivo(permisoId: string, archivo: File): Observable<string> {
    const form = new FormData();
    form.append('archivo', archivo, archivo.name);
    return this.http.post(`${this.apiUrl}/${permisoId}/archivo`, form, { responseType: 'text' });
  }

  /** Descarga un archivo del permiso como Blob (necesita auth headers) */
  descargarArchivo(permisoId: string, nombreArchivo: string): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/${permisoId}/archivo/${encodeURIComponent(nombreArchivo)}`,
      { responseType: 'blob' }
    );
  }
}
