package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PuntoClaveRequest {

    @NotNull(message = "El ID del documento es obligatorio")
    private Long documentoId;

    @NotBlank(message = "El contenido del punto clave es obligatorio")
    private String contenido;

    private String titulo;
    private String tema;
    private String codigo;
    private String tipo;

    private Integer orden;

    @Data
    public static class BatchRequest {
        @NotNull
        private Long documentoId;

        @NotNull
        private List<String> puntos;
    }
}
