import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  SsoConfiguracion,
  SsoExamen,
  SsoImportPreview,
  SsoInicio,
  SsoHistorialItem,
  SsoPreguntaAdmin,
  SsoPreguntaRequest,
  SsoResultado,
  SsoTema,
} from '../models/simulador.model';

@Injectable({ providedIn: 'root' })
export class SimuladorService {
  private admin = `${environment.apiUrl}/simulador/admin`;
  private api = `${environment.apiUrl}/simulador`;

  constructor(private http: HttpClient) {}

  obtenerConfiguracion(): Observable<SsoConfiguracion> {
    return this.http.get<SsoConfiguracion>(`${this.admin}/configuracion`);
  }

  guardarConfiguracion(tiempoExamenMin: number): Observable<SsoConfiguracion> {
    return this.http.put<SsoConfiguracion>(`${this.admin}/configuracion`, { tiempoExamenMin });
  }

  listarTemas(): Observable<SsoTema[]> {
    return this.http.get<SsoTema[]>(`${this.admin}/temas`);
  }

  crearTema(nombre: string, activo = true): Observable<SsoTema> {
    return this.http.post<SsoTema>(`${this.admin}/temas`, { nombre, activo });
  }

  actualizarTema(id: number, nombre: string, activo: boolean): Observable<SsoTema> {
    return this.http.put<SsoTema>(`${this.admin}/temas/${id}`, { nombre, activo });
  }

  listarPreguntas(q?: string, temaId?: number | null, activo?: boolean | null): Observable<SsoPreguntaAdmin[]> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    if (temaId != null) params = params.set('temaId', String(temaId));
    if (activo != null) params = params.set('activo', String(activo));
    return this.http.get<SsoPreguntaAdmin[]>(`${this.admin}/preguntas`, { params });
  }

  obtenerPregunta(id: number): Observable<SsoPreguntaAdmin> {
    return this.http.get<SsoPreguntaAdmin>(`${this.admin}/preguntas/${id}`);
  }

  crearPregunta(body: SsoPreguntaRequest): Observable<SsoPreguntaAdmin> {
    return this.http.post<SsoPreguntaAdmin>(`${this.admin}/preguntas`, body);
  }

  actualizarPregunta(id: number, body: SsoPreguntaRequest): Observable<SsoPreguntaAdmin> {
    return this.http.put<SsoPreguntaAdmin>(`${this.admin}/preguntas/${id}`, body);
  }

  togglePregunta(id: number): Observable<SsoPreguntaAdmin> {
    return this.http.patch<SsoPreguntaAdmin>(`${this.admin}/preguntas/${id}/activo`, {});
  }

  previewImport(file: File): Observable<SsoImportPreview> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<SsoImportPreview>(`${this.admin}/preguntas/importar/preview`, fd);
  }

  confirmarImport(preview: SsoImportPreview): Observable<SsoImportPreview> {
    return this.http.post<SsoImportPreview>(`${this.admin}/preguntas/importar`, preview);
  }

  inicio(): Observable<SsoInicio> {
    return this.http.get<SsoInicio>(`${this.api}/inicio`);
  }

  iniciarExamen(modo: string, temaId?: number | null): Observable<SsoExamen> {
    return this.http.post<SsoExamen>(`${this.api}/examenes`, { modo, temaId: temaId ?? null });
  }

  obtenerExamen(id: number): Observable<SsoExamen> {
    return this.http.get<SsoExamen>(`${this.api}/examenes/${id}`);
  }

  responder(id: number, preguntaId: number, opcionId: number | null): Observable<SsoExamen> {
    return this.http.put<SsoExamen>(`${this.api}/examenes/${id}/respuestas`, { preguntaId, opcionId });
  }

  finalizar(id: number): Observable<SsoResultado> {
    return this.http.post<SsoResultado>(`${this.api}/examenes/${id}/finalizar`, {});
  }

  resultado(id: number): Observable<SsoResultado> {
    return this.http.get<SsoResultado>(`${this.api}/examenes/${id}/resultado`);
  }

  historial(): Observable<SsoHistorialItem[]> {
    return this.http.get<SsoHistorialItem[]>(`${this.api}/historial`);
  }
}
