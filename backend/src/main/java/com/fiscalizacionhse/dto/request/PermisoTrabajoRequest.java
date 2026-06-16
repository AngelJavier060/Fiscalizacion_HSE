package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PermisoTrabajoRequest {

    @NotBlank(message = "El ID del permiso es obligatorio")
    private String id;

    @NotBlank(message = "El título es obligatorio")
    private String title;

    private String area;
    private String responsible;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDateTime startDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDateTime endDate;

    private String imagePath;
    private String criticalTask;
    private String description;
    private String emisor;
    private String ejecutante;
    private String empresaEjecutante;
    private String nota;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @NotNull(message = "El ID de la empresa es obligatorio")
    private Long empresaId;
}
