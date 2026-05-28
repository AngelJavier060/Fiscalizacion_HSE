package com.fiscalizacionhse.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioModuloId implements Serializable {
    private Long usuarioId;
    private String moduloCodigo;
}
