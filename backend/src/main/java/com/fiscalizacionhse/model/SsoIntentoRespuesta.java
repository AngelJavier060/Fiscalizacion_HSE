package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sso_intento_respuestas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoIntentoRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intento_id", nullable = false)
    private SsoIntento intento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private SsoPregunta pregunta;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "orden_opciones", nullable = false, columnDefinition = "TEXT")
    private String ordenOpciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opcion_elegida_id")
    private SsoOpcion opcionElegida;

    @Column(name = "es_correcta")
    private Boolean esCorrecta;
}
