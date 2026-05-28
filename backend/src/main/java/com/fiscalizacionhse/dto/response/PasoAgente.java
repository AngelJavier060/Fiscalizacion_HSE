package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paso visible del agente FISCALIZA-AI (herramienta ejecutada en el flujo multi-paso).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasoAgente {
    /** Orden 1-based en la secuencia. */
    private int orden;
    /** Identificador de herramienta: listar_documentos, buscar_fragmentos, leer_documentos, redactar_respuesta. */
    private String herramienta;
    /** Título corto para la UI. */
    private String titulo;
    /** Detalle de lo realizado. */
    private String detalle;
    /** ok | omitido | error */
    private String estado;
}
