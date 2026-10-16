package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sso_opciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoOpcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private SsoPregunta pregunta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "es_correcta", nullable = false)
    @Builder.Default
    private Boolean esCorrecta = false;

    /** Por qué esta opción es incorrecta. Vacío en la respuesta correcta. */
    @Column(columnDefinition = "TEXT")
    private String argumento;
}
