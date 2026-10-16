package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SsoResponderRequest {

    @NotNull
    private Long preguntaId;

    private Long opcionId;
}
