package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SsoInicioResponse {
    private int vueltaActual;
    private long preguntasVistasVuelta;
    private long preguntasBanco;
    private int falladasPendientes;
    private BigDecimal ultimaNota;
    private Boolean ultimoAprobado;
    private LocalDateTime ultimaFecha;
    private Long intentoPendienteId;
    private String intentoPendienteModo;
    private List<SsoTemaResponse> temas;
    private int notaMinima;
    private int tiempo20Min;
    private int tiempo40Min;
    private int tiempoExamenMin;
    private int preguntasPorBanco;
    private Long bloqueRepetirTemaId;
    private String bloqueRepetirTemaNombre;
    private BigDecimal bloqueRepetirNota;
    private int bloqueRepetirTotal;
    private boolean bloqueRepetir;
    private List<SsoHistorialItemResponse> historial;
}
