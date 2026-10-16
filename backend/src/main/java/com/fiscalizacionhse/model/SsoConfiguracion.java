package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sso_configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoConfiguracion {

    @Id
    @Column(length = 80)
    private String clave;

    @Column(nullable = false, length = 80)
    private String valor;
}
