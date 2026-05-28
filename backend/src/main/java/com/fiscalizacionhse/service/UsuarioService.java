package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.UsuarioRequest;
import com.fiscalizacionhse.dto.response.UsuarioResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Empresa;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.model.enums.RolUsuario;
import com.fiscalizacionhse.repository.EmpresaRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<UsuarioResponse> listarPorEmpresa(Long empresaId, Pageable pageable) {
        return usuarioRepository.findByEmpresaId(empresaId, pageable).map(this::toResponse);
    }

    public UsuarioResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("La contraseña es obligatoria");
        }

        Usuario actor = buscarActorAutenticado();

        // Validar email único
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El email " + request.getEmail() + " ya está registrado");
        }

        // Determinar rol
        RolUsuario rol;
        try {
            rol = request.getRol() != null
                    ? RolUsuario.valueOf(request.getRol().toUpperCase())
                    : RolUsuario.USUARIO;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol inválido: " + request.getRol());
        }

        // No permitir crear SUPER_ADMIN desde este endpoint
        if (rol == RolUsuario.SUPER_ADMIN) {
            throw new BadRequestException("No se puede crear un Super Administrador");
        }

        // Validar empresa
        Empresa empresa = null;
        if (request.getEmpresaId() != null) {
            empresa = empresaRepository.findById(request.getEmpresaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.getEmpresaId()));

            if (!empresa.getActiva()) {
                throw new BadRequestException("No se puede crear usuarios en una empresa suspendida");
            }
        } else if (rol != RolUsuario.SUPER_ADMIN) {
            throw new BadRequestException("El usuario debe pertenecer a una empresa");
        }

        if (actor.getRol() == RolUsuario.ADMIN_EMPRESA) {
            Long miEmpresa = actor.getEmpresa() != null ? actor.getEmpresa().getId() : null;
            if (request.getEmpresaId() == null || miEmpresa == null
                    || !miEmpresa.equals(request.getEmpresaId())) {
                throw new BadRequestException("Solo puede crear usuarios en su propia empresa");
            }
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(rol)
                .empresa(empresa)
                .accesoDesde(request.getAccesoDesde())
                .accesoHasta(request.getAccesoHasta())
                .build();

        usuario = usuarioRepository.save(usuario);

        auditoriaService.registrar(
                actor, empresa, "CREAR_USUARIO", "Usuario",
                usuario.getId(), "Usuario creado: " + usuario.getEmail() + " con rol " + rol, null);

        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        Usuario actor = buscarActorAutenticado();
        Usuario usuario = buscar(id);

        verificarAccesoMismaEmpresa(actor, usuario);

        usuario.setNombre(request.getNombre());

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String nuevo = request.getEmail().trim();
            if (!nuevo.equalsIgnoreCase(usuario.getEmail())
                    && usuarioRepository.existsByEmailAndIdNot(nuevo, usuario.getId())) {
                throw new BadRequestException("El email " + nuevo + " ya está registrado");
            }
            usuario.setEmail(nuevo);
        }

        // Actualizar contraseña solo si se proporciona
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (actor.getRol() == RolUsuario.ADMIN_EMPRESA && request.getEmpresaId() != null) {
            Long miEmpresa = actor.getEmpresa() != null ? actor.getEmpresa().getId() : null;
            if (miEmpresa == null || !miEmpresa.equals(request.getEmpresaId())) {
                throw new BadRequestException("No puede reasignar usuarios a otra empresa");
            }
        }

        // Actualizar rol
        if (request.getRol() != null) {
            RolUsuario nuevoRol;
            try {
                nuevoRol = RolUsuario.valueOf(request.getRol().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Rol inválido: " + request.getRol());
            }

            if (usuario.getRol() == RolUsuario.SUPER_ADMIN && nuevoRol != RolUsuario.SUPER_ADMIN) {
                throw new BadRequestException("No puede cambiar el rol de un Super Administrador");
            }

            if (nuevoRol == RolUsuario.SUPER_ADMIN && usuario.getRol() != RolUsuario.SUPER_ADMIN) {
                throw new BadRequestException("No se puede asignar rol Super Administrador");
            }
            usuario.setRol(nuevoRol);
        }

        // Actualizar empresa (los Super Admin no pertenecen a empresa)
        if (usuario.getRol() != RolUsuario.SUPER_ADMIN && request.getEmpresaId() != null) {
            Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.getEmpresaId()));
            usuario.setEmpresa(empresa);
        }

        // Actualizar vigencia de acceso
        usuario.setAccesoDesde(request.getAccesoDesde());
        usuario.setAccesoHasta(request.getAccesoHasta());

        usuario = usuarioRepository.save(usuario);

        auditoriaService.registrar(
                actor, usuario.getEmpresa(), "ACTUALIZAR_USUARIO", "Usuario",
                usuario.getId(), "Usuario actualizado: " + usuario.getEmail(), null);

        return toResponse(usuario);
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario actor = buscarActorAutenticado();
        Usuario usuario = buscar(id);

        if (usuario.getId().equals(actor.getId())) {
            throw new BadRequestException("No puede eliminar su propio usuario");
        }
        if (usuario.getRol() == RolUsuario.SUPER_ADMIN) {
            throw new BadRequestException("No se puede eliminar un Super Administrador");
        }
        verificarAccesoMismaEmpresa(actor, usuario);

        Empresa empresaRef = usuario.getEmpresa();
        Long idRef = usuario.getId();
        String emailRef = usuario.getEmail();

        try {
            usuarioRepository.delete(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException(
                    "No se puede eliminar el usuario porque tiene registros asociados. "
                            + "Puede desactivarlo con el interruptor de estado.");
        }

        auditoriaService.registrar(
                actor, empresaRef, "ELIMINAR_USUARIO", "Usuario",
                idRef, "Usuario eliminado: " + emailRef, null);
    }

    @Transactional
    public UsuarioResponse toggleActivo(Long id) {
        Usuario actor = buscarActorAutenticado();
        Usuario usuario = buscar(id);
        verificarAccesoMismaEmpresa(actor, usuario);
        usuario.setActivo(!usuario.getActivo());
        usuario = usuarioRepository.save(usuario);

        String accion = usuario.getActivo() ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO";
        String detalle = "Usuario " + (usuario.getActivo() ? "activado" : "desactivado") +
                         ": " + usuario.getEmail();

        auditoriaService.registrar(
                actor, usuario.getEmpresa(), accion, "Usuario",
                usuario.getId(), detalle, null);

        return toResponse(usuario);
    }

    private void verificarAccesoMismaEmpresa(Usuario actor, Usuario objetivo) {
        if (actor.getRol() != RolUsuario.ADMIN_EMPRESA) {
            return;
        }
        Long a = actor.getEmpresa() != null ? actor.getEmpresa().getId() : null;
        Long b = objetivo.getEmpresa() != null ? objetivo.getEmpresa().getId() : null;
        if (a == null || b == null || !a.equals(b)) {
            throw new BadRequestException("No tiene permiso para operar con este usuario");
        }
    }

    private Usuario buscarActorAutenticado() {
        Long actorId = obtenerUsuarioAutenticadoId();
        return usuarioRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", actorId));
    }

    private Long obtenerUsuarioAutenticadoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BadRequestException("Sesión no válida");
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Sesión no válida");
        }
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    private UsuarioResponse toResponse(Usuario u) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .email(u.getEmail())
                .rol(u.getRol().name())
                .activo(u.getActivo())
                .empresaId(u.getEmpresa() != null ? u.getEmpresa().getId() : null)
                .empresaNombre(u.getEmpresa() != null ? u.getEmpresa().getNombre() : null)
                .ultimoAcceso(u.getUltimoAcceso())
                .createdAt(u.getCreatedAt())
                .accesoDesde(u.getAccesoDesde())
                .accesoHasta(u.getAccesoHasta())
                .accesosPersonalizados(u.getAccesosPersonalizados())
                .build();
    }
}
