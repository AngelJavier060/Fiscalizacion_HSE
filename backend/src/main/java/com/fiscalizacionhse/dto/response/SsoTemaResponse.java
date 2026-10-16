package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoTemaResponse {
    private Long id;
    private String nombre;
    private Boolean activo;
    private long totalPreguntas;
    private long preguntasActivas;
}
