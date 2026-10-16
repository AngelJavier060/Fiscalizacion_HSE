package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SsoConfiguracionRequest {
    @NotNull
    private Integer tiempoExamenMin;
}
