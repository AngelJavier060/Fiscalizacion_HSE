package com.fiscalizacionhse.security;

import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.UsuarioRepository;
import com.fiscalizacionhse.service.PermisoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bloquea el acceso a un endpoint si el usuario autenticado no tiene habilitado
 * el módulo correspondiente (matriz por rol u override por usuario).
 *
 * Complementa a {@code SecurityConfig} (que protege por rol): aquí se aplica la
 * capa de módulos. El Super Admin siempre pasa.
 *
 * No es un {@code @Component} a propósito: se instancia en {@code SecurityConfig}
 * para evitar el auto-registro como filtro de servlet (que lo ejecutaría antes
 * de que el JWT establezca la autenticación).
 */
@RequiredArgsConstructor
public class ModuloAccessFilter extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String modulo = moduloRequerido(request.getServletPath());

        if (modulo == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // sin autenticación válida: que lo resuelva la cadena de seguridad (401)
            filterChain.doFilter(request, response);
            return;
        }

        Long usuarioId;
        try {
            usuarioId = Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            filterChain.doFilter(request, response);
            return;
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!permisoService.tieneModulo(usuario, modulo)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"codigo\":403,\"mensaje\":\"No tienes acceso al módulo requerido para esta operación.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Mapea el path (relativo al context-path) al código de módulo requerido.
     * Devuelve null cuando el endpoint no está restringido por módulo.
     */
    private String moduloRequerido(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("/api/empresas")) return "empresas";
        if (path.startsWith("/api/usuarios")) return "usuarios";
        if (path.startsWith("/api/auditoria")) return "auditoria";
        if (path.startsWith("/api/documentos")) return "documentos";
        if (path.startsWith("/api/puntos-clave")) return "puntos_clave";
        if (path.startsWith("/api/recordatorios")) return "recordatorios";
        if (path.startsWith("/api/notificaciones")) return "notificaciones";
        if (path.startsWith("/api/ia/salud")) return null; // health check público
        if (path.startsWith("/api/ia")) return "ia";
        // /api/me, /api/permisos y otros: sin restricción de módulo
        return null;
    }
}
