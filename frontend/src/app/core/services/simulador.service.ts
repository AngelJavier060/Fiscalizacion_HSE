import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  SsoCobertura,
  SsoConfiguracion,
  SsoEstadisticas,
  SsoEstudioPregunta,
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

  guardarConfiguracion(body: {
    tiempoExamenMin: number;
    notaMinima?: number;
    preguntasPorBanco?: number;
  }): Observable<SsoConfiguracion> {
    return this.http.put<SsoConfiguracion>(`${this.admin}/configuracion`, body);
  }

  cobertura(): Observable<SsoCobertura> {
    return this.http.get<SsoCobertura>(`${this.admin}/cobertura`);
  }

  estadisticas(dias = 0): Observable<SsoEstadisticas> {
    return this.http.get<SsoEstadisticas>(`${this.admin}/estadisticas`, {
      params: { dias: String(dias) },
    });
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

  subirSustento(id: number, file: File): Observable<SsoPreguntaAdmin> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<SsoPreguntaAdmin>(`${this.admin}/preguntas/${id}/sustento`, fd);
  }

  eliminarSustento(id: number): Observable<SsoPreguntaAdmin> {
    return this.http.delete<SsoPreguntaAdmin>(`${this.admin}/preguntas/${id}/sustento`);
  }

  descargarSustento(id: number): Observable<Blob> {
    return this.http.get(`${this.api}/preguntas/${id}/sustento`, { responseType: 'blob' });
  }

  /** Emite eventos de progreso de la subida y, al final, la respuesta. */
  previewImport(file: File): Observable<HttpEvent<SsoImportPreview>> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<SsoImportPreview>(`${this.admin}/preguntas/importar/preview`, fd, {
      reportProgress: true,
      observe: 'events',
    });
  }

  confirmarImport(preview: SsoImportPreview, desactivarAusentes = false): Observable<HttpEvent<SsoImportPreview>> {
    return this.http.post<SsoImportPreview>(`${this.admin}/preguntas/importar`, preview, {
      params: { desactivarAusentes: String(desactivarAusentes) },
      reportProgress: true,
      observe: 'events',
    });
  }

  descargarPlantilla(): Observable<Blob> {
    return this.http.get(`${this.admin}/preguntas/plantilla`, { responseType: 'blob' });
  }

  exportarPreguntas(): Observable<Blob> {
    return this.http.get(`${this.admin}/preguntas/exportar`, { responseType: 'blob' });
  }

  inicio(): Observable<SsoInicio> {
    return this.http.get<SsoInicio>(`${this.api}/inicio`);
  }

  iniciarExamen(
    modo: string,
    temaId?: number | null,
    preguntasPorBanco?: number | null,
    nuevoSet = true,
    intentoOrigenId?: number | null,
  ): Observable<SsoExamen> {
    return this.http.post<SsoExamen>(`${this.api}/examenes`, {
      modo,
      temaId: temaId ?? null,
      preguntasPorBanco: preguntasPorBanco ?? null,
      nuevoSet,
      intentoOrigenId: intentoOrigenId ?? null,
    });
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

  listarTemasEstudio(): Observable<SsoTema[]> {
    return this.http.get<SsoTema[]>(`${this.api}/estudio/temas`);
  }

  listarEstudio(q?: string, temaId?: number | null): Observable<SsoEstudioPregunta[]> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    if (temaId != null) params = params.set('temaId', String(temaId));
    return this.http.get<SsoEstudioPregunta[]>(`${this.api}/estudio/preguntas`, { params });
  }
}
