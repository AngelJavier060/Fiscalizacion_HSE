package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Búsqueda semántica + análisis en lenguaje natural (solo documentos de la empresa).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusquedaAsistidaResponse {

    private String consulta;
    /** Explicación en Markdown: hallazgos, resúmenes por documento, términos clave (solo contexto interno). */
    private String analisis;
    /** Fragmentos recuperados (embeddings o búsqueda en texto plano del PDF). */
    private List<BusquedaIaResponse.ResultadoBusqueda> resultados;
    /** Aviso si no hay API DeepSeek o no hay índice semántico. */
    private String advertencia;
}
