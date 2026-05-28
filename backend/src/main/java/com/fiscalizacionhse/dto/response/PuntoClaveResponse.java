package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PuntoClaveResponse {

    private Long id;
    private String contenido;
    private String titulo;
    private String tema;
    private String codigo;
    private String tipo;
    private Integer orden;
    private Boolean esIa;
    private BigDecimal confianzaIa;
    private Boolean revisado;
    private Long documentoId;
    private Long creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime createdAt;
}
