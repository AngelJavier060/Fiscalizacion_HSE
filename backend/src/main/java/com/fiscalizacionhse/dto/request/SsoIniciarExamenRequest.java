package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoIniciarExamenRequest {

    @NotBlank(message = "El modo es obligatorio")
    private String modo;

    private Long temaId;
}
