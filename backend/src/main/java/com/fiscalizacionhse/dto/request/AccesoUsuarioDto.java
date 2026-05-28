package com.fiscalizacionhse.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Accesos a módulos de un usuario concreto */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccesoUsuarioDto {

    /** "rol" (hereda) | "custom" (personalizado) */
    private String modo;

    private List<ModuloFlag> modulos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuloFlag {
        private String modulo;
        private boolean habilitado;
    }
}
