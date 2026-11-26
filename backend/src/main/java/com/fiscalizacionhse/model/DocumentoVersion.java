package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documento_versiones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentoVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "archivo_nombre", nullable = false, length = 500)
    private String archivoNombre;

    @Column(name = "archivo_tipo", nullable = false, length = 100)
    private String archivoTipo;

    @Column(name = "archivo_tamano", nullable = false)
    private Long archivoTamano;

    @Column(name = "ruta_archivo", nullable = false, length = 1000)
    private String rutaArchivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reemplazado_por")
    private Usuario reemplazadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
