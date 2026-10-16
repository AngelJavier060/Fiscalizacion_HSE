package com.fiscalizacionhse.model;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SsoRotacionId implements Serializable {
    private Long usuarioId;
    private Long preguntaId;
    private Integer vuelta;
}
