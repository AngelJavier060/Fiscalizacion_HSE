package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sso_preguntas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tema_id", nullable = false)
    private SsoTema tema;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciado;

    @Column(columnDefinition = "TEXT")
    private String explicacion;

    @Column(columnDefinition = "TEXT")
    private String fuente;

    @Column(length = 500)
    private String imagen;

    /** Texto del sustento de la pregunta. Distinto de la normativa y del archivo. */
    @Column(name = "sustento_texto", columnDefinition = "TEXT")
    private String sustentoTexto;

    /** Nombre original del archivo de sustento (PDF o imagen). */
    @Column(name = "sustento_nombre", length = 255)
    private String sustentoNombre;

    @Column(name = "sustento_tipo", length = 120)
    private String sustentoTipo;

    @Column(name = "sustento_ruta", length = 500)
    private String sustentoRuta;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_edicion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaEdicion = LocalDateTime.now();

    @OneToMany(mappedBy = "pregunta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SsoOpcion> opciones = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        fechaEdicion = LocalDateTime.now();
    }
}
