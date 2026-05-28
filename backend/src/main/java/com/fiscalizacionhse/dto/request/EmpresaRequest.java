package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpresaRequest {

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    private String nombre;

    private String ruc;

    private String direccion;

    private String email;

    private String telefono;

    /** Vigencia del servicio (null = sin límite) */
    private LocalDate vigenciaDesde;
    private LocalDate vigenciaHasta;
}
