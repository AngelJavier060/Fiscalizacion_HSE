package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario_modulo")
@IdClass(UsuarioModuloId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioModulo {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Id
    @Column(name = "modulo_codigo", length = 40)
    private String moduloCodigo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean habilitado = false;
}
