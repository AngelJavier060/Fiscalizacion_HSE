package com.fiscalizacionhse.config;

import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.model.enums.RolUsuario;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.superadmin.email}")
    private String superAdminEmail;

    @Value("${app.superadmin.password}")
    private String superAdminPassword;

    @Value("${app.superadmin.nombre}")
    private String superAdminNombre;

    @Override
    @Transactional
    public void run(String... args) {
        if (!usuarioRepository.existsByEmail(superAdminEmail)) {
            Usuario superAdmin = Usuario.builder()
                    .nombre(superAdminNombre)
                    .email(superAdminEmail)
                    .password(passwordEncoder.encode(superAdminPassword))
                    .rol(RolUsuario.SUPER_ADMIN)
                    .build();

            usuarioRepository.save(superAdmin);
            log.info("✅ Super Administrador creado: {}", superAdminEmail);
        } else {
            log.info("✅ Super Administrador ya existe: {}", superAdminEmail);
        }
    }
}
