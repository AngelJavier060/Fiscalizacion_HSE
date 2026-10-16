package com.fiscalizacionhse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtraccionProgresoResponse {
    private Long documentoId;
    /** EN_COLA, ABRIENDO, EXTRAYENDO, LISTO, SIN_TEXTO, ERROR */
    private String fase;
    private String mensaje;
    private int paginaActual;
    private int paginasTotal;
    private int porcentaje;
    private int caracteres;
    /** Letras extraídas. Solo se envía en LISTO para que el lector no dependa de otro GET. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String textoPlano;
}
