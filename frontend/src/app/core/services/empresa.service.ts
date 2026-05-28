import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Empresa, EmpresaRequest } from '../models/empresa.model';

@Injectable({
  providedIn: 'root',
})
export class EmpresaService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  listar(page: number = 0, size: number = 10): Observable<any> {
    return this.http.get(`${this.apiUrl}/empresas?page=${page}&size=${size}`);
  }

  obtener(id: number): Observable<Empresa> {
    return this.http.get<Empresa>(`${this.apiUrl}/empresas/${id}`);
  }

  crear(request: EmpresaRequest): Observable<Empresa> {
    return this.http.post<Empresa>(`${this.apiUrl}/empresas`, request);
  }

  actualizar(id: number, request: EmpresaRequest): Observable<Empresa> {
    return this.http.put<Empresa>(`${this.apiUrl}/empresas/${id}`, request);
  }

  toggleActivo(id: number): Observable<Empresa> {
    return this.http.patch<Empresa>(`${this.apiUrl}/empresas/${id}/toggle-activo`, {});
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/empresas/${id}`);
  }
}
