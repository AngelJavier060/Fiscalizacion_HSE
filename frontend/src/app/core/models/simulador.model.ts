export interface SsoTema {
  id: number;
  nombre: string;
  activo: boolean;
  totalPreguntas: number;
  preguntasActivas: number;
  cumplidasVuelta?: number;
}

export interface SsoOpcionAdmin {
  id?: number;
  texto: string;
  esCorrecta: boolean;
  argumento?: string;
}

export interface SsoPreguntaAdmin {
  id: number;
  temaId: number;
  temaNombre: string;
  enunciado: string;
  explicacion?: string;
  fuente?: string;
  sustentoTexto?: string;
  imagen?: string;
  sustentoNombre?: string;
  sustentoTipo?: string;
  tieneSustento?: boolean;
  activo: boolean;
  fechaCreacion?: string;
  fechaEdicion?: string;
  usadaEnIntentos: boolean;
  opciones: SsoOpcionAdmin[];
}

export interface SsoEstudioPregunta {
  id: number;
  temaId: number;
  temaNombre: string;
  enunciado: string;
  explicacion?: string;
  fuente?: string;
  sustentoTexto?: string;
  tieneSustento?: boolean;
  sustentoNombre?: string;
  sustentoTipo?: string;
}

export interface SsoPreguntaRequest {
  temaId: number;
  enunciado: string;
  explicacion?: string;
  fuente?: string;
  sustentoTexto?: string;
  activo?: boolean;
  opciones: SsoOpcionAdmin[];
}

export interface SsoImportFila {
  fila: number;
  id: number | null;
  accion: 'crear' | 'actualizar' | 'sin_cambios' | 'error';
  ok: boolean;
  errores: string[];
  temaNuevo: boolean;
  posibleDuplicado: boolean;
  tema: string;
  enunciado: string;
  opcionA: string;
  opcionB: string;
  opcionC: string;
  opcionD: string;
  opcionE: string;
  correcta: string;
  explicacion: string;
  fuente: string;
  activo: boolean | null;
}

export interface SsoImportPreview {
  totalFilas: number;
  validas: number;
  conError: number;
  temasNuevos: number;
  posiblesDuplicados: number;
  nuevas: number;
  actualizadas: number;
  sinCambios: number;
  ausentes: number;
  filas: SsoImportFila[];
}

export interface SsoExamenOpcion {
  id: number;
  texto: string;
}

export interface SsoExamenPregunta {
  id: number;
  orden: number;
  enunciado: string;
  temaNombre?: string;
  opcionElegidaId: number | null;
  opciones: SsoExamenOpcion[];
}

export interface SsoConfiguracion {
  tiempoExamenMin: number;
  notaMinima: number;
  preguntasPorBanco?: number;
  tiemposPermitidos: number[];
  notasPermitidas?: number[];
  bancosPermitidos?: number[];
}

export interface SsoCobertura {
  totalPreguntasActivas: number;
  preguntasNuncaSalieron: number;
  preguntasEnAlMenosUnExamen: number;
  preguntasRepetidas?: number;
  notaAvance: number;
  preguntasPorBanco: number;
  tiempoExamenMin?: number;
  usuarios: {
    id: number;
    nombre: string;
    email: string;
    empresaNombre: string;
    vuelta: number;
    cumplidas: number;
    total: number;
    porcentaje: number;
    debeRepetir: boolean;
    ultimaNota?: number;
    ultimoAlcance?: string;
  }[];
  preguntasSinUso: { id: number; enunciado: string; temaNombre: string; vecesSalio?: number }[];
}

export interface SsoEstadisticaOpcion {
  id: number;
  texto: string;
  correcta: boolean;
  votos: number;
  pct: number;
}

export interface SsoEstadisticaPregunta {
  id: number;
  enunciado: string;
  temaId: number | null;
  temaNombre: string;
  intentos: number;
  aciertos: number;
  aciertoPct: number;
  vecesSalio: number;
  nuncaSalio: boolean;
  repetida: boolean;
  distractorTexto?: string;
  distractorPct: number;
  opciones: SsoEstadisticaOpcion[];
}

export interface SsoEstadisticas {
  dias: number;
  tasaGlobalAcierto: number;
  respuestasContadas: number;
  preguntasActivas: number;
  preguntasEvaluadas: number;
  preguntasNuncaSalieron: number;
  preguntasRepetidas: number;
  preguntasCriticas: number;
  coberturaPct: number;
  masReprobadaId?: number;
  masReprobadaEnunciado?: string;
  masReprobadaTema?: string;
  masReprobadaAcierto: number;
  masReprobadaIntentos: number;
  faciles: number;
  intermedias: number;
  criticas: number;
  porTema: { id: number | null; nombre: string; intentos: number; aciertoPct: number }[];
  preguntas: SsoEstadisticaPregunta[];
}

export interface SsoExamen {
  id: number;
  modo: string;
  temaId: number | null;
  temaNombre: string;
  estado: string;
  fechaInicio: string;
  tiempoLimiteMin: number;
  segundosRestantes?: number;
  totalPreguntas: number;
  respondidas: number;
  bancoInsuficiente: boolean;
  aviso?: string;
  preguntas: SsoExamenPregunta[];
}

export interface SsoResultado {
  id: number;
  modo: string;
  temaId?: number | null;
  temaNombre: string;
  estado: string;
  fechaInicio: string;
  fechaFin?: string;
  totalPreguntas: number;
  correctas: number;
  nota: number;
  aprobado: boolean;
  aciertosPorTema: { tema: string; total: number; correctas: number }[];
  revision: SsoRevision[];
}

export interface SsoRevision {
  preguntaId: number;
  orden: number;
  enunciado: string;
  explicacion?: string;
  fuente?: string;
  sustentoTexto?: string;
  tieneSustento?: boolean;
  sustentoNombre?: string;
  sustentoTipo?: string;
  temaNombre: string;
  opcionElegidaId: number | null;
  opcionCorrectaId: number | null;
  esCorrecta: boolean | null;
  opciones: { id: number; texto: string; correcta: boolean; elegida: boolean; argumento?: string }[];
}

export interface SsoHistorialItem {
  id: number;
  fechaInicio: string;
  fechaFin?: string;
  modo: string;
  temaNombre: string;
  nota?: number;
  aprobado?: boolean;
  estado: string;
  totalPreguntas: number;
  correctas?: number;
}

export interface SsoInicio {
  vueltaActual: number;
  preguntasVistasVuelta: number;
  preguntasBanco: number;
  falladasPendientes: number;
  ultimaNota?: number;
  ultimoAprobado?: boolean;
  ultimaFecha?: string;
  intentoPendienteId?: number;
  intentoPendienteModo?: string;
  temas: SsoTema[];
  notaMinima: number;
  tiempo20Min: number;
  tiempo40Min: number;
  tiempoExamenMin?: number;
  preguntasPorBanco?: number;
  bloqueRepetir?: boolean;
  bloqueRepetirNota?: number;
  bloqueRepetirTotal?: number;
  bloqueRepetirTemaId?: number | null;
  bloqueRepetirTemaNombre?: string;
  historial: SsoHistorialItem[];
}
