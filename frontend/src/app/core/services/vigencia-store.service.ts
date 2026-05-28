import { Injectable } from '@angular/core';

export interface Vigencia {
  /** ISO date (yyyy-MM-dd) o null si sin límite */
  desde: string | null;
  hasta: string | null;
}

export type EstadoVigencia =
  | 'sin_limite'
  | 'pendiente'
  | 'vigente'
  | 'por_vencer'
  | 'vencido';

export interface VigenciaInfo {
  vigencia: Vigencia;
  estado: EstadoVigencia;
  etiqueta: string;
  /** días que faltan para vencer (negativo si ya venció), null si no aplica */
  diasRestantes: number | null;
}

/**
 * Calcula el estado de una vigencia (fecha de inicio/fin de acceso).
 *
 * Las fechas ahora vienen embebidas en cada entidad (usuario/empresa)
 * desde el backend; este servicio solo deriva el estado para mostrarlo.
 */
@Injectable({ providedIn: 'root' })
export class VigenciaStoreService {
  /** umbral de días para marcar "por vencer" */
  private readonly umbralPorVencer = 15;

  /** Atajo: calcula el estado a partir de fechas sueltas (yyyy-MM-dd o null). */
  estado(desde: string | null | undefined, hasta: string | null | undefined): VigenciaInfo {
    return this.calcular({ desde: desde ?? null, hasta: hasta ?? null });
  }

  calcular(vigencia: Vigencia): VigenciaInfo {
    const hoy = this.soloFecha(new Date());
    const desde = vigencia.desde ? this.soloFecha(new Date(vigencia.desde)) : null;
    const hasta = vigencia.hasta ? this.soloFecha(new Date(vigencia.hasta)) : null;

    if (!desde && !hasta) {
      return { vigencia, estado: 'sin_limite', etiqueta: 'Sin límite', diasRestantes: null };
    }

    if (desde && hoy < desde) {
      const dias = this.difDias(hoy, desde);
      return {
        vigencia,
        estado: 'pendiente',
        etiqueta: `Inicia en ${dias} día${dias === 1 ? '' : 's'}`,
        diasRestantes: dias,
      };
    }

    if (hasta) {
      const dias = this.difDias(hoy, hasta);
      if (dias < 0) {
        return { vigencia, estado: 'vencido', etiqueta: 'Vencido', diasRestantes: dias };
      }
      if (dias <= this.umbralPorVencer) {
        return {
          vigencia,
          estado: 'por_vencer',
          etiqueta: dias === 0 ? 'Vence hoy' : `Vence en ${dias} día${dias === 1 ? '' : 's'}`,
          diasRestantes: dias,
        };
      }
    }

    return { vigencia, estado: 'vigente', etiqueta: 'Vigente', diasRestantes: hasta ? this.difDias(hoy, hasta) : null };
  }

  private soloFecha(d: Date): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  private difDias(a: Date, b: Date): number {
    const ms = b.getTime() - a.getTime();
    return Math.round(ms / 86400000);
  }
}
