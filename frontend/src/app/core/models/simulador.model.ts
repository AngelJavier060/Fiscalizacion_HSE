export interface SsoTema {
  id: number;
  nombre: string;
  activo: boolean;
  totalPreguntas: number;
  preguntasActivas: number;
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
  imagen?: string;
  activo: boolean;
  fechaCreacion?: string;
  fechaEdicion?: string;
  usadaEnIntentos: boolean;
  opciones: SsoOpcionAdmin[];
}

export interface SsoPreguntaRequest {
  temaId: number;
  enunciado: string;
  explicacion?: string;
  fuente?: string;
  activo?: boolean;
  opciones: SsoOpcionAdmin[];
}

export interface SsoImportFila {
  fila: number;
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
}

export interface SsoImportPreview {
  totalFilas: number;
  validas: number;
  conError: number;
  temasNuevos: number;
  posiblesDuplicados: number;
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
  tiemposPermitidos: number[];
}

export interface SsoExamen {
  id: number;
  modo: string;
  temaId: number | null;
  temaNombre: string;
  estado: string;
  fechaInicio: string;
  tiempoLimiteMin: number;
  totalPreguntas: number;
  respondidas: number;
  bancoInsuficiente: boolean;
  aviso?: string;
  preguntas: SsoExamenPregunta[];
}

export interface SsoResultado {
  id: number;
  modo: string;
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
  historial: SsoHistorialItem[];
}
