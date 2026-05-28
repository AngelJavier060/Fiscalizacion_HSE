import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

export type RolId = 'SUPER_ADMIN' | 'ADMIN_EMPRESA' | 'USUARIO';

/** Forma de la matriz tal como la entrega/recibe el backend */
interface RolModuloDto {
  rol: string;
  modulo: string;
  habilitado: boolean;
}

interface ModuloFlagDto {
  modulo: string;
  habilitado: boolean;
}

interface AccesoUsuarioDto {
  modo: string;
  modulos: ModuloFlagDto[];
}

export interface RolColumna {
  id: RolId;
  label: string;
  descripcion: string;
  color: string;
  bloqueado?: boolean;
}

export interface ModuloItem {
  id: string;
  label: string;
  icon: string;
  descripcion: string;
}

export interface ModuloGrupo {
  grupo: string;
  modulos: ModuloItem[];
}

/** matriz[moduloId][rolId] = boolean */
export type Matriz = Record<string, Record<RolId, boolean>>;

/** Modo de acceso de un usuario concreto */
export type ModoAcceso = 'rol' | 'custom';

export interface AccesoUsuario {
  modo: ModoAcceso;
  /** solo se usa cuando modo = 'custom' */
  modulos: Record<string, boolean>;
}

/**
 * Servicio central de permisos (conectado al backend).
 *
 * - Catálogo de roles y módulos (estático, refleja el seed de la BD).
 * - Matriz rol × módulo: `/api/permisos/matriz`.
 * - Accesos por usuario individual: `/api/permisos/usuario/{id}`.
 */
@Injectable({ providedIn: 'root' })
export class PermisosService {
  private readonly apiUrl = `${environment.apiUrl}/permisos`;

  constructor(private http: HttpClient) {}

  readonly roles: RolColumna[] = [
    { id: 'SUPER_ADMIN', label: 'Super Admin', descripcion: 'Acceso total', color: '#6d4aff', bloqueado: true },
    { id: 'ADMIN_EMPRESA', label: 'Admin Empresa', descripcion: 'Gestiona su empresa', color: '#006b5b' },
    { id: 'USUARIO', label: 'Usuario', descripcion: 'Acceso operativo', color: '#003ec7' },
  ];

  readonly grupos: ModuloGrupo[] = [
    {
      grupo: 'Administración',
      modulos: [
        { id: 'usuarios', label: 'Usuarios', icon: 'group', descripcion: 'Gestión de cuentas y roles' },
        { id: 'empresas', label: 'Empresas', icon: 'business', descripcion: 'Alta y gestión de empresas' },
        { id: 'auditoria', label: 'Auditoría', icon: 'history', descripcion: 'Registro de acciones del sistema' },
      ],
    },
    {
      grupo: 'Gestión HSE',
      modulos: [
        { id: 'documentos', label: 'Documentos', icon: 'description', descripcion: 'Documentos normativos PDF' },
        { id: 'puntos_clave', label: 'Puntos clave', icon: 'star', descripcion: 'Controles y extractos clave' },
        { id: 'recordatorios', label: 'Recordatorios', icon: 'alarm', descripcion: 'Avisos programados' },
        { id: 'actividades', label: 'Actividades diarias', icon: 'event_note', descripcion: 'Registro diario (próximamente)' },
        { id: 'controles', label: 'Controles críticos', icon: 'security', descripcion: 'Controles críticos (próximamente)' },
        { id: 'permisos_hse', label: 'Permisos de trabajo', icon: 'lock_person', descripcion: 'Permisos HSE (próximamente)' },
        { id: 'conocimientos', label: 'Conocimientos', icon: 'menu_book', descripcion: 'Base de conocimiento (próximamente)' },
      ],
    },
    {
      grupo: 'Comunicación e Inteligencia',
      modulos: [
        { id: 'notificaciones', label: 'Notificaciones', icon: 'notifications', descripcion: 'Bandeja de avisos' },
        { id: 'ia', label: 'FISCALIZA-AI', icon: 'psychology', descripcion: 'Asistente y búsqueda con IA' },
      ],
    },
  ];

  /** [SUPER_ADMIN, ADMIN_EMPRESA, USUARIO] por módulo */
  private readonly defaults: Record<string, [boolean, boolean, boolean]> = {
    usuarios: [true, true, false],
    empresas: [true, false, false],
    auditoria: [true, false, false],
    documentos: [true, true, true],
    puntos_clave: [true, true, true],
    recordatorios: [true, true, true],
    actividades: [true, false, false],
    controles: [true, false, false],
    permisos_hse: [true, false, false],
    conocimientos: [true, false, false],
    notificaciones: [true, true, true],
    ia: [true, true, true],
  };

  get totalModulos(): number {
    return this.grupos.reduce((acc, g) => acc + g.modulos.length, 0);
  }

  get modulosPlanos(): ModuloItem[] {
    return this.grupos.flatMap((g) => g.modulos);
  }

  // ── Matriz por rol ──────────────────────────────────────────────────
  matrizPorDefecto(): Matriz {
    const m: Matriz = {};
    for (const [moduloId, vals] of Object.entries(this.defaults)) {
      m[moduloId] = { SUPER_ADMIN: vals[0], ADMIN_EMPRESA: vals[1], USUARIO: vals[2] };
    }
    return m;
  }

  /** Carga la matriz rol × módulo desde el backend. */
  getMatriz(): Observable<Matriz> {
    return this.http.get<RolModuloDto[]>(`${this.apiUrl}/matriz`).pipe(
      map((filas) => {
        const base = this.matrizPorDefecto();
        for (const f of filas) {
          const rol = f.rol as RolId;
          if (!base[f.modulo]) {
            base[f.modulo] = { SUPER_ADMIN: false, ADMIN_EMPRESA: false, USUARIO: false };
          }
          base[f.modulo][rol] = f.habilitado;
        }
        return base;
      })
    );
  }

  /** Guarda la matriz completa en el backend. */
  guardarMatriz(m: Matriz): Observable<void> {
    const filas: RolModuloDto[] = [];
    for (const mod of this.modulosPlanos) {
      for (const rol of this.roles) {
        filas.push({
          rol: rol.id,
          modulo: mod.id,
          habilitado: m[mod.id]?.[rol.id] ?? false,
        });
      }
    }
    return this.http.put<void>(`${this.apiUrl}/matriz`, filas);
  }

  /** Mapa moduloId → habilitado, según una matriz ya cargada y el rol indicado. */
  modulosDeRol(matriz: Matriz, rol: RolId): Record<string, boolean> {
    const out: Record<string, boolean> = {};
    for (const m of this.modulosPlanos) {
      out[m.id] = matriz[m.id]?.[rol] ?? false;
    }
    return out;
  }

  // ── Acceso por usuario ──────────────────────────────────────────────
  getAcceso(userId: number | string): Observable<AccesoUsuario> {
    return this.http.get<AccesoUsuarioDto>(`${this.apiUrl}/usuario/${userId}`).pipe(
      map((dto) => {
        const modulos: Record<string, boolean> = {};
        for (const f of dto.modulos || []) {
          modulos[f.modulo] = f.habilitado;
        }
        return { modo: dto.modo === 'custom' ? 'custom' : 'rol', modulos };
      })
    );
  }

  guardarAcceso(userId: number | string, acceso: AccesoUsuario): Observable<void> {
    const dto: AccesoUsuarioDto = {
      modo: acceso.modo,
      modulos:
        acceso.modo === 'custom'
          ? this.modulosPlanos.map((m) => ({ modulo: m.id, habilitado: !!acceso.modulos[m.id] }))
          : [],
    };
    return this.http.put<void>(`${this.apiUrl}/usuario/${userId}`, dto);
  }
}
