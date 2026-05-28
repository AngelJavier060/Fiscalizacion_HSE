package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.response.UsuarioResponse;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.UsuarioRepository;
import com.fiscalizacionhse.service.PermisoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioResponse> miPerfil(Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        UsuarioResponse response = UsuarioResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .activo(usuario.getActivo())
                .empresaId(usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null)
                .empresaNombre(usuario.getEmpresa() != null ? usuario.getEmpresa().getNombre() : null)
                .ultimoAcceso(usuario.getUltimoAcceso())
                .createdAt(usuario.getCreatedAt())
                .accesoDesde(usuario.getAccesoDesde())
                .accesoHasta(usuario.getAccesoHasta())
                .accesosPersonalizados(usuario.getAccesosPersonalizados())
                .modulos(permisoService.modulosEfectivos(usuario))
                .build();

        return ResponseEntity.ok(response);
    }
}
