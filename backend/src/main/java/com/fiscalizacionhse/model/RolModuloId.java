package com.fiscalizacionhse.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolModuloId implements Serializable {
    private String rol;
    private String moduloCodigo;
}
