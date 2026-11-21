import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AlertaIncidente {
  id: number;
  codigo: string;
  titulo: string;
  detalle: string;
  fecha: string;
  ambito: string;
  area: string;
  unidad: string;
  dueno: string;
  hora: string;
  empresa: string;
  impacto: string;
  accidenteGrave: boolean;
  iap: boolean;
  eorpv: string;
  archivoNombre: string | null;
  tieneArchivo: boolean;
  prioridad: 'alta' | 'media';
  pdf: string;
}

export interface AlertaIncidentePayload {
  codigo: string;
  titulo: string;
  unidad: string;
  ambito: string;
  fecha: string;
  area: string;
  dueno: string;
  hora: string;
  empresa: string;
  impacto: string;
  accidenteGrave: boolean;
  iap: boolean;
  eorpv: string;
}

@Injectable({ providedIn: 'root' })
export class AlertasIncidentesService {
  private readonly apiUrl = `${environment.apiUrl}/alertas-incidentes`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Omit<AlertaIncidente, 'pdf'>[]> {
    return this.http.get<Omit<AlertaIncidente, 'pdf'>[]>(this.apiUrl);
  }

  crear(datos: AlertaIncidentePayload, archivo: File | null): Observable<Omit<AlertaIncidente, 'pdf'>> {
    const fd = new FormData();
    fd.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }), 'datos.json');
    if (archivo) fd.append('archivo', archivo, archivo.name);
    return this.http.post<Omit<AlertaIncidente, 'pdf'>>(this.apiUrl, fd);
  }

  archivo(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/archivo`, { responseType: 'blob' });
  }
}
