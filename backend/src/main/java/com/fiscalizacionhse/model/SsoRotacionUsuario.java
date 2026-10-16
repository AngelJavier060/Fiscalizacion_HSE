package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sso_rotacion_usuario")
@IdClass(SsoRotacionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoRotacionUsuario {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Id
    @Column(name = "pregunta_id", nullable = false)
    private Long preguntaId;

    @Id
    @Column(nullable = false)
    private Integer vuelta;
}
