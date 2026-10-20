package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SsoCoberturaResponse {
    private long totalPreguntasActivas;
    private long preguntasNuncaSalieron;
    private long preguntasEnAlMenosUnExamen;
    private long preguntasRepetidas;
    private int notaAvance;
    private int preguntasPorBanco;
    private int tiempoExamenMin;
    private List<UsuarioCobertura> usuarios;
    private List<PreguntaSinUso> preguntasSinUso;

    @Data
    @Builder
    public static class UsuarioCobertura {
        private Long id;
        private String nombre;
        private String email;
        private String empresaNombre;
        private int vuelta;
        private long cumplidas;
        private long total;
        private int porcentaje;
        private boolean debeRepetir;
        private BigDecimal ultimaNota;
        private String ultimoAlcance;
    }

    @Data
    @Builder
    public static class PreguntaSinUso {
        private Long id;
        private String enunciado;
        private String temaNombre;
        private long vecesSalio;
    }
}
