package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private Long id;
    private String nombre;
    private String email;
    private String rol;
    private Boolean activo;
    private Long empresaId;
    private String empresaNombre;
    private LocalDateTime ultimoAcceso;
    private LocalDateTime createdAt;

    /** Vigencia de acceso del usuario (null = sin límite) */
    private LocalDate accesoDesde;
    private LocalDate accesoHasta;
    private Boolean accesosPersonalizados;

    /** Códigos de módulos habilitados (se rellena en /api/me/perfil) */
    private List<String> modulos;
}
