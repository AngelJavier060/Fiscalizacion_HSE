package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sso_intentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoIntento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 20)
    private String modo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tema_id")
    private SsoTema tema;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "en_curso";

    @Column(name = "fecha_inicio", nullable = false)
    @Builder.Default
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "tiempo_limite_min", nullable = false)
    private Integer tiempoLimiteMin;

    @Column(name = "total_preguntas", nullable = false)
    @Builder.Default
    private Integer totalPreguntas = 0;

    private Integer correctas;

    @Column(precision = 6, scale = 2)
    private BigDecimal nota;

    private Boolean aprobado;

    @OneToMany(mappedBy = "intento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<SsoIntentoRespuesta> respuestas = new ArrayList<>();
}
