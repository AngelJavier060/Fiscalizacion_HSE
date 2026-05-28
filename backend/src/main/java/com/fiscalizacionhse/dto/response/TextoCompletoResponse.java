package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta con el texto completo extraído de un PDF
 * más las secciones/encabezados detectados automáticamente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextoCompletoResponse {
    private Long id;
    private String titulo;
    private String textoCompleto;
    /** HTML auto-estructurado (títulos + párrafos) para el editor */
    private String textoEstructurado;
    /** Contenido recomendado para el editor: HTML guardado o auto-estructurado */
    private String textoEditor;
    /** Índice completo detectado en el texto extraído */
    private List<SeccionDetectada> indice;
    /** Secciones principales para navegación y lectura por bloques */
    private List<SeccionDetectada> secciones;
    private String idioma;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeccionDetectada {
        private String nivel;       // "1", "1.1", "1.1.1", "ESTANDAR", "CAPITULO"
        private String titulo;      // texto del encabezado
        private int indiceInicio;   // posición en el texto completo (para resaltar)
        private int indiceFin;      // posición final
        private int profundidad;    // 0=principal, 1=sub, 2=sub-sub, etc.
    }
}
