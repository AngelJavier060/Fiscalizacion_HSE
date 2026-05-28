package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionResponse {

    private Long id;
    private String titulo;
    private String mensaje;
    private String tipo;
    private Boolean leida;
    private LocalDateTime fechaLectura;
    private Boolean tieneAudio;
    private String rutaAudio;
    private Integer duracionAudio;
    private Long usuarioId;
    private String usuarioNombre;
    private Long recordatorioId;
    private String recordatorioTitulo;
    private Long documentoId;
    private String documentoTitulo;
    private LocalDateTime createdAt;
}
