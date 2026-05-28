package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class RecordatorioRequest {

    @NotBlank(message = "El título del recordatorio es obligatorio")
    private String titulo;

    private String descripcion;

    private String tipoRecurrencia; // ONE_TIME, DAILY, WEEKLY, MONTHLY, CUSTOM

    private Integer intervaloDias;

    private Integer diaSemana; // 0=Domingo, 1=Lunes... 6=Sábado

    private Integer diaMes; // 1-31

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private LocalTime horaRecordatorio;

    private Boolean incluirAudio;

    private String mensajePersonalizado;

    private Long documentoId;

    @NotNull(message = "El ID de la empresa es obligatorio")
    private Long empresaId;

    private List<Long> destinatarioIds;
}
