package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SsoExamenResponse {
    private Long id;
    private String modo;
    private Long temaId;
    private String temaNombre;
    private String estado;
    private LocalDateTime fechaInicio;
    private Integer tiempoLimiteMin;
    /** Segundos que quedan, calculados en el servidor (evita el desfase de zona horaria). */
    private Integer segundosRestantes;
    private int totalPreguntas;
    private int respondidas;
    private boolean bancoInsuficiente;
    private String aviso;
    private List<Pregunta> preguntas;

    @Data
    @Builder
    public static class Pregunta {
        private Long id;
        private int orden;
        private String enunciado;
        private String temaNombre;
        private Long opcionElegidaId;
        private List<Opcion> opciones;
    }

    @Data
    @Builder
    public static class Opcion {
        private Long id;
        private String texto;
    }
}
