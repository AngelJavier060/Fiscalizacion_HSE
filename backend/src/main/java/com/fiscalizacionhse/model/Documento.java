package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "archivo_nombre", nullable = false, length = 500)
    private String archivoNombre;

    @Column(name = "archivo_tipo", nullable = false, length = 100)
    @Builder.Default
    private String archivoTipo = "application/pdf";

    @Column(name = "archivo_tamano", nullable = false)
    @Builder.Default
    private Long archivoTamano = 0L;

    @Column(name = "ruta_archivo", nullable = false, length = 1000)
    private String rutaArchivo;

    @Column(name = "texto_extraido", columnDefinition = "TEXT")
    private String textoExtraido;

    @Column(name = "texto_traducido", columnDefinition = "TEXT")
    private String textoTraducido;

    @Column(name = "idioma_original", length = 10)
    @Builder.Default
    private String idiomaOriginal = "es";

    @Column(name = "idioma_detectado", length = 10)
    private String idiomaDetectado;

    @Column(name = "requiere_traduccion", nullable = false)
    @Builder.Default
    private Boolean requiereTraduccion = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean traducido = false;

    @Column(name = "puntos_generados_ia", nullable = false)
    @Builder.Default
    private Boolean puntosGeneradosIa = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por", nullable = false)
    private Usuario subidoPor;

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
