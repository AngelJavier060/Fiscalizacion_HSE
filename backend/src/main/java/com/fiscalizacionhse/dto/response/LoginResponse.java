package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tipoToken;
    private Long id;
    private String nombre;
    private String email;
    private String rol;
    private Long empresaId;
    private String empresaNombre;

    /** Códigos de módulos habilitados para este usuario */
    private List<String> modulos;
}
