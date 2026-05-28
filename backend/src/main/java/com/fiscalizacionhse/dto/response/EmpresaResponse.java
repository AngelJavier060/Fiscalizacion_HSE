package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaResponse {

    private Long id;
    private String nombre;
    private String ruc;
    private String direccion;
    private String email;
    private String telefono;
    private Boolean activa;
    private Long cantidadUsuarios;
    private LocalDate vigenciaDesde;
    private LocalDate vigenciaHasta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
