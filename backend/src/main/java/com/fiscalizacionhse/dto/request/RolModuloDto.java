package com.fiscalizacionhse.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una celda de la matriz rol × módulo */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolModuloDto {
    private String rol;
    private String modulo;
    private boolean habilitado;
}
