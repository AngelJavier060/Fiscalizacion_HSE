package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocumentoRequest {

    @NotBlank(message = "El título del documento es obligatorio")
    private String titulo;

    private String descripcion;

    @NotNull(message = "El ID de la empresa es obligatorio")
    private Long empresaId;
}
