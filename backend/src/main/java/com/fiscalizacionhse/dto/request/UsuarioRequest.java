package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UsuarioRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    /** Obligatoria al crear; opcional al actualizar (solo si se desea cambiar). */
    private String password;

    private String rol;

    private Long empresaId;

    /** Vigencia de acceso (null = sin límite) */
    private LocalDate accesoDesde;
    private LocalDate accesoHasta;
}
