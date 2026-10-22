package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SsoPreguntaAdminResponse {
    private Long id;
    private Long temaId;
    private String temaNombre;
    private String enunciado;
    private String explicacion;
    private String fuente;
    private String sustentoTexto;
    private String imagen;
    private String sustentoNombre;
    private String sustentoTipo;
    private boolean tieneSustento;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaEdicion;
    private boolean usadaEnIntentos;
    private List<OpcionAdmin> opciones;

    @Data
    @Builder
    public static class OpcionAdmin {
        private Long id;
        private String texto;
        private Boolean esCorrecta;
        private String argumento;
    }
}
