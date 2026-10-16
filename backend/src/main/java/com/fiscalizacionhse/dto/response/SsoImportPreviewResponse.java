package com.fiscalizacionhse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SsoImportPreviewResponse {
    private int totalFilas;
    private int validas;
    private int conError;
    private int temasNuevos;
    private int posiblesDuplicados;
    private List<Fila> filas;

    @Data
    @Builder
    public static class Fila {
        private int fila;
        private boolean ok;
        private List<String> errores;
        private boolean temaNuevo;
        private boolean posibleDuplicado;
        private String tema;
        private String enunciado;
        private String opcionA;
        private String opcionB;
        private String opcionC;
        private String opcionD;
        private String opcionE;
        private String correcta;
        private String explicacion;
        private String fuente;
    }
}
