package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.AccesoUsuarioDto;
import com.fiscalizacionhse.dto.request.RolModuloDto;
import com.fiscalizacionhse.dto.response.ModuloResponse;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Modulo;
import com.fiscalizacionhse.model.RolModulo;
import com.fiscalizacionhse.model.RolModuloId;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.model.UsuarioModulo;
import com.fiscalizacionhse.model.UsuarioModuloId;
import com.fiscalizacionhse.repository.ModuloRepository;
import com.fiscalizacionhse.repository.RolModuloRepository;
import com.fiscalizacionhse.repository.UsuarioModuloRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermisoService {

    private final ModuloRepository moduloRepository;
    private final RolModuloRepository rolModuloRepository;
    private final UsuarioModuloRepository usuarioModuloRepository;
    private final UsuarioRepository usuarioRepository;

    // ── Catálogo de módulos ───────────────────────────────────────────
    public List<ModuloResponse> listarModulos() {
        return moduloRepository.findAllByOrderByOrdenAsc().stream()
                .map(this::toModuloResponse)
                .collect(Collectors.toList());
    }

    // ── Matriz rol × módulo ───────────────────────────────────────────
    public List<RolModuloDto> getMatriz() {
        return rolModuloRepository.findAll().stream()
                .map(rm -> new RolModuloDto(rm.getRol(), rm.getModuloCodigo(), Boolean.TRUE.equals(rm.getHabilitado())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void guardarMatriz(List<RolModuloDto> permisos) {
        if (permisos == null) {
            return;
        }
        for (RolModuloDto d : permisos) {
            // SUPER_ADMIN siempre con acceso total: no se puede deshabilitar
            boolean habilitado = "SUPER_ADMIN".equalsIgnoreCase(d.getRol()) || d.isHabilitado();
            RolModulo rm = rolModuloRepository
                    .findById(new RolModuloId(d.getRol(), d.getModulo()))
                    .orElse(RolModulo.builder()
                            .rol(d.getRol())
                            .moduloCodigo(d.getModulo())
                            .habilitado(false)
                            .build());
            rm.setHabilitado(habilitado);
            rolModuloRepository.save(rm);
        }
    }

    // ── Acceso por usuario ────────────────────────────────────────────
    public AccesoUsuarioDto getAccesoUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));

        boolean custom = Boolean.TRUE.equals(usuario.getAccesosPersonalizados());

        Map<String, Boolean> habilitados;
        if (custom) {
            habilitados = usuarioModuloRepository.findByUsuarioId(usuarioId).stream()
                    .collect(Collectors.toMap(UsuarioModulo::getModuloCodigo,
                            um -> Boolean.TRUE.equals(um.getHabilitado())));
        } else {
            habilitados = rolModuloRepository.findByRol(usuario.getRol().name()).stream()
                    .collect(Collectors.toMap(RolModulo::getModuloCodigo,
                            rm -> Boolean.TRUE.equals(rm.getHabilitado())));
        }

        List<AccesoUsuarioDto.ModuloFlag> modulos = moduloRepository.findAllByOrderByOrdenAsc().stream()
                .map(m -> AccesoUsuarioDto.ModuloFlag.builder()
                        .modulo(m.getCodigo())
                        .habilitado(habilitados.getOrDefault(m.getCodigo(), false))
                        .build())
                .collect(Collectors.toList());

        return AccesoUsuarioDto.builder()
                .modo(custom ? "custom" : "rol")
                .modulos(modulos)
                .build();
    }

    @Transactional
    public void guardarAccesoUsuario(Long usuarioId, AccesoUsuarioDto dto) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));

        boolean custom = "custom".equalsIgnoreCase(dto.getModo());
        usuario.setAccesosPersonalizados(custom);
        usuarioRepository.save(usuario);

        // siempre se limpian los overrides previos
        usuarioModuloRepository.deleteByUsuarioId(usuarioId);

        if (custom && dto.getModulos() != null) {
            for (AccesoUsuarioDto.ModuloFlag f : dto.getModulos()) {
                usuarioModuloRepository.save(UsuarioModulo.builder()
                        .usuarioId(usuarioId)
                        .moduloCodigo(f.getModulo())
                        .habilitado(f.isHabilitado())
                        .build());
            }
        }
    }

    /** Comprobación puntual (1 consulta) de si el usuario tiene un módulo habilitado. */
    public boolean tieneModulo(Usuario usuario, String codigo) {
        if (usuario.getRol() != null && "SUPER_ADMIN".equals(usuario.getRol().name())) {
            return true;
        }
        if (Boolean.TRUE.equals(usuario.getAccesosPersonalizados())) {
            return usuarioModuloRepository.findById(new UsuarioModuloId(usuario.getId(), codigo))
                    .map(um -> Boolean.TRUE.equals(um.getHabilitado()))
                    .orElse(false);
        }
        return rolModuloRepository.findById(new RolModuloId(usuario.getRol().name(), codigo))
                .map(rm -> Boolean.TRUE.equals(rm.getHabilitado()))
                .orElse(false);
    }

    // ── Módulos efectivos (para login / perfil) ───────────────────────
    public List<String> modulosEfectivos(Usuario usuario) {
        // El Super Admin tiene acceso a todos los módulos del catálogo
        if (usuario.getRol() != null && "SUPER_ADMIN".equals(usuario.getRol().name())) {
            return moduloRepository.findAllByOrderByOrdenAsc().stream()
                    .map(Modulo::getCodigo)
                    .collect(Collectors.toList());
        }

        if (Boolean.TRUE.equals(usuario.getAccesosPersonalizados())) {
            return usuarioModuloRepository.findByUsuarioId(usuario.getId()).stream()
                    .filter(um -> Boolean.TRUE.equals(um.getHabilitado()))
                    .map(UsuarioModulo::getModuloCodigo)
                    .collect(Collectors.toList());
        }

        return rolModuloRepository.findByRol(usuario.getRol().name()).stream()
                .filter(rm -> Boolean.TRUE.equals(rm.getHabilitado()))
                .map(RolModulo::getModuloCodigo)
                .collect(Collectors.toList());
    }

    private ModuloResponse toModuloResponse(Modulo m) {
        return ModuloResponse.builder()
                .codigo(m.getCodigo())
                .nombre(m.getNombre())
                .descripcion(m.getDescripcion())
                .grupo(m.getGrupo())
                .icono(m.getIcono())
                .orden(m.getOrden())
                .build();
    }
}
