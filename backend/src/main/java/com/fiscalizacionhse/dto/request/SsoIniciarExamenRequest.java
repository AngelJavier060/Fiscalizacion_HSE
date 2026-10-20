package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoIniciarExamenRequest {

    @NotBlank(message = "El modo es obligatorio")
    private String modo;

    private Long temaId;

    /** 20, 40, 60 o 0 (todas). Si falta, se usa la regla de Super Admin. */
    private Integer preguntasPorBanco;

    /**
     * true: arma un set nuevo (otras preguntas, al azar).
     * false: si el último intento del alcance reprobó, repite esas mismas preguntas.
     */
    private Boolean nuevoSet;

    /** Resultado desde el que el usuario elige repetir o continuar. */
    private Long intentoOrigenId;
}
