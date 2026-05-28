package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.LoginRequest;
import com.fiscalizacionhse.dto.response.LoginResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.model.Empresa;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.UsuarioRepository;
import com.fiscalizacionhse.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditoriaService auditoriaService;
    private final PermisoService permisoService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        // Verificar que la empresa del usuario esté activa
        if (usuario.getEmpresa() != null && !usuario.getEmpresa().getActiva()) {
            throw new BadRequestException("La empresa se encuentra suspendida");
        }

        // Verificar vigencia de empresa y de usuario
        verificarVigencia(usuario);

        // Actualizar último acceso
        usuario.setUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        // Generar JWT
        String token = jwtTokenProvider.generateToken(
                usuario.getId(), usuario.getEmail(), usuario.getRol().name());

        // Auditar
        auditoriaService.registrar(
                usuario, usuario.getEmpresa(),
                "INICIO_SESION", "Usuario",
                usuario.getId(), "Inicio de sesión exitoso",
                null);

        return LoginResponse.builder()
                .token(token)
                .tipoToken("Bearer")
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .empresaId(usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null)
                .empresaNombre(usuario.getEmpresa() != null ? usuario.getEmpresa().getNombre() : null)
                .modulos(permisoService.modulosEfectivos(usuario))
                .build();
    }

    /** Bloquea el login si la empresa o el usuario están fuera de su periodo de vigencia. */
    private void verificarVigencia(Usuario usuario) {
        LocalDate hoy = LocalDate.now();

        Empresa empresa = usuario.getEmpresa();
        if (empresa != null) {
            if (empresa.getVigenciaDesde() != null && hoy.isBefore(empresa.getVigenciaDesde())) {
                throw new BadRequestException("El servicio de la empresa aún no está vigente.");
            }
            if (empresa.getVigenciaHasta() != null && hoy.isAfter(empresa.getVigenciaHasta())) {
                throw new BadRequestException(
                        "El servicio de la empresa ha vencido. Contacte al administrador.");
            }
        }

        if (usuario.getAccesoDesde() != null && hoy.isBefore(usuario.getAccesoDesde())) {
            throw new BadRequestException("Su acceso aún no está habilitado.");
        }
        if (usuario.getAccesoHasta() != null && hoy.isAfter(usuario.getAccesoHasta())) {
            throw new BadRequestException("Su acceso ha caducado. Contacte al administrador.");
        }
    }
}
