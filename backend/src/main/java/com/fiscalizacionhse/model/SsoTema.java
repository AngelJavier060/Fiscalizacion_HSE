package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sso_temas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoTema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
