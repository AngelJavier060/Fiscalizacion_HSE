package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "puntos_clave")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PuntoClave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    /** Título corto del punto (ej. «Sistemas anticaída…» para CC7). */
    @Column(length = 500)
    private String titulo;

    /** Tema o estándar padre (ej. «ESTÁNDAR DE TRABAJO EN ALTURA»). */
    @Column(length = 500)
    private String tema;

    /** Código del control (ej. CC7). */
    @Column(length = 50)
    private String codigo;

    /** CONTROL_CRITICO | ESTANDAR | GENERAL | MANUAL */
    @Column(length = 50)
    private String tipo;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    @Column(name = "es_ia", nullable = false)
    @Builder.Default
    private Boolean esIa = false;

    @Column(name = "confianza_ia", precision = 5, scale = 2)
    private BigDecimal confianzaIa;

    @Column(nullable = false)
    @Builder.Default
    private Boolean revisado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
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
