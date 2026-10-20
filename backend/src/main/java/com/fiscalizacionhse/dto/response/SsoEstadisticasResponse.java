package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SsoEstadisticasResponse {
    private int dias;
    private int tasaGlobalAcierto;
    private long respuestasContadas;
    private long preguntasActivas;
    private long preguntasEvaluadas;
    private long preguntasNuncaSalieron;
    private long preguntasRepetidas;
    private long preguntasCriticas;
    private int coberturaPct;
    private Long masReprobadaId;
    private String masReprobadaEnunciado;
    private String masReprobadaTema;
    private int masReprobadaAcierto;
    private long masReprobadaIntentos;
    private int faciles;
    private int intermedias;
    private int criticas;
    private List<TemaStat> porTema;
    private List<PreguntaStat> preguntas;

    @Data
    @Builder
    public static class TemaStat {
        private Long id;
        private String nombre;
        private long intentos;
        private int aciertoPct;
    }

    @Data
    @Builder
    public static class PreguntaStat {
        private Long id;
        private String enunciado;
        private Long temaId;
        private String temaNombre;
        private long intentos;
        private long aciertos;
        private int aciertoPct;
        private long vecesSalio;
        private boolean nuncaSalio;
        private boolean repetida;
        private String distractorTexto;
        private int distractorPct;
        private List<OpcionStat> opciones;
    }

    @Data
    @Builder
    public static class OpcionStat {
        private Long id;
        private String texto;
        private boolean correcta;
        private long votos;
        private int pct;
    }
}
