package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modulos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Modulo {

    @Id
    @Column(length = 40)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false, length = 60)
    private String grupo;

    @Column(length = 40)
    private String icono;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;
}
