export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: 'SUPER_ADMIN' | 'ADMIN_EMPRESA' | 'USUARIO';
  activo: boolean;
  empresaId: number | null;
  empresaNombre: string | null;
  ultimoAcceso: string | null;
  createdAt: string;
  /** Vigencia de acceso (yyyy-MM-dd) o null */
  accesoDesde?: string | null;
  accesoHasta?: string | null;
  accesosPersonalizados?: boolean;
  /** Códigos de módulos efectivos (solo en /me/perfil) */
  modulos?: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tipoToken: string;
  id: number;
  nombre: string;
  email: string;
  rol: 'SUPER_ADMIN' | 'ADMIN_EMPRESA' | 'USUARIO';
  empresaId: number | null;
  empresaNombre: string | null;
  /** Códigos de módulos habilitados para este usuario */
  modulos?: string[];
}

export interface UsuarioRequest {
  nombre: string;
  email: string;
  /** Obligatoria al crear; omitir o vacío al editar si no cambia la contraseña. */
  password?: string;
  rol?: string;
  empresaId?: number | null;
  /** Vigencia de acceso (yyyy-MM-dd) o null */
  accesoDesde?: string | null;
  accesoHasta?: string | null;
}
