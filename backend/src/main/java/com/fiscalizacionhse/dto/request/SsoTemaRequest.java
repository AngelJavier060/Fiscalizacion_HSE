package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoTemaRequest {

    @NotBlank(message = "El nombre del tema es obligatorio")
    private String nombre;

    private Boolean activo;
}
