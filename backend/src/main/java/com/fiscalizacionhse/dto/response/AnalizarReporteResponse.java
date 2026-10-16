package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalizarReporteResponse {

    private String motor;
    private boolean ollamaListo;
    private String modelo;
    private String avisoMotor;
    private int caracteresInforme;
    private String archivoNombre;
    private int documentosConsultados;

    @Builder.Default
    private List<Coincidencia> coincidencias = new ArrayList<>();

    @Builder.Default
    private List<String> huecos = new ArrayList<>();

    /** Borrador profesional en Markdown, listo para copiar. */
    private String textoPropuesto;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coincidencia {
        private Long documentoId;
        private String documentoTitulo;
        private String cita;
        private int relevancia;
    }
}
