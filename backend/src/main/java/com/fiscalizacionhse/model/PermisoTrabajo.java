package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "permisos_trabajo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermisoTrabajo {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    @Builder.Default
    private String area = "Sin asignar";

    @Column(nullable = false, length = 255)
    @Builder.Default
    private String responsible = "Sin asignar";

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "image_path", columnDefinition = "TEXT")
    private String imagePath;

    @Column(name = "critical_task", length = 30)
    private String criticalTask;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String emisor;

    @Column(length = 255)
    private String ejecutante;

    @Column(name = "empresa_ejecutante", length = 255)
    private String empresaEjecutante;

    @Column(columnDefinition = "TEXT")
    private String nota;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
