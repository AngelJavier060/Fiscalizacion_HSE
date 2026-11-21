package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertaIncidenteResponse {
    private Long id;
    private String codigo;
    private String titulo;
    private String unidad;
    private String ambito;
    private String fecha;
    private String area;
    private String dueno;
    private String hora;
    private String empresa;
    private String impacto;
    private Boolean accidenteGrave;
    private Boolean iap;
    private String eorpv;
    private String archivoNombre;
    private boolean tieneArchivo;
    private String prioridad;
    private String detalle;
}
