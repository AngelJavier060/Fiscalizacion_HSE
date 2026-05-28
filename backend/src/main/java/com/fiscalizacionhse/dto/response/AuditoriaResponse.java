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
public class AuditoriaResponse {

    private Long id;
    private Long usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private Long empresaId;
    private String empresaNombre;
    private String accion;
    private String entidad;
    private Long entidadId;
    private String detalle;
    private String direccionIp;
    private LocalDateTime createdAt;
}
