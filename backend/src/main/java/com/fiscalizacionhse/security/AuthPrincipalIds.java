package com.fiscalizacionhse.security;

import com.fiscalizacionhse.exception.BadRequestException;
import org.springframework.security.core.Authentication;

/**
 * El JWT configura el principal con {@code User} cuyo {@code username} es el {@code id} numérico del usuario.
 */
public final class AuthPrincipalIds {

    private AuthPrincipalIds() {}

    public static long usuarioId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("No autenticado");
        }
        String name = authentication.getName();
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Sesión inválida: no se pudo identificar al usuario");
        }
    }
}
