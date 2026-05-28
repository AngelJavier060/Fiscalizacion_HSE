package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuloResponse {
    private String codigo;
    private String nombre;
    private String descripcion;
    private String grupo;
    private String icono;
    private Integer orden;
}
