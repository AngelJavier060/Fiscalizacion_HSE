export interface Empresa {
  id: number;
  nombre: string;
  ruc: string;
  direccion: string;
  email: string;
  telefono: string;
  activa: boolean;
  cantidadUsuarios: number;
  createdAt: string;
  updatedAt: string;
  /** Vigencia del servicio (yyyy-MM-dd) o null */
  vigenciaDesde?: string | null;
  vigenciaHasta?: string | null;
}

export interface EmpresaRequest {
  nombre: string;
  ruc?: string;
  direccion?: string;
  email?: string;
  telefono?: string;
  /** Vigencia del servicio (yyyy-MM-dd) o null */
  vigenciaDesde?: string | null;
  vigenciaHasta?: string | null;
}
