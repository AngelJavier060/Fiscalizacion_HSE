package com.fiscalizacionhse.dto.request;

import lombok.Data;

@Data
public class SsoConfiguracionRequest {
    private Integer tiempoExamenMin;
    private Integer notaMinima;
    private Integer preguntasPorBanco;
}
