import { Injectable } from '@angular/core';

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class DocumentoCacheService {
  /** Cache en memoria: clave = "texto_{documentoId}" o "pdf_{documentoId}" */
  private cache = new Map<string, CacheEntry<any>>();
  private readonly TTL_MS = 30 * 60 * 1000; // 30 minutos

  get<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;
    if (Date.now() - entry.timestamp > this.TTL_MS) {
      this.cache.delete(key);
      return null;
    }
    return entry.data as T;
  }

  set(key: string, data: any): void {
    // Limitar tamaño: si hay más de 50 entradas, borrar la más antigua
    if (this.cache.size > 50) {
      const oldest = this.cache.keys().next().value;
      if (oldest) this.cache.delete(oldest);
    }
    this.cache.set(key, { data, timestamp: Date.now() });
  }

  /** Genera clave para texto completo */
  static textoKey(docId: number): string {
    return `texto_${docId}`;
  }

  /** Genera clave para blob PDF (como blob se necesita regenerar) */
  static pdfKey(docId: number): string {
    return `pdf_${docId}`;
  }

  /** Invalidar un documento (cuando se actualiza) */
  invalidar(docId: number): void {
    this.cache.delete(DocumentoCacheService.textoKey(docId));
    this.cache.delete(DocumentoCacheService.pdfKey(docId));
  }

  /** Limpiar todo el caché */
  limpiar(): void {
    this.cache.clear();
  }
}
