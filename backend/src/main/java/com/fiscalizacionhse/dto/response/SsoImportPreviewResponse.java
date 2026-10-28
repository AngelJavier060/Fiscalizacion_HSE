package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoImportPreviewResponse {
    private int totalFilas;
    private int validas;
    private int conError;
    private int temasNuevos;
    private int posiblesDuplicados;
    private int nuevas;
    private int actualizadas;
    private int sinCambios;
    /** Preguntas activas que no están en el archivo (en la confirmación: las que se desactivaron). */
    private int ausentes;
    private List<Fila> filas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fila {
        private int fila;
        /** Pregunta existente que se actualiza; null si es nueva. */
        private Long id;
        /** crear, actualizar, sin_cambios o error. */
        private String accion;
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
        private Boolean activo;
    }
}
