package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentoVersionResponse {
    private Long id;
    private Long documentoId;
    private Integer numeroVersion;
    private String titulo;
    private String descripcion;
    private String archivoNombre;
    private Long archivoTamano;
    private String reemplazadoPorNombre;
    private LocalDateTime createdAt;
}
