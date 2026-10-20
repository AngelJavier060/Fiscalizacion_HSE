package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SsoResultadoResponse {
    private Long id;
    private String modo;
    private Long temaId;
    private String temaNombre;
    private String estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private int totalPreguntas;
    private int correctas;
    private BigDecimal nota;
    private boolean aprobado;
    private List<AciertosTema> aciertosPorTema;
    private List<Revision> revision;

    @Data
    @Builder
    public static class AciertosTema {
        private String tema;
        private int total;
        private int correctas;
    }

    @Data
    @Builder
    public static class Revision {
        private Long preguntaId;
        private int orden;
        private String enunciado;
        private String explicacion;
        private String fuente;
        private boolean tieneSustento;
        private String sustentoNombre;
        private String sustentoTipo;
        private String temaNombre;
        private Long opcionElegidaId;
        private Long opcionCorrectaId;
        private Boolean esCorrecta;
        private List<OpcionRev> opciones;
    }

    @Data
    @Builder
    public static class OpcionRev {
        private Long id;
        private String texto;
        private boolean correcta;
        private boolean elegida;
        private String argumento;
    }
}
