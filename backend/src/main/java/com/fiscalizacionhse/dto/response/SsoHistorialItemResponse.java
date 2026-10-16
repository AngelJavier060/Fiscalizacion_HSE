package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SsoHistorialItemResponse {
    private Long id;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String modo;
    private String temaNombre;
    private BigDecimal nota;
    private Boolean aprobado;
    private String estado;
    private int totalPreguntas;
    private Integer correctas;
}
