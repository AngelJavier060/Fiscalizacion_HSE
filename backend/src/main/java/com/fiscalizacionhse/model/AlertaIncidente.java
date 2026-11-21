package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "alertas_incidentes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaIncidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String codigo;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(name = "unidad_negocio", nullable = false, length = 255)
    private String unidadNegocio;

    @Column(nullable = false, length = 255)
    private String ambito;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 255)
    private String area;

    @Column(name = "dueno_area", nullable = false, length = 255)
    private String duenoArea;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(name = "empresa_involucrada", nullable = false, length = 255)
    private String empresaInvolucrada;

    @Column(nullable = false, length = 40)
    private String impacto;

    @Column(name = "accidente_grave", nullable = false)
    private Boolean accidenteGrave;

    @Column(nullable = false)
    private Boolean iap;

    @Column(nullable = false, length = 255)
    private String eorpv;

    @Column(name = "archivo_nombre", length = 255)
    private String archivoNombre;

    @Column(name = "archivo_ruta", columnDefinition = "TEXT")
    private String archivoRuta;

    @Column(nullable = false, length = 10)
    private String prioridad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
