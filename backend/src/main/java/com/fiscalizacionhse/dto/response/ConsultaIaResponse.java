package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultaIaResponse {

    private Long id;
    private String pregunta;
    private String respuesta;
    private String documentosReferencia;
    private String tipo;
    private String feedback;
    private Long empresaId;
    private String empresaNombre;
    private Long usuarioId;
    private String usuarioNombre;
    private LocalDateTime createdAt;
}
