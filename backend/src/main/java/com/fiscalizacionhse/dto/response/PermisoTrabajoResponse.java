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
public class PermisoTrabajoResponse {

    private String id;
    private String title;
    private String area;
    private String responsible;
    private LocalDateTime startDate;
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
    private Boolean activo;
    private Long empresaId;
    private String empresaNombre;
    private Long creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Campos calculados
    private String status;      // active | warning | expired
    private Integer remainingDays;
}
