package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rol_modulo")
@IdClass(RolModuloId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolModulo {

    @Id
    @Column(name = "rol", length = 30)
    private String rol;

    @Id
    @Column(name = "modulo_codigo", length = 40)
    private String moduloCodigo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean habilitado = false;
}
