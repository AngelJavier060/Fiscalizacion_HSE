package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordatorioResponse {

    private Long id;
    private String titulo;
    private String descripcion;
    private String tipoRecurrencia;
    private Integer intervaloDias;
    private Integer diaSemana;
    private Integer diaMes;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalTime horaRecordatorio;
    private LocalDateTime proximaEjecucion;
    private LocalDateTime ultimaEjecucion;
    private Boolean incluirAudio;
    private String mensajePersonalizado;
    private Boolean activo;
    private Long documentoId;
    private String documentoTitulo;
    private Long empresaId;
    private String empresaNombre;
    private Long creadoPorId;
    private String creadoPorNombre;
    private List<DestinatarioInfo> destinatarios;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinatarioInfo {
        private Long id;
        private String nombre;
        private String email;
    }
}
