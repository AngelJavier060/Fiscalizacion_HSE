import { Injectable, signal } from '@angular/core';

export interface TituloDetectado {
  texto: string;
  pagina: number;
}

export interface BloqueTexto {
  texto: string;
  fontSize: number;
  pagina: number;
}

@Injectable({ providedIn: 'root' })
export class PdfTitleService {
  readonly titulos = signal<TituloDetectado[]>([]);
  readonly procesando = signal(false);
  readonly error = signal<string | null>(null);
  readonly nombreArchivo = signal<string>('');

  /**
   * Procesa un archivo PDF usando pdfjs-dist 100% en el navegador.
   * 
   * Algoritmo de detección de títulos:
   * 1. Extraer cada bloque de texto del PDF con su fontSize
   * 2. Calcular el fontSize promedio de TODO el documento
   * 3. Todo texto cuyo fontSize sea MAYOR al promedio = TÍTULO
   */
  async procesarPdf(file: File): Promise<TituloDetectado[]> {
    this.procesando.set(true);
    this.error.set(null);
    this.titulos.set([]);
    this.nombreArchivo.set(file.name);

    try {
      // Validar extensión
      if (!file.name.toLowerCase().endsWith('.pdf')) {
        throw new Error('Solo se aceptan archivos .pdf');
      }

      // Cargar pdfjs-dist dinámicamente (para evitar problemas con el worker en producción)
      const pdfjsLib = await import('pdfjs-dist');
      pdfjsLib.GlobalWorkerOptions.workerSrc = 'assets/pdf.worker.min.js';

      // Leer el archivo como ArrayBuffer
      const arrayBuffer = await file.arrayBuffer();

      // Cargar el documento PDF
      const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
      const totalPaginas = pdf.numPages;

      console.log(`📄 PDF cargado: ${totalPaginas} páginas`);

      // 1. Extraer TODOS los bloques de texto con su fontSize de todas las páginas
      const todosLosBloques: BloqueTexto[] = [];

      for (let numPag = 1; numPag <= totalPaginas; numPag++) {
        const page = await pdf.getPage(numPag);
        const content = await page.getTextContent();

        for (const item of content.items) {
          if ('str' in item) {
            const texto = (item as any).str?.trim();
            // El fontSize en pdfjs está en el transform[0] (escala horizontal)
            // o en el atributo fontSize directamente
            const fontSize = (item as any).transform?.[0]
              ? Math.round((item as any).transform[0] * 100) / 100
              : (item as any).fontSize || 0;

            if (texto && texto.length > 0 && fontSize > 0) {
              todosLosBloques.push({
                texto,
                fontSize,
                pagina: numPag,
              });
            }
          }
        }
      }

      console.log(`📊 Bloques de texto extraídos: ${todosLosBloques.length}`);

      if (todosLosBloques.length === 0) {
        throw new Error('No se pudo extraer texto del PDF. El archivo podría estar vacío o ser solo imágenes escaneadas.');
      }

      // 2. Calcular el fontSize promedio de TODO el documento
      const sumaFontSizes = todosLosBloques.reduce((sum, b) => sum + b.fontSize, 0);
      const fontSizePromedio = sumaFontSizes / todosLosBloques.length;

      console.log(`📐 FontSize promedio del documento: ${fontSizePromedio.toFixed(2)}`);
      console.log(`📊 FontSize máx: ${Math.max(...todosLosBloques.map(b => b.fontSize)).toFixed(2)}, mín: ${Math.min(...todosLosBloques.map(b => b.fontSize)).toFixed(2)}`);

      // Debug: mostrar algunos bloques con sus tamaños
      const bloquesOrdenados = [...todosLosBloques].sort((a, b) => b.fontSize - a.fontSize);
      console.log('🔍 Top 15 bloques más grandes:');
      bloquesOrdenados.slice(0, 15).forEach((b, i) => {
        console.log(`  ${i+1}. fontSize=${b.fontSize.toFixed(1)} | pág=${b.pagina} | "${b.texto.substring(0, 80)}"`);
      });

      // 3. Filtrar: solo bloques con fontSize MAYOR al promedio = TÍTULO
      const titulosDetectados: TituloDetectado[] = [];

      for (const bloque of todosLosBloques) {
        if (bloque.fontSize > fontSizePromedio) {
          // Verificar que no sea un título duplicado (mismo texto exacto en misma página)
          const existe = titulosDetectados.some(
            (t) => t.texto.toLowerCase() === bloque.texto.toLowerCase() && t.pagina === bloque.pagina
          );
          if (!existe) {
            titulosDetectados.push({
              texto: bloque.texto,
              pagina: bloque.pagina,
            });
          }
        }
      }

      console.log(`🎯 Títulos detectados (fontSize > promedio): ${titulosDetectados.length}`);

      // 4. FALLBACK: Si no se detectaron títulos con el promedio, 
      // intentar con percentil 70 (más sensible)
      if (titulosDetectados.length === 0 && todosLosBloques.length > 3) {
        console.log('⚠️ No se detectaron títulos con el promedio. Usando percentil 70 como fallback...');
        
        const ordenados = [...todosLosBloques].sort((a, b) => b.fontSize - a.fontSize);
        const cantidad = Math.max(1, Math.ceil(ordenados.length * 0.3)); // top 30%
        const candidatos = ordenados.slice(0, cantidad);

        for (const bloque of candidatos) {
          const existe = titulosDetectados.some(
            (t) => t.texto.toLowerCase() === bloque.texto.toLowerCase() && t.pagina === bloque.pagina
          );
          if (!existe) {
            titulosDetectados.push({
              texto: bloque.texto,
              pagina: bloque.pagina,
            });
          }
        }
        console.log(`🎯 Títulos detectados (fallback percentil 70): ${titulosDetectados.length}`);
      }

      // 5. Ordenar por página
      titulosDetectados.sort((a, b) => a.pagina - b.pagina);

      // 6. Limpiar textos: eliminar saltos de línea múltiples y espacios extra
      for (const t of titulosDetectados) {
        t.texto = t.texto.replace(/\s+/g, ' ').trim();
      }

      // Actualizar signal
      this.titulos.set(titulosDetectados);
      this.procesando.set(false);

      console.log(`✅ Procesamiento completado: ${titulosDetectados.length} títulos detectados`);
      
      return titulosDetectados;
    } catch (err: any) {
      const mensaje = err?.message || 'Error desconocido al procesar el PDF';
      console.error('❌ Error procesando PDF:', err);
      this.error.set(mensaje);
      this.procesando.set(false);
      throw err;
    }
  }

  /** Obtener estadísticas de depuración para mostrar en UI */
  async obtenerEstadisticas(file: File): Promise<{ totalBloques: number; fontSizePromedio: number; fontSizeMax: number; fontSizeMin: number } | null> {
    try {
      const pdfjsLib = await import('pdfjs-dist');
      pdfjsLib.GlobalWorkerOptions.workerSrc = 'assets/pdf.worker.min.js';
      const arrayBuffer = await file.arrayBuffer();
      const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
      
      const bloques: number[] = [];
      for (let i = 1; i <= pdf.numPages; i++) {
        const page = await pdf.getPage(i);
        const content = await page.getTextContent();
        for (const item of content.items) {
          if ('str' in item) {
            const fs = (item as any).transform?.[0] || (item as any).fontSize || 0;
            if (fs > 0) bloques.push(fs);
          }
        }
      }
      
      if (!bloques.length) return null;
      
      return {
        totalBloques: bloques.length,
        fontSizePromedio: Math.round((bloques.reduce((a, b) => a + b, 0) / bloques.length) * 100) / 100,
        fontSizeMax: Math.round(Math.max(...bloques) * 100) / 100,
        fontSizeMin: Math.round(Math.min(...bloques) * 100) / 100,
      };
    } catch {
      return null;
    }
  }

  limpiar(): void {
    this.titulos.set([]);
    this.error.set(null);
    this.procesando.set(false);
    this.nombreArchivo.set('');
  }
}
