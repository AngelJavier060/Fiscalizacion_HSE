package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoEstudioPreguntaResponse {
    private Long id;
    private Long temaId;
    private String temaNombre;
    private String enunciado;
    private String explicacion;
    private String fuente;
    private String sustentoTexto;
    private boolean tieneSustento;
    private String sustentoNombre;
    private String sustentoTipo;
}
