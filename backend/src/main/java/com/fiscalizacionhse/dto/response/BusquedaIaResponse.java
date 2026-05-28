package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusquedaIaResponse {

    private String pregunta;
    private String respuesta;
    private String documentosReferencia;
    private List<ResultadoBusqueda> resultados;
    /** Inventario de PDF activos de la empresa (útil cuando no hubo fragmentos RAG o para la UI). */
    private List<CatalogoEmpresaDoc> catalogoEmpresa;
    /** Motor de redacción (DeepSeek). */
    private String motor;
    /** true si DEEPSEEK_API_KEY está configurada en el servidor. */
    private Boolean deepseekActivo;
    /** Cantidad de PDF activos en la empresa al momento de la consulta. */
    private Integer documentosEmpresa;
    /** Aviso operativo (sin índice, sin clave, etc.). */
    private String advertencia;
    /** Pasos del agente multi-paso (listar → buscar → leer → redactar). */
    private List<PasoAgente> pasosAgente;
    /**
     * Si false, la UI no debe mostrar «Archivos relacionados» ni portadas (respuesta tipo chat, solo texto).
     */
    private Boolean mostrarReferencias;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultadoBusqueda {
        private Long chunkId;
        private String chunkText;
        private Integer chunkOrder;
        private Long documentoId;
        private String documentoTitulo;
        private Double similitud;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogoEmpresaDoc {
        private Long id;
        private String titulo;
        /** Breve (puede ser null). */
        private String descripcion;
    }
}
