package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SsoConfiguracionResponse {
    private int tiempoExamenMin;
    private int notaMinima;
    private int preguntasPorBanco;
    private List<Integer> tiemposPermitidos;
    private List<Integer> notasPermitidas;
    private List<Integer> bancosPermitidos;
}
